package com.github.damontecres.wholphin.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.CsfdRatingSender
import com.github.damontecres.wholphin.services.CsfdTvTipsService
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.playOnClickSound
import com.github.damontecres.wholphin.ui.playSoundOnFocus
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * My ČSFD rating of one title: null = not rated yet, 0 = "odpad", 1-5 stars.
 *
 * Sending is done by [CsfdRatingSender] in the application scope, so it finishes (and confirms with a toast) even
 * when the page is left; this only mirrors its state.
 */
@HiltViewModel
class CsfdMyRatingViewModel
    @Inject
    constructor(
        private val csfdTvTipsService: CsfdTvTipsService,
        private val ratingSender: CsfdRatingSender,
    ) : ViewModel() {
        private val _stars = MutableStateFlow<Int?>(null)
        val stars: StateFlow<Int?> = _stars

        private val csfdId = MutableStateFlow<Int?>(null)

        /** The rating is waiting to be sent or being sent */
        val sending: StateFlow<Boolean> =
            combine(csfdId, ratingSender.sending) { id, sending -> id != null && id in sending }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(), false)

        init {
            viewModelScope.launch {
                ratingSender.results.collect { result ->
                    // A failed rating goes back; the toast is shown by the sender
                    if (result.csfdId == csfdId.value && result.error != null) {
                        _stars.update { result.revertTo }
                    }
                }
            }
        }

        fun load(csfdId: Int) {
            this.csfdId.update { csfdId }
            viewModelScope.launchIO {
                val rated = csfdTvTipsService.getMyRatings()[csfdId]
                // A choice still on its way to ČSFD wins over the old rating
                _stars.update { ratingSender.pendingStars(csfdId) ?: rated }
            }
        }

        /**
         * Shows [stars] right away and sends it to ČSFD; if it fails the stars go back
         */
        fun rate(
            csfdId: Int,
            stars: Int,
        ) {
            val previous = _stars.value
            _stars.update { stars }
            ratingSender.rate(csfdId, stars, previous)
        }
    }

/** ČSFD id of [this] item from its provider ids, null if it has none */
val BaseItem.csfdId: Int?
    get() =
        data.providerIds
            ?.entries
            ?.firstOrNull { it.key.equals("Csfd", ignoreCase = true) }
            ?.value
            ?.toIntOrNull()

// Same height as the stream labels (1080p, H264…) under it
private val pillHeight = 24.dp
private val choiceSize = 20.dp
private val EmptyChoiceColor = Color(0xFF6B6B6B)

/**
 * "Moje hodnotenie": my ČSFD rating of [item] as a compact pill [👎][★★★★★] (ČSFD style, no text), editable with the
 * remote and synced to ČSFD. The thumb is "odpad" (0 stars), left of the first star. Unrated = everything grey.
 */
@Composable
fun CsfdMyRating(
    item: BaseItem,
    modifier: Modifier = Modifier,
    viewModel: CsfdMyRatingViewModel = hiltViewModel(key = "csfd_my_rating_${item.id}"),
) {
    val csfdId = remember(item.id) { item.csfdId } ?: return
    LaunchedEffect(csfdId) { viewModel.load(csfdId) }
    val stars by viewModel.stars.collectAsState()
    val sending by viewModel.sending.collectAsState()
    // Pulses while the rating is on its way to ČSFD
    val ratingAlpha =
        if (sending) {
            val transition = rememberInfiniteTransition(label = "csfd_rating_sending")
            val pulse by transition.animateFloat(
                initialValue = 1f,
                targetValue = .35f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 500), repeatMode = RepeatMode.Reverse),
                label = "csfd_rating_sending_alpha",
            )
            pulse
        } else {
            1f
        }
    // 0 = thumb ("odpad"), 1-5 = stars
    val focusRequesters = remember { List(6) { FocusRequester() } }
    // The choice under the cursor is previewed before it is chosen
    var focusedChoice by remember { mutableStateOf<Int?>(null) }
    val shown = focusedChoice ?: stars
    val description = stringResource(R.string.csfd_my_rating)
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .height(pillHeight)
                .background(AppColors.TransparentBlack75, RoundedCornerShape(percent = 50))
                .padding(horizontal = 4.dp)
                .graphicsLayer { alpha = ratingAlpha }
                .semantics { contentDescription = description }
                .focusProperties {
                    onEnter = {
                        // Start on the current rating; unrated = default (nearest) choice
                        stars?.let { focusRequesters[it.coerceIn(0, 5)].tryRequestFocus() }
                    }
                }.focusGroup(),
    ) {
        RatingChoice(
            focusRequester = focusRequesters[0],
            onFocusChanged = {
                if (it) {
                    focusedChoice = 0
                } else if (focusedChoice == 0) {
                    focusedChoice = null
                }
            },
            onClick = { if (stars != 0) viewModel.rate(csfdId, 0) },
        ) {
            Text(
                text = stringResource(R.string.fa_thumbs_down),
                fontFamily = FontAwesome,
                fontSize = 13.sp,
                color = if (shown == 0) CsfdRed else EmptyChoiceColor,
            )
        }
        for (i in 1..5) {
            RatingChoice(
                focusRequester = focusRequesters[i],
                onFocusChanged = {
                    if (it) {
                        focusedChoice = i
                    } else if (focusedChoice == i) {
                        focusedChoice = null
                    }
                },
                onClick = { if (stars != i) viewModel.rate(csfdId, i) },
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (shown != null && shown >= i) CsfdRed else EmptyChoiceColor,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/**
 * One focusable choice in [CsfdMyRating] (the thumb or a star), highlighted by a light circle while focused
 */
@Composable
private fun RatingChoice(
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(choiceSize)
                .clip(CircleShape)
                .background(if (focused) Color.White.copy(alpha = .3f) else Color.Transparent)
                .focusRequester(focusRequester)
                .onFocusChanged {
                    focused = it.isFocused
                    onFocusChanged.invoke(it.isFocused)
                }.playSoundOnFocus(true)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    playOnClickSound(context)
                    onClick.invoke()
                },
    ) {
        content()
    }
}

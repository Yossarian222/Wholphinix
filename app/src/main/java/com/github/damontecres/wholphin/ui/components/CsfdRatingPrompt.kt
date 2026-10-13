package com.github.damontecres.wholphin.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.CsfdTvTipsService
import com.github.damontecres.wholphin.services.PendingRating
import com.github.damontecres.wholphin.services.PendingRatingService
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class CsfdRatingPromptViewModel
    @Inject
    constructor(
        private val pendingRatingService: PendingRatingService,
        private val csfdTvTipsService: CsfdTvTipsService,
    ) : ViewModel() {
        val pending: StateFlow<PendingRating?> = pendingRatingService.pending

        private val _sending = MutableStateFlow(false)
        val sending: StateFlow<Boolean> = _sending

        /** The stars chosen in the prompt (0 = "odpad"), kept while sending and after a failure so it can be retried */
        private val _chosen = MutableStateFlow<Int?>(null)
        val chosen: StateFlow<Int?> = _chosen

        /** Result of the last rating, shown once by the UI: null = nothing, "" = success, else an error message */
        val result = MutableStateFlow<String?>(null)

        /** Sends [stars] (0 = "odpad") to ČSFD; the prompt closes on success and stays open on failure */
        fun rate(
            pending: PendingRating,
            stars: Int,
        ) {
            if (_sending.value) {
                Timber.i("ČSFD rating prompt: %s stars ignored, still sending", stars)
                return
            }
            _sending.update { true }
            _chosen.update { stars }
            Timber.i(
                "ČSFD rating prompt: sending %s stars for %s (ČSFD %s, item %s)",
                stars,
                pending.title,
                pending.csfdId,
                pending.itemId,
            )
            viewModelScope.launchIO {
                val error = csfdTvTipsService.rate(pending.csfdId, stars)
                _sending.update { false }
                if (error == null) {
                    Timber.i("ČSFD rating prompt: %s stars for ČSFD %s sent", stars, pending.csfdId)
                    _chosen.update { null }
                    pendingRatingService.clear()
                    result.update { "" }
                } else {
                    Timber.w("ČSFD rating prompt: %s stars for ČSFD %s failed: %s", stars, pending.csfdId, error)
                    result.update { error }
                }
            }
        }

        fun dismiss() {
            Timber.i("ČSFD rating prompt dismissed")
            _chosen.update { null }
            pendingRatingService.clear()
        }
    }

/**
 * "How did you like it?" asked after a movie (or the last episode of a season) with a ČSFD id was watched to the end
 * and is not rated yet.
 *
 * The stars stay focusable and lit at the chosen rating while it is sent (clicks are ignored meanwhile), so the focus
 * never jumps away. "odpad!" is below the stars and needs a second press to confirm.
 *
 * @param show false while the prompt must wait, e.g. the player is still on screen
 */
@Composable
fun CsfdRatingPrompt(
    show: Boolean,
    viewModel: CsfdRatingPromptViewModel,
) {
    val context = LocalContext.current
    val result by viewModel.result.collectAsState()
    val ratedMessage = stringResource(R.string.csfd_rating_prompt_sent)
    LaunchedEffect(result) {
        result?.let {
            Toast.makeText(context, it.ifEmpty { ratedMessage }, Toast.LENGTH_LONG).show()
            viewModel.result.update { null }
        }
    }
    val pending by viewModel.pending.collectAsState()
    val current = pending
    if (!show || current == null) return
    val sending by viewModel.sending.collectAsState()
    val chosen by viewModel.chosen.collectAsState()
    BasicDialog(onDismissRequest = viewModel::dismiss) {
        val starsFocus = remember { FocusRequester() }
        LaunchedEffect(current.itemId) { starsFocus.tryRequestFocus() }
        // "odpad!" asks "Naozaj odpad?" first, a second press sends it
        var confirmTrash by remember(current.itemId) { mutableStateOf(false) }
        val title =
            current.seasonNumber?.let { stringResource(R.string.csfd_rating_prompt_season, current.title, it) }
                ?: current.title
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .widthIn(max = 520.dp)
                    .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.csfd_rating_prompt_title, title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            StarRating(
                rating100 = (chosen ?: 0) * 20,
                onRatingChange = { rating100 ->
                    val stars = rating100 / 20
                    // While sending the stars stay focused and lit, another press does nothing
                    if (stars > 0 && !sending) {
                        confirmTrash = false
                        viewModel.rate(current, stars)
                    }
                },
                precision = StarRatingPrecision.FULL,
                enabled = true,
                playSoundOnFocus = true,
                allowZero = false,
                wrapAround = false,
                modifier =
                    Modifier
                        .height(ratingBarHeight * 1.5f)
                        .width(ratingBarHeight * 9)
                        .focusRequester(starsFocus),
            )
            Text(
                text = if (sending) stringResource(R.string.csfd_rating_prompt_sending) else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // ČSFD's rating below one star
                Surface(
                    onClick = {
                        if (!sending) {
                            if (confirmTrash) {
                                confirmTrash = false
                                viewModel.rate(current, 0)
                            } else {
                                confirmTrash = true
                            }
                        }
                    },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors =
                        ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                        ),
                    modifier =
                        Modifier.onFocusChanged {
                            // Leaving the button cancels the confirmation
                            if (!it.isFocused) confirmTrash = false
                        },
                ) {
                    Text(
                        text =
                            stringResource(
                                if (confirmTrash) R.string.csfd_rating_prompt_trash_confirm else R.string.csfd_trash,
                            ),
                        style = MaterialTheme.typography.titleMedium,
                        color = csfdColor(0),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                TextButton(
                    stringRes = R.string.csfd_rating_prompt_not_now,
                    onClick = viewModel::dismiss,
                )
            }
        }
    }
}

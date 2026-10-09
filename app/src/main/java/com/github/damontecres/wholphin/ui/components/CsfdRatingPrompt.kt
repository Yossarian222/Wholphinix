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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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

        /** Result of the last rating, shown once by the UI: null = nothing, "" = success, else an error message */
        val result = MutableStateFlow<String?>(null)

        /** Sends [stars] (0 = "odpad") to ČSFD; the prompt closes on success and stays open on failure */
        fun rate(
            pending: PendingRating,
            stars: Int,
        ) {
            if (_sending.value) return
            _sending.update { true }
            viewModelScope.launchIO {
                val error = csfdTvTipsService.rate(pending.csfdId, stars)
                _sending.update { false }
                if (error == null) {
                    pendingRatingService.clear()
                    result.update { "" }
                } else {
                    result.update { error }
                }
            }
        }

        fun dismiss() {
            pendingRatingService.clear()
        }
    }

/**
 * "How did you like it?" asked after a movie with a ČSFD id was watched to the end and is not rated yet.
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
    BasicDialog(onDismissRequest = viewModel::dismiss) {
        val starsFocus = remember { FocusRequester() }
        LaunchedEffect(current.itemId) { starsFocus.tryRequestFocus() }
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .widthIn(max = 520.dp)
                    .padding(24.dp),
        ) {
            Text(
                text = stringResource(R.string.csfd_rating_prompt_title, current.title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StarRating(
                    rating100 = 0,
                    onRatingChange = { rating100 ->
                        val stars = rating100 / 20
                        if (stars > 0) viewModel.rate(current, stars)
                    },
                    precision = StarRatingPrecision.FULL,
                    enabled = !sending,
                    playSoundOnFocus = true,
                    allowZero = false,
                    // Right of the fifth star is the "odpad!" button
                    wrapAround = false,
                    modifier =
                        Modifier
                            .height(ratingBarHeight * 1.5f)
                            .width(ratingBarHeight * 9)
                            .focusRequester(starsFocus),
                )
                // ČSFD's rating below one star
                Surface(
                    onClick = { viewModel.rate(current, 0) },
                    enabled = !sending,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(6.dp)),
                    colors =
                        ClickableSurfaceDefaults.colors(
                            containerColor = Color.Transparent,
                            focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                        ),
                ) {
                    Text(
                        text = stringResource(R.string.csfd_trash),
                        style = MaterialTheme.typography.titleMedium,
                        color = csfdColor(0),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            TextButton(
                stringRes = R.string.csfd_rating_prompt_not_now,
                onClick = viewModel::dismiss,
            )
        }
    }
}

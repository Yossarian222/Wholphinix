package com.github.damontecres.wholphin.ui.components

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.CsfdTvTipsService
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * My ČSFD rating of one title: null = not rated yet, 0 = "odpad", 1-5 stars
 */
@HiltViewModel
class CsfdMyRatingViewModel
    @Inject
    constructor(
        private val csfdTvTipsService: CsfdTvTipsService,
    ) : ViewModel() {
        private val _stars = MutableStateFlow<Int?>(null)
        val stars: StateFlow<Int?> = _stars

        fun load(csfdId: Int) {
            viewModelScope.launchIO {
                _stars.update { csfdTvTipsService.getMyRatings()[csfdId] }
            }
        }

        /** Error from the last rating, shown once by the UI */
        val error = MutableStateFlow<String?>(null)

        /**
         * Sends the rating to ČSFD; if it fails the stars go back and [error] gets the message
         */
        fun rate(
            csfdId: Int,
            stars: Int,
        ) {
            val previous = _stars.value
            _stars.update { stars }
            viewModelScope.launchIO {
                csfdTvTipsService.rate(csfdId, stars)?.let { message ->
                    _stars.update { previous }
                    error.update { message }
                }
            }
        }
    }

/**
 * "Moje hodnotenie": my ČSFD stars for [item], editable with the remote (whole stars, synced to ČSFD)
 */
@Composable
fun CsfdMyRating(
    item: BaseItem,
    modifier: Modifier = Modifier,
    viewModel: CsfdMyRatingViewModel = hiltViewModel(key = "csfd_my_rating_${item.id}"),
) {
    val csfdId =
        remember(item.id) {
            item.data.providerIds
                ?.entries
                ?.firstOrNull { it.key.equals("Csfd", ignoreCase = true) }
                ?.value
                ?.toIntOrNull()
        } ?: return
    LaunchedEffect(csfdId) { viewModel.load(csfdId) }
    val stars by viewModel.stars.collectAsState()
    val context = LocalContext.current
    val error by viewModel.error.collectAsState()
    LaunchedEffect(error) {
        error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.error.update { null }
        }
    }
    var focused by remember { mutableStateOf(false) }
    // Grows a little while the stars are being chosen
    val scale by animateFloatAsState(if (focused) 1.25f else 1f, label = "csfdRatingScale")
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Text(
            text = stringResource(R.string.csfd_my_rating),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        StarRating(
            rating100 = (stars ?: 0) * 20,
            onRatingChange = { rating100 ->
                viewModel.rate(csfdId, rating100 / 20)
            },
            precision = StarRatingPrecision.FULL,
            enabled = true,
            playSoundOnFocus = true,
            modifier =
                Modifier
                    .height(ratingBarHeight)
                    .width(ratingBarHeight * 6)
                    .onFocusChanged { focused = it.hasFocus }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin =
                            androidx.compose.ui.graphics
                                .TransformOrigin(0f, 0.5f)
                    },
        )
        if (stars == 0) {
            Text(
                text = stringResource(R.string.csfd_trash),
                style = MaterialTheme.typography.bodyMedium,
                color = csfdColor(0),
            )
        }
    }
}

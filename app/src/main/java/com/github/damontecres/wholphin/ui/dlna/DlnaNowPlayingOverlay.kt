package com.github.damontecres.wholphin.ui.dlna

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.TvMessageService
import com.github.damontecres.wholphin.services.dlna.DlnaNowPlaying
import com.github.damontecres.wholphin.services.dlna.DlnaRendererService
import com.github.damontecres.wholphin.services.dlna.DlnaXml
import com.github.damontecres.wholphin.services.dlna.TransportStates
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

/**
 * Full screen "Now playing" for music sent from a phone over DLNA, over the rest of the app.
 *
 * D-pad: OK/play-pause toggles, left/right seek 10 seconds, back hides the screen (the music goes on).
 */
@Composable
fun DlnaNowPlayingOverlay(
    service: DlnaRendererService,
    tvMessageService: TvMessageService,
    modifier: Modifier = Modifier,
) {
    val state by service.state.collectAsState()
    val context = LocalContext.current
    val hide = {
        val wasPlaying = service.state.value.isPlaying
        service.hide()
        if (wasPlaying) {
            tvMessageService.show(
                context.getString(R.string.dlna_renderer_title),
                context.getString(R.string.dlna_music_continues),
                4000L,
            )
        }
    }
    // Nothing more to show a while after the phone stopped the music
    LaunchedEffect(state.visible, state.transportState) {
        if (state.visible &&
            (state.transportState == TransportStates.STOPPED || state.transportState == TransportStates.NO_MEDIA)
        ) {
            delay(STOPPED_HIDE_DELAY)
            service.hide()
        }
    }
    AnimatedVisibility(
        visible = state.visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        BackHandler(enabled = state.visible) { hide() }
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) {
            try {
                focusRequester.requestFocus()
            } catch (_: Exception) {
            }
        }
        NowPlayingContent(
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        val native = event.nativeKeyEvent
                        val isDown = native.action == KeyEvent.ACTION_DOWN
                        when (native.keyCode) {
                            KeyEvent.KEYCODE_DPAD_CENTER,
                            KeyEvent.KEYCODE_ENTER,
                            KeyEvent.KEYCODE_NUMPAD_ENTER,
                            KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
                            KeyEvent.KEYCODE_MEDIA_PLAY,
                            KeyEvent.KEYCODE_MEDIA_PAUSE,
                            -> {
                                if (!isDown) service.togglePlayPause()
                                true
                            }

                            KeyEvent.KEYCODE_DPAD_LEFT,
                            KeyEvent.KEYCODE_MEDIA_REWIND,
                            -> {
                                if (isDown) service.seekBy(-SEEK_MS)
                                true
                            }

                            KeyEvent.KEYCODE_DPAD_RIGHT,
                            KeyEvent.KEYCODE_MEDIA_FAST_FORWARD,
                            -> {
                                if (isDown) service.seekBy(SEEK_MS)
                                true
                            }

                            KeyEvent.KEYCODE_MEDIA_STOP -> {
                                if (!isDown) service.stopPlayback()
                                true
                            }

                            KeyEvent.KEYCODE_BACK -> {
                                if (!isDown) hide()
                                true
                            }

                            // Keep the focus here, nothing behind the screen should react
                            KeyEvent.KEYCODE_DPAD_UP,
                            KeyEvent.KEYCODE_DPAD_DOWN,
                            -> {
                                true
                            }

                            else -> {
                                false
                            }
                        }
                    },
        )
    }
}

@Composable
private fun NowPlayingContent(
    state: DlnaNowPlaying,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier.background(
                Brush.linearGradient(listOf(Color(0xFF0F1020), Color(0xFF1E1530), Color(0xFF0A0A12))),
            ),
    ) {
        // Blurred-looking backdrop of the cover
        state.albumArtUri?.let { art ->
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.18f,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 80.dp, vertical = 48.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(340.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Text(
                    text = "\u266B",
                    fontSize = 120.sp,
                    color = Color.White.copy(alpha = 0.5f),
                )
                state.albumArtUri?.let { art ->
                    AsyncImage(
                        model = art,
                        contentDescription = state.album,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.dlna_now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White.copy(alpha = 0.6f),
                )
                Text(
                    text = state.title ?: "",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                state.artist?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                state.album?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(24.dp))
                ProgressBar(state.positionMs, state.durationMs)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (state.isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24,
                            ),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                    Text(
                        text =
                            when {
                                state.isBuffering && !state.isPlaying -> stringResource(R.string.dlna_loading)
                                state.transportState == TransportStates.STOPPED -> stringResource(R.string.dlna_stopped)
                                else -> DlnaXml.formatTime(state.positionMs)
                            },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                    )
                    Spacer(Modifier.weight(1f))
                    if (state.durationMs > 0) {
                        Text(
                            text = DlnaXml.formatTime(state.durationMs),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White.copy(alpha = 0.7f),
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.dlna_controls_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.5f),
                )
            }
        }
    }
}

@Composable
private fun ProgressBar(
    positionMs: Long,
    durationMs: Long,
) {
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.White.copy(alpha = 0.2f)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(MaterialTheme.colorScheme.primary),
        )
    }
}

private const val SEEK_MS = 10_000L
private val STOPPED_HIDE_DELAY = 8.seconds

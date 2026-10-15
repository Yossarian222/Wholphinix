package com.github.damontecres.wholphin.ui.components

import androidx.annotation.OptIn
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.util.lerp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import com.github.damontecres.wholphin.R

// TV screen quad in the intro video (normalized tl, tr, br, bl), from the renderer's intro_quad.json
private val TV_QUAD = floatArrayOf(0.38496f, 0.27361f, 0.6149f, 0.2736f, 0.61224f, 0.50632f, 0.38762f, 0.50576f)
private val FULL_QUAD = floatArrayOf(0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f)
private const val TV_ON_MS = 14_650L
private const val TV_FADE_MS = 300f
private const val ZOOM_MS = 750

/**
 * Plays the bundled NASflix intro while [active]; the app [content] appears inside the TV in the
 * video's last shot, then zooms to full screen and [onFinished] is called (also on error).
 * [content] is always composed at the same place, so the app keeps its state across the intro.
 */
@OptIn(UnstableApi::class)
@Composable
fun IntroHost(
    active: Boolean,
    onFinished: () -> Unit,
    content: @Composable () -> Unit,
) {
    // Reset each time the intro (re)starts
    var reveal by remember(active) { mutableFloatStateOf(0f) }
    var zoom by remember(active) { mutableFloatStateOf(0f) }
    val finish by rememberUpdatedState(onFinished)
    Box(Modifier.fillMaxSize()) {
        if (active) {
            val context = LocalContext.current
            val player =
                remember {
                    ExoPlayer.Builder(context).build().apply {
                        setMediaItem(MediaItem.fromUri("android.resource://${context.packageName}/${R.raw.intro}"))
                        playWhenReady = true
                        prepare()
                    }
                }
            DisposableEffect(player) {
                val listener =
                    object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) = finish()
                    }
                player.addListener(listener)
                onDispose {
                    player.removeListener(listener)
                    player.release()
                }
            }
            LaunchedEffect(player) {
                while (player.playbackState != Player.STATE_ENDED) {
                    withFrameMillis { }
                    reveal = ((player.currentPosition - TV_ON_MS) / TV_FADE_MS).coerceIn(0f, 1f)
                }
                reveal = 1f
                Animatable(0f).animateTo(1f, tween(ZOOM_MS, easing = FastOutSlowInEasing)) { zoom = value }
                finish()
            }
            PlayerSurface(
                player = player,
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        }
        Box(
            Modifier.fillMaxSize().drawWithContent {
                if (!active) {
                    drawContent()
                } else if (reveal > 0f) {
                    val src = floatArrayOf(0f, 0f, size.width, 0f, size.width, size.height, 0f, size.height)
                    val dst = FloatArray(8) { i -> lerp(TV_QUAD[i], FULL_QUAD[i], zoom) * if (i % 2 == 0) size.width else size.height }
                    val matrix = android.graphics.Matrix().apply { setPolyToPoly(src, 0, dst, 0, 4) }
                    val canvas = drawContext.canvas
                    canvas.saveLayer(Rect(0f, 0f, size.width, size.height), Paint().apply { alpha = reveal })
                    canvas.nativeCanvas.concat(matrix)
                    drawContent()
                    canvas.restore()
                }
            },
        ) {
            content()
        }
    }
}

package com.github.damontecres.wholphin.ui.components

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_SURFACE_VIEW
import com.github.damontecres.wholphin.R

/** Plays the bundled NASflix intro once, calls [onFinished] when it ends or fails. */
@OptIn(UnstableApi::class)
@Composable
fun IntroVideo(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_ENDED) onFinished()
                }

                override fun onPlayerError(error: PlaybackException) = onFinished()
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    PlayerSurface(
        player = player,
        surfaceType = SURFACE_TYPE_SURFACE_VIEW,
        modifier = modifier.fillMaxSize().background(Color.Black),
    )
}

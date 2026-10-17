package com.github.damontecres.wholphin.ui.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import org.jellyfin.sdk.model.api.MediaSourceInfo
import com.github.damontecres.wholphin.services.PlaybackEvents
import timber.log.Timber

class TracksChangedListener(
    val player: Player,
    val audioIndex: Int?,
    val subtitleIndex: Int?,
    val source: MediaSourceInfo,
    val onFailure: () -> Unit,
) : Player.Listener {
    override fun onTracksChanged(tracks: Tracks) {
        Timber.v("onTracksChanged: $tracks")
        if (tracks.groups.isNotEmpty()) {
            val result =
                TrackSelectionUtils.createTrackSelections(
                    player.trackSelectionParameters,
                    player.currentTracks,
                    audioIndex,
                    subtitleIndex,
                    source,
                )
            Timber.v("onTracksChanged: %s", result)
            player.removeListener(this)
            if (result.bothSelected) {
                player.trackSelectionParameters =
                    result.trackSelectionParameters
            } else {
                // Fall back to transcoding
                Timber.w(
                    "Failed to select tracks, falling back to transcoding: %s",
                    result,
                )
                val formats =
                    tracks.groups
                        .filter { it.type == C.TRACK_TYPE_AUDIO || it.type == C.TRACK_TYPE_TEXT }
                        .joinToString { g ->
                            val f = g.getTrackFormat(0)
                            "${f.sampleMimeType}${f.codecs?.let { "($it)" }.orEmpty()} ${f.language} supported=${g.isSupported}"
                        }
                PlaybackEvents.add(
                    "track selection failed (audio=${result.audio}, sub=${result.subtitle}, wanted a=$audioIndex s=$subtitleIndex) → transcode; tracks: $formats",
                )
                onFailure.invoke()
            }
        }
    }
}

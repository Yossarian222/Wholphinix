package com.github.damontecres.wholphin.ui.tidal

import androidx.compose.ui.input.key.Key

/** What a media key of the remote does on the TIDAL page */
enum class TidalMediaAction {
    PLAY_PAUSE,
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS,
    FORWARD,
    REWIND,
}

/**
 * The action for a media key of the remote (Play/Pause, next/previous track, fast forward/rewind), null for other keys.
 * Channel up/down also skip tracks, some remotes (and HDMI-CEC) have no next/previous buttons.
 */
fun tidalMediaAction(key: Key): TidalMediaAction? =
    when (key) {
        Key.MediaPlayPause -> TidalMediaAction.PLAY_PAUSE
        Key.MediaPlay -> TidalMediaAction.PLAY
        Key.MediaPause, Key.MediaStop -> TidalMediaAction.PAUSE
        Key.MediaNext, Key.MediaSkipForward, Key.ChannelUp -> TidalMediaAction.NEXT
        Key.MediaPrevious, Key.MediaSkipBackward, Key.ChannelDown -> TidalMediaAction.PREVIOUS
        Key.MediaFastForward -> TidalMediaAction.FORWARD
        Key.MediaRewind -> TidalMediaAction.REWIND
        else -> null
    }

/** Fast forward / rewind step of the media keys */
const val MEDIA_KEY_SEEK_MS = 10_000L

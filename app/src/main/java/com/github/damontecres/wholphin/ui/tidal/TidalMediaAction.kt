package com.github.damontecres.wholphin.ui.tidal

import androidx.compose.ui.input.key.Key

/** What a media key of the remote does on the TIDAL page */
enum class TidalMediaAction {
    PLAY_PAUSE,
    PLAY,
    PAUSE,
    NEXT,
    PREVIOUS,
}

/**
 * The action for a media key of the remote (Play/Pause, next/previous track), null for other keys.
 *
 * Fast forward / rewind also skip tracks (#78): a TV remote over HDMI-CEC usually has no next/previous buttons and the
 * TV keeps channel up/down for itself, but ⏩/⏪ get through; seeking within a song is rarely wanted for music.
 */
fun tidalMediaAction(key: Key): TidalMediaAction? =
    when (key) {
        Key.MediaPlayPause -> TidalMediaAction.PLAY_PAUSE
        Key.MediaPlay -> TidalMediaAction.PLAY
        Key.MediaPause, Key.MediaStop -> TidalMediaAction.PAUSE
        Key.MediaNext, Key.MediaSkipForward, Key.MediaFastForward, Key.ChannelUp -> TidalMediaAction.NEXT
        Key.MediaPrevious, Key.MediaSkipBackward, Key.MediaRewind, Key.ChannelDown -> TidalMediaAction.PREVIOUS
        else -> null
    }

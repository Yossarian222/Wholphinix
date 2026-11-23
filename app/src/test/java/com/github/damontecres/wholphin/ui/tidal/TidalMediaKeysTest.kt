package com.github.damontecres.wholphin.ui.tidal

import androidx.compose.ui.input.key.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TidalMediaKeysTest {
    @Test
    fun `Remote media keys control the music`() {
        assertEquals(TidalMediaAction.PLAY_PAUSE, tidalMediaAction(Key.MediaPlayPause))
        assertEquals(TidalMediaAction.PLAY, tidalMediaAction(Key.MediaPlay))
        assertEquals(TidalMediaAction.PAUSE, tidalMediaAction(Key.MediaPause))
        assertEquals(TidalMediaAction.NEXT, tidalMediaAction(Key.MediaNext))
        assertEquals(TidalMediaAction.PREVIOUS, tidalMediaAction(Key.MediaPrevious))
        assertEquals(TidalMediaAction.NEXT, tidalMediaAction(Key.ChannelUp))
        // ⏩/⏪ of a TV remote over HDMI-CEC skip tracks
        assertEquals(TidalMediaAction.NEXT, tidalMediaAction(Key.MediaFastForward))
        assertEquals(TidalMediaAction.PREVIOUS, tidalMediaAction(Key.MediaRewind))
    }

    @Test
    fun `Navigation keys are left alone`() {
        assertNull(tidalMediaAction(Key.DirectionCenter))
        assertNull(tidalMediaAction(Key.DirectionLeft))
        assertNull(tidalMediaAction(Key.Back))
    }
}

package com.github.damontecres.wholphin.services

import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.junit.Assert
import org.junit.Test

class AutoForeignSubtitlesTest {
    private fun audio(
        index: Int,
        lang: String?,
    ): MediaStream =
        MediaStream(
            type = MediaStreamType.AUDIO,
            language = lang,
            isDefault = index == 0,
            isForced = false,
            isHearingImpaired = false,
            isInterlaced = false,
            index = index,
            isExternal = false,
            isTextSubtitleStream = false,
            supportsExternalStream = false,
        )

    private fun sub(
        index: Int,
        lang: String?,
        forced: Boolean = false,
        sdh: Boolean = false,
        title: String? = null,
        default: Boolean = false,
    ): MediaStream =
        MediaStream(
            type = MediaStreamType.SUBTITLE,
            language = lang,
            isDefault = default,
            isForced = forced,
            isHearingImpaired = sdh,
            isInterlaced = false,
            index = index,
            isExternal = false,
            isTextSubtitleStream = true,
            supportsExternalStream = true,
            title = title,
        )

    private fun choose(
        audio: List<MediaStream>,
        subs: List<MediaStream>,
        enabled: Boolean = true,
    ): Int? = AutoForeignSubtitles.choose(audio, subs, enabled)?.stream?.index

    private val englishOnly = listOf(audio(0, "eng"))

    @Test
    fun `english audio with sk cz en subtitles chooses slovak`() {
        Assert.assertEquals(
            3,
            choose(englishOnly, listOf(sub(1, "eng"), sub(2, "cze"), sub(3, "slo"))),
        )
    }

    @Test
    fun `english audio with cz en subtitles chooses czech`() {
        Assert.assertEquals(2, choose(englishOnly, listOf(sub(1, "eng", default = true), sub(2, "ces"))))
    }

    @Test
    fun `english audio with en subtitles chooses english`() {
        Assert.assertEquals(1, choose(englishOnly, listOf(sub(1, "en"))))
    }

    @Test
    fun `english audio with subtitles without language chooses that one`() {
        Assert.assertEquals(1, choose(englishOnly, listOf(sub(1, null))))
        Assert.assertEquals(2, choose(englishOnly, listOf(sub(1, "fre"), sub(2, "und"))))
        Assert.assertEquals(2, choose(englishOnly, listOf(sub(1, "fre"), sub(2, "ger", title = "External"))))
    }

    @Test
    fun `english audio with only other languages keeps behavior`() {
        Assert.assertNull(choose(englishOnly, listOf(sub(1, "fre"), sub(2, "ger"))))
    }

    @Test
    fun `full slovak preferred over forced slovak`() {
        Assert.assertEquals(2, choose(englishOnly, listOf(sub(1, "slk", forced = true, default = true), sub(2, "slk"))))
        Assert.assertEquals(2, choose(englishOnly, listOf(sub(1, "sk", title = "Forced"), sub(2, "sk"))))
    }

    @Test
    fun `full preferred over sdh and sdh over forced`() {
        Assert.assertEquals(2, choose(englishOnly, listOf(sub(1, "eng", sdh = true), sub(2, "eng"))))
        Assert.assertEquals(1, choose(englishOnly, listOf(sub(1, "eng", sdh = true), sub(2, "eng", forced = true))))
    }

    @Test
    fun `forced slovak still preferred over full czech`() {
        Assert.assertEquals(1, choose(englishOnly, listOf(sub(1, "slo", forced = true), sub(2, "cze"))))
    }

    @Test
    fun `czech audio keeps behavior`() {
        Assert.assertNull(choose(listOf(audio(0, "cze")), listOf(sub(1, "slo"), sub(2, "eng"))))
    }

    @Test
    fun `english and slovak audio keeps behavior`() {
        Assert.assertNull(choose(listOf(audio(0, "eng"), audio(1, "slk")), listOf(sub(1, "slo"), sub(2, "eng"))))
    }

    @Test
    fun `disabled preference keeps behavior`() {
        Assert.assertNull(choose(englishOnly, listOf(sub(1, "slo")), enabled = false))
    }

    @Test
    fun `no subtitles keeps behavior`() {
        Assert.assertNull(choose(englishOnly, emptyList()))
    }

    @Test
    fun `language codes are normalized`() {
        listOf("sk", "slo", "slk", "SK", "sk-SK").forEach {
            Assert.assertEquals(it, AutoForeignSubtitles.Language.SLOVAK, AutoForeignSubtitles.normalizeLanguage(it))
        }
        listOf("cs", "cz", "cze", "ces", "cs-CZ").forEach {
            Assert.assertEquals(it, AutoForeignSubtitles.Language.CZECH, AutoForeignSubtitles.normalizeLanguage(it))
        }
        listOf("en", "eng", "en_US").forEach {
            Assert.assertEquals(it, AutoForeignSubtitles.Language.ENGLISH, AutoForeignSubtitles.normalizeLanguage(it))
        }
        Assert.assertNull(AutoForeignSubtitles.normalizeLanguage("und"))
        Assert.assertNull(AutoForeignSubtitles.normalizeLanguage(null))
    }
}

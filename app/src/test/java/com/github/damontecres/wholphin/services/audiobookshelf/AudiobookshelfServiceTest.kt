package com.github.damontecres.wholphin.services.audiobookshelf

import io.mockk.mockk
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.junit.Assert
import org.junit.Test

class AudiobookshelfServiceTest {
    private val json = Json { ignoreUnknownKeys = true }
    private val config = AbsConfig("http://192.168.1.201:13378", "tok123", "roman")
    private val service = AudiobookshelfService(mockk<OkHttpClient>(), mockk(relaxed = true))

    @Test
    fun normalizeBaseUrl() {
        Assert.assertEquals(
            "http://192.168.1.201:13378/",
            AudiobookshelfService.normalizeBaseUrl("192.168.1.201:13378").toString(),
        )
        Assert.assertEquals(
            "https://abs.example/",
            AudiobookshelfService.normalizeBaseUrl(" https://abs.example/ ").toString(),
        )
    }

    @Test
    fun coverUrlIncludesToken() {
        val url = service.coverUrl(config, "item1", 300)!!
        Assert.assertTrue(url.startsWith("http://192.168.1.201:13378/api/items/item1/cover"))
        Assert.assertTrue("token=tok123" in url)
        Assert.assertTrue("width=300" in url)
    }

    @Test
    fun coverUrlNullWhenNotConfigured() {
        Assert.assertNull(service.coverUrl(AbsConfig(), "item1"))
    }

    @Test
    fun trackUrlResolvesRelativeContentUrl() {
        val url = service.trackUrl(config, AbsAudioTrack(contentUrl = "/public/session/abc/track/1"))
        Assert.assertEquals("http://192.168.1.201:13378/public/session/abc/track/1?token=tok123", url)
    }

    @Test
    fun parsesPodcastItemWithUnknownFields() {
        val item =
            json.decodeFromString<AbsLibraryItem>(
                """
                {"id":"li_1","someNewField":42,"mediaType":"podcast",
                 "media":{"metadata":{"title":"Radiolab","author":"WNYC"},
                   "episodes":[{"id":"ep_1","title":"Ep 1","duration":1800.5,
                     "userMediaProgress":{"currentTime":600.0,"progress":0.33,"isFinished":false}}]}}
                """.trimIndent(),
            )
        Assert.assertEquals("Radiolab", item.title)
        Assert.assertEquals("WNYC", item.author)
        val ep = item.media!!.episodes.single()
        Assert.assertEquals(600.0, ep.userMediaProgress!!.currentTime, 0.0)
    }

    @Test
    fun parsesPlaySession() {
        val session =
            json.decodeFromString<AbsPlaySession>(
                """
                {"id":"s1","currentTime":12.5,"duration":3000.0,
                 "audioTracks":[{"index":1,"startOffset":0,"duration":3000,"contentUrl":"/public/session/s1/track/1","mimeType":"audio/mpeg"}]}
                """.trimIndent(),
            )
        Assert.assertEquals(12.5, session.currentTime, 0.0)
        Assert.assertEquals("/public/session/s1/track/1", session.audioTracks.single().contentUrl)
    }
}

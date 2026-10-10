package com.github.damontecres.wholphin.services.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaXmlTest {
    @Test
    fun formatTime() {
        assertEquals("0:00:00", DlnaXml.formatTime(0))
        assertEquals("0:00:00", DlnaXml.formatTime(-5000))
        assertEquals("0:03:25", DlnaXml.formatTime(205_999))
        assertEquals("1:02:03", DlnaXml.formatTime(3_723_000))
        assertEquals("12:00:00", DlnaXml.formatTime(12 * 3_600_000L))
    }

    @Test
    fun parseTime() {
        assertEquals(205_000L, DlnaXml.parseTime("0:03:25"))
        assertEquals(205_000L, DlnaXml.parseTime("00:03:25"))
        assertEquals(3_723_500L, DlnaXml.parseTime("1:02:03.500"))
        assertEquals(3_723_500L, DlnaXml.parseTime("1:02:03.5"))
        assertEquals(3_723_250L, DlnaXml.parseTime("1:02:03.1/4"))
        assertEquals(65_000L, DlnaXml.parseTime("01:05"))
        assertEquals(42_000L, DlnaXml.parseTime("42"))
        assertEquals(0L, DlnaXml.parseTime("+0:00:00"))
        assertNull(DlnaXml.parseTime("NOT_IMPLEMENTED"))
        assertNull(DlnaXml.parseTime(""))
        assertNull(DlnaXml.parseTime(null))
        assertNull(DlnaXml.parseTime("1:2:3:4"))
    }

    @Test
    fun formatAndParseRoundTrip() {
        listOf(0L, 1_000L, 59_000L, 61_000L, 3_599_000L, 3_600_000L, 36_061_000L).forEach {
            assertEquals(it, DlnaXml.parseTime(DlnaXml.formatTime(it)))
        }
    }

    @Test
    fun escape() {
        assertEquals("a &amp; b &lt;c&gt; &quot;d&quot; &apos;e&apos;", DlnaXml.escape("a & b <c> \"d\" 'e'"))
    }

    @Test
    fun parseSoapRequest_setAvTransportUri() {
        val didl =
            """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
                """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="1" parentID="0" restricted="1">""" +
                """<dc:title>Song &amp; Dance</dc:title><upnp:artist>Artist</upnp:artist></item></DIDL-Lite>"""
        val xml =
            """
            <?xml version="1.0" encoding="utf-8"?>
            <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
              <s:Body>
                <u:SetAVTransportURI xmlns:u="urn:schemas-upnp-org:service:AVTransport:1">
                  <InstanceID>0</InstanceID>
                  <CurrentURI>http://192.168.1.10:57645/proxy/track.flac?a=1&amp;b=2</CurrentURI>
                  <CurrentURIMetaData>${DlnaXml.escape(didl)}</CurrentURIMetaData>
                </u:SetAVTransportURI>
              </s:Body>
            </s:Envelope>
            """.trimIndent()
        val request = DlnaXml.parseSoapRequest(xml)
        assertNotNull(request)
        request!!
        assertEquals("SetAVTransportURI", request.action)
        assertEquals("urn:schemas-upnp-org:service:AVTransport:1", request.serviceType)
        assertEquals("0", request.arguments["InstanceID"])
        assertEquals("http://192.168.1.10:57645/proxy/track.flac?a=1&b=2", request.arguments["CurrentURI"])
        assertEquals(didl, request.arguments["CurrentURIMetaData"])
        val metadata = DlnaXml.parseDidlLite(request.arguments["CurrentURIMetaData"])
        assertEquals("Song & Dance", metadata?.title)
        assertEquals("Artist", metadata?.artist)
    }

    @Test
    fun parseSoapRequest_invalid() {
        assertNull(DlnaXml.parseSoapRequest("not xml"))
        assertNull(DlnaXml.parseSoapRequest("<a><b/></a>"))
        assertNull(DlnaXml.parseSoapRequest(""))
    }

    @Test
    fun buildSoapResponse_isParseable() {
        val response =
            DlnaXml.buildSoapResponse(
                "urn:schemas-upnp-org:service:AVTransport:1",
                "GetTransportInfo",
                listOf("CurrentTransportState" to "PLAYING", "CurrentTransportStatus" to "OK", "CurrentSpeed" to "1"),
            )
        assertTrue(response.contains("<u:GetTransportInfoResponse xmlns:u=\"urn:schemas-upnp-org:service:AVTransport:1\">"))
        assertTrue(response.contains("<CurrentTransportState>PLAYING</CurrentTransportState>"))
        // The response parses as a SOAP envelope with the output arguments
        val parsed = DlnaXml.parseSoapRequest(response)
        assertEquals("GetTransportInfoResponse", parsed?.action)
        assertEquals("PLAYING", parsed?.arguments?.get("CurrentTransportState"))
    }

    @Test
    fun buildSoapResponse_escapesValues() {
        val response =
            DlnaXml.buildSoapResponse(
                "urn:schemas-upnp-org:service:AVTransport:1",
                "GetMediaInfo",
                listOf("CurrentURI" to "http://x/a?b=1&c=2", "CurrentURIMetaData" to "<DIDL-Lite/>"),
            )
        assertTrue(response.contains("<CurrentURI>http://x/a?b=1&amp;c=2</CurrentURI>"))
        assertTrue(response.contains("<CurrentURIMetaData>&lt;DIDL-Lite/&gt;</CurrentURIMetaData>"))
        val parsed = DlnaXml.parseSoapRequest(response)
        assertEquals("<DIDL-Lite/>", parsed?.arguments?.get("CurrentURIMetaData"))
    }

    @Test
    fun buildSoapFault() {
        val fault = DlnaXml.buildSoapFault(701, "Transition not available")
        assertTrue(fault.contains("<errorCode>701</errorCode>"))
        assertTrue(fault.contains("<errorDescription>Transition not available</errorDescription>"))
        assertTrue(fault.contains("<faultstring>UPnPError</faultstring>"))
    }

    private val bubbleDidl =
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
            """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/" xmlns:dlna="urn:schemas-dlna-org:metadata-1-0/">""" +
            """<item id="tidal/123" parentID="tidal" restricted="1">""" +
            """<dc:title>Bohemian Rhapsody</dc:title>""" +
            """<dc:creator>Queen</dc:creator>""" +
            """<upnp:artist role="AlbumArtist">Queen (Album)</upnp:artist>""" +
            """<upnp:artist role="Performer">Queen</upnp:artist>""" +
            """<upnp:album>A Night at the Opera</upnp:album>""" +
            """<upnp:albumArtURI dlna:profileID="JPEG_TN">http://192.168.1.10:57645/art/123.jpg?size=640&amp;q=1</upnp:albumArtURI>""" +
            """<upnp:class>object.item.audioItem.musicTrack</upnp:class>""" +
            """<res duration="0:05:55.000" protocolInfo="http-get:*:audio/flac:DLNA.ORG_OP=01">""" +
            """http://192.168.1.10:57645/tidal/123.flac</res>""" +
            """</item></DIDL-Lite>"""

    @Test
    fun parseDidlLite_full() {
        val m = DlnaXml.parseDidlLite(bubbleDidl)!!
        assertEquals("Bohemian Rhapsody", m.title)
        assertEquals("Queen", m.artist)
        assertEquals("A Night at the Opera", m.album)
        assertEquals("http://192.168.1.10:57645/art/123.jpg?size=640&q=1", m.albumArtUri)
        assertEquals(355_000L, m.durationMs)
        assertEquals("http-get:*:audio/flac:DLNA.ORG_OP=01", m.protocolInfo)
        assertEquals("http://192.168.1.10:57645/tidal/123.flac", m.resUri)
        assertEquals("object.item.audioItem.musicTrack", m.upnpClass)
        assertEquals("audio/flac", DlnaXml.mimeTypeFromProtocolInfo(m.protocolInfo))
    }

    @Test
    fun parseDidlLite_creatorOnly() {
        val didl =
            """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/">""" +
                """<item id="1" parentID="0" restricted="1"><dc:title>T</dc:title><dc:creator>C</dc:creator></item></DIDL-Lite>"""
        val m = DlnaXml.parseDidlLite(didl)!!
        assertEquals("T", m.title)
        assertEquals("C", m.artist)
        assertNull(m.album)
        assertNull(m.albumArtUri)
        assertNull(m.durationMs)
    }

    @Test
    fun parseDidlLite_malformedFallsBackToLenient() {
        // An unescaped '&' in the URL makes this invalid XML
        val didl =
            """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
                """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="1" parentID="0" restricted="1">""" +
                """<dc:title>Title</dc:title><upnp:artist>Artist</upnp:artist><upnp:album>Album</upnp:album>""" +
                """<upnp:albumArtURI>http://h/art.jpg?a=1&b=2</upnp:albumArtURI>""" +
                """<res duration="0:03:00" protocolInfo="http-get:*:audio/mpeg:*">http://h/song.mp3?x=1&y=2</res>""" +
                """</item></DIDL-Lite>"""
        val m = DlnaXml.parseDidlLite(didl)!!
        assertEquals("Title", m.title)
        assertEquals("Artist", m.artist)
        assertEquals("Album", m.album)
        assertEquals("http://h/art.jpg?a=1&b=2", m.albumArtUri)
        assertEquals(180_000L, m.durationMs)
        assertEquals("http-get:*:audio/mpeg:*", m.protocolInfo)
        assertEquals("http://h/song.mp3?x=1&y=2", m.resUri)
    }

    @Test
    fun parseDidlLite_empty() {
        assertNull(DlnaXml.parseDidlLite(null))
        assertNull(DlnaXml.parseDidlLite(""))
        assertNull(DlnaXml.parseDidlLite("NOT_IMPLEMENTED"))
        assertNull(DlnaXml.parseDidlLite("garbage"))
    }

    @Test
    fun buildDidlLite_roundTrip() {
        val metadata =
            DidlMetadata(
                title = "A & B",
                artist = "Artist",
                album = "Album",
                albumArtUri = "http://h/a.jpg?x=1&y=2",
                durationMs = 61_000,
                protocolInfo = "http-get:*:audio/flac:*",
            )
        val xml = DlnaXml.buildDidlLite("http://h/s.flac?a=1&b=2", metadata)
        val parsed = DlnaXml.parseDidlLite(xml)!!
        assertEquals("A & B", parsed.title)
        assertEquals("Artist", parsed.artist)
        assertEquals("Album", parsed.album)
        assertEquals("http://h/a.jpg?x=1&y=2", parsed.albumArtUri)
        assertEquals(61_000L, parsed.durationMs)
        assertEquals("http://h/s.flac?a=1&b=2", parsed.resUri)
    }

    @Test
    fun mimeTypeFromProtocolInfo() {
        assertEquals("audio/mp4", DlnaXml.mimeTypeFromProtocolInfo("http-get:*:audio/mp4:*"))
        assertNull(DlnaXml.mimeTypeFromProtocolInfo("http-get:*:*:*"))
        assertNull(DlnaXml.mimeTypeFromProtocolInfo(null))
    }
}

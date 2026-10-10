package com.github.damontecres.wholphin.services.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaSoapHandlerTest {
    private class FakeControl : DlnaRendererControl {
        var state = RendererSnapshot()
        val calls = mutableListOf<String>()
        var playError: UpnpException? = null

        override fun snapshot() = state

        override fun setUri(
            uri: String,
            metadataXml: String?,
            metadata: DidlMetadata?,
        ) {
            calls.add("setUri $uri ${metadata?.title}")
            state = state.copy(uri = uri, metadataXml = metadataXml, metadata = metadata, transportState = TransportStates.STOPPED)
        }

        override fun setNextUri(
            uri: String?,
            metadataXml: String?,
            metadata: DidlMetadata?,
        ) {
            calls.add("setNextUri $uri")
            state = state.copy(nextUri = uri, nextMetadataXml = metadataXml)
        }

        override fun play() {
            playError?.let { throw it }
            calls.add("play")
            state = state.copy(transportState = TransportStates.PLAYING)
        }

        override fun pause() {
            calls.add("pause")
            state = state.copy(transportState = TransportStates.PAUSED)
        }

        override fun stop() {
            calls.add("stop")
            state = state.copy(transportState = TransportStates.STOPPED)
        }

        override fun seek(positionMs: Long) {
            calls.add("seek $positionMs")
            state = state.copy(positionMs = positionMs)
        }

        override fun setVolume(volume: Int) {
            calls.add("volume $volume")
            state = state.copy(volume = volume)
        }

        override fun setMute(mute: Boolean) {
            calls.add("mute $mute")
            state = state.copy(muted = mute)
        }
    }

    private val control = FakeControl()
    private val handler = DlnaSoapHandler(control)

    private fun soap(
        service: DlnaService,
        action: String,
        vararg args: Pair<String, String>,
    ): String =
        buildString {
            append("""<?xml version="1.0"?><s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/"><s:Body>""")
            append("""<u:$action xmlns:u="${service.serviceType}">""")
            args.forEach { (k, v) -> append("<$k>${DlnaXml.escape(v)}</$k>") }
            append("</u:$action></s:Body></s:Envelope>")
        }

    private fun call(
        service: DlnaService,
        action: String,
        vararg args: Pair<String, String>,
    ): Pair<SoapResult, Map<String, String>> {
        val request = DlnaXml.parseSoapRequest(soap(service, action, *args))!!
        val result = handler.handle(service, request)
        val output = DlnaXml.parseSoapRequest(result.body)?.arguments.orEmpty()
        return result to output
    }

    private fun errorCode(result: SoapResult): Int? =
        Regex("<errorCode>(\\d+)</errorCode>")
            .find(result.body)
            ?.groupValues
            ?.get(1)
            ?.toInt()

    private val didl =
        """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" """ +
            """xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/"><item id="1" parentID="0" restricted="1">""" +
            """<dc:title>Song</dc:title><upnp:artist>Artist</upnp:artist>""" +
            """<res duration="0:04:00" protocolInfo="http-get:*:audio/flac:*">http://phone/song.flac</res></item></DIDL-Lite>"""

    @Test
    fun setUriPlayPauseStop() {
        val (setResult, _) =
            call(
                DlnaService.AV_TRANSPORT,
                "SetAVTransportURI",
                "InstanceID" to "0",
                "CurrentURI" to "http://phone/song.flac",
                "CurrentURIMetaData" to didl,
            )
        assertEquals(200, setResult.httpStatus)
        assertTrue(setResult.body.contains("SetAVTransportURIResponse"))
        assertEquals("setUri http://phone/song.flac Song", control.calls.last())

        assertEquals(200, call(DlnaService.AV_TRANSPORT, "Play", "InstanceID" to "0", "Speed" to "1").first.httpStatus)
        assertEquals("play", control.calls.last())

        val (_, info) = call(DlnaService.AV_TRANSPORT, "GetTransportInfo", "InstanceID" to "0")
        assertEquals("PLAYING", info["CurrentTransportState"])
        assertEquals("OK", info["CurrentTransportStatus"])
        assertEquals("1", info["CurrentSpeed"])

        call(DlnaService.AV_TRANSPORT, "Pause", "InstanceID" to "0")
        assertEquals("pause", control.calls.last())
        call(DlnaService.AV_TRANSPORT, "Stop", "InstanceID" to "0")
        assertEquals("stop", control.calls.last())
    }

    @Test
    fun playWithoutMedia() {
        val (result, _) = call(DlnaService.AV_TRANSPORT, "Play", "InstanceID" to "0", "Speed" to "1")
        assertEquals(500, result.httpStatus)
        assertEquals(701, errorCode(result))
        assertFalse(control.calls.contains("play"))
    }

    @Test
    fun playRefusedByControl() {
        control.state = RendererSnapshot(uri = "http://phone/a.mp3")
        control.playError = UpnpException(701, "A video is playing")
        val (result, _) = call(DlnaService.AV_TRANSPORT, "Play", "InstanceID" to "0", "Speed" to "1")
        assertEquals(500, result.httpStatus)
        assertEquals(701, errorCode(result))
    }

    @Test
    fun seek() {
        control.state = RendererSnapshot(uri = "http://phone/a.mp3", transportState = TransportStates.PLAYING)
        val (result, _) =
            call(DlnaService.AV_TRANSPORT, "Seek", "InstanceID" to "0", "Unit" to "REL_TIME", "Target" to "0:01:30")
        assertEquals(200, result.httpStatus)
        assertEquals("seek 90000", control.calls.last())

        val (bad, _) = call(DlnaService.AV_TRANSPORT, "Seek", "InstanceID" to "0", "Unit" to "TRACK_NR", "Target" to "2")
        assertEquals(710, errorCode(bad))
        val (badTarget, _) =
            call(DlnaService.AV_TRANSPORT, "Seek", "InstanceID" to "0", "Unit" to "REL_TIME", "Target" to "abc")
        assertEquals(711, errorCode(badTarget))
    }

    @Test
    fun positionInfo() {
        control.state =
            RendererSnapshot(
                uri = "http://phone/a.mp3",
                metadata = DidlMetadata(title = "A", durationMs = 240_000),
                transportState = TransportStates.PLAYING,
                positionMs = 65_400,
            )
        val (result, out) = call(DlnaService.AV_TRANSPORT, "GetPositionInfo", "InstanceID" to "0")
        assertEquals(200, result.httpStatus)
        assertEquals("1", out["Track"])
        assertEquals("0:04:00", out["TrackDuration"])
        assertEquals("0:01:05", out["RelTime"])
        assertEquals("0:01:05", out["AbsTime"])
        assertEquals("http://phone/a.mp3", out["TrackURI"])
        // No metadata XML from the phone: generated DIDL-Lite
        assertEquals("A", DlnaXml.parseDidlLite(out["TrackMetaData"])?.title)
    }

    @Test
    fun positionInfoNoMedia() {
        val (_, out) = call(DlnaService.AV_TRANSPORT, "GetPositionInfo", "InstanceID" to "0")
        assertEquals("0", out["Track"])
        assertEquals("0:00:00", out["TrackDuration"])
        assertEquals("", out["TrackURI"])
    }

    @Test
    fun mediaInfo() {
        call(
            DlnaService.AV_TRANSPORT,
            "SetAVTransportURI",
            "InstanceID" to "0",
            "CurrentURI" to "http://phone/song.flac",
            "CurrentURIMetaData" to didl,
        )
        call(
            DlnaService.AV_TRANSPORT,
            "SetNextAVTransportURI",
            "InstanceID" to "0",
            "NextURI" to "http://phone/2.flac",
            "NextURIMetaData" to "",
        )
        assertEquals("setNextUri http://phone/2.flac", control.calls.last())
        val (_, out) = call(DlnaService.AV_TRANSPORT, "GetMediaInfo", "InstanceID" to "0")
        assertEquals("1", out["NrTracks"])
        assertEquals("0:04:00", out["MediaDuration"])
        assertEquals("http://phone/song.flac", out["CurrentURI"])
        assertEquals(didl, out["CurrentURIMetaData"])
        assertEquals("http://phone/2.flac", out["NextURI"])
        assertEquals("NETWORK", out["PlayMedium"])
    }

    @Test
    fun invalidInstanceAndAction() {
        val (badInstance, _) = call(DlnaService.AV_TRANSPORT, "GetTransportInfo", "InstanceID" to "3")
        assertEquals(718, errorCode(badInstance))
        val (badAction, _) = call(DlnaService.AV_TRANSPORT, "Record", "InstanceID" to "0")
        assertEquals(401, errorCode(badAction))
        val (missingArg, _) = call(DlnaService.AV_TRANSPORT, "SetAVTransportURI", "InstanceID" to "0")
        assertEquals(402, errorCode(missingArg))
    }

    @Test
    fun volumeAndMute() {
        assertEquals(
            200,
            call(
                DlnaService.RENDERING_CONTROL,
                "SetVolume",
                "InstanceID" to "0",
                "Channel" to "Master",
                "DesiredVolume" to "35",
            ).first.httpStatus,
        )
        assertEquals("volume 35", control.calls.last())
        assertEquals(
            "35",
            call(DlnaService.RENDERING_CONTROL, "GetVolume", "InstanceID" to "0", "Channel" to "Master").second["CurrentVolume"],
        )
        call(DlnaService.RENDERING_CONTROL, "SetVolume", "InstanceID" to "0", "Channel" to "Master", "DesiredVolume" to "150")
        assertEquals("volume 100", control.calls.last())

        call(DlnaService.RENDERING_CONTROL, "SetMute", "InstanceID" to "0", "Channel" to "Master", "DesiredMute" to "1")
        assertEquals("mute true", control.calls.last())
        assertEquals("1", call(DlnaService.RENDERING_CONTROL, "GetMute", "InstanceID" to "0", "Channel" to "Master").second["CurrentMute"])
        call(DlnaService.RENDERING_CONTROL, "SetMute", "InstanceID" to "0", "Channel" to "Master", "DesiredMute" to "false")
        assertEquals("mute false", control.calls.last())

        val (bad, _) =
            call(
                DlnaService.RENDERING_CONTROL,
                "SetVolume",
                "InstanceID" to "0",
                "Channel" to "Master",
                "DesiredVolume" to "loud",
            )
        assertEquals(402, errorCode(bad))
    }

    @Test
    fun connectionManager() {
        val (_, protocol) = call(DlnaService.CONNECTION_MANAGER, "GetProtocolInfo")
        assertEquals("", protocol["Source"])
        val sink = protocol["Sink"]!!.split(',')
        listOf("audio/flac", "audio/x-flac", "audio/mpeg", "audio/mp4").forEach {
            assertTrue("$it in sink", sink.contains("http-get:*:$it:*"))
        }
        assertEquals("0", call(DlnaService.CONNECTION_MANAGER, "GetCurrentConnectionIDs").second["ConnectionIDs"])
        val (_, info) = call(DlnaService.CONNECTION_MANAGER, "GetCurrentConnectionInfo", "ConnectionID" to "0")
        assertEquals("Input", info["Direction"])
        assertEquals("OK", info["Status"])
        val (bad, _) = call(DlnaService.CONNECTION_MANAGER, "GetCurrentConnectionInfo", "ConnectionID" to "5")
        assertEquals(706, errorCode(bad))
    }

    @Test
    fun descriptionsAreWellFormed() {
        val device = DlnaDescriptions.deviceDescription("Wholphinix – Obývačka & co", "uuid:1234", "14")
        val doc = DlnaXml.parseXml(device)
        assertEquals("root", doc.documentElement.localName)
        assertTrue(device.contains("<friendlyName>Wholphinix – Obývačka &amp; co</friendlyName>"))
        assertTrue(device.contains(DlnaDescriptions.DEVICE_TYPE))
        DlnaService.entries.forEach {
            assertTrue(device.contains("<controlURL>${it.controlPath}</controlURL>"))
            assertEquals("scpd", DlnaXml.parseXml(DlnaDescriptions.scpdFor(it)).documentElement.localName)
        }
        assertEquals(DlnaService.AV_TRANSPORT, DlnaService.fromPath("/AVTransport/control"))
        assertNull(DlnaService.fromPath("/other"))
    }

    @Test
    fun lastChangeEvents() {
        val snapshot =
            RendererSnapshot(
                transportState = TransportStates.PLAYING,
                uri = "http://phone/a.flac?x=1&y=2",
                metadata = DidlMetadata(title = "T"),
                volume = 42,
            )
        val avt = DlnaDescriptions.eventBody(DlnaService.AV_TRANSPORT, snapshot)
        val doc = DlnaXml.parseXml(avt)
        val lastChange = doc.getElementsByTagName("LastChange").item(0).textContent
        // The LastChange value is itself XML
        val event = DlnaXml.parseXml(lastChange)
        assertEquals("Event", event.documentElement.localName)
        assertTrue(lastChange.contains("<TransportState val=\"PLAYING\"/>"))
        assertTrue(lastChange.contains("http://phone/a.flac?x=1&amp;y=2"))

        val rcs = DlnaDescriptions.eventBody(DlnaService.RENDERING_CONTROL, snapshot)
        val rcsChange =
            DlnaXml
                .parseXml(rcs)
                .getElementsByTagName("LastChange")
                .item(0)
                .textContent
        assertTrue(rcsChange.contains("<Volume channel=\"Master\" val=\"42\"/>"))
    }
}

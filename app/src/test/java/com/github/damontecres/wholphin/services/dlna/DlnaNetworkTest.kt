package com.github.damontecres.wholphin.services.dlna

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DlnaNetworkTest {
    private val udn = "uuid:11111111-2222-3333-4444-555555555555"

    @Test
    fun parseMSearch() {
        val text =
            "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n" +
                "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n\r\n"
        val search = SsdpServer.parseMSearch(text)
        assertNotNull(search)
        assertEquals("urn:schemas-upnp-org:device:MediaRenderer:1", search!!.st)
        assertEquals(2, search.mx)

        assertNull(SsdpServer.parseMSearch("NOTIFY * HTTP/1.1\r\nNT: upnp:rootdevice\r\n\r\n"))
        assertNull(SsdpServer.parseMSearch("M-SEARCH * HTTP/1.1\r\nST: ssdp:all\r\n\r\n"))
    }

    @Test
    fun searchTargets() {
        val all = SsdpServer.searchResponseTargets("ssdp:all", udn)
        assertTrue(all.contains("upnp:rootdevice"))
        assertTrue(all.contains(udn))
        assertTrue(all.contains(DlnaDescriptions.DEVICE_TYPE))
        assertTrue(all.contains(DlnaService.AV_TRANSPORT.serviceType))
        assertEquals(listOf(DlnaDescriptions.DEVICE_TYPE), SsdpServer.searchResponseTargets(DlnaDescriptions.DEVICE_TYPE, udn))
        assertEquals(listOf(udn), SsdpServer.searchResponseTargets(udn, udn))
        assertTrue(SsdpServer.searchResponseTargets("urn:schemas-upnp-org:device:MediaServer:1", udn).isEmpty())
    }

    @Test
    fun ssdpMessages() {
        val notify =
            SsdpServer.buildNotify(
                "upnp:rootdevice",
                "$udn::upnp:rootdevice",
                SsdpServer.NTS_ALIVE,
                "http://1.2.3.4:49494/description.xml",
            )
        assertTrue(notify.startsWith("NOTIFY * HTTP/1.1\r\n"))
        assertTrue(notify.contains("LOCATION: http://1.2.3.4:49494/description.xml\r\n"))
        assertTrue(notify.contains("NTS: ssdp:alive\r\n"))
        assertTrue(notify.endsWith("\r\n\r\n"))

        val response = SsdpServer.buildSearchResponse("upnp:rootdevice", "$udn::upnp:rootdevice", "http://1.2.3.4:49494/description.xml")
        assertTrue(response.startsWith("HTTP/1.1 200 OK\r\n"))
        assertTrue(response.contains("ST: upnp:rootdevice\r\n"))
        assertTrue(response.contains("USN: $udn::upnp:rootdevice\r\n"))
    }

    @Test
    fun readHttpRequest() {
        val body = "<s:Envelope/>"
        val raw =
            "POST /AVTransport/control HTTP/1.1\r\nHost: 1.2.3.4\r\nSOAPACTION: \"urn:x#Play\"\r\n" +
                "Content-Length: ${body.length}\r\n\r\n$body"
        val request = DlnaHttpServer.readRequest(raw.byteInputStream(), "5.6.7.8")!!
        assertEquals("POST", request.method)
        assertEquals("/AVTransport/control", request.path)
        assertEquals("\"urn:x#Play\"", request.header("SoapAction"))
        assertEquals(body, request.body)
        assertEquals("5.6.7.8", request.remoteAddress)
    }

    @Test
    fun readChunkedHttpRequest() {
        val raw = "POST /x HTTP/1.1\r\nTransfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n6\r\n world\r\n0\r\n\r\n"
        val request = DlnaHttpServer.readRequest(raw.byteInputStream(), null)!!
        assertEquals("hello world", request.body)
    }

    @Test
    fun genaSubscribe() {
        val eventing = DlnaEventing { RendererSnapshot() }
        val subscribe =
            HttpRequest(
                method = "SUBSCRIBE",
                path = "/AVTransport/event",
                headers = mapOf("callback" to "<http://127.0.0.1:1/cb>", "nt" to "upnp:event", "timeout" to "Second-300"),
                body = "",
                remoteAddress = null,
            )
        val response = eventing.handle(DlnaService.AV_TRANSPORT, subscribe)
        assertEquals(200, response.status)
        val sid = response.headers.first { it.first == "SID" }.second
        assertTrue(sid.startsWith("uuid:"))
        assertEquals("Second-300", response.headers.first { it.first == "TIMEOUT" }.second)

        // Renewal
        val renew =
            eventing.handle(
                DlnaService.AV_TRANSPORT,
                HttpRequest("SUBSCRIBE", "/AVTransport/event", mapOf("sid" to sid, "timeout" to "Second-600"), "", null),
            )
        assertEquals(200, renew.status)
        assertEquals(sid, renew.headers.first { it.first == "SID" }.second)

        val unsubscribe =
            eventing.handle(DlnaService.AV_TRANSPORT, HttpRequest("UNSUBSCRIBE", "/AVTransport/event", mapOf("sid" to sid), "", null))
        assertEquals(200, unsubscribe.status)
        val again =
            eventing.handle(DlnaService.AV_TRANSPORT, HttpRequest("UNSUBSCRIBE", "/AVTransport/event", mapOf("sid" to sid), "", null))
        assertEquals(412, again.status)

        val noCallback =
            eventing.handle(DlnaService.AV_TRANSPORT, HttpRequest("SUBSCRIBE", "/AVTransport/event", mapOf("nt" to "upnp:event"), "", null))
        assertEquals(412, noCallback.status)
        eventing.clear()
    }

    @Test
    fun genaHeaders() {
        assertEquals(1800, DlnaEventing.parseTimeout(null))
        assertEquals(1800, DlnaEventing.parseTimeout("infinite"))
        assertEquals(300, DlnaEventing.parseTimeout("Second-300"))
        assertEquals(60, DlnaEventing.parseTimeout("Second-5"))
        assertEquals(
            listOf("http://192.168.1.5:5000/a", "http://192.168.1.5:5000/b"),
            DlnaEventing.parseCallbacks("<http://192.168.1.5:5000/a><http://192.168.1.5:5000/b>"),
        )
        assertTrue(DlnaEventing.parseCallbacks("<https://x/>").isEmpty())
    }
}

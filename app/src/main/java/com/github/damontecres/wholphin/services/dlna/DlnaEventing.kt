package com.github.damontecres.wholphin.services.dlna

import timber.log.Timber
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * GENA eventing: control points SUBSCRIBE to a service and get NOTIFY requests with the changed state variables
 */
class DlnaEventing(
    private val snapshot: () -> RendererSnapshot,
) {
    private class Subscription(
        val sid: String,
        val service: DlnaService,
        val callbacks: List<String>,
        @Volatile var expiresAt: Long,
        @Volatile var seq: Long = 0,
        @Volatile var lastBody: String? = null,
    )

    private val subscriptions = ConcurrentHashMap<String, Subscription>()
    private val sender = Executors.newSingleThreadExecutor { r -> Thread(r, "dlna-gena").apply { isDaemon = true } }

    fun handle(
        service: DlnaService,
        request: HttpRequest,
    ): HttpResponse {
        removeExpired()
        return when (request.method) {
            "SUBSCRIBE" -> subscribe(service, request)
            "UNSUBSCRIBE" -> unsubscribe(request)
            else -> HttpResponse(405, contentType = null)
        }
    }

    private fun subscribe(
        service: DlnaService,
        request: HttpRequest,
    ): HttpResponse {
        val timeoutSeconds = parseTimeout(request.header("TIMEOUT"))
        val existingSid = request.header("SID")
        if (existingSid != null) {
            // Renewal
            if (request.header("CALLBACK") != null || request.header("NT") != null) return HttpResponse(400, contentType = null)
            val sub = subscriptions[existingSid] ?: return HttpResponse(412, contentType = null)
            sub.expiresAt = System.currentTimeMillis() + timeoutSeconds * 1000L
            return HttpResponse(200, contentType = null, headers = subscriptionHeaders(sub.sid, timeoutSeconds))
        }
        if (request.header("NT") != "upnp:event") return HttpResponse(412, contentType = null)
        val callbacks = parseCallbacks(request.header("CALLBACK"))
        if (callbacks.isEmpty()) return HttpResponse(412, contentType = null)
        val sid = "uuid:${UUID.randomUUID()}"
        val sub = Subscription(sid, service, callbacks, System.currentTimeMillis() + timeoutSeconds * 1000L)
        subscriptions[sid] = sub
        Timber.d("DLNA: %s subscribed to %s", request.remoteAddress, service.path)
        // The initial event must come after the SUBSCRIBE response
        sender.execute {
            try {
                Thread.sleep(INITIAL_EVENT_DELAY_MS)
            } catch (_: InterruptedException) {
                return@execute
            }
            send(sub, force = true)
        }
        return HttpResponse(200, contentType = null, headers = subscriptionHeaders(sid, timeoutSeconds))
    }

    private fun unsubscribe(request: HttpRequest): HttpResponse {
        val sid = request.header("SID") ?: return HttpResponse(412, contentType = null)
        return if (subscriptions.remove(sid) != null) HttpResponse(200, contentType = null) else HttpResponse(412, contentType = null)
    }

    /** Send the current state to every subscriber whose state changed since the last event */
    fun notifyChanged() {
        if (subscriptions.isEmpty()) return
        sender.execute {
            removeExpired()
            subscriptions.values.forEach { send(it, force = false) }
        }
    }

    fun clear() {
        subscriptions.clear()
    }

    private fun removeExpired() {
        val now = System.currentTimeMillis()
        subscriptions.values.removeAll { it.expiresAt < now }
    }

    private fun send(
        sub: Subscription,
        force: Boolean,
    ) {
        val body = DlnaDescriptions.eventBody(sub.service, snapshot())
        if (!force && body == sub.lastBody) return
        sub.lastBody = body
        val seq = sub.seq
        sub.seq = if (seq >= UInt.MAX_VALUE.toLong()) 1 else seq + 1
        for (callback in sub.callbacks) {
            if (sendNotify(callback, sub.sid, seq, body)) break
        }
    }

    private fun sendNotify(
        callback: String,
        sid: String,
        seq: Long,
        body: String,
    ): Boolean =
        try {
            val uri = URI(callback)
            val host = uri.host ?: return false
            val port = if (uri.port > 0) uri.port else 80
            val path = (uri.rawPath?.takeIf { it.isNotEmpty() } ?: "/") + (uri.rawQuery?.let { "?$it" } ?: "")
            val bytes = body.toByteArray(Charsets.UTF_8)
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), NOTIFY_TIMEOUT_MS)
                socket.soTimeout = NOTIFY_TIMEOUT_MS
                val header =
                    buildString {
                        append("NOTIFY $path HTTP/1.1\r\n")
                        append("HOST: $host:$port\r\n")
                        append("CONTENT-TYPE: text/xml; charset=\"utf-8\"\r\n")
                        append("NT: upnp:event\r\n")
                        append("NTS: upnp:propchange\r\n")
                        append("SID: $sid\r\n")
                        append("SEQ: $seq\r\n")
                        append("CONTENT-LENGTH: ${bytes.size}\r\n")
                        append("Connection: close\r\n\r\n")
                    }
                val out = socket.getOutputStream()
                out.write(header.toByteArray(Charsets.ISO_8859_1))
                out.write(bytes)
                out.flush()
                // Read the status line so the event is not cut off by closing too early
                val status = socket.getInputStream().bufferedReader(Charsets.ISO_8859_1).readLine()
                status != null
            }
        } catch (ex: Exception) {
            Timber.d(ex, "DLNA: event to %s failed", callback)
            false
        }

    companion object {
        private const val DEFAULT_TIMEOUT_SECONDS = 1800
        private const val INITIAL_EVENT_DELAY_MS = 200L
        private const val NOTIFY_TIMEOUT_MS = 3000

        private fun subscriptionHeaders(
            sid: String,
            timeoutSeconds: Int,
        ) = listOf("SID" to sid, "TIMEOUT" to "Second-$timeoutSeconds")

        /** "Second-1800" or "infinite" -> seconds (capped) */
        fun parseTimeout(header: String?): Int {
            val value = header?.trim()?.lowercase() ?: return DEFAULT_TIMEOUT_SECONDS
            if (value == "infinite" || value == "second-infinite") return DEFAULT_TIMEOUT_SECONDS
            return value
                .removePrefix("second-")
                .toIntOrNull()
                ?.coerceIn(60, 86_400) ?: DEFAULT_TIMEOUT_SECONDS
        }

        /** "<http://a/b><http://c/d>" -> list of URLs */
        fun parseCallbacks(header: String?): List<String> =
            header
                ?.let { Regex("<([^>]+)>").findAll(it).map { m -> m.groupValues[1].trim() }.toList() }
                .orEmpty()
                .filter { it.startsWith("http://", ignoreCase = true) }
    }
}

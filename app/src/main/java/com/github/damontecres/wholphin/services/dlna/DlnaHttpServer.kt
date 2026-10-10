package com.github.damontecres.wholphin.services.dlna

import timber.log.Timber
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

data class HttpRequest(
    val method: String,
    val path: String,
    val headers: Map<String, String>,
    val body: String,
    val remoteAddress: String?,
) {
    /** Header value, case-insensitive */
    fun header(name: String): String? = headers[name.lowercase(Locale.ROOT)]
}

data class HttpResponse(
    val status: Int,
    val body: String = "",
    val contentType: String? = "text/xml; charset=\"utf-8\"",
    val headers: List<Pair<String, String>> = emptyList(),
) {
    companion object {
        fun notFound() = HttpResponse(404, contentType = null)

        fun reasonPhrase(status: Int): String =
            when (status) {
                200 -> "OK"
                400 -> "Bad Request"
                404 -> "Not Found"
                405 -> "Method Not Allowed"
                412 -> "Precondition Failed"
                500 -> "Internal Server Error"
                else -> "Status"
            }
    }
}

/**
 * A tiny HTTP/1.1 server (one request per connection) for the UPnP descriptions, SOAP control and GENA eventing
 */
class DlnaHttpServer(
    private val handler: (HttpRequest) -> HttpResponse,
) {
    private var serverSocket: ServerSocket? = null
    private var executor: ExecutorService? = null

    val port: Int get() = serverSocket?.localPort ?: 0

    /** Start listening on [preferredPort], or any free port if that is taken. Returns the port. */
    @Synchronized
    fun start(preferredPort: Int): Int {
        serverSocket?.let { return it.localPort }
        val socket =
            try {
                ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress(preferredPort))
                }
            } catch (ex: Exception) {
                Timber.w(ex, "DLNA: port %s is taken, using any free port", preferredPort)
                ServerSocket(0)
            }
        serverSocket = socket
        val pool = Executors.newFixedThreadPool(THREADS)
        executor = pool
        Thread({ acceptLoop(socket, pool) }, "dlna-http").apply {
            isDaemon = true
            start()
        }
        Timber.i("DLNA: HTTP server listening on port %s", socket.localPort)
        return socket.localPort
    }

    @Synchronized
    fun stop() {
        try {
            serverSocket?.close()
        } catch (_: Exception) {
        }
        serverSocket = null
        executor?.shutdownNow()
        executor = null
    }

    private fun acceptLoop(
        socket: ServerSocket,
        pool: ExecutorService,
    ) {
        while (!socket.isClosed) {
            try {
                val client = socket.accept()
                pool.execute { handleClient(client) }
            } catch (_: SocketException) {
                // Closed
            } catch (ex: Exception) {
                Timber.w(ex, "DLNA: accept failed")
            }
        }
    }

    private fun handleClient(client: Socket) {
        client.use { socket ->
            try {
                socket.soTimeout = SOCKET_TIMEOUT_MS
                val input = BufferedInputStream(socket.getInputStream())
                val request = readRequest(input, socket.inetAddress?.hostAddress) ?: return
                val response =
                    try {
                        handler(request)
                    } catch (ex: Exception) {
                        Timber.w(ex, "DLNA: error handling %s %s", request.method, request.path)
                        HttpResponse(500, contentType = null)
                    }
                writeResponse(socket, response, head = request.method == "HEAD")
            } catch (ex: Exception) {
                Timber.d(ex, "DLNA: connection error")
            }
        }
    }

    companion object {
        private const val THREADS = 6
        private const val SOCKET_TIMEOUT_MS = 10_000
        private const val MAX_BODY = 1_000_000
        private const val MAX_LINE = 16_384

        private fun readLine(input: InputStream): String? {
            val out = ByteArrayOutputStream()
            while (true) {
                val b = input.read()
                if (b == -1) return if (out.size() == 0) null else out.toString(Charsets.ISO_8859_1.name())
                if (b == '\n'.code) break
                if (b != '\r'.code) out.write(b)
                if (out.size() > MAX_LINE) return null
            }
            return out.toString(Charsets.ISO_8859_1.name())
        }

        /** Parse an HTTP request from [input], or null if it is malformed */
        fun readRequest(
            input: InputStream,
            remoteAddress: String?,
        ): HttpRequest? {
            val requestLine = readLine(input)?.trim() ?: return null
            val parts = requestLine.split(' ')
            if (parts.size < 2) return null
            val headers = LinkedHashMap<String, String>()
            while (true) {
                val line = readLine(input) ?: break
                if (line.isEmpty()) break
                val colon = line.indexOf(':')
                if (colon > 0) {
                    headers[line.substring(0, colon).trim().lowercase(Locale.ROOT)] = line.substring(colon + 1).trim()
                }
            }
            val body =
                if (headers["transfer-encoding"]?.contains("chunked", true) == true) {
                    readChunked(input)
                } else {
                    val length = headers["content-length"]?.toIntOrNull()?.coerceIn(0, MAX_BODY) ?: 0
                    val bytes = ByteArray(length)
                    var read = 0
                    while (read < length) {
                        val n = input.read(bytes, read, length - read)
                        if (n < 0) break
                        read += n
                    }
                    bytes.copyOf(read)
                }
            val path =
                parts[1]
                    .substringBefore(
                        '?',
                    ).let { if (it.startsWith("http")) "/" + it.substringAfter("://").substringAfter('/') else it }
            return HttpRequest(
                method = parts[0].uppercase(Locale.ROOT),
                path = path,
                headers = headers,
                body = String(body, Charsets.UTF_8),
                remoteAddress = remoteAddress,
            )
        }

        private fun readChunked(input: InputStream): ByteArray {
            val out = ByteArrayOutputStream()
            while (true) {
                val sizeLine = readLine(input) ?: break
                val size = sizeLine.substringBefore(';').trim().toIntOrNull(16) ?: break
                if (size == 0) {
                    readLine(input)
                    break
                }
                val buffer = ByteArray(size)
                var read = 0
                while (read < size) {
                    val n = input.read(buffer, read, size - read)
                    if (n < 0) break
                    read += n
                }
                out.write(buffer, 0, read)
                readLine(input)
                if (out.size() > MAX_BODY) break
            }
            return out.toByteArray()
        }

        private fun writeResponse(
            socket: Socket,
            response: HttpResponse,
            head: Boolean,
        ) {
            val body = response.body.toByteArray(Charsets.UTF_8)
            val sb = StringBuilder()
            sb
                .append("HTTP/1.1 ")
                .append(response.status)
                .append(' ')
                .append(HttpResponse.reasonPhrase(response.status))
                .append("\r\n")
            response.contentType?.let { sb.append("Content-Type: ").append(it).append("\r\n") }
            sb.append("Content-Length: ").append(body.size).append("\r\n")
            sb.append("Server: ").append(SERVER_HEADER).append("\r\n")
            sb.append("EXT:\r\n")
            response.headers.forEach { (name, value) ->
                sb
                    .append(name)
                    .append(": ")
                    .append(value)
                    .append("\r\n")
            }
            sb.append("Connection: close\r\n\r\n")
            val out = socket.getOutputStream()
            out.write(sb.toString().toByteArray(Charsets.ISO_8859_1))
            if (!head) out.write(body)
            out.flush()
        }

        val SERVER_HEADER: String by lazy {
            val release =
                try {
                    android.os.Build.VERSION.RELEASE
                } catch (_: Throwable) {
                    null
                }
            "Android/${release ?: "0"} UPnP/1.0 Wholphinix/1.0"
        }
    }
}

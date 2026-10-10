package com.github.damontecres.wholphin.services.dlna

import timber.log.Timber
import java.net.DatagramPacket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketAddress
import java.net.SocketException
import java.util.Locale
import kotlin.random.Random

/**
 * SSDP discovery: announces the renderer (NOTIFY ssdp:alive / ssdp:byebye) and answers M-SEARCH requests.
 *
 * [location] returns the description URL for the address a control point can reach, it is re-evaluated for every
 * message so a changed IP address is picked up.
 */
class SsdpServer(
    private val udn: String,
    private val location: () -> String?,
) {
    @Volatile
    private var socket: MulticastSocket? = null

    @Volatile
    private var running = false

    private var aliveThread: Thread? = null

    @Synchronized
    fun start() {
        if (running) return
        running = true
        val s =
            try {
                MulticastSocket(null as SocketAddress?).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(SSDP_PORT))
                    timeToLive = 4
                }
            } catch (ex: Exception) {
                Timber.e(ex, "DLNA: cannot open the SSDP socket")
                running = false
                return
            }
        joinGroups(s)
        socket = s
        Thread({ receiveLoop(s) }, "dlna-ssdp").apply {
            isDaemon = true
            start()
        }
        aliveThread =
            Thread({ aliveLoop() }, "dlna-ssdp-alive").apply {
                isDaemon = true
                start()
            }
    }

    @Synchronized
    fun stop() {
        if (!running) return
        running = false
        aliveThread?.interrupt()
        aliveThread = null
        socket?.let { s ->
            try {
                notifyMessages(NTS_BYEBYE).forEach { send(s, it, GROUP_ADDRESS) }
            } catch (ex: Exception) {
                Timber.d(ex, "DLNA: byebye failed")
            }
            try {
                s.close()
            } catch (_: Exception) {
            }
        }
        socket = null
    }

    private fun joinGroups(s: MulticastSocket) {
        val group = InetSocketAddress(InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT)
        var joined = false
        lanInterfaces().forEach { iface ->
            try {
                s.joinGroup(group, iface)
                joined = true
            } catch (ex: Exception) {
                Timber.d(ex, "DLNA: cannot join the SSDP group on %s", iface.name)
            }
        }
        if (!joined) {
            try {
                @Suppress("DEPRECATION")
                s.joinGroup(InetAddress.getByName(SSDP_ADDRESS))
            } catch (ex: Exception) {
                Timber.w(ex, "DLNA: cannot join the SSDP group")
            }
        }
    }

    private fun aliveLoop() {
        try {
            // A few announcements right away (UDP may get lost), then regularly
            repeat(3) {
                announce()
                Thread.sleep(1_000)
            }
            while (running) {
                Thread.sleep(ALIVE_INTERVAL_MS)
                announce()
            }
        } catch (_: InterruptedException) {
        }
    }

    private fun announce() {
        val s = socket ?: return
        try {
            notifyMessages(NTS_ALIVE).forEach { send(s, it, GROUP_ADDRESS) }
        } catch (ex: Exception) {
            Timber.d(ex, "DLNA: alive failed")
        }
    }

    private fun receiveLoop(s: MulticastSocket) {
        val buffer = ByteArray(4096)
        while (running && !s.isClosed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                s.receive(packet)
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val search = parseMSearch(text) ?: continue
                val targets = searchResponseTargets(search.st, udn)
                if (targets.isEmpty()) continue
                val reply = InetSocketAddress(packet.address, packet.port)
                // Spread the answers over a short time like the MX header asks, but keep it quick
                val delay = Random.nextLong(0, (search.mx.coerceIn(1, 3) * 100L) + 1)
                Thread {
                    try {
                        Thread.sleep(delay)
                        targets.forEach { st -> searchResponse(st)?.let { send(s, it, reply) } }
                    } catch (_: Exception) {
                    }
                }.apply {
                    isDaemon = true
                    start()
                }
            } catch (_: SocketException) {
                // Closed
            } catch (ex: Exception) {
                Timber.d(ex, "DLNA: SSDP receive failed")
            }
        }
    }

    private fun send(
        s: MulticastSocket,
        message: String,
        address: InetSocketAddress,
    ) {
        val bytes = message.toByteArray(Charsets.UTF_8)
        s.send(DatagramPacket(bytes, bytes.size, address))
    }

    private fun notifyMessages(nts: String): List<String> {
        val loc = location() ?: return emptyList()
        return notificationTypes(udn).map { (nt, usn) -> buildNotify(nt, usn, nts, loc) }
    }

    private fun searchResponse(st: String): String? {
        val loc = location() ?: return null
        val usn = notificationTypes(udn).firstOrNull { it.first == st }?.second ?: "$udn::$st"
        return buildSearchResponse(st, usn, loc)
    }

    data class MSearch(
        val st: String,
        val mx: Int,
    )

    companion object {
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        private const val MAX_AGE = 1800
        private const val ALIVE_INTERVAL_MS = 60_000L
        const val NTS_ALIVE = "ssdp:alive"
        const val NTS_BYEBYE = "ssdp:byebye"

        private val GROUP_ADDRESS by lazy { InetSocketAddress(InetAddress.getByName(SSDP_ADDRESS), SSDP_PORT) }

        /** The (NT, USN) pairs the device announces */
        fun notificationTypes(udn: String): List<Pair<String, String>> =
            buildList {
                add("upnp:rootdevice" to "$udn::upnp:rootdevice")
                add(udn to udn)
                add(DlnaDescriptions.DEVICE_TYPE to "$udn::${DlnaDescriptions.DEVICE_TYPE}")
                DlnaService.entries.forEach { add(it.serviceType to "$udn::${it.serviceType}") }
            }

        /** Parse an M-SEARCH request, or null if it is something else */
        fun parseMSearch(text: String): MSearch? {
            val lines = text.split("\r\n", "\n")
            if (!lines.first().trim().startsWith("M-SEARCH", ignoreCase = true)) return null
            val headers =
                lines
                    .drop(1)
                    .mapNotNull { line ->
                        val colon = line.indexOf(':')
                        if (colon > 0) line.substring(0, colon).trim().uppercase(Locale.ROOT) to line.substring(colon + 1).trim() else null
                    }.toMap()
            if (headers["MAN"]?.contains("ssdp:discover", true) != true) return null
            val st = headers["ST"] ?: return null
            return MSearch(st = st, mx = headers["MX"]?.toIntOrNull() ?: 1)
        }

        /** The search targets to answer an M-SEARCH for [st] with */
        fun searchResponseTargets(
            st: String,
            udn: String,
        ): List<String> {
            val types = notificationTypes(udn).map { it.first }
            return when (st) {
                "ssdp:all" -> types
                in types -> listOf(st)
                else -> emptyList()
            }
        }

        fun buildNotify(
            nt: String,
            usn: String,
            nts: String,
            location: String,
        ): String =
            buildString {
                append("NOTIFY * HTTP/1.1\r\n")
                append("HOST: $SSDP_ADDRESS:$SSDP_PORT\r\n")
                append("CACHE-CONTROL: max-age=$MAX_AGE\r\n")
                append("LOCATION: $location\r\n")
                append("NT: $nt\r\n")
                append("NTS: $nts\r\n")
                append("SERVER: ${DlnaHttpServer.SERVER_HEADER}\r\n")
                append("USN: $usn\r\n")
                append("\r\n")
            }

        fun buildSearchResponse(
            st: String,
            usn: String,
            location: String,
        ): String =
            buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("CACHE-CONTROL: max-age=$MAX_AGE\r\n")
                append("DATE: ${httpDate()}\r\n")
                append("EXT:\r\n")
                append("LOCATION: $location\r\n")
                append("SERVER: ${DlnaHttpServer.SERVER_HEADER}\r\n")
                append("ST: $st\r\n")
                append("USN: $usn\r\n")
                append("Content-Length: 0\r\n")
                append("\r\n")
            }

        private fun httpDate(): String {
            val format = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US)
            format.timeZone = java.util.TimeZone.getTimeZone("GMT")
            return format.format(java.util.Date())
        }

        /** Network interfaces on the LAN (up, not loopback, with an IPv4 address) */
        fun lanInterfaces(): List<NetworkInterface> =
            try {
                NetworkInterface
                    .getNetworkInterfaces()
                    ?.toList()
                    .orEmpty()
                    .filter { iface ->
                        try {
                            iface.isUp && !iface.isLoopback && iface.supportsMulticast() &&
                                iface.inetAddresses.toList().any { it is Inet4Address }
                        } catch (_: Exception) {
                            false
                        }
                    }
            } catch (ex: Exception) {
                Timber.w(ex, "DLNA: cannot list network interfaces")
                emptyList()
            }

        /** The IPv4 address of the LAN, preferring ethernet and wifi over e.g. VPN interfaces */
        fun lanAddress(): Inet4Address? {
            val candidates =
                lanInterfaces().flatMap { iface ->
                    iface.inetAddresses
                        .toList()
                        .filterIsInstance<Inet4Address>()
                        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                        .map { iface.name to it }
                }
            return (
                candidates.firstOrNull { (name, addr) ->
                    addr.isSiteLocalAddress && (name.startsWith("eth") || name.startsWith("wlan"))
                } ?: candidates.firstOrNull { it.second.isSiteLocalAddress } ?: candidates.firstOrNull()
            )?.second
        }
    }
}

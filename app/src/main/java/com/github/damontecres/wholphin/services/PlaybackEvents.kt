package com.github.damontecres.wholphin.services

import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The last few things that happened when starting playback (decision, fallbacks, buffering), so they can be read
 * remotely through [DeviceStatusService] (jellyfin-mcp device_status) without adb.
 */
object PlaybackEvents {
    private const val MAX = 25
    private val events = ArrayDeque<String>()
    private val time = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun add(message: String) {
        Timber.i("PlaybackEvent: %s", message)
        synchronized(events) {
            events.addLast("${time.format(Date())} $message")
            while (events.size > MAX) events.removeFirst()
        }
    }

    fun all(): List<String> = synchronized(events) { events.toList() }
}

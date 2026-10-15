package com.github.damontecres.wholphin.services

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import timber.log.Timber
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Status of the TV box (RPi5) for Claude / Telka (#52): CPU load, SoC temperature, CPU frequency, throttling, RAM,
 * uptime and how the current video plays. Measured every [SAMPLE_INTERVAL], stored on the server every
 * [UPLOAD_INTERVAL] through [TelemetryService] with the last [MAX_SAMPLES] measurements, and right away when
 * jellyfin-mcp asks for it ([reportNow]).
 *
 * Everything is best effort: Android may not let an app read some of the files, the values are then missing.
 */
@Singleton
class DeviceStatusService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val telemetryService: TelemetryService,
        private val tvMessageService: TvMessageService,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        /** How the video on screen plays, set by the player */
        data class PlaybackSnapshot(
            val title: String?,
            val method: String,
            val video: String?,
            val droppedFrames: Int,
        )

        private class Sample(
            val time: Long,
            val cpu: Double?,
            val temp: Double?,
        )

        @Volatile
        var playback: PlaybackSnapshot? = null

        private val mutex = Mutex()
        private val samples = ArrayDeque<Sample>()
        private var previousTimes: Map<String, LongArray>? = null
        private var job: Job? = null
        private var lastUpload: TimeSource.Monotonic.ValueTimeMark? = null
        private var lastHeatWarning: TimeSource.Monotonic.ValueTimeMark? = null

        /** Starts measuring (once per process) */
        @Synchronized
        fun start() {
            if (job?.isActive == true) return
            job =
                scope.launch {
                    while (true) {
                        try {
                            val report = measure()
                            if (lastUpload?.let { it.elapsedNow() >= UPLOAD_INTERVAL } != false) {
                                upload(report)
                            }
                        } catch (ex: Exception) {
                            Timber.w(ex, "Device status failed")
                        }
                        delay(SAMPLE_INTERVAL)
                    }
                }
        }

        /** jellyfin-mcp asked for a fresh status (`device_status`) */
        fun reportNow() {
            scope.launch {
                try {
                    upload(measure())
                } catch (ex: Exception) {
                    Timber.w(ex, "Device status report failed")
                }
            }
        }

        private suspend fun upload(report: String) {
            lastUpload = TimeSource.Monotonic.markNow()
            telemetryService.put(mapOf(PREF_KEY to report))
        }

        /** One measurement, kept as a sample; returns the whole report as JSON */
        private suspend fun measure(): String =
            mutex.withLock {
                val now = System.currentTimeMillis()
                val cores = cpuLoad()
                val cpu = cores?.get("cpu")
                val coreLoads =
                    cores
                        ?.filterKeys { it != "cpu" }
                        ?.toSortedMap(compareBy { it.removePrefix("cpu").toIntOrNull() ?: 0 })
                        ?.values
                        ?.toList()
                val temp = temperature()
                val freqs = cpuFiles("cpufreq/scaling_cur_freq").mapNotNull { it.toLongOrNull() }
                val maxFreq = cpuFiles("cpufreq/cpuinfo_max_freq").mapNotNull { it.toLongOrNull() }.maxOrNull()
                val throttled = throttled(temp, freqs.maxOrNull(), maxFreq)
                val memory =
                    ActivityManager.MemoryInfo().also {
                        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
                    }
                samples.addLast(Sample(now, cpu, temp))
                while (samples.size > MAX_SAMPLES) samples.removeFirst()
                warnIfHot(temp)
                buildJsonObject {
                    put("time", now)
                    put("cpu_percent", cpu?.round1())
                    putJsonArray("cpu_cores") { coreLoads?.forEach { add(JsonPrimitive(it.round1())) } }
                    put("temp_c", temp?.round1())
                    put("freq_mhz", freqs.takeIf { it.isNotEmpty() }?.let { (it.average() / 1000).roundToInt() })
                    put("max_freq_mhz", maxFreq?.let { (it / 1000).toInt() })
                    put("throttled", throttled)
                    put("ram_free_mb", memory.availMem / MB)
                    put("ram_total_mb", memory.totalMem / MB)
                    put("uptime_min", SystemClock.elapsedRealtime() / 60_000)
                    playback?.let { p ->
                        putJsonObject("playback") {
                            put("title", p.title)
                            put("method", p.method)
                            put("video", p.video)
                            put("dropped_frames", p.droppedFrames)
                        }
                    }
                    putJsonArray("samples") {
                        samples.forEach { s ->
                            add(
                                JsonArray(
                                    listOf(
                                        JsonPrimitive(s.time),
                                        s.cpu?.let { JsonPrimitive(it.round1()) } ?: JsonNull,
                                        s.temp?.let { JsonPrimitive(it.round1()) } ?: JsonNull,
                                    ),
                                ),
                            )
                        }
                    }
                }.toString()
            }

        /**
         * Load per CPU line of /proc/stat since the previous call ("cpu" = all cores), in percent.
         * Null on the first call or when Android does not allow reading it; then /proc/loadavg is tried.
         */
        private fun cpuLoad(): Map<String, Double>? {
            val times =
                try {
                    File("/proc/stat")
                        .readLines()
                        .filter { it.startsWith("cpu") }
                        .associate { line ->
                            val parts = line.trim().split(Regex("\\s+"))
                            val values = parts.drop(1).take(8).map { it.toLongOrNull() ?: 0L }
                            // idle + iowait, total
                            parts[0] to longArrayOf(values.getOrElse(3) { 0 } + values.getOrElse(4) { 0 }, values.sum())
                        }
                } catch (ex: Exception) {
                    null
                }
            if (times == null) {
                return loadAverage()?.let { mapOf("cpu" to it) }
            }
            val previous = previousTimes
            previousTimes = times
            if (previous == null) return null
            return times
                .mapNotNull { (name, now) ->
                    val before = previous[name] ?: return@mapNotNull null
                    val total = now[1] - before[1]
                    val idle = now[0] - before[0]
                    if (total <= 0) null else name to (100.0 * (total - idle) / total).coerceIn(0.0, 100.0)
                }.toMap()
        }

        /** 1 minute load average as percent of all cores */
        private fun loadAverage(): Double? =
            try {
                val load =
                    File("/proc/loadavg")
                        .readText()
                        .split(" ")
                        .first()
                        .toDouble()
                (100.0 * load / Runtime.getRuntime().availableProcessors()).coerceIn(0.0, 100.0)
            } catch (ex: Exception) {
                null
            }

        /** SoC temperature in °C from the thermal zones (the CPU/SoC one if named) */
        private fun temperature(): Double? =
            try {
                val zones =
                    File("/sys/class/thermal")
                        .listFiles { f -> f.name.startsWith("thermal_zone") }
                        .orEmpty()
                        .sortedBy { it.name.removePrefix("thermal_zone").toIntOrNull() ?: 0 }
                val preferred =
                    zones.firstOrNull { zone ->
                        val type = runCatching { File(zone, "type").readText().lowercase() }.getOrDefault("")
                        "cpu" in type || "soc" in type
                    }
                (listOfNotNull(preferred) + zones).firstNotNullOfOrNull { zone ->
                    runCatching { File(zone, "temp").readText().trim().toDouble() }
                        .getOrNull()
                        ?.let { if (it > 1000) it / 1000 else it }
                        ?.takeIf { it in 1.0..150.0 }
                }
            } catch (ex: Exception) {
                null
            }

        private fun cpuFiles(path: String): List<String> =
            (0 until Runtime.getRuntime().availableProcessors()).mapNotNull { core ->
                runCatching { File("/sys/devices/system/cpu/cpu$core/$path").readText().trim() }.getOrNull()
            }

        /** Android's thermal status says it reduces performance, or the CPU runs well below its maximum while hot */
        private fun throttled(
            temp: Double?,
            freq: Long?,
            maxFreq: Long?,
        ): Boolean {
            val thermal =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
                    power.currentThermalStatus >= PowerManager.THERMAL_STATUS_MODERATE
                } else {
                    false
                }
            val capped = temp != null && temp >= HOT_C && freq != null && maxFreq != null && freq < maxFreq * 0.85
            return thermal || capped
        }

        /** Jean tells it on the TV when the RPi gets too hot, at most once an hour */
        private fun warnIfHot(temp: Double?) {
            if (temp == null || temp < HOT_C) return
            if (lastHeatWarning?.let { it.elapsedNow() < 1.hours } == true) return
            lastHeatWarning = TimeSource.Monotonic.markNow()
            Timber.i("RPi is hot: %.1f °C", temp)
            tvMessageService.show(
                title = "Jean",
                text = "RPi má ${temp.roundToInt()} °C a začína sa potiť 🥵 Chcelo by to viac vzduchu alebo chladič.",
                source = TvMessageSource.JEAN,
            )
        }

        private fun Double.round1() = (this * 10).roundToInt() / 10.0

        companion object {
            /** Read by jellyfin-mcp */
            const val PREF_KEY = "device_status"

            /** Sent by jellyfin-mcp (SendString) to ask for a fresh report */
            const val REPORT_REQUEST = "wholphinix:device_status"

            private val SAMPLE_INTERVAL = 30.seconds
            private val UPLOAD_INTERVAL = 55.seconds
            private const val MAX_SAMPLES = 60
            private const val HOT_C = 80.0
            private const val MB = 1024 * 1024
        }
    }

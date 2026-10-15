package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/** Where a bubble on the TV came from */
enum class TvMessageSource(
    val key: String,
) {
    /** A quip of Jean, the companion ([ClaudeCompanionService]) */
    JEAN("jean"),

    /** A message sent from Telka / Claude through jellyfin-mcp (`show_message`) */
    TELKA("telka"),
}

@Serializable
data class JeanBubble(
    /** Epoch millis */
    val time: Long,
    val title: String? = null,
    val text: String,
    /** [TvMessageSource.key] */
    val source: String,
    /** The film or episode that was playing, if any */
    val media: String? = null,
)

/**
 * History of Jean's bubbles (#47): the last [MAX_ENTRIES], kept in a file so they survive a restart, shown in the
 * settings and shared with jellyfin-mcp through [TelemetryService], so Telka can answer "čo si tým myslel?".
 */
@Singleton
class JeanHistoryService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val telemetryService: TelemetryService,
        @param:IoCoroutineScope private val scope: CoroutineScope,
    ) {
        private val json = Json { ignoreUnknownKeys = true }
        private val file get() = File(context.filesDir, FILE_NAME)

        private val _entries = MutableStateFlow<List<JeanBubble>>(emptyList())

        /** Newest first */
        val entries: StateFlow<List<JeanBubble>> = _entries

        @Volatile
        private var nowPlayingId: UUID? = null

        @Volatile
        private var nowPlayingName: String? = null

        private var uploadJob: Job? = null

        init {
            scope.launch {
                try {
                    if (file.exists()) {
                        val loaded = json.decodeFromString<List<JeanBubble>>(file.readText())
                        _entries.value = loaded.take(MAX_ENTRIES)
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not read Jean's history")
                }
            }
        }

        /** The film or episode that is playing now (null when the player is closed), its name goes with the bubbles */
        fun setNowPlaying(itemId: UUID?) {
            if (itemId == nowPlayingId) return
            nowPlayingId = itemId
            nowPlayingName = null
            if (itemId == null) return
            scope.launch {
                try {
                    val item = api.userLibraryApi.getItem(itemId).content
                    val name =
                        if (item.type == BaseItemKind.EPISODE) {
                            val number =
                                listOfNotNull(
                                    item.parentIndexNumber?.let { "S$it" },
                                    item.indexNumber?.let { "E$it" },
                                ).joinToString("")
                            listOfNotNull(item.seriesName, number.ifEmpty { null }, item.name).joinToString(" · ")
                        } else {
                            listOfNotNull(item.name, item.productionYear?.let { "($it)" }).joinToString(" ")
                        }
                    if (nowPlayingId == itemId) nowPlayingName = name
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not get the name of %s", itemId)
                }
            }
        }

        fun record(
            source: TvMessageSource,
            title: String?,
            text: String,
        ) {
            Timber.i("TV bubble (%s): %s", source.key, text.replace('\n', ' ').take(LOG_LENGTH))
            val bubble =
                JeanBubble(
                    time = System.currentTimeMillis(),
                    title = title,
                    text = text,
                    source = source.key,
                    media = nowPlayingName.takeIf { nowPlayingId != null },
                )
            val updated =
                synchronized(this) {
                    (listOf(bubble) + _entries.value).take(MAX_ENTRIES).also { _entries.value = it }
                }
            scope.launch {
                try {
                    file.writeText(json.encodeToString(updated))
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not save Jean's history")
                }
            }
            scheduleUpload()
        }

        /** Several bubbles in a row are sent once */
        @Synchronized
        private fun scheduleUpload() {
            uploadJob?.cancel()
            uploadJob =
                scope.launch {
                    delay(2.seconds)
                    telemetryService.put(mapOf(PREF_KEY to json.encodeToString(_entries.value)))
                }
        }

        companion object {
            const val MAX_ENTRIES = 30

            /** Read by jellyfin-mcp */
            const val PREF_KEY = "jean_history"
            private const val FILE_NAME = "jean_history.json"
            private const val LOG_LENGTH = 120
        }
    }

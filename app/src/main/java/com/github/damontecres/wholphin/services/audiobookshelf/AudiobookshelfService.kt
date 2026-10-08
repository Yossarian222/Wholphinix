package com.github.damontecres.wholphin.services.audiobookshelf

import com.github.damontecres.wholphin.services.KeyValueService
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

class AbsException(
    message: String,
    val code: Int? = null,
) : IOException(message)

/**
 * Minimal REST client for an Audiobookshelf server (tested against the 2.x API shape).
 *
 * Authentication is a bearer token; for media/cover URLs handed to the player or Coil the token
 * is appended as a `token` query parameter because those loaders can't easily add headers.
 */
@Singleton
class AudiobookshelfService
    @Inject
    constructor(
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
        private val keyValueService: KeyValueService,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
                encodeDefaults = true
            }

        /** A stable per-install id so ABS can tell this device apart in its session list */
        private val deviceId: String by lazy { UUID.randomUUID().toString() }

        val config: Flow<AbsConfig> = keyValueService.get(CONFIG_KEY, AbsConfig())

        suspend fun currentConfig(): AbsConfig = config.first()

        suspend fun saveConfig(config: AbsConfig) = keyValueService.save(CONFIG_KEY, config)

        suspend fun logout() = keyValueService.save(CONFIG_KEY, AbsConfig())

        // ---- Auth ----

        /**
         * Log in with username/password and persist the resulting token.
         *
         * @param rawBaseUrl e.g. `http://192.168.1.201:13378`
         */
        suspend fun login(
            rawBaseUrl: String,
            username: String,
            password: String,
        ): AbsConfig {
            val baseUrl = normalizeBaseUrl(rawBaseUrl)
            val body = json.encodeToString(AbsLoginRequest(username, password))
            val request =
                Request
                    .Builder()
                    .url(baseUrl.newBuilder().addPathSegment("login").build())
                    .post(body.toRequestBody(JSON))
                    .build()
            val response: AbsLoginResponse = execute(request)
            val token =
                response.user.token ?: response.user.accessToken
                    ?: throw AbsException("Server did not return a token")
            return AbsConfig(baseUrl.toString().trimEnd('/'), token, username).also { saveConfig(it) }
        }

        // ---- Library browsing ----

        suspend fun libraries(): List<AbsLibrary> = get<AbsLibrariesResponse>("api", "libraries").libraries

        suspend fun podcastLibraries(): List<AbsLibrary> = libraries().filter { it.isPodcast }

        suspend fun libraryItems(
            libraryId: String,
            limit: Int = 200,
            page: Int = 0,
        ): AbsLibraryItemsResponse =
            get(
                "api",
                "libraries",
                libraryId,
                "items",
                query =
                    mapOf(
                        "limit" to limit.toString(),
                        "page" to page.toString(),
                        "sort" to "media.metadata.title",
                    ),
            )

        /** Full item including the episode list (podcasts) and per-episode user progress */
        suspend fun item(itemId: String): AbsLibraryItem =
            get(
                "api",
                "items",
                itemId,
                query = mapOf("expanded" to "1", "include" to "progress"),
            )

        /** Podcast episodes the user started but hasn't finished ("continue listening") */
        suspend fun itemsInProgress(): List<AbsLibraryItem> = get<AbsItemsInProgressResponse>("api", "me", "items-in-progress").libraryItems

        suspend fun progress(
            itemId: String,
            episodeId: String?,
        ): AbsMediaProgress? =
            try {
                get<AbsMediaProgress>(*progressPath(itemId, episodeId))
            } catch (ex: AbsException) {
                // 404 just means "never played"
                if (ex.code == 404) null else throw ex
            }

        // ---- Images ----

        /** URL of the item's cover art, authenticated via query token. Null if not configured. */
        suspend fun coverUrl(
            itemId: String,
            width: Int = 600,
        ): String? = coverUrl(currentConfig(), itemId, width)

        fun coverUrl(
            config: AbsConfig,
            itemId: String,
            width: Int = 600,
        ): String? {
            if (!config.isConfigured) return null
            val base = config.baseUrl.toHttpUrlOrNull() ?: return null
            return base
                .newBuilder()
                .addPathSegments("api/items/$itemId/cover")
                .addQueryParameter("width", width.toString())
                .addQueryParameter("format", "jpeg")
                .addQueryParameter("token", config.token)
                .build()
                .toString()
        }

        // ---- Playback ----

        /**
         * Open a playback session. The returned session includes the resume position
         * ([AbsPlaySession.currentTime]) and the audio tracks to feed the player.
         */
        suspend fun startSession(
            itemId: String,
            episodeId: String?,
        ): AbsPlaySession {
            val body =
                json.encodeToString(
                    AbsPlayRequest(AbsDeviceInfo(clientName = "Wholphinix", deviceId = deviceId)),
                )
            val segments = mutableListOf("api", "items", itemId, "play")
            episodeId?.let { segments += it }
            return post(*segments.toTypedArray(), body = body)
        }

        /** Absolute, authenticated URL for a session audio track */
        suspend fun trackUrl(track: AbsAudioTrack): String = trackUrl(currentConfig(), track)

        fun trackUrl(
            config: AbsConfig,
            track: AbsAudioTrack,
        ): String {
            val base = config.baseUrl.toHttpUrl()
            val resolved = base.resolve(track.contentUrl) ?: throw AbsException("Bad track url ${track.contentUrl}")
            return resolved
                .newBuilder()
                .setQueryParameter("token", config.token)
                .build()
                .toString()
        }

        /** Report progress during playback. Call roughly every 15-30 seconds. */
        suspend fun syncSession(
            sessionId: String,
            currentTimeSec: Double,
            timeListenedSec: Double,
            durationSec: Double? = null,
        ) {
            val body = json.encodeToString(AbsSyncRequest(currentTimeSec, timeListenedSec, durationSec))
            postNoContent("api", "session", sessionId, "sync", body = body)
        }

        suspend fun closeSession(
            sessionId: String,
            currentTimeSec: Double,
            timeListenedSec: Double,
        ) {
            val body = json.encodeToString(AbsSyncRequest(currentTimeSec, timeListenedSec))
            postNoContent("api", "session", sessionId, "close", body = body)
        }

        /** Directly set progress, e.g. "mark as finished" without playing */
        suspend fun updateProgress(
            itemId: String,
            episodeId: String?,
            currentTimeSec: Double,
            durationSec: Double,
            isFinished: Boolean,
        ) {
            val progress = if (durationSec > 0) (currentTimeSec / durationSec).coerceIn(0.0, 1.0) else 0.0
            val body =
                json.encodeToString(AbsProgressUpdate(currentTimeSec, durationSec, progress, isFinished))
            val request =
                authed(requireConfig(), *progressPath(itemId, episodeId))
                    .patch(body.toRequestBody(JSON))
                    .build()
            executeNoContent(request)
        }

        // ---- HTTP plumbing ----

        private fun progressPath(
            itemId: String,
            episodeId: String?,
        ): Array<String> =
            buildList {
                addAll(listOf("api", "me", "progress", itemId))
                episodeId?.let { add(it) }
            }.toTypedArray()

        private suspend fun requireConfig(): AbsConfig =
            currentConfig().also { if (!it.isConfigured) throw AbsException("Audiobookshelf is not configured") }

        private fun authed(
            config: AbsConfig,
            vararg segments: String,
            query: Map<String, String> = emptyMap(),
        ): Request.Builder {
            val url =
                config.baseUrl
                    .toHttpUrl()
                    .newBuilder()
                    .apply {
                        segments.forEach { addPathSegment(it) }
                        query.forEach { (k, v) -> addQueryParameter(k, v) }
                    }.build()
            return Request.Builder().url(url).header("Authorization", "Bearer ${config.token}")
        }

        private suspend inline fun <reified T> get(
            vararg segments: String,
            query: Map<String, String> = emptyMap(),
        ): T = execute(authed(requireConfig(), *segments, query = query).get().build())

        private suspend inline fun <reified T> post(
            vararg segments: String,
            body: String,
        ): T = execute(authed(requireConfig(), *segments).post(body.toRequestBody(JSON)).build())

        private suspend fun postNoContent(
            vararg segments: String,
            body: String,
        ) = executeNoContent(authed(requireConfig(), *segments).post(body.toRequestBody(JSON)).build())

        private suspend inline fun <reified T> execute(request: Request): T =
            withContext(WholphinDispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    val text = response.body.string()
                    if (!response.isSuccessful) {
                        Timber.w("ABS %s %s -> %s", request.method, request.url.encodedPath, response.code)
                        throw AbsException("HTTP ${response.code}", response.code)
                    }
                    try {
                        json.decodeFromString<T>(text)
                    } catch (ex: Exception) {
                        throw AbsException("Unexpected response from ${request.url.encodedPath}: ${ex.message}")
                    }
                }
            }

        private suspend fun executeNoContent(request: Request) =
            withContext(WholphinDispatchers.IO) {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Timber.w("ABS %s %s -> %s", request.method, request.url.encodedPath, response.code)
                        throw AbsException("HTTP ${response.code}", response.code)
                    }
                }
            }

        companion object {
            const val CONFIG_KEY = "audiobookshelf_config"
            private val JSON = "application/json; charset=utf-8".toMediaType()

            /** Accepts `192.168.1.201:13378`, `http://host:port/` etc. */
            fun normalizeBaseUrl(raw: String): HttpUrl {
                val trimmed = raw.trim().trimEnd('/')
                val withScheme = if ("://" in trimmed) trimmed else "http://$trimmed"
                return withScheme.toHttpUrlOrNull() ?: throw AbsException("Invalid server address: $raw")
            }
        }
    }

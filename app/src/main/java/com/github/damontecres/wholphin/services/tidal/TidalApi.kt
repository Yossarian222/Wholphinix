package com.github.damontecres.wholphin.services.tidal

import android.util.Base64
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Logged in TIDAL account, stored by [TidalService] */
@Serializable
data class TidalSession(
    val accessToken: String = "",
    val refreshToken: String = "",
    val userId: Long = 0,
    val countryCode: String = "US",
) {
    val isLoggedIn: Boolean get() = refreshToken.isNotBlank()
}

@Serializable
data class TidalDeviceCode(
    val deviceCode: String,
    val userCode: String,
    val verificationUriComplete: String,
    val expiresIn: Int,
    val interval: Int = 2,
)

@Serializable
data class TidalTokenUser(
    val userId: Long,
    val countryCode: String = "US",
)

@Serializable
data class TidalTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String? = null,
    val user: TidalTokenUser? = null,
)

@Serializable
data class TidalArtist(
    val id: Long = 0,
    val name: String = "",
)

@Serializable
data class TidalAlbum(
    val id: Long,
    val title: String = "",
    val cover: String? = null,
    val artist: TidalArtist? = null,
    val numberOfTracks: Int = 0,
)

@Serializable
data class TidalTrack(
    val id: Long,
    val title: String = "",
    val duration: Int = 0,
    val artists: List<TidalArtist> = emptyList(),
    val album: TidalAlbum? = null,
    val streamReady: Boolean = true,
    val explicit: Boolean = false,
    val audioQuality: String? = null,
    val copyright: String? = null,
    val trackNumber: Int = 0,
    /** When the track was added, only in playlist responses (ISO 8601, so it sorts as text) */
    val dateAdded: String? = null,
) {
    val artistNames: String get() = artists.joinToString(", ") { it.name }
}

@Serializable
data class TidalPlaylist(
    val uuid: String,
    val title: String = "",
    val squareImage: String? = null,
    val image: String? = null,
    val numberOfTracks: Int = 0,
    val creator: TidalArtist? = null,
)

@Serializable
data class TidalImage(
    val url: String = "",
)

/** A personal mix (My Mix, Daily Discovery, New Arrivals…) */
@Serializable
data class TidalMix(
    val id: String,
    val title: String = "",
    val subTitle: String = "",
    val mixType: String? = null,
    val images: Map<String, TidalImage> = emptyMap(),
) {
    val imageUrl: String? get() = (images["MEDIUM"] ?: images["LARGE"] ?: images["SMALL"])?.url?.takeIf { it.isNotBlank() }
}

/** A list of tracks TIDAL recommends on its home page, e.g. "Suggested new tracks" */
data class TidalTrackShelf(
    val title: String,
    val tracks: List<TidalTrack>,
)

/** What TIDAL recommends: personal mixes and track lists */
data class TidalForYou(
    val mixes: List<TidalMix>,
    val shelves: List<TidalTrackShelf>,
)

@Serializable
private data class Page<T>(
    val items: List<T> = emptyList(),
    val totalNumberOfItems: Int = 0,
)

@Serializable
private data class Favorite<T>(
    val item: T,
)

@Serializable
private data class PlaybackInfo(
    val manifestMimeType: String,
    val manifest: String,
    val audioQuality: String? = null,
)

/** Lyrics of a track; [subtitles] is LRC ("[mm:ss.xx] line") when TIDAL has them synced */
@Serializable
data class TidalLyrics(
    val lyrics: String? = null,
    val subtitles: String? = null,
)

/** A resolved stream with the quality TIDAL actually serves */
data class TidalStream(
    val url: String,
    val audioQuality: String?,
)

@Serializable
private data class BtsManifest(
    val urls: List<String>,
)

/** Not logged in anymore (refresh token revoked or expired) */
class TidalAuthException(
    message: String,
) : IOException(message)

class TidalHttpException(
    val code: Int,
    body: String,
) : IOException("TIDAL HTTP $code: ${body.take(300)}")

/**
 * Minimal client for TIDAL's v1 API, the one the official TIDAL apps use. Login uses the device code flow
 * (code shown on the TV, confirmed on link.tidal.com), so nothing has to be typed with the remote.
 */
@Singleton
class TidalApi
    @Inject
    constructor(
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                coerceInputValues = true
            }

        // ponytail: one shared TIDAL TV client id, set at build time (local.properties or CI secret)
        val clientId: String = BuildConfig.TIDAL_CLIENT_ID
        private val clientSecret: String = BuildConfig.TIDAL_CLIENT_SECRET
        val isConfigured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()

        suspend fun startDeviceLogin(): TidalDeviceCode =
            json.decodeFromString(post(AUTH + "device_authorization", mapOf("client_id" to clientId, "scope" to SCOPE)))

        /** One poll of the device login; null while the user has not confirmed the code yet */
        suspend fun pollDeviceLogin(deviceCode: String): TidalSession? =
            try {
                val tok =
                    json.decodeFromString<TidalTokenResponse>(
                        post(
                            AUTH + "token",
                            mapOf(
                                "client_id" to clientId,
                                "client_secret" to clientSecret,
                                "device_code" to deviceCode,
                                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                                "scope" to SCOPE,
                            ),
                        ),
                    )
                TidalSession(
                    accessToken = tok.accessToken,
                    refreshToken = tok.refreshToken.orEmpty(),
                    userId = tok.user?.userId ?: 0,
                    countryCode = tok.user?.countryCode ?: "US",
                )
            } catch (ex: TidalHttpException) {
                if (ex.code == 400 && ex.message.orEmpty().contains("authorization_pending")) null else throw ex
            }

        /** New access token for [session] */
        suspend fun refresh(session: TidalSession): TidalSession {
            val tok =
                try {
                    json.decodeFromString<TidalTokenResponse>(
                        post(
                            AUTH + "token",
                            mapOf(
                                "client_id" to clientId,
                                "client_secret" to clientSecret,
                                "refresh_token" to session.refreshToken,
                                "grant_type" to "refresh_token",
                                "scope" to SCOPE,
                            ),
                        ),
                    )
                } catch (ex: TidalHttpException) {
                    if (ex.code in 400..401) throw TidalAuthException("Refresh failed: ${ex.message}") else throw ex
                }
            return session.copy(
                accessToken = tok.accessToken,
                refreshToken = tok.refreshToken ?: session.refreshToken,
            )
        }

        suspend fun favoriteTracks(s: TidalSession): List<TidalTrack> =
            pagedAll<Favorite<TidalTrack>>(s, "users/${s.userId}/favorites/tracks", "order" to "DATE", "orderDirection" to "DESC")
                .map { it.item }

        suspend fun favoriteAlbums(s: TidalSession): List<TidalAlbum> =
            pagedAll<Favorite<TidalAlbum>>(s, "users/${s.userId}/favorites/albums", "order" to "DATE", "orderDirection" to "DESC")
                .map { it.item }

        suspend fun playlists(s: TidalSession): List<TidalPlaylist> = pagedAll(s, "users/${s.userId}/playlists")

        /** Personal mixes and recommended track lists from TIDAL's pages, the same ones its apps show */
        suspend fun forYou(s: TidalSession): TidalForYou {
            val mixes = LinkedHashMap<String, TidalMix>()
            val shelves = mutableListOf<TidalTrackShelf>()
            for (page in listOf("pages/my_collection_my_mixes", "pages/home", "pages/for_you")) {
                val root =
                    try {
                        json.parseToJsonElement(get(s, page, "deviceType" to "BROWSER", "locale" to "en_US"))
                    } catch (ex: TidalHttpException) {
                        if (ex.code in 400..404) continue else throw ex
                    }
                walk(root) { obj ->
                    if (obj["mixType"] != null && obj["id"] is JsonPrimitive) {
                        runCatching { json.decodeFromJsonElement(TidalMix.serializer(), obj) }
                            .getOrNull()
                            ?.let { mixes.putIfAbsent(it.id, it) }
                    }
                    if (obj.string("type") == "TRACK_LIST") {
                        val title = obj.string("title").orEmpty()
                        val paged = obj["pagedList"] as? JsonObject
                        val tracks = paged?.let { trackList(s, it) }.orEmpty()
                        if (title.isNotBlank() && tracks.isNotEmpty() && shelves.none { it.title == title }) {
                            shelves += TidalTrackShelf(title, tracks)
                        }
                    }
                }
            }
            return TidalForYou(mixes.values.toList(), shelves)
        }

        /** Tracks of a page module: all of them via its data path when there is one, else the ones inlined */
        private suspend fun trackList(
            s: TidalSession,
            paged: JsonObject,
        ): List<TidalTrack> {
            paged.string("dataApiPath")?.let { path ->
                try {
                    return pagedAll<TidalTrack>(s, path, "deviceType" to "BROWSER", "locale" to "en_US", max = 200)
                } catch (ex: Exception) {
                    if (ex is TidalHttpException && ex.code == 401) throw ex
                }
            }
            return (paged["items"] as? JsonArray)
                ?.mapNotNull { runCatching { json.decodeFromJsonElement(TidalTrack.serializer(), it) }.getOrNull() }
                .orEmpty()
        }

        suspend fun mixTracks(
            s: TidalSession,
            mixId: String,
        ): List<TidalTrack> =
            try {
                pagedAll<Favorite<JsonElement>>(s, "mixes/$mixId/items").mapNotNull {
                    runCatching { json.decodeFromJsonElement(TidalTrack.serializer(), it.item) }.getOrNull()
                }
            } catch (ex: TidalHttpException) {
                if (ex.code == 401) throw ex
                // Older route: the mix page, whose track list module holds the tracks
                val root = json.parseToJsonElement(get(s, "pages/mix", "mixId" to mixId, "deviceType" to "BROWSER", "locale" to "en_US"))
                val tracks = mutableListOf<TidalTrack>()
                walk(root) { obj ->
                    if (tracks.isEmpty() && obj.string("type") == "TRACK_LIST") {
                        (obj["pagedList"] as? JsonObject)?.let { tracks += trackList(s, it) }
                    }
                }
                tracks
            }

        /**
         * Tracks of a playlist; [newestFirst] asks TIDAL to order them by when they were added,
         * else they come in the playlist's own order
         */
        suspend fun playlistTracks(
            s: TidalSession,
            uuid: String,
            newestFirst: Boolean,
        ): List<TidalTrack> =
            if (newestFirst) {
                pagedAll(s, "playlists/$uuid/tracks", "order" to "DATE", "orderDirection" to "DESC")
            } else {
                pagedAll(s, "playlists/$uuid/tracks")
            }

        /** Adds [trackId] to the user's playlist [uuid] (skipped when it is already there) */
        suspend fun addToPlaylist(
            s: TidalSession,
            uuid: String,
            trackId: Long,
        ) = withContext(WholphinDispatchers.IO) {
            // TIDAL wants the playlist's current ETag so concurrent edits don't clash
            val etag =
                okHttpClient.newCall(request(s, "playlists/$uuid")).execute().use { response ->
                    if (!response.isSuccessful) throw TidalHttpException(response.code, response.body.string())
                    response.header("ETag")
                }
            val body =
                FormBody
                    .Builder()
                    .add("trackIds", trackId.toString())
                    .add("onDupes", "SKIP")
                    .add("onArtifactNotFound", "SKIP")
                    .build()
            execute(
                request(s, "playlists/$uuid/items")
                    .newBuilder()
                    .post(body)
                    .apply { etag?.let { header("If-None-Match", it) } }
                    .build(),
            )
            Unit
        }

        /** Calls [visit] on every JSON object in [element], depth first */
        private suspend fun walk(
            element: JsonElement,
            visit: suspend (JsonObject) -> Unit,
        ) {
            when (element) {
                is JsonObject -> {
                    visit(element)
                    element.values.forEach { walk(it, visit) }
                }

                is JsonArray -> element.forEach { walk(it, visit) }

                else -> Unit
            }
        }

        private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

        suspend fun albumTracks(
            s: TidalSession,
            albumId: Long,
        ): List<TidalTrack> = pagedAll(s, "albums/$albumId/tracks")

        /** Direct, short-lived URL of the full track */
        suspend fun streamUrl(
            s: TidalSession,
            trackId: Long,
        ): TidalStream {
            val info =
                json.decodeFromString<PlaybackInfo>(
                    get(
                        s,
                        "tracks/$trackId/playbackinfopostpaywall",
                        "audioquality" to "LOSSLESS",
                        "playbackmode" to "STREAM",
                        "assetpresentation" to "FULL",
                    ),
                )
            if (info.manifestMimeType != BTS) throw IOException("Unsupported TIDAL manifest ${info.manifestMimeType}")
            val manifest = String(Base64.decode(info.manifest, Base64.DEFAULT))
            return TidalStream(
                url = json.decodeFromString<BtsManifest>(manifest).urls.first(),
                audioQuality = info.audioQuality,
            )
        }

        /** Lyrics, or null when TIDAL has none for the track */
        suspend fun lyrics(
            s: TidalSession,
            trackId: Long,
        ): TidalLyrics? =
            try {
                json.decodeFromString<TidalLyrics>(get(s, "tracks/$trackId/lyrics"))
            } catch (ex: TidalHttpException) {
                if (ex.code == 404) null else throw ex
            }

        private suspend inline fun <reified T> pagedAll(
            s: TidalSession,
            path: String,
            vararg query: Pair<String, String>,
            max: Int = Int.MAX_VALUE,
        ): List<T> {
            val all = mutableListOf<T>()
            do {
                val page =
                    json.decodeFromString<Page<T>>(
                        get(s, path, *query, "limit" to PAGE.toString(), "offset" to all.size.toString()),
                    )
                all += page.items
            } while (page.items.isNotEmpty() && all.size < page.totalNumberOfItems && all.size < max)
            return all
        }

        private suspend fun get(
            s: TidalSession,
            path: String,
            vararg query: Pair<String, String>,
        ): String =
            withContext(WholphinDispatchers.IO) { execute(request(s, path, *query)) }

        private fun request(
            s: TidalSession,
            path: String,
            vararg query: Pair<String, String>,
        ): Request {
            val url =
                (API + path.removePrefix("/")).toHttpUrl().newBuilder().apply {
                    addQueryParameter("countryCode", s.countryCode)
                    query.forEach { (k, v) -> addQueryParameter(k, v) }
                }.build()
            return Request.Builder().url(url).header("Authorization", "Bearer ${s.accessToken}").build()
        }

        private suspend fun post(
            url: String,
            form: Map<String, String>,
        ): String =
            withContext(WholphinDispatchers.IO) {
                val body = FormBody.Builder().apply { form.forEach { (k, v) -> add(k, v) } }.build()
                execute(Request.Builder().url(url).post(body).build())
            }

        private fun execute(request: Request): String =
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                if (!response.isSuccessful) throw TidalHttpException(response.code, body)
                body
            }

        companion object {
            private const val API = "https://api.tidal.com/v1/"
            private const val AUTH = "https://auth.tidal.com/v1/oauth2/"
            private const val SCOPE = "r_usr w_usr w_sub"
            private const val BTS = "application/vnd.tidal.bts"
            private const val PAGE = 100

            /** Cover or playlist image id to a square image URL */
            fun imageUrl(
                id: String?,
                size: Int = 320,
            ): String? = id?.let { "https://resources.tidal.com/images/${it.replace('-', '/')}/${size}x$size.jpg" }
        }
    }

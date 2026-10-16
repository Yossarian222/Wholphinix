package com.github.damontecres.wholphin.services.tidal

import android.util.Base64
import com.github.damontecres.wholphin.BuildConfig
import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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

        suspend fun albumTracks(
            s: TidalSession,
            albumId: Long,
        ): List<TidalTrack> = pagedAll(s, "albums/$albumId/tracks")

        suspend fun playlistTracks(
            s: TidalSession,
            uuid: String,
        ): List<TidalTrack> = pagedAll(s, "playlists/$uuid/tracks")

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
        ): List<T> {
            val all = mutableListOf<T>()
            do {
                val page =
                    json.decodeFromString<Page<T>>(
                        get(s, path, *query, "limit" to PAGE.toString(), "offset" to all.size.toString()),
                    )
                all += page.items
            } while (page.items.isNotEmpty() && all.size < page.totalNumberOfItems)
            return all
        }

        private suspend fun get(
            s: TidalSession,
            path: String,
            vararg query: Pair<String, String>,
        ): String =
            withContext(WholphinDispatchers.IO) {
                val url =
                    (API + path).toHttpUrl().newBuilder().apply {
                        addQueryParameter("countryCode", s.countryCode)
                        query.forEach { (k, v) -> addQueryParameter(k, v) }
                    }.build()
                execute(Request.Builder().url(url).header("Authorization", "Bearer ${s.accessToken}").build())
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

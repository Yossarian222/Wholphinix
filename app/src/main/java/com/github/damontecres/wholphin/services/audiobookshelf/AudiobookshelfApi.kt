package com.github.damontecres.wholphin.services.audiobookshelf

import com.github.damontecres.wholphin.services.hilt.StandardOkHttpClient
import com.github.damontecres.wholphin.util.WholphinDispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal REST client for the Audiobookshelf API. Uses the standard OkHttp client so the
 * Jellyfin auth header is not sent to Audiobookshelf.
 */
@Singleton
class AudiobookshelfApi
    @Inject
    constructor(
        @param:StandardOkHttpClient private val okHttpClient: OkHttpClient,
    ) {
        private val json =
            Json {
                ignoreUnknownKeys = true
                isLenient = true
            }
        private val jsonType = "application/json".toMediaType()

        suspend fun libraries(conn: AbsConnection): AbsLibrariesResponse =
            withContext(WholphinDispatchers.IO) {
                json.decodeFromString(get(conn, "/api/libraries"))
            }

        /** All items in a podcast library */
        suspend fun libraryItems(
            conn: AbsConnection,
            libraryId: String,
        ): AbsLibraryItemsResponse =
            withContext(WholphinDispatchers.IO) {
                json.decodeFromString(get(conn, "/api/libraries/$libraryId/items?limit=0&minified=1"))
            }

        /** One podcast with its full episode list */
        suspend fun podcast(
            conn: AbsConnection,
            itemId: String,
        ): AbsLibraryItem =
            withContext(WholphinDispatchers.IO) {
                json.decodeFromString(get(conn, "/api/items/$itemId?expanded=1"))
            }

        /** The current user, including the listening progress for every episode */
        suspend fun me(conn: AbsConnection): AbsUser =
            withContext(WholphinDispatchers.IO) {
                json.decodeFromString(get(conn, "/api/me"))
            }

        /** Starts a playback session for one episode. The response includes the saved position. */
        suspend fun startPlay(
            conn: AbsConnection,
            itemId: String,
            episodeId: String,
        ): AbsPlaySession =
            withContext(WholphinDispatchers.IO) {
                json.decodeFromString(
                    post(
                        conn,
                        "/api/items/$itemId/play/$episodeId",
                        json.encodeToString(AbsPlayRequest()),
                    ),
                )
            }

        /** Reports the current position so progress is saved on the server */
        suspend fun sync(
            conn: AbsConnection,
            sessionId: String,
            request: AbsSyncRequest,
        ) = withContext(WholphinDispatchers.IO) {
            post(conn, "/api/session/$sessionId/sync", json.encodeToString(request))
        }

        suspend fun close(
            conn: AbsConnection,
            sessionId: String,
        ) = withContext(WholphinDispatchers.IO) {
            post(conn, "/api/session/$sessionId/close", null)
        }

        /**
         * Cover image URL. The token is passed as a query parameter so the image loader needs no extra headers.
         */
        fun coverUrl(
            conn: AbsConnection,
            itemId: String,
        ): String = "${conn.baseUrl.trimEnd('/')}/api/items/$itemId/cover?token=${conn.token}"

        private fun get(
            conn: AbsConnection,
            path: String,
        ): String {
            val request =
                Request
                    .Builder()
                    .url(conn.baseUrl.trimEnd('/') + path)
                    .header("Authorization", "Bearer ${conn.token}")
                    .get()
                    .build()
            return execute(request, path)
        }

        private fun post(
            conn: AbsConnection,
            path: String,
            body: String?,
        ): String {
            val requestBody = (body ?: "").toRequestBody(jsonType)
            val request =
                Request
                    .Builder()
                    .url(conn.baseUrl.trimEnd('/') + path)
                    .header("Authorization", "Bearer ${conn.token}")
                    .post(requestBody)
                    .build()
            return execute(request, path)
        }

        private fun execute(
            request: Request,
            path: String,
        ): String =
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw AudiobookshelfHttpException(response.code, path)
                }
                response.body?.string() ?: ""
            }
    }

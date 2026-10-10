package com.github.damontecres.wholphin.services.audiobookshelf

import com.github.damontecres.wholphin.services.KeyValueService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds the Audiobookshelf settings and runs requests against the LAN address,
 * falling back to the Tailscale address only when the LAN address cannot be reached.
 */
@Singleton
class AudiobookshelfService
    @Inject
    constructor(
        private val keyValueService: KeyValueService,
        private val api: AudiobookshelfApi,
    ) {
        companion object {
            private const val KEY = "audiobookshelf_config"
        }

        /** Current settings, or an empty config if none were saved */
        val config: Flow<AbsConfig> = keyValueService.get<AbsConfig>(KEY, AbsConfig())

        /** Whether the integration is configured (the nav drawer item is shown either way) */
        val active: Flow<Boolean> = config.map { it.isComplete }

        suspend fun save(config: AbsConfig) {
            keyValueService.save(KEY, config)
        }

        /** Clears the saved connection by saving an empty config */
        suspend fun clear() {
            keyValueService.save(KEY, AbsConfig())
        }

        /**
         * Runs [block] against the LAN address first. Only a network failure (not an HTTP error)
         * moves to the Tailscale address. Returns the connection that worked along with the result.
         */
        suspend fun <T> withConnection(
            config: AbsConfig,
            block: suspend (AbsConnection) -> T,
        ): Pair<AbsConnection, T> {
            val lan = AbsConnection(config.lanUrl, config.token)
            try {
                return lan to block(lan)
            } catch (ex: AudiobookshelfHttpException) {
                throw ex
            } catch (ex: IOException) {
                if (config.tailscaleUrl.isBlank()) throw ex
            }
            val fallback = AbsConnection(config.tailscaleUrl, config.token)
            return fallback to block(fallback)
        }

        suspend fun libraries(conn: AbsConnection) = api.libraries(conn)

        suspend fun libraryItems(
            conn: AbsConnection,
            libraryId: String,
        ) = api.libraryItems(conn, libraryId)

        suspend fun podcast(
            conn: AbsConnection,
            itemId: String,
        ) = api.podcast(conn, itemId)

        suspend fun me(conn: AbsConnection) = api.me(conn)

        suspend fun startPlay(
            conn: AbsConnection,
            itemId: String,
            episodeId: String,
        ) = api.startPlay(conn, itemId, episodeId)

        suspend fun sync(
            conn: AbsConnection,
            sessionId: String,
            request: AbsSyncRequest,
        ) = api.sync(conn, sessionId, request)

        suspend fun close(
            conn: AbsConnection,
            sessionId: String,
        ) = api.close(conn, sessionId)

        fun coverUrl(
            conn: AbsConnection,
            itemId: String,
        ) = api.coverUrl(conn, itemId)
    }

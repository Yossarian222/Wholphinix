package com.github.damontecres.wholphin.services.tidal

import com.github.damontecres.wholphin.services.KeyValueService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import javax.inject.Inject
import javax.inject.Singleton

/** What the user set on the TIDAL settings screen */
@Serializable
data class TidalSettings(
    /** One of [TidalTheme] names */
    val theme: String = "BLACK",
    /** LOW, HIGH or LOSSLESS, as TIDAL calls them */
    val quality: String = "LOSSLESS",
    /** Playlists open newest added first */
    val newestFirst: Boolean = true,
    /** Now playing opens with the lyrics instead of the play queue */
    val lyricsFirst: Boolean = false,
)

/** Keeps the TIDAL login and runs API calls with it, refreshing the access token when it expires */
@Singleton
class TidalService
    @Inject
    constructor(
        private val keyValueService: KeyValueService,
        val api: TidalApi,
    ) {
        companion object {
            private const val KEY = "tidal_session"
            private const val SETTINGS_KEY = "tidal_settings"
        }

        val settings: Flow<TidalSettings> = keyValueService.get(SETTINGS_KEY, TidalSettings())

        suspend fun saveSettings(settings: TidalSettings) = keyValueService.save(SETTINGS_KEY, settings)

        val session: Flow<TidalSession> = keyValueService.get(KEY, TidalSession())
        private val refreshLock = Mutex()

        /**
         * Shows [onCode] to the user and waits until the code is confirmed on link.tidal.com.
         * Throws when the code expires.
         */
        suspend fun login(onCode: (TidalDeviceCode) -> Unit) {
            val code = api.startDeviceLogin()
            onCode(code)
            val deadline = System.currentTimeMillis() + code.expiresIn * 1000L
            while (System.currentTimeMillis() < deadline) {
                delay(code.interval * 1000L)
                api.pollDeviceLogin(code.deviceCode)?.let {
                    keyValueService.save(KEY, it)
                    return
                }
            }
            throw TidalAuthException("Code expired")
        }

        suspend fun logout() = keyValueService.save(KEY, TidalSession())

        /** Runs [block] with the saved session, refreshing the token once on HTTP 401 */
        suspend fun <T> call(block: suspend (TidalSession) -> T): T {
            val current = session.first()
            if (!current.isLoggedIn) throw TidalAuthException("Not logged in")
            return try {
                block(current)
            } catch (ex: TidalHttpException) {
                if (ex.code != 401) throw ex
                block(refreshed(current))
            }
        }

        private suspend fun refreshed(stale: TidalSession): TidalSession =
            refreshLock.withLock {
                // Another call may have refreshed it while this one waited
                val now = session.first()
                if (now.accessToken != stale.accessToken) return now
                try {
                    api.refresh(now).also { keyValueService.save(KEY, it) }
                } catch (ex: TidalAuthException) {
                    logout()
                    throw ex
                }
            }
    }

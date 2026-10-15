package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.ServerRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.displayPreferencesApi
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shares small pieces of state from the TV with the jellyfin-mcp server (Telka / Claude) through Jellyfin itself:
 * they are stored as custom display preferences of the user ([DISPLAY_PREFERENCES_ID], client [CLIENT]), which the
 * server reads with its API key. No extra address or secret is needed on the TV.
 *
 * Used for the history of Jean's bubbles (#47) and the device status of the RPi (#52).
 */
@Singleton
class TelemetryService
    @Inject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
    ) {
        private val mutex = Mutex()

        /**
         * Store [values] (key → JSON string) for the current user. Errors are only logged.
         */
        suspend fun put(values: Map<String, String>) {
            val userId = serverRepository.currentUser?.id ?: return
            if (api.accessToken == null) return
            try {
                mutex.withLock {
                    val current =
                        api.displayPreferencesApi
                            .getDisplayPreferences(
                                displayPreferencesId = DISPLAY_PREFERENCES_ID,
                                userId = userId,
                                client = CLIENT,
                            ).content
                    api.displayPreferencesApi.updateDisplayPreferences(
                        displayPreferencesId = DISPLAY_PREFERENCES_ID,
                        userId = userId,
                        client = CLIENT,
                        data = current.copy(customPrefs = current.customPrefs + values),
                    )
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Could not store %s on the server", values.keys)
            }
        }

        companion object {
            /** Read by jellyfin-mcp, keep in sync with `TELEMETRY_PREFS_ID` there */
            const val DISPLAY_PREFERENCES_ID = "wholphinix-telemetry"
            const val CLIENT = "Wholphinix"
        }
    }

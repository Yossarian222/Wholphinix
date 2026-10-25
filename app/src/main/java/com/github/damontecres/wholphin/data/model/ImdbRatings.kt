package com.github.damontecres.wholphin.data.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.hours

/**
 * IMDb ratings of the library's titles (IMDb id → rating 1-10), read from the ČSFD plugin's `/Csfd/Imdb`.
 * Shown next to the ČSFD rating only; the user's own rating stays ČSFD's.
 */
object ImdbRatings {
    var ratings by mutableStateOf<Map<String, Double>>(emptyMap())
        private set

    /** Set by the service that can reach the plugin; null result = plugin missing or unreachable */
    var loader: (suspend () -> Map<String, Double>?)? = null

    private val mutex = Mutex()
    private var loadedAt = 0L

    // ponytail: one map for the whole app, not per server/user; the plugin refreshes it daily anyway
    suspend fun ensureLoaded() {
        val load = loader ?: return
        mutex.withLock {
            if (System.currentTimeMillis() - loadedAt < 6.hours.inWholeMilliseconds) return
            loadedAt = System.currentTimeMillis()
            load()?.let { ratings = it }
        }
    }
}

package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.model.BaseItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * Red dot at "Chcem vidieť (ČSFD)" in the nav drawer (#48): a title from the watchlist that was missing in the
 * library (requested in Seerr) and is in the library now.
 *
 * Remembers the ČSFD ids of the missing titles from every watchlist load. When one of them later comes back as a
 * library item, it has arrived and the dot is shown until the watchlist screen is opened. Kept in shared preferences,
 * so a film that was downloaded while the app was closed still lights up the dot.
 *
 * Since that only works for titles the app saw missing before (not for films requested before the update, or
 * beyond the few missing titles of the home row), Seerr is asked too (#55): a request whose media became available
 * since the previous check lights up the dot as well.
 */
@Singleton
class WatchlistArrivalsService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        // Provider: SeerrService is not needed until the first check
        private val seerrService: Provider<SeerrService>,
    ) {
        private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

        private val _hasNew = MutableStateFlow(false)

        /** True while there is a newly downloaded watchlist title the user has not seen yet */
        val hasNew: StateFlow<Boolean> = _hasNew

        init {
            try {
                _hasNew.value = prefs.getStringSet(KEY_ARRIVED, null).orEmpty().isNotEmpty()
            } catch (ex: Exception) {
                Timber.w(ex, "Could not read the watchlist arrivals")
            }
        }

        /**
         * Called with every loaded watchlist: library items have [BaseItem.inLibrary], the missing ones not
         */
        @Synchronized
        fun onWatchlistLoaded(items: List<BaseItem>) {
            try {
                val missingNow = items.filter { !it.inLibrary }.mapNotNull { it.csfdId?.toString() }.toSet()
                val inLibraryNow = items.filter { it.inLibrary }.mapNotNull { it.csfdId?.toString() }.toSet()
                val missingBefore = prefs.getStringSet(KEY_MISSING, null).orEmpty()
                val arrived = missingBefore.intersect(inLibraryNow)
                // The list of missing titles is limited, so keep the older ones until they arrive or get too many
                val missing = (missingNow + (missingBefore - inLibraryNow)).take(MAX_MISSING).toSet()
                val arrivedAll = prefs.getStringSet(KEY_ARRIVED, null).orEmpty() + arrived
                prefs
                    .edit()
                    .putStringSet(KEY_MISSING, missing)
                    .putStringSet(KEY_ARRIVED, arrivedAll)
                    .apply()
                if (arrived.isNotEmpty()) {
                    Timber.i("Watchlist titles now in the library: %s", arrived)
                }
                _hasNew.value = arrivedAll.isNotEmpty()
            } catch (ex: Exception) {
                Timber.w(ex, "Could not update the watchlist arrivals")
            }
        }

        /**
         * Asks Seerr for the latest requests: one whose media became available since the last check has arrived.
         * Seerr may touch the media's update time on every sync, so the available ones are remembered instead. The
         * first check after the update has nothing to compare with: it takes the ones changed in the last few days,
         * so a film downloaded just before the update still shows the dot.
         */
        suspend fun checkSeerr() {
            try {
                val seerr = seerrService.get()
                // Right after the app starts the Seerr connection may not be up yet
                if (withTimeoutOrNull(SEERR_CONNECT_WAIT) { seerr.active.first { it } } == null) {
                    Timber.d("Watchlist arrivals: Seerr not active, skipping")
                    return
                }
                val requests =
                    seerr.api.requestApi
                        .requestGet(take = SEERR_TAKE)
                        .results
                        .orEmpty()
                val available =
                    requests
                        .mapNotNull { it.media }
                        .filter { it.tmdbId != null && (it.status == STATUS_PARTIALLY_AVAILABLE || it.status == STATUS_AVAILABLE) }
                val availableIds = available.map { "tmdb:${it.tmdbId}" }.toSet()
                synchronized(this) {
                    val known = prefs.getStringSet(KEY_KNOWN_AVAILABLE, null)
                    val arrived =
                        if (known == null) {
                            val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(FIRST_CHECK_DAYS_BACK)
                            available
                                .filter { media ->
                                    media.updatedAt
                                        ?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
                                        ?.let { it > since } == true
                                }.map { "tmdb:${it.tmdbId}" }
                                .toSet()
                        } else {
                            availableIds - known
                        }
                    Timber.i(
                        "Watchlist arrivals: %d Seerr requests, %d available, new: %s (first check: %s)",
                        requests.size,
                        availableIds.size,
                        arrived,
                        known == null,
                    )
                    val arrivedAll = prefs.getStringSet(KEY_ARRIVED, null).orEmpty() + arrived
                    prefs
                        .edit()
                        // Only the latest requests are asked for, so the older known ones are kept
                        .putStringSet(KEY_KNOWN_AVAILABLE, known.orEmpty() + availableIds)
                        .putStringSet(KEY_ARRIVED, arrivedAll)
                        .apply()
                    if (arrived.isNotEmpty()) _hasNew.value = true
                }
            } catch (ex: Exception) {
                Timber.w(ex, "Watchlist arrivals: Seerr check failed")
            }
        }

        /** The watchlist screen was opened: the new titles were seen */
        @Synchronized
        fun markSeen() {
            prefs
                .edit()
                .remove(KEY_ARRIVED)
                .apply()
            if (_hasNew.value) Timber.i("Watchlist arrivals: seen")
            _hasNew.value = false
        }

        companion object {
            private const val PREFS = "wholphinix_watchlist_arrivals"
            private const val KEY_MISSING = "missing"
            private const val KEY_ARRIVED = "arrived"
            private const val KEY_KNOWN_AVAILABLE = "known_available"
            private const val MAX_MISSING = 300
            private const val SEERR_TAKE = 50
            private val SEERR_CONNECT_WAIT = 60.seconds
            private const val FIRST_CHECK_DAYS_BACK = 3L
            private const val STATUS_PARTIALLY_AVAILABLE = 4
            private const val STATUS_AVAILABLE = 5
        }
    }

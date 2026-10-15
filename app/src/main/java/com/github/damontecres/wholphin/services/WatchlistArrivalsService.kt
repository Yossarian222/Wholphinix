package com.github.damontecres.wholphin.services

import android.content.Context
import com.github.damontecres.wholphin.data.model.BaseItem
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Red dot at "Chcem vidieť (ČSFD)" in the nav drawer (#48): a title from the watchlist that was missing in the
 * library (requested in Seerr) and is in the library now.
 *
 * Remembers the ČSFD ids of the missing titles from every watchlist load. When one of them later comes back as a
 * library item, it has arrived and the dot is shown until the watchlist screen is opened. Kept in shared preferences,
 * so a film that was downloaded while the app was closed still lights up the dot.
 */
@Singleton
class WatchlistArrivalsService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
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

        /** The watchlist screen was opened: the new titles were seen */
        @Synchronized
        fun markSeen() {
            if (!_hasNew.value) return
            prefs.edit().remove(KEY_ARRIVED).apply()
            _hasNew.value = false
        }

        companion object {
            private const val PREFS = "wholphinix_watchlist_arrivals"
            private const val KEY_MISSING = "missing"
            private const val KEY_ARRIVED = "arrived"
            private const val MAX_MISSING = 300
        }
    }

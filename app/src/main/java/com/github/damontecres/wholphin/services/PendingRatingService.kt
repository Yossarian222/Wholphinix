package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.util.ExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.tvShowsApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds

/**
 * A finished movie (or series) waiting for its ČSFD rating
 */
data class PendingRating(
    val itemId: UUID,
    val csfdId: Int,
    val title: String,
)

/**
 * Asks for a ČSFD rating after a movie or the last episode of a series has been watched to the end.
 *
 * The player reports the movie when it is left, the root composable shows the prompt once the player is gone.
 */
@Singleton
class PendingRatingService
    @Inject
    constructor(
        private val api: ApiClient,
        private val csfdTvTipsService: CsfdTvTipsService,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
    ) {
        private val _pending = MutableStateFlow<PendingRating?>(null)
        val pending: StateFlow<PendingRating?> = _pending

        // Items already asked about (rated or dismissed) in this app session
        private val handled = ConcurrentHashMap.newKeySet<UUID>()

        /**
         * Called when the player is left.
         *
         * @param item the movie that was playing
         * @param watched whether it ended or was left after at least [WATCHED_FRACTION] of its length
         */
        fun onPlaybackLeft(
            item: BaseItem,
            watched: Boolean,
        ) {
            when (item.type) {
                BaseItemKind.MOVIE -> {
                    val csfdId =
                        item.csfdId ?: run {
                            Timber.i("No rating prompt for %s: no ČSFD id", item.id)
                            return
                        }
                    ask(item, watched) { PendingRating(item.id, csfdId, item.name ?: "") }
                }

                // After the last episode of a series, ask about the whole series
                BaseItemKind.EPISODE -> {
                    val seriesId =
                        item.data.seriesId ?: run {
                            Timber.i("No rating prompt for %s: episode without a series", item.id)
                            return
                        }
                    ask(item, watched, seriesId) { seriesRating(seriesId, item.id) }
                }

                else -> {
                    Timber.i("No rating prompt for %s: %s is not rated", item.id, item.type)
                }
            }
        }

        /**
         * @param key the item the rating is for, asked about at most once per app session
         * @param rating builds the prompt or returns null if there is nothing to ask
         */
        private fun ask(
            item: BaseItem,
            watched: Boolean,
            key: UUID = item.id,
            rating: suspend () -> PendingRating?,
        ) {
            if (key in handled) {
                Timber.i("No rating prompt for %s: already asked in this session", key)
                return
            }
            val wasPlayed = item.played
            scope.launch(ExceptionHandler()) {
                val finished =
                    watched ||
                        // The server may use its own threshold for marking an item played,
                        // give the stop report a moment to arrive first
                        (!wasPlayed && playedOnServer(item.id))
                if (!finished) {
                    Timber.i("No rating prompt for %s: not watched enough", item.id)
                    return@launch
                }
                val pending =
                    rating() ?: run {
                        Timber.i("No rating prompt for %s: not the last episode or the series has no ČSFD id", item.id)
                        return@launch
                    }
                // Only a known rating skips the prompt: an empty map may just mean the plugin could not read the
                // ČSFD profile (or has none set), and then rating still works through the plugin's account or fails
                // with a message
                val myRatings = csfdTvTipsService.getMyRatings()
                if (myRatings.containsKey(pending.csfdId)) {
                    Timber.i("No rating prompt for %s: ČSFD %s already rated", pending.itemId, pending.csfdId)
                    return@launch
                }
                if (myRatings.isEmpty()) Timber.i("ČSFD ratings unknown (empty or not readable), asking anyway")
                if (handled.add(key)) {
                    Timber.i("Asking for a ČSFD rating of %s (%s)", pending.itemId, pending.csfdId)
                    _pending.update { pending }
                } else {
                    Timber.i("No rating prompt for %s: already asked in this session", key)
                }
            }
        }

        /** The series to rate if [episodeId] is its last episode and the series has a ČSFD id */
        private suspend fun seriesRating(
            seriesId: UUID,
            episodeId: UUID,
        ): PendingRating? {
            val laterEpisodes =
                api.tvShowsApi
                    .getEpisodes(
                        seriesId = seriesId,
                        startItemId = episodeId,
                        limit = 2,
                    ).content.items
            if (laterEpisodes.size > 1) return null
            val series = api.userLibraryApi.getItem(seriesId).content
            val csfdId = series.providerIds.csfdId() ?: return null
            return PendingRating(seriesId, csfdId, series.name ?: "")
        }

        /** The prompt was answered or dismissed */
        fun clear() {
            _pending.update { null }
        }

        private suspend fun playedOnServer(itemId: UUID): Boolean {
            delay(2.seconds)
            return try {
                api.userLibraryApi
                    .getItem(itemId)
                    .content.userData
                    ?.played == true
            } catch (ex: Exception) {
                Timber.w(ex, "Could not check whether %s was played", itemId)
                false
            }
        }

        companion object {
            const val WATCHED_FRACTION = 0.9
        }
    }

/** The ČSFD id from the ČSFD metadata plugin, if any */
val BaseItem.csfdId: Int?
    get() = data.providerIds.csfdId()

private fun Map<String, String?>?.csfdId(): Int? =
    this
        ?.entries
        ?.firstOrNull { it.key.equals("Csfd", ignoreCase = true) }
        ?.value
        ?.toIntOrNull()

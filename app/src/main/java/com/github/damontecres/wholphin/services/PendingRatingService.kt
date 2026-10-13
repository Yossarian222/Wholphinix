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
 * A finished movie (or season, or series) waiting for its ČSFD rating
 *
 * @param title the movie's or series' name
 * @param seasonNumber for a season, its number, shown after [title] (e.g. "Dark – 2. séria")
 */
data class PendingRating(
    val itemId: UUID,
    val csfdId: Int,
    val title: String,
    val seasonNumber: Int? = null,
)

/**
 * Asks for a ČSFD rating after a movie or the last episode of a season has been watched to the end.
 *
 * The player reports the item when it is left, the root composable shows the prompt once the player is gone.
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

        // Titles already asked about (rated or dismissed) in this app session
        private val handled = ConcurrentHashMap.newKeySet<UUID>()

        /**
         * Called when the player is left.
         *
         * A movie is asked about itself. An episode only when it is the last one of its season, and then the season is
         * asked about (or the whole series when the season has no ČSFD id); episodes are rated only on their page.
         *
         * @param item the movie or episode that was playing
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
                    if (item.id in handled) {
                        Timber.i("No rating prompt for %s: already asked in this session", item.id)
                        return
                    }
                    ask(item, watched) {
                        val pending = PendingRating(item.id, csfdId, item.name ?: "")
                        pending.takeUnless { csfdTvTipsService.getMyRatings().isRated(it) }
                    }
                }

                BaseItemKind.EPISODE -> {
                    val seriesId =
                        item.data.seriesId ?: run {
                            Timber.i("No rating prompt for %s: episode without a series", item.id)
                            return
                        }
                    val seasonId =
                        item.data.seasonId ?: item.data.parentId ?: run {
                            Timber.i("No rating prompt for %s: episode without a season", item.id)
                            return
                        }
                    ask(item, watched) { seasonRating(item, seriesId, seasonId) }
                }

                else -> {
                    Timber.i("No rating prompt for %s: %s is not rated", item.id, item.type)
                }
            }
        }

        /**
         * @param rating builds the prompt or returns null if there is nothing to ask (e.g. already rated)
         */
        private fun ask(
            item: BaseItem,
            watched: Boolean,
            rating: suspend () -> PendingRating?,
        ) {
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
                        Timber.i("No rating prompt for %s: nothing to rate or already rated", item.id)
                        return@launch
                    }
                // Each title is asked about at most once per app session
                if (handled.add(pending.itemId)) {
                    Timber.i("Asking for a ČSFD rating: %s", pending)
                    _pending.update { pending }
                } else {
                    Timber.i("No rating prompt for %s: already asked in this session", pending.itemId)
                }
            }
        }

        /** The season (or series) to rate if [episode] is the last one of its season, see [episodeRatingTarget] */
        private suspend fun seasonRating(
            episode: BaseItem,
            seriesId: UUID,
            seasonId: UUID,
        ): PendingRating? {
            val laterEpisodes =
                api.tvShowsApi
                    .getEpisodes(
                        seriesId = seriesId,
                        seasonId = seasonId,
                        isMissing = false,
                        startItemId = episode.id,
                        limit = 2,
                    ).content.items
            if (laterEpisodes.size > 1) {
                Timber.i("No rating prompt for %s: not the last episode of season %s", episode.id, seasonId)
                return null
            }
            val season = api.userLibraryApi.getItem(seasonId).content
            val seriesName = season.seriesName ?: episode.data.seriesName ?: ""
            val seasonCandidate =
                RatingCandidate(
                    itemId = season.id,
                    csfdId = season.providerIds.csfdId(),
                    title =
                        if (season.indexNumber != null) {
                            seriesName
                        } else {
                            listOfNotNull(seriesName, season.name).joinToString(" – ")
                        },
                    seasonNumber = season.indexNumber,
                )
            val seriesCandidate =
                if (seasonCandidate.csfdId == null) {
                    Timber.i("Season %s has no ČSFD id, offering the series %s", seasonId, seriesId)
                    val series = api.userLibraryApi.getItem(seriesId).content
                    RatingCandidate(series.id, series.providerIds.csfdId(), series.name ?: seriesName)
                } else {
                    null
                }
            if (seasonCandidate.csfdId == null && seriesCandidate?.csfdId == null) {
                Timber.i("No rating prompt for %s: neither the season nor the series has a ČSFD id", episode.id)
                return null
            }
            return episodeRatingTarget(
                lastOfSeason = true,
                season = seasonCandidate,
                series = seriesCandidate,
                myRatings = csfdTvTipsService.getMyRatings(),
            )
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

/** A title that could be asked about, see [episodeRatingTarget] */
internal data class RatingCandidate(
    val itemId: UUID,
    val csfdId: Int?,
    val title: String,
    val seasonNumber: Int? = null,
)

/**
 * Whether [pending] is already rated in [this] (ČSFD id → stars). Only a known rating counts: an empty map may just
 * mean the plugin could not read the ČSFD profile (or has none set), and then rating still works through the plugin's
 * account or fails with a message.
 */
internal fun Map<Int, Int>.isRated(pending: PendingRating): Boolean {
    val rated = containsKey(pending.csfdId)
    if (rated) {
        Timber.i("ČSFD %s (%s) already rated", pending.csfdId, pending.itemId)
    } else if (isEmpty()) {
        Timber.i("ČSFD ratings unknown (empty or not readable), asking anyway")
    }
    return rated
}

/**
 * What to ask about after an episode was watched to the end: nothing in the middle of a season; after the last episode
 * of a season that [season], or the whole [series] when the season has no ČSFD id; nothing when that one is already
 * rated in [myRatings] (ČSFD id → stars) or there is no ČSFD id at all.
 */
internal fun episodeRatingTarget(
    lastOfSeason: Boolean,
    season: RatingCandidate?,
    series: RatingCandidate?,
    myRatings: Map<Int, Int>,
): PendingRating? {
    if (!lastOfSeason) return null
    val target = listOfNotNull(season, series).firstOrNull { it.csfdId != null } ?: return null
    val csfdId = target.csfdId ?: return null
    val pending = PendingRating(target.itemId, csfdId, target.title, target.seasonNumber)
    return pending.takeUnless { myRatings.isRated(it) }
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

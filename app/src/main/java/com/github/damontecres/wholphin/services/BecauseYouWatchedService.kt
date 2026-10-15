package com.github.damontecres.wholphin.services

import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.ui.HomeItemFields
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import org.jellyfin.sdk.model.api.request.GetSimilarItemsRequest
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Rows "Pretože ste pozreli <film>" on the home page (#50): for each of the last [MAX_ROWS] fully watched movies,
 * similar movies from the library that were not watched yet.
 *
 * All rows are computed together, so a movie similar to more of the watched ones is only in the first (higher) row.
 * The result is cached for a short time, because the home page asks for every row separately.
 */
@Singleton
class BecauseYouWatchedService
    @Inject
    constructor(
        private val api: ApiClient,
    ) {
        data class Row(
            val watched: BaseItem,
            val items: List<BaseItem>,
        )

        private val mutex = Mutex()
        private var cached: Cached? = null

        private data class Cached(
            val userId: UUID,
            val time: Long,
            val rows: List<Row>,
        )

        /**
         * The row at [index] (0 = the most recently watched movie) or null if there is no such row
         */
        suspend fun getRow(
            userId: UUID,
            index: Int,
            isRefresh: Boolean,
        ): Row? = getRows(userId, isRefresh).getOrNull(index)

        suspend fun getRows(
            userId: UUID,
            isRefresh: Boolean,
        ): List<Row> =
            mutex.withLock {
                val now = System.currentTimeMillis()
                cached
                    ?.takeIf { it.userId == userId && now - it.time < if (isRefresh) REFRESH_TTL_MS else CACHE_TTL_MS }
                    ?.let { return@withLock it.rows }
                val rows = fetchRows(userId)
                cached = Cached(userId, now, rows)
                rows
            }

        private suspend fun fetchRows(userId: UUID): List<Row> {
            val watched =
                GetItemsRequestHandler
                    .execute(
                        api,
                        GetItemsRequest(
                            userId = userId,
                            includeItemTypes = listOf(BaseItemKind.MOVIE),
                            recursive = true,
                            isPlayed = true,
                            sortBy = listOf(ItemSortBy.DATE_PLAYED),
                            sortOrder = listOf(SortOrder.DESCENDING),
                            limit = MAX_ROWS,
                            fields = HomeItemFields,
                        ),
                    ).toBaseItems()
            // The watched movies themselves never show up as a suggestion
            val seen = watched.map { it.id }.toMutableSet()
            return watched.mapNotNull { movie ->
                try {
                    val similar =
                        api.libraryApi
                            .getSimilarItems(
                                GetSimilarItemsRequest(
                                    itemId = movie.id,
                                    userId = userId,
                                    fields = HomeItemFields,
                                    limit = SIMILAR_LIMIT,
                                ),
                            ).content.items
                            .map { BaseItem(it, false) }
                            .filter { it.type == BaseItemKind.MOVIE && !it.played && seen.add(it.id) }
                            .take(ROW_SIZE)
                    if (similar.isEmpty()) null else Row(movie, similar)
                } catch (ex: Exception) {
                    Timber.w(ex, "Could not get similar items for %s", movie.id)
                    null
                }
            }
        }

        companion object {
            const val MAX_ROWS = 3
            private const val ROW_SIZE = 20

            // More than a row, the watched and duplicate ones are filtered out
            private const val SIMILAR_LIMIT = 50
            private const val CACHE_TTL_MS = 10 * 60_000L
            private const val REFRESH_TTL_MS = 30_000L
        }
    }

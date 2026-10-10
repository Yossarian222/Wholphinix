package com.github.damontecres.wholphin.ui.audiobookshelf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.audiobookshelf.AbsAuthor
import com.github.damontecres.wholphin.services.audiobookshelf.AbsConfig
import com.github.damontecres.wholphin.services.audiobookshelf.AbsConnection
import com.github.damontecres.wholphin.services.audiobookshelf.AbsEpisode
import com.github.damontecres.wholphin.services.audiobookshelf.AbsLibrary
import com.github.damontecres.wholphin.services.audiobookshelf.AbsLibraryItem
import com.github.damontecres.wholphin.services.audiobookshelf.AbsMediaProgress
import com.github.damontecres.wholphin.services.audiobookshelf.AbsSeries
import com.github.damontecres.wholphin.services.audiobookshelf.AudiobookshelfService
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import timber.log.Timber
import javax.inject.Inject

/** A tab of the Audiobookshelf page, like the tabs of a Jellyfin library */
sealed interface AbsTab {
    val key: String

    data class Library(
        val library: AbsLibrary,
    ) : AbsTab {
        override val key get() = "library_${library.id}"
    }

    data object Continue : AbsTab {
        override val key get() = "continue"
    }

    data class Series(
        val library: AbsLibrary,
        val showLibraryName: Boolean,
    ) : AbsTab {
        override val key get() = "series_${library.id}"
    }

    data class Authors(
        val library: AbsLibrary,
        val showLibraryName: Boolean,
    ) : AbsTab {
        override val key get() = "authors_${library.id}"
    }
}

/** Books of one series or author, shown as a grid in place of the tabs */
data class AbsSubGrid(
    val title: String,
    val items: List<AbsLibraryItem>,
)

/** The item detail page */
data class AbsDetailState(
    val item: AbsLibraryItem,
    val loading: Boolean = true,
    /** Episodes: started (not finished) first, then newest first */
    val episodes: List<AbsEpisode> = emptyList(),
    val error: String? = null,
)

data class AbsBrowseState(
    val loading: Boolean = true,
    val error: String? = null,
    val conn: AbsConnection? = null,
    val tabs: List<AbsTab> = emptyList(),
    val items: Map<String, List<AbsLibraryItem>> = emptyMap(),
    val inProgress: List<AbsLibraryItem> = emptyList(),
    val series: Map<String, List<AbsSeries>> = emptyMap(),
    val authors: Map<String, List<AbsAuthor>> = emptyMap(),
    /** Sort per library id */
    val sort: Map<String, SortAndDirection> = emptyMap(),
    /** Saved progress of podcast episodes keyed by episode id */
    val episodeProgress: Map<String, AbsMediaProgress> = emptyMap(),
    /** Saved progress of books keyed by library item id */
    val itemProgress: Map<String, AbsMediaProgress> = emptyMap(),
    val subGrid: AbsSubGrid? = null,
    val detail: AbsDetailState? = null,
)

/** Sort options offered for an Audiobookshelf library */
val AbsSortOptions =
    listOf(
        ItemSortBy.SORT_NAME,
        ItemSortBy.DATE_CREATED,
        ItemSortBy.PREMIERE_DATE,
        ItemSortBy.DATE_PLAYED,
        ItemSortBy.RUNTIME,
        ItemSortBy.RANDOM,
    )

/**
 * Loads the Audiobookshelf libraries (podcasts and books), the items in progress, series, authors and
 * item details for [AudiobookshelfPage]. Reloads whenever the saved connection changes.
 */
@HiltViewModel
class AbsBrowseViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val service: AudiobookshelfService,
        private val backdropService: BackdropService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AbsBrowseState())
        val state: StateFlow<AbsBrowseState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                service.config
                    .filter { it.isComplete }
                    .distinctUntilChanged()
                    .collect { load() }
            }
        }

        /** Loads everything shown on the tabs */
        fun load() {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                try {
                    val cfg = service.config.first()
                    loadAll(cfg)
                } catch (ex: Exception) {
                    Timber.e(ex, "Audiobookshelf load failed")
                    _state.update { it.copy(loading = false, error = context.absErrorMessage(ex, "Connection error")) }
                }
            }
        }

        // coroutineScope so a failed request fails only this call, caught by the caller
        private suspend fun loadAll(cfg: AbsConfig) =
            coroutineScope {
                val (conn, libraries) = service.withConnection(cfg) { c -> service.libraries(c) }
                val audioLibraries =
                    libraries.libraries.filter { it.mediaType == "podcast" || it.mediaType == "book" }
                val bookLibraries = audioLibraries.filter { it.mediaType == "book" }
                val itemsAsync =
                    audioLibraries.map { lib ->
                        async { lib.id to service.libraryItems(conn, lib.id).results }
                    }
                val seriesAsync =
                    bookLibraries.map { lib ->
                        async {
                            lib.id to
                                try {
                                    service.series(conn, lib.id).results
                                } catch (ex: Exception) {
                                    Timber.w(ex, "Audiobookshelf series failed")
                                    emptyList()
                                }
                        }
                    }
                val authorsAsync =
                    bookLibraries.map { lib ->
                        async {
                            lib.id to
                                try {
                                    service.authors(conn, lib.id).authors
                                } catch (ex: Exception) {
                                    Timber.w(ex, "Audiobookshelf authors failed")
                                    emptyList()
                                }
                        }
                    }
                val inProgressAsync =
                    async {
                        try {
                            service.itemsInProgress(conn).libraryItems
                        } catch (ex: Exception) {
                            Timber.w(ex, "Audiobookshelf items in progress failed")
                            emptyList()
                        }
                    }
                val progress = service.me(conn).mediaProgress
                val items = itemsAsync.awaitAll().toMap()
                val series = seriesAsync.awaitAll().toMap()
                val authors = authorsAsync.awaitAll().toMap()
                val inProgress = inProgressAsync.await()

                val tabs =
                    buildList {
                        audioLibraries.forEach { add(AbsTab.Library(it)) }
                        add(AbsTab.Continue)
                        bookLibraries.forEach { lib ->
                            if (series[lib.id].orEmpty().isNotEmpty()) {
                                add(AbsTab.Series(lib, bookLibraries.size > 1))
                            }
                        }
                        bookLibraries.forEach { lib ->
                            if (authors[lib.id].orEmpty().isNotEmpty()) {
                                add(AbsTab.Authors(lib, bookLibraries.size > 1))
                            }
                        }
                    }
                val episodeProgress =
                    progress.filter { it.episodeId != null }.associateBy { it.episodeId!! }
                val itemProgress =
                    progress.filter { it.episodeId == null }.associateBy { it.libraryItemId }
                _state.update { current ->
                    current.copy(
                        loading = false,
                        conn = conn,
                        tabs = tabs,
                        items =
                            items.mapValues { (libId, list) ->
                                sortItems(
                                    list,
                                    current.sort[libId] ?: SortAndDirection.DEFAULT,
                                    itemProgress,
                                    episodeProgress,
                                )
                            },
                        inProgress = inProgress,
                        series = series.mapValues { (_, list) -> list.sortedBy { it.sortName.lowercase() } },
                        authors = authors.mapValues { (_, list) -> list.sortedBy { it.name.lowercase() } },
                        episodeProgress = episodeProgress,
                        itemProgress = itemProgress,
                    )
                }
                // Like the other libraries, start with a random backdrop until an item is focused
                items.values
                    .flatten()
                    .randomOrNull()
                    ?.let { focusItem(it) }
            }

        /** Reloads the saved progress and the items in progress, e.g. after listening */
        fun refreshProgress() {
            val conn = _state.value.conn ?: return
            viewModelScope.launch {
                try {
                    val progress = service.me(conn).mediaProgress
                    val inProgress = service.itemsInProgress(conn).libraryItems
                    _state.update {
                        it.copy(
                            episodeProgress = progress.filter { p -> p.episodeId != null }.associateBy { p -> p.episodeId!! },
                            itemProgress = progress.filter { p -> p.episodeId == null }.associateBy { p -> p.libraryItemId },
                            inProgress = inProgress,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Audiobookshelf progress refresh failed")
                }
            }
        }

        fun changeSort(
            libraryId: String,
            sort: SortAndDirection,
        ) {
            _state.update {
                it.copy(
                    sort = it.sort + (libraryId to sort),
                    items =
                        it.items +
                            (
                                libraryId to
                                    sortItems(
                                        it.items[libraryId].orEmpty(),
                                        sort,
                                        it.itemProgress,
                                        it.episodeProgress,
                                    )
                            ),
                )
            }
        }

        /** Shows the item's cover as the page backdrop */
        fun focusItem(item: AbsLibraryItem) {
            val conn = _state.value.conn ?: return
            viewModelScope.launch {
                backdropService.submit("abs_${item.id}", service.coverUrl(conn, item.id))
            }
        }

        fun openSeries(series: AbsSeries) {
            _state.update { it.copy(subGrid = AbsSubGrid(series.name, series.books)) }
        }

        fun openAuthor(author: AbsAuthor) {
            val conn = _state.value.conn ?: return
            viewModelScope.launch {
                try {
                    val full = service.author(conn, author.id)
                    _state.update {
                        it.copy(subGrid = AbsSubGrid(author.name, full.libraryItems.sortedBy { item -> item.sortTitle.lowercase() }))
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Audiobookshelf author failed")
                    _state.update { it.copy(error = context.absErrorMessage(ex, "Connection error")) }
                }
            }
        }

        fun closeSubGrid() {
            _state.update { it.copy(subGrid = null) }
        }

        /** Opens the detail page of a podcast or book */
        fun openItem(item: AbsLibraryItem) {
            val conn = _state.value.conn ?: return
            _state.update { it.copy(detail = AbsDetailState(item = item)) }
            focusItem(item)
            viewModelScope.launch {
                try {
                    val full = service.podcast(conn, item.id)
                    val progress = _state.value.episodeProgress
                    val sorted =
                        full.media
                            ?.episodes
                            .orEmpty()
                            .sortedWith(
                                compareBy<AbsEpisode>(
                                    { if (isStarted(progress[it.id])) 0 else 1 },
                                    { -(it.publishedAt ?: 0L) },
                                ),
                            )
                    _state.update {
                        if (it.detail?.item?.id != item.id) return@update it
                        it.copy(
                            detail =
                                AbsDetailState(
                                    // Keep the type from the list in case the expanded item lacks it
                                    item = full.copy(mediaType = full.mediaType ?: item.mediaType),
                                    loading = false,
                                    episodes = sorted,
                                ),
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Audiobookshelf item load failed")
                    _state.update {
                        it.copy(detail = it.detail?.copy(loading = false, error = context.absErrorMessage(ex, "Connection error")))
                    }
                }
            }
        }

        fun closeDetail() {
            _state.update { it.copy(detail = null) }
        }

        private fun isStarted(progress: AbsMediaProgress?): Boolean = progress != null && !progress.isFinished && progress.currentTime > 0
    }

private val AbsSeries.sortName: String get() = nameIgnorePrefix?.takeIf { it.isNotBlank() } ?: name

/** Last time anything in the item was listened to */
private fun lastPlayed(
    item: AbsLibraryItem,
    itemProgress: Map<String, AbsMediaProgress>,
    episodeProgress: Map<String, AbsMediaProgress>,
): Long =
    if (item.isPodcast) {
        episodeProgress.values
            .filter { it.libraryItemId == item.id }
            .maxOfOrNull { it.lastUpdate } ?: 0L
    } else {
        itemProgress[item.id]?.lastUpdate ?: 0L
    }

internal fun sortItems(
    items: List<AbsLibraryItem>,
    sort: SortAndDirection,
    itemProgress: Map<String, AbsMediaProgress>,
    episodeProgress: Map<String, AbsMediaProgress>,
): List<AbsLibraryItem> {
    val comparator: Comparator<AbsLibraryItem> =
        when (sort.sort) {
            ItemSortBy.DATE_CREATED -> {
                compareBy<AbsLibraryItem> { it.addedAt ?: 0L }
            }

            ItemSortBy.PREMIERE_DATE -> {
                compareBy<AbsLibraryItem> {
                    it.media?.metadata?.publishedYear
                        ?: it.media?.metadata?.releaseDate
                        ?: ""
                }
            }

            ItemSortBy.DATE_PLAYED -> {
                compareBy<AbsLibraryItem> { lastPlayed(it, itemProgress, episodeProgress) }
            }

            ItemSortBy.RUNTIME -> {
                compareBy<AbsLibraryItem> { it.media?.duration ?: 0.0 }
            }

            ItemSortBy.RANDOM -> {
                return items.shuffled()
            }

            else -> {
                compareBy<AbsLibraryItem> { it.sortTitle.lowercase() }
            }
        }
    val sorted = items.sortedWith(comparator.thenBy { it.sortTitle.lowercase() })
    return if (sort.direction == SortOrder.DESCENDING) sorted.reversed() else sorted
}

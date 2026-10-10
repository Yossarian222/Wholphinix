package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.CsfdTvTipsService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.OneTimeLaunchedEffect
import com.github.damontecres.wholphin.ui.SlimItemFields
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.toBaseItems
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ItemFields
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID
import kotlin.math.roundToInt

/**
 * An entry of the library ranking grid: an item or the header of the items outside the ČSFD top 1000
 */
@Stable
sealed interface RankingEntry : CardGridItem

/**
 * An item of the library ranking with its position in the ČSFD best-of rankings (if it is in the top 1000)
 */
@Stable
data class RankedItem(
    val item: BaseItem,
    val csfdRank: Int?,
) : RankingEntry {
    override val gridId: String get() = item.gridId
    override val playable: Boolean get() = item.playable
    override val sortName: String get() = item.sortName
}

/**
 * Full-width separator between the items in the ČSFD top 1000 and the other items sorted by rating
 */
@Stable
data object OutsideTopHeader : RankingEntry {
    override val gridId: String get() = "csfd_outside_top_header"
    override val playable: Boolean get() = false
    override val sortName: String get() = ""
}

@HiltViewModel(assistedFactory = CsfdRankingViewModel.Factory::class)
class CsfdRankingViewModel
    @AssistedInject
    constructor(
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val csfdTvTipsService: CsfdTvTipsService,
        val navigationManager: NavigationManager,
        @Assisted private val parentId: UUID,
        @Assisted private val itemKind: BaseItemKind,
    ) : ViewModel() {
        @AssistedFactory
        interface Factory {
            fun create(
                parentId: UUID,
                itemKind: BaseItemKind,
            ): CsfdRankingViewModel
        }

        private val _state = MutableStateFlow<DataLoadingState<List<RankingEntry>>>(DataLoadingState.Pending)
        val state: StateFlow<DataLoadingState<List<RankingEntry>>> = _state

        fun init() {
            _state.update { DataLoadingState.Loading }
            viewModelScope.launchIO {
                try {
                    // The ranks can take minutes on the first call of the week, so the items come first and the
                    // positions are added when they arrive
                    val ranks = async { csfdTvTipsService.getRanks() }
                    // The ČSFD plugin stores the ČSFD rating as the community rating, so this is the library's own chart
                    val items = mutableListOf<Pair<Int, BaseItem>>()
                    var skipped = 0
                    var startIndex = 0
                    // Items without a ČSFD id carry a TMDb/IMDb rating and items with incomplete ČSFD metadata have no
                    // rating at all; fetch extra and drop them, page by page until the ranking is full and all items
                    // rated well enough to be in the ČSFD top 1000 are there (they are listed first by their position)
                    val pageSize = RANKING_SIZE * 3
                    var pages = 0
                    while (pages++ < MAX_PAGES) {
                        val pageItems =
                            GetItemsRequestHandler
                                .execute(
                                    api,
                                    GetItemsRequest(
                                        userId = serverRepository.currentUser?.id,
                                        parentId = parentId,
                                        includeItemTypes = listOf(itemKind),
                                        recursive = true,
                                        fields = SlimItemFields + ItemFields.GENRES,
                                        sortBy = listOf(ItemSortBy.COMMUNITY_RATING, ItemSortBy.SORT_NAME),
                                        sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
                                        startIndex = startIndex,
                                        limit = pageSize,
                                        enableTotalRecordCount = false,
                                    ),
                                ).toBaseItems(true)
                        val selected = selectRanked(pageItems)
                        items.addAll(selected.items)
                        skipped += selected.skippedWithoutRating
                        if (pageItems.size < pageSize || !shouldFetchMore(items.size, pageItems)) break
                        startIndex += pageSize
                    }
                    if (skipped > 0) {
                        Timber.i("ČSFD ranking: skipped %d items with a ČSFD id but no ČSFD rating", skipped)
                    }
                    val csfdRanks = if (ranks.isCompleted) ranks.await() else null
                    _state.update { DataLoadingState.Success(arrange(items, csfdRanks)) }
                    if (csfdRanks == null) {
                        // Re-sort when the positions arrive
                        val lateRanks = ranks.await()
                        if (lateRanks.isNotEmpty()) {
                            _state.update { DataLoadingState.Success(arrange(items, lateRanks)) }
                        }
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching ČSFD ranking")
                    _state.update { DataLoadingState.Error(ex) }
                }
            }
        }

        companion object {
            const val RANKING_SIZE = 250

            /** At most this many pages of [RANKING_SIZE] * 3 items are fetched to fill the ranking */
            private const val MAX_PAGES = 4

            /**
             * Items rated at least this (community rating, 0-10) may be in the ČSFD top 1000 (its lowest rating is
             * around 80 %), so they are all fetched to list the ranked ones first even beyond [RANKING_SIZE]
             */
            const val MIN_TOP_RATING = 7f

            /**
             * Whether to fetch another page after [page] (sorted by rating, descending) with [selectedCount] items
             * selected so far: until there are [RANKING_SIZE] items and the ratings dropped below [MIN_TOP_RATING]
             */
            fun shouldFetchMore(
                selectedCount: Int,
                page: List<BaseItem>,
            ): Boolean {
                val lowest =
                    page
                        .lastOrNull()
                        ?.data
                        ?.communityRating ?: return false
                return selectedCount < RANKING_SIZE || lowest >= MIN_TOP_RATING
            }

            /**
             * Arranges the ranking from [items] (ČSFD id and item pairs sorted by rating): first the items in the ČSFD
             * top 1000 ([ranks]: ČSFD id → position) by their position, then [OutsideTopHeader] and up to [limit] other
             * items by rating. Without positions (not loaded yet or none of the items ranked) just the first [limit]
             * items by rating.
             */
            fun arrange(
                items: List<Pair<Int, BaseItem>>,
                ranks: Map<Int, Int>?,
                limit: Int = RANKING_SIZE,
            ): List<RankingEntry> {
                val withRanks = items.map { (csfdId, item) -> RankedItem(item, ranks?.get(csfdId)) }
                val (ranked, others) = withRanks.partition { it.csfdRank != null }
                if (ranked.isEmpty()) return others.take(limit)
                val rest = others.take(limit)
                return ranked.sortedBy { it.csfdRank } + (if (rest.isNotEmpty()) listOf(OutsideTopHeader) + rest else listOf())
            }

            /**
             * Keeps the entries of [entries] matching [predicate] and [OutsideTopHeader] only when there are items both
             * before and after it
             */
            fun filterEntries(
                entries: List<RankingEntry>,
                predicate: (RankedItem) -> Boolean,
            ): List<RankingEntry> {
                val filtered = entries.filter { it !is RankedItem || predicate(it) }
                val headerIndex = filtered.indexOf(OutsideTopHeader)
                return if (headerIndex == 0 || headerIndex == filtered.lastIndex) {
                    filtered.filter { it !== OutsideTopHeader }
                } else {
                    filtered
                }
            }

            /**
             * Picks up to [limit] items for the ranking from [items] (sorted by rating): only items with a ČSFD id and a
             * ČSFD rating (community rating). Items with a ČSFD id but no rating have incomplete metadata and are counted
             * in [RankingSelection.skippedWithoutRating].
             */
            fun selectRanked(
                items: List<BaseItem>,
                limit: Int = Int.MAX_VALUE,
            ): RankingSelection {
                val selected = mutableListOf<Pair<Int, BaseItem>>()
                var skipped = 0
                for (item in items) {
                    if (selected.size >= limit) break
                    val csfdId =
                        item.data.providerIds
                            ?.entries
                            ?.firstOrNull { it.key.equals("Csfd", ignoreCase = true) }
                            ?.value
                            ?.toIntOrNull()
                            ?: continue
                    if (item.data.communityRating == null) {
                        skipped++
                        continue
                    }
                    selected.add(csfdId to item)
                }
                return RankingSelection(selected, skipped)
            }
        }
    }

/**
 * Result of [CsfdRankingViewModel.selectRanked]: ČSFD id and item pairs, and how many were left out for missing a rating
 */
data class RankingSelection(
    val items: List<Pair<Int, BaseItem>>,
    val skippedWithoutRating: Int,
)

/**
 * The "Rebríčky" tab of a library: the items in the ČSFD top 1000 by their position, then the other best rated items
 */
@Composable
fun CsfdRankingGrid(
    parentId: UUID,
    itemKind: BaseItemKind,
    modifier: Modifier = Modifier,
    viewModel: CsfdRankingViewModel =
        hiltViewModel<CsfdRankingViewModel, CsfdRankingViewModel.Factory>(
            creationCallback = { it.create(parentId, itemKind) },
        ),
) {
    OneTimeLaunchedEffect { viewModel.init() }
    val state by viewModel.state.collectAsState()
    val gridFocusRequester = remember { FocusRequester() }
    when (val st = state) {
        DataLoadingState.Pending,
        DataLoadingState.Loading,
        -> {
            LoadingPage(modifier.focusable())
        }

        is DataLoadingState.Error -> {
            ErrorMessage(st, modifier.focusable())
        }

        is DataLoadingState.Success<List<RankingEntry>> -> {
            var minRating by rememberSaveable { mutableStateOf<Int?>(null) }
            var decade by rememberSaveable { mutableStateOf<Int?>(null) }
            var genre by rememberSaveable { mutableStateOf<String?>(null) }
            val shown =
                remember(st.data, minRating, decade, genre) {
                    CsfdRankingViewModel.filterEntries(st.data) { ranked ->
                        val dto = ranked.item.data
                        val percent = dto.communityRating?.times(10)?.roundToInt() ?: 0
                        val year = dto.productionYear
                        (minRating == null || percent >= minRating!!) &&
                            (decade == null || (year != null && year / 10 * 10 == decade)) &&
                            (genre == null || dto.genres.orEmpty().any { it.equals(genre, ignoreCase = true) })
                    }
                }
            Column(modifier = modifier) {
                CsfdRankingFilters(
                    items = st.data.filterIsInstance<RankedItem>(),
                    minRating = minRating,
                    decade = decade,
                    genre = genre,
                    onMinRating = { minRating = it },
                    onDecade = { decade = it },
                    onGenre = { genre = it },
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                )
                LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
                CardGrid(
                    pager = shown,
                    onClickItem = { _, entry ->
                        if (entry is RankedItem) viewModel.navigationManager.navigateTo(entry.item.destination())
                    },
                    onLongClickItem = { _, _ -> },
                    onClickPlay = { _, _ -> },
                    letterPosition = { 0 },
                    gridFocusRequester = gridFocusRequester,
                    showJumpButtons = false,
                    showLetterButtons = false,
                    modifier = Modifier.fillMaxSize(),
                    initialPosition = 0,
                    positionCallback = { _, _ -> },
                    columns = 6,
                    spacing = 16.dp,
                    fullLineItem = { it === OutsideTopHeader },
                    cardContent = { (entry, index, onClick, onLongClick, widthPx, mod) ->
                        if (entry === OutsideTopHeader) {
                            Text(
                                text = stringResource(R.string.csfd_outside_top),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(top = 16.dp, start = 4.dp),
                            )
                        } else {
                            val ranked = entry as? RankedItem
                            Box(modifier = mod) {
                                GridCard(
                                    item = ranked?.item,
                                    onClick = onClick,
                                    onLongClick = onLongClick,
                                    showTitle = true,
                                    // Without a size the card does not build an image URL
                                    fillWidth = widthPx,
                                    imageContentScale = ContentScale.Crop,
                                )
                                ranked?.csfdRank?.let { CsfdRankBadge(it, Modifier.align(Alignment.TopStart)) }
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Filter buttons above the ranking: minimal ČSFD rating, decade and genre
 */
@Composable
private fun CsfdRankingFilters(
    items: List<RankedItem>,
    minRating: Int?,
    decade: Int?,
    genre: String?,
    onMinRating: (Int?) -> Unit,
    onDecade: (Int?) -> Unit,
    onGenre: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val all = stringResource(R.string.csfd_filter_all)
    var dialog by remember { mutableStateOf<Pair<String, List<DialogItemEntry>>?>(null) }
    val decades =
        remember(items) {
            items
                .mapNotNull {
                    it.item.data.productionYear
                        ?.let { y -> y / 10 * 10 }
                }.distinct()
                .sortedDescending()
        }
    val genres =
        remember(items) {
            items
                .flatMap {
                    it.item.data.genres
                        .orEmpty()
                }.groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key }
        }
    val ratingTitle = stringResource(R.string.csfd_filter_rating)
    val yearTitle = stringResource(R.string.csfd_filter_year)
    val genreTitle = stringResource(R.string.csfd_filter_genre)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = modifier) {
        Button(onClick = {
            dialog = ratingTitle to
                (listOf<Int?>(null) + listOf(90, 80, 70, 60, 50)).map { value ->
                    DialogItem(text = value?.let { "$it %+" } ?: all) { onMinRating(value) }
                }
        }) { Text("$ratingTitle: ${minRating?.let { "$it %+" } ?: all}") }
        Button(onClick = {
            dialog = yearTitle to
                (listOf<Int?>(null) + decades).map { value ->
                    DialogItem(text = value?.let { "$it–${it + 9}" } ?: all) { onDecade(value) }
                }
        }) { Text("$yearTitle: ${decade?.let { "$it–${it + 9}" } ?: all}") }
        Button(onClick = {
            dialog = genreTitle to
                (listOf<String?>(null) + genres).map { value ->
                    DialogItem(text = value ?: all) { onGenre(value) }
                }
        }) { Text("$genreTitle: ${genre ?: all}") }
    }
    dialog?.let { (title, entries) ->
        DialogPopup(
            showDialog = true,
            title = title,
            dialogItems = entries,
            onDismissRequest = { dialog = null },
            waitToLoad = false,
        )
    }
}

/**
 * Position in the ČSFD rankings, in the ČSFD red
 */
@Composable
fun CsfdRankBadge(
    rank: Int,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(4.dp)
                .background(csfdColor(100), RoundedCornerShape(4.dp)),
    ) {
        Text(
            text = "$rank.",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

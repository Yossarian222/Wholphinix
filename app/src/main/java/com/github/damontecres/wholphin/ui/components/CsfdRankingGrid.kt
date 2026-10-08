package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
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
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.SortOrder
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import java.util.UUID

/**
 * An item of the library ranking with its position in the ČSFD best-of rankings (if it is in the top 1000)
 */
@Stable
data class RankedItem(
    val item: BaseItem,
    val csfdRank: Int?,
) : CardGridItem {
    override val gridId: String get() = item.gridId
    override val playable: Boolean get() = item.playable
    override val sortName: String get() = item.sortName
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

        private val _state = MutableStateFlow<DataLoadingState<List<RankedItem>>>(DataLoadingState.Pending)
        val state: StateFlow<DataLoadingState<List<RankedItem>>> = _state

        fun init() {
            _state.update { DataLoadingState.Loading }
            viewModelScope.launchIO {
                try {
                    val ranks = async { csfdTvTipsService.getRanks() }
                    // The ČSFD plugin stores the ČSFD rating as the community rating, so this is the library's own chart
                    val items =
                        GetItemsRequestHandler
                            .execute(
                                api,
                                GetItemsRequest(
                                    userId = serverRepository.currentUser?.id,
                                    parentId = parentId,
                                    includeItemTypes = listOf(itemKind),
                                    recursive = true,
                                    fields = SlimItemFields,
                                    sortBy = listOf(ItemSortBy.COMMUNITY_RATING, ItemSortBy.SORT_NAME),
                                    sortOrder = listOf(SortOrder.DESCENDING, SortOrder.ASCENDING),
                                    // Items without a ČSFD id carry a TMDb/IMDb rating; fetch extra and drop them
                                    limit = RANKING_SIZE * 3,
                                    enableTotalRecordCount = false,
                                ),
                            ).toBaseItems(true)
                    val csfdRanks = ranks.await()
                    _state.update {
                        DataLoadingState.Success(
                            items
                                .mapNotNull { item ->
                                    val csfdId =
                                        item.data.providerIds
                                            ?.entries
                                            ?.firstOrNull { it.key.equals("Csfd", ignoreCase = true) }
                                            ?.value
                                            ?.toIntOrNull()
                                            ?: return@mapNotNull null
                                    RankedItem(item, csfdRanks[csfdId])
                                }.take(RANKING_SIZE),
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching ČSFD ranking")
                    _state.update { DataLoadingState.Error(ex) }
                }
            }
        }

        companion object {
            const val RANKING_SIZE = 250
        }
    }

/**
 * The "Rebríčky" tab of a library: best rated items first, each with its position in the ČSFD rankings
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

        is DataLoadingState.Success<List<RankedItem>> -> {
            Box(modifier = modifier) {
                LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
                CardGrid(
                    pager = st.data,
                    onClickItem = { _, ranked -> viewModel.navigationManager.navigateTo(ranked.item.destination()) },
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
                    cardContent = { (ranked, index, onClick, onLongClick, widthPx, mod) ->
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
                    },
                )
            }
        }
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

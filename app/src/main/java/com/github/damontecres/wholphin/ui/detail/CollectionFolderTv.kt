package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import com.github.damontecres.wholphin.data.filter.DefaultTvFilterOptions
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.CollectionFolderFilter
import com.github.damontecres.wholphin.data.model.GetItemsFilter
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.components.CollectionFolderView
import com.github.damontecres.wholphin.ui.components.CsfdRankingGrid
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GenreCardGrid
import com.github.damontecres.wholphin.ui.components.StudioCardGrid
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.components.ViewOptionsPoster
import com.github.damontecres.wholphin.ui.data.SeriesSortOptions
import com.github.damontecres.wholphin.ui.nav.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.CollectionType
import javax.inject.Inject

@HiltViewModel
class CollectionFolderTvViewModel
    @Inject
    constructor(
        val navigationManager: NavigationManager,
    ) : ViewModel()

@Composable
fun CollectionFolderTv(
    preferences: UserPreferences,
    destination: Destination.MediaItem,
    modifier: Modifier = Modifier,
    viewModel: CollectionFolderTvViewModel = hiltViewModel(),
) {
    val tabKeys = preferences.appPreferences.libraryTabs(LibraryTabsKind.TV)
    val tabs = remember(tabKeys) { tabKeys.map { TabDetails(it.title) } }
    val onClickItem =
        remember {
            { position: Int, item: BaseItem ->
                viewModel.navigationManager.navigateTo(item.destination())
            }
        }

    var showHeader by rememberSaveable { mutableStateOf(true) }

    TabbedPage(
        itemId = libraryTabsRememberKey(destination.itemId.toString(), tabKeys),
        tabs = tabs,
        modifier = modifier,
        showTabs = showHeader,
    ) { tabIndex, tabDetails ->
        when (tabKeys.getOrNull(tabIndex)) {
            LibraryTab.LIBRARY -> {
                CollectionFolderView(
                    preferences = preferences,
                    itemId = destination.itemId,
                    initialFilter =
                        CollectionFolderFilter(
                            filter =
                                GetItemsFilter(
                                    includeItemTypes = listOf(BaseItemKind.SERIES),
                                ),
                        ),
                    showTitle = false,
                    recursive = true,
                    sortOptions = SeriesSortOptions,
                    filterOptions = DefaultTvFilterOptions,
                    defaultViewOptions = ViewOptionsPoster,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                    positionCallback = { columns, position ->
                        showHeader = position < columns
                    },
                    onClickItem = onClickItem,
                    playEnabled = false,
                    focusRequesterOnEmpty = tabDetails.tabFocusRequester,
                )
            }

            LibraryTab.GENRES -> {
                GenreCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.SERIES),
                    collectionType = CollectionType.TVSHOWS,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            LibraryTab.STUDIOS -> {
                StudioCardGrid(
                    itemId = destination.itemId,
                    includeItemTypes = listOf(BaseItemKind.SERIES),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            // Rebríčky (ČSFD)
            LibraryTab.CSFD_RANKINGS -> {
                CsfdRankingGrid(
                    parentId = destination.itemId,
                    itemKind = BaseItemKind.SERIES,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .focusRequester(tabDetails.contentFocusRequester),
                )
            }

            LibraryTab.COLLECTIONS, null -> {
                ErrorMessage("Invalid tab index $tabIndex", null)
            }
        }
    }
}

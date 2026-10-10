package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.services.CsfdTvTipsService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.ui.cards.GridCard
import com.github.damontecres.wholphin.ui.components.CircularProgress
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.GridTitle
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.util.DataLoadingState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

@HiltViewModel
class CsfdWatchlistViewModel
    @Inject
    constructor(
        private val serverRepository: ServerRepository,
        private val csfdTvTipsService: CsfdTvTipsService,
        val navigationManager: NavigationManager,
    ) : ViewModel() {
        private val _state = MutableStateFlow<DataLoadingState<List<BaseItem>>>(DataLoadingState.Pending)
        val state: StateFlow<DataLoadingState<List<BaseItem>>> = _state

        init {
            load()
        }

        private fun load() {
            val userId = serverRepository.currentUser?.id ?: return
            _state.update { DataLoadingState.Loading }
            viewModelScope.launchIO {
                try {
                    // Same as the home row (library items first, then the missing ones found in Seerr), but more of
                    // them and a long timeout: the plugin can take a while on the first load and this page has nothing
                    // else to show meanwhile
                    val items =
                        csfdTvTipsService.getWatchlistRowItems(
                            userId = userId,
                            useSeries = true,
                            limit = LIMIT,
                            missing = MISSING,
                            timeout = 90.seconds,
                        )
                    _state.update { DataLoadingState.Success(items) }
                } catch (ex: Exception) {
                    Timber.e(ex, "Error fetching the ČSFD watchlist")
                    _state.update { DataLoadingState.Error(ex) }
                }
            }
        }

        companion object {
            private const val LIMIT = 200
            private const val MISSING = 40
        }
    }

/**
 * Full screen grid of the user's ČSFD watchlist ("Chcem vidieť"), opened from the nav drawer
 */
@Composable
fun CsfdWatchlistPage(
    modifier: Modifier = Modifier,
    viewModel: CsfdWatchlistViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val gridFocusRequester = remember { FocusRequester() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        GridTitle(stringResource(R.string.csfd_watchlist))
        when (val st = state) {
            DataLoadingState.Pending,
            DataLoadingState.Loading,
            -> {
                CsfdWatchlistMessage(
                    text = stringResource(R.string.csfd_watchlist_loading),
                    loading = true,
                )
            }

            is DataLoadingState.Error -> {
                ErrorMessage(st, Modifier.fillMaxSize().focusable())
            }

            is DataLoadingState.Success<List<BaseItem>> -> {
                if (st.data.isEmpty()) {
                    CsfdWatchlistMessage(
                        text = stringResource(R.string.csfd_watchlist_empty),
                        loading = false,
                    )
                } else {
                    LaunchedEffect(Unit) { gridFocusRequester.tryRequestFocus() }
                    CardGrid(
                        pager = st.data,
                        onClickItem = { index, item -> viewModel.navigationManager.navigateTo(item.destination(index)) },
                        // Same as a click: missing titles have no context menu and library ones open their details
                        onLongClickItem = { index, item -> viewModel.navigationManager.navigateTo(item.destination(index)) },
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
                        cardContent = { (item, _, onClick, onLongClick, widthPx, mod) ->
                            GridCard(
                                item = item,
                                onClick = onClick,
                                onLongClick = onLongClick,
                                showTitle = true,
                                // Without a size the card does not build an image URL
                                fillWidth = widthPx,
                                imageContentScale = ContentScale.Crop,
                                modifier = mod,
                            )
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CsfdWatchlistMessage(
    text: String,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                // Keeps the focus on the page instead of the nav drawer
                .focusRequester(focusRequester)
                .focusable(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (loading) {
                CircularProgress(Modifier.size(48.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

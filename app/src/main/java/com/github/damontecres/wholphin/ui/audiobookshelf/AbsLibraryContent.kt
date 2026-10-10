package com.github.damontecres.wholphin.ui.audiobookshelf

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.audiobookshelf.AbsLibraryItem
import com.github.damontecres.wholphin.services.audiobookshelf.AbsMediaProgress
import com.github.damontecres.wholphin.ui.AspectRatios
import com.github.damontecres.wholphin.ui.cards.ItemCardImage
import com.github.damontecres.wholphin.ui.cards.SlidingCardText
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.components.ExpandableFaButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.SortByButton
import com.github.damontecres.wholphin.ui.components.TabDetails
import com.github.damontecres.wholphin.ui.components.TabbedPage
import com.github.damontecres.wholphin.ui.data.SortAndDirection
import com.github.damontecres.wholphin.ui.detail.CardGrid
import com.github.damontecres.wholphin.ui.detail.CardGridItem
import com.github.damontecres.wholphin.ui.enableMarquee
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackButton
import com.github.damontecres.wholphin.ui.tryRequestFocus
import com.github.damontecres.wholphin.ui.util.ResArgStringProvider
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.ui.util.StringStringProvider
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.ItemSortBy

/** Asks a grid to take focus once it is shown; [onDone] clears the request */
class AbsFocusRequest(
    val requested: Boolean,
    val onDone: () -> Unit,
)

/** One card of an Audiobookshelf grid: a podcast, book, series or author */
data class AbsCard(
    override val gridId: String,
    override val playable: Boolean,
    override val sortName: String,
    val title: String,
    val subtitle: String?,
    val imageUrl: String?,
    /** 0-100, or null when not started */
    val progressPercent: Double? = null,
    val finished: Boolean = false,
) : CardGridItem

/**
 * The Audiobookshelf library, built from the same pieces as a Jellyfin library: tabs, a sortable grid of
 * cards, a detail page and the Wholphin playback controls.
 */
@Composable
fun AbsBrowser(
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AbsBrowseViewModel = hiltViewModel(),
    playerViewModel: AbsPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val playerState by playerViewModel.state.collectAsState()

    // No background audio: pause when the app goes to the background or another page (e.g. a remote "play") opens
    LifecycleStartEffect(Unit) {
        onStopOrDispose { playerViewModel.pause() }
    }
    // Progress changed on the server (paused or stopped), so update the cards and the "continue" tab
    LaunchedEffect(playerState.savedVersion) {
        if (playerState.savedVersion > 0) viewModel.refreshProgress()
    }

    // Focus moves into the grid when the page opens and when coming back from a detail page or sub grid
    var focusRequested by remember { mutableStateOf(true) }
    val focus = AbsFocusRequest(focusRequested) { focusRequested = false }
    // Keeps the grids' scroll position and focused card while a detail page is shown
    val saveableStateHolder = rememberSaveableStateHolder()

    LaunchedEffect(state.subGrid) {
        if (state.subGrid != null) focusRequested = true
    }

    BackHandler(enabled = state.detail != null) {
        viewModel.closeDetail()
        focusRequested = true
    }
    BackHandler(enabled = state.detail == null && state.subGrid != null) {
        viewModel.closeSubGrid()
        focusRequested = true
    }

    val play = { item: AbsLibraryItem, episodeId: String?, startMs: Long? ->
        val episode =
            episodeId?.let { id ->
                state.detail
                    ?.episodes
                    ?.firstOrNull { it.id == id }
                    ?: item.recentEpisode?.takeIf { it.id == id }
            }
        playerViewModel.play(item, episode, startMs)
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val detail = state.detail
            val subGrid = state.subGrid
            when {
                state.loading && state.tabs.isEmpty() -> {
                    LoadingPage(focusEnabled = false)
                }

                state.error != null && state.tabs.isEmpty() -> {
                    ErrorMessage(state.error, null)
                }

                detail != null -> {
                    AbsDetailPage(
                        detail = detail,
                        conn = state.conn,
                        itemProgress = state.itemProgress[detail.item.id],
                        episodeProgress = state.episodeProgress,
                        onPlay = { episodeId, startMs -> play(detail.item, episodeId, startMs) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                subGrid != null -> {
                    saveableStateHolder.SaveableStateProvider("sub_${subGrid.title}") {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = subGrid.title,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp),
                            )
                            AbsItemGrid(
                                items = subGrid.items,
                                state = state,
                                onOpen = viewModel::openItem,
                                onPlay = { play(it, null, null) },
                                onFocus = viewModel::focusItem,
                                focus = focus,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                }

                else -> {
                    saveableStateHolder.SaveableStateProvider("tabs") {
                        AbsTabs(
                            state = state,
                            viewModel = viewModel,
                            onOpenSettings = onOpenSettings,
                            onPlay = { item, episodeId -> play(item, episodeId, null) },
                            focus = focus,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        playerState.nowPlaying?.let { nowPlaying ->
            if (!playerState.showPlayer) {
                AbsMiniPlayer(
                    nowPlaying = nowPlaying,
                    isPlaying = playerState.isPlaying,
                    positionMs = playerState.positionMs,
                    durationMs = playerState.durationMs,
                    onOpen = playerViewModel::showPlayer,
                    onPlayPause = playerViewModel::togglePlayPause,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }

    if (playerState.showPlayer && playerState.nowPlaying != null) {
        AbsPlayerDialog(
            viewModel = playerViewModel,
            onDismiss = playerViewModel::hidePlayer,
        )
    }
}

@Composable
private fun AbsTabs(
    state: AbsBrowseState,
    viewModel: AbsBrowseViewModel,
    onOpenSettings: () -> Unit,
    onPlay: (AbsLibraryItem, String?) -> Unit,
    focus: AbsFocusRequest,
    modifier: Modifier = Modifier,
) {
    val tabs =
        remember(state.tabs) {
            state.tabs.map { tab ->
                when (tab) {
                    is AbsTab.Library -> {
                        TabDetails(StringStringProvider(tab.library.name))
                    }

                    AbsTab.Continue -> {
                        TabDetails(ResStringProvider(R.string.abs_continue_listening))
                    }

                    is AbsTab.Series -> {
                        if (tab.showLibraryName) {
                            TabDetails(ResArgStringProvider(R.string.abs_series_tab, arrayOf(tab.library.name)))
                        } else {
                            TabDetails(ResStringProvider(R.string.abs_series))
                        }
                    }

                    is AbsTab.Authors -> {
                        if (tab.showLibraryName) {
                            TabDetails(ResArgStringProvider(R.string.abs_authors_tab, arrayOf(tab.library.name)))
                        } else {
                            TabDetails(ResStringProvider(R.string.abs_authors))
                        }
                    }
                }
            }
        }
    var showHeader by rememberSaveable { mutableStateOf(true) }

    TabbedPage(
        itemId = "audiobookshelf_" + state.tabs.joinToString(",") { it.key },
        tabs = tabs,
        modifier = modifier,
        showTabs = showHeader,
    ) { tabIndex, tabDetails ->
        val positionCallback = { columns: Int, position: Int -> showHeader = position < columns }
        val contentModifier = Modifier.fillMaxSize()
        when (val tab = state.tabs.getOrNull(tabIndex)) {
            is AbsTab.Library -> {
                val sort = state.sort[tab.library.id] ?: SortAndDirection.DEFAULT
                val items = state.items[tab.library.id].orEmpty()
                Column(modifier = contentModifier) {
                    AnimatedVisibility(showHeader) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp, end = 40.dp).fillMaxWidth(),
                        ) {
                            SortByButton(
                                sortOptions = AbsSortOptions,
                                current = sort,
                                onSortChange = { viewModel.changeSort(tab.library.id, it) },
                            )
                            ExpandableFaButton(
                                title = R.string.abs_settings,
                                iconStringRes = R.string.fa_sliders,
                                onClick = onOpenSettings,
                            )
                            Spacer(Modifier.weight(1f))
                            ExpandableFaButton(
                                title = R.string.sort_by_random,
                                iconStringRes = R.string.fa_dice,
                                onClick = { items.randomOrNull()?.let { viewModel.openItem(it) } },
                                enabled = items.isNotEmpty(),
                            )
                        }
                    }
                    AbsItemGrid(
                        items = items,
                        state = state,
                        onOpen = viewModel::openItem,
                        onPlay = { onPlay(it, null) },
                        onFocus = viewModel::focusItem,
                        showLetterButtons = sort.sort == ItemSortBy.SORT_NAME,
                        positionCallback = positionCallback,
                        gridFocusRequester = tabDetails.contentFocusRequester,
                        focus = focus,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            AbsTab.Continue -> {
                if (state.inProgress.isEmpty()) {
                    Box(modifier = contentModifier, contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.abs_nothing_in_progress),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                } else {
                    val cards =
                        state.inProgress.map { item ->
                            val episode = item.recentEpisode
                            val progress =
                                if (episode != null) state.episodeProgress[episode.id] else state.itemProgress[item.id]
                            AbsCard(
                                gridId = item.id + (episode?.id ?: ""),
                                playable = true,
                                sortName = item.sortTitle,
                                title = episode?.title ?: item.title,
                                subtitle = if (episode != null) item.title else item.author,
                                imageUrl = state.conn?.let { absCoverUrl(it, item.id) },
                                progressPercent = progress.percent(),
                                finished = progress?.isFinished == true,
                            )
                        }
                    AbsCardGrid(
                        cards = cards,
                        onClick = { index -> viewModel.openItem(state.inProgress[index]) },
                        onPlay = { index ->
                            val item = state.inProgress[index]
                            onPlay(item, item.recentEpisode?.id)
                        },
                        onFocus = { index -> viewModel.focusItem(state.inProgress[index]) },
                        showLetterButtons = false,
                        positionCallback = positionCallback,
                        gridFocusRequester = tabDetails.contentFocusRequester,
                        focus = focus,
                        modifier = contentModifier,
                    )
                }
            }

            is AbsTab.Series -> {
                val series = state.series[tab.library.id].orEmpty()
                val cards =
                    series.map { s ->
                        AbsCard(
                            gridId = s.id,
                            playable = false,
                            sortName = s.nameIgnorePrefix ?: s.name,
                            title = s.name,
                            subtitle = pluralStringResource(R.plurals.abs_book_count, s.books.size, s.books.size),
                            imageUrl =
                                s.books.firstOrNull()?.let { book ->
                                    state.conn?.let { absCoverUrl(it, book.id) }
                                },
                        )
                    }
                AbsCardGrid(
                    cards = cards,
                    onClick = { index -> viewModel.openSeries(series[index]) },
                    onPlay = {},
                    onFocus = { index -> series[index].books.firstOrNull()?.let { viewModel.focusItem(it) } },
                    showLetterButtons = true,
                    positionCallback = positionCallback,
                    gridFocusRequester = tabDetails.contentFocusRequester,
                    focus = focus,
                    modifier = contentModifier,
                )
            }

            is AbsTab.Authors -> {
                val authors = state.authors[tab.library.id].orEmpty()
                val cards =
                    authors.map { author ->
                        AbsCard(
                            gridId = author.id,
                            playable = false,
                            sortName = author.name,
                            title = author.name,
                            subtitle =
                                author.numBooks?.let {
                                    pluralStringResource(R.plurals.abs_book_count, it, it)
                                },
                            imageUrl =
                                if (author.imagePath.isNullOrBlank()) {
                                    null
                                } else {
                                    state.conn?.let { absAuthorImageUrl(it, author.id) }
                                },
                        )
                    }
                AbsCardGrid(
                    cards = cards,
                    onClick = { index -> viewModel.openAuthor(authors[index]) },
                    onPlay = {},
                    onFocus = {},
                    showLetterButtons = true,
                    positionCallback = positionCallback,
                    gridFocusRequester = tabDetails.contentFocusRequester,
                    focus = focus,
                    modifier = contentModifier,
                )
            }

            null -> {
                Spacer(contentModifier)
            }
        }
    }
}

/** Grid of podcasts or books with their progress */
@Composable
private fun AbsItemGrid(
    items: List<AbsLibraryItem>,
    state: AbsBrowseState,
    onOpen: (AbsLibraryItem) -> Unit,
    onPlay: (AbsLibraryItem) -> Unit,
    onFocus: (AbsLibraryItem) -> Unit,
    modifier: Modifier = Modifier,
    showLetterButtons: Boolean = false,
    positionCallback: ((Int, Int) -> Unit)? = null,
    focus: AbsFocusRequest,
    gridFocusRequester: FocusRequester = remember { FocusRequester() },
) {
    val cards =
        items.map { item ->
            val progress = if (item.isPodcast) null else state.itemProgress[item.id]
            AbsCard(
                gridId = item.id,
                playable = !item.isPodcast,
                sortName = item.sortTitle,
                title = item.title,
                subtitle =
                    item.author
                        ?: item.media
                            ?.numEpisodes
                            ?.takeIf { item.isPodcast }
                            ?.let { pluralStringResource(R.plurals.abs_episode_count, it, it) },
                imageUrl = state.conn?.let { absCoverUrl(it, item.id) },
                progressPercent = progress.percent(),
                finished = progress?.isFinished == true,
            )
        }
    AbsCardGrid(
        cards = cards,
        onClick = { onOpen(items[it]) },
        onPlay = { onPlay(items[it]) },
        onFocus = { onFocus(items[it]) },
        showLetterButtons = showLetterButtons,
        positionCallback = positionCallback,
        gridFocusRequester = gridFocusRequester,
        focus = focus,
        modifier = modifier,
    )
}

/** [CardGrid] of square [AbsGridCard]s, like a Jellyfin library with square images */
@Composable
private fun AbsCardGrid(
    cards: List<AbsCard>,
    onClick: (Int) -> Unit,
    onPlay: (Int) -> Unit,
    onFocus: (Int) -> Unit,
    showLetterButtons: Boolean,
    positionCallback: ((Int, Int) -> Unit)?,
    gridFocusRequester: FocusRequester,
    focus: AbsFocusRequest,
    modifier: Modifier = Modifier,
) {
    if (focus.requested && cards.isNotEmpty()) {
        LaunchedEffect(Unit) {
            // Wait for the grid to be laid out
            delay(100)
            gridFocusRequester.tryRequestFocus()
            focus.onDone()
        }
    }
    CardGrid(
        pager = cards,
        onClickItem = { index, _ -> onClick(index) },
        onLongClickItem = { _, _ -> },
        onClickPlay = { index, _ -> onPlay(index) },
        letterPosition = { letter ->
            cards.indexOfFirst { card ->
                val first = card.sortName.firstOrNull()?.uppercaseChar()
                if (letter == '#') first?.isDigit() == true else first == letter.uppercaseChar()
            }
        },
        gridFocusRequester = gridFocusRequester,
        showJumpButtons = false,
        showLetterButtons = showLetterButtons,
        positionCallback = positionCallback,
        columns = 6,
        spacing = 16.dp,
        cardContent = { details ->
            AbsGridCard(
                card = details.item,
                onClick = details.onClick,
                modifier =
                    details.mod.onFocusChanged {
                        if (it.isFocused) onFocus(details.index)
                    },
            )
        },
        modifier = modifier.padding(horizontal = 16.dp),
    )
}

/** Same look as [com.github.damontecres.wholphin.ui.cards.GridCard], with a square cover */
@Composable
fun AbsGridCard(
    card: AbsCard?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    val focused by interactionSource.collectIsFocusedAsState()
    var focusedAfterDelay by remember { mutableStateOf(false) }
    LaunchedEffect(focused) {
        if (focused) delay(500)
        focusedAfterDelay = focused
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClick,
            interactionSource = interactionSource,
            colors = CardDefaults.colors(containerColor = Color.Transparent),
        ) {
            ItemCardImage(
                imageUrl = card?.imageUrl,
                name = card?.title,
                showOverlay = true,
                favorite = false,
                watched = card?.finished == true,
                unwatchedCount = -1,
                watchedPercent = card?.progressPercent,
                numberOfVersions = 0,
                useFallbackText = true,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(AspectRatios.SQUARE)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }
        SlidingCardText(focused) {
            Text(
                text = card?.title ?: "",
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .enableMarquee(focusedAfterDelay),
            )
            Text(
                text = card?.subtitle ?: "",
                maxLines = 1,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Normal,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .enableMarquee(focusedAfterDelay),
            )
        }
    }
}

/** Shown under the library while something plays and the full screen player is closed */
@Composable
private fun AbsMiniPlayer(
    nowPlaying: AbsNowPlaying,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Card(
            onClick = onOpen,
            modifier = Modifier.weight(1f),
            colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = .75f)),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp),
            ) {
                AsyncImage(
                    model = nowPlaying.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(6.dp)),
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = nowPlaying.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            listOf(
                                nowPlaying.subtitle,
                                "${formatClock(positionMs)} / ${formatClock(durationMs)}",
                            ).filter { it.isNotBlank() }
                                .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    AbsProgressBar(
                        fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        PlaybackButton(
            iconRes = if (isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24,
            onClick = onPlayPause,
            onControllerInteraction = {},
        )
    }
}

/** Progress (0-100) of a saved [AbsMediaProgress], or null when not started or finished */
internal fun AbsMediaProgress?.percent(): Double? {
    if (this == null || isFinished) return null
    val fraction =
        if (duration > 0) currentTime / duration else progress
    return (fraction * 100).takeIf { it > 0 && it < 100 }
}

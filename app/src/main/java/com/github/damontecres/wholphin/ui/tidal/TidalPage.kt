package com.github.damontecres.wholphin.ui.tidal

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.tidal.TidalApi
import com.github.damontecres.wholphin.services.tidal.TidalHomeItem
import com.github.damontecres.wholphin.services.tidal.TidalHomeRow
import com.github.damontecres.wholphin.services.tidal.TidalPlaylist
import com.github.damontecres.wholphin.services.tidal.TidalTrack
import com.github.damontecres.wholphin.services.tidal.TidalVideo
import com.github.damontecres.wholphin.ui.FontAwesome
import com.github.damontecres.wholphin.ui.tryRequestFocus

/** TIDAL's own dark palette, used instead of the Wholphin theme so the page looks like the TIDAL app */
private enum class TidalTheme(
    @StringRes val label: Int,
    val background: Color,
    val bar: Color,
    val elevated: Color,
    val focused: Color,
    val divider: Color,
    val accent: Color,
) {
    BLACK(
        R.string.tidal_theme_black,
        Color(0xFF000000),
        Color(0xFF0D0D0E),
        Color(0xFF1E1E21),
        Color(0xFF2B2B30),
        Color(0xFF26262B),
        Color(0xFF33FFEE),
    ),
    GRAPHITE(
        R.string.tidal_theme_graphite,
        Color(0xFF17171A),
        Color(0xFF202024),
        Color(0xFF2C2C31),
        Color(0xFF3A3A41),
        Color(0xFF34343A),
        Color(0xFF33FFEE),
    ),
    MIDNIGHT(
        R.string.tidal_theme_midnight,
        Color(0xFF070B18),
        Color(0xFF0D1324),
        Color(0xFF18213A),
        Color(0xFF243052),
        Color(0xFF1C2742),
        Color(0xFF7AA7FF),
    ),
    AMBER(
        R.string.tidal_theme_amber,
        Color(0xFF000000),
        Color(0xFF0E0C0A),
        Color(0xFF221E1A),
        Color(0xFF332C25),
        Color(0xFF2A2520),
        Color(0xFFFFB547),
    ),
    ;

    companion object {
        fun of(name: String) = entries.firstOrNull { it.name == name } ?: BLACK
    }
}

/** The page's colours, from the theme picked in the TIDAL settings (TIDAL's own black by default) */
private object Tidal {
    var theme by mutableStateOf(TidalTheme.BLACK)
    val background get() = theme.background
    val bar get() = theme.bar
    val elevated get() = theme.elevated
    val focused get() = theme.focused
    val text = Color(0xFFFFFFFF)
    val secondary = Color(0xFFA6A6AD)
    val divider get() = theme.divider
    val accent get() = theme.accent
}

@Composable
fun TidalPage(
    modifier: Modifier = Modifier,
    viewModel: TidalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val player by viewModel.player.collectAsState()

    // The player lives with this page, so leaving the app pauses it (same as Audiobookshelf)
    LifecycleStartEffect(Unit) { onStopOrDispose { viewModel.pause() } }
    SideEffect { Tidal.theme = TidalTheme.of(state.settings.theme) }
    BackHandler(enabled = state.showSettings) { viewModel.showSettings(false) }
    val overlay = state.addToPlaylist != null || state.trackMenu != null
    BackHandler(enabled = player.showNowPlaying && !overlay) { viewModel.showNowPlaying(false) }
    BackHandler(enabled = !player.showNowPlaying && state.detail != null && !overlay) { viewModel.closeDetail() }
    BackHandler(enabled = state.trackMenu != null) { viewModel.showTrackMenu(null) }
    BackHandler(enabled = state.addToPlaylist != null) { viewModel.pickPlaylistFor(null) }
    BackHandler(enabled = state.playingVideo != null) { viewModel.closeVideo() }

    Box(modifier = modifier.fillMaxSize().background(Tidal.background)) {
        when {
            !viewModel.configured -> {
                CenterMessage(stringResource(R.string.tidal_not_configured))
            }

            state.loggedIn == null -> {
                Unit
            }

            state.loggedIn == false -> {
                LoginScreen(state, onRetry = viewModel::startLogin)
            }

            else -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        val detail = state.detail
                        if (detail != null) {
                            DetailScreen(detail, player, viewModel)
                        } else {
                            CollectionScreen(state, player, viewModel)
                        }
                    }
                    if (player.current != null) PlayerBar(player, state, viewModel)
                }
            }
        }
        if (player.showNowPlaying && player.current != null) NowPlaying(player, viewModel, state.settings.lyricsFirst)
        if (state.showSettings) SettingsScreen(state.settings, viewModel)
        state.playingVideo?.let { VideoScreen(it, state.videoError, viewModel) }
        state.trackMenu?.let { TrackMenu(it, state.isFavorite(it), viewModel) }
        state.addToPlaylist?.let { PlaylistPicker(it, state.playlists, onPick = viewModel::addToPlaylist) }
        state.message?.let { Notice(it, Modifier.align(Alignment.TopEnd)) }
    }
}

/** TIDAL settings: look, stream quality, playlist order, lyrics, logout */
@Composable
private fun SettingsScreen(
    settings: com.github.damontecres.wholphin.services.tidal.TidalSettings,
    viewModel: TidalViewModel,
) {
    val focus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .8f)), contentAlignment = Alignment.Center) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier =
                Modifier
                    .width(640.dp)
                    .fillMaxHeight(.9f)
                    .background(Tidal.bar, RoundedCornerShape(8.dp))
                    .padding(28.dp),
        ) {
            item {
                Text(stringResource(R.string.tidal_settings), color = Tidal.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            item { SettingLabel(stringResource(R.string.tidal_setting_theme)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TidalTheme.entries.forEachIndexed { index, theme ->
                        PillButton(
                            stringResource(theme.label),
                            primary = settings.theme == theme.name,
                            onClick = { viewModel.updateSettings { it.copy(theme = theme.name) } },
                            modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
                        )
                    }
                }
            }
            item { SettingLabel(stringResource(R.string.tidal_setting_quality)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    listOf(
                        "LOW" to R.string.tidal_quality_low,
                        "HIGH" to R.string.tidal_quality_high,
                        "LOSSLESS" to R.string.tidal_quality_lossless,
                    ).forEach { (quality, label) ->
                        PillButton(
                            stringResource(label),
                            primary = settings.quality == quality,
                            onClick = { viewModel.updateSettings { it.copy(quality = quality) } },
                        )
                    }
                }
            }
            item {
                Text(stringResource(R.string.tidal_quality_hint), color = Tidal.secondary, fontSize = 12.sp)
            }
            item { SettingLabel(stringResource(R.string.tidal_setting_playlist_order)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(
                        stringResource(R.string.tidal_sort_newest),
                        primary = settings.newestFirst,
                        onClick = { viewModel.updateSettings { it.copy(newestFirst = true) } },
                    )
                    PillButton(
                        stringResource(R.string.tidal_sort_playlist),
                        primary = !settings.newestFirst,
                        onClick = { viewModel.updateSettings { it.copy(newestFirst = false) } },
                    )
                }
            }
            item { SettingLabel(stringResource(R.string.tidal_setting_now_playing)) }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PillButton(
                        stringResource(R.string.tidal_queue),
                        primary = !settings.lyricsFirst,
                        onClick = { viewModel.updateSettings { it.copy(lyricsFirst = false) } },
                    )
                    PillButton(
                        stringResource(R.string.tidal_lyrics),
                        primary = settings.lyricsFirst,
                        onClick = { viewModel.updateSettings { it.copy(lyricsFirst = true) } },
                    )
                }
            }
            item { SettingLabel(stringResource(R.string.tidal_setting_account)) }
            item {
                PillButton(stringResource(R.string.tidal_logout), primary = false, onClick = viewModel::logout)
            }
        }
    }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
}

@Composable
private fun SettingLabel(text: String) =
    Text(
        text.uppercase(),
        color = Tidal.secondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = 14.dp),
    )

/** A music video over the whole screen; the player's own controls work with the remote */
@Composable
private fun VideoScreen(
    video: TidalVideo,
    error: String?,
    viewModel: TidalViewModel,
) {
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = viewModel.videoPlayer
                    useController = true
                    controllerShowTimeoutMs = 3000
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                }
            },
            // Keys must reach the player view, not the page under it
            update = { if (!it.hasFocus()) it.requestFocus() },
            onRelease = { it.player = null },
            modifier = Modifier.fillMaxSize(),
        )
        Column(Modifier.align(Alignment.TopStart).padding(32.dp)) {
            Text(video.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(video.artistNames, color = Color.White.copy(alpha = .8f), fontSize = 16.sp)
            error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 14.sp) }
        }
    }
}

/** Picks one of the user's playlists for [track] */
@Composable
private fun PlaylistPicker(
    track: TidalTrack,
    playlists: List<TidalPlaylist>,
    onPick: (TidalPlaylist) -> Unit,
) {
    val focus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .75f)), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .width(520.dp)
                    .fillMaxHeight(.8f)
                    .background(Tidal.bar, RoundedCornerShape(8.dp))
                    .padding(24.dp),
        ) {
            Text(stringResource(R.string.tidal_add_to_playlist), color = Tidal.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "${track.title} · ${track.artistNames}",
                color = Tidal.secondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(16.dp))
            if (playlists.isEmpty()) {
                Text(stringResource(R.string.tidal_no_playlists), color = Tidal.secondary, fontSize = 15.sp)
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(playlists, key = { _, p -> p.uuid }) { index, playlist ->
                    Surface(
                        onClick = { onPick(playlist) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        colors =
                            ClickableSurfaceDefaults.colors(
                                containerColor = Color.Transparent,
                                focusedContainerColor = Tidal.focused,
                            ),
                        modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(focus) else Modifier),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(8.dp)) {
                            AsyncImage(
                                model = TidalApi.imageUrl(playlist.squareImage ?: playlist.image, 160),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(3.dp)).background(Tidal.elevated),
                            )
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(playlist.title, color = Tidal.text, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    pluralStringResource(R.plurals.tidal_track_count, playlist.numberOfTracks, playlist.numberOfTracks),
                                    color = Tidal.secondary,
                                    fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
}

@Composable
private fun Notice(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .padding(24.dp)
                .widthIn(max = 480.dp)
                .background(Tidal.elevated, RoundedCornerShape(8.dp))
                .border(1.dp, Tidal.accent, RoundedCornerShape(8.dp))
                .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(text, color = Tidal.text, fontSize = 15.sp)
    }
}

@Composable
private fun CenterMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = Tidal.secondary, fontSize = 18.sp)
    }
}

@Composable
private fun LoginScreen(
    state: TidalUiState,
    onRetry: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("TIDAL", color = Tidal.text, fontSize = 44.sp, fontWeight = FontWeight.Black, letterSpacing = 6.sp)
        Spacer(Modifier.height(32.dp))
        val code = state.loginCode
        when {
            code != null -> {
                Text(stringResource(R.string.tidal_login_step1), color = Tidal.secondary, fontSize = 18.sp)
                Text("link.tidal.com", color = Tidal.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.tidal_login_step2), color = Tidal.secondary, fontSize = 18.sp)
                Spacer(Modifier.height(8.dp))
                Box(
                    modifier =
                        Modifier
                            .background(Tidal.elevated, RoundedCornerShape(8.dp))
                            .padding(horizontal = 32.dp, vertical = 12.dp),
                ) {
                    Text(code.userCode, color = Tidal.accent, fontSize = 48.sp, fontWeight = FontWeight.Bold, letterSpacing = 8.sp)
                }
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.tidal_login_waiting), color = Tidal.secondary, fontSize = 14.sp)
            }

            state.loginError != null -> {
                Text(stringResource(R.string.tidal_login_failed), color = Tidal.text, fontSize = 18.sp)
                Spacer(Modifier.height(16.dp))
                PillButton(
                    stringResource(R.string.tidal_login_retry),
                    primary = true,
                    onClick = onRetry,
                    modifier = Modifier.focusRequester(focus),
                )
                LaunchedEffect(Unit) { focus.tryRequestFocus() }
            }

            else -> {
                Text(stringResource(R.string.tidal_login_waiting), color = Tidal.secondary)
            }
        }
    }
}

@Composable
private fun CollectionScreen(
    state: TidalUiState,
    player: TidalPlayerState,
    viewModel: TidalViewModel,
) {
    Column(modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, top = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.tidal_my_collection),
                color = Tidal.text,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                stringResource(R.string.tidal_settings),
                primary = false,
                faIcon = R.string.fa_sliders,
                onClick = { viewModel.showSettings(true) },
            )
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TidalTab.entries.forEach { tab ->
                val label =
                    when (tab) {
                        TidalTab.PLAYLISTS -> R.string.tidal_playlists
                        TidalTab.MIXES -> R.string.tidal_mixes
                        TidalTab.FOR_YOU -> R.string.tidal_for_you
                        TidalTab.VIDEOS -> R.string.tidal_videos
                        TidalTab.ALBUMS -> R.string.tidal_albums
                        TidalTab.TRACKS -> R.string.tidal_tracks
                    }
                PillButton(stringResource(label), primary = state.tab == tab, onClick = { viewModel.selectTab(tab) })
            }
        }
        Spacer(Modifier.height(24.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                state.error != null -> {
                    CenterMessage(state.error)
                }

                state.tab == TidalTab.PLAYLISTS -> {
                    CoverGrid(
                        items = state.playlists,
                        loading = state.loading,
                        title = { it.title },
                        subtitle = { pluralStringResource(R.plurals.tidal_track_count, it.numberOfTracks, it.numberOfTracks) },
                        image = { TidalApi.imageUrl(it.squareImage ?: it.image) },
                        onClick = viewModel::openPlaylist,
                    )
                }

                // TIDAL's "Custom mixes": My Daily Discovery, My Mix 1-n…, in the order TIDAL lists them
                state.tab == TidalTab.MIXES -> {
                    CoverGrid(
                        items = state.mixes,
                        loading = state.forYouLoading,
                        title = { it.title },
                        subtitle = { it.subTitle },
                        image = { it.imageUrl },
                        onClick = viewModel::openMix,
                    )
                }

                state.tab == TidalTab.FOR_YOU -> {
                    HomeFeed(state, player, viewModel)
                }

                state.tab == TidalTab.VIDEOS -> {
                    CoverGrid(
                        items = (state.videos + state.recommendedVideos).distinctBy { it.id },
                        loading = state.videosLoading || state.forYouLoading,
                        title = { it.title },
                        subtitle = { it.artistNames },
                        image = { it.imageUrl },
                        onClick = viewModel::playVideo,
                        columns = 4,
                        aspectRatio = 16f / 9f,
                    )
                }

                state.tab == TidalTab.ALBUMS -> {
                    CoverGrid(
                        items = state.albums,
                        loading = state.loading,
                        title = { it.title },
                        subtitle = { it.artist?.name.orEmpty() },
                        image = { TidalApi.imageUrl(it.cover) },
                        onClick = viewModel::openAlbum,
                    )
                }

                else -> {
                    TrackList(
                        tracks = state.tracks,
                        player = player,
                        onPlay = { viewModel.play(state.tracks, it) },
                        onLongClick = viewModel::showTrackMenu,
                    )
                }
            }
        }
    }
}

@Composable
private fun <T> CoverGrid(
    items: List<T>,
    loading: Boolean,
    title: (T) -> String,
    subtitle: @Composable (T) -> String,
    image: (T) -> String?,
    onClick: (T) -> Unit,
    columns: Int = 6,
    aspectRatio: Float = 1f,
) {
    if (items.isEmpty()) {
        CenterMessage(stringResource(if (loading) R.string.tidal_loading else R.string.tidal_empty))
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
        contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp),
    ) {
        itemsIndexed(items) { _, item ->
            Column {
                Surface(
                    onClick = { onClick(item) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
                    border =
                        ClickableSurfaceDefaults.border(
                            focusedBorder = androidx.tv.material3.Border(BorderStroke(2.dp, Tidal.text)),
                        ),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Tidal.elevated, focusedContainerColor = Tidal.elevated),
                    modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio),
                ) {
                    AsyncImage(
                        model = image(item),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    title(item),
                    color = Tidal.text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(subtitle(item), color = Tidal.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<TidalTrack>,
    player: TidalPlayerState,
    onPlay: (Int) -> Unit,
    header: (@Composable () -> Unit)? = null,
    showCover: Boolean = true,
    onLongClick: ((TidalTrack) -> Unit)? = null,
) {
    val focus = remember { FocusRequester() }
    LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
        header?.let { item { it() } }
        item {
            Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                ColumnLabel("#", Modifier.width(40.dp))
                ColumnLabel(stringResource(R.string.tidal_col_title), Modifier.weight(1f))
                ColumnLabel(stringResource(R.string.tidal_col_album), Modifier.weight(0.7f))
                ColumnLabel(stringResource(R.string.tidal_col_time), Modifier.width(64.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Tidal.divider))
        }
        itemsIndexed(tracks, key = { i, t -> "$i-${t.id}" }) { index, track ->
            TrackRow(
                index = index,
                track = track,
                playing = player.current?.id == track.id,
                showCover = showCover,
                onClick = { onPlay(index) },
                onLongClick = onLongClick?.let { cb -> { cb(track) } },
                modifier = if (index == 0) Modifier.focusRequester(focus) else Modifier,
            )
        }
    }
    if (header == null && tracks.isNotEmpty()) LaunchedEffect(Unit) { focus.tryRequestFocus() }
    if (tracks.isEmpty()) CenterMessage(stringResource(R.string.tidal_empty))
}

@Composable
private fun ColumnLabel(
    text: String,
    modifier: Modifier,
) = Text(
    text.uppercase(),
    color = Tidal.secondary,
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    letterSpacing = 1.sp,
    modifier = modifier,
)

@Composable
private fun TrackRow(
    index: Int,
    track: TidalTrack,
    playing: Boolean,
    showCover: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showAlbum: Boolean = true,
    onLongClick: (() -> Unit)? = null,
) {
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Tidal.focused,
                contentColor = Tidal.text,
                focusedContentColor = Tidal.text,
            ),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                if (playing) "▶" else "${index + 1}",
                color = if (playing) Tidal.accent else Tidal.secondary,
                fontSize = 14.sp,
                modifier = Modifier.width(40.dp),
            )
            if (showCover) {
                AsyncImage(
                    model = TidalApi.imageUrl(track.album?.cover, 160),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(3.dp)).background(Tidal.elevated),
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    track.title,
                    color = if (playing) Tidal.accent else Tidal.text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(track.artistNames, color = Tidal.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showAlbum) {
                Text(
                    track.album?.title.orEmpty(),
                    color = Tidal.secondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(0.7f).padding(start = 12.dp),
                )
            }
            Text(formatTime(track.duration * 1000L), color = Tidal.secondary, fontSize = 13.sp, modifier = Modifier.width(64.dp))
        }
    }
}

@Composable
private fun DetailScreen(
    detail: TidalDetail,
    player: TidalPlayerState,
    viewModel: TidalViewModel,
) {
    val playFocus = remember { FocusRequester() }
    Box(Modifier.fillMaxSize()) {
        // Soft wash of the cover behind the header, like the album page in TIDAL
        AsyncImage(
            model = detail.imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.35f,
            modifier = Modifier.fillMaxWidth().height(360.dp).blur(60.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Tidal.background))),
        )
        Box(Modifier.fillMaxSize().padding(horizontal = 48.dp)) {
            TrackList(
                tracks = detail.tracks,
                player = player,
                showCover = !detail.isAlbum,
                onPlay = { viewModel.play(detail.tracks, it) },
                onLongClick = viewModel::showTrackMenu,
                header = {
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(top = 40.dp, bottom = 28.dp),
                    ) {
                        AsyncImage(
                            model = detail.imageUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(220.dp).clip(RoundedCornerShape(4.dp)).background(Tidal.elevated),
                        )
                        Spacer(Modifier.width(32.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                stringResource(
                                    when (detail.kind) {
                                        TidalDetailKind.ALBUM -> R.string.tidal_album
                                        TidalDetailKind.PLAYLIST -> R.string.tidal_playlist
                                        TidalDetailKind.MIX -> R.string.tidal_mix
                                        TidalDetailKind.SHELF -> R.string.tidal_for_you
                                        TidalDetailKind.ARTIST -> R.string.tidal_artist
                                    },
                                ).uppercase(),
                                color = Tidal.secondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                            )
                            Text(
                                detail.title,
                                color = Tidal.text,
                                fontSize = 40.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (detail.subtitle.isNotBlank()) Text(detail.subtitle, color = Tidal.text, fontSize = 18.sp)
                            val minutes = detail.tracks.sumOf { it.duration } / 60
                            Text(
                                if (detail.loading) {
                                    stringResource(R.string.tidal_loading)
                                } else {
                                    pluralStringResource(R.plurals.tidal_track_count, detail.tracks.size, detail.tracks.size) +
                                        " · " + stringResource(R.string.tidal_minutes, minutes)
                                },
                                color = Tidal.secondary,
                                fontSize = 14.sp,
                            )
                            detail.error?.let { Text(it, color = Color(0xFFFF6B6B), fontSize = 14.sp) }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                PillButton(
                                    stringResource(R.string.tidal_play),
                                    primary = true,
                                    iconRes = R.drawable.baseline_play_arrow_24,
                                    onClick = { viewModel.play(detail.tracks) },
                                    modifier = Modifier.focusRequester(playFocus),
                                )
                                PillButton(
                                    stringResource(R.string.tidal_shuffle),
                                    primary = false,
                                    faIcon = R.string.fa_shuffle,
                                    onClick = { viewModel.play(detail.tracks, shuffle = true) },
                                )
                                detail.artist?.let { artist ->
                                    PillButton(
                                        stringResource(R.string.tidal_artist_radio),
                                        primary = false,
                                        faIcon = R.string.fa_shuffle,
                                        onClick = { viewModel.artistRadio(artist) },
                                    )
                                }
                                if (detail.playlistUuid != null) {
                                    PillButton(
                                        stringResource(
                                            if (detail.newestFirst) R.string.tidal_sort_newest else R.string.tidal_sort_playlist,
                                        ),
                                        primary = false,
                                        faIcon = R.string.fa_arrow_down_long,
                                        onClick = viewModel::toggleNewestFirst,
                                    )
                                }
                            }
                            Text(
                                stringResource(R.string.tidal_long_press_hint),
                                color = Tidal.secondary.copy(alpha = .7f),
                                fontSize = 12.sp,
                            )
                        }
                    }
                },
            )
        }
    }
    LaunchedEffect(detail.loading) { if (!detail.loading) playFocus.tryRequestFocus() }
}

/** White pill for the main action, dark pill for the others, like TIDAL's buttons and filter chips */
@Composable
private fun PillButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    @DrawableRes iconRes: Int? = null,
    faIcon: Int? = null,
) {
    val content = if (primary) Color.Black else Tidal.text
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(BorderStroke(2.dp, Tidal.accent))),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (primary) Tidal.text else Tidal.elevated,
                focusedContainerColor = if (primary) Tidal.text else Tidal.focused,
                contentColor = content,
                focusedContentColor = content,
            ),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 10.dp),
        ) {
            iconRes?.let { Icon(painterResource(it), contentDescription = null, tint = content, modifier = Modifier.size(20.dp)) }
            faIcon?.let { Text(stringResource(it), fontFamily = FontAwesome, color = content, fontSize = 15.sp) }
            Text(text, color = content, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PlayerBar(
    player: TidalPlayerState,
    state: TidalUiState,
    viewModel: TidalViewModel,
) {
    val track = player.current ?: return
    Column(modifier = Modifier.fillMaxWidth().background(Tidal.bar)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Tidal.divider))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 10.dp),
        ) {
            Surface(
                onClick = { viewModel.showNowPlaying(true) },
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Tidal.focused),
                modifier = Modifier.weight(1f),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                    AsyncImage(
                        model = TidalApi.imageUrl(track.album?.cover, 160),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(3.dp)).background(Tidal.elevated),
                    )
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(
                            track.title,
                            color = Tidal.text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(track.artistNames, color = Tidal.secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1.4f)) {
                Controls(player, viewModel, playSize = 44.dp)
                ProgressRow(player, Modifier.widthIn(max = 560.dp).padding(top = 4.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                modifier = Modifier.weight(1f),
            ) {
                player.error?.let {
                    Text(it, color = Color(0xFFFF6B6B), fontSize = 12.sp, maxLines = 2, modifier = Modifier.weight(1f, fill = false))
                }
                FaControl(R.string.fa_heart, active = state.isFavorite(track), onClick = { viewModel.toggleFavorite(track) })
                FaControl(R.string.fa_ellipsis, active = false, onClick = { viewModel.showTrackMenu(track) })
                (player.quality ?: track.audioQuality)?.let { QualityPill(it) }
            }
        }
    }
}

@Composable
private fun Controls(
    player: TidalPlayerState,
    viewModel: TidalViewModel,
    playSize: Dp,
    playFocus: FocusRequester? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        FaControl(R.string.fa_shuffle, active = player.shuffle, onClick = viewModel::toggleShuffle)
        IconControl(R.drawable.baseline_skip_previous_24, onClick = viewModel::previous)
        Surface(
            onClick = viewModel::togglePlayPause,
            shape = ClickableSurfaceDefaults.shape(CircleShape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
            border = ClickableSurfaceDefaults.border(focusedBorder = androidx.tv.material3.Border(BorderStroke(2.dp, Tidal.accent))),
            colors = ClickableSurfaceDefaults.colors(containerColor = Tidal.text, focusedContainerColor = Tidal.text),
            modifier = Modifier.size(playSize).then(playFocus?.let { Modifier.focusRequester(it) } ?: Modifier),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(if (player.isPlaying) R.drawable.baseline_pause_24 else R.drawable.baseline_play_arrow_24),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(playSize * 0.6f),
                )
            }
        }
        IconControl(R.drawable.baseline_skip_next_24, onClick = viewModel::next)
        Box(contentAlignment = Alignment.TopEnd) {
            FaControl(R.string.fa_repeat, active = player.repeatMode != Player.REPEAT_MODE_OFF, onClick = viewModel::cycleRepeat)
            if (player.repeatMode == Player.REPEAT_MODE_ONE) {
                Text("1", color = Tidal.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 4.dp))
            }
        }
    }
}

@Composable
private fun RoundControl(
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f),
        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Tidal.focused),
        modifier = Modifier.size(40.dp),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content(Tidal.text) }
    }
}

@Composable
private fun IconControl(
    @DrawableRes iconRes: Int,
    onClick: () -> Unit,
) = RoundControl(onClick) { tint ->
    Icon(painterResource(iconRes), contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
}

@Composable
private fun FaControl(
    icon: Int,
    active: Boolean,
    onClick: () -> Unit,
) = RoundControl(onClick) {
    Text(stringResource(icon), fontFamily = FontAwesome, color = if (active) Tidal.accent else Tidal.secondary, fontSize = 16.sp)
}

@Composable
private fun ProgressRow(
    player: TidalPlayerState,
    modifier: Modifier = Modifier,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        Text(formatTime(player.positionMs), color = Tidal.secondary, fontSize = 11.sp)
        Box(
            Modifier
                .weight(1f)
                .padding(horizontal = 10.dp)
                .height(3.dp)
                .clip(CircleShape)
                .background(Tidal.divider),
        ) {
            val fraction = if (player.durationMs > 0) (player.positionMs.toFloat() / player.durationMs).coerceIn(0f, 1f) else 0f
            Box(Modifier.fillMaxWidth(fraction).fillMaxHeight().background(Tidal.text))
        }
        Text(formatTime(player.durationMs), color = Tidal.secondary, fontSize = 11.sp)
    }
}

/** Full screen "now playing" like TIDAL's: cover and track details on the left, play queue or lyrics on the right */
@Composable
private fun NowPlaying(
    player: TidalPlayerState,
    viewModel: TidalViewModel,
    lyricsFirst: Boolean,
) {
    val track = player.current ?: return
    val playFocus = remember { FocusRequester() }
    var showLyrics by rememberSaveable { mutableStateOf(lyricsFirst) }
    val cover = TidalApi.imageUrl(track.album?.cover, 1280)
    Box(Modifier.fillMaxSize().background(Tidal.background)) {
        AsyncImage(
            model = cover,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = 0.5f,
            modifier = Modifier.fillMaxSize().blur(90.dp),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.horizontalGradient(listOf(Color.Black.copy(alpha = .35f), Color.Black.copy(alpha = .8f)))),
        )
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 48.dp)) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f).fillMaxHeight(),
            ) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .weight(1f, fill = false)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Tidal.elevated),
                )
                Spacer(Modifier.height(24.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        track.title,
                        color = Tidal.text,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (track.explicit) Badge("E", filled = true)
                    (player.quality ?: track.audioQuality)?.let { Badge(qualityLabel(it), filled = false) }
                }
                Text(
                    track.artistNames,
                    color = Tidal.text.copy(alpha = .85f),
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                track.album?.title?.let {
                    Text(it, color = Tidal.secondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(20.dp))
                ProgressRow(player, Modifier.widthIn(max = 640.dp))
                Spacer(Modifier.height(8.dp))
                Controls(player, viewModel, playSize = 60.dp, playFocus = playFocus)
                track.copyright?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, color = Tidal.secondary.copy(alpha = .7f), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(56.dp))
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    PillButton(stringResource(R.string.tidal_queue), primary = !showLyrics, onClick = { showLyrics = false })
                    PillButton(stringResource(R.string.tidal_lyrics), primary = showLyrics, onClick = { showLyrics = true })
                    PillButton(
                        stringResource(R.string.tidal_more),
                        primary = false,
                        faIcon = R.string.fa_ellipsis,
                        onClick = { viewModel.showTrackMenu(track) },
                    )
                    PillButton(
                        stringResource(R.string.tidal_clip),
                        primary = false,
                        faIcon = R.string.fa_video,
                        onClick = { viewModel.playClipOf(track) },
                    )
                }
                Spacer(Modifier.height(20.dp))
                if (showLyrics) Lyrics(player) else Queue(player, onPlay = viewModel::playIndex, onAdd = viewModel::showTrackMenu)
            }
        }
    }
    LaunchedEffect(Unit) { playFocus.tryRequestFocus() }
}

/** The stream quality in a gold pill, like the one at the right of TIDAL's player bar */
@Composable
private fun QualityPill(quality: String) {
    val gold = Color(0xFFFFD432)
    Box(
        Modifier
            .background(gold.copy(alpha = .12f), CircleShape)
            .border(1.dp, gold.copy(alpha = .6f), CircleShape)
            .padding(horizontal = 14.dp, vertical = 5.dp),
    ) {
        Text(qualityLabel(quality), color = gold, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** TIDAL calls LOSSLESS "HiFi" and hi-res "Max" */
private fun qualityLabel(quality: String) =
    when (quality) {
        "HI_RES", "HI_RES_LOSSLESS" -> "MAX"
        "LOSSLESS" -> "HIFI"
        else -> quality.replace('_', ' ')
    }

@Composable
private fun Badge(
    text: String,
    filled: Boolean,
) {
    val color = if (filled) Tidal.secondary else Tidal.accent
    Box(
        modifier =
            Modifier
                .then(if (filled) Modifier.background(color, RoundedCornerShape(3.dp)) else Modifier)
                .border(1.dp, color, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, color = if (filled) Color.Black else color, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
    }
}

@Composable
private fun Queue(
    player: TidalPlayerState,
    onPlay: (Int) -> Unit,
    onAdd: (TidalTrack) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(player.index) { if (player.index >= 0) listState.scrollToItem(player.index) }
    LazyColumn(state = listState) {
        itemsIndexed(player.queue, key = { i, t -> "$i-${t.id}" }) { index, track ->
            TrackRow(
                index = index,
                track = track,
                playing = index == player.index,
                showCover = true,
                showAlbum = false,
                onClick = { onPlay(index) },
                onLongClick = { onAdd(track) },
            )
        }
    }
}

@Composable
private fun Lyrics(player: TidalPlayerState) {
    val synced = player.syncedLyrics
    val plain = player.plainLyrics
    when {
        synced.isNotEmpty() -> {
            val active = synced.indexOfLast { it.timeMs <= player.positionMs }
            val listState = rememberLazyListState()
            LaunchedEffect(active) { if (active > 2) listState.animateScrollToItem(active - 2) }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(14.dp)) {
                itemsIndexed(synced) { index, line ->
                    Text(
                        line.text.ifBlank { "♪" },
                        color = if (index == active) Tidal.text else Tidal.secondary.copy(alpha = if (index < active) .45f else .75f),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        plain != null -> {
            LazyColumn {
                item { Text(plain, color = Tidal.text, fontSize = 20.sp, lineHeight = 32.sp) }
            }
        }

        else -> {
            Text(
                stringResource(if (player.lyricsLoading) R.string.tidal_loading else R.string.tidal_no_lyrics),
                color = Tidal.secondary,
                fontSize = 18.sp,
            )
        }
    }
}

private fun formatTime(ms: Long): String {
    val total = ms / 1000
    return "%d:%02d".format(total / 60, total % 60)
}

/** TIDAL's home page: one titled row per section, cards scroll sideways, tracks in three columns */
@Composable
private fun HomeFeed(
    state: TidalUiState,
    player: TidalPlayerState,
    viewModel: TidalViewModel,
) {
    if (state.homeRows.isEmpty()) {
        CenterMessage(stringResource(if (state.forYouLoading) R.string.tidal_loading else R.string.tidal_empty))
        return
    }
    LazyColumn(verticalArrangement = Arrangement.spacedBy(36.dp), contentPadding = PaddingValues(bottom = 32.dp, top = 8.dp)) {
        items(state.homeRows, key = { it.title }) { row ->
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                val allMixes = row.items.isNotEmpty() && row.items.all { it is TidalHomeItem.Mix }
                Row(verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        if (row.subtitle.isNotBlank()) Text(row.subtitle, color = Tidal.secondary, fontSize = 13.sp)
                        Text(row.title, color = Tidal.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                    when {
                        row.tracks.size > HOME_TRACKS -> {
                            PillButton(stringResource(R.string.tidal_view_all), primary = false, onClick = { viewModel.openRow(row) })
                        }

                        allMixes && state.mixes.size > row.items.size -> {
                            PillButton(
                                stringResource(R.string.tidal_view_all),
                                primary = false,
                                onClick = { viewModel.selectTab(TidalTab.MIXES) },
                            )
                        }
                    }
                }
                if (row.items.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        items(row.items) { HomeCard(it, viewModel) }
                    }
                }
                if (row.tracks.isNotEmpty()) HomeTracks(row, player, viewModel)
            }
        }
    }
}

private const val HOME_TRACKS = 9

/** The first tracks of a row in three columns, like "Recommended new tracks" in TIDAL */
@Composable
private fun HomeTracks(
    row: TidalHomeRow,
    player: TidalPlayerState,
    viewModel: TidalViewModel,
) {
    val shown = row.tracks.take(HOME_TRACKS)
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        shown.chunked((shown.size + 2) / 3).forEachIndexed { column, tracks ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                tracks.forEachIndexed { i, track ->
                    val index = column * ((shown.size + 2) / 3) + i
                    val playing = player.current?.id == track.id
                    Surface(
                        onClick = { viewModel.play(row.tracks, index) },
                        onLongClick = { viewModel.showTrackMenu(track) },
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                        colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Tidal.focused),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(6.dp)) {
                            AsyncImage(
                                model = TidalApi.imageUrl(track.album?.cover, 160),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(3.dp)).background(Tidal.elevated),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        track.title,
                                        color = if (playing) Tidal.accent else Tidal.text,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    if (track.explicit) Badge("E", filled = true)
                                }
                                Text(
                                    track.artistNames,
                                    color = Tidal.secondary,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A card in a home row: square cover, round for artists, wide for music videos */
@Composable
private fun HomeCard(
    item: TidalHomeItem,
    viewModel: TidalViewModel,
) {
    val width = if (item is TidalHomeItem.Video) 300.dp else 180.dp
    val shape = if (item is TidalHomeItem.Artist) CircleShape else RoundedCornerShape(4.dp)
    val (title, subtitle, image) =
        when (item) {
            is TidalHomeItem.Mix -> {
                Triple(item.mix.title, item.mix.subTitle, item.mix.imageUrl)
            }

            is TidalHomeItem.Album -> {
                Triple(item.album.title, item.album.artistName, TidalApi.imageUrl(item.album.cover))
            }

            is TidalHomeItem.Playlist -> {
                Triple(
                    item.playlist.title,
                    item.playlist.creator
                        ?.name
                        ?.takeIf { it.isNotBlank() } ?: "TIDAL",
                    TidalApi.imageUrl(item.playlist.squareImage ?: item.playlist.image),
                )
            }

            is TidalHomeItem.Artist -> {
                Triple(item.artist.name, "", TidalApi.imageUrl(item.artist.picture))
            }

            is TidalHomeItem.Video -> {
                Triple(item.video.title, item.video.artistNames, item.video.imageUrl)
            }
        }
    Column(Modifier.width(width)) {
        Surface(
            onClick = {
                when (item) {
                    is TidalHomeItem.Mix -> viewModel.openMix(item.mix)
                    is TidalHomeItem.Album -> viewModel.openAlbum(item.album)
                    is TidalHomeItem.Playlist -> viewModel.openPlaylist(item.playlist)
                    is TidalHomeItem.Artist -> viewModel.openArtist(item.artist)
                    is TidalHomeItem.Video -> viewModel.playVideo(item.video)
                }
            },
            shape = ClickableSurfaceDefaults.shape(shape),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.06f),
            border =
                ClickableSurfaceDefaults.border(
                    focusedBorder = androidx.tv.material3.Border(BorderStroke(2.dp, Tidal.text), shape = shape),
                ),
            colors = ClickableSurfaceDefaults.colors(containerColor = Tidal.elevated, focusedContainerColor = Tidal.elevated),
            modifier = Modifier.fillMaxWidth().aspectRatio(if (item is TidalHomeItem.Video) 16f / 9f else 1f),
        ) {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                color = Tidal.text,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (item is TidalHomeItem.Album && item.album.explicit) Badge("E", filled = true)
        }
        if (subtitle.isNotBlank()) {
            Text(subtitle, color = Tidal.secondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** TIDAL's "…" menu of a track */
@Composable
private fun TrackMenu(
    track: TidalTrack,
    favorite: Boolean,
    viewModel: TidalViewModel,
) {
    val focus = remember { FocusRequester() }
    val artist = track.artists.firstOrNull()
    val options =
        listOfNotNull(
            Triple(R.string.tidal_play_next, R.string.fa_list_ul) { viewModel.playNext(track) },
            Triple(R.string.tidal_add_to_queue, R.string.fa_list_ul) { viewModel.addToQueue(track) },
            Triple(
                if (favorite) R.string.tidal_unfavorite else R.string.tidal_favorite,
                R.string.fa_heart,
            ) { viewModel.toggleFavorite(track) },
            Triple(R.string.tidal_add_to_playlist, R.string.fa_list_ul) { viewModel.pickPlaylistFor(track) },
            Triple(R.string.tidal_track_radio, R.string.fa_shuffle) { viewModel.trackRadio(track) },
            artist?.let { Triple(R.string.tidal_artist_radio, R.string.fa_shuffle) { viewModel.artistRadio(it) } },
            artist?.let { Triple(R.string.tidal_go_to_artist, R.string.fa_ellipsis) { viewModel.openArtist(it) } },
            track.album?.let { Triple(R.string.tidal_go_to_album, R.string.fa_compact_disc) { viewModel.openAlbum(it) } },
            Triple(R.string.tidal_clip, R.string.fa_video) { viewModel.playClipOf(track) },
        )
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .75f)), contentAlignment = Alignment.Center) {
        Column(
            modifier =
                Modifier
                    .width(460.dp)
                    .background(Tidal.bar, RoundedCornerShape(8.dp))
                    .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
                AsyncImage(
                    model = TidalApi.imageUrl(track.album?.cover, 160),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(3.dp)).background(Tidal.elevated),
                )
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        track.title,
                        color = Tidal.text,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(track.artistNames, color = Tidal.secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            options.forEachIndexed { index, (label, icon, action) ->
                Surface(
                    onClick = {
                        viewModel.showTrackMenu(null)
                        // Opening an album, artist or radio covers the now playing screen
                        if (label in
                            listOf(
                                R.string.tidal_track_radio,
                                R.string.tidal_artist_radio,
                                R.string.tidal_go_to_artist,
                                R.string.tidal_go_to_album,
                            )
                        ) {
                            viewModel.showNowPlaying(false)
                        }
                        action()
                    },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(4.dp)),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    colors = ClickableSurfaceDefaults.colors(containerColor = Color.Transparent, focusedContainerColor = Tidal.focused),
                    modifier = Modifier.fillMaxWidth().then(if (index == 0) Modifier.focusRequester(focus) else Modifier),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                        Text(
                            stringResource(icon),
                            fontFamily = FontAwesome,
                            color = if (icon == R.string.fa_heart && favorite) Tidal.accent else Tidal.secondary,
                            fontSize = 15.sp,
                            modifier = Modifier.width(32.dp),
                        )
                        Text(stringResource(label), color = Tidal.text, fontSize = 16.sp)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { focus.tryRequestFocus() }
}

package com.github.damontecres.wholphin.ui.audiobookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.services.audiobookshelf.AbsConfig
import com.github.damontecres.wholphin.services.audiobookshelf.AbsEpisode
import com.github.damontecres.wholphin.services.audiobookshelf.AbsLibraryItem
import com.github.damontecres.wholphin.services.audiobookshelf.AbsMediaProgress
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.EditTextBox
import java.util.Date

/**
 * Audiobookshelf podcasts: a grid of podcasts, then the episodes of the one opened,
 * with a bottom bar for playback. Colors come from the active Wholphin theme.
 */
@Composable
fun AudiobookshelfPage(
    modifier: Modifier = Modifier,
    viewModel: AudiobookshelfViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsState()

    Column(
        modifier = modifier.fillMaxSize().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = state.selected?.title ?: "Podcasty",
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (state.selected != null) {
                Button(onClick = viewModel::closePodcast) { Text("Späť") }
            }
            Button(onClick = viewModel::openSettings) { Text("Nastavenia") }
        }

        state.error?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !state.configured -> {
                    Text(
                        text = "Audiobookshelf ešte nie je nastavený.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }

                state.loading && state.podcasts.isEmpty() -> {
                    Text(text = "Načítavam…", style = MaterialTheme.typography.bodyLarge)
                }

                state.selected == null -> {
                    PodcastGrid(
                        podcasts = state.podcasts,
                        coverUrl = { id -> coverUrl(state.activeBaseUrl, state.config.token, id) },
                        // activeBaseUrl is the address that actually answered (LAN or Tailscale)
                        onOpen = viewModel::openPodcast,
                    )
                }

                else -> {
                    val podcast = state.selected!!
                    EpisodeList(
                        episodes = state.episodes,
                        progress = state.progress,
                        nowPlayingEpisodeId = state.nowPlaying?.episodeId,
                        onPlay = { episode -> viewModel.play(podcast, episode) },
                    )
                }
            }
        }

        state.nowPlaying?.let { nowPlaying ->
            PlayerBar(
                title = nowPlaying.episodeTitle,
                subtitle = nowPlaying.podcastTitle,
                coverUrl = nowPlaying.coverUrl,
                isPlaying = state.isPlaying,
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onPlayPause = viewModel::togglePlayPause,
                onBack = viewModel::seekBack,
                onForward = viewModel::seekForward,
            )
        }
    }

    if (state.showSettings) {
        SettingsDialog(
            config = state.config,
            error = state.settingsError,
            onSave = viewModel::saveSettings,
            onDismiss = viewModel::closeSettings,
        )
    }
}

private fun coverUrl(
    baseUrl: String,
    token: String,
    itemId: String,
): String = "${baseUrl.trimEnd('/')}/api/items/$itemId/cover?token=$token"

@Composable
private fun PodcastGrid(
    podcasts: List<AbsLibraryItem>,
    coverUrl: (String) -> String,
    onOpen: (AbsLibraryItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(podcasts, key = { it.id }) { podcast ->
            Card(
                onClick = { onOpen(podcast) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    AsyncImage(
                        model = coverUrl(podcast.id),
                        contentDescription = podcast.title,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                    )
                    Text(
                        text = podcast.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeList(
    episodes: List<AbsEpisode>,
    progress: Map<String, AbsMediaProgress>,
    nowPlayingEpisodeId: String?,
    onPlay: (AbsEpisode) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(episodes, key = { it.id }) { episode ->
            EpisodeRow(
                episode = episode,
                progress = progress[episode.id],
                playing = episode.id == nowPlayingEpisodeId,
                onPlay = { onPlay(episode) },
            )
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: AbsEpisode,
    progress: AbsMediaProgress?,
    playing: Boolean,
    onPlay: () -> Unit,
) {
    val context = LocalContext.current
    val duration = (progress?.duration?.takeIf { it > 0 } ?: episode.duration ?: 0.0)
    val fraction =
        if (duration > 0 && progress != null) {
            (progress.currentTime / duration).toFloat().coerceIn(0f, 1f)
        } else {
            0f
        }
    val finished = progress?.isFinished == true

    Card(onClick = onPlay, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = (episode.title ?: "Epizóda") + if (playing) "  ▶" else "",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text =
                    listOfNotNull(
                        episode.publishedAt?.let {
                            android.text.format.DateFormat
                                .getMediumDateFormat(context)
                                .format(Date(it))
                        },
                        duration.takeIf { it > 0 }?.let { "${(it / 60).toInt()} min" },
                        if (finished) "Dopočuté" else null,
                    ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
            ProgressBar(fraction = if (finished) 1f else fraction)
        }
    }
}

@Composable
private fun ProgressBar(fraction: Float) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(2.dp)),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(fraction)
                    .height(4.dp)
                    .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun PlayerBar(
    title: String,
    subtitle: String,
    coverUrl: String,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onPlayPause: () -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
) {
    val fraction =
        if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ProgressBar(fraction = fraction)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = subtitle,
                modifier = Modifier.size(56.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "${formatTime(positionMs)} / ${formatTime(durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onBack) { Text("−30 s") }
            Button(onClick = onPlayPause) { Text(if (isPlaying) "Pauza" else "Prehrať") }
            Button(onClick = onForward) { Text("+30 s") }
        }
    }
}

private fun formatTime(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

@Composable
private fun SettingsDialog(
    config: AbsConfig,
    error: String?,
    onSave: (AbsConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val lan = rememberTextFieldState(config.lanUrl)
    val tailscale = rememberTextFieldState(config.tailscaleUrl)
    val token = rememberTextFieldState(config.token)

    BasicDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.widthIn(min = 420.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Nastavenia Audiobookshelf", style = MaterialTheme.typography.titleLarge)
            Text(text = "Adresa v domácej sieti (primárna)", style = MaterialTheme.typography.bodySmall)
            EditTextBox(state = lan)
            Text(text = "Tailscale adresa (záložná, nepovinná)", style = MaterialTheme.typography.bodySmall)
            EditTextBox(state = tailscale)
            Text(text = "API token", style = MaterialTheme.typography.bodySmall)
            EditTextBox(state = token, isPassword = true)
            error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss) { Text("Zrušiť") }
                Button(
                    onClick = {
                        onSave(
                            AbsConfig(
                                lanUrl = lan.text.toString().trim(),
                                tailscaleUrl = tailscale.text.toString().trim(),
                                token = token.text.toString().trim(),
                            ),
                        )
                    },
                ) { Text("Uložiť") }
            }
        }
    }
}

package com.github.damontecres.wholphin.ui.audiobookshelf

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.audiobookshelf.AbsChapter
import com.github.damontecres.wholphin.services.audiobookshelf.AbsConnection
import com.github.damontecres.wholphin.services.audiobookshelf.AbsEpisode
import com.github.damontecres.wholphin.services.audiobookshelf.AbsMediaProgress
import com.github.damontecres.wholphin.ui.AppColors
import com.github.damontecres.wholphin.ui.cards.ItemCardImage
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.ExpandablePlayButton
import com.github.damontecres.wholphin.ui.components.LoadingPage
import com.github.damontecres.wholphin.ui.components.OverviewText
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.playback.isPlayKeyUp
import com.github.damontecres.wholphin.ui.tryRequestFocus
import java.util.Date
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Detail page of a podcast or book in the style of the movie details: big cover, title, details,
 * description and play buttons, then the episodes or chapters as a row of cards with progress.
 */
@Composable
fun AbsDetailPage(
    detail: AbsDetailState,
    conn: AbsConnection?,
    itemProgress: AbsMediaProgress?,
    episodeProgress: Map<String, AbsMediaProgress>,
    onPlay: (episodeId: String?, startMs: Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = detail.item
    val meta = item.media?.metadata
    val coverUrl = conn?.let { absCoverUrl(it, item.id) }
    val description = remember(meta?.description) { htmlToText(meta?.description) }
    var showDescription by remember { mutableStateOf<String?>(null) }
    val playFocusRequester = remember { FocusRequester() }
    LaunchedEffect(detail.loading) {
        if (!detail.loading) playFocusRequester.tryRequestFocus()
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            modifier
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 32.dp, bottom = 16.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(32.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AsyncImage(
                model = coverUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(280.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f).padding(end = 160.dp),
            ) {
                Text(
                    text = item.title,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                meta?.subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(text = it, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                }
                val episodeCount = detail.episodes.size.takeIf { it > 0 } ?: item.media?.numEpisodes
                val quickDetails =
                    listOfNotNull(
                        item.author,
                        meta?.publishedYear ?: meta?.releaseDate?.take(4),
                        if (item.isPodcast) {
                            episodeCount?.let { pluralStringResource(R.plurals.abs_episode_count, it, it) }
                        } else {
                            item.media
                                ?.duration
                                ?.takeIf { it > 0 }
                                ?.let { formatRuntime(it) }
                        },
                        meta
                            ?.genres
                            ?.take(3)
                            ?.joinToString(", ")
                            ?.takeIf { it.isNotBlank() },
                    ).filter { it.isNotBlank() }
                Text(
                    text = quickDetails.joinToString("  •  "),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val narrators = meta?.narrators?.joinToString(", ")?.takeIf { it.isNotBlank() } ?: meta?.narratorName
                narrators?.takeIf { it.isNotBlank() }?.let {
                    Text(text = stringResource(R.string.abs_narrated_by, it), style = MaterialTheme.typography.bodyMedium)
                }
                meta?.series?.firstOrNull()?.let { series ->
                    Text(
                        text =
                            series.sequence?.takeIf { it.isNotBlank() }?.let {
                                stringResource(R.string.abs_series_sequence, series.name, it)
                            } ?: series.name,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (!item.isPodcast && itemProgress != null) {
                    BookProgress(itemProgress, item.media?.duration ?: itemProgress.duration)
                }

                PlayButtons(
                    detail = detail,
                    itemProgress = itemProgress,
                    episodeProgress = episodeProgress,
                    onPlay = onPlay,
                    playFocusRequester = playFocusRequester,
                )

                description?.let { text ->
                    OverviewText(
                        overview = text,
                        maxLines = 5,
                        onClick = { showDescription = text },
                        textBoxHeight = Dp.Unspecified,
                    )
                }
            }
        }

        when {
            detail.loading -> {
                Box(modifier = Modifier.fillMaxWidth().height(160.dp)) {
                    LoadingPage(focusEnabled = false)
                }
            }

            detail.error != null -> {
                Text(text = detail.error, color = MaterialTheme.colorScheme.error)
            }

            item.isPodcast && detail.episodes.isNotEmpty() -> {
                EpisodeRow(
                    episodes = detail.episodes,
                    coverUrl = coverUrl,
                    episodeProgress = episodeProgress,
                    onPlay = { onPlay(it.id, null) },
                    onShowDescription = { showDescription = it },
                )
            }

            !item.isPodcast &&
                item.media
                    ?.chapters
                    .orEmpty()
                    .isNotEmpty()
            -> {
                ChapterRow(
                    chapters = item.media?.chapters.orEmpty(),
                    positionSeconds = itemProgress?.takeIf { !it.isFinished }?.currentTime,
                    onPlay = { chapter -> onPlay(null, (chapter.start * 1000).toLong()) },
                )
            }
        }
    }

    showDescription?.let { text ->
        BasicDialog(
            onDismissRequest = { showDescription = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Column(
                modifier =
                    Modifier
                        .width(720.dp)
                        .heightIn(max = 480.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
            ) {
                Text(text = text, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun BookProgress(
    progress: AbsMediaProgress,
    durationSec: Double,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        if (progress.isFinished) {
            Text(text = stringResource(R.string.abs_finished), style = MaterialTheme.typography.bodyMedium)
        } else if (durationSec > 0 && progress.currentTime > 0) {
            AbsProgressBar(
                fraction = (progress.currentTime / durationSec).toFloat(),
                modifier = Modifier.width(240.dp),
            )
            Text(
                text = stringResource(R.string.abs_time_left, formatRuntime(durationSec - progress.currentTime)),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun PlayButtons(
    detail: AbsDetailState,
    itemProgress: AbsMediaProgress?,
    episodeProgress: Map<String, AbsMediaProgress>,
    onPlay: (episodeId: String?, startMs: Long?) -> Unit,
    playFocusRequester: FocusRequester,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier =
            Modifier
                .padding(vertical = 8.dp)
                .focusGroup()
                .focusRestorer(playFocusRequester),
    ) {
        if (detail.item.isPodcast) {
            val resumeEpisode =
                detail.episodes.firstOrNull { ep ->
                    episodeProgress[ep.id]?.let { !it.isFinished && it.currentTime > 0 } == true
                }
            val latest = detail.episodes.maxByOrNull { it.publishedAt ?: 0L }
            if (resumeEpisode != null) {
                ExpandablePlayButton(
                    title = R.string.resume,
                    resume = (episodeProgress[resumeEpisode.id]?.currentTime ?: 0.0).seconds,
                    icon = Icons.Default.PlayArrow,
                    onClick = { onPlay(resumeEpisode.id, null) },
                    modifier = Modifier.focusRequester(playFocusRequester),
                )
            }
            if (latest != null && latest.id != resumeEpisode?.id) {
                ExpandablePlayButton(
                    title = R.string.abs_play_latest,
                    resume = Duration.ZERO,
                    icon = Icons.Default.PlayArrow,
                    onClick = {
                        val finished = episodeProgress[latest.id]?.isFinished == true
                        onPlay(latest.id, if (finished) 0L else null)
                    },
                    modifier = Modifier.ifElse(resumeEpisode == null, Modifier.focusRequester(playFocusRequester)),
                )
            }
        } else {
            val started = itemProgress != null && !itemProgress.isFinished && itemProgress.currentTime > 0
            ExpandablePlayButton(
                title = if (started) R.string.resume else R.string.play,
                resume = (if (started) itemProgress?.currentTime ?: 0.0 else 0.0).seconds,
                icon = Icons.Default.PlayArrow,
                onClick = { onPlay(null, if (started) null else 0L) },
                modifier = Modifier.focusRequester(playFocusRequester),
                enabled = !detail.loading,
            )
            if (started) {
                ExpandablePlayButton(
                    title = R.string.restart,
                    resume = Duration.ZERO,
                    icon = Icons.Default.Refresh,
                    onClick = { onPlay(null, 0L) },
                    mirrorIcon = true,
                )
            }
        }
    }
}

/** Podcast episodes as a row of cards like a season's episodes, with the focused episode's details below */
@Composable
private fun EpisodeRow(
    episodes: List<AbsEpisode>,
    coverUrl: String?,
    episodeProgress: Map<String, AbsMediaProgress>,
    onPlay: (AbsEpisode) -> Unit,
    onShowDescription: (String) -> Unit,
) {
    val context = LocalContext.current
    var focusedIndex by remember { mutableIntStateOf(0) }
    val firstFocus = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.episodes),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
            modifier = Modifier.fillMaxWidth().focusRestorer(firstFocus),
        ) {
            itemsIndexed(episodes, key = { _, ep -> ep.id }) { index, episode ->
                val progress = episodeProgress[episode.id]
                val interactionSource = remember { MutableInteractionSource() }
                if (interactionSource.collectIsFocusedAsState().value) focusedIndex = index
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.width(EpisodeCardWidth),
                ) {
                    Card(
                        onClick = { onPlay(episode) },
                        interactionSource = interactionSource,
                        colors = CardDefaults.colors(containerColor = Color.Transparent),
                        modifier =
                            Modifier
                                .ifElse(index == 0, Modifier.focusRequester(firstFocus))
                                .onKeyEvent {
                                    if (isPlayKeyUp(it)) {
                                        onPlay(episode)
                                        true
                                    } else {
                                        false
                                    }
                                },
                    ) {
                        ItemCardImage(
                            imageUrl = coverUrl,
                            name = episode.title,
                            showOverlay = true,
                            favorite = false,
                            watched = progress?.isFinished == true,
                            unwatchedCount = -1,
                            watchedPercent = progress.percent(),
                            numberOfVersions = 0,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(EpisodeCardWidth),
                        )
                    }
                    Text(
                        text = episode.title ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            listOfNotNull(
                                episode.publishedAt?.let {
                                    android.text.format.DateFormat
                                        .getMediumDateFormat(context)
                                        .format(Date(it))
                                },
                                episode.duration?.takeIf { it > 0 }?.let { formatRuntime(it) },
                            ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                    )
                }
            }
        }
        // Like the focused episode on a series page: its description below the row
        episodes.getOrNull(focusedIndex)?.let { episode ->
            val text = remember(episode.id) { htmlToText(episode.description ?: episode.subtitle) }
            text?.let {
                OverviewText(
                    overview = it,
                    maxLines = 3,
                    onClick = { onShowDescription(it) },
                    textBoxHeight = Dp.Unspecified,
                    modifier = Modifier.fillMaxWidth(.7f).padding(start = 8.dp),
                )
            }
        }
    }
}

/** Book chapters as cards, the current one highlighted with its progress */
@Composable
private fun ChapterRow(
    chapters: List<AbsChapter>,
    positionSeconds: Double?,
    onPlay: (AbsChapter) -> Unit,
) {
    val currentIndex = positionSeconds?.let { pos -> chapters.indexOfLast { it.start <= pos } } ?: -1
    val firstFocus = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(R.string.chapters),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 8.dp),
            modifier = Modifier.fillMaxWidth().focusRestorer(firstFocus),
        ) {
            itemsIndexed(chapters) { index, chapter ->
                val fraction =
                    when {
                        positionSeconds == null || index > currentIndex -> 0f
                        index < currentIndex -> 1f
                        else -> ((positionSeconds - chapter.start) / (chapter.end - chapter.start).coerceAtLeast(1.0)).toFloat()
                    }
                Card(
                    onClick = { onPlay(chapter) },
                    colors =
                        CardDefaults.colors(
                            containerColor =
                                if (index == currentIndex) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    AppColors.TransparentBlack50
                                },
                        ),
                    modifier =
                        Modifier
                            .width(220.dp)
                            .height(110.dp)
                            .ifElse(index == maxOf(currentIndex, 0), Modifier.focusRequester(firstFocus)),
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                    ) {
                        Text(
                            text = "${index + 1}. ${chapter.title ?: ""}",
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${formatClock((chapter.start * 1000).toLong())} · ${formatRuntime(chapter.end - chapter.start)}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        AbsProgressBar(fraction = fraction, modifier = Modifier.fillMaxWidth(), height = 3.dp)
                    }
                }
            }
        }
    }
}

private val EpisodeCardWidth = 160.dp

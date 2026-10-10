package com.github.damontecres.wholphin.ui.audiobookshelf

import android.view.Gravity
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.util.UnstableApi
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.DpadSeekMode
import com.github.damontecres.wholphin.ui.playback.PlaybackKeyHandler
import com.github.damontecres.wholphin.ui.playback.overlay.BottomDialog
import com.github.damontecres.wholphin.ui.playback.overlay.BottomDialogItem
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackAction
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackButtons
import com.github.damontecres.wholphin.ui.playback.overlay.PlaybackFaButton
import com.github.damontecres.wholphin.ui.playback.overlay.SeekBar
import com.github.damontecres.wholphin.ui.playback.overlay.SkipIndicator
import com.github.damontecres.wholphin.ui.playback.overlay.buttonSpacing
import com.github.damontecres.wholphin.ui.playback.overlay.rememberSeekBarState
import com.github.damontecres.wholphin.ui.tryRequestFocus
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Full screen player shown over the page (and the nav drawer) */
@Composable
fun AbsPlayerDialog(
    viewModel: AbsPlayerViewModel,
    onDismiss: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties =
            DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        AbsPlayerPage(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Audio mode of the Wholphin player: big cover over its blurred copy, and the regular Wholphin playback
 * controls (seek bar with chapter markers, D-pad seeking, playback speed).
 */
@OptIn(UnstableApi::class)
@Composable
fun AbsPlayerPage(
    viewModel: AbsPlayerViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val nowPlaying = state.nowPlaying ?: return
    val player = viewModel.player
    val controllerViewState = viewModel.controllerViewState
    val scope = rememberCoroutineScope()
    val seekBarState = rememberSeekBarState(player, scope)
    val chapters = nowPlaying.chapters
    val chapterIndex = chapterIndexAt(chapters, state.positionMs)
    val seekBack = ABS_SEEK_MS.milliseconds
    val seekForward = ABS_SEEK_MS.milliseconds

    var skipIndicatorDuration by remember { mutableLongStateOf(0L) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var showChaptersDialog by remember { mutableStateOf(false) }

    val keyHandler =
        remember(player) {
            PlaybackKeyHandler(
                player = player,
                controlsEnabled = true,
                skipWithLeftRight = true,
                seekBack = seekBack,
                seekForward = seekForward,
                getDurationMs = { player.duration },
                controllerViewState = controllerViewState,
                updateSkipIndicator = { delta ->
                    if ((skipIndicatorDuration > 0 && delta < 0) || (skipIndicatorDuration < 0 && delta > 0)) {
                        skipIndicatorDuration = 0
                    }
                    skipIndicatorDuration += delta
                },
                clearSkipIndicator = { skipIndicatorDuration = 0L },
                skipBackOnResume = null,
                oneClickPause = false,
                onInteraction = { controllerViewState.pulseControls() },
                onStop = viewModel::stop,
                onPlaybackDialogTypeClick = {},
                dpadSeekMode = DpadSeekMode.SEEKBAR_MINIMAL,
            )
        }
    // There is no media session for this player, so the remote's play/pause keys are handled here
    val handleMediaKey = { event: KeyEvent ->
        if (event.key == Key.MediaPlayPause || event.key == Key.MediaPlay || event.key == Key.MediaPause) {
            if (event.type == KeyEventType.KeyUp) {
                when (event.key) {
                    Key.MediaPlay -> player.play()
                    Key.MediaPause -> player.pause()
                    else -> viewModel.togglePlayPause()
                }
            }
            true
        } else {
            false
        }
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(controllerViewState.controlsVisible) {
        if (!controllerViewState.controlsVisible) focusRequester.tryRequestFocus()
    }
    BackHandler(controllerViewState.controlsVisible) {
        controllerViewState.hideControls()
    }

    Box(
        modifier =
            modifier
                .background(Color.Black)
                .onKeyEvent { handleMediaKey(it) },
    ) {
        // Blurred cover as the background
        AsyncImage(
            model = nowPlaying.coverUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .blur(48.dp),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = .45f), Color.Black.copy(alpha = .85f)),
                        ),
                    ),
        )
        // Receives the D-pad while the controls are hidden
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onPreviewKeyEvent { handleMediaKey(it) || keyHandler.onKeyEvent(it) }
                    .focusRequester(focusRequester)
                    .focusable(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(48.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(start = 64.dp, end = 64.dp, top = 56.dp),
        ) {
            AsyncImage(
                model = nowPlaying.coverUrl,
                contentDescription = nowPlaying.title,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(300.dp)
                        .shadow(16.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
            )
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (nowPlaying.subtitle.isNotBlank()) {
                    Text(
                        text = nowPlaying.subtitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = .75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = nowPlaying.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                chapters.getOrNull(chapterIndex)?.let { chapter ->
                    Text(
                        text = chapter.title ?: stringResource(R.string.abs_chapter_of, chapterIndex + 1, chapters.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.border,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.abs_chapter_of, chapterIndex + 1, chapters.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = .75f),
                    )
                }
                Text(
                    text =
                        buildString {
                            append(formatClock(state.positionMs))
                            append(" / ")
                            append(formatClock(state.durationMs))
                            if (state.speed != 1f) {
                                append("  •  ")
                                append(formatSpeed(state.speed))
                            }
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = .75f),
                )
                state.error?.let {
                    Text(text = it, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        // Thin progress bar along the bottom while the controls are hidden
        if (!controllerViewState.controlsVisible) {
            AbsProgressBar(
                fraction = if (state.durationMs > 0) state.positionMs.toFloat() / state.durationMs else 0f,
                height = 3.dp,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth(),
            )
            if (skipIndicatorDuration != 0L) {
                LaunchedEffect(skipIndicatorDuration) {
                    delay(1.5.seconds)
                    skipIndicatorDuration = 0L
                }
                SkipIndicator(
                    durationMs = skipIndicatorDuration,
                    onFinish = { skipIndicatorDuration = 0L },
                    modifier =
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 70.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = controllerViewState.controlsVisible,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            val playFocusRequester = remember { FocusRequester() }
            LaunchedEffect(Unit) { playFocusRequester.tryRequestFocus() }
            val onControllerInteraction = { controllerViewState.pulseControls() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .drawBehind {
                            drawRect(
                                brush =
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        startY = 0f,
                                        endY = size.height,
                                    ),
                            )
                        }.padding(top = 32.dp, bottom = 16.dp),
            ) {
                ChapterMarkers(
                    chapters = chapters,
                    durationMs = state.durationMs,
                    modifier =
                        Modifier
                            .fillMaxWidth(.95f)
                            .padding(horizontal = 4.dp)
                            .height(8.dp),
                )
                SeekBar(
                    player = player,
                    isEnabled = true,
                    intervals = 0,
                    controllerViewState = controllerViewState,
                    onSeekProgress = { seekBarState.onValueChange(it) },
                    seekBack = seekBack,
                    seekForward = seekForward,
                    modifier = Modifier.fillMaxWidth(.95f),
                )
                Box(
                    modifier =
                        Modifier
                            .padding(horizontal = 8.dp)
                            .fillMaxWidth(),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
                    ) {
                        PlaybackFaButton(
                            iconRes = R.string.fa_clock,
                            onClick = {
                                onControllerInteraction()
                                showSpeedDialog = true
                            },
                            onControllerInteraction = onControllerInteraction,
                        )
                        if (chapters.isNotEmpty()) {
                            PlaybackFaButton(
                                iconRes = R.string.fa_list_ul,
                                onClick = {
                                    onControllerInteraction()
                                    showChaptersDialog = true
                                },
                                onControllerInteraction = onControllerInteraction,
                            )
                        }
                    }
                    PlaybackButtons(
                        player = player,
                        initialFocusRequester = playFocusRequester,
                        onControllerInteraction = onControllerInteraction,
                        onPlaybackActionClick = { action ->
                            when (action) {
                                PlaybackAction.Previous -> {
                                    viewModel.previousChapter()
                                }

                                PlaybackAction.Next -> {
                                    viewModel.nextChapter()
                                }

                                else -> {}
                            }
                        },
                        showPlay = !state.isPlaying,
                        previousEnabled = true,
                        nextEnabled = chapterIndex < chapters.lastIndex,
                        seekBack = seekBack,
                        skipBackOnResume = null,
                        seekForward = seekForward,
                        modifier = Modifier.align(Alignment.Center),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 24.dp),
                    ) {
                        PlaybackFaButton(
                            iconRes = R.string.fa_xmark,
                            onClick = viewModel::stop,
                            onControllerInteraction = onControllerInteraction,
                        )
                    }
                }
            }
        }
    }

    if (showSpeedDialog) {
        val choices =
            remember {
                ABS_SPEEDS.map { BottomDialogItem(data = it, headline = formatSpeed(it), supporting = null) }
            }
        BottomDialog(
            choices = choices,
            currentChoice = choices.firstOrNull { it.data == state.speed },
            onDismissRequest = {
                showSpeedDialog = false
                controllerViewState.pulseControls()
            },
            onSelectChoice = { _, choice -> viewModel.setSpeed(choice.data) },
            gravity = Gravity.START,
        )
    }
    if (showChaptersDialog) {
        val choices =
            remember(chapters) {
                chapters.mapIndexed { index, chapter ->
                    BottomDialogItem(
                        data = (chapter.start * 1000).toLong(),
                        headline = "${index + 1}. ${chapter.title ?: ""}",
                        supporting = formatClock((chapter.start * 1000).toLong()),
                    )
                }
            }
        BottomDialog(
            choices = choices,
            currentChoice = choices.getOrNull(chapterIndex),
            onDismissRequest = {
                showChaptersDialog = false
                controllerViewState.pulseControls()
            },
            onSelectChoice = { _, choice -> viewModel.seekTo(choice.data) },
            gravity = Gravity.START,
        )
    }
}

/** Small ticks above the seek bar where chapters start */
@Composable
private fun ChapterMarkers(
    chapters: List<com.github.damontecres.wholphin.services.audiobookshelf.AbsChapter>,
    durationMs: Long,
    modifier: Modifier = Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = .7f)
    Canvas(modifier = modifier) {
        if (durationMs <= 0) return@Canvas
        chapters.drop(1).forEach { chapter ->
            val x = size.width * ((chapter.start * 1000) / durationMs).toFloat().coerceIn(0f, 1f)
            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

private fun formatSpeed(speed: Float): String {
    val text = if (speed == speed.toInt().toFloat()) speed.toInt().toString() else speed.toString()
    return "$text×"
}

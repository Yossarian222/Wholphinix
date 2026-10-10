package com.github.damontecres.wholphin.ui.audiobookshelf

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ConcatenatingMediaSource2
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.audiobookshelf.AbsAudioTrack
import com.github.damontecres.wholphin.services.audiobookshelf.AbsChapter
import com.github.damontecres.wholphin.services.audiobookshelf.AbsConnection
import com.github.damontecres.wholphin.services.audiobookshelf.AbsEpisode
import com.github.damontecres.wholphin.services.audiobookshelf.AbsLibraryItem
import com.github.damontecres.wholphin.services.audiobookshelf.AbsPlaySession
import com.github.damontecres.wholphin.services.audiobookshelf.AbsSyncRequest
import com.github.damontecres.wholphin.services.audiobookshelf.AudiobookshelfService
import com.github.damontecres.wholphin.ui.playback.ControllerViewState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** The episode or book currently loaded in the player */
data class AbsNowPlaying(
    val itemId: String,
    /** Null for a book */
    val episodeId: String?,
    /** Episode or book title */
    val title: String,
    /** Podcast title or the book's author */
    val subtitle: String,
    val coverUrl: String,
    /** Chapters of the book or episode, used as seek bar markers */
    val chapters: List<AbsChapter> = emptyList(),
)

data class AbsPlayerState(
    val nowPlaying: AbsNowPlaying? = null,
    /** The full screen player is shown */
    val showPlayer: Boolean = false,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val error: String? = null,
    /** Incremented after progress was saved on pause or stop, so the library can refresh its progress */
    val savedVersion: Int = 0,
)

/** Seek distance for the rewind and forward buttons */
const val ABS_SEEK_MS = 30_000L

/** Playback speeds offered in the speed dialog */
val ABS_SPEEDS = listOf(0.75f, 1f, 1.1f, 1.25f, 1.5f, 1.75f, 2f)

/** How often progress is sent to the server while playing */
private const val SYNC_INTERVAL_MS = 10_000L

/**
 * Plays Audiobookshelf podcast episodes and books with a local [ExoPlayer] and reports the progress back
 * to the server. A book with several audio files is played as one continuous timeline.
 */
@HiltViewModel
class AbsPlayerViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val service: AudiobookshelfService,
        private val screensaverService: ScreensaverService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AbsPlayerState())
        val state: StateFlow<AbsPlayerState> = _state.asStateFlow()

        val controllerViewState =
            ControllerViewState(
                AppPreference.ControllerTimeout.defaultValue,
                true,
            )

        private var session: AbsPlaySession? = null
        private var sessionConn: AbsConnection? = null
        private var listenedSinceSyncMs = 0L

        private val exoPlayer: ExoPlayer =
            ExoPlayer
                .Builder(context)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .setSeekBackIncrementMs(ABS_SEEK_MS)
                .setSeekForwardIncrementMs(ABS_SEEK_MS)
                .build()

        /** The player for the shared Wholphin playback controls */
        val player: Player get() = exoPlayer

        init {
            exoPlayer.addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                        if (!isPlaying) syncNow(notify = true)
                    }

                    override fun onPlayWhenReadyChanged(
                        playWhenReady: Boolean,
                        reason: Int,
                    ) {
                        updateKeepScreenOn()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        updateKeepScreenOn()
                        if (playbackState == Player.STATE_ENDED) {
                            syncNow(notify = true)
                        }
                    }
                },
            )
            viewModelScope.launch {
                controllerViewState.observe()
            }
            viewModelScope.launch {
                var sinceSyncMs = 0L
                while (isActive) {
                    delay(1_000)
                    if (exoPlayer.isPlaying) {
                        listenedSinceSyncMs += 1_000
                        sinceSyncMs += 1_000
                    }
                    _state.update {
                        it.copy(
                            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
                            durationMs = exoPlayer.duration.coerceAtLeast(0L),
                        )
                    }
                    if (exoPlayer.isPlaying && sinceSyncMs >= SYNC_INTERVAL_MS) {
                        sinceSyncMs = 0L
                        syncNow(notify = false)
                    }
                }
            }
        }

        /**
         * Starts playback of a podcast episode, or of a book when [episode] is null.
         *
         * @param startMs where to start, e.g. 0 to restart or a chapter's start; null resumes from the saved position
         */
        fun play(
            item: AbsLibraryItem,
            episode: AbsEpisode?,
            startMs: Long? = null,
        ) {
            viewModelScope.launch {
                try {
                    val cfg = service.config.first()
                    closeCurrentSession()
                    val (conn, sess) =
                        service
                            .withConnection(cfg) { c ->
                                c to service.startPlay(c, item.id, episode?.id)
                            }.second
                    val tracks = sess.audioTracks.sortedBy { it.startOffset ?: it.index?.toDouble() ?: 0.0 }
                    if (tracks.isEmpty()) throw IllegalStateException("Server returned no audio track")
                    val title = episode?.title ?: sess.displayTitle ?: item.title
                    val subtitle =
                        if (episode != null) item.title else sess.displayAuthor ?: item.author.orEmpty()
                    val coverUrl = service.coverUrl(conn, item.id)
                    // Stop first so the listener's sync can't report the old position for the new session
                    exoPlayer.stop()
                    setMedia(conn, tracks, title, subtitle, coverUrl)
                    exoPlayer.prepare()
                    val startAtMs = startMs ?: (sess.currentTime * 1000).toLong()
                    exoPlayer.seekTo(startAtMs)
                    session = sess
                    sessionConn = conn
                    listenedSinceSyncMs = 0L
                    exoPlayer.setPlaybackSpeed(_state.value.speed)
                    exoPlayer.play()
                    _state.update {
                        it.copy(
                            nowPlaying =
                                AbsNowPlaying(
                                    itemId = item.id,
                                    episodeId = episode?.id,
                                    title = title,
                                    subtitle = subtitle,
                                    coverUrl = coverUrl,
                                    chapters = sess.chapters.ifEmpty { episode?.chapters.orEmpty() },
                                ),
                            showPlayer = true,
                            positionMs = startAtMs,
                            durationMs = (sess.duration * 1000).toLong(),
                            error = null,
                        )
                    }
                    controllerViewState.showControls()
                } catch (ex: Exception) {
                    Timber.e(ex, "Audiobookshelf playback failed")
                    _state.update { it.copy(error = ex.message ?: "Playback failed") }
                }
            }
        }

        /** One audio file plays directly; several are joined into one timeline so seeking spans the whole book */
        @OptIn(UnstableApi::class)
        private fun setMedia(
            conn: AbsConnection,
            tracks: List<AbsAudioTrack>,
            title: String,
            subtitle: String,
            coverUrl: String,
        ) {
            val metadata =
                MediaMetadata
                    .Builder()
                    .setTitle(title)
                    .setArtist(subtitle)
                    .setArtworkUri(Uri.parse(coverUrl))
                    .build()
            if (tracks.size == 1) {
                exoPlayer.setMediaItem(
                    MediaItem
                        .Builder()
                        .setUri(streamUrl(conn, tracks.first().contentUrl))
                        .setMediaMetadata(metadata)
                        .build(),
                )
            } else {
                val builder =
                    ConcatenatingMediaSource2
                        .Builder()
                        .useDefaultMediaSourceFactory(context)
                        .setMediaItem(MediaItem.Builder().setMediaMetadata(metadata).build())
                tracks.forEach { track ->
                    builder.add(
                        MediaItem.fromUri(streamUrl(conn, track.contentUrl)),
                        ((track.duration ?: 0.0) * 1000).toLong().takeIf { it > 0 } ?: C.TIME_UNSET,
                    )
                }
                exoPlayer.setMediaSource(builder.build())
            }
        }

        fun showPlayer() {
            if (_state.value.nowPlaying != null) {
                _state.update { it.copy(showPlayer = true) }
                controllerViewState.showControls()
            }
        }

        /** Leaves the full screen player; playback continues in the mini player */
        fun hidePlayer() {
            _state.update { it.copy(showPlayer = false) }
        }

        /** Stops playback, saves the position and closes the session */
        fun stop() {
            exoPlayer.pause()
            viewModelScope.launch {
                closeCurrentSession()
                exoPlayer.stop()
                exoPlayer.clearMediaItems()
                _state.update {
                    it.copy(
                        nowPlaying = null,
                        showPlayer = false,
                        savedVersion = it.savedVersion + 1,
                    )
                }
            }
        }

        fun pause() = exoPlayer.pause()

        fun togglePlayPause() {
            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
        }

        fun setSpeed(speed: Float) {
            exoPlayer.setPlaybackSpeed(speed)
            _state.update { it.copy(speed = speed) }
        }

        fun seekTo(positionMs: Long) = exoPlayer.seekTo(positionMs.coerceAtLeast(0L))

        /** Jumps to the start of the current chapter, or the previous one when close to the current start */
        fun previousChapter() {
            val chapters =
                _state.value.nowPlaying
                    ?.chapters
                    .orEmpty()
            val position = exoPlayer.currentPosition
            val target =
                chapters
                    .map { (it.start * 1000).toLong() }
                    .lastOrNull { it < position - 3_000 }
                    ?: 0L
            exoPlayer.seekTo(target)
        }

        fun nextChapter() {
            val chapters =
                _state.value.nowPlaying
                    ?.chapters
                    .orEmpty()
            val position = exoPlayer.currentPosition
            chapters
                .map { (it.start * 1000).toLong() }
                .firstOrNull { it > position + 500 }
                ?.let { exoPlayer.seekTo(it) }
        }

        /** No screensaver while audio plays; paused for 15 minutes starts the system screensaver */
        private fun updateKeepScreenOn() {
            val playing =
                exoPlayer.playWhenReady &&
                    exoPlayer.playbackState != Player.STATE_ENDED &&
                    exoPlayer.playbackState != Player.STATE_IDLE
            if (playing) {
                screensaverService.acquireKeepScreenOn(this)
            } else {
                screensaverService.releaseKeepScreenOn(this)
            }
        }

        /**
         * The audio file URL. The file endpoints need authentication, which the player gets as the `token` query
         * parameter (like the cover images). `contentUrl` already contains the server's base path, if any.
         */
        private fun streamUrl(
            conn: AbsConnection,
            contentUrl: String,
        ): String {
            val base = conn.baseUrl.trimEnd('/')
            val uri = Uri.parse(base)
            val basePath = uri.path.orEmpty().trimEnd('/')
            val root =
                if (basePath.isNotEmpty() && contentUrl.startsWith("$basePath/")) {
                    base.removeSuffix(basePath)
                } else {
                    base
                }
            val separator = if ('?' in contentUrl) '&' else '?'
            return "$root$contentUrl${separator}token=${Uri.encode(conn.token)}"
        }

        /** Sends the current position to the server. Runs on the main thread, then the request runs in the background. */
        private fun syncNow(notify: Boolean) {
            val s = session ?: return
            val c = sessionConn ?: return
            val request = currentSyncRequest(s)
            viewModelScope.launch {
                try {
                    service.sync(c, s.id, request)
                    if (notify) _state.update { it.copy(savedVersion = it.savedVersion + 1) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Audiobookshelf sync failed")
                }
            }
        }

        private fun currentSyncRequest(s: AbsPlaySession): AbsSyncRequest {
            val durationSec =
                exoPlayer.duration.takeIf { it > 0 }?.div(1000.0) ?: s.duration
            val listened = listenedSinceSyncMs / 1000.0
            listenedSinceSyncMs = 0L
            return AbsSyncRequest(
                currentTime = exoPlayer.currentPosition.coerceAtLeast(0L) / 1000.0,
                timeListened = listened,
                duration = durationSec,
            )
        }

        private suspend fun closeCurrentSession() {
            val s = session ?: return
            val c = sessionConn ?: return
            session = null
            sessionConn = null
            try {
                service.sync(c, s.id, currentSyncRequest(s))
                service.close(c, s.id)
            } catch (ex: Exception) {
                Timber.w(ex, "Audiobookshelf close failed")
            }
        }

        override fun onCleared() {
            // viewModelScope is already cancelled here, so finish the last sync on a plain scope
            val s = session
            val c = sessionConn
            if (s != null && c != null) {
                val request = currentSyncRequest(s)
                CoroutineScope(WholphinDispatchers.IO).launch {
                    try {
                        service.sync(c, s.id, request)
                        service.close(c, s.id)
                    } catch (ex: Exception) {
                        Timber.w(ex, "Audiobookshelf final sync failed")
                    }
                }
            }
            screensaverService.releaseKeepScreenOn(this)
            exoPlayer.release()
            super.onCleared()
        }
    }

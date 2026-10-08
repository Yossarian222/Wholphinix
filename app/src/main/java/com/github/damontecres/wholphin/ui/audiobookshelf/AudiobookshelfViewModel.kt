package com.github.damontecres.wholphin.ui.audiobookshelf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.github.damontecres.wholphin.services.audiobookshelf.AbsConfig
import com.github.damontecres.wholphin.services.audiobookshelf.AbsConnection
import com.github.damontecres.wholphin.services.audiobookshelf.AbsEpisode
import com.github.damontecres.wholphin.services.audiobookshelf.AbsLibraryItem
import com.github.damontecres.wholphin.services.audiobookshelf.AbsMediaProgress
import com.github.damontecres.wholphin.services.audiobookshelf.AbsPlaySession
import com.github.damontecres.wholphin.services.audiobookshelf.AbsSyncRequest
import com.github.damontecres.wholphin.services.audiobookshelf.AudiobookshelfService
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

/** The episode currently loaded in the player */
data class AbsNowPlaying(
    val itemId: String,
    val episodeId: String,
    val podcastTitle: String,
    val episodeTitle: String,
    val coverUrl: String,
)

data class AbsUiState(
    val configured: Boolean = false,
    val loading: Boolean = false,
    val error: String? = null,
    val podcasts: List<AbsLibraryItem> = emptyList(),
    val selected: AbsLibraryItem? = null,
    /** Episodes already sorted: started (not finished) first, then newest first */
    val episodes: List<AbsEpisode> = emptyList(),
    /** Saved progress keyed by episode id */
    val progress: Map<String, AbsMediaProgress> = emptyMap(),
    val nowPlaying: AbsNowPlaying? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val showSettings: Boolean = false,
    val settingsError: String? = null,
    val config: AbsConfig = AbsConfig(),
    /** The address that answered most recently (LAN or Tailscale), used for cover images */
    val activeBaseUrl: String = "",
)

/** Seek distance for the rewind and forward buttons */
const val ABS_SEEK_MS = 30_000L

/** How often progress is sent to the server while playing */
private const val SYNC_INTERVAL_MS = 10_000L

@HiltViewModel
class AudiobookshelfViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val service: AudiobookshelfService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(AbsUiState())
        val state: StateFlow<AbsUiState> = _state.asStateFlow()

        private var config: AbsConfig? = null
        private var libraryConn: AbsConnection? = null

        private var session: AbsPlaySession? = null
        private var sessionConn: AbsConnection? = null
        private var listenedSinceSyncMs = 0L

        private val player: ExoPlayer =
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

        init {
            player.addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _state.update { it.copy(isPlaying = isPlaying) }
                        if (!isPlaying) syncNow()
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_ENDED) {
                            syncNow()
                        }
                    }
                },
            )
            viewModelScope.launch {
                var sinceSyncMs = 0L
                while (isActive) {
                    delay(1_000)
                    if (player.isPlaying) {
                        listenedSinceSyncMs += 1_000
                        sinceSyncMs += 1_000
                    }
                    _state.update {
                        it.copy(
                            positionMs = player.currentPosition.coerceAtLeast(0L),
                            durationMs = player.duration.coerceAtLeast(0L),
                        )
                    }
                    if (player.isPlaying && sinceSyncMs >= SYNC_INTERVAL_MS) {
                        sinceSyncMs = 0L
                        syncNow()
                    }
                }
            }
        }

        /** Loads the saved settings and, if configured, the podcast list */
        fun load() {
            viewModelScope.launch {
                val cfg = service.config.first()
                config = cfg
                _state.update { it.copy(configured = cfg.isComplete, config = cfg) }
                if (!cfg.isComplete) return@launch

                _state.update { it.copy(loading = true, error = null) }
                try {
                    val (conn, libraries) = service.withConnection(cfg) { c -> service.libraries(c) }
                    libraryConn = conn
                    val podcastLibrary =
                        libraries.libraries.firstOrNull { it.mediaType == "podcast" }
                            ?: throw IllegalStateException("No podcast library found")

                    val items = service.libraryItems(conn, podcastLibrary.id).results
                    val progress = loadProgress(conn)
                    _state.update {
                        it.copy(
                            loading = false,
                            podcasts = items,
                            progress = progress,
                            activeBaseUrl = conn.baseUrl,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Audiobookshelf load failed")
                    _state.update { it.copy(loading = false, error = ex.message ?: "Chyba pripojenia") }
                }
            }
        }

        private suspend fun loadProgress(conn: AbsConnection): Map<String, AbsMediaProgress> =
            service
                .me(conn)
                .mediaProgress
                .filter { it.episodeId != null }
                .associateBy { it.episodeId!! }

        /** Opens a podcast and shows its episodes with the started ones first */
        fun openPodcast(item: AbsLibraryItem) {
            val conn = libraryConn ?: return
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                try {
                    val full = service.podcast(conn, item.id)
                    val progress = _state.value.progress
                    val sorted =
                        full.media
                            ?.episodes
                            .orEmpty()
                            .sortedWith(
                                compareBy<AbsEpisode>(
                                    { if (isStarted(progress[it.id])) 0 else 1 },
                                    { -(it.publishedAt ?: 0L) },
                                ),
                            )
                    _state.update {
                        it.copy(loading = false, selected = full, episodes = sorted)
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Audiobookshelf podcast load failed")
                    _state.update { it.copy(loading = false, error = ex.message ?: "Chyba pripojenia") }
                }
            }
        }

        fun closePodcast() {
            _state.update { it.copy(selected = null, episodes = emptyList()) }
        }

        /** Starts playback of one episode, resuming from the saved position */
        fun play(
            podcast: AbsLibraryItem,
            episode: AbsEpisode,
        ) {
            val cfg = config ?: return
            viewModelScope.launch {
                try {
                    closeCurrentSession()
                    val (conn, sess) =
                        service.withConnection(cfg) { c ->
                            c to service.startPlay(c, podcast.id, episode.id)
                        }.second
                    val track =
                        sess.audioTracks.firstOrNull()
                            ?: throw IllegalStateException("Server returned no audio track")
                    session = sess
                    sessionConn = conn
                    player.setMediaItem(MediaItem.fromUri(conn.baseUrl.trimEnd('/') + track.contentUrl))
                    player.prepare()
                    player.seekTo((sess.currentTime * 1000).toLong())
                    player.play()
                    _state.update {
                        it.copy(
                            nowPlaying =
                                AbsNowPlaying(
                                    itemId = podcast.id,
                                    episodeId = episode.id,
                                    podcastTitle = podcast.title,
                                    episodeTitle = episode.title ?: "",
                                    coverUrl = service.coverUrl(conn, podcast.id),
                                ),
                            durationMs = (sess.duration * 1000).toLong(),
                            error = null,
                        )
                    }
                } catch (ex: Exception) {
                    Timber.e(ex, "Audiobookshelf playback failed")
                    _state.update { it.copy(error = ex.message ?: "Prehrávanie zlyhalo") }
                }
            }
        }

        fun togglePlayPause() {
            if (player.isPlaying) player.pause() else player.play()
        }

        fun seekBack() = player.seekBack()

        fun seekForward() = player.seekForward()

        fun openSettings() {
            _state.update { it.copy(showSettings = true, settingsError = null) }
        }

        fun closeSettings() {
            _state.update { it.copy(showSettings = false) }
        }

        /** Tests the entered settings, saves them if the server answers, then reloads */
        fun saveSettings(draft: AbsConfig) {
            viewModelScope.launch {
                try {
                    service.withConnection(draft) { c -> service.libraries(c) }
                    service.save(draft)
                    config = draft
                    _state.update {
                        it.copy(
                            showSettings = false,
                            settingsError = null,
                            config = draft,
                            configured = draft.isComplete,
                        )
                    }
                    load()
                } catch (ex: Exception) {
                    Timber.w(ex, "Audiobookshelf settings test failed")
                    _state.update { it.copy(settingsError = ex.message ?: "Nepodarilo sa pripojiť") }
                }
            }
        }

        private fun isStarted(progress: AbsMediaProgress?): Boolean =
            progress != null && !progress.isFinished && progress.currentTime > 0

        /** Sends the current position to the server. Runs on the main thread, then the request runs in the background. */
        private fun syncNow() {
            val s = session ?: return
            val c = sessionConn ?: return
            val request = currentSyncRequest(s)
            viewModelScope.launch {
                try {
                    service.sync(c, s.id, request)
                } catch (ex: Exception) {
                    Timber.w(ex, "Audiobookshelf sync failed")
                }
            }
        }

        private fun currentSyncRequest(s: AbsPlaySession): AbsSyncRequest {
            val durationSec =
                player.duration.takeIf { it > 0 }?.div(1000.0) ?: s.duration
            val listened = listenedSinceSyncMs / 1000.0
            listenedSinceSyncMs = 0L
            return AbsSyncRequest(
                currentTime = player.currentPosition.coerceAtLeast(0L) / 1000.0,
                timeListened = listened,
                duration = durationSec,
            )
        }

        private suspend fun closeCurrentSession() {
            val s = session ?: return
            val c = sessionConn ?: return
            try {
                service.sync(c, s.id, currentSyncRequest(s))
                service.close(c, s.id)
            } catch (ex: Exception) {
                Timber.w(ex, "Audiobookshelf close failed")
            }
            session = null
            sessionConn = null
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
            player.release()
            super.onCleared()
        }
    }

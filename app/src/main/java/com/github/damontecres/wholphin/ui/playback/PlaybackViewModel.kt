package com.github.damontecres.wholphin.ui.playback

import android.content.Context
import android.media.MediaCodecList
import android.os.Build
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.compose.ui.unit.Density
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.DecoderCounters
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser
import androidx.media3.extractor.text.subrip.SubripParser
import androidx.media3.extractor.text.webvtt.WebvttParser
import androidx.media3.session.MediaSession
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.size.Size
import com.github.damontecres.wholphin.data.ItemPlaybackDao
import com.github.damontecres.wholphin.data.ItemPlaybackRepository
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.Chapter
import com.github.damontecres.wholphin.data.model.ItemPlayback
import com.github.damontecres.wholphin.data.model.PlaybackLanguageChoice
import com.github.damontecres.wholphin.data.model.Playlist
import com.github.damontecres.wholphin.data.model.PlaylistItem
import com.github.damontecres.wholphin.data.model.TrackIndex
import com.github.damontecres.wholphin.mpv.MpvPlayer
import com.github.damontecres.wholphin.preferences.AppPreference
import com.github.damontecres.wholphin.preferences.PlayerBackend
import com.github.damontecres.wholphin.preferences.ShowNextUpWhen
import com.github.damontecres.wholphin.preferences.SkipSegmentBehavior
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.enabled
import com.github.damontecres.wholphin.services.AutoForeignSubtitles
import com.github.damontecres.wholphin.services.ClaudeCompanionService
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.DeviceProfileService
import com.github.damontecres.wholphin.services.DeviceStatusService
import com.github.damontecres.wholphin.services.PlaybackEvents
import com.github.damontecres.wholphin.services.ImageUrlService
import com.github.damontecres.wholphin.services.MusicService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.PendingRatingService
import com.github.damontecres.wholphin.services.PlayerFactory
import com.github.damontecres.wholphin.services.PlaylistCreationResult
import com.github.damontecres.wholphin.services.PlaylistCreator
import com.github.damontecres.wholphin.services.RefreshRateService
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.StreamChoiceService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.hilt.AuthOkHttpClient
import com.github.damontecres.wholphin.ui.formatBitrate
import com.github.damontecres.wholphin.ui.gt
import com.github.damontecres.wholphin.ui.isNotNullOrBlank
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.ui.onMain
import com.github.damontecres.wholphin.ui.preferences.subtitle.SubtitleSettings.applyToMpv
import com.github.damontecres.wholphin.ui.seekBack
import com.github.damontecres.wholphin.ui.seekForward
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.PlaybackItemState
import com.github.damontecres.wholphin.util.TrackActivityPlaybackListener
import com.github.damontecres.wholphin.util.WholphinDispatchers
import com.github.damontecres.wholphin.util.checkForSupport
import com.github.damontecres.wholphin.util.mpv.mpvDeviceProfile
import com.github.damontecres.wholphin.util.profile.Codec
import com.github.damontecres.wholphin.util.subtitleMimeTypes
import com.github.damontecres.wholphin.util.supportItemKinds
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.mediaInfoApi
import org.jellyfin.sdk.api.client.extensions.mediaSegmentsApi
import org.jellyfin.sdk.api.client.extensions.sessionApi
import org.jellyfin.sdk.api.client.extensions.trickplayApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.api.client.extensions.videosApi
import org.jellyfin.sdk.api.sockets.subscribe
import org.jellyfin.sdk.model.DeviceInfo
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.GeneralCommand
import org.jellyfin.sdk.model.api.GeneralCommandMessage
import org.jellyfin.sdk.model.api.GeneralCommandType
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.jellyfin.sdk.model.api.MediaSourceInfo
import org.jellyfin.sdk.model.api.MediaStream
import org.jellyfin.sdk.model.api.MediaStreamType
import org.jellyfin.sdk.model.api.MediaType
import org.jellyfin.sdk.model.api.PlayMethod
import org.jellyfin.sdk.model.api.PlaybackInfoDto
import org.jellyfin.sdk.model.api.PlaystateCommand
import org.jellyfin.sdk.model.api.PlaystateMessage
import org.jellyfin.sdk.model.api.PlaystateRequest
import org.jellyfin.sdk.model.api.TrickplayInfo
import org.jellyfin.sdk.model.api.VideoRange
import org.jellyfin.sdk.model.api.VideoRangeType
import org.jellyfin.sdk.model.extensions.inWholeTicks
import org.jellyfin.sdk.model.extensions.ticks
import org.jellyfin.sdk.model.serializer.toUUIDOrNull
import timber.log.Timber
import java.io.File
import java.util.Date
import java.util.UUID
import kotlin.math.roundToInt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * This [ViewModel] is responsible for playing media including moving through playlists (including next up episodes)
 */
@HiltViewModel(assistedFactory = PlaybackViewModel.Factory::class)
@OptIn(markerClass = [UnstableApi::class])
class PlaybackViewModel
    @AssistedInject
    constructor(
        @param:ApplicationContext internal val context: Context,
        internal val api: ApiClient,
        val navigationManager: NavigationManager,
        private val playlistCreator: PlaylistCreator,
        private val itemPlaybackDao: ItemPlaybackDao,
        internal val serverRepository: ServerRepository,
        internal val itemPlaybackRepository: ItemPlaybackRepository,
        private val playerFactory: PlayerFactory,
        private val datePlayedService: DatePlayedService,
        private val deviceInfo: DeviceInfo,
        private val deviceProfileService: DeviceProfileService,
        private val refreshRateService: RefreshRateService,
        val streamChoiceService: StreamChoiceService,
        private val userPreferencesService: UserPreferencesService,
        private val imageUrlService: ImageUrlService,
        private val screensaverService: ScreensaverService,
        private val musicService: MusicService,
        private val pendingRatingService: PendingRatingService,
        private val claudeCompanionService: ClaudeCompanionService,
        private val deviceStatusService: DeviceStatusService,
        @param:AuthOkHttpClient private val authOkHttpClient: OkHttpClient,
        @Assisted private val destination: Destination,
    ) : ViewModel(),
        Player.Listener,
        AnalyticsListener {
        @AssistedFactory
        interface Factory {
            fun create(destination: Destination): PlaybackViewModel
        }

        val currentPlayer = MutableStateFlow<PlayerInstance?>(null)

        internal lateinit var player: Player

        private var mediaSession: MediaSession? = null

        // Mutex & Job for changing playlist index
        private val playlistMutex = Mutex()
        private var playlistJob: Job? = null

        val controllerViewState =
            ControllerViewState(
                AppPreference.ControllerTimeout.defaultValue,
                true,
            )

        private val _state = MutableStateFlow(PlaybackState())
        val state: StateFlow<PlaybackState> = _state

        internal lateinit var preferences: UserPreferences
        internal lateinit var itemId: UUID
        internal lateinit var currentItem: PlaylistItem
        internal var forceTranscoding: Boolean = false
        private var activityListener: TrackActivityPlaybackListener? = null
        private var trackChangeListener: TracksChangedListener? = null

        /** When the current item was asked to play, to log how long the start took (#56) */
        private var startRequestedAtMs: Long? = null
        private val jobs = mutableListOf<Job>()
        private var subscribeJob: Job? = null

        private val isPlaylist = destination is Destination.PlaybackList

        val subtitleSearchState = MutableStateFlow(SubtitleSearchState())

        val currentUserDto = serverRepository.currentUserDtoFlow

        // Exposed for testing
        val initJob: Job

        init {
            // No screensaver while playing (also while buffering); paused for 15 minutes starts the system one.
            // Acquired here, before anything asynchronous, so the release in onCleared always comes after it.
            screensaverService.acquireKeepScreenOn(this)
            addCloseable { screensaverService.releaseKeepScreenOn(this@PlaybackViewModel) }
            // Asked before the system screensaver starts, in case a missed event released the screen while playing
            screensaverService.registerPlaybackProbe(this) {
                ::player.isInitialized &&
                    player.playWhenReady &&
                    (player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING)
            }
            addCloseable { screensaverService.unregisterPlaybackProbe(this@PlaybackViewModel) }
            addCloseable { claudeCompanionService.onPlaybackStopped() }
            // How the video plays, for device_status in Telka (#52)
            viewModelScope.launchIO {
                _state.collect { st -> deviceStatusService.playback = st.toDeviceSnapshot() }
            }
            addCloseable { deviceStatusService.playback = null }
            initJob =
                viewModelScope.launchIO {
                    addCloseable {
                        disconnectPlayer()
                    }
                    init()
                }
        }

        private fun disconnectPlayer() {
            if (this@PlaybackViewModel::player.isInitialized) {
                player.removeListener(this@PlaybackViewModel)
                (player as? ExoPlayer)?.removeAnalyticsListener(this@PlaybackViewModel)

                this@PlaybackViewModel.activityListener?.let {
                    it.release()
                    player.removeListener(it)
                }
                this@PlaybackViewModel.trackChangeListener?.let {
                    player.removeListener(it)
                }
                player.release()
                mediaSession?.release()
            }
            jobs.forEach { it.cancel() }
        }

        private suspend fun createPlayer(
            isHdr: Boolean,
            is4k: Boolean,
        ) {
            val softwareDecoding =
                !preferences.appPreferences.playbackPreferences.mpvOptions.enableHardwareDecoding
            val requestBackend =
                (destination as? Destination.Playback)?.backend
                    ?: preferences.appPreferences.playbackPreferences.playerBackend
            val playerBackend =
                when (requestBackend) {
                    PlayerBackend.UNRECOGNIZED,
                    PlayerBackend.EXO_PLAYER,
                    -> PlayerBackend.EXO_PLAYER

                    PlayerBackend.MPV -> PlayerBackend.MPV

                    PlayerBackend.PREFER_MPV -> if (isHdr || (is4k && softwareDecoding)) PlayerBackend.EXO_PLAYER else PlayerBackend.MPV

                    PlayerBackend.EXTERNAL_PLAYER -> throw IllegalStateException("Cannot use this for external playback")
                }

            Timber.d("Selected backend: %s", playerBackend)
            if (currentPlayer.value?.backend != playerBackend) {
                Timber.i("Switching player backend to %s", playerBackend)
                withContext(WholphinDispatchers.Main) {
                    disconnectPlayer()
                }

                val playerCreation =
                    playerFactory.createVideoPlayer(
                        playerBackend,
                        preferences.appPreferences,
                    )
                this.player = playerCreation.player
                currentPlayer.update {
                    PlayerInstance(playerCreation.player, playerBackend, playerCreation.assHandler)
                }
                configurePlayer()
            }
        }

        private fun configurePlayer() {
            player.addListener(this)
            (player as? ExoPlayer)?.addAnalyticsListener(this)
            subscribeToWebSocket()
            jobs.add(listenForTranscodeReason())
            val sessionPlayer =
                MediaSessionPlayer(
                    player,
                    preferences.appPreferences.playbackPreferences,
                )
            mediaSession = playerFactory.createMediaSession(sessionPlayer)
        }

        /**
         * Initialize from the UI to start playback
         */
        private suspend fun init() {
            musicService.stop()
            _state.update { it.copy(nextUp = null) }
            this.preferences = userPreferencesService.getCurrent()
            if (preferences.appPreferences.playbackPreferences.refreshRateSwitching) {
                addCloseable { refreshRateService.resetRefreshRate() }
            }
            controllerViewState.hideMilliseconds =
                preferences.appPreferences.playbackPreferences.controllerTimeoutMs
            this.forceTranscoding =
                (destination as? Destination.Playback)?.forceTranscoding ?: false
            val positionMs: Long
            val forceTranscoding: Boolean

            val itemId =
                when (val d = destination) {
                    is Destination.Playback -> {
                        positionMs = d.positionMs
                        forceTranscoding = d.forceTranscoding
                        d.itemId
                    }

                    is Destination.PlaybackList -> {
                        positionMs = 0
                        forceTranscoding = false
                        d.itemId
                    }

                    else -> {
                        throw IllegalArgumentException("Destination not supported: $destination")
                    }
                }
            this.itemId = itemId
            val queriedItem = api.userLibraryApi.getItem(itemId).content
            val playlistItem =
                if (queriedItem.type.playable) {
                    PlaylistItem.Media(BaseItem(queriedItem, false))
                } else {
                    val playlistResult =
                        if (destination is Destination.PlaybackList) {
                            playlistCreator.createFrom(
                                item = queriedItem,
                                startIndex = destination.startIndex ?: 0,
                                sortAndDirection = destination.sortAndDirection,
                                shuffled = destination.shuffle,
                                recursive = destination.recursive,
                                filter = destination.filter,
                            )
                        } else {
                            val shuffled =
                                if (destination is Destination.Playback) {
                                    destination.shuffle
                                } else {
                                    false
                                }
                            // Try to create a playlist
                            playlistCreator.createFrom(
                                item = queriedItem,
                                recursive = true,
                                shuffled = shuffled,
                            )
                        }
                    when (val r = playlistResult) {
                        is PlaylistCreationResult.Error -> {
                            _state.update { it.copy(loading = LoadingState.Error(r.message, r.ex)) }
                            return
                        }

                        is PlaylistCreationResult.Success -> {
                            if (r.playlist.items.isEmpty()) {
                                showToast(context, "Playlist is empty", Toast.LENGTH_SHORT)
                                navigationManager.goBack()
                                return
                            }
                            _state.update {
                                it.copy(playlist = r.playlist)
                            }
                            r.playlist.items.first()
                        }
                    }
                }

            viewModelScope.launch(ExceptionHandler()) { controllerViewState.observe() }

            val intros =
                // If not resuming playback & cinema mode is enabled, get potential intros
                if (positionMs == 0L && preferences.appPreferences.playbackPreferences.cinemaMode) {
                    api.userLibraryApi
                        .getIntros(
                            itemId = playlistItem.id,
                            userId = serverRepository.currentUser?.id,
                        ).content.items
                        .map {
                            PlaylistItem.Intro(BaseItem(it))
                        }
                } else {
                    emptyList()
                }
            val firstItem =
                if (intros.isNotEmpty()) {
                    Timber.v("Got %s intros", intros.size)
                    _state.update {
                        it.copy(playlist = Playlist(intros + it.playlist.items))
                    }
                    intros.first()
                } else {
                    playlistItem
                }

            val played =
                play(
                    firstItem,
                    positionMs,
                    forceTranscoding,
                )
            if (!played) {
                playNextUp()
            }

            if (!isPlaylist) {
                val result = playlistCreator.createFrom(queriedItem)
                if (result is PlaylistCreationResult.Success && result.playlist.items.isNotEmpty()) {
                    _state.update {
                        it.copy(
                            playlist = Playlist(it.playlist.items + result.playlist.items),
                        )
                    }
                }
            }
        }

        internal fun updateCurrentPlayback(block: (CurrentPlayback?) -> CurrentPlayback?) {
            _state.update {
                it.copy(currentPlayback = block.invoke(it.currentPlayback))
            }
        }

        /**
         * Play an item
         *
         * @param currentItem the item to play
         * @param positionMs the starting playback position in milliseconds
         * @param itemPlayback the parameters for playback such chosen subtitle or audio streams
         * @param forceTranscoding whether the user has requested to force playback via transcoding
         */
        private suspend fun play(
            playlistItem: PlaylistItem,
            positionMs: Long,
            forceTranscoding: Boolean = this.forceTranscoding,
        ): Boolean =
            withContext(WholphinDispatchers.IO) {
                val item =
                    when (playlistItem) {
                        is PlaylistItem.Intro -> playlistItem.item
                        is PlaylistItem.Media -> playlistItem.item
                    }

                Timber.i("Playing ${item.id}")

                // New item, so we can clear the media segment tracker & subtitle cues
                resetSegmentState()
                stopLocalSubtitle()
                _state.update { it.copy(subtitleCues = emptyList()) }

                viewModelScope.launchIO {
                    // Starting playback, so want to invalidate the last played timestamp for this item
                    datePlayedService.invalidate(item)
                }

                if (item.type !in supportItemKinds) {
                    showToast(
                        context,
                        "Unsupported type '${item.type}', skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }
                this@PlaybackViewModel.currentItem = playlistItem
                this@PlaybackViewModel.itemId = item.id
                // The progress of the previous item must not count for this one
                lastPositionMs = C.TIME_UNSET
                lastDurationMs = C.TIME_UNSET
                if (playlistItem is PlaylistItem.Media) {
                    claudeCompanionService.onPlaybackStarted(item.id, item.type)
                }

                val isLiveTv = item.type == BaseItemKind.TV_CHANNEL
                val base = item.data

                // Use the provided playback parameters or else check if the database has some
                val itemPlayback =
                    serverRepository.currentUser?.let { user ->
                        itemPlaybackDao.getItem(user, base.id)?.let {
                            Timber.v("Fetched itemPlayback from DB: %s", it)
                            if (it.sourceId != null) {
                                it
                            } else {
                                null
                            }
                        }
                    }
                val mediaSource =
                    if (!isLiveTv) {
                        streamChoiceService.chooseSource(base, itemPlayback)
                    } else {
                        null
                    }

                val plc = streamChoiceService.getPlaybackLanguageChoice(base)

                if (mediaSource == null && !isLiveTv) {
                    showToast(
                        context,
                        "Item has no media sources, skipping...",
                        Toast.LENGTH_SHORT,
                    )
                    return@withContext false
                }

                val videoStream =
                    mediaSource
                        ?.mediaStreams
                        ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                        ?.let {
                            val isHdr =
                                it.videoRange == VideoRange.HDR ||
                                    (it.videoRangeType != VideoRangeType.SDR && it.videoRangeType != VideoRangeType.UNKNOWN)
                            // Often times 4k movies have a wider aspect ratio so the height is lower even though the width is still 3840
                            val is4k = (it.width ?: 0) > 2560 || (it.height ?: 0) > 1440
                            SimpleVideoStream(it.index, isHdr, is4k)
                        }

                // Create the correct player for the media
                createPlayer(videoStream?.hdr == true, videoStream?.is4k == true)

                val subtitleStreams = mediaSource?.let { getSubtitleStreams(mediaSource) }.orEmpty()
                val audioStreams = mediaSource?.let { getAudioStreams(mediaSource) }.orEmpty()
                val audioStream =
                    mediaSource?.let {
                        streamChoiceService
                            .chooseAudioStream(
                                source = mediaSource,
                                seriesId = base.seriesId,
                                itemPlayback = itemPlayback,
                                plc = plc,
                                prefs = preferences,
                            )
                    }
                val audioIndex = audioStream?.index

                val defaultSubtitleIndex =
                    mediaSource?.let {
                        streamChoiceService
                            .chooseSubtitleStream(
                                source = mediaSource,
                                audioStream = audioStream,
                                seriesId = base.seriesId,
                                itemPlayback = itemPlayback,
                                plc = plc,
                                prefs = preferences,
                            )?.index
                    }
                val subtitleIndex =
                    if (mediaSource != null) {
                        applyAutoForeignSubtitles(mediaSource, itemPlayback, plc, defaultSubtitleIndex)
                    } else {
                        defaultSubtitleIndex
                    }
                Timber.d(
                    "Selected mediaSource=%s, audioIndex=%s, subtitleIndex=%s",
                    mediaSource?.id,
                    audioIndex,
                    subtitleIndex,
                )

                val trickPlayInfo =
                    mediaSource?.id?.let { sourceId ->
                        item.data.trickplay
                            ?.get(sourceId)
                            ?.values
                            ?.firstOrNull()
                    }

                trickPlayInfo?.let { trickplayInfo ->
                    mediaSource.runTimeTicks?.ticks?.let { duration ->
                        viewModelScope.launchIO {
                            prefetchTrickplay(
                                duration,
                                trickplayInfo,
                                mediaSource.id?.toUUIDOrNull(),
                            )
                        }
                    }
                }

                val chapters = Chapter.fromDto(base)
                _state.update {
                    it.copy(
                        currentItemPlayback = itemPlayback,
                        currentPlayback = null,
                    )
                }
                updateCurrentMedia {
                    CurrentMediaInfo(
                        sourceId = mediaSource?.id,
                        videoStream = videoStream,
                        audioStreams = audioStreams,
                        subtitleStreams = subtitleStreams,
                        chapters = chapters,
                        trickPlayInfo = trickPlayInfo,
                    )
                }
                startRequestedAtMs = System.currentTimeMillis()
                withContext(WholphinDispatchers.Main) {
                    changeStreams(
                        item = item,
                        audioIndex = audioIndex,
                        subtitleIndex = subtitleIndex,
                        positionMs = if (positionMs > 0) positionMs else C.TIME_UNSET,
                        sourceId = mediaSource?.id,
                        enableDirectPlay = !forceTranscoding,
                        enableDirectStream = !forceTranscoding,
                        deferExtractedSubtitle = true,
                    )
                    player.prepare()
                    player.play()
                }
                listenForSegments(item.id)
                return@withContext true
            }

        /**
         * Automatic subtitles for foreign language audio (#46), only applied when an item starts playing.
         *
         * An explicit choice by the user (for the item or the series) always wins. Track changes, seeking
         * and source changes go through [changeStreams], so this never overrides them.
         */
        private fun applyAutoForeignSubtitles(
            mediaSource: MediaSourceInfo,
            itemPlayback: ItemPlayback?,
            plc: PlaybackLanguageChoice?,
            defaultSubtitleIndex: Int?,
        ): Int? {
            val userChoseSubtitles =
                (itemPlayback != null && itemPlayback.subtitleIndex != TrackIndex.UNSPECIFIED) ||
                    plc?.subtitleLanguage.isNotNullOrBlank() ||
                    plc?.subtitlesDisabled == true
            if (userChoseSubtitles) {
                Timber.i(
                    "Auto foreign subtitles: skipped, user chose subtitles (itemPlayback=%s, plc=%s)",
                    itemPlayback?.subtitleIndex,
                    plc?.subtitleLanguage ?: plc?.subtitlesDisabled,
                )
                return defaultSubtitleIndex
            }
            val enabled = !preferences.appPreferences.playbackPreferences.autoForeignSubtitlesDisabled
            val streams = mediaSource.mediaStreams.orEmpty()
            val choice =
                AutoForeignSubtitles.choose(
                    audioStreams = streams.filter { it.type == MediaStreamType.AUDIO },
                    subtitleStreams = streams.filter { it.type == MediaStreamType.SUBTITLE },
                    enabled = enabled,
                )
            return if (choice != null) {
                Timber.i(
                    "Auto foreign subtitles: chose subtitle index=%s lang=%s title=%s, because %s",
                    choice.stream.index,
                    choice.stream.language,
                    choice.stream.title ?: choice.stream.displayTitle,
                    choice.reason,
                )
                choice.stream.index
            } else {
                Timber.i(
                    "Auto foreign subtitles: no change (enabled=%s), keeping subtitleIndex=%s",
                    enabled,
                    defaultSubtitleIndex,
                )
                defaultSubtitleIndex
            }
        }

        /**
         * Turns on an embedded subtitle that was held back at start (see [changeStreams]).
         *
         * The server reads the whole file from the NAS to extract it, which starves a transcode that is just
         * starting (#56: the film played ~5 s and then stalled ~30 s). So the subtitle is asked for only once
         * playback runs with a good buffer, it is saved locally and, with ExoPlayer, added to the current media
         * item without a new playback info / transcode job (same stream URL, position kept).
         */
        private suspend fun enableSubtitleWhenReady(
            item: BaseItem,
            stream: MediaStream,
            deliveryUrl: String,
        ) {
            val subtitleIndex = stream.index
            val waitStart = System.currentTimeMillis()
            if (!awaitPlaybackBuffered(item.id)) {
                Timber.i("Deferred subtitle %s: playback moved on, not enabling", subtitleIndex)
                return
            }
            Timber.i(
                "Deferred subtitle %s: playback buffered after %d ms, fetching it",
                subtitleIndex,
                System.currentTimeMillis() - waitStart,
            )
            val fetchStart = System.currentTimeMillis()
            val file =
                try {
                    authOkHttpClient
                        .newCall(Request.Builder().url(api.createUrl(deliveryUrl)).build())
                        .execute()
                        .use { response ->
                            if (!response.isSuccessful) {
                                Timber.w("Deferred subtitle %s: HTTP %s", subtitleIndex, response.code)
                                null
                            } else {
                                val dir = File(context.cacheDir, "deferred-subtitles").apply { mkdirs() }
                                // Only the latest one is needed
                                dir.listFiles()?.forEach { it.delete() }
                                val ext = stream.codec?.lowercase()?.takeIf { it.all(Char::isLetterOrDigit) } ?: "srt"
                                File(dir, "${item.id}-$subtitleIndex.$ext").also { out ->
                                    response.body.byteStream().use { input ->
                                        out.outputStream().use { input.copyTo(it) }
                                    }
                                }
                            }
                        }
                } catch (ex: Exception) {
                    Timber.w(ex, "Deferred subtitle %s: fetching failed", subtitleIndex)
                    null
                }
            Timber.i(
                "Deferred subtitle %s: fetched in %d ms (%s bytes)",
                subtitleIndex,
                System.currentTimeMillis() - fetchStart,
                file?.length(),
            )
            val current = state.value.currentPlayback
            // Skip if playback moved on or the user picked subtitles meanwhile
            if (current?.item?.id != item.id || current.subtitleIndex != TrackIndex.DISABLED) return
            _state.update { it.copy(currentPlayback = it.currentPlayback?.copy(subtitleIndex = subtitleIndex)) }
            // Text subtitles are shown next to the player without touching it: replacing the media item
            // re-prepares the whole stream, which stalled the film for a few seconds (#57)
            val cues =
                file?.takeIf { current.backend == PlayerBackend.EXO_PLAYER }?.let { parseTextSubtitle(it, deliveryUrl, stream.codec) }
            if (cues != null) {
                Timber.i("Deferred subtitle %s: showing %d cues without reloading", subtitleIndex, cues.size)
                showLocalSubtitle(item.id, subtitleIndex, cues)
                viewModelScope.launchIO { loadSubtitleDelay() }
                return
            }
            val addedInPlace =
                file != null &&
                    current.backend == PlayerBackend.EXO_PLAYER &&
                    onMain { addSubtitleInPlace(stream, file, current.mediaSourceInfo) }
            if (!addedInPlace) {
                Timber.i("Deferred subtitle %s: enabling via new playback info", subtitleIndex)
                changeStreams(
                    item = item,
                    sourceId = current.mediaSourceInfo.id,
                    audioIndex = current.audioIndex,
                    subtitleIndex = subtitleIndex,
                    positionMs = onMain { player.currentPosition },
                    enableDirectPlay = true,
                )
            }
        }

        /**
         * Parses a saved text subtitle (SubRip or WebVTT, by the delivery URL's extension or the codec).
         * Returns null for other formats or if parsing fails, so the caller falls back to the player.
         */
        @OptIn(UnstableApi::class)
        private fun parseTextSubtitle(
            file: File,
            deliveryUrl: String,
            codec: String?,
        ): List<CuesWithTiming>? {
            val format =
                deliveryUrl
                    .substringBefore('?')
                    .substringAfterLast('/')
                    .substringAfterLast('.', "")
                    .lowercase()
                    .ifBlank { codec?.lowercase().orEmpty() }
            val parser =
                when (format) {
                    "srt", "subrip" -> SubripParser()
                    "vtt", "webvtt" -> WebvttParser()
                    else -> return null
                }
            return try {
                val result = mutableListOf<CuesWithTiming>()
                parser.parse(file.readBytes(), SubtitleParser.OutputOptions.allCues()) { result.add(it) }
                result.sortedBy { it.startTimeUs }.takeIf { it.isNotEmpty() }
            } catch (ex: Exception) {
                Timber.w(ex, "Deferred subtitle: parsing %s failed", format)
                null
            }
        }

        private var localSubtitleJob: Job? = null

        /**
         * Shows [cues] in sync with the player's position (minus the subtitle delay) until another item plays
         * or the user picks different subtitles. The player itself keeps playing undisturbed.
         */
        @OptIn(UnstableApi::class)
        private fun showLocalSubtitle(
            itemId: UUID,
            subtitleIndex: Int,
            cues: List<CuesWithTiming>,
        ) {
            stopLocalSubtitle()
            localSubtitleJob =
                viewModelScope.launch(WholphinDispatchers.Main) {
                    // Only these cues are shown; the player's own captions would fight with them
                    disablePlayerText()
                    var shown: List<Cue> = emptyList()
                    try {
                        while (isActive) {
                            val current = state.value.currentPlayback
                            if (current?.item?.id != itemId || current.subtitleIndex != subtitleIndex) break
                            val positionUs = (player.currentPosition - current.subtitleDelay.inWholeMilliseconds) * 1000
                            val active =
                                cues
                                    .asSequence()
                                    .takeWhile { it.startTimeUs <= positionUs }
                                    .filter { it.endTimeUs == C.TIME_UNSET || positionUs < it.endTimeUs }
                                    .flatMap { it.cues }
                                    .toList()
                            // The player reports its own (empty) cues on seeks, so compare with what's on screen
                            if (active != shown || state.value.subtitleCues != active) {
                                shown = active
                                _state.update { it.copy(subtitleCues = active) }
                            }
                            delay(100)
                        }
                    } finally {
                        if (shown.isNotEmpty()) _state.update { it.copy(subtitleCues = emptyList()) }
                    }
                }
        }

        /** Turns off ExoPlayer's own text tracks (closed captions, subtitles in the stream) */
        private fun disablePlayerText() {
            player.trackSelectionParameters =
                player.trackSelectionParameters
                    .buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                    .build()
        }

        private fun stopLocalSubtitle() {
            localSubtitleJob?.cancel()
            localSubtitleJob = null
        }

        /**
         * Waits until the item plays with enough buffered ahead (or long enough after it got ready).
         * Returns false if another item is playing meanwhile.
         */
        private suspend fun awaitPlaybackBuffered(itemId: UUID): Boolean {
            val start = System.currentTimeMillis()
            var readySince: Long? = null
            while (System.currentTimeMillis() - start < DEFERRED_SUBTITLE_MAX_WAIT_MS) {
                val (sameItem, ready, bufferedMs) =
                    onMain {
                        Triple(
                            state.value.currentPlayback
                                ?.item
                                ?.id == itemId,
                            player.playbackState == Player.STATE_READY,
                            player.totalBufferedDuration,
                        )
                    }
                if (!sameItem) return false
                if (ready) {
                    val since = readySince ?: System.currentTimeMillis().also { readySince = it }
                    if (bufferedMs >= DEFERRED_SUBTITLE_MIN_BUFFER_MS ||
                        System.currentTimeMillis() - since >= DEFERRED_SUBTITLE_AFTER_READY_MS
                    ) {
                        Timber.d("Deferred subtitle: buffered %d ms", bufferedMs)
                        return true
                    }
                }
                delay(500)
            }
            return state.value.currentPlayback
                ?.item
                ?.id == itemId
        }

        /**
         * Adds a locally saved [file] for [stream] to the current media item, keeping the stream URL (so the
         * server's transcode keeps going) and the position. Returns false if there is nothing to add it to.
         */
        private fun addSubtitleInPlace(
            stream: MediaStream,
            file: File,
            source: MediaSourceInfo,
        ): Boolean {
            val mediaItem = player.currentMediaItem ?: return false
            var flags = 0
            if (stream.isForced) flags = flags.or(C.SELECTION_FLAG_FORCED)
            if (stream.isDefault) flags = flags.or(C.SELECTION_FLAG_DEFAULT)
            val config =
                MediaItem.SubtitleConfiguration
                    .Builder(file.toUri())
                    .setId("e:${stream.index}")
                    .setMimeType(subtitleMimeTypes[stream.codec])
                    .setLanguage(stream.language)
                    .setLabel(stream.title)
                    .setSelectionFlags(flags)
                    .build()
            val updated =
                mediaItem
                    .buildUpon()
                    .setSubtitleConfigurations(listOf(config))
                    .build()
            trackChangeListener?.let { player.removeListener(it) }
            val listener =
                TracksChangedListener(
                    player = player,
                    audioIndex = null,
                    subtitleIndex = stream.index,
                    source = source,
                    onFailure = { Timber.w("Deferred subtitle %s: could not select the track", stream.index) },
                )
            player.addListener(listener)
            trackChangeListener = listener
            val position = player.currentPosition
            Timber.i("Deferred subtitle %s: adding in place at %d ms", stream.index, position)
            player.setMediaItem(updated, position)
            viewModelScope.launchIO { loadSubtitleDelay() }
            return true
        }

        /**
         * Change which streams (ie audio or subtitle) are active
         */
        @OptIn(UnstableApi::class)
        internal suspend fun changeStreams(
            item: BaseItem,
            sourceId: String?,
            audioIndex: Int?,
            subtitleIndex: Int?,
            positionMs: Long = 0,
            enableDirectPlay: Boolean = !this.forceTranscoding,
            enableDirectStream: Boolean = !this.forceTranscoding,
            deferExtractedSubtitle: Boolean = false,
        ): Unit =
            withContext(WholphinDispatchers.IO) {
                val itemId = item.id

                stopLocalSubtitle()
                trackChangeListener?.let { onMain { player.removeListener(it) } }
                trackChangeListener = null

                state.value.currentPlayback?.let { currentPlayback ->
                    if (currentPlayback.item.id == item.id &&
                        currentPlayback.playMethod == PlayMethod.DIRECT_PLAY &&
                        enableDirectPlay
                    ) {
                        val wasSuccessful =
                            changeStreamsDirectPlay(
                                currentPlayback = currentPlayback,
                                audioIndex = audioIndex,
                                subtitleIndex = subtitleIndex,
                            )
                        if (wasSuccessful) return@withContext
                    }
                }

                Timber.i(
                    "changeStreams (%s): audioIndex=%s, subtitleIndex=%s, enableDirectPlay=%s, enableDirectStream=%s, positionMs=%s",
                    itemId,
                    audioIndex,
                    subtitleIndex,
                    enableDirectPlay,
                    enableDirectStream,
                    positionMs,
                )

                val maxBitrate =
                    preferences.appPreferences.playbackPreferences.maxBitrate
                        .takeIf { it > 0 } ?: AppPreference.DEFAULT_BITRATE
                val response by
                    api.mediaInfoApi
                        .getPostedPlaybackInfo(
                            itemId,
                            PlaybackInfoDto(
                                startTimeTicks = null,
                                deviceProfile =
                                    if (currentPlayer.value!!.backend == PlayerBackend.EXO_PLAYER) {
                                        deviceProfileService.getOrCreateDeviceProfile(
                                            preferences.appPreferences,
                                            serverRepository.currentServer?.serverVersion,
                                        )
                                    } else {
                                        mpvDeviceProfile
                                    },
                                maxAudioChannels = null,
                                audioStreamIndex = audioIndex,
                                subtitleStreamIndex = subtitleIndex,
                                mediaSourceId = sourceId,
                                alwaysBurnInSubtitleWhenTranscoding = false,
                                maxStreamingBitrate = maxBitrate.toInt(),
                                enableDirectPlay = enableDirectPlay,
                                enableDirectStream = enableDirectStream,
                                allowVideoStreamCopy = enableDirectStream,
                                allowAudioStreamCopy = enableDirectStream,
                                enableTranscoding = true,
                                autoOpenLiveStream = true,
                            ),
                        )
                if (response.errorCode != null) {
                    Timber.e("Error in PostedPlaybackInfo: %s", response.errorCode)
                    _state.update { it.copy(loading = LoadingState.Error(response.errorCode?.serialName)) }
                    return@withContext
                }
                val source = response.mediaSources.firstOrNull()
                source?.let { source ->
                    val mediaUrl =
                        if (source.supportsDirectPlay) {
                            if (source.isRemote && source.path.isNotNullOrBlank()) {
                                Timber.i("Playback is remote for source: %s", source.id)
                                source.path
                            } else {
                                api.videosApi.getVideoStreamUrl(
                                    itemId = itemId,
                                    mediaSourceId = source.id,
                                    static = true,
                                    tag = source.eTag,
                                    playSessionId = response.playSessionId,
                                )
                            }
                        } else if (source.supportsDirectStream) {
                            source.transcodingUrl?.let(api::createUrl)
                        } else {
                            source.transcodingUrl?.let(api::createUrl)
                        }
                    if (mediaUrl.isNullOrBlank()) {
                        _state.update {
                            it.copy(
                                loading =
                                    LoadingState.Error(
                                        "Unable to get media URL from the server. Do you have permission to view and/or transcode?",
                                    ),
                            )
                        }
                        return@withContext
                    }
                    val transcodeType =
                        when {
//                        playerBackend == PlayerBackend.MPV -> PlayMethod.DIRECT_PLAY
                            source.supportsDirectPlay -> PlayMethod.DIRECT_PLAY

                            source.supportsDirectStream -> PlayMethod.DIRECT_STREAM

                            source.supportsTranscoding -> PlayMethod.TRANSCODE

                            else -> throw Exception("No supported playback method")
                        }
                    Timber.i("Playback decision for $itemId: $transcodeType")
                    PlaybackEvents.add(
                        "${item.title}: $transcodeType, audio=$audioIndex, sub=$subtitleIndex, from=${positionMs / 1000}s, " +
                            "audio codecs=" +
                            source.mediaStreams
                                .orEmpty()
                                .filter { it.type == MediaStreamType.AUDIO }
                                .joinToString { "${it.index}:${it.codec}/${it.profile}/${it.channels}ch" },
                    )

                    // An embedded subtitle the server first has to extract from the whole file would
                    // block the start, so start without it and turn it on once it's ready
                    val deferredSubtitle =
                        source
                            .findExternalSubtitle(subtitleIndex)
                            ?.takeIf { deferExtractedSubtitle && !it.isExternal && it.deliveryUrl != null }
                    val subtitleIndex = if (deferredSubtitle != null) null else subtitleIndex

                    val externalSubtitleCount = source.externalSubtitlesCount

                    val externalSubtitle =
                        source.findExternalSubtitle(subtitleIndex)?.let {
                            Timber.v("Using externally delivered subtitle tracks %s", subtitleIndex)
                            it.deliveryUrl?.let { deliveryUrl ->
                                var flags = 0
                                if (it.isForced) flags = flags.or(C.SELECTION_FLAG_FORCED)
                                if (it.isDefault) flags = flags.or(C.SELECTION_FLAG_DEFAULT)
                                MediaItem.SubtitleConfiguration
                                    .Builder(
                                        api.createUrl(deliveryUrl).toUri(),
                                    ).setId("e:${it.index}")
                                    .setMimeType(subtitleMimeTypes[it.codec])
                                    .setLanguage(it.language)
                                    .setLabel(it.title)
                                    .setSelectionFlags(flags)
                                    .build()
                            }
                        }

                    Timber.v(
                        "subtitleIndex=$subtitleIndex, externalSubtitleCount=$externalSubtitleCount, externalSubtitle=$externalSubtitle",
                    )

                    val mediaItem =
                        MediaItem
                            .Builder()
                            .setMediaId(itemId.toString())
                            .setMediaMetadata(
                                item.toMediaMetadata(
                                    imageUrlService.getItemImageUrl(
                                        item,
                                        ImageType.PRIMARY,
                                        useSeriesForPrimary = true,
                                    ),
                                ),
                            ).setUri(mediaUrl.toUri())
                            .setSubtitleConfigurations(listOfNotNull(externalSubtitle))
                            .apply {
                                when (source.container) {
                                    Codec.Container.HLS -> setMimeType(MimeTypes.APPLICATION_M3U8)
                                    Codec.Container.DASH -> setMimeType(MimeTypes.APPLICATION_MPD)
                                }
                            }.build()

                    val playback =
                        CurrentPlayback(
                            item = item,
                            tracks = listOf(),
                            backend = currentPlayer.value!!.backend,
                            playMethod = transcodeType,
                            playSessionId = response.playSessionId,
                            liveStreamId = source.liveStreamId,
                            mediaSourceInfo = source,
                            audioIndex = audioIndex ?: TrackIndex.DISABLED,
                            subtitleIndex = subtitleIndex ?: TrackIndex.DISABLED,
                        )

                    preferences.appPreferences.playbackPreferences.let { prefs ->
                        source.mediaStreams
                            ?.firstOrNull { it.type == MediaStreamType.VIDEO }
                            ?.let { stream ->
                                refreshRateService.changeRefreshRate(
                                    stream = stream,
                                    switchRefreshRate = prefs.refreshRateSwitching,
                                    switchResolution = prefs.resolutionSwitching,
                                )
                            }
                    }
                    withContext(WholphinDispatchers.Main) {
                        // TODO, don't need to release & recreate when switching streams
                        this@PlaybackViewModel.activityListener?.let {
                            it.release()
                            player.removeListener(it)
                        }

                        val playbackItemState = PlaybackItemState(playback)
                        val activityListener =
                            TrackActivityPlaybackListener(
                                api = api,
                                player = player,
                                getState = { playbackItemState },
                            )
                        player.addListener(activityListener)
                        this@PlaybackViewModel.activityListener = activityListener

                        _state.update {
                            it.copy(
                                loading = LoadingState.Success,
                                currentPlayback = playback,
                            )
                        }
                        if (deferredSubtitle != null && currentPlayer.value?.backend == PlayerBackend.EXO_PLAYER) {
                            // Until the deferred subtitle is on, ExoPlayer must not pick a text track by itself,
                            // e.g. English closed captions carried in the H264 stream (#60)
                            disablePlayerText()
                        }
                        player.setMediaItem(
                            mediaItem,
                            positionMs,
                        )
                        val onFailure: () -> Unit = {
                            if (player is MpvPlayer && externalSubtitle != null) {
                                // MpvPlayer may change tracks more than once to add external subtitles
                                trackChangeListener?.let { player.addListener(it) }
                            } else {
                                viewModelScope.launchIO {
                                    changeStreams(
                                        item = item,
                                        sourceId = sourceId,
                                        audioIndex = audioIndex,
                                        subtitleIndex = subtitleIndex,
                                        positionMs = positionMs,
                                        enableDirectPlay = false,
                                        enableDirectStream = true,
                                    )
                                }
                            }
                        }
                        val onTracksChangedListener =
                            if (transcodeType == PlayMethod.DIRECT_PLAY && (audioIndex != null || subtitleIndex != null)) {
                                TracksChangedListener(
                                    player = player,
                                    audioIndex = audioIndex,
                                    subtitleIndex = subtitleIndex,
                                    source = source,
                                    onFailure = onFailure,
                                )
                            } else if (externalSubtitle != null) {
                                TracksChangedListener(
                                    player = player,
                                    audioIndex = null, // Do not manually activate audio
                                    subtitleIndex = subtitleIndex,
                                    source = source,
                                    onFailure = onFailure,
                                )
                            } else {
                                null
                            }
                        onTracksChangedListener?.let {
                            player.addListener(it)
                            this@PlaybackViewModel.trackChangeListener = it
                        }

                        if (subtitleIndex != null) {
                            viewModelScope.launchIO { loadSubtitleDelay() }
                        }
                        deferredSubtitle?.let {
                            viewModelScope.launchIO { enableSubtitleWhenReady(item, it, it.deliveryUrl!!) }
                        }
                    }
                }
            }

        /**
         * If direct playing, can try to switch tracks without playback restarting
         * Except for external subtitles
         */
        @OptIn(UnstableApi::class)
        private suspend fun changeStreamsDirectPlay(
            currentPlayback: CurrentPlayback,
            audioIndex: Int?,
            subtitleIndex: Int?,
        ): Boolean =
            withContext(WholphinDispatchers.IO) {
                Timber.v("changeStreams direct play")

                // TODO Better way to handle unsupported types in general is needed
                // This is a workaround for switching to a non AC3 track when the user wants audio transcoded to AC3
                if (preferences.appPreferences.experimentalPreferences.enabled { preferAc3Surround } && audioIndex != null) {
                    currentPlayback.mediaSourceInfo.mediaStreams
                        .orEmpty()
                        .firstOrNull { it.index == audioIndex }
                        ?.let {
                            if (it.channels.gt(2) && it.codec != Codec.Audio.AC3) {
                                // User wants to transcode audio into AC3
                                return@withContext false
                            }
                        }
                }

                val source = currentPlayback.mediaSourceInfo
                val externalSubtitle = source.findExternalSubtitle(subtitleIndex)

                if (externalSubtitle == null) {
                    val result =
                        withContext(WholphinDispatchers.Main) {
                            TrackSelectionUtils.createTrackSelections(
                                onMain { player.trackSelectionParameters },
                                onMain { player.currentTracks },
                                audioIndex,
                                subtitleIndex,
                                source,
                            )
                        }
                    if (result.bothSelected) {
                        onMain { player.trackSelectionParameters = result.trackSelectionParameters }
                        Timber.d("Changes tracks audio=$audioIndex, subtitle=$subtitleIndex")
                        _state.update {
                            it.copy(
                                currentPlayback =
                                    (it.currentPlayback ?: currentPlayback).copy(
                                        tracks = checkForSupport(onMain { player.currentTracks }),
                                    ),
                            )
                        }
                        loadSubtitleDelay()
                        return@withContext true
                    }
                } else {
                    Timber.v("changeStreams direct play, external subtitle was requested")
                }
                return@withContext false
            }

        fun changeAudioStream(index: Int) {
            viewModelScope.launchIO {
                val currentPlayback = state.value.currentPlayback
                if (currentPlayback != null) {
                    Timber.d("Changing audio track to %s", index)
                    saveTrackSelection(index, MediaStreamType.AUDIO)
                    _state.update {
                        it.copy(
                            currentPlayback = it.currentPlayback?.copy(audioIndex = index),
                        )
                    }

                    // Resolve ONLY_FORCED to actual track based on new audio language
                    val resolvedSubtitleIndex =
                        streamChoiceService.resolveSubtitleIndex(
                            source = currentPlayback.mediaSourceInfo,
                            audioStreamIndex = index,
                            seriesId = currentItem.item.data.seriesId,
                            subtitleIndex = currentPlayback.subtitleIndex,
                            prefs = preferences,
                        )

                    changeStreams(
                        item = currentItem.item,
                        sourceId = currentPlayback.mediaSourceInfo.id,
                        audioIndex = index,
                        subtitleIndex = resolvedSubtitleIndex,
                        positionMs = onMain { player.currentPosition },
                        enableDirectPlay = true,
                    )
                } else {
                    Timber.w("Trying to change audio, but currentPlayback is null")
                }
            }
        }

        fun changeSubtitleStream(index: Int): Job =
            viewModelScope.launchIO {
                val currentPlayback = state.value.currentPlayback
                if (currentPlayback != null) {
                    Timber.d("Changing subtitle track to %s", index)
                    saveTrackSelection(index, MediaStreamType.SUBTITLE)
                    _state.update {
                        it.copy(
                            currentPlayback = it.currentPlayback?.copy(subtitleIndex = index),
                        )
                    }

                    // Resolve ONLY_FORCED to actual track index for playback
                    val resolvedIndex =
                        streamChoiceService.resolveSubtitleIndex(
                            source = currentPlayback.mediaSourceInfo,
                            audioStreamIndex = currentPlayback.audioIndex,
                            seriesId = currentItem.item.data.seriesId,
                            subtitleIndex = index,
                            prefs = preferences,
                        )

                    changeStreams(
                        item = currentItem.item,
                        sourceId = currentPlayback.mediaSourceInfo.id,
                        audioIndex = currentPlayback.audioIndex,
                        subtitleIndex = resolvedIndex,
                        positionMs = onMain { player.currentPosition },
                        enableDirectPlay = true,
                    )
                } else {
                    Timber.w("Trying to change subtitle, but currentPlayback is null")
                }
            }

        internal suspend fun saveTrackSelection(
            trackIndex: Int,
            type: MediaStreamType,
        ) {
            val itemPlayback =
                itemPlaybackRepository.saveTrackSelection(
                    item = currentItem.item,
                    itemPlayback = state.value.currentItemPlayback,
                    trackIndex = trackIndex,
                    type = type,
                )
            _state.update {
                it.copy(
                    currentItemPlayback = itemPlayback,
                )
            }
        }

        private suspend fun prefetchTrickplay(
            duration: Duration,
            trickplayInfo: TrickplayInfo,
            mediaSourceId: UUID?,
        ) {
            val tilesPerImage = trickplayInfo.tileWidth * trickplayInfo.tileHeight
            val totalCount =
                (duration.inWholeMilliseconds / trickplayInfo.interval).toInt() / tilesPerImage + 1
            (0..<totalCount).forEach {
                val url = getTrickplayUrl(it, trickplayInfo, mediaSourceId)
                context.imageLoader.enqueue(
                    ImageRequest
                        .Builder(context)
                        .data(url)
                        .size(Size.ORIGINAL)
                        .build(),
                )
            }
        }

        fun getTrickplayUrl(
            index: Int,
            trickPlayInfo: TrickplayInfo? = state.value.currentMediaInfo.trickPlayInfo,
            mediaSourceId: UUID? =
                state.value.currentPlayback
                    ?.mediaSourceInfo
                    ?.id
                    ?.toUUIDOrNull(),
        ): String? =
            trickPlayInfo?.let {
                val itemId = currentItem.id
                return api.trickplayApi.getTrickplayTileImageUrl(
                    itemId,
                    trickPlayInfo.width,
                    index,
                    mediaSourceId,
                )
            }

        /** Keeps the screen on while playing or about to play, a pause or the end lets the screensaver start */
        private fun updateKeepScreenOn() {
            val p = player
            val playing =
                p.playWhenReady && p.playbackState != Player.STATE_ENDED && p.playbackState != Player.STATE_IDLE
            if (playing) {
                screensaverService.acquireKeepScreenOn(this)
            } else {
                screensaverService.releaseKeepScreenOn(this)
            }
        }

        override fun onPlayWhenReadyChanged(
            playWhenReady: Boolean,
            reason: Int,
        ) {
            updateKeepScreenOn()
            claudeCompanionService.onPlayingChanged(playWhenReady)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            updateKeepScreenOn()
            // Progress for the rating prompt; a loop is avoided so tests (Robolectric main looper) never spin forever
            runCatching { rememberProgress(player) }
            if (player.isPlaying) screensaverService.reportPlaybackActive(this)
            if (playbackState == Player.STATE_READY) {
                startRequestedAtMs?.let {
                    Timber.i("Playback ready %d ms after start", System.currentTimeMillis() - it)
                    PlaybackEvents.add("ready after ${System.currentTimeMillis() - it} ms")
                    startRequestedAtMs = null
                }
            } else if (playbackState == Player.STATE_BUFFERING && startRequestedAtMs == null) {
                Timber.i("Playback buffering at %d ms", player.currentPosition)
                PlaybackEvents.add("buffering at ${player.currentPosition / 1000}s")
            }
            if (playbackState == Player.STATE_ENDED) {
                Timber.v("Playback state is STATE_ENDED")
                viewModelScope.launchDefault {
                    when (val nextItem = state.value.nextItem()) {
                        is PlaylistItem.Intro -> {
                            Timber.v("Next item is intro, so playing immediately")
                            playNextUp()
                        }

                        is PlaylistItem.Media -> {
                            val prefs = preferences.appPreferences.playbackPreferences
                            when {
                                currentItem is PlaylistItem.Intro -> {
                                    Timber.v("Current item is intro, so playing next up immediately")
                                    playNextUp()
                                }

                                prefs.showNextUpWhen == ShowNextUpWhen.NEXT_UP_NEVER && !prefs.autoPlayNext -> {
                                    Timber.v("Never show or auto play next up, returning")
                                    navigationManager.goBack()
                                }

                                prefs.showNextUpWhen != ShowNextUpWhen.NEXT_UP_NEVER -> {
                                    Timber.v("Setting next up to ${nextItem.id}")
                                    _state.update { it.copy(nextUp = nextItem.item) }
                                }

                                else -> {
                                    controllerViewState.showControls()
                                }
                            }
                        }

                        null -> {
                            Timber.v("No next up")
                            navigationManager.goBack()
                        }
                    }
                }
            }
        }

        // Variables for tracking segment state
        // Exposed for testing
        internal var segmentJob: Job? = null
        private val autoSkippedSegments = mutableSetOf<UUID>()
        private val outroShownSegments = mutableSetOf<UUID>()

        /**
         * Cancels listening for segments and clears current segment state
         */
        private fun resetSegmentState() {
            segmentJob?.cancel()
            autoSkippedSegments.clear()
            outroShownSegments.clear()
            _state.update { it.copy(currentSegment = null) }
        }

        /**
         * This sets up a coroutine to periodically check whether the current playback progress is within a media segment (intro, outro, etc)
         */
        private fun listenForSegments(itemId: UUID) {
            segmentJob?.cancel()
            segmentJob =
                viewModelScope.launchIO {
                    val prefs = preferences.appPreferences.playbackPreferences
                    val segments by api.mediaSegmentsApi.getItemSegments(itemId)
                    if (segments.items.isNotEmpty()) {
                        while (isActive) {
                            delay(500L)
                            val currentTicks =
                                onMain { player.currentPosition.milliseconds.inWholeTicks }
                            val currentSegment =
                                segments.items
                                    .firstOrNull {
                                        it.type != MediaSegmentType.UNKNOWN && currentTicks >= it.startTicks && currentTicks < it.endTicks
                                    }
                            if (currentSegment != null &&
                                currentSegment.itemId == this@PlaybackViewModel.itemId
                            ) {
                                if (currentSegment.id !=
                                    state.value.currentSegment
                                        ?.segment
                                        ?.id
                                ) {
                                    Timber.d(
                                        "Found media segment for %s: %s, %s",
                                        currentSegment.itemId,
                                        currentSegment.id,
                                        currentSegment.type,
                                    )
                                }
                                val state = state.value

                                if (currentSegment.type == MediaSegmentType.OUTRO &&
                                    prefs.showNextUpWhen == ShowNextUpWhen.DURING_CREDITS &&
                                    state.hasNext &&
                                    outroShownSegments.add(currentSegment.id)
                                ) {
                                    val nextItem = state.nextItem()
                                    if (nextItem is PlaylistItem.Media) {
                                        Timber.v("Setting next up during outro to ${nextItem?.id}")
                                        _state.update { it.copy(nextUp = nextItem.item) }
                                    }
                                } else {
                                    val behavior =
                                        when (currentSegment.type) {
                                            MediaSegmentType.COMMERCIAL -> prefs.skipCommercials
                                            MediaSegmentType.PREVIEW -> prefs.skipPreviews
                                            MediaSegmentType.RECAP -> prefs.skipRecaps
                                            MediaSegmentType.OUTRO -> prefs.skipOutros
                                            MediaSegmentType.INTRO -> prefs.skipIntros
                                            MediaSegmentType.UNKNOWN -> SkipSegmentBehavior.IGNORE
                                        }
                                    withContext(WholphinDispatchers.Main) {
                                        val newSegment =
                                            when (behavior) {
                                                SkipSegmentBehavior.AUTO_SKIP -> {
                                                    if (autoSkippedSegments.add(currentSegment.id)) {
                                                        onMain { player.seekTo(currentSegment.endTicks.ticks.inWholeMilliseconds + 1) }
                                                    }
                                                    MediaSegmentState(currentSegment, true)
                                                }

                                                SkipSegmentBehavior.ASK_TO_SKIP -> {
                                                    MediaSegmentState(
                                                        currentSegment,
                                                        autoSkippedSegments.contains(currentSegment.id),
                                                    )
                                                }

                                                else -> {
                                                    null
                                                }
                                            }
                                        _state.update { it.copy(currentSegment = newSegment) }
                                    }
                                }
                            } else if (currentSegment == null) {
                                _state.update { it.copy(currentSegment = null) }
                            }
                        }
                    }
                }
        }

        fun updateSegment(
            segmentId: UUID?,
            dismissed: Boolean,
        ) {
            viewModelScope.launchDefault {
                val segment = state.value.currentSegment?.segment
                if (segment != null && segment.id == segmentId) {
                    autoSkippedSegments.add(segment.id)
                    if (dismissed) {
                        _state.update { it.copy(currentSegment = it.currentSegment?.copy(interacted = true)) }
                    } else {
                        _state.update { it.copy(currentSegment = null) }
                        onMain { player.seekTo(segment.endTicks.ticks.inWholeMilliseconds + 1) }
                    }
                }
            }
        }

        private fun listenForTranscodeReason(): Job =
            viewModelScope.launchIO {
                state.map { it.currentPlayback }.collectLatest {
                    if (it != null && it.playMethod == PlayMethod.TRANSCODE && it.transcodeInfo == null) {
                        try {
                            var transcodeInfo = it.transcodeInfo
                            while (isActive && transcodeInfo == null) {
                                delay(2.seconds)
                                transcodeInfo =
                                    api.sessionApi
                                        .getSessions(deviceId = deviceInfo.id)
                                        .content
                                        .firstOrNull()
                                        ?.transcodingInfo
                                if (transcodeInfo == null) delay(3.seconds)
                            }
                            Timber.v("transcodeInfo=$transcodeInfo")
                            updateCurrentPlayback { current ->
                                current?.copy(transcodeInfo = transcodeInfo)
                            }
                        } catch (ex: Exception) {
                            if (ex !is CancellationException) {
                                Timber.w(ex, "Exception trying to get session info")
                                updateCurrentPlayback { current ->
                                    current?.copy(transcodeInfo = null)
                                }
                            }
                        }
                    }
                }
            }

        private var lastInteractionDate: Date = Date()

        /**
         * Tracks interactions with the UI for passout protection
         */
        fun reportInteraction() {
//            Timber.v("reportInteraction")
            lastInteractionDate = Date()
        }

        fun shouldAutoPlayNextUp(): Boolean =
            preferences.appPreferences.playbackPreferences.let {
                it.autoPlayNext &&
                    if (it.passOutProtectionMs > 0) {
                        (Date().time - lastInteractionDate.time) < it.passOutProtectionMs
                    } else {
                        true
                    }
            }

        fun playNextUp() {
            viewModelScope.launchDefault {
                playlistMutex.withLock {
                    val state = state.value
                    if (state.hasNext) {
                        cancelUpNextEpisode()
                        val nextIndex = state.playlistIndex + 1
                        val item = state.playlist.items[nextIndex]
                        _state.update { it.copy(playlistIndex = nextIndex) }
                        playlistJob?.cancel()
                        playlistJob =
                            viewModelScope.launchDefault {
                                val played = play(item, 0)
                                if (!played) {
                                    playNextUp()
                                }
                            }
                    } else {
                        Timber.w("Attempting to play next, but there are no more items")
                        return@launchDefault
                    }
                }
            }
        }

        fun playPrevious() {
            viewModelScope.launchDefault {
                playlistMutex.withLock {
                    val state = state.value
                    if (state.hasPrevious) {
                        cancelUpNextEpisode()
                        val previousIndex = state.playlistIndex - 1
                        val item = state.playlist.items[previousIndex]
                        _state.update { it.copy(playlistIndex = previousIndex) }
                        playlistJob?.cancel()
                        playlistJob =
                            viewModelScope.launchDefault {
                                val played = play(item, 0)
                                if (!played) {
                                    playPrevious()
                                }
                            }
                    } else {
                        Timber.w("Attempting to play previous, but there is none")
                        return@launchDefault
                    }
                }
            }
        }

        suspend fun cancelUpNextEpisode() {
            _state.update { it.copy(nextUp = null) }
        }

        fun playItemInPlaylist(item: BaseItem) {
            viewModelScope.launchDefault {
                playlistMutex.withLock {
                    val state = state.value
                    val index = state.playlist.items.indexOfFirst { it.id == item.id }
                    if (index in state.playlist.items.indices) {
                        val toPlay = state.playlist.items[index]
                        _state.update { it.copy(playlistIndex = index) }
                        playlistJob?.cancel()
                        playlistJob =
                            viewModelScope.launchDefault {
                                val played = play(toPlay, 0)
                                if (!played) {
                                    playNextUp()
                                }
                            }
                    } else {
                        Timber.w("Item not found in playlist %s", item.id)
                        return@launchDefault
                    }
                }
            }
        }

        override fun onTracksChanged(tracks: Tracks) {
            updateCurrentPlayback {
                it?.copy(
                    tracks = checkForSupport(tracks),
                )
            }
        }

        override fun onCues(cueGroup: CueGroup) {
            _state.update { it.copy(subtitleCues = cueGroup.cues) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Timber.e(error, "Playback error")
            viewModelScope.launch(WholphinDispatchers.Main + ExceptionHandler()) {
                state.value.currentPlayback?.let {
                    when (it.playMethod) {
                        PlayMethod.TRANSCODE -> {
                            _state.update {
                                it.copy(
                                    loading =
                                        LoadingState.Error(
                                            "Error during playback",
                                            error,
                                        ),
                                )
                            }
                        }

                        PlayMethod.DIRECT_STREAM, PlayMethod.DIRECT_PLAY -> {
                            Timber.w("Playback error during ${it.playMethod}, falling back to transcoding")
                            PlaybackEvents.add("error during ${it.playMethod} → transcode: ${error.errorCodeName} ${error.cause?.message ?: error.message}")
                            val currentPlayback = state.value.currentPlayback
                            if (currentPlayback == null) {
                                Timber.w("Playback error, currentPlayback is null")
                            }
                            changeStreams(
                                item = currentItem.item,
                                sourceId = currentPlayback?.mediaSourceInfo?.id,
                                audioIndex = currentPlayback?.audioIndex,
                                subtitleIndex = currentPlayback?.subtitleIndex,
                                positionMs = player.currentPosition,
                                enableDirectPlay = false,
                                enableDirectStream = false,
                            )
                            withContext(WholphinDispatchers.Main) {
                                player.prepare()
                                player.play()
                            }
                        }
                    }
                }
            }
        }

        fun release() {
            Timber.v("release")
            released = true
            reportLeftForRating()
            disconnectPlayer()
            activityListener = null
        }

        @Volatile
        private var released = false

        // Last known progress, for the rating prompt when the player cannot tell any more (e.g. already released)
        @Volatile
        private var lastPositionMs = C.TIME_UNSET

        @Volatile
        private var lastDurationMs = C.TIME_UNSET

        private fun rememberProgress(p: Player) {
            val position = p.currentPosition
            val duration = p.duration
            if (position > 0) lastPositionMs = position
            if (duration != C.TIME_UNSET && duration > 0) lastDurationMs = duration
        }

        private var ratingReported = false

        /**
         * Offers the ČSFD rating prompt for a movie (or the last episode of a season) that ended or was left after most of it was watched.
         * Not for playlists or when another item follows (except the next season's first episode), so autoplay is never
         * interrupted.
         *
         * Also called when the app goes to the background during playback (the page's lifecycle stops), using the
         * last known position.
         */
        private fun reportLeftForRating() {
            if (ratingReported) return
            // Released on stop and again when the page is disposed, decided (and logged) once
            ratingReported = true
            if (isPlaylist) {
                Timber.i("No rating prompt: played as a playlist")
                return
            }
            if (!this::player.isInitialized || !this::currentItem.isInitialized) {
                Timber.i("No rating prompt: nothing was played")
                return
            }
            val media =
                currentItem as? PlaylistItem.Media ?: run {
                    Timber.i("No rating prompt: left during an intro")
                    return
                }
            val next = state.value.nextItem()
            if (next is PlaylistItem.Media && !endsSeason(media.item, next.item)) {
                Timber.i("No rating prompt for %s: another item follows", media.item.id)
                return
            }
            try {
                val p = player
                val ended = runCatching { p.playbackState == Player.STATE_ENDED }.getOrDefault(false)
                runCatching { rememberProgress(p) }
                val duration =
                    lastDurationMs.takeIf { it > 0 }
                        ?: media.item.data.runTimeTicks
                            ?.ticks
                            ?.inWholeMilliseconds
                            ?.takeIf { it > 0 }
                val position = lastPositionMs
                val watched =
                    ended ||
                        (duration != null && position > 0 && position >= duration * PendingRatingService.WATCHED_FRACTION)
                Timber.i(
                    "Left %s at %s of %s ms (ended=%s), watched=%s",
                    media.item.id,
                    position,
                    duration,
                    ended,
                    watched,
                )
                pendingRatingService.onPlaybackLeft(media.item, watched)
            } catch (ex: Exception) {
                Timber.w(ex, "Could not check playback progress for the rating prompt")
            }
        }

        /** Whether [next] is the first episode of another season after the episode [current] */
        private fun endsSeason(
            current: BaseItem,
            next: BaseItem,
        ): Boolean =
            current.type == BaseItemKind.EPISODE &&
                next.type == BaseItemKind.EPISODE &&
                current.data.seriesId == next.data.seriesId &&
                (current.data.seasonId ?: current.data.parentId) != (next.data.seasonId ?: next.data.parentId)

        fun subscribeToWebSocket() {
            subscribeJob?.cancel()
            try {
                subscribeJob =
                    viewModelScope.launch {
                        coroutineScope {
                            api.webSocket
                                .subscribe<PlaystateMessage>()
                                .onEach { message ->
                                    message.data?.let { request ->
                                        Timber.v("Received playstate request: %s", request)
                                        withContext(WholphinDispatchers.Main) {
                                            handlePlaystateRequest(request)
                                        }
                                    }
                                }.catch { ex ->
                                    Timber.e(ex, "Error in websocket subscription")
                                }.launchIn(this)
                            api.webSocket
                                .subscribe<GeneralCommandMessage>()
                                .onEach { message ->
                                    // Caught here, a failing command must not end the subscription
                                    try {
                                        message.data?.let { handleGeneralCommand(it) }
                                    } catch (ex: Exception) {
                                        Timber.e(ex, "Error handling general command")
                                    }
                                }.catch { ex ->
                                    Timber.e(ex, "Error in general command websocket subscription")
                                }.launchIn(this)
                        }
                    }
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Error in playback websocket subscription")
                if (viewModelScope.isActive) {
                    subscribeToWebSocket()
                }
            }
        }

        /**
         * Remote audio/subtitle track switching (e.g. from the Claude MCP bridge).
         * Jellyfin uses -1 for "subtitles off".
         */
        private fun handleGeneralCommand(command: GeneralCommand) {
            val index = command.arguments["Index"]?.toIntOrNull()

            // Only accept indexes of an existing stream of the right type, a wrong one would be saved for the item
            fun isStream(type: MediaStreamType) =
                state.value.currentPlayback
                    ?.mediaSourceInfo
                    ?.mediaStreams
                    ?.any { it.index == index && it.type == type } == true
            when (command.name) {
                GeneralCommandType.SET_AUDIO_STREAM_INDEX -> {
                    Timber.i("Remote audio stream change to %s", index)
                    if (index != null && isStream(MediaStreamType.AUDIO)) changeAudioStream(index)
                }

                GeneralCommandType.SET_SUBTITLE_STREAM_INDEX -> {
                    Timber.i("Remote subtitle stream change to %s", index)
                    when {
                        index == null -> {}

                        index < 0 -> {
                            changeSubtitleStream(TrackIndex.DISABLED)
                        }

                        isStream(MediaStreamType.SUBTITLE) -> {
                            changeSubtitleStream(index)
                        }
                    }
                }

                else -> {
                    // Other general commands are handled by ServerEventListener
                }
            }
        }

        private suspend fun handlePlaystateRequest(request: PlaystateRequest) =
            withContext(WholphinDispatchers.Main) {
                when (request.command) {
                    PlaystateCommand.STOP -> {
                        release()
                        navigationManager.goBack()
                    }

                    PlaystateCommand.PAUSE -> {
                        player.pause()
                    }

                    PlaystateCommand.UNPAUSE -> {
                        player.play()
                    }

                    PlaystateCommand.NEXT_TRACK -> {
                        playNextUp()
                    }

                    PlaystateCommand.PREVIOUS_TRACK -> {
                        playPrevious()
                    }

                    PlaystateCommand.SEEK -> {
                        request.seekPositionTicks?.ticks?.let {
                            player.seekTo(it.inWholeMilliseconds)
                        }
                    }

                    PlaystateCommand.REWIND -> {
                        player.seekBack(
                            preferences.appPreferences.playbackPreferences.skipBackMs.milliseconds,
                        )
                    }

                    PlaystateCommand.FAST_FORWARD -> {
                        player.seekForward(
                            preferences.appPreferences.playbackPreferences.skipForwardMs.milliseconds,
                        )
                    }

                    PlaystateCommand.PLAY_PAUSE -> {
                        Util.handlePlayPauseButtonAction(player)
                    }
                }
            }

        /**
         * Atomically update [currentMediaInfo]
         */
        internal suspend fun updateCurrentMedia(block: (CurrentMediaInfo) -> CurrentMediaInfo) =
            withContext(WholphinDispatchers.Default) {
                _state.update {
                    it.copy(currentMediaInfo = block.invoke(it.currentMediaInfo))
                }
            }

        private fun updateDecoder(
            decoderName: String,
            type: MediaType,
        ) {
            viewModelScope.launchDefault {
                val codecInfo =
                    MediaCodecList(MediaCodecList.ALL_CODECS)
                        .codecInfos
                        .firstOrNull { !it.isEncoder && it.name == decoderName }
                val decoderString =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        if (codecInfo?.isHardwareAccelerated == true) {
                            "$decoderName (HW)"
                        } else {
                            decoderName
                        }
                    } else {
                        decoderName
                    }
                updateCurrentPlayback {
                    when (type) {
                        MediaType.VIDEO -> it?.copy(videoDecoder = decoderString)
                        MediaType.AUDIO -> it?.copy(audioDecoder = decoderString)
                        else -> throw IllegalArgumentException("Unsupported type: $type")
                    }
                }
            }
        }

        override fun onVideoDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Timber.v("onVideoDecoderInitialized: decoder=$decoderName")
            updateDecoder(decoderName, MediaType.VIDEO)
        }

        override fun onVideoDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            Timber.d("onVideoDisabled")
            updateCurrentPlayback { it?.copy(videoDecoder = null) }
        }

        override fun onVideoInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            decoderReuseEvaluation?.let { decoder ->
                if (decoder.result != DecoderReuseEvaluation.REUSE_RESULT_NO) {
                    Timber.d("onVideoInputFormatChanged: decoder=${decoder.decoderName}")
                    updateDecoder(decoder.decoderName, MediaType.VIDEO)
                }
            }
        }

        override fun onAudioDecoderInitialized(
            eventTime: AnalyticsListener.EventTime,
            decoderName: String,
            initializedTimestampMs: Long,
            initializationDurationMs: Long,
        ) {
            Timber.d("decoder: onAudioDecoderInitialized: decoder=$decoderName")
            updateDecoder(decoderName, MediaType.AUDIO)
        }

        override fun onAudioInputFormatChanged(
            eventTime: AnalyticsListener.EventTime,
            format: Format,
            decoderReuseEvaluation: DecoderReuseEvaluation?,
        ) {
            decoderReuseEvaluation?.let { decoder ->
                if (decoder.result != DecoderReuseEvaluation.REUSE_RESULT_NO) {
                    Timber.d("decoder: onAudioInputFormatChanged: decoder=${decoder.decoderName}")
                    updateDecoder(decoder.decoderName, MediaType.AUDIO)
                }
            }
        }

        override fun onAudioDisabled(
            eventTime: AnalyticsListener.EventTime,
            decoderCounters: DecoderCounters,
        ) {
            Timber.d("decoder: onAudioDisabled")
            updateCurrentPlayback { it?.copy(audioDecoder = null) }
        }

        private var subtitleDelaySaveJob: Job? = null

        fun updateSubtitleDelay(delta: Duration) {
            subtitleDelaySaveJob?.cancel()
            updateCurrentPlayback {
                it?.let {
                    val newDelay = it.subtitleDelay + delta
                    val result = it.copy(subtitleDelay = it.subtitleDelay + delta)
                    subtitleDelaySaveJob =
                        viewModelScope.launchIO {
                            // Debounce & save
                            state.value.currentItemPlayback?.let { item ->
                                delay(1500)
                                itemPlaybackRepository.saveTrackModifications(
                                    item.itemId,
                                    item.subtitleIndex,
                                    newDelay,
                                )
                            }
                        }
                    result
                }
            }
        }

        suspend fun loadSubtitleDelay() {
            state.value.currentPlayback?.let {
                if (it.subtitleIndexEnabled) {
                    val result =
                        itemPlaybackRepository.getTrackModifications(it.item.id, it.subtitleIndex)
                    if (result != null) {
                        Timber.v(
                            "Loading subtitle delay %s for track=%s, itemId=%s",
                            result.delayMs,
                            it.subtitleIndex,
                            it.item.id,
                        )
                        updateCurrentPlayback { it?.copy(subtitleDelay = result.delayMs.milliseconds) }
                    }
                }
            }
        }

        override fun onAvailableCommandsChanged(availableCommands: Player.Commands) {
            val player = this@PlaybackViewModel.player
            if (availableCommands.contains(Player.COMMAND_PREPARE) && player is MpvPlayer) {
                // MpvPlayer is initialized, so configure subtitles
                Timber.i("Applying subtitle config to MPV")
                viewModelScope.launchDefault {
                    val configuration = context.resources.configuration
                    val density = Density(context.resources.displayMetrics.density)
                    preferences.appPreferences.interfacePreferences.subtitlesPreferences.applyToMpv(
                        configuration,
                        density,
                    )
                }
            }
        }

        fun updateDensity(density: Density) {
            Timber.d("Density changed")
            viewModelScope.launchDefault {
                val availableCommands = onMain { player.availableCommands }
                if (availableCommands.contains(Player.COMMAND_PREPARE) && player is MpvPlayer) {
                    Timber.i("Applying density change for subtitle config to MPV")
                    val configuration = context.resources.configuration
                    preferences.appPreferences.interfacePreferences.subtitlesPreferences.applyToMpv(
                        configuration,
                        density,
                    )
                }
            }
        }

        override fun onBandwidthEstimate(
            eventTime: AnalyticsListener.EventTime,
            totalLoadTimeMs: Int,
            totalBytesLoaded: Long,
            bitrateEstimate: Long,
        ) {
            Timber.v(
                "onBandwidthEstimate: totalLoadTimeMs=%s, totalBytesLoaded=%s, bitrateEstimate=%s",
                totalLoadTimeMs,
                totalBytesLoaded,
                bitrateEstimate,
            )
            if (totalLoadTimeMs > 0 && totalBytesLoaded > 0) {
                _state.update {
                    it.copy(
                        analyticsState =
                            it.analyticsState.copy(
                                bitrate = formatBitrate((totalBytesLoaded.toDouble() / (totalLoadTimeMs / 1000.0) * 8).roundToInt()),
                                bitrateEstimate = formatBitrate(bitrateEstimate.toInt()),
                            ),
                    )
                }
            }
        }

        override fun onDroppedVideoFrames(
            eventTime: AnalyticsListener.EventTime,
            droppedFrames: Int,
            elapsedMs: Long,
        ) {
            _state.update {
                it.copy(
                    analyticsState =
                        it.analyticsState.copy(
                            droppedFrames = it.analyticsState.droppedFrames + droppedFrames,
                        ),
                )
            }
        }
    }

private const val DEFERRED_SUBTITLE_MIN_BUFFER_MS = 40_000L
private const val DEFERRED_SUBTITLE_AFTER_READY_MS = 30_000L
private const val DEFERRED_SUBTITLE_MAX_WAIT_MS = 120_000L

/** What [DeviceStatusService] reports about the video on screen: how it plays, codec and size, dropped frames */
private fun PlaybackState.toDeviceSnapshot(): DeviceStatusService.PlaybackSnapshot? {
    val playback = currentPlayback ?: return null
    val stream = playback.mediaSourceInfo.mediaStreams?.firstOrNull { it.type == MediaStreamType.VIDEO }
    val source =
        listOfNotNull(
            stream?.codec?.uppercase(),
            stream?.let { s -> s.width?.let { w -> s.height?.let { h -> "$w×$h" } } },
            stream?.videoRangeType?.takeIf { it != VideoRangeType.SDR && it != VideoRangeType.UNKNOWN }?.serialName,
        ).joinToString(" ")
    val transcoded =
        playback.transcodeInfo?.let { info ->
            listOfNotNull(
                info.videoCodec?.uppercase(),
                info.width?.let { w -> info.height?.let { h -> "$w×$h" } },
            ).joinToString(" ").takeIf { it.isNotBlank() }
        }
    return DeviceStatusService.PlaybackSnapshot(
        title = playback.item.title,
        method = playback.playMethod.serialName,
        video = listOfNotNull(source.ifBlank { null }, transcoded?.let { "→ $it" }).joinToString(" ").ifBlank { null },
        droppedFrames = analyticsState.droppedFrames,
        transcodeReasons = playback.transcodeInfo?.transcodeReasons.orEmpty().map { it.serialName },
    )
}

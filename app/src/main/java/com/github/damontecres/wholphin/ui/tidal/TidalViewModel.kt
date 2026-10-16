package com.github.damontecres.wholphin.ui.tidal

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.github.damontecres.wholphin.services.tidal.TidalAlbum
import com.github.damontecres.wholphin.services.tidal.TidalApi
import com.github.damontecres.wholphin.services.tidal.TidalAuthException
import com.github.damontecres.wholphin.services.tidal.TidalDeviceCode
import com.github.damontecres.wholphin.services.tidal.TidalMix
import com.github.damontecres.wholphin.services.tidal.TidalPlaylist
import com.github.damontecres.wholphin.services.tidal.TidalService
import com.github.damontecres.wholphin.services.tidal.TidalSession
import com.github.damontecres.wholphin.services.tidal.TidalSettings
import com.github.damontecres.wholphin.services.tidal.TidalTrack
import com.github.damontecres.wholphin.services.tidal.TidalTrackShelf
import com.github.damontecres.wholphin.R
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import timber.log.Timber
import javax.inject.Inject

enum class TidalTab { PLAYLISTS, FOR_YOU, ALBUMS, TRACKS }

enum class TidalDetailKind { ALBUM, PLAYLIST, MIX, SHELF }

/** An opened album, playlist, mix or recommended track list */
data class TidalDetail(
    val kind: TidalDetailKind,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val tracks: List<TidalTrack> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
    /** Set for a playlist, so its tracks can be reordered */
    val playlistUuid: String? = null,
    /** Playlist shown newest added first instead of in its own order */
    val newestFirst: Boolean = true,
) {
    val isAlbum: Boolean get() = kind == TidalDetailKind.ALBUM
}

data class TidalUiState(
    /** Null until the saved login was read */
    val loggedIn: Boolean? = null,
    val loginCode: TidalDeviceCode? = null,
    val loginError: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val tab: TidalTab = TidalTab.PLAYLISTS,
    val playlists: List<TidalPlaylist> = emptyList(),
    val albums: List<TidalAlbum> = emptyList(),
    val tracks: List<TidalTrack> = emptyList(),
    val mixes: List<TidalMix> = emptyList(),
    val shelves: List<TidalTrackShelf> = emptyList(),
    val forYouLoading: Boolean = false,
    val detail: TidalDetail? = null,
    /** Track the user is picking a playlist for */
    val addToPlaylist: TidalTrack? = null,
    /** Short notice, e.g. that a track was added to a playlist */
    val message: String? = null,
    val settings: TidalSettings = TidalSettings(),
    val showSettings: Boolean = false,
)

data class TidalPlayerState(
    val queue: List<TidalTrack> = emptyList(),
    val index: Int = -1,
    val isPlaying: Boolean = false,
    val buffering: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val showNowPlaying: Boolean = false,
    val error: String? = null,
    /** Quality TIDAL streams the current track in, e.g. HIGH or LOSSLESS */
    val quality: String? = null,
    /** Synced lyrics of the current track, empty when there are only plain lyrics or none */
    val syncedLyrics: List<LyricLine> = emptyList(),
    val plainLyrics: String? = null,
    val lyricsLoading: Boolean = false,
) {
    val current: TidalTrack? get() = queue.getOrNull(index)
}

data class LyricLine(
    val timeMs: Long,
    val text: String,
)

private val LRC_LINE = Regex("""\[(\d+):(\d+(?:\.\d+)?)]\s*(.*)""")

/** Parses LRC lines like "[01:23.45] text" */
internal fun parseLrc(lrc: String?): List<LyricLine> =
    lrc
        ?.lineSequence()
        ?.mapNotNull { line ->
            LRC_LINE.matchEntire(line.trim())?.destructured?.let { (min, sec, text) ->
                LyricLine((min.toLong() * 60_000 + sec.toDouble() * 1000).toLong(), text)
            }
        }?.toList()
        .orEmpty()

/** The TIDAL page: login, the user's collection and a local [ExoPlayer] for the play queue */
@OptIn(UnstableApi::class)
@HiltViewModel
class TidalViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val service: TidalService,
    ) : ViewModel() {
        private val _state = MutableStateFlow(TidalUiState())
        val state: StateFlow<TidalUiState> = _state.asStateFlow()

        private val _player = MutableStateFlow(TidalPlayerState())
        val player: StateFlow<TidalPlayerState> = _player.asStateFlow()

        val configured: Boolean = service.api.isConfigured
        private var loginJob: Job? = null
        private var detailJob: Job? = null
        private var lyricsJob: Job? = null
        private val qualities = ConcurrentHashMap<Long, String>()

        /** Read on the player's loading thread, so kept outside the state */
        @Volatile private var streamQuality: String = TidalSettings().quality

        // Stream URLs expire, so each track's URL is fetched only when the player opens it
        private val exoPlayer: ExoPlayer =
            ExoPlayer
                .Builder(context)
                .setMediaSourceFactory(
                    DefaultMediaSourceFactory(
                        ResolvingDataSource.Factory(DefaultDataSource.Factory(context)) { spec ->
                            val id = spec.uri.lastPathSegment?.toLongOrNull() ?: return@Factory spec
                            // Runs on the player's loading thread
                            val stream = runBlocking { service.call { service.api.streamUrl(it, id, streamQuality) } }
                            stream.audioQuality?.let { q ->
                                qualities[id] = q
                                _player.update { if (it.current?.id == id) it.copy(quality = q) else it }
                            }
                            spec.withUri(Uri.parse(stream.url))
                        },
                    ),
                ).setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .build()

        init {
            exoPlayer.addListener(
                object : Player.Listener {
                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _player.update { it.copy(isPlaying = isPlaying) }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        _player.update { it.copy(buffering = playbackState == Player.STATE_BUFFERING) }
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int,
                    ) {
                        onTrackChanged()
                    }

                    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                        _player.update { it.copy(shuffle = shuffleModeEnabled) }
                    }

                    override fun onRepeatModeChanged(repeatMode: Int) {
                        _player.update { it.copy(repeatMode = repeatMode) }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Timber.w(error, "TIDAL playback failed")
                        _player.update { it.copy(error = error.cause?.message ?: error.message) }
                        // Skip a track that cannot be streamed (e.g. not available in the country)
                        if (exoPlayer.hasNextMediaItem()) {
                            exoPlayer.seekToNextMediaItem()
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                    }
                },
            )
            viewModelScope.launch {
                while (isActive) {
                    delay(500)
                    _player.update {
                        it.copy(
                            positionMs = exoPlayer.currentPosition.coerceAtLeast(0),
                            durationMs = exoPlayer.duration.takeIf { d -> d > 0 } ?: it.current?.duration?.times(1000L) ?: 0,
                        )
                    }
                }
            }
            viewModelScope.launch {
                service.settings.collect { settings ->
                    streamQuality = settings.quality
                    _state.update { it.copy(settings = settings) }
                }
            }
            viewModelScope.launch {
                service.session.distinctUntilChangedBy { it.isLoggedIn }.collect { session ->
                    _state.update { it.copy(loggedIn = session.isLoggedIn) }
                    if (session.isLoggedIn) loadCollection() else startLogin()
                }
            }
        }

        fun startLogin() {
            if (!configured) return
            loginJob?.cancel()
            loginJob =
                viewModelScope.launch {
                    _state.update { it.copy(loginCode = null, loginError = null) }
                    try {
                        service.login { code -> _state.update { it.copy(loginCode = code) } }
                    } catch (ex: Exception) {
                        Timber.w(ex, "TIDAL login failed")
                        _state.update { it.copy(loginCode = null, loginError = ex.message) }
                    }
                }
        }

        fun showSettings(show: Boolean) = _state.update { it.copy(showSettings = show) }

        fun updateSettings(change: (TidalSettings) -> TidalSettings) {
            val updated = change(_state.value.settings)
            _state.update { it.copy(settings = updated) }
            viewModelScope.launch { service.saveSettings(updated) }
        }

        fun logout() {
            _state.update { it.copy(showSettings = false) }
            exoPlayer.stop()
            exoPlayer.clearMediaItems()
            _player.value = TidalPlayerState()
            viewModelScope.launch { service.logout() }
        }

        fun loadCollection() {
            viewModelScope.launch {
                _state.update { it.copy(loading = true, error = null) }
                try {
                    val playlists = service.call { service.api.playlists(it) }
                    _state.update { it.copy(playlists = playlists) }
                    val albums = service.call { service.api.favoriteAlbums(it) }
                    _state.update { it.copy(albums = albums) }
                    val tracks = service.call { service.api.favoriteTracks(it) }
                    _state.update { it.copy(tracks = tracks, loading = false) }
                } catch (ex: TidalAuthException) {
                    _state.update { it.copy(loading = false) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Loading TIDAL collection failed")
                    _state.update { it.copy(loading = false, error = ex.message) }
                }
            }
            loadForYou()
        }

        /** Mixes and recommended tracks; failing here must not hide the rest of the collection */
        private fun loadForYou() {
            viewModelScope.launch {
                _state.update { it.copy(forYouLoading = true) }
                try {
                    val forYou = service.call { service.api.forYou(it) }
                    Timber.i("TIDAL for you: %d mixes, %d track lists", forYou.mixes.size, forYou.shelves.size)
                    _state.update { it.copy(mixes = forYou.mixes, shelves = forYou.shelves, forYouLoading = false) }
                } catch (ex: Exception) {
                    Timber.w(ex, "Loading TIDAL mixes failed")
                    _state.update { it.copy(forYouLoading = false) }
                }
            }
        }

        fun selectTab(tab: TidalTab) = _state.update { it.copy(tab = tab) }

        fun openAlbum(album: TidalAlbum) =
            openDetail(
                TidalDetail(
                    kind = TidalDetailKind.ALBUM,
                    title = album.title,
                    subtitle = album.artist?.name.orEmpty(),
                    imageUrl = TidalApi.imageUrl(album.cover, 640),
                ),
            ) { service.api.albumTracks(it, album.id) }

        fun openPlaylist(playlist: TidalPlaylist) =
            openPlaylistDetail(
                TidalDetail(
                    kind = TidalDetailKind.PLAYLIST,
                    title = playlist.title,
                    subtitle = "",
                    imageUrl = TidalApi.imageUrl(playlist.squareImage ?: playlist.image, 640),
                    playlistUuid = playlist.uuid,
                    newestFirst = _state.value.settings.newestFirst,
                ),
            )

        private fun openPlaylistDetail(detail: TidalDetail) {
            val uuid = detail.playlistUuid ?: return
            openDetail(detail) { session ->
                val tracks = service.api.playlistTracks(session, uuid, detail.newestFirst)
                // In case TIDAL ignores the order, sort by the dates it sends with playlist tracks
                if (detail.newestFirst && tracks.any { it.dateAdded != null }) {
                    tracks.sortedByDescending { it.dateAdded.orEmpty() }
                } else {
                    tracks
                }
            }
        }

        /** Switches an opened playlist between newest added first and its own order */
        fun toggleNewestFirst() {
            val detail = _state.value.detail?.takeIf { it.playlistUuid != null } ?: return
            openPlaylistDetail(detail.copy(newestFirst = !detail.newestFirst, loading = true, error = null, tracks = emptyList()))
        }

        fun openMix(mix: TidalMix) =
            openDetail(
                TidalDetail(
                    kind = TidalDetailKind.MIX,
                    title = mix.title,
                    subtitle = mix.subTitle,
                    imageUrl = mix.imageUrl,
                ),
            ) { service.api.mixTracks(it, mix.id) }

        fun openShelf(shelf: TidalTrackShelf) {
            detailJob?.cancel()
            _state.update {
                it.copy(
                    detail =
                        TidalDetail(
                            kind = TidalDetailKind.SHELF,
                            title = shelf.title,
                            subtitle = "",
                            imageUrl = TidalApi.imageUrl(shelf.tracks.firstOrNull()?.album?.cover, 640),
                            tracks = shelf.tracks,
                            loading = false,
                        ),
                )
            }
        }

        /** Asks which playlist [track] should go to */
        fun pickPlaylistFor(track: TidalTrack?) = _state.update { it.copy(addToPlaylist = track) }

        fun addToPlaylist(playlist: TidalPlaylist) {
            val track = _state.value.addToPlaylist ?: return
            _state.update { it.copy(addToPlaylist = null) }
            viewModelScope.launch {
                val message =
                    try {
                        service.call { service.api.addToPlaylist(it, playlist.uuid, track.id) }
                        // Refresh the counts on the playlist covers
                        runCatching { service.call { service.api.playlists(it) } }.getOrNull()?.let { playlists ->
                            _state.update { it.copy(playlists = playlists) }
                        }
                        // An opened copy of that playlist gets the track too
                        _state.value.detail?.takeIf { it.playlistUuid == playlist.uuid }?.let { openPlaylistDetail(it) }
                        context.getString(R.string.tidal_added_to_playlist, track.title, playlist.title)
                    } catch (ex: Exception) {
                        Timber.w(ex, "Adding to TIDAL playlist failed")
                        context.getString(R.string.tidal_add_failed, ex.message.orEmpty())
                    }
                _state.update { it.copy(message = message) }
                delay(4000)
                _state.update { if (it.message == message) it.copy(message = null) else it }
            }
        }

        private fun openDetail(
            detail: TidalDetail,
            load: suspend (TidalSession) -> List<TidalTrack>,
        ) {
            detailJob?.cancel()
            _state.update { it.copy(detail = detail) }
            detailJob =
                viewModelScope.launch {
                    try {
                        val tracks = service.call(load)
                        _state.update { it.copy(detail = it.detail?.copy(tracks = tracks, loading = false)) }
                    } catch (ex: Exception) {
                        Timber.w(ex, "Loading TIDAL tracks failed")
                        _state.update { it.copy(detail = it.detail?.copy(loading = false, error = ex.message)) }
                    }
                }
        }

        fun closeDetail() {
            detailJob?.cancel()
            _state.update { it.copy(detail = null) }
        }

        /** Replaces the queue with [tracks] and plays from [index]; [shuffle] plays them in random order */
        fun play(
            tracks: List<TidalTrack>,
            index: Int = 0,
            shuffle: Boolean = false,
        ) {
            if (tracks.isEmpty()) return
            exoPlayer.setMediaItems(tracks.map { it.toMediaItem() }, if (shuffle) 0 else index, 0)
            exoPlayer.shuffleModeEnabled = shuffle
            if (shuffle) exoPlayer.seekTo(exoPlayer.currentTimeline.getFirstWindowIndex(true), 0)
            exoPlayer.prepare()
            exoPlayer.play()
            _player.update { it.copy(queue = tracks) }
            onTrackChanged()
        }

        private fun onTrackChanged() {
            val index = exoPlayer.currentMediaItemIndex
            _player.update { it.copy(index = index, error = null) }
            val track = _player.value.current ?: return
            _player.update {
                it.copy(quality = qualities[track.id], syncedLyrics = emptyList(), plainLyrics = null, lyricsLoading = true)
            }
            lyricsJob?.cancel()
            lyricsJob =
                viewModelScope.launch {
                    val lyrics =
                        try {
                            service.call { service.api.lyrics(it, track.id) }
                        } catch (ex: Exception) {
                            Timber.w(ex, "TIDAL lyrics failed")
                            null
                        }
                    _player.update {
                        if (it.current?.id != track.id) return@update it
                        it.copy(syncedLyrics = parseLrc(lyrics?.subtitles), plainLyrics = lyrics?.lyrics, lyricsLoading = false)
                    }
                }
        }

        fun togglePlayPause() {
            if (exoPlayer.isPlaying) {
                exoPlayer.pause()
            } else {
                if (exoPlayer.playbackState == Player.STATE_ENDED) exoPlayer.seekTo(0, 0)
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }

        fun pause() = exoPlayer.pause()

        fun next() = exoPlayer.seekToNextMediaItem()

        fun previous() = exoPlayer.seekToPrevious()

        fun seekBy(ms: Long) = exoPlayer.seekTo((exoPlayer.currentPosition + ms).coerceIn(0, exoPlayer.duration.coerceAtLeast(0)))

        fun playIndex(index: Int) {
            exoPlayer.seekTo(index, 0)
            exoPlayer.play()
        }

        fun toggleShuffle() {
            exoPlayer.shuffleModeEnabled = !exoPlayer.shuffleModeEnabled
        }

        /** Off, repeat all, repeat one - same order as the TIDAL app */
        fun cycleRepeat() {
            exoPlayer.repeatMode =
                when (exoPlayer.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }
        }

        fun showNowPlaying(show: Boolean) = _player.update { it.copy(showNowPlaying = show) }

        override fun onCleared() {
            exoPlayer.release()
        }

        private fun TidalTrack.toMediaItem() =
            MediaItem
                .Builder()
                .setMediaId(id.toString())
                .setUri("tidal://track/$id")
                .setMediaMetadata(
                    MediaMetadata
                        .Builder()
                        .setTitle(title)
                        .setArtist(artistNames)
                        .setAlbumTitle(album?.title)
                        .setArtworkUri(TidalApi.imageUrl(album?.cover, 640)?.let(Uri::parse))
                        .build(),
                ).build()
    }

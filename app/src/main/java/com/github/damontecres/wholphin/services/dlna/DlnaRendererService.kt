package com.github.damontecres.wholphin.services.dlna

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ScreensaverService
import com.github.damontecres.wholphin.services.TvMessageService
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.ui.nav.Destination
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/** What the "Now playing" screen shows */
data class DlnaNowPlaying(
    val visible: Boolean = false,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtUri: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val transportState: String = TransportStates.NO_MEDIA,
)

/**
 * Makes the app a UPnP/DLNA MediaRenderer on the LAN ("Wholphinix – <device name>"), so phone apps such as
 * BubbleUPnP (which can play from TIDAL or Qobuz) can play music on the TV.
 *
 * It runs while the app process lives and the setting is on. The music plays in an ExoPlayer owned by this service,
 * shown by the "Now playing" overlay ([state]).
 */
@Singleton
class DlnaRendererService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
        private val preferences: DataStore<AppPreferences>,
        private val navigationManager: NavigationManager,
        private val screensaverService: ScreensaverService,
        private val tvMessageService: TvMessageService,
    ) : DlnaRendererControl {
        private val _state = MutableStateFlow(DlnaNowPlaying())
        val state: StateFlow<DlnaNowPlaying> = _state

        private val udn: String by lazy { loadUdn() }
        private val soapHandler = DlnaSoapHandler(this)
        private val eventing = DlnaEventing { snapshot() }
        private var httpServer: DlnaHttpServer? = null
        private var ssdpServer: SsdpServer? = null
        private var multicastLock: WifiManager.MulticastLock? = null

        @Volatile
        private var lanAddress: String? = null

        // Player state, only touched on the main thread
        private var player: ExoPlayer? = null
        private var currentUri: String? = null
        private var currentMetadataXml: String? = null
        private var currentMetadata: DidlMetadata? = null
        private var nextUri: String? = null
        private var nextMetadataXml: String? = null
        private var nextMetadata: DidlMetadata? = null
        private var stopped = true
        private var error = false
        private var volume = 100
        private var muted = false
        private var tickerJob: Job? = null
        private var lastPlaybackReport: TimeMark? = null
        private var keepingScreenOn = false

        init {
            preferences.data
                .map { !it.interfacePreferences.dlnaRendererDisabled }
                .distinctUntilChanged()
                .onEach { enabled ->
                    withContext(WholphinDispatchers.IO) {
                        if (enabled) startNetwork() else stopNetwork()
                    }
                    if (!enabled) withContext(WholphinDispatchers.Main) { releasePlayer() }
                }.catch { ex ->
                    Timber.e(ex, "DLNA: error with the renderer setting")
                }.launchIn(scope)
        }

        /** Called when the app comes to the foreground: re-announce if the network changed meanwhile */
        fun onAppResumed() {
            scope.launch(WholphinDispatchers.IO) {
                if (httpServer == null) return@launch
                val address = SsdpServer.lanAddress()?.hostAddress
                if (address != null && address != lanAddress) {
                    Timber.i("DLNA: the LAN address changed to %s, restarting discovery", address)
                    synchronized(this@DlnaRendererService) {
                        lanAddress = address
                        ssdpServer?.stop()
                        ssdpServer = SsdpServer(udn) { location() }.also { it.start() }
                    }
                }
            }
        }

        @Synchronized
        private fun startNetwork() {
            if (httpServer != null) return
            try {
                lanAddress = SsdpServer.lanAddress()?.hostAddress
                val server = DlnaHttpServer(::handleHttp)
                server.start(PREFERRED_PORT)
                httpServer = server
                acquireMulticastLock()
                ssdpServer = SsdpServer(udn) { location() }.also { it.start() }
                Timber.i("DLNA: renderer '%s' started at %s", friendlyName(), location())
            } catch (ex: Exception) {
                Timber.e(ex, "DLNA: cannot start the renderer")
                stopNetwork()
            }
        }

        @Synchronized
        private fun stopNetwork() {
            ssdpServer?.stop()
            ssdpServer = null
            httpServer?.stop()
            httpServer = null
            eventing.clear()
            try {
                multicastLock?.takeIf { it.isHeld }?.release()
            } catch (ex: Exception) {
                Timber.w(ex, "DLNA: cannot release the multicast lock")
            }
            multicastLock = null
        }

        private fun acquireMulticastLock() {
            try {
                val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return
                multicastLock =
                    wifi.createMulticastLock("wholphinix-dlna").apply {
                        setReferenceCounted(false)
                        acquire()
                    }
            } catch (ex: Exception) {
                // e.g. no wifi on an ethernet-only device
                Timber.w(ex, "DLNA: cannot acquire the multicast lock")
            }
        }

        private fun location(): String? {
            val address = lanAddress ?: SsdpServer.lanAddress()?.hostAddress?.also { lanAddress = it } ?: return null
            val port = httpServer?.port?.takeIf { it > 0 } ?: return null
            return "http://$address:$port/description.xml"
        }

        private fun friendlyName(): String {
            val deviceName =
                try {
                    Settings.Global.getString(context.contentResolver, "device_name")
                } catch (_: Exception) {
                    null
                }?.takeIf { it.isNotBlank() } ?: Build.MODEL ?: "TV"
            return "Wholphinix – $deviceName"
        }

        private fun loadUdn(): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val existing = prefs.getString(KEY_UDN, null)
            if (existing != null) return existing
            val created = "uuid:${UUID.randomUUID()}"
            prefs.edit().putString(KEY_UDN, created).apply()
            return created
        }

        private fun handleHttp(request: HttpRequest): HttpResponse {
            val path = request.path
            if (path == "/description.xml" || path == "/") {
                if (request.method != "GET" && request.method != "HEAD") return HttpResponse(405, contentType = null)
                return HttpResponse(
                    200,
                    DlnaDescriptions.deviceDescription(friendlyName(), udn, Build.VERSION.RELEASE ?: "1"),
                )
            }
            val service = DlnaService.fromPath(path) ?: return HttpResponse.notFound()
            return when (path) {
                service.scpdPath -> {
                    HttpResponse(200, DlnaDescriptions.scpdFor(service))
                }

                service.controlPath -> {
                    if (request.method != "POST") return HttpResponse(405, contentType = null)
                    val soap =
                        DlnaXml.parseSoapRequest(request.body)
                            ?: return HttpResponse(
                                500,
                                DlnaXml.buildSoapFault(DlnaSoapHandler.ERROR_INVALID_ACTION, "Invalid action"),
                            )
                    Timber.d("DLNA: %s %s from %s", service.path, soap.action, request.remoteAddress)
                    val result = soapHandler.handle(service, soap)
                    if (result.httpStatus == 200) eventing.notifyChanged()
                    HttpResponse(result.httpStatus, result.body)
                }

                service.eventPath -> {
                    eventing.handle(service, request)
                }

                else -> {
                    HttpResponse.notFound()
                }
            }
        }

        // -------------------------------------------------------------------------------------------------------
        // DlnaRendererControl, called from the HTTP threads

        private fun <T> onMain(block: () -> T): T =
            runBlocking {
                withTimeout(MAIN_TIMEOUT) {
                    withContext(WholphinDispatchers.Main) { block() }
                }
            }

        override fun snapshot(): RendererSnapshot = onMain { buildSnapshot() }

        override fun setUri(
            uri: String,
            metadataXml: String?,
            metadata: DidlMetadata?,
        ) = onMain {
            Timber.i("DLNA: set URI %s (%s - %s)", uri, metadata?.artist, metadata?.title)
            val p = getOrCreatePlayer()
            currentUri = uri
            currentMetadataXml = metadataXml
            currentMetadata = metadata
            nextUri = null
            nextMetadataXml = null
            nextMetadata = null
            stopped = true
            error = false
            p.stop()
            p.setMediaItems(listOf(mediaItem(uri, metadata)), true)
            updateState()
        }

        override fun setNextUri(
            uri: String?,
            metadataXml: String?,
            metadata: DidlMetadata?,
        ) = onMain {
            val p = getOrCreatePlayer()
            nextUri = uri
            nextMetadataXml = metadataXml
            nextMetadata = metadata
            // The next track follows gaplessly as the second item of the playlist
            if (p.mediaItemCount > 1) p.removeMediaItems(1, p.mediaItemCount)
            if (uri != null && currentUri != null) p.addMediaItem(mediaItem(uri, metadata))
            updateState()
        }

        override fun play() =
            onMain {
                if (isVideoPlaying()) {
                    tvMessageService.show(
                        context.getString(R.string.dlna_renderer_title),
                        context.getString(R.string.dlna_video_playing),
                    )
                    throw UpnpException(DlnaSoapHandler.ERROR_TRANSITION_NOT_AVAILABLE, "A video is playing")
                }
                val p = getOrCreatePlayer()
                if (p.mediaItemCount == 0) {
                    currentUri?.let { p.setMediaItems(listOf(mediaItem(it, currentMetadata)), true) }
                        ?: throw UpnpException(DlnaSoapHandler.ERROR_TRANSITION_NOT_AVAILABLE, "No media")
                }
                if (stopped || p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
                    if (p.playbackState == Player.STATE_ENDED) p.seekTo(0, 0L)
                    p.prepare()
                }
                stopped = false
                error = false
                p.play()
                // Show the "Now playing" screen over the app (and over the in-app screensaver)
                screensaverService.stop(false)
                screensaverService.pulse()
                updateState(visible = true)
            }

        override fun pause() =
            onMain {
                player?.pause()
                updateState()
            }

        override fun stop() =
            onMain {
                player?.let {
                    it.stop()
                    it.seekTo(0, 0L)
                }
                stopped = true
                updateState()
            }

        override fun seek(positionMs: Long) =
            onMain {
                player?.seekTo(positionMs.coerceAtLeast(0L))
                updateState()
            }

        override fun setVolume(volume: Int) =
            onMain {
                this.volume = volume.coerceIn(0, 100)
                applyVolume()
                updateState()
            }

        override fun setMute(mute: Boolean) =
            onMain {
                muted = mute
                applyVolume()
                updateState()
            }

        // -------------------------------------------------------------------------------------------------------
        // Controls of the "Now playing" screen (main thread)

        fun togglePlayPause() {
            val p = player ?: return
            if (p.isPlaying) {
                p.pause()
            } else {
                if (p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED) {
                    if (p.playbackState == Player.STATE_ENDED) p.seekTo(0, 0L)
                    p.prepare()
                }
                stopped = false
                p.play()
            }
            changed()
        }

        fun seekBy(deltaMs: Long) {
            val p = player ?: return
            val duration = p.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            val target = (p.currentPosition + deltaMs).coerceAtLeast(0L).let { if (duration != null) it.coerceAtMost(duration) else it }
            p.seekTo(target)
            changed()
        }

        fun stopPlayback() {
            player?.let {
                it.stop()
                it.seekTo(0, 0L)
            }
            stopped = true
            changed()
            hide()
        }

        /** Hide the "Now playing" screen, the music goes on */
        fun hide() {
            _state.value = _state.value.copy(visible = false)
            updateKeepScreenOn()
        }

        // -------------------------------------------------------------------------------------------------------

        private fun applyVolume() {
            player?.volume = if (muted) 0f else volume / 100f
        }

        private fun isVideoPlaying(): Boolean {
            val last = navigationManager.backStack.lastOrNull()
            return last is Destination.Playback || last is Destination.PlaybackList
        }

        private fun mediaItem(
            uri: String,
            metadata: DidlMetadata?,
        ): MediaItem {
            val builder =
                MediaItem
                    .Builder()
                    .setUri(uri)
                    .setMediaId(uri)
                    .setMediaMetadata(
                        MediaMetadata
                            .Builder()
                            .setTitle(metadata?.title)
                            .setArtist(metadata?.artist)
                            .setAlbumTitle(metadata?.album)
                            .build(),
                    )
            DlnaXml.mimeTypeFromProtocolInfo(metadata?.protocolInfo)?.let { mime ->
                // Only pass adaptive streaming types, ExoPlayer detects the audio containers by itself
                if (mime.contains("mpegurl", true) || mime.contains("dash", true)) builder.setMimeType(mime)
            }
            return builder.build()
        }

        private fun getOrCreatePlayer(): ExoPlayer {
            player?.let { return it }
            val p =
                ExoPlayer
                    .Builder(context)
                    .setAudioAttributes(
                        AudioAttributes
                            .Builder()
                            .setUsage(C.USAGE_MEDIA)
                            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                            .build(),
                        true,
                    ).build()
            p.addListener(
                object : Player.Listener {
                    override fun onEvents(
                        player: Player,
                        events: Player.Events,
                    ) {
                        changed()
                    }

                    override fun onMediaItemTransition(
                        mediaItem: MediaItem?,
                        reason: Int,
                    ) {
                        if (p.currentMediaItemIndex > 0 && nextUri != null) {
                            // The next track started: it becomes the current one
                            currentUri = nextUri
                            currentMetadataXml = nextMetadataXml
                            currentMetadata = nextMetadata
                            nextUri = null
                            nextMetadataXml = null
                            nextMetadata = null
                            p.removeMediaItems(0, p.currentMediaItemIndex)
                        }
                    }

                    override fun onPlayerError(playbackError: PlaybackException) {
                        Timber.w(playbackError, "DLNA: playback error for %s", currentUri)
                        error = true
                        stopped = true
                    }
                },
            )
            player = p
            applyVolume()
            return p
        }

        private fun releasePlayer() {
            tickerJob?.cancel()
            tickerJob = null
            player?.release()
            player = null
            currentUri = null
            currentMetadataXml = null
            currentMetadata = null
            nextUri = null
            nextMetadataXml = null
            nextMetadata = null
            stopped = true
            _state.value = DlnaNowPlaying()
            updateKeepScreenOn()
        }

        private fun transportState(): String {
            val p = player
            return when {
                currentUri == null || p == null -> {
                    TransportStates.NO_MEDIA
                }

                stopped || error -> {
                    TransportStates.STOPPED
                }

                p.playbackState == Player.STATE_IDLE || p.playbackState == Player.STATE_ENDED -> {
                    TransportStates.STOPPED
                }

                p.playbackState == Player.STATE_BUFFERING && p.playWhenReady -> {
                    TransportStates.TRANSITIONING
                }

                p.playWhenReady -> {
                    TransportStates.PLAYING
                }

                else -> {
                    TransportStates.PAUSED
                }
            }
        }

        private fun buildSnapshot(): RendererSnapshot {
            val p = player
            val duration = p?.duration?.takeIf { it != C.TIME_UNSET && it > 0 } ?: currentMetadata?.durationMs ?: 0L
            return RendererSnapshot(
                transportState = transportState(),
                transportStatus = if (error) "ERROR_OCCURRED" else "OK",
                uri = currentUri,
                metadataXml = currentMetadataXml,
                metadata = currentMetadata,
                nextUri = nextUri,
                nextMetadataXml = nextMetadataXml,
                positionMs = p?.currentPosition?.coerceAtLeast(0L) ?: 0L,
                durationMs = duration,
                volume = volume,
                muted = muted,
            )
        }

        /** State changed on the main thread: update the screen and tell the subscribers */
        private fun changed() {
            updateState()
            eventing.notifyChanged()
        }

        private fun updateState(visible: Boolean? = null) {
            val p = player
            val snapshot = buildSnapshot()
            _state.value =
                DlnaNowPlaying(
                    visible = (visible ?: _state.value.visible) && currentUri != null,
                    title = currentMetadata?.title ?: currentUri?.substringAfterLast('/')?.substringBefore('?'),
                    artist = currentMetadata?.artist,
                    album = currentMetadata?.album,
                    albumArtUri = currentMetadata?.albumArtUri,
                    positionMs = snapshot.positionMs,
                    durationMs = snapshot.durationMs,
                    isPlaying = p?.isPlaying == true,
                    isBuffering = p?.playbackState == Player.STATE_BUFFERING,
                    transportState = snapshot.transportState,
                )
            updateKeepScreenOn()
            updateTicker()
        }

        /** While the music plays on the "Now playing" screen, no screensaver over it */
        private fun updateKeepScreenOn() {
            val s = _state.value
            val keep = s.visible && s.isPlaying
            if (keep != keepingScreenOn) {
                keepingScreenOn = keep
                if (keep) screensaverService.acquireKeepScreenOn(this) else screensaverService.releaseKeepScreenOn(this)
            }
            if (keep) {
                val last = lastPlaybackReport
                if (last == null || last.elapsedNow() > REPORT_INTERVAL) {
                    lastPlaybackReport = TimeSource.Monotonic.markNow()
                    screensaverService.reportPlaybackActive(this)
                }
            } else {
                lastPlaybackReport = null
            }
        }

        /** Updates the position on the screen every half second while playing */
        private fun updateTicker() {
            val playing = player?.isPlaying == true
            if (playing && tickerJob?.isActive != true) {
                tickerJob =
                    scope.launch(WholphinDispatchers.Main) {
                        while (isActive && player?.isPlaying == true) {
                            delay(500.milliseconds)
                            updateState()
                        }
                    }
            }
        }

        companion object {
            private const val PREFERRED_PORT = 49494
            private const val PREFS_NAME = "dlna_renderer"
            private const val KEY_UDN = "udn"
            private val MAIN_TIMEOUT = 5.seconds
            private val REPORT_INTERVAL = 4.minutes
        }
    }

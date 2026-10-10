package com.github.damontecres.wholphin.services

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import coil3.imageLoader
import coil3.request.ImageRequest
import com.github.damontecres.wholphin.services.hilt.DefaultCoroutineScope
import com.github.damontecres.wholphin.ui.components.ScreensaverItem
import com.github.damontecres.wholphin.ui.formatDate
import com.github.damontecres.wholphin.util.ApiRequestPager
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.GetItemsRequestHandler
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.cancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.ItemSortBy
import org.jellyfin.sdk.model.api.request.GetItemsRequest
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * Handles the queue of items to show on the screensaver, both in-app or OS
 */
@Singleton
class ScreensaverService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:DefaultCoroutineScope private val scope: CoroutineScope,
        private val api: ApiClient,
        private val userPreferencesService: UserPreferencesService,
        private val imageUrlService: ImageUrlService,
    ) {
        private val _state = MutableStateFlow(ScreensaverState(false, false, false, false))
        val state: StateFlow<ScreensaverState> = _state

        val keepScreenOn = MutableStateFlow(false)

        private var waitJob: Job? = null
        private var dimJob: Job? = null

        /** Starts the system screensaver (e.g. Aerial Views) after [SYSTEM_SCREENSAVER_DELAY] without input */
        private var idleJob: Job? = null

        /** The system screensaver could not be started, so the OS screen timeout is used instead */
        @Volatile
        private var systemScreensaverUnavailable = false

        /** Who currently needs the screen on (players, slideshow). Guarded by `this`. */
        private val keepOnOwners = mutableSetOf<Any>()
        private val requestedKeepOn get() = keepOnOwners.isNotEmpty()

        init {
            userPreferencesService.flow
                .onEach { prefs ->
                    synchronized(this) {
                        val enabled =
                            prefs.appPreferences.interfacePreferences.screensaverPreference.enabled
                        // Preferences can change during playback (e.g. a saved subtitle delay), which must not let
                        // the OS or in-app screensaver start over the video
                        keepScreenOnInternal(enabled || requestedKeepOn)
                        _state.update {
                            ScreensaverState(
                                enabled = enabled,
                                enabledTemp = false,
                                active = false,
                                paused = requestedKeepOn,
                                dimEnabled = prefs.appPreferences.interfacePreferences.screensaverPreference.dimEnabled,
                                dimActive = false,
                            )
                        }
                    }
                }.launchIn(scope)
        }

        /**
         * Reset the timer before showing the in-app screensaver
         */
        fun pulse() {
            restartIdleTimer()
            waitJob?.cancel()
            if (_state.value.enabled) {
//                Timber.v("pulse")
                _state.update {
                    if (!it.active) {
                        it.copy(
                            active = false,
                            dimActive = false,
                        )
                    } else {
                        it
                    }
                }

                if (!_state.value.paused) {
                    waitJob =
                        scope.launch(ExceptionHandler()) {
                            val startDelay =
                                userPreferencesService
                                    .getCurrent()
                                    .appPreferences.interfacePreferences.screensaverPreference.startDelay.milliseconds
                            delay(startDelay)
                            _state.update {
                                it.copy(
                                    active = true,
                                    dimActive = it.dimEnabled,
                                )
                            }
                        }
                }
            }
            dimJob?.cancel()
            if (_state.value.dimEnabled) {
                _state.update {
                    it.copy(dimActive = false)
                }
                if (!_state.value.paused) {
                    dimJob =
                        scope.launch(ExceptionHandler()) {
                            val startDelay =
                                userPreferencesService
                                    .getCurrent()
                                    .appPreferences.interfacePreferences.screensaverPreference.startDelay.milliseconds
                            delay(startDelay)
                            Timber.v("state dim=%s", _state.value)
                            _state.update {
                                it.copy(dimActive = !_state.value.paused)
                            }
                        }
                }
            }
        }

        /**
         * Immediately start the in-app screensaver
         */
        fun start() {
            _state.update {
                it.copy(
                    enabledTemp = true,
                    active = true,
                    dimActive = true,
                )
            }
        }

        /**
         * Immediately stop the in-app screensaver
         */
        fun stop(cancelJob: Boolean) {
            _state.update {
                it.copy(
                    enabledTemp = false,
                    active = false,
                    dimActive = false,
                )
            }
            if (cancelJob) {
                waitJob?.cancel()
                dimJob?.cancel()
                idleJob?.cancel()
            }
        }

        /**
         * After [SYSTEM_SCREENSAVER_DELAY] without input, and with nothing playing, start the system screensaver
         * (the one chosen in the TV settings, e.g. Aerial Views). Called on every key press and when playback stops
         * or pauses.
         */
        private fun restartIdleTimer() {
            idleJob?.cancel()
            idleJob =
                scope.launch(ExceptionHandler()) {
                    delay(SYSTEM_SCREENSAVER_DELAY)
                    onIdleTimeout()
                }
        }

        /** The idle timer ran out: start the system screensaver unless something still needs the screen */
        private fun onIdleTimeout() {
            val owners = synchronized(this) { keepOnOwners.map { it::class.simpleName } }
            when {
                owners.isNotEmpty() -> {
                    // Releasing the last owner starts the timer again
                    Timber.i("Idle for %s, but the screen is kept on by %s: no screensaver", SYSTEM_SCREENSAVER_DELAY, owners)
                }

                playbackReportedRecently() -> {
                    Timber.i(
                        "Idle for %s, but the player reported playback recently: no screensaver, timer re-armed",
                        SYSTEM_SCREENSAVER_DELAY,
                    )
                    restartIdleTimer()
                }

                isAudioPlaying() -> {
                    Timber.i("Idle for %s, but audio is playing: no screensaver, timer re-armed", SYSTEM_SCREENSAVER_DELAY)
                    restartIdleTimer()
                }

                else -> {
                    Timber.i("Idle for %s and nothing is playing, starting the system screensaver", SYSTEM_SCREENSAVER_DELAY)
                    startSystemScreensaver()
                }
            }
        }

        /** Whether any app plays audio (a movie plays its sound on the music stream) */
        private fun isAudioPlaying(): Boolean =
            try {
                context.getSystemService(AudioManager::class.java)?.isMusicActive == true
            } catch (ex: Exception) {
                Timber.w(ex, "Cannot check whether audio is playing")
                false
            }

        @Volatile
        private var lastPlaybackReport: TimeMark? = null

        private fun playbackReportedRecently(): Boolean = lastPlaybackReport?.let { it.elapsedNow() < PLAYBACK_REPORT_INTERVAL * 2 } == true

        /**
         * Called by a player every [PLAYBACK_REPORT_INTERVAL] while it is playing: the idle timer does not start the
         * screensaver for a while, and [owner] keeps the screen on even if a missed player event released it.
         */
        fun reportPlaybackActive(owner: Any) {
            lastPlaybackReport = TimeSource.Monotonic.markNow()
            synchronized(this) {
                if (owner !in keepOnOwners) {
                    Timber.w("%s is playing but did not keep the screen on, acquiring", owner::class.simpleName)
                    acquireKeepScreenOn(owner)
                }
                if (!keepScreenOn.value) {
                    Timber.w("Playing but the screen is not kept on (screensaver enabled=%s)", state.value.enabled)
                    keepScreenOnInternal(true)
                }
            }
            Timber.d("Playback active, keepScreenOn=%s", keepScreenOn.value)
        }

        private fun startSystemScreensaver() {
            try {
                // SystemUI's "start screensaver now" activity, it shows the screensaver selected in the TV settings
                context.startActivity(
                    Intent(Intent.ACTION_MAIN)
                        .setClassName("com.android.systemui", "com.android.systemui.Somnambulator")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (ex: Exception) {
                Timber.w(ex, "Cannot start the system screensaver, falling back to the OS screen timeout")
                systemScreensaverUnavailable = true
                synchronized(this) {
                    keepScreenOnInternal(state.value.enabled || requestedKeepOn)
                }
            }
        }

        /**
         * Signal to the OS for keeping the screen on such as during playback or when the in-app screensaver is active
         */
        fun keepScreenOn(keep: Boolean) {
            if (keep) acquireKeepScreenOn(this) else releaseKeepScreenOn(this)
        }

        /**
         * Keep the screen on until [owner] calls [releaseKeepScreenOn]. With several owners (e.g. a closing player and
         * the next one) the screen stays on until the last one releases, whatever order they are cleared in.
         */
        fun acquireKeepScreenOn(owner: Any) {
            synchronized(this) {
                // A transient release (e.g. a stream switch) armed the idle timer, which must not outlive the playback
                idleJob?.let {
                    if (it.isActive) Timber.d("Keep screen on by %s, cancelling the idle timer", owner::class.simpleName)
                    it.cancel()
                }
                idleJob = null
                keepOnOwners.add(owner)
                applyKeepScreenOn(true)
            }
        }

        fun releaseKeepScreenOn(owner: Any) {
            synchronized(this) {
                if (keepOnOwners.remove(owner) && !requestedKeepOn) {
                    Timber.i("Screen no longer kept on (released by %s), idle timer started", owner::class.simpleName)
                    applyKeepScreenOn(false)
                    // e.g. a paused movie: the screensaver starts 15 minutes after the pause, not after the last key
                    restartIdleTimer()
                }
            }
        }

        private fun applyKeepScreenOn(keep: Boolean) {
            val screensaverEnabled = state.value.enabled
            val dimEnabled = state.value.dimEnabled
            Timber.d("Keep screen on: %s, screensaverEnabled=%s", keep, screensaverEnabled)
            if (screensaverEnabled || dimEnabled) {
                // Page is requesting to keep screen on, so we don't wait to show the screensaver
                _state.update {
                    it.copy(
                        active = false,
                        dimActive = false,
                        paused = keep,
                    )
                }
                if (!keep) {
                    pulse()
                }
            }
            if (!screensaverEnabled) {
                // If in-app screensaver is not enabled, send keep screen on to the OS
                keepScreenOnInternal(keep)
            }
        }

        private fun keepScreenOnInternal(keep: Boolean) {
            // The screen stays on so the idle timer above decides when the screensaver starts, unless that is not
            // possible on this device
            keepScreenOn.update { keep || !systemScreensaverUnavailable }
        }

        /**
         * Create a flow of items to show on the screensaver
         */
        fun createItemFlow(scope: CoroutineScope): Flow<ScreensaverItem?> =
            flow {
                val pager =
                    try {
                        createPager()
                    } catch (ex: Exception) {
                        Timber.e(ex, "Error creating pager for screensaver")
                        emit(ScreensaverItem.Error(ex))
                        return@flow
                    }
                Timber.v("Got %s items", pager.size)
                var index = 0
                if (pager.isEmpty()) {
                    emit(ScreensaverItem.Empty)
                } else {
                    val duration =
                        userPreferencesService
                            .getCurrent()
                            .appPreferences
                            .interfacePreferences.screensaverPreference.duration.milliseconds
                    while (true) {
                        try {
                            val item = pager.getBlocking(index)
                            Timber.v("Next index=%s, item=%s", index, item?.id)
                            if (item != null) {
                                val backdropUrl =
                                    if (item.type == BaseItemKind.PHOTO) {
                                        api.libraryApi.getDownloadUrl(item.id)
                                    } else {
                                        imageUrlService.getItemImageUrl(item, ImageType.BACKDROP)
                                    }
                                val title =
                                    if (item.type == BaseItemKind.PHOTO) {
                                        item.data.premiereDate?.let {
                                            formatDate(it.toLocalDate())
                                        }
                                    } else {
                                        item.title
                                    }
                                val logoUrl = imageUrlService.getItemImageUrl(item, ImageType.LOGO)
                                if (backdropUrl != null) {
                                    context.imageLoader
                                        .enqueue(
                                            ImageRequest
                                                .Builder(context)
                                                .data(backdropUrl)
                                                .build(),
                                        ).job
                                        .await()
                                    emit(
                                        ScreensaverItem.CurrentItem(
                                            item,
                                            backdropUrl,
                                            logoUrl,
                                            title ?: "",
                                        ),
                                    )
                                    delay(duration)
                                }
                            }
                        } catch (_: CancellationException) {
                            break
                        } catch (ex: Exception) {
                            Timber.e(ex, "Error fetching next item")
                            delay(duration)
                        }
                        index++
                        if (index > pager.lastIndex) index = 0
                    }
                }
            }.flowOn(WholphinDispatchers.Default).cancellable()

        private suspend fun createPager(): ApiRequestPager<GetItemsRequest> {
            val prefs =
                userPreferencesService.flow
                    .first()
                    .appPreferences
                    .interfacePreferences.screensaverPreference
            val maxAge = prefs.maxAgeFilter.takeIf { it >= 0 }
            val itemTypes = prefs.itemTypesList.map { BaseItemKind.fromName(it) }
            val request =
                GetItemsRequest(
                    recursive = true,
                    includeItemTypes = itemTypes,
                    imageTypes = if (BaseItemKind.PHOTO in itemTypes) null else listOf(ImageType.BACKDROP),
                    sortBy = listOf(ItemSortBy.RANDOM),
                    maxOfficialRating = maxAge?.toString(),
                    hasParentalRating = maxAge?.let { true },
                )
            return ApiRequestPager(api, request, GetItemsRequestHandler, scope).init()
        }

        companion object {
            val SYSTEM_SCREENSAVER_DELAY = 15.minutes

            /** How often a playing player calls [reportPlaybackActive] */
            val PLAYBACK_REPORT_INTERVAL = 5.minutes

            val enterAnimation = fadeIn(animationSpec = tween(durationMillis = 1000))
            val exitAnimation = fadeOut(animationSpec = tween(durationMillis = 500))
        }
    }

data class ScreensaverState(
    val enabled: Boolean,
    val enabledTemp: Boolean,
    val active: Boolean,
    val paused: Boolean,
    val dimEnabled: Boolean = false,
    val dimActive: Boolean = false,
) {
    val show get() = (enabled || enabledTemp) && active && !paused

    val showDim get() = dimEnabled && dimActive && !paused
}

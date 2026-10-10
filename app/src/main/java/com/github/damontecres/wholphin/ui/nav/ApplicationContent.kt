package com.github.damontecres.wholphin.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import androidx.tv.material3.DrawerValue
import androidx.tv.material3.rememberDrawerState
import com.github.damontecres.wholphin.data.model.JellyfinServer
import com.github.damontecres.wholphin.data.model.JellyfinUser
import com.github.damontecres.wholphin.preferences.BackdropStyle
import com.github.damontecres.wholphin.preferences.UserPreferences
import com.github.damontecres.wholphin.preferences.toBackdropRotateMinutes
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.RandomBackdropService
import com.github.damontecres.wholphin.ui.components.CsfdRatingPrompt
import com.github.damontecres.wholphin.ui.components.CsfdRatingPromptViewModel
import com.github.damontecres.wholphin.ui.components.ErrorMessage
import com.github.damontecres.wholphin.ui.launchIO
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import org.jellyfin.sdk.model.api.BaseItemKind
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

// Top scrim configuration for text readability (clock, season tabs)
const val TOP_SCRIM_ALPHA = 0.55f
const val TOP_SCRIM_END_FRACTION = 0.25f // Fraction of backdrop image height

// Extra dimming of the random fallback backdrop so text on top stays readable
const val RANDOM_BACKDROP_DIM_ALPHA = 0.35f

@HiltViewModel
class ApplicationContentViewModel
    @Inject
    constructor(
        val backdropService: BackdropService,
        val randomBackdropService: RandomBackdropService,
    ) : ViewModel() {
        fun clearBackdrop() {
            viewModelScope.launchIO { backdropService.clearBackdrop() }
        }
    }

/**
 * Whether the destination either draws its own full screen content (so a fallback backdrop would never be visible)
 * or is the details of a specific item which shows that item's backdrop (avoids flashing a random one while loading)
 */
private val Destination?.hidesBackdrop: Boolean
    get() =
        when (this) {
            is Destination.Playback,
            is Destination.PlaybackList,
            is Destination.Slideshow,
            is Destination.NowPlaying,
            is Destination.SeriesOverview,
            is Destination.DiscoveredItem,
            -> true

            is Destination.MediaItem -> type in itemDetailTypes

            else -> false
        }

private val itemDetailTypes =
    setOf(
        BaseItemKind.MOVIE,
        BaseItemKind.SERIES,
        BaseItemKind.SEASON,
        BaseItemKind.EPISODE,
        BaseItemKind.VIDEO,
        BaseItemKind.MUSIC_VIDEO,
    )

/**
 * This is generally the root composable of the of the app
 *
 * Here the navigation backstack is used and pages are rendered in the nav drawer or full screen
 */
@Composable
fun ApplicationContent(
    server: JellyfinServer,
    user: JellyfinUser,
    navigationManager: NavigationManager,
    preferences: UserPreferences,
    modifier: Modifier = Modifier,
    enableTopScrim: Boolean = true,
    viewModel: ApplicationContentViewModel = hiltViewModel(),
    ratingPromptViewModel: CsfdRatingPromptViewModel = hiltViewModel(),
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    Box(
        modifier = modifier,
    ) {
        val backdropStyle = preferences.appPreferences.interfacePreferences.backdropStyle
        val topDestination = navigationManager.backStack.lastOrNull()
        val itemBackdrop by viewModel.backdropService.backdropFlow.collectAsStateWithLifecycle()
        val randomBackdrop by viewModel.randomBackdropService.current.collectAsStateWithLifecycle()
        // When the page doesn't show a specific item's backdrop, fallback to a random one from the library
        val useRandom =
            itemBackdrop.itemId == null &&
                backdropStyle != BackdropStyle.BACKDROP_NONE &&
                !topDestination.hidesBackdrop
        val useRandomState by rememberUpdatedState(useRandom)
        val rotateInterval =
            preferences.appPreferences.interfacePreferences.backdropRotateMinutes
                .toBackdropRotateMinutes()
                .minutes
        // Change the random backdrop when navigating to another page
        LaunchedEffect(topDestination, backdropStyle) {
            if (backdropStyle == BackdropStyle.BACKDROP_NONE || topDestination.hidesBackdrop) return@LaunchedEffect
            viewModel.randomBackdropService.next()
        }
        // And periodically while idle; restarted when the interval setting changes so it applies immediately
        LaunchedEffect(topDestination, backdropStyle, rotateInterval) {
            if (backdropStyle == BackdropStyle.BACKDROP_NONE || topDestination.hidesBackdrop) return@LaunchedEffect
            while (true) {
                delay(rotateInterval)
                if (useRandomState) {
                    viewModel.randomBackdropService.next()
                }
            }
        }
        val random = randomBackdrop?.takeIf { useRandom }
        Backdrop(
            backdrop = random ?: itemBackdrop,
            drawerIsOpen = drawerState.isOpen,
            backdropStyle = backdropStyle,
            enableTopScrim = enableTopScrim,
            dimAlpha = if (random != null) RANDOM_BACKDROP_DIM_ALPHA else 0f,
        )
        val navDrawerListState = rememberLazyListState()
        NavDisplay(
            backStack = navigationManager.backStack,
            onBack = { navigationManager.goBack() },
            entryDecorators =
                listOf(
                    rememberSaveableStateHolderNavEntryDecorator(),
                    rememberViewModelStoreNavEntryDecorator(),
                ),
            entryProvider = { key ->
                key as Destination
                val contentKey = "${key}_${server?.id}_${user?.id}"
                NavEntry(key, contentKey = contentKey) {
                    if (key.fullScreen) {
                        DestinationContent(
                            destination = key,
                            preferences = preferences,
                            onClearBackdrop = viewModel::clearBackdrop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else if (user != null && server != null) {
                        NavDrawer(
                            destination = key,
                            preferences = preferences,
                            user = user,
                            server = server,
                            drawerState = drawerState,
                            navDrawerListState = navDrawerListState,
                            onClearBackdrop = viewModel::clearBackdrop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        ErrorMessage("Trying to go to $key without a user logged in", null)
                    }
                }
            },
        )
        // Asks for a ČSFD rating of a finished movie, only once the player is gone
        CsfdRatingPrompt(
            show = topDestination !is Destination.Playback && topDestination !is Destination.PlaybackList,
            viewModel = ratingPromptViewModel,
        )
    }
}

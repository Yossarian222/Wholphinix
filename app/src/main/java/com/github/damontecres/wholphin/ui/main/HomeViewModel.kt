package com.github.damontecres.wholphin.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.data.model.HomeRowConfig
import com.github.damontecres.wholphin.data.model.HomeRowViewOptions
import com.github.damontecres.wholphin.data.model.ServerUserConfig
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.preferences.HomePagePreferences
import com.github.damontecres.wholphin.services.BackdropService
import com.github.damontecres.wholphin.services.CsfdRowUnavailableException
import com.github.damontecres.wholphin.services.CsfdTvTipsService
import com.github.damontecres.wholphin.services.DatePlayedService
import com.github.damontecres.wholphin.services.FavoriteWatchManager
import com.github.damontecres.wholphin.services.HomeCache
import com.github.damontecres.wholphin.services.HomePageResolvedSettings
import com.github.damontecres.wholphin.services.HomeRowConfigDisplay
import com.github.damontecres.wholphin.services.HomeSettingsService
import com.github.damontecres.wholphin.services.LatestNextUpService
import com.github.damontecres.wholphin.services.MediaManagementService
import com.github.damontecres.wholphin.services.NavDrawerService
import com.github.damontecres.wholphin.services.NavigationManager
import com.github.damontecres.wholphin.services.ServerReportService
import com.github.damontecres.wholphin.services.UserPreferencesService
import com.github.damontecres.wholphin.services.deleteItem
import com.github.damontecres.wholphin.services.hilt.IoCoroutineScope
import com.github.damontecres.wholphin.ui.collectLatestIn
import com.github.damontecres.wholphin.ui.combinePair
import com.github.damontecres.wholphin.ui.data.RowColumn
import com.github.damontecres.wholphin.ui.launchDefault
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.main.settings.Library
import com.github.damontecres.wholphin.ui.seasonal.Holiday
import com.github.damontecres.wholphin.ui.seasonal.activeHoliday
import com.github.damontecres.wholphin.ui.seasonal.rowTitle
import com.github.damontecres.wholphin.ui.showToast
import com.github.damontecres.wholphin.ui.util.EmptyStringProvider
import com.github.damontecres.wholphin.ui.util.ResStringProvider
import com.github.damontecres.wholphin.util.ExceptionHandler
import com.github.damontecres.wholphin.util.HomeRowLoadingState
import com.github.damontecres.wholphin.util.LoadingState
import com.github.damontecres.wholphin.util.WholphinDispatchers
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        val navigationManager: NavigationManager,
        val serverRepository: ServerRepository,
        val serverReportService: ServerReportService,
        private val navDrawerService: NavDrawerService,
        private val homeSettingsService: HomeSettingsService,
        private val favoriteWatchManager: FavoriteWatchManager,
        private val datePlayedService: DatePlayedService,
        private val backdropService: BackdropService,
        private val userPreferencesService: UserPreferencesService,
        private val mediaManagementService: MediaManagementService,
        private val latestNextUpService: LatestNextUpService,
        private val csfdTvTipsService: CsfdTvTipsService,
        private val homeCache: HomeCache,
        @param:IoCoroutineScope private val homeCacheScope: CoroutineScope,
    ) : ViewModel() {
        private val _state = MutableStateFlow(HomeState.EMPTY)
        val state: StateFlow<HomeState> = _state

        private var dataLoadingJob: Job? = null

        init {
            datePlayedService.invalidateAll()
            viewModelScope.launch {
                csfdTvTipsService.rowUpdates.collect { refreshCsfdRows() }
            }
            serverRepository.currentUserDtoFlow
                .combinePair(homeSettingsService.currentSettings)
                .collectLatestIn(viewModelScope) { (userDto, settings) ->
                    Timber.v(
                        "Got new userDto & settings, userId=%s, settings?=%s",
                        userDto?.id,
                        settings != HomePageResolvedSettings.EMPTY,
                    )
//                    Timber.v("userDto=%s", userDto)
                    dataLoadingJob?.cancel()
                    if (userDto == null) {
                        Timber.d("UserDto is null")
                        _state.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (settings == HomePageResolvedSettings.EMPTY) {
                        Timber.d("Home settings are empty")
                        _state.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (userDto.id != settings.userId) {
                        Timber.d("User IDs don't match: %s vs %s", userDto?.id, settings.userId)
                        _state.update { HomeState.EMPTY }
                        return@collectLatestIn
                    }
                    if (state.value.settings.userId != settings.userId) {
                        Timber.d("User changed")
                        _state.update { HomeState.EMPTY }
                    }
                    dataLoadingJob =
                        viewModelScope.launchIO {
                            loadHomeRows(userDto, settings)
                        }
                }
        }

        fun init() {
            viewModelScope.launchIO {
                Timber.d("init HomeViewModel")
                serverRepository.currentUserDto?.let { userDto ->
                    if (dataLoadingJob?.isActive == false) {
                        val settings =
                            homeSettingsService.currentSettings.first { it != HomePageResolvedSettings.EMPTY }
                        if (userDto.id == settings.userId) {
                            dataLoadingJob =
                                viewModelScope.launchIO {
                                    loadHomeRows(userDto, settings)
                                }
                        } else {
                            Timber.d(
                                "Init user IDs don't match: %s vs %s",
                                userDto.id,
                                settings.userId,
                            )
                        }
                    } else {
                        Timber.v("Data loading job is active")
                    }
                }
            }
        }

        suspend fun loadHomeRows(
            userDto: ServerUserConfig,
            settings: HomePageResolvedSettings,
        ) {
            Timber.i("Starting loadHomeRows")
            try {
                val preferences = userPreferencesService.getCurrent()
                val prefs = preferences.appPreferences.homePagePreferences
                // During a holiday period (or its preview) the seasonal recommendations are the first row, they are
                // not one of the saved home rows
                val seasonal = activeHoliday(preferences.appPreferences.interfacePreferences, LocalDate.now())
                val rowConfigs = settings.rows.map { it.config }

                val state = state.value

                // Refreshing if a load has already occurred and the rows haven't significantly changed
                val refresh =
                    state.loadingState == LoadingState.Success && state.settings == settings && state.seasonal == seasonal
                Timber.v(
                    "refresh=%s, state.loadingState=%s, %s rows",
                    refresh,
                    state.loadingState,
                    settings.rows.size,
                )
                if (!refresh) {
                    // Show the rows from the last time right away (or placeholders), then replace them as they load,
                    // so no row waits for the slowest one (eg the ČSFD rows)
                    val pending = { index: Int ->
                        if (seasonal != null && index == 0) {
                            HomeRowLoadingState.Pending(ResStringProvider(seasonal.rowTitle))
                        } else {
                            HomeRowLoadingState.Pending(
                                settings.rows.getOrNull(index - if (seasonal != null) 1 else 0)?.title
                                    ?: EmptyStringProvider,
                            )
                        }
                    }
                    val cached =
                        HomeCache.restoreRows(homeCache.get(userDto.id), rowConfigs, seasonal, pending)
                    Timber.v("Restored cached home rows: %s", cached != null)
                    _state.update {
                        it.copy(
                            loadingState = LoadingState.Success,
                            refreshState = LoadingState.Loading,
                            settings = settings,
                            seasonal = seasonal,
                            homeRows = cached ?: List(rowConfigs.size + if (seasonal != null) 1 else 0, pending),
                        )
                    }
                } else {
                    _state.update { it.copy(refreshState = LoadingState.Loading) }
                }

                val libraries =
                    navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)

                val semaphore = Semaphore(4)

                val deferred =
                    settings.rows
                        .map { row ->
                            viewModelScope.async(WholphinDispatchers.IO) {
                                semaphore.withPermit {
                                    fetchRow(row, prefs, userDto, libraries, refresh)
                                }
                            }
                        }

                val seasonalDeferred: Deferred<HomeRowLoadingState>? =
                    seasonal?.let { holiday ->
                        viewModelScope.async(WholphinDispatchers.IO) { loadSeasonalRow(holiday, userDto.id) }
                    }
                val allDeferred = listOfNotNull(seasonalDeferred) + deferred

                // Replace rows as they complete
                val remaining = allDeferred.withIndex().toMutableList()
                while (remaining.isNotEmpty()) {
                    val (rowIndex, rowData) =
                        select {
                            // "Return" the first remaining that is completed
                            remaining
                                .forEach { (rowIndex, deferred) ->
                                    deferred.onAwait { rowIndex to it }
                                }
                        }
                    Timber.v("Got row data index=%s", rowIndex)
                    remaining.removeIf { it.index == rowIndex }
                    updateRow(rowIndex, rowData)
                }
                _state.update {
                    it.copy(
                        loadingState = LoadingState.Success,
                        refreshState = LoadingState.Success,
                    )
                }
                saveCache(userDto.id, toDisk = true)
                Timber.d("Home page load complete")
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                Timber.e(ex, "Exception during home page loading")
                if (state.value.loadingState == LoadingState.Success &&
                    state.value.homeRows.any { it is HomeRowLoadingState.Success }
                ) {
                    showToast(context, "Error refreshing home: ${ex.localizedMessage}")
                    _state.update { it.copy(refreshState = LoadingState.Error(ex)) }
                } else {
                    _state.update {
                        it.copy(loadingState = LoadingState.Error(ex))
                    }
                }
            }
        }

        private suspend fun fetchRow(
            row: HomeRowConfigDisplay,
            prefs: HomePagePreferences,
            userDto: ServerUserConfig,
            libraries: List<Library>,
            isRefresh: Boolean,
        ): HomeRowLoadingState {
            Timber.v("Fetching row: %s", row)
            return try {
                homeSettingsService.fetchDataForRow(
                    row = row.config,
                    scope = viewModelScope,
                    prefs = prefs,
                    userDto = userDto,
                    libraries = libraries,
                    limit = prefs.maxItemsPerRow,
                    isRefresh = isRefresh,
                    csfdThrowIfUnavailable = true,
                )
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: InvalidStatusException) {
                if (ex.status == 404) {
                    Timber.w(ex, "404 on row %s", row)
                    HomeRowLoadingState.Success(
                        row.title,
                        emptyList(),
                    )
                } else {
                    Timber.e(
                        ex,
                        "Error %s on row %s",
                        ex.status,
                        row,
                    )
                    HomeRowLoadingState.Error(
                        row.title,
                        exception = ex,
                    )
                }
            } catch (ex: CsfdRowUnavailableException) {
                Timber.i("Row not available yet: %s", row)
                HomeRowLoadingState.Error(row.title, exception = ex)
            } catch (ex: Exception) {
                Timber.e(ex, "Error on row %s", row)
                HomeRowLoadingState.Error(
                    row.title,
                    exception = ex,
                )
            }
        }

        /**
         * Replaces one row, keeping the previous version if the new one failed, and remembers the rows for the next time
         */
        private fun updateRow(
            rowIndex: Int,
            rowData: HomeRowLoadingState,
        ) {
            _state.update { state ->
                if (rowIndex !in state.homeRows.indices) return@update state
                val newRows =
                    state.homeRows.toMutableList().apply {
                        set(rowIndex, HomeCache.mergeRow(get(rowIndex), rowData))
                    }
                state.copy(homeRows = newRows)
            }
            saveCache(state.value.settings.userId, toDisk = false)
        }

        private fun saveCache(
            userId: UUID,
            toDisk: Boolean,
        ) {
            val state = state.value
            if (state.settings.userId != userId || state.loadingState != LoadingState.Success) return
            val home = HomeCache.toCache(state.homeRows, state.settings.rows.map { it.config }, state.seasonal)
            if (toDisk) {
                // Not in viewModelScope, so leaving the page does not stop the write
                homeCacheScope.launch { homeCache.put(userId, home) }
            } else {
                homeCache.putInMemory(userId, home)
            }
        }

        /**
         * A ČSFD row finished loading in the background after the home page stopped waiting for it, so fetch the ČSFD
         * rows again (which is fast now) to show it
         */
        private fun refreshCsfdRows() {
            viewModelScope.launchIO {
                val state = state.value
                val userDto = serverRepository.currentUserDto ?: return@launchIO
                if (state.loadingState != LoadingState.Success || state.settings.userId != userDto.id) return@launchIO
                val prefs = userPreferencesService.getCurrent().appPreferences.homePagePreferences
                val offset = if (state.seasonal != null) 1 else 0
                // The page may have been reloaded with other rows in the meantime
                val unchanged = { current: HomeState -> current.settings == state.settings && current.seasonal == state.seasonal }
                state.seasonal?.let { holiday ->
                    val rowData = loadSeasonalRow(holiday, userDto.id)
                    if (unchanged(this@HomeViewModel.state.value)) updateRow(0, rowData)
                }
                val csfdRows =
                    state.settings.rows.withIndex().filter { (_, row) ->
                        row.config is HomeRowConfig.CsfdTvTips || row.config is HomeRowConfig.CsfdWatchlist
                    }
                if (csfdRows.isNotEmpty()) {
                    val libraries = navDrawerService.getAllUserLibraries(userDto.id, userDto.tvAccess)
                    csfdRows.forEach { (index, row) ->
                        val rowData = fetchRow(row, prefs, userDto, libraries, isRefresh = true)
                        if (unchanged(this@HomeViewModel.state.value)) {
                            updateRow(index + offset, rowData)
                        }
                    }
                }
                saveCache(userDto.id, toDisk = true)
            }
        }

        /**
         * The seasonal recommendations row from the ČSFD plugin; empty (so not shown) if the plugin does not have them
         */
        private suspend fun loadSeasonalRow(
            holiday: Holiday,
            userId: UUID,
        ): HomeRowLoadingState {
            val title = ResStringProvider(holiday.rowTitle)
            val viewOptions = HomeRowViewOptions.csfdTipsDefault
            return try {
                val items =
                    csfdTvTipsService.getSeasonalRowItems(
                        event = holiday.key,
                        userId = userId,
                        useSeries = viewOptions.useSeries,
                        throwIfUnavailable = true,
                    )
                HomeRowLoadingState.Success(title, items, viewOptions, rowType = null, showViewMore = false)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: CsfdRowUnavailableException) {
                // Keeps the previous version, if any
                HomeRowLoadingState.Error(title, exception = ex)
            } catch (ex: Exception) {
                Timber.w(ex, "Seasonal row %s failed", holiday)
                HomeRowLoadingState.Success(title, listOf(), viewOptions, rowType = null, showViewMore = false)
            }
        }

        fun setWatched(
            itemId: UUID,
            played: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setWatched(itemId, played)
            withContext(WholphinDispatchers.Main) {
                init()
            }
        }

        fun setFavorite(
            itemId: UUID,
            favorite: Boolean,
        ) = viewModelScope.launch(ExceptionHandler() + WholphinDispatchers.IO) {
            favoriteWatchManager.setFavorite(itemId, favorite)
            withContext(WholphinDispatchers.Main) {
                init()
            }
        }

        fun updateBackdrop(item: BaseItem) {
            viewModelScope.launchIO {
                backdropService.submit(item)
            }
        }

        fun deleteItem(
            position: RowColumn,
            item: BaseItem,
        ) {
            deleteItem(context, mediaManagementService, item) {
                viewModelScope.launchDefault {
                    val row = state.value.homeRows.getOrNull(position.row)
                    if (row is HomeRowLoadingState.Success) {
                        _state.update {
                            val newRow =
                                row.items.toMutableList().apply {
                                    removeAt(position.column)
                                }
                            it.copy(
                                homeRows =
                                    it.homeRows.toMutableList().apply {
                                        set(position.row, row.copy(items = newRow))
                                    },
                            )
                        }
                        saveCache(state.value.settings.userId, toDisk = false)
                    }
                }
            }
        }

        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
        ): Boolean = mediaManagementService.canDelete(item, appPreferences)

        fun removeFromNextUp(item: BaseItem) {
            if (item.type == BaseItemKind.EPISODE) {
                viewModelScope.launchDefault {
                    serverRepository.currentUser?.id?.let { userId ->
                        latestNextUpService.removeFromNextUp(userId, item)
                        init()
                    }
                }
            } else {
                Timber.w("Item is not an episode %s", item.id)
            }
        }
    }

data class HomeState(
    val loadingState: LoadingState,
    val refreshState: LoadingState,
    val homeRows: List<HomeRowLoadingState>,
    val settings: HomePageResolvedSettings,
    /** The holiday whose seasonal row is first in [homeRows], if any */
    val seasonal: Holiday? = null,
) {
    companion object {
        val EMPTY =
            HomeState(
                LoadingState.Pending,
                LoadingState.Pending,
                emptyList(),
                HomePageResolvedSettings.EMPTY,
            )
    }
}

/**
 * Whether a row is a "is watching" type
 */
private fun isWatchingRow(row: HomeRowConfig) =
    row is HomeRowConfig.ContinueWatching ||
        row is HomeRowConfig.NextUp ||
        row is HomeRowConfig.ContinueWatchingCombined

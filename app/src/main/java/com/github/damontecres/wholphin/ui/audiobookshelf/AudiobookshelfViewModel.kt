package com.github.damontecres.wholphin.ui.audiobookshelf

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.audiobookshelf.AbsConfig
import com.github.damontecres.wholphin.services.audiobookshelf.AudiobookshelfService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/** Connection settings of the Audiobookshelf page; the library itself is in [AbsBrowseViewModel] */
data class AbsUiState(
    val configured: Boolean = false,
    val showSettings: Boolean = false,
    val settingsError: String? = null,
    /** Positive result of the connection test in the settings dialog */
    val settingsInfo: String? = null,
    /** A connection test or save is running */
    val testing: Boolean = false,
    val config: AbsConfig = AbsConfig(),
)

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

        /** Loads the saved settings; the library reloads by itself when they change */
        fun load() {
            viewModelScope.launch {
                val cfg = service.config.first()
                config = cfg
                _state.update { it.copy(configured = cfg.isComplete, config = cfg) }
                if (!cfg.isComplete) {
                    // Nothing to show yet: open the connection form right away
                    _state.update { it.copy(showSettings = true, settingsError = null, settingsInfo = null) }
                }
            }
        }

        fun openSettings() {
            _state.update { it.copy(showSettings = true, settingsError = null, settingsInfo = null) }
        }

        fun closeSettings() {
            _state.update { it.copy(showSettings = false) }
        }

        /** Tests the entered settings without saving them */
        fun testSettings(draft: AbsConfig) {
            if (!draft.isComplete) {
                _state.update {
                    it.copy(settingsError = context.getString(R.string.abs_settings_incomplete), settingsInfo = null)
                }
                return
            }
            viewModelScope.launch {
                _state.update { it.copy(testing = true, settingsError = null, settingsInfo = null) }
                try {
                    val (conn, libraries) = service.withConnection(draft) { c -> service.libraries(c) }
                    val podcastLibraries = libraries.libraries.count { it.mediaType == "podcast" }
                    _state.update {
                        it.copy(
                            testing = false,
                            settingsInfo =
                                context.getString(
                                    R.string.abs_settings_test_ok,
                                    conn.baseUrl,
                                    libraries.libraries.size,
                                    podcastLibraries,
                                ),
                        )
                    }
                } catch (ex: Exception) {
                    Timber.w(ex, "Audiobookshelf connection test failed")
                    _state.update {
                        it.copy(
                            testing = false,
                            settingsError = ex.message ?: context.getString(R.string.abs_settings_test_failed),
                        )
                    }
                }
            }
        }

        /** Tests the entered settings, saves them if the server answers, then reloads */
        fun saveSettings(draft: AbsConfig) {
            if (!draft.isComplete) {
                _state.update {
                    it.copy(settingsError = context.getString(R.string.abs_settings_incomplete), settingsInfo = null)
                }
                return
            }
            viewModelScope.launch {
                _state.update { it.copy(testing = true, settingsError = null, settingsInfo = null) }
                try {
                    service.withConnection(draft) { c -> service.libraries(c) }
                    service.save(draft)
                    config = draft
                    _state.update {
                        it.copy(
                            showSettings = false,
                            testing = false,
                            settingsError = null,
                            config = draft,
                            configured = draft.isComplete,
                        )
                    }
                    load()
                } catch (ex: Exception) {
                    Timber.w(ex, "Audiobookshelf settings test failed")
                    _state.update {
                        it.copy(
                            testing = false,
                            settingsError = ex.message ?: context.getString(R.string.abs_settings_test_failed),
                        )
                    }
                }
            }
        }
    }

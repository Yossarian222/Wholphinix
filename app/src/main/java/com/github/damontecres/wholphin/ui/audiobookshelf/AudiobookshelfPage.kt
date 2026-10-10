package com.github.damontecres.wholphin.ui.audiobookshelf

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.audiobookshelf.AbsConfig
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.components.EditTextBox
import com.github.damontecres.wholphin.ui.tryRequestFocus

/**
 * Audiobookshelf podcasts and audiobooks, shown like a Jellyfin library (see [AbsBrowser]).
 * Colors come from the active Wholphin theme.
 */
@Composable
fun AudiobookshelfPage(
    modifier: Modifier = Modifier,
    viewModel: AudiobookshelfViewModel = hiltViewModel(),
) {
    LaunchedEffect(Unit) { viewModel.load() }
    val state by viewModel.state.collectAsState()

    if (state.configured) {
        AbsBrowser(
            onOpenSettings = viewModel::openSettings,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        Box(modifier = modifier.fillMaxSize().padding(16.dp)) {
            NotConfigured(onSetUp = viewModel::openSettings)
        }
    }

    if (state.showSettings) {
        SettingsDialog(
            config = state.config,
            error = state.settingsError,
            info = state.settingsInfo,
            testing = state.testing,
            onTest = viewModel::testSettings,
            onSave = viewModel::saveSettings,
            onDismiss = viewModel::closeSettings,
        )
    }
}

/** Shown instead of the podcasts until a connection is saved */
@Composable
private fun NotConfigured(onSetUp: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.abs_not_configured),
            style = MaterialTheme.typography.bodyLarge,
        )
        Text(
            text = stringResource(R.string.abs_not_configured_hint),
            style = MaterialTheme.typography.bodyMedium,
        )
        Button(
            onClick = onSetUp,
            modifier = Modifier.focusRequester(focusRequester),
        ) { Text(stringResource(R.string.abs_set_up_connection)) }
    }
    LaunchedEffect(Unit) { focusRequester.tryRequestFocus() }
}

@Composable
private fun SettingsDialog(
    config: AbsConfig,
    error: String?,
    info: String?,
    testing: Boolean,
    onTest: (AbsConfig) -> Unit,
    onSave: (AbsConfig) -> Unit,
    onDismiss: () -> Unit,
) {
    val lan = rememberTextFieldState(config.lanUrl)
    val tailscale = rememberTextFieldState(config.tailscaleUrl)
    val token = rememberTextFieldState(config.token)

    fun draft() =
        AbsConfig(
            lanUrl = lan.text.toString().trim(),
            tailscaleUrl = tailscale.text.toString().trim(),
            token = token.text.toString().trim(),
        )

    BasicDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier.widthIn(min = 420.dp).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = stringResource(R.string.abs_settings_title), style = MaterialTheme.typography.titleLarge)
            Text(text = stringResource(R.string.abs_settings_lan_url), style = MaterialTheme.typography.bodySmall)
            EditTextBox(state = lan)
            Text(text = stringResource(R.string.abs_settings_tailscale_url), style = MaterialTheme.typography.bodySmall)
            EditTextBox(state = tailscale)
            Text(text = stringResource(R.string.abs_settings_token), style = MaterialTheme.typography.bodySmall)
            EditTextBox(state = token, isPassword = true)
            if (testing) {
                Text(text = stringResource(R.string.abs_settings_testing), style = MaterialTheme.typography.bodySmall)
            }
            info?.let {
                Text(text = it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
            error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(
                    onClick = { onTest(draft()) },
                    enabled = !testing,
                ) { Text(stringResource(R.string.abs_settings_test)) }
                Button(
                    onClick = { onSave(draft()) },
                    enabled = !testing,
                ) { Text(stringResource(R.string.save)) }
            }
        }
    }
}

package com.github.damontecres.wholphin.ui.detail

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.tv.material3.ListItem
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.services.JeanBubble
import com.github.damontecres.wholphin.services.JeanHistoryService
import com.github.damontecres.wholphin.services.TvMessageSource
import com.github.damontecres.wholphin.ui.components.GridTitle
import com.github.damontecres.wholphin.ui.ifElse
import com.github.damontecres.wholphin.ui.tryRequestFocus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

@HiltViewModel
class JeanHistoryViewModel
    @Inject
    constructor(
        jeanHistoryService: JeanHistoryService,
    ) : ViewModel() {
        val entries = jeanHistoryService.entries
    }

private val timeFormat = DateTimeFormatter.ofPattern("d. M. HH:mm")

/**
 * "História hlášok" (#47): the last bubbles of Jean and the messages from Telka, newest first
 */
@Composable
fun JeanHistoryPage(
    modifier: Modifier = Modifier,
    viewModel: JeanHistoryViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsState()
    val firstFocus = remember { FocusRequester() }
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        GridTitle(stringResource(R.string.jean_history))
        if (entries.isEmpty()) {
            LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
            Text(
                text = stringResource(R.string.jean_history_empty),
                style = MaterialTheme.typography.bodyLarge,
                modifier =
                    Modifier
                        .padding(32.dp)
                        .focusRequester(firstFocus)
                        .focusable(),
            )
        } else {
            LaunchedEffect(Unit) { firstFocus.tryRequestFocus() }
            LazyColumn(
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(entries) { index, entry ->
                    JeanHistoryItem(
                        entry = entry,
                        modifier = Modifier.ifElse(index == 0, Modifier.focusRequester(firstFocus)),
                    )
                }
            }
        }
    }
}

@Composable
private fun JeanHistoryItem(
    entry: JeanBubble,
    modifier: Modifier = Modifier,
) {
    val time =
        remember(entry.time) {
            timeFormat.format(Instant.ofEpochMilli(entry.time).atZone(ZoneId.systemDefault()))
        }
    val source =
        if (entry.source == TvMessageSource.TELKA.key) {
            stringResource(R.string.jean_history_from_telka)
        } else {
            entry.title ?: stringResource(R.string.jean_history_from_jean)
        }
    ListItem(
        selected = false,
        onClick = {},
        overlineContent = {
            Text(listOfNotNull(time, source, entry.media).joinToString(" · "))
        },
        headlineContent = {
            Text(
                text = entry.text,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        modifier = modifier.fillMaxWidth(),
    )
}

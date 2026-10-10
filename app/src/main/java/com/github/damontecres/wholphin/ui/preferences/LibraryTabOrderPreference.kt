package com.github.damontecres.wholphin.ui.preferences

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.ui.components.BasicDialog
import com.github.damontecres.wholphin.ui.detail.LibraryTab
import com.github.damontecres.wholphin.ui.detail.moveByOne

/**
 * A preference which opens a dialog to reorder the tabs of a movie or TV library.
 *
 * The first tab in the order is opened when entering the library.
 *
 * @param tabs the tabs in the current order
 * @param onSave called with the new order when the dialog is closed and the order changed
 */
@Composable
fun LibraryTabOrderPreference(
    title: String,
    summary: String?,
    tabs: List<LibraryTab>,
    onSave: (List<LibraryTab>) -> Unit,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
) {
    var showDialog by remember { mutableStateOf(false) }
    ClickPreference(
        title = title,
        summary = summary,
        onClick = { showDialog = true },
        interactionSource = interactionSource,
        modifier = modifier,
    )
    if (showDialog) {
        var order by remember(tabs) { mutableStateOf(tabs) }
        LibraryTabOrderDialog(
            title = title,
            tabs = order,
            onMove = { index, up -> order = order.moveByOne(index, up) },
            onDismissRequest = {
                showDialog = false
                if (order != tabs) onSave(order)
            },
        )
    }
}

@Composable
private fun LibraryTabOrderDialog(
    title: String,
    tabs: List<LibraryTab>,
    onMove: (index: Int, up: Boolean) -> Unit,
    onDismissRequest: () -> Unit,
) {
    BasicDialog(
        onDismissRequest = onDismissRequest,
        elevation = 3.dp,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                itemsIndexed(tabs, key = { _, tab -> tab.key }) { index, tab ->
                    NavDrawerPreferenceListItem(
                        title = stringResource(tab.title),
                        pinned = true,
                        moveUpAllowed = index > 0,
                        moveDownAllowed = index < tabs.lastIndex,
                        onClick = {},
                        onMoveUp = { onMove(index, true) },
                        onMoveDown = { onMove(index, false) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}

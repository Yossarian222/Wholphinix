package com.github.damontecres.wholphin.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.formatDateTime
import org.jellyfin.sdk.model.DateTime

/**
 * The full premiere date, e.g. "Premiere: 12. 3. 2024" (formatted for the current locale); nothing if unknown.
 * The years (and for series the end year or "present") are already part of the quick details.
 */
@Composable
fun PremiereDateText(
    premiereDate: DateTime?,
    modifier: Modifier = Modifier,
) {
    if (premiereDate == null) return
    val formatted = remember(premiereDate) { formatDateTime(premiereDate) }
    Text(
        text = stringResource(R.string.premiere_date, formatted),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        modifier = modifier,
    )
}

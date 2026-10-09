package com.github.damontecres.wholphin.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.util.LocalClock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * Displays the [LocalClock] in the upper right corner of the parent [androidx.compose.foundation.layout.Box]
 */
@Composable
fun BoxScope.TimeDisplay(modifier: Modifier = Modifier) {
    val timeString by LocalClock.current.timeString
    Text(
        text = timeString,
        fontSize = 18.sp,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyLarge,
        modifier =
            modifier
                .align(Alignment.TopEnd)
                .padding(vertical = 16.dp, horizontal = 24.dp),
    )
}

/**
 * Formats the date line shown above the clock, eg `piatok 9. 10. 2026` in Slovak
 */
fun formatHeaderDate(
    date: LocalDate,
    locale: Locale,
): String {
    val formatter =
        if (locale.language == "sk" || locale.language == "cs") {
            DateTimeFormatter.ofPattern("EEEE d. M. yyyy", locale)
        } else {
            DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(locale)
        }
    return date.format(formatter)
}

/**
 * The block in the upper right corner of the parent [androidx.compose.foundation.layout.Box] on pages with the nav drawer
 *
 * Shows the date (smaller) above a row with a search (magnifier) button and the [LocalClock] time.
 * Both lines are right-aligned so their right edges line up.
 *
 * @param showClock whether to show the date & time, the search button is always shown
 * @param onSearchClick called when the search button is clicked
 */
@Composable
fun BoxScope.TopRightHeader(
    showClock: Boolean,
    onSearchClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeString by LocalClock.current.timeString
    val now by LocalClock.current.now
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current)[0] ?: Locale.getDefault()
    val date = now.toLocalDate()
    val dateString = remember(date, locale) { formatHeaderDate(date, locale) }
    Column(
        horizontalAlignment = Alignment.End,
        // Room under the date so the focused (enlarged) search circle does not overlap it
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier =
            modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = HeaderEndPadding),
    ) {
        if (showClock) {
            Text(
                text = dateString,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = .85f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Surface(
                onClick = onSearchClick,
                shape = ClickableSurfaceDefaults.shape(CircleShape),
                scale = ClickableSurfaceDefaults.scale(1f, 1.1f, .95f),
                colors =
                    ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.inverseSurface,
                        focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    ),
                modifier = Modifier.size(with(LocalDensity.current) { 34.sp.toDp() }),
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = stringResource(R.string.search),
                    modifier =
                        Modifier
                            .align(Alignment.Center)
                            .size(with(LocalDensity.current) { 22.sp.toDp() }),
                )
            }
            if (showClock) {
                Text(
                    text = timeString,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Right margin of the [TopRightHeader], matches the end padding of the page content in the nav drawer
 */
val HeaderEndPadding = 16.dp

package com.github.damontecres.wholphin.ui.seasonal

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.ui.util.LocalClock
import java.time.Duration
import java.time.LocalDateTime
import java.time.Month

// The seasonal decorations in the top right header (search button & clock), see TopRightHeader

/**
 * Whether the holiday replaces the search button's magnifier with its own icon
 */
val Holiday.replacesSearchIcon: Boolean
    get() = this == Holiday.HALLOWEEN || this == Holiday.VALENTINE

/**
 * The search button's icon: a pumpkin on Halloween, a love letter on Valentine's Day
 */
@Composable
fun SeasonalSearchIcon(
    holiday: Holiday,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val s = size.minDimension
        val o = Offset((size.width - s) / 2, (size.height - s) / 2)
        when (holiday) {
            Holiday.HALLOWEEN -> {
                drawPumpkin(o, s)
            }

            Holiday.VALENTINE -> {
                drawLoveLetter(o, s)
            }

            else -> {}
        }
    }
}

/**
 * Decoration shown before the search button: an Easter egg, the countdown to midnight on New Year's Eve
 */
@Composable
fun SeasonalHeaderLeading(
    state: SeasonalState,
    showClock: Boolean,
    iconSize: Dp,
) {
    when (state.holiday) {
        Holiday.EASTER -> {
            Canvas(Modifier.size(iconSize)) { drawEasterEgg(Offset.Zero, size.minDimension) }
        }

        Holiday.NEW_YEAR -> {
            if (showClock) NewYearCountdown(state)
        }

        else -> {}
    }
}

/**
 * Decoration next to the clock: a Christmas tree, St. Nicholas's boot, a rose on Valentine's Day
 */
@Composable
fun SeasonalClockDecoration(
    state: SeasonalState,
    iconSize: Dp,
) {
    when (state.holiday) {
        Holiday.CHRISTMAS -> {
            val animate = rememberSeasonalAnimationsAllowed()
            if (animate) {
                val time = rememberTicker(frameMs = 150L)
                Canvas(Modifier.size(iconSize)) {
                    drawChristmasTree(Offset.Zero, size.minDimension, time.longValue / 400f)
                }
            } else {
                Canvas(Modifier.size(iconSize)) { drawChristmasTree(Offset.Zero, size.minDimension) }
            }
        }

        Holiday.NICHOLAS -> {
            Canvas(Modifier.size(iconSize)) { drawBoot(Offset.Zero, size.minDimension) }
        }

        Holiday.VALENTINE -> {
            Canvas(Modifier.size(iconSize)) { drawRose(Offset.Zero, size.minDimension) }
        }

        else -> {}
    }
}

/**
 * "2:15 do polnoci" on 31. 12. (or always in the preview)
 */
@Composable
private fun NewYearCountdown(state: SeasonalState) {
    val now by LocalClock.current.now
    if (!state.preview && !(now.month == Month.DECEMBER && now.dayOfMonth == 31)) return
    val minutes = remember(now.hour, now.minute) { minutesToMidnight(now) }
    val text = "%d:%02d".format(minutes / 60, minutes % 60)
    Text(
        text = stringResource(R.string.seasonal_countdown, text),
        fontSize = 14.sp,
        color = Color(0xFFFFD54F),
        style = MaterialTheme.typography.bodyMedium,
        maxLines = 1,
    )
}

/**
 * Whole minutes left until the next midnight, rounded up
 */
fun minutesToMidnight(now: LocalDateTime): Long {
    val midnight = now.toLocalDate().plusDays(1).atStartOfDay()
    val seconds = Duration.between(now, midnight).seconds
    return (seconds + 59) / 60
}

/**
 * Size of the header decorations, a bit larger than the search icon
 */
@Composable
fun seasonalIconSize(): Dp = with(LocalDensity.current) { 26.sp.toDp() }

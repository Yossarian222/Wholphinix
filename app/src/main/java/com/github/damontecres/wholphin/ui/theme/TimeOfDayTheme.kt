package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.github.damontecres.wholphin.preferences.AppThemeColors
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.temporal.ChronoUnit

/** Hour (inclusive) when the day theme starts */
private const val DAY_START_HOUR = 7

/** Hour (inclusive) when the dusk theme starts */
private const val DUSK_START_HOUR = 17

/** Hour (inclusive) when the night theme starts */
private const val NIGHT_START_HOUR = 20

/** Upper bound for a single wait, so clock/time zone changes and device sleep are picked up quickly */
private const val MAX_WAIT_MILLIS = 60_000L

/**
 * Returns the concrete theme for the given local time:
 * 07:00-17:00 Daylight, 17:00-20:00 Dusk, 20:00-07:00 Night
 */
fun themeForTimeOfDay(time: LocalTime): AppThemeColors =
    when (time.hour) {
        in DAY_START_HOUR until DUSK_START_HOUR -> AppThemeColors.DAYLIGHT
        in DUSK_START_HOUR until NIGHT_START_HOUR -> AppThemeColors.DUSK
        else -> AppThemeColors.NIGHT
    }

/** Milliseconds from [time] until the next theme boundary */
fun millisUntilNextTimeOfDayBoundary(time: LocalTime): Long {
    val nextHour =
        listOf(DAY_START_HOUR, DUSK_START_HOUR, NIGHT_START_HOUR).firstOrNull { it > time.hour }
    val next = LocalTime.of(nextHour ?: DAY_START_HOUR, 0)
    val millis = time.until(next, ChronoUnit.MILLIS)
    // A negative value means the boundary is tomorrow (wrap around midnight)
    return if (millis > 0) millis else millis + 24 * 60 * 60 * 1000L
}

/**
 * Resolves [appThemeColors] to a concrete theme. For [AppThemeColors.AUTO_TIME_OF_DAY] the result
 * follows the local time and updates automatically while the app is running.
 */
@Composable
fun rememberResolvedTheme(appThemeColors: AppThemeColors): AppThemeColors {
    val resolved by produceState(
        initialValue =
            if (appThemeColors == AppThemeColors.AUTO_TIME_OF_DAY) {
                themeForTimeOfDay(LocalTime.now())
            } else {
                appThemeColors
            },
        appThemeColors,
    ) {
        if (appThemeColors != AppThemeColors.AUTO_TIME_OF_DAY) {
            value = appThemeColors
            return@produceState
        }
        while (true) {
            val now = LocalTime.now()
            value = themeForTimeOfDay(now)
            // Wake up just after the boundary, but at most once a minute
            delay(minOf(millisUntilNextTimeOfDayBoundary(now) + 500L, MAX_WAIT_MILLIS))
        }
    }
    return resolved
}

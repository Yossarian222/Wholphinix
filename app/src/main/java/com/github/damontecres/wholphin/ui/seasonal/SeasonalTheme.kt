package com.github.damontecres.wholphin.ui.seasonal

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.preferences.InterfacePreferences
import com.github.damontecres.wholphin.preferences.SeasonalThemePreview
import com.github.damontecres.wholphin.ui.util.LocalClock
import java.time.LocalDate

/**
 * The seasonal theme currently shown in the menus
 *
 * @param holiday the active holiday
 * @param preview whether it is only a preview chosen in the settings (outside of the holiday's period)
 */
@Stable
class SeasonalState(
    val holiday: Holiday,
    val preview: Boolean,
) {
    /**
     * Where the clock in the top right header is (in root coordinates), eg for Cupid's arrow to stick into it
     */
    val clockBounds = mutableStateOf<Rect?>(null)
}

/**
 * The [SeasonalState] for pages with the nav drawer, null when no seasonal theme is active (and always in the player)
 */
val LocalSeasonalState = staticCompositionLocalOf<SeasonalState?> { null }

/**
 * Which holiday's theme to show: the preview chosen in the settings, otherwise the holiday period [date] is in.
 * Null if seasonal themes are turned off.
 */
fun activeHoliday(
    prefs: InterfacePreferences,
    date: LocalDate,
): Holiday? {
    if (prefs.seasonalThemesDisabled) return null
    return prefs.seasonalThemePreview.toHoliday() ?: HolidayCalendar.holidayOn(date)
}

fun SeasonalThemePreview.toHoliday(): Holiday? =
    when (this) {
        SeasonalThemePreview.SEASONAL_PREVIEW_NEW_YEAR -> Holiday.NEW_YEAR

        SeasonalThemePreview.SEASONAL_PREVIEW_VALENTINE -> Holiday.VALENTINE

        SeasonalThemePreview.SEASONAL_PREVIEW_EASTER -> Holiday.EASTER

        SeasonalThemePreview.SEASONAL_PREVIEW_HALLOWEEN -> Holiday.HALLOWEEN

        SeasonalThemePreview.SEASONAL_PREVIEW_NICHOLAS -> Holiday.NICHOLAS

        SeasonalThemePreview.SEASONAL_PREVIEW_CHRISTMAS -> Holiday.CHRISTMAS

        SeasonalThemePreview.SEASONAL_PREVIEW_NONE,
        SeasonalThemePreview.UNRECOGNIZED,
        -> null
    }

/**
 * Title of the seasonal recommendations row on the home page, eg "🎃 Na Halloween"
 */
@get:StringRes
val Holiday.rowTitle: Int
    get() =
        when (this) {
            Holiday.NEW_YEAR -> R.string.seasonal_row_new_year
            Holiday.VALENTINE -> R.string.seasonal_row_valentine
            Holiday.EASTER -> R.string.seasonal_row_easter
            Holiday.HALLOWEEN -> R.string.seasonal_row_halloween
            Holiday.NICHOLAS -> R.string.seasonal_row_nicholas
            Holiday.CHRISTMAS -> R.string.seasonal_row_christmas
        }

/**
 * Remembers the [SeasonalState] for the current date & preferences, recomputed only when the date changes (not on
 * every clock tick)
 */
@Composable
fun rememberSeasonalState(prefs: InterfacePreferences): SeasonalState? {
    val clock = LocalClock.current
    val date by remember(clock) { derivedStateOf { clock.now.value.toLocalDate() } }
    val disabled = prefs.seasonalThemesDisabled
    val preview = prefs.seasonalThemePreview
    return remember(date, disabled, preview) {
        if (disabled) {
            null
        } else {
            val previewHoliday = preview.toHoliday()
            val holiday = previewHoliday ?: HolidayCalendar.holidayOn(date)
            holiday?.let { SeasonalState(it, previewHoliday != null && previewHoliday != HolidayCalendar.holidayOn(date)) }
        }
    }
}

/**
 * Accent colors of a holiday: [primary] for buttons etc, [border] for the focused card's frame
 */
private data class SeasonalAccent(
    val primary: Color,
    val onPrimary: Color,
    val border: Color,
)

private fun Holiday.accent(): SeasonalAccent =
    when (this) {
        Holiday.HALLOWEEN -> SeasonalAccent(Color(0xFFE8761C), Color(0xFF1A0F1F), Color(0xFFB57BE8))

        Holiday.CHRISTMAS -> SeasonalAccent(Color(0xFFC62828), Color.White, Color(0xFFE8C766))

        Holiday.NICHOLAS -> SeasonalAccent(Color(0xFFD32F2F), Color.White, Color(0xFFEF5350))

        Holiday.NEW_YEAR -> SeasonalAccent(Color(0xFFD4AF37), Color(0xFF1A1405), Color(0xFFFFD54F))

        // The focused card gets a pink frame
        Holiday.VALENTINE -> SeasonalAccent(Color(0xFFD81B60), Color.White, Color(0xFFFF80AB))

        Holiday.EASTER -> SeasonalAccent(Color(0xFF7CB342), Color(0xFF10200A), Color(0xFFFFE082))
    }

/**
 * Provides [LocalSeasonalState] and overrides the accent colors of the current theme while a seasonal theme is active.
 * Only used around the pages with the nav drawer, never around the player.
 */
@Composable
fun SeasonalTheme(
    state: SeasonalState?,
    content: @Composable () -> Unit,
) {
    // Same structure with or without a holiday, so toggling it does not recreate the content
    val base = MaterialTheme.colorScheme
    val holiday = state?.holiday
    val colorScheme =
        remember(base, holiday) {
            holiday?.accent()?.let { accent ->
                base.copy(
                    primary = accent.primary,
                    onPrimary = accent.onPrimary,
                    border = accent.border,
                )
            } ?: base
        }
    CompositionLocalProvider(LocalSeasonalState provides state) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = MaterialTheme.shapes,
            typography = MaterialTheme.typography,
            content = content,
        )
    }
}

/**
 * Whether the decorations may be animated: not in battery/power saving mode and not with animations turned off in the
 * system settings
 */
fun seasonalAnimationsAllowed(context: Context): Boolean {
    val powerSave =
        runCatching { (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode }
            .getOrNull() ?: false
    val animatorScale =
        runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
        }.getOrDefault(1f)
    return !powerSave && animatorScale > 0f
}

@Composable
fun rememberSeasonalAnimationsAllowed(): Boolean {
    val context = LocalContext.current
    return remember(context) { seasonalAnimationsAllowed(context) }
}

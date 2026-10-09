package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.tv.material3.MaterialTheme
import com.github.damontecres.wholphin.preferences.AppThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.BlueThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.BoldBlueThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.BrownThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.CinemaThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.DaylightThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.DuskThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.GoldThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.GraphiteThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.GreenThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.MidnightThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.NeonThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.NightThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.OledThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.OrangeThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.PurpleThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.RedThemeColors
import com.github.damontecres.wholphin.ui.theme.colors.TealThemeColors
import java.time.LocalTime

val LocalTheme =
    compositionLocalOf<AppThemeColors> { AppThemeColors.PURPLE }

fun getThemeColors(appThemeColors: AppThemeColors): ThemeColors =
    when (appThemeColors) {
        AppThemeColors.PURPLE -> PurpleThemeColors
        AppThemeColors.BLUE -> BlueThemeColors
        AppThemeColors.GREEN -> GreenThemeColors
        AppThemeColors.ORANGE -> OrangeThemeColors
        AppThemeColors.OLED_BLACK -> OledThemeColors
        AppThemeColors.BOLD_BLUE -> BoldBlueThemeColors
        AppThemeColors.RED -> RedThemeColors
        AppThemeColors.BROWN -> BrownThemeColors
        AppThemeColors.CINEMA -> CinemaThemeColors
        AppThemeColors.GOLD -> GoldThemeColors
        AppThemeColors.MIDNIGHT -> MidnightThemeColors
        AppThemeColors.TEAL -> TealThemeColors
        AppThemeColors.NEON -> NeonThemeColors
        AppThemeColors.GRAPHITE -> GraphiteThemeColors
        AppThemeColors.DAYLIGHT -> DaylightThemeColors
        AppThemeColors.DUSK -> DuskThemeColors
        AppThemeColors.NIGHT -> NightThemeColors
        AppThemeColors.AUTO_TIME_OF_DAY -> getThemeColors(themeForTimeOfDay(LocalTime.now()))
        AppThemeColors.UNRECOGNIZED -> PurpleThemeColors
    }

@Composable
fun WholphinTheme(
    darkTheme: Boolean = true,
    appThemeColors: AppThemeColors = AppThemeColors.PURPLE,
    content: @Composable () -> Unit,
) {
    // AUTO_TIME_OF_DAY is resolved to a concrete theme here, so everything below
    // (including LocalTheme consumers) only ever sees a concrete theme
    val resolvedTheme = rememberResolvedTheme(appThemeColors)
    val themeColors = getThemeColors(resolvedTheme)

    val colorScheme =
        when {
            darkTheme -> themeColors.darkScheme
            else -> themeColors.lightScheme
        }
    CompositionLocalProvider(LocalTheme provides resolvedTheme) {
        androidx.compose.material3.MaterialTheme(
            colorScheme = if (darkTheme) themeColors.darkSchemeMaterial else themeColors.lightSchemeMaterial,
            typography = androidx.compose.material3.Typography(),
        ) {
            MaterialTheme(
                colorScheme = colorScheme,
                typography = AppTypography,
                content = content,
            )
        }
    }
}

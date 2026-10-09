package com.github.damontecres.wholphin.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.github.damontecres.wholphin.preferences.toUiScalePercent

/**
 * Scale factor for the navigation panel (side nav drawer & top tabs): icon sizes, text & widths
 *
 * 1f is the default size
 */
val LocalNavScale = compositionLocalOf { 1f }

/**
 * Applies the user's font size scale app wide (via [LocalDensity]'s font scale) and provides [LocalNavScale]
 *
 * Changes are applied immediately without restarting the app
 *
 * @param fontScalePercent the stored font scale percentage, 0 means default
 * @param navScalePercent the stored nav panel scale percentage, 0 means default
 */
@Composable
fun ProvideUiScale(
    fontScalePercent: Int,
    navScalePercent: Int,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val fontScale = fontScalePercent.toUiScalePercent() / 100f
    val navScale = navScalePercent.toUiScalePercent() / 100f
    val scaledDensity =
        remember(density, fontScale) {
            Density(density = density.density, fontScale = density.fontScale * fontScale)
        }
    CompositionLocalProvider(
        LocalDensity provides scaledDensity,
        LocalNavScale provides navScale,
        content = content,
    )
}

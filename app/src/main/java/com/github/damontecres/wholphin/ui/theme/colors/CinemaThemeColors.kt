package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val CinemaThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF6C0029)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFF8B1E3F)
        val onPrimaryContainerLight = Color(0xFFFF9DB0)
        val secondaryLight = Color(0xFF78555B)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFFDCFD5)
        val onSecondaryContainerLight = Color(0xFF79565B)
        val tertiaryLight = Color(0xFF433000)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFF5F4600)
        val onTertiaryContainerLight = Color(0xFFDBB561)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFFFF8F7)
        val onBackgroundLight = Color(0xFF211A1B)
        val surfaceLight = Color(0xFFFFF8F7)
        val onSurfaceLight = Color(0xFF211A1B)
        val surfaceVariantLight = Color(0xFFF5DDDF)
        val onSurfaceVariantLight = Color(0xFF534345)
        val outlineLight = Color(0xFF867275)
        val outlineVariantLight = Color(0xFFD8C1C3)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF372E2F)
        val inverseOnSurfaceLight = Color(0xFFFCEDEE)
        val inversePrimaryLight = Color(0xFFFFB2BF)
        val surfaceDimLight = Color(0xFFE5D7D8)
        val surfaceBrightLight = Color(0xFFFFF8F7)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFFFF0F1)
        val surfaceContainerLight = Color(0xFFF9EAEB)
        val surfaceContainerHighLight = Color(0xFFF4E5E6)
        val surfaceContainerHighestLight = Color(0xFFEEDFE0)

        val primaryDark = Color(0xFFFFB2BF)
        val onPrimaryDark = Color(0xFF660027)
        val primaryContainerDark = Color(0xFF8B1E3F)
        val onPrimaryContainerDark = Color(0xFFFF9DB0)
        val secondaryDark = Color(0xFFE8BBC2)
        val onSecondaryDark = Color(0xFF45282E)
        val secondaryContainerDark = Color(0xFF5E3E44)
        val onSecondaryContainerDark = Color(0xFFD5AAB1)
        val tertiaryDark = Color(0xFFE9C16C)
        val onTertiaryDark = Color(0xFF3F2E00)
        val tertiaryContainerDark = Color(0xFF5F4600)
        val onTertiaryContainerDark = Color(0xFFDBB561)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF181213)
        val onBackgroundDark = Color(0xFFEEDFE0)
        val surfaceDark = Color(0xFF181213)
        val onSurfaceDark = Color(0xFFEEDFE0)
        val surfaceVariantDark = Color(0xFF534345)
        val onSurfaceVariantDark = Color(0xFFD8C1C3)
        val outlineDark = Color(0xFFA08C8E)
        val outlineVariantDark = Color(0xFF534345)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFEEDFE0)
        val inverseOnSurfaceDark = Color(0xFF372E2F)
        val inversePrimaryDark = Color(0xFFA73453)
        val surfaceDimDark = Color(0xFF181213)
        val surfaceBrightDark = Color(0xFF403738)
        val surfaceContainerLowestDark = Color(0xFF130C0D)
        val surfaceContainerLowDark = Color(0xFF211A1B)
        val surfaceContainerDark = Color(0xFF251E1F)
        val surfaceContainerHighDark = Color(0xFF302829)
        val surfaceContainerHighestDark = Color(0xFF3B3334)

        override val lightSchemeMaterial: ColorScheme =
            androidx.compose.material3.lightColorScheme(
                primary = primaryLight,
                onPrimary = onPrimaryLight,
                primaryContainer = primaryContainerLight,
                onPrimaryContainer = onPrimaryContainerLight,
                secondary = secondaryLight,
                onSecondary = onSecondaryLight,
                secondaryContainer = secondaryContainerLight,
                onSecondaryContainer = onSecondaryContainerLight,
                tertiary = tertiaryLight,
                onTertiary = onTertiaryLight,
                tertiaryContainer = tertiaryContainerLight,
                onTertiaryContainer = onTertiaryContainerLight,
                error = errorLight,
                onError = onErrorLight,
                errorContainer = errorContainerLight,
                onErrorContainer = onErrorContainerLight,
                background = backgroundLight,
                onBackground = onBackgroundLight,
                surface = surfaceLight,
                onSurface = onSurfaceLight,
                surfaceVariant = surfaceVariantLight,
                onSurfaceVariant = onSurfaceVariantLight,
                scrim = scrimLight,
                inverseSurface = inverseSurfaceLight,
                inverseOnSurface = inverseOnSurfaceLight,
                inversePrimary = inversePrimaryLight,
            )

        override val lightScheme =
            lightColorScheme(
                primary = primaryLight,
                onPrimary = onPrimaryLight,
                primaryContainer = primaryContainerLight,
                onPrimaryContainer = onPrimaryContainerLight,
                secondary = secondaryLight,
                onSecondary = onSecondaryLight,
                secondaryContainer = secondaryContainerLight,
                onSecondaryContainer = onSecondaryContainerLight,
                tertiary = tertiaryLight,
                onTertiary = onTertiaryLight,
                tertiaryContainer = tertiaryContainerLight,
                onTertiaryContainer = onTertiaryContainerLight,
                error = errorLight,
                onError = onErrorLight,
                errorContainer = errorContainerLight,
                onErrorContainer = onErrorContainerLight,
                background = backgroundLight,
                onBackground = onBackgroundLight,
                surface = surfaceLight,
                onSurface = onSurfaceLight,
                surfaceVariant = surfaceVariantLight,
                onSurfaceVariant = onSurfaceVariantLight,
                scrim = scrimLight,
                inverseSurface = inverseSurfaceLight,
                inverseOnSurface = inverseOnSurfaceLight,
                inversePrimary = inversePrimaryLight,
                border = inversePrimaryLight,
            )

        override val darkSchemeMaterial =
            androidx.compose.material3.darkColorScheme(
                primary = primaryDark,
                onPrimary = onPrimaryDark,
                primaryContainer = primaryContainerDark,
                onPrimaryContainer = onPrimaryContainerDark,
                secondary = secondaryDark,
                onSecondary = onSecondaryDark,
                secondaryContainer = secondaryContainerDark,
                onSecondaryContainer = onSecondaryContainerDark,
                tertiary = tertiaryDark,
                onTertiary = onTertiaryDark,
                tertiaryContainer = tertiaryContainerDark,
                onTertiaryContainer = onTertiaryContainerDark,
                error = errorDark,
                onError = onErrorDark,
                errorContainer = errorContainerDark,
                onErrorContainer = onErrorContainerDark,
                background = backgroundDark,
                onBackground = onBackgroundDark,
                surface = surfaceDark,
                onSurface = onSurfaceDark,
                surfaceVariant = surfaceVariantDark,
                onSurfaceVariant = onSurfaceVariantDark,
                scrim = scrimDark,
                inverseSurface = inverseSurfaceDark,
                inverseOnSurface = inverseOnSurfaceDark,
                inversePrimary = inversePrimaryDark,
            )

        override val darkScheme =
            darkColorScheme(
                primary = primaryDark,
                onPrimary = onPrimaryDark,
                primaryContainer = primaryContainerDark,
                onPrimaryContainer = onPrimaryContainerDark,
                secondary = secondaryDark,
                onSecondary = onSecondaryDark,
                secondaryContainer = secondaryContainerDark,
                onSecondaryContainer = onSecondaryContainerDark,
                tertiary = tertiaryDark,
                onTertiary = onTertiaryDark,
                tertiaryContainer = tertiaryContainerDark,
                onTertiaryContainer = onTertiaryContainerDark,
                error = errorDark,
                onError = onErrorDark,
                errorContainer = errorContainerDark,
                onErrorContainer = onErrorContainerDark,
                background = backgroundDark,
                onBackground = onBackgroundDark,
                surface = surfaceDark,
                onSurface = onSurfaceDark,
                surfaceVariant = surfaceVariantDark,
                onSurfaceVariant = onSurfaceVariantDark,
                scrim = scrimDark,
                inverseSurface = inverseSurfaceDark,
                inverseOnSurface = inverseOnSurfaceDark,
                inversePrimary = inversePrimaryDark,
                border = inversePrimaryDark,
            )
    }

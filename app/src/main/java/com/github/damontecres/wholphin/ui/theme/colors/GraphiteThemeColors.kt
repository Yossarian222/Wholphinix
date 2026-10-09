package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val GraphiteThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF51606F)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFD4E4F6)
        val onPrimaryContainerLight = Color(0xFF394857)
        val secondaryLight = Color(0xFF5B5F63)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFE0E2E8)
        val onSecondaryContainerLight = Color(0xFF43474C)
        val tertiaryLight = Color(0xFF51625E)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFD4E7E2)
        val onTertiaryContainerLight = Color(0xFF394A47)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFFBF9FA)
        val onBackgroundLight = Color(0xFF1B1C1D)
        val surfaceLight = Color(0xFFFBF9FA)
        val onSurfaceLight = Color(0xFF1B1C1D)
        val surfaceVariantLight = Color(0xFFE2E2E5)
        val onSurfaceVariantLight = Color(0xFF45474A)
        val outlineLight = Color(0xFF76777A)
        val outlineVariantLight = Color(0xFFC6C6C9)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF303031)
        val inverseOnSurfaceLight = Color(0xFFF2F0F1)
        val inversePrimaryLight = Color(0xFFB9C8DA)
        val surfaceDimLight = Color(0xFFDBD9DA)
        val surfaceBrightLight = Color(0xFFFBF9FA)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFF5F3F4)
        val surfaceContainerLight = Color(0xFFEFEDEE)
        val surfaceContainerHighLight = Color(0xFFEAE7E9)
        val surfaceContainerHighestLight = Color(0xFFE4E2E3)

        val primaryDark = Color(0xFFB9C8DA)
        val onPrimaryDark = Color(0xFF233240)
        val primaryContainerDark = Color(0xFF394857)
        val onPrimaryContainerDark = Color(0xFFD4E4F6)
        val secondaryDark = Color(0xFFC4C6CC)
        val onSecondaryDark = Color(0xFF2D3135)
        val secondaryContainerDark = Color(0xFF43474C)
        val onSecondaryContainerDark = Color(0xFFE0E2E8)
        val tertiaryDark = Color(0xFFB8CAC6)
        val onTertiaryDark = Color(0xFF233430)
        val tertiaryContainerDark = Color(0xFF394A47)
        val onTertiaryContainerDark = Color(0xFFD4E7E2)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF131314)
        val onBackgroundDark = Color(0xFFE4E2E3)
        val surfaceDark = Color(0xFF131314)
        val onSurfaceDark = Color(0xFFE4E2E3)
        val surfaceVariantDark = Color(0xFF45474A)
        val onSurfaceVariantDark = Color(0xFFC6C6C9)
        val outlineDark = Color(0xFF909194)
        val outlineVariantDark = Color(0xFF45474A)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFE4E2E3)
        val inverseOnSurfaceDark = Color(0xFF303031)
        val inversePrimaryDark = Color(0xFF51606F)
        val surfaceDimDark = Color(0xFF131314)
        val surfaceBrightDark = Color(0xFF39393A)
        val surfaceContainerLowestDark = Color(0xFF0E0E0F)
        val surfaceContainerLowDark = Color(0xFF1B1C1D)
        val surfaceContainerDark = Color(0xFF1F2021)
        val surfaceContainerHighDark = Color(0xFF292A2B)
        val surfaceContainerHighestDark = Color(0xFF343536)

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

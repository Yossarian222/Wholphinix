package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val GoldThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF7A5900)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFE0B252)
        val onPrimaryContainerLight = Color(0xFF5F4500)
        val secondaryLight = Color(0xFF6C5C3F)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFF6E0BB)
        val onSecondaryContainerLight = Color(0xFF726245)
        val tertiaryLight = Color(0xFF8B4D41)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFF4A596)
        val onTertiaryContainerLight = Color(0xFF72392E)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFFFF8F5)
        val onBackgroundLight = Color(0xFF211A16)
        val surfaceLight = Color(0xFFFFF8F5)
        val onSurfaceLight = Color(0xFF211A16)
        val surfaceVariantLight = Color(0xFFF5DED0)
        val onSurfaceVariantLight = Color(0xFF53443A)
        val outlineLight = Color(0xFF867468)
        val outlineVariantLight = Color(0xFFD8C2B5)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF372F2A)
        val inverseOnSurfaceLight = Color(0xFFFCEEE6)
        val inversePrimaryLight = Color(0xFFEFC05E)
        val surfaceDimLight = Color(0xFFE5D7D0)
        val surfaceBrightLight = Color(0xFFFFF8F5)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFFFF1E9)
        val surfaceContainerLight = Color(0xFFFAEBE3)
        val surfaceContainerHighLight = Color(0xFFF4E5DE)
        val surfaceContainerHighestLight = Color(0xFFEEE0D8)

        val primaryDark = Color(0xFFFECD6A)
        val onPrimaryDark = Color(0xFF402D00)
        val primaryContainerDark = Color(0xFFE0B252)
        val onPrimaryContainerDark = Color(0xFF5F4500)
        val secondaryDark = Color(0xFFD9C4A0)
        val onSecondaryDark = Color(0xFF3B2F15)
        val secondaryContainerDark = Color(0xFF55472C)
        val onSecondaryContainerDark = Color(0xFFCAB693)
        val tertiaryDark = Color(0xFFFFC8BD)
        val onTertiaryDark = Color(0xFF532118)
        val tertiaryContainerDark = Color(0xFFF4A596)
        val onTertiaryContainerDark = Color(0xFF72392E)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF18120E)
        val onBackgroundDark = Color(0xFFEEE0D8)
        val surfaceDark = Color(0xFF18120E)
        val onSurfaceDark = Color(0xFFEEE0D8)
        val surfaceVariantDark = Color(0xFF53443A)
        val onSurfaceVariantDark = Color(0xFFD8C2B5)
        val outlineDark = Color(0xFFA08D81)
        val outlineVariantDark = Color(0xFF53443A)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFEEE0D8)
        val inverseOnSurfaceDark = Color(0xFF372F2A)
        val inversePrimaryDark = Color(0xFF7A5900)
        val surfaceDimDark = Color(0xFF18120E)
        val surfaceBrightDark = Color(0xFF403832)
        val surfaceContainerLowestDark = Color(0xFF130D09)
        val surfaceContainerLowDark = Color(0xFF211A16)
        val surfaceContainerDark = Color(0xFF251E1A)
        val surfaceContainerHighDark = Color(0xFF302824)
        val surfaceContainerHighestDark = Color(0xFF3B332E)

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

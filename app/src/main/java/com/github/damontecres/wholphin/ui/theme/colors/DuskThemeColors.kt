package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val DuskThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF805434)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFFFDCC5)
        val onPrimaryContainerLight = Color(0xFF653D1F)
        val secondaryLight = Color(0xFF635C68)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFEADFED)
        val onSecondaryContainerLight = Color(0xFF4B4450)
        val tertiaryLight = Color(0xFF795557)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFFFDADA)
        val onTertiaryContainerLight = Color(0xFF5F3E40)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFFFF7FE)
        val onBackgroundLight = Color(0xFF1D1B1F)
        val surfaceLight = Color(0xFFFFF7FE)
        val onSurfaceLight = Color(0xFF1D1B1F)
        val surfaceVariantLight = Color(0xFFE8E0E9)
        val onSurfaceVariantLight = Color(0xFF4A454D)
        val outlineLight = Color(0xFF7B757D)
        val outlineVariantLight = Color(0xFFCCC4CD)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF332F34)
        val inverseOnSurfaceLight = Color(0xFFF6EFF5)
        val inversePrimaryLight = Color(0xFFF4BB93)
        val surfaceDimLight = Color(0xFFDFD8DE)
        val surfaceBrightLight = Color(0xFFFFF7FE)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFF9F1F8)
        val surfaceContainerLight = Color(0xFFF3ECF2)
        val surfaceContainerHighLight = Color(0xFFEDE6EC)
        val surfaceContainerHighestLight = Color(0xFFE7E0E7)

        val primaryDark = Color(0xFFF4BB93)
        val onPrimaryDark = Color(0xFF4A280B)
        val primaryContainerDark = Color(0xFF653D1F)
        val onPrimaryContainerDark = Color(0xFFFFDCC5)
        val secondaryDark = Color(0xFFCDC3D1)
        val onSecondaryDark = Color(0xFF342E39)
        val secondaryContainerDark = Color(0xFF4B4450)
        val onSecondaryContainerDark = Color(0xFFEADFED)
        val tertiaryDark = Color(0xFFE9BBBD)
        val onTertiaryDark = Color(0xFF46282A)
        val tertiaryContainerDark = Color(0xFF5F3E40)
        val onTertiaryContainerDark = Color(0xFFFFDADA)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF1F1D21)
        val onBackgroundDark = Color(0xFFE7E0E7)
        val surfaceDark = Color(0xFF1F1D21)
        val onSurfaceDark = Color(0xFFE7E0E7)
        val surfaceVariantDark = Color(0xFF433E46)
        val onSurfaceVariantDark = Color(0xFFCCC4CD)
        val outlineDark = Color(0xFF908992)
        val outlineVariantDark = Color(0xFF433E46)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFE7E0E7)
        val inverseOnSurfaceDark = Color(0xFF332F34)
        val inversePrimaryDark = Color(0xFF805434)
        val surfaceDimDark = Color(0xFF1F1D21)
        val surfaceBrightDark = Color(0xFF423F44)
        val surfaceContainerLowestDark = Color(0xFF171519)
        val surfaceContainerLowDark = Color(0xFF232125)
        val surfaceContainerDark = Color(0xFF282529)
        val surfaceContainerHighDark = Color(0xFF302D32)
        val surfaceContainerHighestDark = Color(0xFF39363B)

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

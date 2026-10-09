package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val MidnightThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF286294)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFD0E4FF)
        val onPrimaryContainerLight = Color(0xFF004A7A)
        val secondaryLight = Color(0xFF545E7A)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFDAE2FF)
        val onSecondaryContainerLight = Color(0xFF3C4661)
        val tertiaryLight = Color(0xFF635785)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFE8DDFF)
        val onTertiaryContainerLight = Color(0xFF4B406C)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFFAF8FF)
        val onBackgroundLight = Color(0xFF191B24)
        val surfaceLight = Color(0xFFFAF8FF)
        val onSurfaceLight = Color(0xFF191B24)
        val surfaceVariantLight = Color(0xFFDDE2F6)
        val onSurfaceVariantLight = Color(0xFF414756)
        val outlineLight = Color(0xFF717788)
        val outlineVariantLight = Color(0xFFC1C6D9)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF2E3039)
        val inverseOnSurfaceLight = Color(0xFFEFF0FC)
        val inversePrimaryLight = Color(0xFF9BCBFF)
        val surfaceDimLight = Color(0xFFD8D9E5)
        val surfaceBrightLight = Color(0xFFFAF8FF)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFF2F3FF)
        val surfaceContainerLight = Color(0xFFECEDF9)
        val surfaceContainerHighLight = Color(0xFFE6E7F3)
        val surfaceContainerHighestLight = Color(0xFFE1E2EE)

        val primaryDark = Color(0xFF9BCBFF)
        val onPrimaryDark = Color(0xFF003256)
        val primaryContainerDark = Color(0xFF004A7A)
        val onPrimaryContainerDark = Color(0xFFD0E4FF)
        val secondaryDark = Color(0xFFBCC6E7)
        val onSecondaryDark = Color(0xFF26304A)
        val secondaryContainerDark = Color(0xFF3C4661)
        val onSecondaryContainerDark = Color(0xFFDAE2FF)
        val tertiaryDark = Color(0xFFCDBFF3)
        val onTertiaryDark = Color(0xFF342954)
        val tertiaryContainerDark = Color(0xFF4B406C)
        val onTertiaryContainerDark = Color(0xFFE8DDFF)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF10131B)
        val onBackgroundDark = Color(0xFFE1E2EE)
        val surfaceDark = Color(0xFF10131B)
        val onSurfaceDark = Color(0xFFE1E2EE)
        val surfaceVariantDark = Color(0xFF414756)
        val onSurfaceVariantDark = Color(0xFFC1C6D9)
        val outlineDark = Color(0xFF8B90A2)
        val outlineVariantDark = Color(0xFF414756)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFE1E2EE)
        val inverseOnSurfaceDark = Color(0xFF2E3039)
        val inversePrimaryDark = Color(0xFF286294)
        val surfaceDimDark = Color(0xFF10131B)
        val surfaceBrightDark = Color(0xFF363942)
        val surfaceContainerLowestDark = Color(0xFF0B0E16)
        val surfaceContainerLowDark = Color(0xFF191B24)
        val surfaceContainerDark = Color(0xFF1D1F28)
        val surfaceContainerHighDark = Color(0xFF272A32)
        val surfaceContainerHighestDark = Color(0xFF32343D)

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

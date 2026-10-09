package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val NeonThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFFAF0A78)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFCF3192)
        val onPrimaryContainerLight = Color(0xFFFFFBFF)
        val secondaryLight = Color(0xFF625886)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFD8CAFF)
        val onSecondaryContainerLight = Color(0xFF5E5381)
        val tertiaryLight = Color(0xFF00666F)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFF00818C)
        val onTertiaryContainerLight = Color(0xFFF6FEFF)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFFEF7FF)
        val onBackgroundLight = Color(0xFF1D1A22)
        val surfaceLight = Color(0xFFFEF7FF)
        val onSurfaceLight = Color(0xFF1D1A22)
        val surfaceVariantLight = Color(0xFFE8DFF5)
        val onSurfaceVariantLight = Color(0xFF4A4455)
        val outlineLight = Color(0xFF7B7487)
        val outlineVariantLight = Color(0xFFCCC3D8)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF322F37)
        val inverseOnSurfaceLight = Color(0xFFF5EEFA)
        val inversePrimaryLight = Color(0xFFFFAFD5)
        val surfaceDimLight = Color(0xFFDED8E3)
        val surfaceBrightLight = Color(0xFFFEF7FF)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFF8F1FD)
        val surfaceContainerLight = Color(0xFFF2EBF7)
        val surfaceContainerHighLight = Color(0xFFEDE6F1)
        val surfaceContainerHighestLight = Color(0xFFE7E0EB)

        val primaryDark = Color(0xFFFFAFD5)
        val onPrimaryDark = Color(0xFF620041)
        val primaryContainerDark = Color(0xFFF451B0)
        val onPrimaryContainerDark = Color(0xFF380024)
        val secondaryDark = Color(0xFFCCBFF4)
        val onSecondaryDark = Color(0xFF332A54)
        val secondaryContainerDark = Color(0xFF4D426F)
        val onSecondaryContainerDark = Color(0xFFBEB1E5)
        val tertiaryDark = Color(0xFF73D5E1)
        val onTertiaryDark = Color(0xFF00363C)
        val tertiaryContainerDark = Color(0xFF359EAA)
        val onTertiaryContainerDark = Color(0xFF001C1F)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF15121A)
        val onBackgroundDark = Color(0xFFE7E0EB)
        val surfaceDark = Color(0xFF15121A)
        val onSurfaceDark = Color(0xFFE7E0EB)
        val surfaceVariantDark = Color(0xFF4A4455)
        val onSurfaceVariantDark = Color(0xFFCCC3D8)
        val outlineDark = Color(0xFF958DA1)
        val outlineVariantDark = Color(0xFF4A4455)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFE7E0EB)
        val inverseOnSurfaceDark = Color(0xFF322F37)
        val inversePrimaryDark = Color(0xFFB2107B)
        val surfaceDimDark = Color(0xFF15121A)
        val surfaceBrightDark = Color(0xFF3B3840)
        val surfaceContainerLowestDark = Color(0xFF0F0D14)
        val surfaceContainerLowDark = Color(0xFF1D1A22)
        val surfaceContainerDark = Color(0xFF211E26)
        val surfaceContainerHighDark = Color(0xFF2C2931)
        val surfaceContainerHighestDark = Color(0xFF36333C)

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

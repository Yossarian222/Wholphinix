package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val NightThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF5E5C74)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFE4DFFC)
        val onPrimaryContainerLight = Color(0xFF47445C)
        val secondaryLight = Color(0xFF5D5E64)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFE2E2E9)
        val onSecondaryContainerLight = Color(0xFF45464C)
        val tertiaryLight = Color(0xFF665A69)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFEEDDEE)
        val onTertiaryContainerLight = Color(0xFF4E4351)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFFAF8FF)
        val onBackgroundLight = Color(0xFF1A1B22)
        val surfaceLight = Color(0xFFFAF8FF)
        val onSurfaceLight = Color(0xFF1A1B22)
        val surfaceVariantLight = Color(0xFFE3E1EE)
        val onSurfaceVariantLight = Color(0xFF464650)
        val outlineLight = Color(0xFF767681)
        val outlineVariantLight = Color(0xFFC6C5D1)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF2F3037)
        val inverseOnSurfaceLight = Color(0xFFF1F0F9)
        val inversePrimaryLight = Color(0xFFC8C3E0)
        val surfaceDimLight = Color(0xFFDAD9E2)
        val surfaceBrightLight = Color(0xFFFAF8FF)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFF4F2FC)
        val surfaceContainerLight = Color(0xFFEEEDF6)
        val surfaceContainerHighLight = Color(0xFFE8E7F0)
        val surfaceContainerHighestLight = Color(0xFFE3E1EA)

        val primaryDark = Color(0xFFBCB8D4)
        val onPrimaryDark = Color(0xFF2C2A40)
        val primaryContainerDark = Color(0xFF3D3B52)
        val onPrimaryContainerDark = Color(0xFFE4DFFC)
        val secondaryDark = Color(0xFFBBBBC2)
        val onSecondaryDark = Color(0xFF2B2C31)
        val secondaryContainerDark = Color(0xFF3C3D43)
        val onSecondaryContainerDark = Color(0xFFE2E2E9)
        val tertiaryDark = Color(0xFFC6B7C7)
        val onTertiaryDark = Color(0xFF332935)
        val tertiaryContainerDark = Color(0xFF453A47)
        val onTertiaryContainerDark = Color(0xFFEEDDEE)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF0F1117)
        val onBackgroundDark = Color(0xFFDAD9E2)
        val surfaceDark = Color(0xFF0F1117)
        val onSurfaceDark = Color(0xFFDAD9E2)
        val surfaceVariantDark = Color(0xFF31323B)
        val onSurfaceVariantDark = Color(0xFFBBBAC6)
        val outlineDark = Color(0xFF80808B)
        val outlineVariantDark = Color(0xFF31323B)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFDAD9E2)
        val inverseOnSurfaceDark = Color(0xFF2F3037)
        val inversePrimaryDark = Color(0xFF5E5C74)
        val surfaceDimDark = Color(0xFF0F1117)
        val surfaceBrightDark = Color(0xFF313239)
        val surfaceContainerLowestDark = Color(0xFF06070D)
        val surfaceContainerLowDark = Color(0xFF14151B)
        val surfaceContainerDark = Color(0xFF181920)
        val surfaceContainerHighDark = Color(0xFF202128)
        val surfaceContainerHighestDark = Color(0xFF292A30)

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

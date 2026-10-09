package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val TealThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF006A60)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFF2BB3A3)
        val onPrimaryContainerLight = Color(0xFF003F38)
        val secondaryLight = Color(0xFF4A635F)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFCCE8E2)
        val onSecondaryContainerLight = Color(0xFF506965)
        val tertiaryLight = Color(0xFF984714)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFEA8750)
        val onTertiaryContainerLight = Color(0xFF602600)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFF6FAF8)
        val onBackgroundLight = Color(0xFF181D1B)
        val surfaceLight = Color(0xFFF6FAF8)
        val onSurfaceLight = Color(0xFF181D1B)
        val surfaceVariantLight = Color(0xFFD9E5E1)
        val onSurfaceVariantLight = Color(0xFF3E4947)
        val outlineLight = Color(0xFF6E7A77)
        val outlineVariantLight = Color(0xFFBDC9C6)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF2C3130)
        val inverseOnSurfaceLight = Color(0xFFEDF2EF)
        val inversePrimaryLight = Color(0xFF5EDAC9)
        val surfaceDimLight = Color(0xFFD6DBD9)
        val surfaceBrightLight = Color(0xFFF6FAF8)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFF0F5F2)
        val surfaceContainerLight = Color(0xFFEAEFEC)
        val surfaceContainerHighLight = Color(0xFFE5E9E7)
        val surfaceContainerHighestLight = Color(0xFFDFE3E1)

        val primaryDark = Color(0xFF5EDAC9)
        val onPrimaryDark = Color(0xFF003731)
        val primaryContainerDark = Color(0xFF2BB3A3)
        val onPrimaryContainerDark = Color(0xFF003F38)
        val secondaryDark = Color(0xFFB1CCC6)
        val onSecondaryDark = Color(0xFF1C3531)
        val secondaryContainerDark = Color(0xFF354E49)
        val onSecondaryContainerDark = Color(0xFFA3BEB8)
        val tertiaryDark = Color(0xFFFFB690)
        val onTertiaryDark = Color(0xFF552100)
        val tertiaryContainerDark = Color(0xFFEA8750)
        val onTertiaryContainerDark = Color(0xFF602600)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF0F1413)
        val onBackgroundDark = Color(0xFFDFE3E1)
        val surfaceDark = Color(0xFF0F1413)
        val onSurfaceDark = Color(0xFFDFE3E1)
        val surfaceVariantDark = Color(0xFF3E4947)
        val onSurfaceVariantDark = Color(0xFFBDC9C6)
        val outlineDark = Color(0xFF879390)
        val outlineVariantDark = Color(0xFF3E4947)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFDFE3E1)
        val inverseOnSurfaceDark = Color(0xFF2C3130)
        val inversePrimaryDark = Color(0xFF006A60)
        val surfaceDimDark = Color(0xFF0F1413)
        val surfaceBrightDark = Color(0xFF353A39)
        val surfaceContainerLowestDark = Color(0xFF0A0F0E)
        val surfaceContainerLowDark = Color(0xFF181D1B)
        val surfaceContainerDark = Color(0xFF1C211F)
        val surfaceContainerHighDark = Color(0xFF262B2A)
        val surfaceContainerHighestDark = Color(0xFF313634)

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

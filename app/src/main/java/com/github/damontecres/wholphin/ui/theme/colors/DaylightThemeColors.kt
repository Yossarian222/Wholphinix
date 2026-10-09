package com.github.damontecres.wholphin.ui.theme.colors

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.darkColorScheme
import androidx.tv.material3.lightColorScheme
import com.github.damontecres.wholphin.ui.theme.ThemeColors

val DaylightThemeColors =
    object : ThemeColors {
        val primaryLight = Color(0xFF3D6561)
        val onPrimaryLight = Color(0xFFFFFFFF)
        val primaryContainerLight = Color(0xFFC0EBE6)
        val onPrimaryContainerLight = Color(0xFF254D4A)
        val secondaryLight = Color(0xFF57615B)
        val onSecondaryLight = Color(0xFFFFFFFF)
        val secondaryContainerLight = Color(0xFFDBE5DE)
        val onSecondaryContainerLight = Color(0xFF404944)
        val tertiaryLight = Color(0xFF745A34)
        val onTertiaryLight = Color(0xFFFFFFFF)
        val tertiaryContainerLight = Color(0xFFFFDDB2)
        val onTertiaryContainerLight = Color(0xFF5B421E)
        val errorLight = Color(0xFFBA1A1A)
        val onErrorLight = Color(0xFFFFFFFF)
        val errorContainerLight = Color(0xFFFFDAD6)
        val onErrorContainerLight = Color(0xFF93000A)
        val backgroundLight = Color(0xFFFDF8F5)
        val onBackgroundLight = Color(0xFF1C1B19)
        val surfaceLight = Color(0xFFFDF8F5)
        val onSurfaceLight = Color(0xFF1C1B19)
        val surfaceVariantLight = Color(0xFFE0E3E2)
        val onSurfaceVariantLight = Color(0xFF444747)
        val outlineLight = Color(0xFF747877)
        val outlineVariantLight = Color(0xFFC4C7C6)
        val scrimLight = Color(0xFF000000)
        val inverseSurfaceLight = Color(0xFF32302E)
        val inverseOnSurfaceLight = Color(0xFFF4F0EC)
        val inversePrimaryLight = Color(0xFFA4CFCA)
        val surfaceDimLight = Color(0xFFDDD9D6)
        val surfaceBrightLight = Color(0xFFFDF8F5)
        val surfaceContainerLowestLight = Color(0xFFFFFFFF)
        val surfaceContainerLowLight = Color(0xFFF7F3EF)
        val surfaceContainerLight = Color(0xFFF2EDE9)
        val surfaceContainerHighLight = Color(0xFFECE7E4)
        val surfaceContainerHighestLight = Color(0xFFE6E2DE)

        val primaryDark = Color(0xFFAAD4CF)
        val onPrimaryDark = Color(0xFF0A3633)
        val primaryContainerDark = Color(0xFF2F5753)
        val onPrimaryContainerDark = Color(0xFFC0EBE6)
        val secondaryDark = Color(0xFFC5CEC8)
        val onSecondaryDark = Color(0xFF29322E)
        val secondaryContainerDark = Color(0xFF49524D)
        val onSecondaryContainerDark = Color(0xFFDBE5DE)
        val tertiaryDark = Color(0xFFEAC698)
        val onTertiaryDark = Color(0xFF422C0A)
        val tertiaryContainerDark = Color(0xFF654C27)
        val onTertiaryContainerDark = Color(0xFFFFDDB2)
        val errorDark = Color(0xFFFFB4AB)
        val onErrorDark = Color(0xFF690005)
        val errorContainerDark = Color(0xFF93000A)
        val onErrorContainerDark = Color(0xFFFFDAD6)
        val backgroundDark = Color(0xFF2F2E2C)
        val onBackgroundDark = Color(0xFFECE7E4)
        val surfaceDark = Color(0xFF2F2E2C)
        val onSurfaceDark = Color(0xFFECE7E4)
        val surfaceVariantDark = Color(0xFF4B4E4E)
        val onSurfaceVariantDark = Color(0xFFCFD2D1)
        val outlineDark = Color(0xFF939795)
        val outlineVariantDark = Color(0xFF4B4E4E)
        val scrimDark = Color(0xFF000000)
        val inverseSurfaceDark = Color(0xFFECE7E4)
        val inverseOnSurfaceDark = Color(0xFF32302E)
        val inversePrimaryDark = Color(0xFF3D6561)
        val surfaceDimDark = Color(0xFF2F2E2C)
        val surfaceBrightDark = Color(0xFF545250)
        val surfaceContainerLowestDark = Color(0xFF252321)
        val surfaceContainerLowDark = Color(0xFF343230)
        val surfaceContainerDark = Color(0xFF383734)
        val surfaceContainerHighDark = Color(0xFF41403D)
        val surfaceContainerHighestDark = Color(0xFF4B4946)

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

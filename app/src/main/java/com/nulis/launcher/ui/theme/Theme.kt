// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalNulisColors = staticCompositionLocalOf { BlackColors }
val LocalLook = staticCompositionLocalOf { Looks.Dot }
val LocalNulisTypography = staticCompositionLocalOf { NulisTypography(Looks.Dot) }

/** Access point for the design tokens, e.g. `NulisTheme.colors.hairline`. */
object NulisTheme {
    val colors: NulisColors
        @Composable @ReadOnlyComposable get() = LocalNulisColors.current
    val look: Look
        @Composable @ReadOnlyComposable get() = LocalLook.current
    val type: NulisTypography
        @Composable @ReadOnlyComposable get() = LocalNulisTypography.current
}

object NulisShapes {
    val card = RoundedCornerShape(20.dp)
    val tile = RoundedCornerShape(16.dp)
    val sheet = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    val pill = RoundedCornerShape(50)
}

/** Spacing grid: multiples of 8dp, 24dp screen margins. */
object NulisSpacing {
    val unit = 8.dp
    val screenMargin = 24.dp
    val touchTarget = 48.dp
}

@Composable
fun NulisTheme(
    look: Look,
    colors: NulisColors,
    displayFont: NulisFont? = null,
    bodyFont: NulisFont? = null,
    textScale: Float = 1f,
    uppercaseLabels: Boolean = true,
    content: @Composable () -> Unit,
) {
    val type = remember(look, displayFont, bodyFont, textScale, uppercaseLabels) {
        NulisTypography(look, displayFont, bodyFont, textScale, uppercaseLabels)
    }
    val scheme = remember(colors) {
        val base = if (colors.isDark) darkColorScheme() else lightColorScheme()
        base.copy(
            primary = colors.onBackground,
            onPrimary = colors.background,
            background = colors.background,
            onBackground = colors.onBackground,
            surface = colors.background,
            onSurface = colors.onBackground,
            surfaceContainer = colors.surface,
            surfaceContainerLow = colors.surface,
            surfaceContainerHigh = colors.surfaceRaised,
            surfaceContainerHighest = colors.surfaceRaised,
            onSurfaceVariant = colors.secondary,
            outline = colors.hairline,
            outlineVariant = colors.hairline,
            error = colors.danger,
        )
    }
    CompositionLocalProvider(
        LocalNulisColors provides colors,
        LocalLook provides look,
        LocalNulisTypography provides type,
        LocalContentColor provides colors.onBackground,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = Typography(bodyLarge = type.bodyM, bodyMedium = type.bodyM, labelLarge = type.labelL),
            content = content,
        )
    }
}

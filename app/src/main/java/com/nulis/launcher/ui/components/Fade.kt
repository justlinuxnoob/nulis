// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * Fades the first few pixels of a scrolling list into the background.
 *
 * Every list screen here is a fixed header - a title, a section label, a slider - with a list
 * scrolling underneath it. Without this, a row passing under the header is sliced through the
 * middle of its letters and the result reads as a rendering bug rather than as a list that
 * continues. A short fade says "this goes on above" in the quietest way available.
 *
 * Deliberately not used in the app drawer: that list is the one surface whose every frame is
 * measured, and an extra draw pass over the top of it is not worth the tidiness.
 */
@Composable
fun Modifier.fadeTop(height: Dp = 20.dp): Modifier {
    val background = NulisTheme.colors.background
    return drawWithContent {
        drawContent()
        val fade = height.toPx().coerceAtMost(size.height)
        if (fade <= 0f) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(background, Color.Transparent),
                startY = 0f,
                endY = fade,
            ),
            topLeft = Offset.Zero,
            size = Size(size.width, fade),
        )
    }
}

/**
 * Fades the bottom of a fixed-size box that is showing something taller than itself.
 *
 * A page miniature is a crop: a card shorter than the page it draws will cut whatever is at its
 * bottom edge, and a row of text sliced through the middle of its letters reads as a bug rather
 * than as a crop. This is measured as a fraction of the box's own height, because a miniature is
 * as tall as the card it is in and has no fixed size to work from.
 */
@Composable
fun Modifier.fadeBottom(fraction: Float = 0.18f): Modifier {
    val background = NulisTheme.colors.background
    return drawWithContent {
        drawContent()
        val band = size.height * fraction
        if (band <= 0f) return@drawWithContent
        drawRect(
            brush = Brush.verticalGradient(
                // Fully the background well before the edge, so a row cut by it is gone rather
                // than merely dimmed.
                0f to Color.Transparent,
                0.6f to background,
                1f to background,
                startY = size.height - band,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - band),
            size = Size(size.width, band),
        )
    }
}

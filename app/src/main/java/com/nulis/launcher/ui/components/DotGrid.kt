// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.NulisTheme

/** Faint dot-grid motif behind editing surfaces. Draws nothing in looks without the dot motif. */
@Composable
fun Modifier.dotGrid(spacing: Dp = 20.dp): Modifier {
    if (!NulisTheme.look.dotMotif) return this
    val color = NulisTheme.colors.hairline
    return drawBehind {
        val step = spacing.toPx()
        val radius = 0.9.dp.toPx()
        var y = step / 2f
        while (y < size.height) {
            var x = step / 2f
            while (x < size.width) {
                drawCircle(color, radius, Offset(x, y))
                x += step
            }
            y += step
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.LineStyle
import com.nulis.launcher.ui.theme.NulisTheme

/** A 1dp divider: dotted in the Dot look, solid in the Clean look. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color? = null) {
    val colors = NulisTheme.colors
    val dotted = NulisTheme.look.lineStyle == LineStyle.DOTTED
    val lineColor = color ?: if (dotted) colors.tertiary else colors.hairline
    Canvas(modifier.fillMaxWidth().height(2.dp)) {
        val y = size.height / 2f
        if (dotted) {
            val dot = 1.5.dp.toPx()
            drawLine(
                color = lineColor,
                start = Offset(dot, y),
                end = Offset(size.width - dot, y),
                strokeWidth = dot,
                cap = StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(0.01f, 5.dp.toPx())),
            )
        } else {
            drawLine(
                color = lineColor,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1.dp.toPx(),
            )
        }
    }
}

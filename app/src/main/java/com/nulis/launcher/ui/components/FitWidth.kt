// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.IntrinsicMeasurable
import androidx.compose.ui.layout.IntrinsicMeasureScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasurePolicy
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/**
 * Measures [content] at whatever width it wants and shrinks it to fit if that is too wide.
 *
 * Giant digits are the whole point of a clock skin, and between a Huge size setting, a large
 * system font scale and a long string like 03:24:05 there is no fixed size that always fits.
 * Wrapping a clock onto two lines looks broken and clipping it looks worse, so the type simply
 * gets smaller - which is what anyone setting type by hand would do.
 *
 * Content never grows; at or below the available width it is laid out untouched. The layout is
 * only as wide as the content unless a caller passes a modifier that asks for more (a
 * `fillMaxWidth`, say), so two of these can sit beside each other in a row.
 */
@Composable
fun FitWidth(
    modifier: Modifier = Modifier,
    align: Alignment.Horizontal = Alignment.Start,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier, measurePolicy = remember(align) { FitWidthPolicy(align) })
}

private class FitWidthPolicy(private val align: Alignment.Horizontal) : MeasurePolicy {

    // Shrinking is the whole point, so there is no width this cannot be drawn at. Saying so keeps
    // BlockFrame from shrinking the whole block around content that would have shrunk itself.
    override fun IntrinsicMeasureScope.minIntrinsicWidth(measurables: List<IntrinsicMeasurable>, height: Int): Int = 0

    override fun MeasureScope.measure(measurables: List<Measurable>, constraints: Constraints): MeasureResult {
        val placeable = measurables.firstOrNull()?.measure(Constraints())
            ?: return layout(constraints.minWidth, constraints.minHeight) {}
        val available = constraints.maxWidth
        val scale = if (available == Constraints.Infinity || placeable.width <= available) {
            1f
        } else {
            available.toFloat() / placeable.width
        }
        // Only as wide as it needs to be, unless the caller asked to fill: greedily taking the
        // whole row would push a sibling off the end of it.
        val scaledWidth = (placeable.width * scale).roundToInt()
        val width = if (available == Constraints.Infinity) {
            placeable.width
        } else {
            scaledWidth.coerceIn(constraints.minWidth, available)
        }
        val height = (placeable.height * scale).roundToInt()
        return layout(width, constraints.constrainHeight(height)) {
            val x = align.align(scaledWidth, width, layoutDirection)
            placeable.placeWithLayer(x, 0) {
                scaleX = scale
                scaleY = scale
                transformOrigin = TransformOrigin(0f, 0f)
            }
        }
    }
}

private fun Constraints.constrainHeight(height: Int) = height.coerceIn(minHeight, maxHeight)

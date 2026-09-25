// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpSize
import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.blocks.LocalBlockArea

/**
 * The rectangle a block is drawn inside.
 *
 * Every block declares the smallest grid rectangle it can live in, and every style is built to
 * reflow inside one - but a phone in landscape, a 200% font scale and a style the user has just
 * switched to can all leave a block with content taller than the cells it was given. Rather than
 * letting it spill onto its neighbour, the frame scales it down to fit, from the block's own
 * alignment edge so left-aligned text stays against the margin.
 *
 * Content that already fits is placed untouched, which is the whole of the common path: one
 * measurement, no layer, nothing to invalidate.
 */
@Composable
fun BlockFrame(
    area: DpSize,
    align: BlockAlign,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalBlockArea provides area) {
        Box(
            modifier = modifier.clipToBounds(),
            contentAlignment = align.box,
        ) {
            Box(Modifier.shrinkToFit(align)) { content() }
        }
    }
}

/**
 * How much wider than its rectangle a block may be laid out before being scaled back down. Past
 * this a single long word - a pasted link in a note - is allowed to break rather than shrinking
 * the whole block to nothing.
 */
private const val MaxWiden = 4f

private fun Modifier.shrinkToFit(align: BlockAlign): Modifier = layout { measurable, constraints ->
    val room = constraints.maxHeight
    val width = constraints.maxWidth
    // A word is never broken in the middle. Text that is simply longer than the block wraps
    // between words, as it should; but a number or a word wider than the block itself used to be
    // broken wherever it ran out of room - "5,24" over "0", "7 6 %" stacked a digit a line - and
    // that reads as broken, not as small. So the block is laid out as wide as its longest word
    // needs and scaled down to the width it actually has, the way the clock's digits already
    // were. Anything built on a subcomposition cannot answer the question, and keeps its old
    // behaviour.
    val needed = if (width == Constraints.Infinity) {
        0
    } else {
        runCatching { measurable.minIntrinsicWidth(Constraints.Infinity) }.getOrDefault(0)
    }
    val widened = needed > width && width > 0
    val layoutWidth = if (widened) minOf(needed, (width * MaxWiden).toInt()) else width
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = if (widened) layoutWidth else constraints.minWidth,
            maxWidth = layoutWidth,
            minHeight = 0,
            maxHeight = Constraints.Infinity,
        ),
    )
    val widthScale = if (widened) width.toFloat() / placeable.width else 1f
    val heightScale = if (room == Constraints.Infinity) 1f else room.toFloat() / (placeable.height * widthScale)
    if (widthScale >= 1f && heightScale >= 1f) {
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val scale = widthScale * minOf(1f, heightScale)
    val originX = when (align) {
        BlockAlign.LEFT -> 0f
        BlockAlign.CENTER -> 0.5f
        BlockAlign.RIGHT -> 1f
    }
    val shownWidth = if (widened) width else placeable.width
    val shownHeight = minOf((placeable.height * scale).toInt(), if (room == Constraints.Infinity) Int.MAX_VALUE else room)
    layout(shownWidth, shownHeight) {
        // A widened layout is centred on the block's own rectangle before it is scaled, so the
        // alignment edge stays where the block says it is.
        val x = if (widened) ((shownWidth - placeable.width) * originX).toInt() else 0
        placeable.placeWithLayer(x, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(originX, 0f)
        }
    }
}

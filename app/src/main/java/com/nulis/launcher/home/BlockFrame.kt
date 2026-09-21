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

private fun Modifier.shrinkToFit(align: BlockAlign): Modifier = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val room = constraints.maxHeight
    if (room == Constraints.Infinity || placeable.height <= room) {
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val scale = room.toFloat() / placeable.height
    val originX = when (align) {
        BlockAlign.LEFT -> 0f
        BlockAlign.CENTER -> 0.5f
        BlockAlign.RIGHT -> 1f
    }
    layout(placeable.width, room) {
        placeable.placeWithLayer(0, 0) {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(originX, 0f)
        }
    }
}

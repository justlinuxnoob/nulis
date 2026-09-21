// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * True inside a [ScaledPreview]. A block whose live state is empty right now (nothing playing,
 * no permission yet) can use this to show a representative sample instead, so a picker never
 * offers a row of blank cards.
 */
val LocalBlockPreview = staticCompositionLocalOf { false }

/**
 * True inside a preview that must not animate: a page miniature, of which several are on screen
 * at once. A block that runs an endless animation checks this and draws one still frame instead.
 */
val LocalPreviewStill = staticCompositionLocalOf { false }

/**
 * Renders [content] as if it were [contentWidth] wide, then scales it down to fit [modifier]'s
 * width. Touches are swallowed so the preview is purely visual. Height is clipped to whatever
 * the caller gives; wrap in a fixed-height box.
 */
@Composable
fun ScaledPreview(
    modifier: Modifier = Modifier,
    contentWidth: Dp = 330.dp,
    content: @Composable () -> Unit,
) {
    // A preview is a picture. Without this, a screen reader walks every app row inside it and a
    // keyboard tabs into controls that cannot be used.
    Box(modifier.clipToBounds().clearAndSetSemantics { }) {
        Layout(
            content = { CompositionLocalProvider(LocalBlockPreview provides true, content = content) },
            modifier = Modifier.fillMaxWidth(),
        ) { measurables, constraints ->
            val fullWidth = contentWidth.roundToPx()
            val scale = if (constraints.maxWidth == Constraints.Infinity) 1f else constraints.maxWidth.toFloat() / fullWidth
            val placeable = measurables.firstOrNull()?.measure(Constraints(maxWidth = fullWidth))
            val height = placeable?.let { (it.height * scale).roundToInt() } ?: 0
            layout(constraints.maxWidth, constraints.constrainHeight(height)) {
                placeable?.placeWithLayer(0, 0) {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
        // An overlay with a pointer-input node is what hit testing finds first, so the preview's own
        // clickables underneath never see a touch. It consumes nothing, so the card's click and any
        // scrolling parent still work.
        Box(Modifier.matchParentSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } })
    }
}

/** Lets a child extend [horizontal] beyond its parent's padding on both sides, e.g. an edge-to-edge scrolling row. */
fun Modifier.bleed(horizontal: Dp): Modifier = layout { measurable, constraints ->
    val extra = (horizontal * 2).roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = constraints.maxWidth + extra))
    layout(constraints.maxWidth, placeable.height) {
        placeable.place(-horizontal.roundToPx(), 0)
    }
}

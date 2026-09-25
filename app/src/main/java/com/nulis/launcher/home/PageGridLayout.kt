// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.GridRect
import com.nulis.launcher.blocks.GridSpan
import com.nulis.launcher.blocks.PageGrid
import com.nulis.launcher.ui.theme.NulisMotion
import kotlin.math.roundToInt

/**
 * Where the cells of a page land.
 *
 * The grid fills the page exactly: six columns and five gutters across, twelve rows and eleven
 * gutters down. A cell step is therefore one cell plus one gutter, which makes every other
 * number here a multiplication.
 */
@Immutable
class GridMetrics(
    val pageWidth: Dp,
    val pageHeight: Dp,
    val gutter: Dp,
    private val density: Density,
) {
    private val stepX: Dp = (pageWidth + gutter) / PageGrid.COLUMNS
    private val stepY: Dp = (pageHeight + gutter) / PageGrid.ROWS

    val cellWidth: Dp get() = stepX - gutter
    val cellHeight: Dp get() = stepY - gutter

    fun x(col: Int): Dp = stepX * col
    fun y(row: Int): Dp = stepY * row
    fun width(cols: Int): Dp = (stepX * cols - gutter).coerceAtLeast(0.dp)
    fun height(rows: Int): Dp = (stepY * rows - gutter).coerceAtLeast(0.dp)

    fun sizeOf(rect: GridRect): DpSize = DpSize(width(rect.cols), height(rect.rows))

    // Pixel forms, for gestures. A drag never touches composition, so it works in px throughout.
    val stepXpx: Float get() = with(density) { stepX.toPx() }
    val stepYpx: Float get() = with(density) { stepY.toPx() }

    /** How many whole cells a drag of this many pixels has travelled. */
    fun colsMoved(dx: Float): Int = if (stepXpx <= 0f) 0 else (dx / stepXpx).roundToInt()
    fun rowsMoved(dy: Float): Int = if (stepYpx <= 0f) 0 else (dy / stepYpx).roundToInt()

    /** The span a rectangle dragged out by this many pixels wants. */
    fun spanFor(baseCols: Int, baseRows: Int, dx: Float, dy: Float): GridSpan = GridSpan(
        cols = (baseCols + colsMoved(dx)).coerceIn(1, PageGrid.COLUMNS),
        rows = (baseRows + rowsMoved(dy)).coerceIn(1, PageGrid.ROWS),
    )
}

/**
 * How much bigger than on a phone a block's contents are drawn, for a page whose cells are.
 *
 * On a tablet the page grows with the screen, but a clock or an app list drawn inside a block
 * twice the size of a phone's is still a phone's clock and a phone's app list, sitting in the
 * middle of a lot of nothing. So the block's contents grow with its cells: everything inside -
 * type, icons, strokes, spacing - is drawn at this multiple. The reference cell is a little
 * bigger than the biggest phone's, so a phone always gets exactly 1 and draws exactly as it did;
 * a landscape tablet with short cells does too, because the smaller of the two ratios decides.
 */
fun blockScale(cellWidth: Dp, cellHeight: Dp): Float {
    val scale = minOf(cellWidth / ReferenceCellWidth, cellHeight / ReferenceCellHeight)
    return if (scale < MinScaleUp) 1f else scale.coerceAtMost(MaxScaleUp)
}

private val ReferenceCellWidth = 64.dp
private val ReferenceCellHeight = 72.dp

/** Below this the difference is not worth a different density; above it, type starts to shout. */
private const val MinScaleUp = 1.1f
private const val MaxScaleUp = 2f

/**
 * [content] drawn [scale] times bigger inside a rectangle of [area], by lending it a denser
 * density: every dp and sp inside grows, and the rectangle it is told about shrinks by the same
 * factor, so it still fills exactly the cells it was given. At a scale of 1 - every phone - it is
 * the content itself, with nothing wrapped around it.
 */
@Composable
fun ScaledBlock(scale: Float, area: DpSize, content: @Composable (DpSize) -> Unit) {
    if (scale == 1f) {
        content(area)
        return
    }
    val density = LocalDensity.current
    val scaled = remember(density, scale) { Density(density.density * scale, density.fontScale) }
    CompositionLocalProvider(LocalDensity provides scaled) {
        content(DpSize(area.width / scale, area.height / scale))
    }
}

/**
 * A page: every block placed on the grid, filling the rectangle it was given exactly.
 *
 * Nothing here scrolls and nothing here stacks. The grid is the screen, so a page can never be
 * taller than one, and a block that has been given four cells gets four cells whether its own
 * content wants three or five - [BlockFrame] is what makes the content fit.
 *
 * [rectOf] lets the editor draw a block somewhere other than where it is saved, which is how a
 * dragged block's neighbours slide out of the way before anything has been committed.
 */
@Composable
fun PageBoard(
    blocks: List<Block>,
    gutter: Dp,
    modifier: Modifier = Modifier,
    onMetrics: (GridMetrics) -> Unit = {},
    rectOf: (Block) -> GridRect = { it.area },
    /**
     * True while blocks should glide between rectangles rather than jump. The editor turns it on
     * so that a block making room for a dragged one slides out of the way; a live page leaves it
     * off, because a page that is not being edited never moves and must not pay for the state
     * reads that would let it.
     */
    animate: Boolean = false,
    cell: @Composable (block: Block, area: DpSize) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val metrics = remember(maxWidth, maxHeight, gutter, density) {
            GridMetrics(maxWidth, maxHeight, gutter, density)
        }
        SideEffect { onMetrics(metrics) }
        val scale = remember(metrics) { blockScale(metrics.cellWidth, metrics.cellHeight) }
        blocks.forEach { block ->
            key(block.id) {
                val rect = rectOf(block)
                val x = metrics.x(rect.col)
                val y = metrics.y(rect.row)
                val area = metrics.sizeOf(rect)
                if (animate) {
                    val ax by animateDpAsState(x, NulisMotion.slotDp, label = "x")
                    val ay by animateDpAsState(y, NulisMotion.slotDp, label = "y")
                    val aw by animateDpAsState(area.width, NulisMotion.slotDp, label = "w")
                    val ah by animateDpAsState(area.height, NulisMotion.slotDp, label = "h")
                    // The lambda overload: the animated offset is read in the layout pass
                    // rather than in composition, so a block sliding out of the way does not
                    // recompose anything, including itself.
                    Box(
                        Modifier
                            .offset { IntOffset(ax.roundToPx(), ay.roundToPx()) }
                            .size(aw, ah),
                    ) { ScaledBlock(scale, DpSize(aw, ah)) { cell(block, it) } }
                } else {
                    Box(Modifier.offset(x = x, y = y).size(area)) { ScaledBlock(scale, area) { cell(block, it) } }
                }
            }
        }
    }
}

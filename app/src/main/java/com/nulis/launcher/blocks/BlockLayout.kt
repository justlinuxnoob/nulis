// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.serialization.Serializable

/**
 * Which edge a block's content hangs from. Every style honours it: text through [textAlign],
 * stacks through [horizontal], rows through [arrangement], and anything that draws itself
 * (bars, charts, dials) by sitting in a [Box] with [box].
 */
@Serializable
enum class BlockAlign {
    LEFT, CENTER, RIGHT;

    val textAlign: TextAlign
        get() = when (this) {
            LEFT -> TextAlign.Start
            CENTER -> TextAlign.Center
            RIGHT -> TextAlign.End
        }

    val horizontal: Alignment.Horizontal
        get() = when (this) {
            LEFT -> Alignment.Start
            CENTER -> Alignment.CenterHorizontally
            RIGHT -> Alignment.End
        }

    val arrangement: Arrangement.Horizontal
        get() = when (this) {
            LEFT -> Arrangement.Start
            CENTER -> Arrangement.Center
            RIGHT -> Arrangement.End
        }

    val box: Alignment
        get() = when (this) {
            LEFT -> Alignment.CenterStart
            CENTER -> Alignment.Center
            RIGHT -> Alignment.CenterEnd
        }

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * How much of a row a block takes. Blocks of the same width pack into one row - two halves, three
 * thirds - and a full-width block always has a row to itself. A row that does not fill up keeps
 * its blocks at their own width and hangs from the page's alignment edge, so a lone half really
 * does look like half.
 */
@Serializable
enum class BlockWidth(val fraction: Float, val perRow: Int) {
    FULL(1f, 1),
    HALF(0.5f, 2),
    THIRD(1f / 3f, 3);

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }

    /** The next width in the cycle, for the one-tap control on an edit card. */
    fun next(): BlockWidth = entries[(ordinal + 1) % entries.size]
}

/**
 * Where a page's stack of blocks used to sit vertically. A grid says where each block is, so
 * this survives only to place the blocks of a page saved before the grid.
 */
@Serializable
enum class PageAnchor {
    TOP, CENTER, BOTTOM;

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * How much air is left between cells of the grid. [gap] and [topInset] are what the old stacked
 * page used and are still read by the migration; [gutter] is what a grid page draws with.
 */
@Serializable
enum class PageDensity(val gap: Dp, val topInset: Dp, val gutter: Dp) {
    COMPACT(16.dp, 48.dp, 4.dp),
    COMFORTABLE(32.dp, 72.dp, 10.dp),
    AIRY(56.dp, 104.dp, 18.dp);

    val label: String get() = name.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * The alignment the block being rendered right now was given. Provided by the page (and by
 * previews), read by every style. Styles never look at [Block.align] themselves, so a preview
 * can render the same block at a different alignment without touching the saved layout.
 */
val LocalBlockAlign = staticCompositionLocalOf { BlockAlign.LEFT }

@Composable
@ReadOnlyComposable
fun blockAlign(): BlockAlign = LocalBlockAlign.current

/** A column whose children hang from the block's alignment edge. */
@Composable
inline fun BlockColumn(
    modifier: Modifier = Modifier,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = verticalArrangement,
        horizontalAlignment = LocalBlockAlign.current.horizontal,
        content = content,
    )
}

/** A row pushed to the block's alignment edge. Fills width so the arrangement has room to work. */
@Composable
inline fun BlockRow(
    modifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = LocalBlockAlign.current.arrangement,
        verticalAlignment = verticalAlignment,
        content = content,
    )
}

/**
 * Wraps content that draws itself at a natural width - a meter, a chart, a dial - so that it
 * sits against the block's alignment edge instead of always stretching edge to edge.
 * [fraction] is how much of the page width the content is allowed to take.
 */
@Composable
inline fun BlockBox(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = LocalBlockAlign.current.box,
        content = content,
    )
}

/**
 * A block's caption line: a label and an optional trailing readout. Left-aligned it spreads to
 * both edges like a panel legend; centred or right-aligned the two sit together against that
 * edge, because a legend pinned to the far side of a right-aligned block reads as a mistake.
 */
@Composable
fun BlockCaptionRow(
    label: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val align = LocalBlockAlign.current
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // A third of a row has no business carrying a legend and a readout both: at that width
        // one of them is going to be cut, and the reading is the half worth keeping.
        val showTrailing = trailing != null && maxWidth >= CompactBlockWidth
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = align.arrangement,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (align) {
                BlockAlign.LEFT -> {
                    com.nulis.launcher.ui.components.Caption(label)
                    Spacer(Modifier.weight(1f))
                    // The padding rather than the spacer is what guarantees the gap: on a narrow
                    // block the weighted spacer collapses to nothing and the two words touch.
                    if (showTrailing) {
                        com.nulis.launcher.ui.components.Caption(trailing!!, modifier = Modifier.padding(start = 10.dp))
                    }
                }
                BlockAlign.RIGHT -> {
                    if (showTrailing) {
                        com.nulis.launcher.ui.components.Caption(trailing!!, modifier = Modifier.padding(end = 10.dp))
                    }
                    com.nulis.launcher.ui.components.Caption(label)
                }
                BlockAlign.CENTER -> {
                    com.nulis.launcher.ui.components.Caption(label)
                    if (showTrailing) {
                        com.nulis.launcher.ui.components.Caption(trailing!!, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }
        }
    }
}

/**
 * False while this block's page is not the one the user is looking at: another page of the
 * pager, or a page hidden under a fully open drawer, or the whole launcher in the background.
 *
 * All three pages stay composed so paging never has to compose mid-gesture, which means an
 * animated block would otherwise keep a frame callback alive forever. Every block that animates
 * gates its loop on [blockIsAnimating], so an off-screen page renders nothing at all.
 */
val LocalBlockActive = staticCompositionLocalOf { true }

/**
 * True when a block should be running its animation: it is on a page the user can see, and it
 * is not a still preview inside a picker.
 */
@Composable
@ReadOnlyComposable
fun blockIsAnimating(): Boolean =
    LocalBlockActive.current &&
        !com.nulis.launcher.ui.components.LocalPreviewStill.current &&
        !com.nulis.launcher.ui.theme.LocalReducedMotion.current

/**
 * The rectangle the block being rendered right now was given, in dp. A style that wants to know
 * whether it has room for a caption, a second line or an album cover asks this rather than
 * measuring itself; previews provide a believable one.
 */
val LocalBlockArea = staticCompositionLocalOf { DpSize(320.dp, 96.dp) }

@Composable
@ReadOnlyComposable
fun blockArea(): DpSize = LocalBlockArea.current

/**
 * How tall a bar, chart or meter should be drawn inside the rectangle its block was given.
 *
 * A block is a rectangle on the grid and everything in it belongs to that rectangle: give a
 * chart four rows instead of two and it should be twice as tall, not the same height with air
 * under it. [reserved] is what the lines above and below the chart take - a caption, a readout,
 * the spacing between them - and the chart gets the rest, never less than [min].
 *
 * Deliberately generous with [reserved]: asking for a cell more than there is makes [BlockFrame]
 * scale the whole block down to fit, and a chart that shrinks its own captions is worse than a
 * chart with a few dp of air beneath it.
 */
@Composable
@ReadOnlyComposable
fun chartHeight(min: Dp, reserved: Dp): Dp {
    val room = LocalBlockArea.current.height - reserved
    return if (room > min) room else min
}

/**
 * The same for anything round - a ring, a dial - which is bounded by the shorter side of the
 * rectangle rather than by its height alone.
 */
@Composable
@ReadOnlyComposable
fun dialSize(min: Dp, reserved: Dp): Dp {
    val area = LocalBlockArea.current
    val room = minOf(area.width, area.height - reserved)
    return if (room > min) room else min
}

/** Under this a block has room for one line and no legend. */
val CompactBlockHeight: Dp = 64.dp

/**
 * Under this a block is a narrow column: no trailing readouts, no legends beside a number.
 *
 * 190 dp rather than 150: at 170 - a third of a phone - "21m SCREEN TIME" kept its legend and
 * then cut it to "21m SCREEN ...", which is worse than either half on its own.
 */
val CompactBlockWidth: Dp = 190.dp

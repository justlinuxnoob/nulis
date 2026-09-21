// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * What a block looks like when it has nothing to say yet.
 *
 * A block with no content used to be a sentence of instructions floating in the middle of a
 * rectangle - "tap to work something out", "long-press to add a date" - which told you what to do
 * and nothing at all about what you would get. Worse, half of them were out of date the night a
 * long press anywhere started opening the editor.
 *
 * An empty block draws the *shape* of what it will hold instead: ruled lines for something you
 * write on, an unticked box for a checklist, a row of empty discs for people, a readout showing
 * zero. Faint, never interactive on their own, and silent to a screen reader - the block itself
 * carries the label and the tap.
 *
 * Everything here hangs from the block's own alignment edge, so an empty block sits where a full
 * one would.
 */

/** How far down towards the background a placeholder is drawn. Present, never competing. */
private const val GhostAlpha = 0.45f

/** Ruled lines, for a block you write on: notes, the journal, a quote. */
@Composable
fun WritingLines(
    lines: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
) {
    val align = blockAlign()
    Column(modifier.fillMaxWidth().alpha(GhostAlpha).clearAndSetSemantics { }) {
        repeat(lines) { index ->
            // The last line is short, the way a paragraph ends. Without it three equal rules
            // read as a table rather than as writing.
            val fraction = if (index == lines - 1) 0.45f else if (index % 2 == 0) 1f else 0.82f
            Row(Modifier.fillMaxWidth(), horizontalArrangement = align.arrangement) {
                Rule(Modifier.fillMaxWidth(fraction))
            }
            if (index < lines - 1) Spacer(Modifier.height(spacing))
        }
    }
}

/** An unticked box and the line beside it, for a checklist with nothing on it yet. */
@Composable
fun ChecklistRows(
    rows: Int,
    modifier: Modifier = Modifier,
    spacing: Dp = 14.dp,
) {
    val colors = NulisTheme.colors
    Column(modifier.fillMaxWidth().alpha(GhostAlpha).clearAndSetSemantics { }) {
        repeat(rows) { index ->
            BlockRow(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(20.dp)
                        .border(1.dp, colors.hairline, RoundedCornerShape(6.dp)),
                )
                Spacer(Modifier.width(14.dp))
                Rule(Modifier.width(if (index == 0) 132.dp else 96.dp))
            }
            if (index < rows - 1) Spacer(Modifier.height(spacing))
        }
    }
}

/** Empty discs, for a block that will hold people or apps. */
@Composable
fun GhostDiscs(
    count: Int,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    val colors = NulisTheme.colors
    Row(
        modifier.fillMaxWidth().alpha(GhostAlpha).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(12.dp, blockAlign().horizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) {
            Box(Modifier.size(size).border(1.dp, colors.hairline, CircleShape))
        }
    }
}

/** One faint rule. The unit the rest of this file is built from. */
@Composable
private fun Rule(modifier: Modifier = Modifier) {
    val colors = NulisTheme.colors
    Box(
        modifier
            .height(2.dp)
            .background(colors.hairline, RoundedCornerShape(1.dp)),
    )
}

/** Empty pills, for a block that will hold a handful of small readings. */
@Composable
fun GhostPills(count: Int, modifier: Modifier = Modifier) {
    val colors = NulisTheme.colors
    Row(
        modifier.fillMaxWidth().alpha(GhostAlpha).clearAndSetSemantics { },
        horizontalArrangement = Arrangement.spacedBy(8.dp, blockAlign().horizontal),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .height(26.dp)
                    .width(if (index == 1) 84.dp else 64.dp)
                    .border(1.dp, colors.hairline, RoundedCornerShape(13.dp)),
            )
        }
    }
}

/**
 * A picture that is not there yet: the frame, a horizon and a sun. Three shapes is enough for
 * "this holds a photograph" and few enough that it never reads as a photograph itself.
 */
@Composable
fun GhostPhoto(modifier: Modifier = Modifier) {
    val colors = NulisTheme.colors
    Canvas(modifier.alpha(GhostAlpha).clearAndSetSemantics { }) {
        val stroke = 1.5.dp.toPx()
        val inset = size.minDimension * 0.18f
        val w = size.width - inset * 2
        val h = size.height - inset * 2
        if (w <= 0f || h <= 0f) return@Canvas
        drawRoundRect(
            color = colors.hairline,
            topLeft = Offset(inset, inset),
            size = Size(w, h),
            cornerRadius = CornerRadius(6.dp.toPx()),
            style = Stroke(stroke),
        )
        drawCircle(colors.hairline, h * 0.09f, Offset(inset + w * 0.26f, inset + h * 0.3f), style = Stroke(stroke))
        val base = inset + h * 0.78f
        val path = Path().apply {
            moveTo(inset + w * 0.12f, base)
            lineTo(inset + w * 0.45f, inset + h * 0.42f)
            lineTo(inset + w * 0.7f, base)
            close()
        }
        drawPath(path, colors.hairline, style = Stroke(stroke))
    }
}

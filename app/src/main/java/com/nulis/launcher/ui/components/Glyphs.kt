// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Simple stroked glyphs drawn by hand so every icon shares one weight and feel. */
enum class Glyph {
    ChevronUp, ChevronDown, ChevronLeft, ChevronRight, Close, Plus, Check, Search, ArrowLeft, ArrowRight,
    Dots, Grip, Gear, Play, Pause, Previous, Next, Layout, Pair, Resize, Copy, Trash, Undo, Align, Style,
    Minus,
}

/**
 * @param contentDescription what a screen reader should say. Null makes the glyph decorative,
 * which is right when the thing around it is already labelled.
 */
@Composable
fun GlyphIcon(
    glyph: Glyph,
    color: Color,
    modifier: Modifier = Modifier,
    size: Dp = 18.dp,
    contentDescription: String? = null,
) {
    Canvas(
        modifier
            .size(size)
            .then(
                if (contentDescription == null) {
                    Modifier.clearAndSetSemantics { }
                } else {
                    Modifier.semantics { this.contentDescription = contentDescription }
                },
            ),
    ) {
        val stroke = 1.6.dp.toPx()
        val w = this.size.width
        val h = this.size.height
        val cap = StrokeCap.Round
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * w, y1 * h), Offset(x2 * w, y2 * h), stroke, cap)
        when (glyph) {
            Glyph.ChevronUp -> { line(0.25f, 0.62f, 0.5f, 0.38f); line(0.5f, 0.38f, 0.75f, 0.62f) }
            Glyph.ChevronDown -> { line(0.25f, 0.38f, 0.5f, 0.62f); line(0.5f, 0.62f, 0.75f, 0.38f) }
            Glyph.Close -> { line(0.28f, 0.28f, 0.72f, 0.72f); line(0.72f, 0.28f, 0.28f, 0.72f) }
            Glyph.Plus -> { line(0.5f, 0.22f, 0.5f, 0.78f); line(0.22f, 0.5f, 0.78f, 0.5f) }
            Glyph.Minus -> line(0.22f, 0.5f, 0.78f, 0.5f)
            Glyph.Check -> { line(0.22f, 0.52f, 0.42f, 0.72f); line(0.42f, 0.72f, 0.78f, 0.32f) }
            Glyph.ChevronLeft -> { line(0.62f, 0.25f, 0.38f, 0.5f); line(0.38f, 0.5f, 0.62f, 0.75f) }
            Glyph.ChevronRight -> { line(0.38f, 0.25f, 0.62f, 0.5f); line(0.62f, 0.5f, 0.38f, 0.75f) }
            Glyph.ArrowLeft -> { line(0.2f, 0.5f, 0.8f, 0.5f); line(0.2f, 0.5f, 0.45f, 0.25f); line(0.2f, 0.5f, 0.45f, 0.75f) }
            Glyph.ArrowRight -> { line(0.2f, 0.5f, 0.8f, 0.5f); line(0.8f, 0.5f, 0.55f, 0.25f); line(0.8f, 0.5f, 0.55f, 0.75f) }
            Glyph.Resize -> {
                // A corner being pulled outwards: the resize handle, said in one mark.
                line(0.28f, 0.72f, 0.72f, 0.28f)
                line(0.72f, 0.28f, 0.72f, 0.5f)
                line(0.72f, 0.28f, 0.5f, 0.28f)
                line(0.28f, 0.72f, 0.28f, 0.5f)
                line(0.28f, 0.72f, 0.5f, 0.72f)
            }
            Glyph.Copy -> {
                drawRect(color, Offset(0.2f * w, 0.2f * h), androidx.compose.ui.geometry.Size(0.42f * w, 0.42f * h), style = Stroke(stroke))
                drawRect(color, Offset(0.38f * w, 0.38f * h), androidx.compose.ui.geometry.Size(0.42f * w, 0.42f * h), style = Stroke(stroke))
            }
            Glyph.Trash -> {
                line(0.18f, 0.3f, 0.82f, 0.3f)
                line(0.4f, 0.3f, 0.42f, 0.2f)
                line(0.6f, 0.3f, 0.58f, 0.2f)
                line(0.28f, 0.3f, 0.34f, 0.82f)
                line(0.72f, 0.3f, 0.66f, 0.82f)
                line(0.34f, 0.82f, 0.66f, 0.82f)
            }
            Glyph.Undo -> {
                // An arrow curling back on itself.
                drawArc(
                    color = color,
                    startAngle = 30f,
                    sweepAngle = 260f,
                    useCenter = false,
                    topLeft = Offset(0.22f * w, 0.24f * h),
                    size = androidx.compose.ui.geometry.Size(0.56f * w, 0.56f * h),
                    style = Stroke(stroke, cap = cap),
                )
                line(0.72f, 0.66f, 0.72f, 0.42f)
                line(0.72f, 0.66f, 0.5f, 0.66f)
            }
            Glyph.Align -> {
                line(0.2f, 0.26f, 0.8f, 0.26f)
                line(0.3f, 0.5f, 0.7f, 0.5f)
                line(0.2f, 0.74f, 0.8f, 0.74f)
            }
            Glyph.Style -> {
                // Two overlapping swatches: "the same block, drawn another way".
                drawCircle(color, 0.24f * w, Offset(0.38f * w, 0.5f * h), style = Stroke(stroke))
                drawCircle(color, 0.24f * w, Offset(0.62f * w, 0.5f * h), style = Stroke(stroke))
            }
            Glyph.Search -> {
                drawCircle(color, radius = 0.26f * w, center = Offset(0.42f * w, 0.42f * h), style = Stroke(stroke))
                line(0.62f, 0.62f, 0.82f, 0.82f)
            }
            Glyph.Grip -> {
                // Two columns of three dots: the universal "drag me" handle.
                val r = stroke * 0.8f
                for (x in listOf(0.38f, 0.62f)) for (y in listOf(0.28f, 0.5f, 0.72f)) drawCircle(color, r, Offset(x * w, y * h))
            }
            Glyph.Gear -> {
                // One continuous cog outline plus a hub. Detached spokes read as a sun at 18dp;
                // a closed outline where the teeth are joined by the root circle reads as a gear.
                val c = Offset(0.5f * w, 0.5f * h)
                val teeth = 7
                val outer = 0.42f * w
                val root = 0.31f * w
                val step = (2.0 * Math.PI / teeth).toFloat()
                // Each tooth: flat top, then a short flank down to the root arc between teeth.
                val top = step * 0.34f
                val flank = step * 0.11f
                val path = Path()
                for (i in 0 until teeth) {
                    val start = i * step - top / 2f
                    fun at(angle: Float, r: Float) = Offset(c.x + kotlin.math.cos(angle) * r, c.y + kotlin.math.sin(angle) * r)
                    if (i == 0) path.moveTo(at(start, outer).x, at(start, outer).y) else path.lineTo(at(start, outer).x, at(start, outer).y)
                    path.lineTo(at(start + top, outer).x, at(start + top, outer).y)
                    path.lineTo(at(start + top + flank, root).x, at(start + top + flank, root).y)
                    // The gap between two teeth follows the root circle, so the teeth stay connected.
                    val gapEnd = start + step - flank
                    var a = start + top + flank
                    while (a < gapEnd) {
                        a = minOf(a + step * 0.09f, gapEnd)
                        path.lineTo(at(a, root).x, at(a, root).y)
                    }
                    path.lineTo(at(start + step, outer).x, at(start + step, outer).y)
                }
                path.close()
                drawPath(path, color, style = Stroke(stroke, join = StrokeJoin.Round))
                drawCircle(color, radius = 0.13f * w, center = c, style = Stroke(stroke))
            }
            // Transport controls. The triangles are filled so they hold their shape next to the
            // two hairline bars of Pause, which no stroked triangle this small manages.
            Glyph.Play -> drawPath(triangle(w, h, 0.32f, 0.72f, 0.5f), color)
            Glyph.Pause -> { line(0.38f, 0.28f, 0.38f, 0.72f); line(0.62f, 0.28f, 0.62f, 0.72f) }
            Glyph.Previous -> {
                drawPath(triangle(w, h, 0.9f, 0.34f, 0.5f), color)
                line(0.24f, 0.28f, 0.24f, 0.72f)
            }
            Glyph.Next -> {
                drawPath(triangle(w, h, 0.1f, 0.66f, 0.5f), color)
                line(0.76f, 0.28f, 0.76f, 0.72f)
            }
            Glyph.Layout -> {
                // Three stacked rules of different lengths: a page seen from far away.
                line(0.2f, 0.28f, 0.8f, 0.28f)
                line(0.2f, 0.5f, 0.62f, 0.5f)
                line(0.2f, 0.72f, 0.74f, 0.72f)
            }
            Glyph.Pair -> {
                // Two panes side by side: the "share a row" mark.
                drawRect(color, Offset(0.16f * w, 0.28f * h), androidx.compose.ui.geometry.Size(0.3f * w, 0.44f * h), style = Stroke(stroke))
                drawRect(color, Offset(0.54f * w, 0.28f * h), androidx.compose.ui.geometry.Size(0.3f * w, 0.44f * h), style = Stroke(stroke))
            }
            Glyph.Dots -> {
                val r = stroke * 0.9f
                drawCircle(color, r, Offset(0.25f * w, 0.5f * h))
                drawCircle(color, r, Offset(0.5f * w, 0.5f * h))
                drawCircle(color, r, Offset(0.75f * w, 0.5f * h))
            }
        }
    }
}

/** A triangle from [backX] to the [tipX] point, centred on [cy], in 0..1 glyph space. */
private fun triangle(w: Float, h: Float, backX: Float, tipX: Float, cy: Float): Path {
    val half = 0.22f
    return Path().apply {
        moveTo(backX * w, (cy - half) * h)
        lineTo(tipX * w, cy * h)
        lineTo(backX * w, (cy + half) * h)
        close()
    }
}

/** What a screen reader calls this glyph when the control around it has no better name. */
val Glyph.spokenName: String
    get() = when (this) {
        Glyph.ChevronUp -> "Up"
        Glyph.ChevronDown -> "Down"
        Glyph.ChevronLeft -> "Move left"
        Glyph.ChevronRight -> "Move right"
        Glyph.ArrowRight -> "Forward"
        Glyph.Resize -> "Resize"
        Glyph.Copy -> "Duplicate"
        Glyph.Trash -> "Delete"
        Glyph.Undo -> "Undo"
        Glyph.Align -> "Align"
        Glyph.Style -> "Style"
        Glyph.Close -> "Close"
        Glyph.Plus -> "Add"
        Glyph.Minus -> "Less"
        Glyph.Check -> "Done"
        Glyph.Search -> "Search"
        Glyph.ArrowLeft -> "Back"
        Glyph.Dots -> "More"
        Glyph.Grip -> "Drag handle"
        Glyph.Gear -> "Settings"
        Glyph.Play -> "Play"
        Glyph.Pause -> "Pause"
        Glyph.Previous -> "Previous track"
        Glyph.Next -> "Next track"
        Glyph.Layout -> "Page layout"
        Glyph.Pair -> "Shares a row"
    }

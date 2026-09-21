// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.`fun`

import com.nulis.launcher.blocks.GridSpan
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.blockIsAnimating
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * A clock that only runs while the block is on a page the user can see. Returns seconds since
 * the block came into view; off screen it stops asking for frames entirely, which is the whole
 * point - all three pages stay composed, so an unguarded animation would never stop.
 */
@Composable
private fun rememberSeconds(speed: Float = 1f): Float {
    val animating = blockIsAnimating()
    var seconds by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animating, speed) {
        if (!animating) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            seconds += ((now - previous) / 1e9f) * speed
            previous = now
        }
    }
    return seconds
}

// --------------------------------------------------------------------- dot cat

/**
 * A cat, drawn in squares, running along the bottom of the block. It runs faster the more you
 * have walked today: a still cat means a still day.
 */
object DotCatBlockDefinition : BlockDefinition {
    override val type = "dotcat"
    override val label = "Dot cat"

    override val minSpan = GridSpan(2, 1)
    override val defaultSpan = GridSpan(6, 2)
    override val styles: List<BlockStyle> = listOf(RunStyle, SitStyle)
    override val defaultSize get() = BlockSize.WIDE
    override val previewHeight get() = 96.dp

    /** Steps per day at which the cat is at a full sprint. */
    private const val SprintSteps = 12_000f

    private object RunStyle : BlockStyle {
        override val id = "run"
        override val label = "Running"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val steps = context.steps.today
            // A cat that never moves is sad; one that moves at a walk with no steps is honest.
            val speed = 0.35f + (steps / SprintSteps).coerceIn(0f, 1f) * 1.65f
            val seconds = rememberSeconds(speed)
            BlockColumn(modifier) {
                BlockCaptionRow("Dot cat", trailing = if (context.steps.granted) "${"%,d".format(steps)} steps" else null)
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.fillMaxWidth().height(if (wide) 72.dp else 48.dp)) {
                    val unit = size.height / 14f
                    val ground = size.height - unit
                    drawLine(colors.hairline, Offset(0f, ground), Offset(size.width, ground), unit * 0.6f)
                    // The cat loops across and comes back on again, like a sprite on a strip.
                    val travel = (seconds * unit * 22f) % (size.width + unit * 14f)
                    val x = travel - unit * 14f
                    drawCat(x, ground, unit, seconds, colors.onBackground)
                }
            }
        }
    }

    private object SitStyle : BlockStyle {
        override val id = "sit"
        override val label = "Sitting"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val seconds = rememberSeconds(0.4f)
            BlockColumn(modifier) {
                Canvas(Modifier.fillMaxWidth().height(if (wide) 72.dp else 48.dp)) {
                    val unit = size.height / 12f
                    val ground = size.height - unit
                    // Sitting still, tail swishing, blinking now and then.
                    drawSittingCat(unit * 3f, ground, unit, seconds, colors.onBackground, colors.background)
                }
            }
        }
    }

    /** Every part of the cat is a whole cell, so it belongs to the same grid as the digits. */
    private fun DrawScope.drawCat(x: Float, ground: Float, unit: Float, seconds: Float, color: Color) {
        fun cell(cx: Int, cy: Int) = drawRect(
            color = color,
            topLeft = Offset(x + cx * unit, ground - (cy + 1) * unit),
            size = Size(unit, unit),
        )
        // Body and head.
        for (cx in 2..7) cell(cx, 2)
        for (cx in 2..7) cell(cx, 3)
        for (cx in 7..9) cell(cx, 4)
        cell(7, 5)
        cell(9, 5)
        // Ears.
        cell(7, 6)
        cell(9, 6)
        // Tail, flicking with the stride.
        val tail = (sin(seconds * 9f) * 1.4f).roundToInt()
        cell(1, 3 + tail.coerceIn(0, 2))
        cell(0, 4 + tail.coerceIn(0, 2))
        // Four legs on a two-beat gallop.
        val phase = ((seconds * 9f) % (2 * PI)).toFloat()
        val front = if (sin(phase) > 0) 1 else 0
        val back = if (sin(phase) > 0) 0 else 1
        cell(3, 1 - back)
        cell(4, 1 - back + back)
        cell(6, 1 - front)
        cell(7, 1 - front + front)
    }

    private fun DrawScope.drawSittingCat(
        x: Float,
        ground: Float,
        unit: Float,
        seconds: Float,
        color: Color,
        background: Color,
    ) {
        fun cell(cx: Int, cy: Int) = drawRect(
            color = color,
            topLeft = Offset(x + cx * unit, ground - (cy + 1) * unit),
            size = Size(unit, unit),
        )
        for (cy in 0..3) for (cx in 3..6) cell(cx, cy)
        for (cx in 3..6) cell(cx, 4)
        cell(3, 5)
        cell(6, 5)
        // The tail sweeps a slow arc along the ground.
        val sweep = (sin(seconds * 1.6f) * 2.6f).roundToInt()
        cell(2 + sweep.coerceIn(-2, 2), 0)
        cell(1 + sweep.coerceIn(-1, 3), 1)
        // Eyes, closed for a moment every few seconds. The sitting cat's head is a solid block of
        // [color], so the eyes have to be knocked out of it in the background: drawn in the cat's
        // own colour they were invisible, and the whole animal read as a rectangle with two ears.
        // They belong to the head row (cy 4), not the body row below it.
        val blinking = (seconds % 4.4f) < 0.16f
        if (!blinking) {
            val eyeTop = ground - 5 * unit + unit * 0.25f
            val eye = Size(unit * 0.5f, unit * 0.5f)
            drawRect(color = background, topLeft = Offset(x + 3.9f * unit, eyeTop), size = eye)
            drawRect(color = background, topLeft = Offset(x + 5.6f * unit, eyeTop), size = eye)
        }
    }
}

// ------------------------------------------------------------------ life

/**
 * Conway's Game of Life on a small grid. It reseeds itself when it settles, so it never becomes
 * a still picture pretending to be an animation. One step every third of a second: this is
 * something to glance at, not a screensaver.
 */
object LifeBlockDefinition : BlockDefinition {
    override val type = "life"
    override val label = "Life"

    override val minSpan = GridSpan(2, 2)
    override val defaultSpan = GridSpan(6, 3)
    override val styles: List<BlockStyle> = listOf(DotsStyle, CellsStyle)
    override val previewHeight get() = 110.dp

    private const val StepSeconds = 0.33f

    private object DotsStyle : BlockStyle {
        override val id = "dots"
        override val label = "Dots"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) = Board(block, rounded = true, modifier = modifier)
    }

    private object CellsStyle : BlockStyle {
        override val id = "cells"
        override val label = "Cells"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) = Board(block, rounded = false, modifier = modifier)
    }

    @Composable
    private fun Board(block: Block, rounded: Boolean, modifier: Modifier) {
        val colors = NulisTheme.colors
        val haptics = LocalHapticFeedback.current
        val wide = block.size == BlockSize.WIDE
        val columns = if (wide) 32 else 22
        val rows = if (wide) 16 else 10
        val world = remember(columns, rows) { LifeWorld(columns, rows) }
        var generation by remember { mutableIntStateOf(0) }
        val seconds = rememberSeconds()
        // Stepping off the frame time rather than a timer keeps it in sync with what is drawn.
        val step = (seconds / StepSeconds).toInt()
        LaunchedEffect(step) {
            if (step > generation) {
                world.step()
                generation = step
            }
        }
        BlockColumn(
            modifier
                .pressFeedback()
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    haptics.performHapticFeedback(NulisHaptics.tick)
                    world.seed()
                },
        ) {
            BlockCaptionRow("Life", trailing = "gen ${world.generation}")
            Spacer(Modifier.height(8.dp))
            Canvas(Modifier.fillMaxWidth().height(if (wide) 96.dp else 64.dp)) {
                val cellW = size.width / columns
                val cellH = size.height / rows
                val radius = minOf(cellW, cellH) * 0.42f
                for (y in 0 until rows) {
                    for (x in 0 until columns) {
                        if (!world.alive(x, y)) continue
                        val cx = (x + 0.5f) * cellW
                        val cy = (y + 0.5f) * cellH
                        if (rounded) {
                            drawCircle(colors.onBackground, radius, Offset(cx, cy))
                        } else {
                            drawRect(
                                colors.onBackground,
                                Offset(cx - radius, cy - radius),
                                Size(radius * 2, radius * 2),
                            )
                        }
                    }
                }
            }
        }
    }

    /** The board. Kept out of composition so a step never allocates a new grid. */
    private class LifeWorld(val columns: Int, val rows: Int) {
        private var cells = BooleanArray(columns * rows)
        private var next = BooleanArray(columns * rows)
        private var stale = 0
        var generation = 0
            private set

        init {
            seed()
        }

        fun alive(x: Int, y: Int) = cells[y * columns + x]

        fun seed() {
            val random = Random(System.nanoTime())
            for (i in cells.indices) cells[i] = random.nextFloat() < 0.32f
            generation = 0
            stale = 0
        }

        fun step() {
            var changed = false
            for (y in 0 until rows) {
                for (x in 0 until columns) {
                    var neighbours = 0
                    for (dy in -1..1) {
                        for (dx in -1..1) {
                            if (dx == 0 && dy == 0) continue
                            // The board wraps, so gliders sail off one edge and back on the other.
                            val nx = (x + dx + columns) % columns
                            val ny = (y + dy + rows) % rows
                            if (cells[ny * columns + nx]) neighbours++
                        }
                    }
                    val was = cells[y * columns + x]
                    val now = if (was) neighbours == 2 || neighbours == 3 else neighbours == 3
                    next[y * columns + x] = now
                    if (now != was) changed = true
                }
            }
            val swap = cells
            cells = next
            next = swap
            generation++
            // A board that stops changing (or oscillates in place) gets a fresh start rather
            // than pretending to be alive.
            stale = if (changed) 0 else stale + 1
            if (stale > 4 || generation > 600) seed()
        }
    }
}

// --------------------------------------------------------------------- pet

/**
 * A small creature that lives on the page. It wanders, naps, and looks up when you tap it.
 * It has no needs and cannot be neglected, which is the entire design: it is company, not a
 * chore.
 */
object PetBlockDefinition : BlockDefinition {
    override val type = "pet"
    override val label = "Pet"

    override val minSpan = GridSpan(2, 1)
    override val defaultSpan = GridSpan(6, 2)
    override val styles: List<BlockStyle> = listOf(BlobStyle, BirdStyle)
    override val previewHeight get() = 96.dp

    private object BlobStyle : BlockStyle {
        override val id = "blob"
        override val label = "Blob"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) = Pet(block, bird = false, modifier = modifier)
    }

    private object BirdStyle : BlockStyle {
        override val id = "bird"
        override val label = "Bird"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) = Pet(block, bird = true, modifier = modifier)
    }

    @Composable
    private fun Pet(block: Block, bird: Boolean, modifier: Modifier) {
        val colors = NulisTheme.colors
        val haptics = LocalHapticFeedback.current
        val wide = block.size == BlockSize.WIDE
        val seconds = rememberSeconds()
        var pokedAt by remember { mutableFloatStateOf(-10f) }
        val startled = (seconds - pokedAt) in 0f..1.2f
        BlockColumn(
            modifier
                .pressFeedback()
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    haptics.performHapticFeedback(NulisHaptics.tick)
                    pokedAt = seconds
                },
        ) {
            Canvas(Modifier.fillMaxWidth().height(if (wide) 80.dp else 56.dp)) {
                val ground = size.height - 2.dp.toPx()
                val body = size.height * 0.42f
                // A slow wander with a pause at each end, so it never looks like a pendulum.
                val t = (seconds * 0.16f) % 1f
                val eased = (sin((t * 2 - 0.5f) * PI).toFloat() + 1f) / 2f
                val x = body + (size.width - body * 2) * eased
                val hop = if (startled) abs(sin((seconds - pokedAt) * 12f)) * body * 0.5f else 0f
                val squash = 1f + sin(seconds * 2.1f) * 0.06f
                val cy = ground - body * 0.9f - hop

                if (bird) {
                    drawBird(x, cy, body, seconds, startled, colors.onBackground, colors.background)
                } else {
                    drawBlob(x, cy, body, squash, startled, seconds, colors.onBackground, colors.background)
                }
                drawLine(colors.hairline, Offset(0f, ground), Offset(size.width, ground), 2.dp.toPx(), StrokeCap.Round)
            }
            Spacer(Modifier.height(6.dp))
            BlockCaptionRow(if (startled) "Hello" else "Tap to say hello")
        }
    }

    private fun DrawScope.drawBlob(
        x: Float,
        y: Float,
        body: Float,
        squash: Float,
        startled: Boolean,
        seconds: Float,
        color: Color,
        eyeColor: Color,
    ) {
        drawCircle(color, body * squash, Offset(x, y))
        val eye = body * 0.16f
        val blink = !startled && (seconds % 5.2f) < 0.14f
        if (blink) {
            drawLine(eyeColor, Offset(x - body * 0.42f, y - body * 0.12f), Offset(x - body * 0.1f, y - body * 0.12f), eye)
            drawLine(eyeColor, Offset(x + body * 0.1f, y - body * 0.12f), Offset(x + body * 0.42f, y - body * 0.12f), eye)
        } else {
            drawCircle(eyeColor, eye, Offset(x - body * 0.26f, y - body * 0.12f))
            drawCircle(eyeColor, eye, Offset(x + body * 0.26f, y - body * 0.12f))
        }
        if (startled) {
            // Two little arcs over its head, the universal mark for "oh!"
            drawArc(
                color = color,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(x - body * 0.5f, y - body * 1.9f),
                size = Size(body, body * 0.7f),
                style = Stroke(body * 0.1f, cap = StrokeCap.Round),
            )
        }
    }

    private fun DrawScope.drawBird(
        x: Float,
        y: Float,
        body: Float,
        seconds: Float,
        startled: Boolean,
        color: Color,
        eyeColor: Color,
    ) {
        drawOval(color, Offset(x - body, y - body * 0.7f), Size(body * 2f, body * 1.4f))
        drawCircle(color, body * 0.55f, Offset(x + body * 0.6f, y - body * 0.8f))
        // Beak.
        val beak = androidx.compose.ui.graphics.Path().apply {
            moveTo(x + body * 1.05f, y - body * 0.9f)
            lineTo(x + body * 1.6f, y - body * 0.72f)
            lineTo(x + body * 1.05f, y - body * 0.55f)
            close()
        }
        drawPath(beak, color)
        // Wing, beating when startled and resting otherwise.
        val lift = if (startled) sin((seconds) * 22f) * body * 0.5f else sin(seconds * 1.3f) * body * 0.06f
        drawOval(eyeColor, Offset(x - body * 0.7f, y - body * 0.35f - lift), Size(body * 1.1f, body * 0.6f))
        drawOval(color, Offset(x - body * 0.7f, y - body * 0.35f - lift), Size(body * 1.1f, body * 0.6f), style = Stroke(body * 0.08f))
        drawCircle(eyeColor, body * 0.12f, Offset(x + body * 0.75f, y - body * 0.9f))
        drawLine(color, Offset(x - body * 0.3f, y + body * 0.65f), Offset(x - body * 0.3f, y + body * 1.0f), body * 0.1f)
        drawLine(color, Offset(x + body * 0.2f, y + body * 0.65f), Offset(x + body * 0.2f, y + body * 1.0f), body * 0.1f)
    }
}

// --------------------------------------------------------------------- hourglass

/**
 * My own addition: an hourglass that drains over the hour and turns itself over on the hour.
 * It is a clock you read without reading, and it is made of exactly the same dots as the rest
 * of the launcher.
 */
object HourglassBlockDefinition : BlockDefinition {
    override val type = "hourglass"
    override val label = "Hourglass"

    override val minSpan = GridSpan(2, 2)
    override val defaultSpan = GridSpan(6, 3)
    override val styles: List<BlockStyle> = listOf(HourStyle, DayStyle)
    override val previewHeight get() = 120.dp

    private object HourStyle : BlockStyle {
        override val id = "hour"
        override val label = "This hour"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val minutes = context.time.minute + context.time.second / 60f
            Glass(block, modifier, minutes / 60f, "${60 - context.time.minute} min left in the hour")
        }
    }

    private object DayStyle : BlockStyle {
        override val id = "day"
        override val label = "Today"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val minutes = context.time.hour * 60 + context.time.minute
            val left = (1440 - minutes + 59) / 60
            Glass(block, modifier, minutes / 1440f, if (left == 1) "1 hour left today" else "$left hours left today")
        }
    }

    @Composable
    private fun Glass(block: Block, modifier: Modifier, drained: Float, caption: String) {
        val colors = NulisTheme.colors
        val wide = block.size == BlockSize.WIDE
        val seconds = rememberSeconds()
        BlockColumn(modifier) {
            Canvas(Modifier.fillMaxWidth().height(if (wide) 110.dp else 72.dp)) {
                val h = size.height
                val w = h * 0.62f
                val left = (size.width - w) / 2f
                val cell = h / 26f
                val neck = h / 2f

                // The frame: two triangles meeting at the neck, drawn as an outline.
                val frame = androidx.compose.ui.graphics.Path().apply {
                    moveTo(left, 0f)
                    lineTo(left + w, 0f)
                    lineTo(left + w * 0.54f, neck)
                    lineTo(left + w, h)
                    lineTo(left, h)
                    lineTo(left + w * 0.46f, neck)
                    close()
                }
                drawPath(frame, colors.hairline, style = Stroke(1.5.dp.toPx()))

                // Sand as cells: what is left above, what has fallen below.
                val rowsTop = 11
                for (row in 0 until rowsTop) {
                    val fill = ((1f - drained) * rowsTop) - row
                    if (fill <= 0f) continue
                    val y = neck - (row + 1) * cell - cell * 0.4f
                    val halfWidth = (w * 0.46f) * (1f - (neck - y) / neck).coerceIn(0f, 1f)
                    drawSandRow(left + w / 2f, y, halfWidth, cell, colors.onBackground, fill.coerceAtMost(1f))
                }
                for (row in 0 until rowsTop) {
                    val fill = (drained * rowsTop) - row
                    if (fill <= 0f) continue
                    val y = h - (row + 1) * cell - cell * 0.4f
                    val halfWidth = (w * 0.46f) * ((h - y) / neck).coerceIn(0f, 1f)
                    drawSandRow(left + w / 2f, y, halfWidth, cell, colors.secondary, fill.coerceAtMost(1f))
                }
                // One grain in the neck, falling, as long as there is anything left to fall.
                if (drained < 1f) {
                    val fall = (seconds * 2.2f) % 1f
                    val y = neck - cell + fall * cell * 4f
                    drawRect(colors.onBackground, Offset(left + w / 2f - cell * 0.3f, y), Size(cell * 0.6f, cell * 0.6f))
                }
            }
            Spacer(Modifier.height(6.dp))
            BlockCaptionRow(caption)
        }
    }

    /** A row of grains, centred, with the last one faded in as the row fills. */
    private fun DrawScope.drawSandRow(centerX: Float, y: Float, halfWidth: Float, cell: Float, color: Color, fill: Float) {
        val count = (halfWidth * 2 / cell).toInt().coerceAtLeast(1)
        val shown = (count * fill).roundToInt().coerceAtLeast(1)
        val start = centerX - (shown * cell) / 2f
        repeat(shown) { index ->
            drawRect(color, Offset(start + index * cell, y), Size(cell * 0.72f, cell * 0.72f))
        }
    }
}

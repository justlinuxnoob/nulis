// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.battery

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme
import kotlin.math.roundToInt
import com.nulis.launcher.ui.theme.LineStyle
import com.nulis.launcher.blocks.BlockBox
import com.nulis.launcher.blocks.blockArea
import com.nulis.launcher.blocks.chartHeight
import com.nulis.launcher.blocks.dialSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.Canvas

object BatteryBlockDefinition : BlockDefinition {
    override val type = "battery"
    override val label = "Battery"

    override val minSpan = GridSpan(1, 1)
    override val defaultSpan = GridSpan(2, 1)
    override val styles: List<BlockStyle> = listOf(PercentStyle, SegmentsStyle, PillStyle, RingStyle, CellStyle, GaugeStyle)
    override val defaultSize: BlockSize get() = BlockSize.SMALL

    // The ring and the cell are drawn shapes, and a line of text's worth of preview clips them.
    override val previewHeight get() = 104.dp

    override fun accessibilityLabel(block: Block, context: BlockContext): String =
        "Battery ${context.battery.percent} per cent" + if (context.battery.charging) ", charging" else ""

    /** The percentage in the display face, with a mono "charging" label when plugged in. */
    private object PercentStyle : BlockStyle {
        override val id = "percent"
        override val label = "Percent"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val battery = context.battery
            val wide = block.size == BlockSize.WIDE
            val type = NulisTheme.type
            BlockRow(modifier = modifier, verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "${battery.percent}%",
                    style = if (wide) type.displayL else type.displayM,
                    color = NulisTheme.colors.onBackground,
                )
                Spacer(Modifier.width(12.dp))
                Caption(if (battery.charging) "Charging" else "Battery", modifier = Modifier.padding(bottom = if (wide) 8.dp else 4.dp))
            }
        }
    }

    /** Ten segments, filled from the left; a hardware-style level meter. */
    private object SegmentsStyle : BlockStyle {
        override val id = "segments"
        override val label = "Segments"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val battery = context.battery
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val filled = (battery.percent + 5) / 10
            BlockColumn(modifier) {
                Row(
                    Modifier.fillMaxWidth(if (wide) 1f else 0.55f),
                    horizontalArrangement = Arrangement.spacedBy(if (wide) 6.dp else 4.dp),
                ) {
                    repeat(10) { index ->
                        Box(
                            Modifier
                                .weight(1f)
                                .height(if (wide) 14.dp else 8.dp)
                                .background(if (index < filled) colors.onBackground else colors.surfaceRaised, NulisShapes.pill)
                                .border(1.dp, if (index < filled) colors.onBackground else colors.hairline, NulisShapes.pill),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                BlockCaptionRow("${battery.percent}%", trailing = if (battery.charging) "Charging" else null)
            }
        }
    }

    /** A tiny pill: level as a mono label with a dot that lights up while charging. */
    private object PillStyle : BlockStyle {
        override val id = "pill"
        override val label = "Pill"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val battery = context.battery
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            BlockRow(modifier) {
                Row(
                    modifier = Modifier
                        .height(if (wide) 40.dp else 32.dp)
                        .background(colors.surface, NulisShapes.pill)
                        .border(1.dp, colors.hairline, NulisShapes.pill)
                        .padding(horizontal = if (wide) 16.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).background(if (battery.charging) colors.accent else colors.tertiary, CircleShape))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${battery.percent}%",
                        style = if (wide) NulisTheme.type.labelL.copy(fontSize = 15.sp) else NulisTheme.type.label,
                        color = colors.onBackground,
                    )
                }
            }
        }
    }

    /** A ring that empties as the battery does, with the number in the middle of it. */
    private object RingStyle : BlockStyle {
        override val id = "ring"
        override val label = "Ring"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val battery = context.battery
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val diameter = dialSize(min = if (wide) 96.dp else 64.dp, reserved = 4.dp)
            BlockBox(modifier) {
                Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(diameter)) {
                        val stroke = size.minDimension * 0.11f
                        val radius = size.minDimension / 2f - stroke / 2f
                        val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
                        val arcSize = Size(radius * 2f, radius * 2f)
                        drawArc(
                            color = colors.surfaceRaised,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = colors.onBackground,
                            startAngle = -90f,
                            sweepAngle = 360f * (battery.percent / 100f),
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    Text(
                        text = "${battery.percent}",
                        style = if (wide) NulisTheme.type.displayM else NulisTheme.type.displayS,
                        color = colors.onBackground,
                    )
                }
            }
        }
    }

    /**
     * The battery drawn as a battery: an outline with a nub on the end and a level inside it.
     * The one shape everyone already knows how to read, so it needs no caption at all.
     */
    private object CellStyle : BlockStyle {
        override val id = "cell"
        override val label = "Cell"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val battery = context.battery
            val colors = NulisTheme.colors
            val dotted = NulisTheme.look.lineStyle == LineStyle.DOTTED
            val wide = block.size == BlockSize.WIDE
            // A cell keeps its proportions, but grows to whatever the rectangle allows.
            val cellHeight = chartHeight(min = if (wide) 61.dp else 39.dp, reserved = 28.dp)
            val cellWidth = minOf(cellHeight / 0.46f, blockArea().width)
            BlockColumn(modifier.fillMaxWidth()) {
                Canvas(Modifier.size(cellWidth, cellHeight)) {
                    val nub = size.width * 0.05f
                    val body = Size(size.width - nub - 2.dp.toPx(), size.height)
                    val radius = CornerRadius(size.height * 0.22f, size.height * 0.22f)
                    drawRoundRect(colors.secondary, size = body, cornerRadius = radius, style = Stroke(2.dp.toPx()))
                    // The nub, so it reads as a cell rather than as a progress bar.
                    drawRoundRect(
                        colors.secondary,
                        topLeft = Offset(size.width - nub, size.height * 0.3f),
                        size = Size(nub, size.height * 0.4f),
                        cornerRadius = CornerRadius(nub / 2f, nub / 2f),
                    )
                    val inset = 5.dp.toPx()
                    val fillWidth = (body.width - inset * 2) * (battery.percent / 100f)
                    if (dotted) {
                        // Dot look: the level is ten bars inside the cell rather than one block,
                        // so it belongs to the same grid as the digits and reads as a level.
                        val count = 10
                        val lit = ((count * battery.percent) / 100f).roundToInt()
                        val slot = (body.width - inset * 2) / count
                        repeat(lit) { index ->
                            drawRect(
                                color = colors.onBackground,
                                topLeft = Offset(inset + index * slot, inset),
                                size = Size(slot * 0.78f, body.height - inset * 2),
                            )
                        }
                    } else if (fillWidth > 0f) {
                        drawRoundRect(
                            colors.onBackground,
                            topLeft = Offset(inset, inset),
                            size = Size(fillWidth, body.height - inset * 2),
                            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                BlockCaptionRow("${battery.percent}%", trailing = if (battery.charging) "Charging" else null)
            }
        }
    }

    /**
     * A gauge with a scale: a full-width track marked at every quarter, and a needle at the
     * level. For a page where the battery is one line among several readouts and should look
     * like an instrument rather than a widget.
     */
    private object GaugeStyle : BlockStyle {
        override val id = "gauge"
        override val label = "Gauge"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val battery = context.battery
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            BlockColumn(modifier.fillMaxWidth()) {
                BlockCaptionRow(
                    if (battery.charging) "Charging" else "Battery",
                    trailing = "${battery.percent}%",
                )
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.fillMaxWidth().height(chartHeight(min = if (wide) 22.dp else 16.dp, reserved = 44.dp))) {
                    val line = size.height * 0.34f
                    val y = size.height * 0.5f
                    drawLine(colors.hairline, Offset(0f, y), Offset(size.width, y), 2.dp.toPx(), cap = StrokeCap.Round)
                    drawLine(
                        colors.onBackground,
                        Offset(0f, y),
                        Offset(size.width * (battery.percent / 100f), y),
                        2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                    // Quarter marks, the thing that turns a bar into a scale.
                    listOf(0f, 0.25f, 0.5f, 0.75f, 1f).forEach { at ->
                        val x = (size.width * at).coerceIn(1.dp.toPx(), size.width - 1.dp.toPx())
                        drawLine(colors.hairline, Offset(x, y - line), Offset(x, y + line), 1.5.dp.toPx())
                    }
                    val needle = (size.width * (battery.percent / 100f)).coerceIn(line, size.width - line)
                    drawLine(
                        colors.onBackground,
                        Offset(needle, y - size.height * 0.5f),
                        Offset(needle, y + size.height * 0.5f),
                        3.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
    }
}

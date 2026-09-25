// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.clock

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import com.nulis.launcher.blocks.blockArea
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nulis.launcher.apps.SystemApp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockBox
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.FitWidth
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisBottomSheet
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.SegmentedPills
import com.nulis.launcher.ui.theme.GeistMonoFontFamily
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.LocalTime
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.roundToInt
import com.nulis.launcher.ui.theme.LineStyle
import com.nulis.launcher.blocks.BlockCaptionRow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Arrangement

/** Current time, updated on every minute boundary and whenever [refreshKey] changes. */
@Composable
fun rememberCurrentTime(refreshKey: Any?): LocalDateTime {
    val time by produceState(initialValue = NulisClock.now(), key1 = refreshKey) {
        while (true) {
            value = NulisClock.now()
            val untilNextMinute = 60_000L - (System.currentTimeMillis() % 60_000L)
            delay(untilNextMinute + 20L)
        }
    }
    return time
}

/**
 * Where the page's "now" comes from. The phone's clock, always - except in the screenshot tests
 * that make the store listing, where every phone in every picture says 9:41.
 */
object NulisClock {
    @Volatile
    var now: () -> LocalDateTime = { LocalDateTime.now() }
}

/**
 * The hero block. Fourteen skins of the same instant, every one of them built from the Look's
 * display face and the block's alignment, and every one of them driven by the same four options:
 * 12 or 24 hours, seconds, a size scale and a weight.
 */
object ClockBlockDefinition : BlockDefinition {
    override val type = "clock"
    override val label = "Clock"

    override val minSpan = GridSpan(2, 1)
    override val defaultSpan = GridSpan(6, 3)

    override val styles: List<BlockStyle> = listOf(
        DisplayStyle,
        StackedStyle,
        MonoStyle,
        OutlineStyle,
        VerticalStyle,
        FlipStyle,
        AnalogStyle,
        AnalogDotStyle,
        RingStyle,
        RomanStyle,
        BinaryStyle,
        WordsStyle,
        DualStyle,
        CornerStyle,
        SegmentStyle,
        BarsStyle,
        RingsStyle,
        DayArcStyle,
    )

    override fun defaultSettings(): Map<String, String> = ClockSettings().toMap()

    override fun accessibilityLabel(block: Block, context: BlockContext): String {
        val settings = clockSettings(block)
        val time = context.time.toLocalTime()
        return "Clock, ${hourText(time.hour, settings.hour24)}:${"%02d".format(time.minute)}"
    }

    // Dials and stacked digits are much taller than a line of text.
    override val previewHeight get() = 132.dp

    /** A tap goes to the phone's own clock; the page folds this into its double-tap gesture. */
    override fun tapAction(block: Block, context: BlockContext): () -> Unit =
        { context.openSystemApp(SystemApp.CLOCK) }

    override val hasOptions: Boolean get() = true

    @Composable
    override fun Options(block: Block, context: BlockContext, onUpdate: (Block) -> Unit) {
        val s = clockSettings(block)
        var zoneSheet by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxWidth()) {
            SectionLabel("Hours")
            SegmentedPills(
                options = listOf(true, false),
                selected = s.hour24,
                label = { if (it) "24 h" else "12 h" },
                onSelect = { onUpdate(block.withClock(s.copy(hour24 = it))) },
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel("Size")
            SegmentedPills(
                options = ClockScale.entries,
                selected = s.scale,
                label = { it.label },
                onSelect = { onUpdate(block.withClock(s.copy(scale = it))) },
            )
            Spacer(Modifier.height(12.dp))
            SectionLabel("Weight")
            SegmentedPills(
                options = ClockWeight.entries,
                selected = s.weight ?: ClockWeight.REGULAR,
                label = { it.label },
                onSelect = { onUpdate(block.withClock(s.copy(weight = it))) },
            )
            Spacer(Modifier.height(8.dp))
            ListRow(
                title = "Show seconds",
                subtitle = "Ticks once a second while the page is open",
                onClick = { onUpdate(block.withClock(s.copy(seconds = !s.seconds))) },
                trailing = { NulisToggle(checked = s.seconds, onCheckedChange = { onUpdate(block.withClock(s.copy(seconds = it))) }) },
                divider = block.style == DualStyle.id,
            )
            if (block.style == DualStyle.id) {
                ListRow(
                    title = "Second time zone",
                    subtitle = s.secondZoneLabel,
                    onClick = { zoneSheet = true },
                    divider = false,
                )
            }
        }
        if (zoneSheet) {
            NulisBottomSheet(onDismiss = { zoneSheet = false }) {
                Text("Second time zone", style = NulisTheme.type.displayM, color = NulisTheme.colors.onBackground)
                Spacer(Modifier.height(8.dp))
                SecondZones.forEachIndexed { index, (id, city) ->
                    ListRow(
                        title = city,
                        subtitle = id,
                        divider = index != SecondZones.lastIndex,
                        onClick = {
                            onUpdate(block.withClock(s.copy(secondZone = id)))
                            dismiss()
                        },
                        trailing = {
                            if (id == s.secondZone) Box(Modifier.size(6.dp).background(NulisTheme.colors.accent, NulisShapes.pill))
                        },
                    )
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    // ---------------------------------------------------------------- digit skins

    /** Digits in the current Look's display face: dot-matrix in Dot, thin Geist in Clean. */
    private object DisplayStyle : BlockStyle {
        override val id = "display"
        override val label = "Display"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            Column(modifier, horizontalAlignment = blockAlign().horizontal) {
                FitWidth(align = blockAlign().horizontal) {
                    Text(
                        text = clockText(now.toLocalTime(), s),
                        style = displayStyle(block, s, 96f),
                        color = NulisTheme.colors.onBackground,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                meridiem(now.hour, s.hour24)?.let { Caption(it, Modifier.padding(top = 4.dp)) }
            }
        }
    }

    /** Hours over minutes, as big as the block will allow. The poster version. */
    private object StackedStyle : BlockStyle {
        override val id = "stacked"
        override val label = "Stacked"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val style = displayStyle(block, s, 104f).copy(lineHeight = (104f * clockScale(block, s) * 0.92f).sp)
            BlockColumn(modifier) {
                FitWidth(align = blockAlign().horizontal) {
                    Text(hourText(now.hour, s.hour24), style = style, color = NulisTheme.colors.onBackground, maxLines = 1, softWrap = false)
                }
                FitWidth(align = blockAlign().horizontal) {
                    Text("%02d".format(now.minute), style = style, color = NulisTheme.colors.secondary, maxLines = 1, softWrap = false)
                }
                if (s.seconds) Text("%02d".format(now.second), style = style.copy(fontSize = style.fontSize * 0.45f, lineHeight = style.fontSize * 0.5f), color = NulisTheme.colors.tertiary)
            }
        }
    }

    /** Digits as a mono readout, like an instrument panel. */
    private object MonoStyle : BlockStyle {
        override val id = "mono"
        override val label = "Mono"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val size = (80f * clockScale(block, s)).sp
            FitWidth(modifier, align = blockAlign().horizontal) {
                Text(
                    text = clockText(now.toLocalTime(), s),
                    fontFamily = GeistMonoFontFamily,
                    fontWeight = s.weight?.weight ?: FontWeight.Light,
                    fontSize = size,
                    lineHeight = size,
                    letterSpacing = (-0.04).em,
                    color = NulisTheme.colors.onBackground,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

    /** Hollow digits: the same face drawn as an outline, so the background shows through. */
    private object OutlineStyle : BlockStyle {
        override val id = "outline"
        override val label = "Outline"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val density = LocalDensity.current
            val base = displayStyle(block, s, 96f)
            val stroke = with(density) { (1.6.dp * clockScale(block, s).coerceAtLeast(0.6f)).toPx() }
            FitWidth(modifier, align = blockAlign().horizontal) {
                Text(
                    text = clockText(now.toLocalTime(), s),
                    style = base.copy(drawStyle = Stroke(width = stroke)),
                    color = NulisTheme.colors.onBackground,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

    /** One digit per line, running down the page. */
    private object VerticalStyle : BlockStyle {
        override val id = "vertical"
        override val label = "Vertical"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val size = 48f * clockScale(block, s)
            val style = displayStyle(block, s, 48f).copy(lineHeight = (size * 1.05f).sp)
            val colors = NulisTheme.colors
            val hours = hourText(now.hour, s.hour24).padStart(2, '0')
            val minutes = "%02d".format(now.minute)
            BlockColumn(modifier) {
                hours.forEach { Text(it.toString(), style = style, color = colors.onBackground) }
                Box(Modifier.padding(vertical = (size * 0.06f).dp).size((size * 0.1f).dp).background(colors.tertiary, NulisShapes.pill))
                minutes.forEach { Text(it.toString(), style = style, color = colors.secondary) }
                if (s.seconds) "%02d".format(now.second).forEach {
                    Text(it.toString(), style = style.copy(fontSize = (size * 0.5f).sp, lineHeight = (size * 0.55f).sp), color = colors.tertiary)
                }
            }
        }
    }

    /** Split-flap cards. A digit that changes falls into place; nothing else moves. */
    private object FlipStyle : BlockStyle {
        override val id = "flip"
        override val label = "Flip"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val scale = clockScale(block, s)
            val text = clockText(now.toLocalTime(), s)
            val style = displayStyle(block, s, 56f)
            BlockRow(modifier) {
                text.forEachIndexed { index, char ->
                    if (char == ':') {
                        Text(":", style = style, color = NulisTheme.colors.tertiary, modifier = Modifier.padding(horizontal = (4f * scale).dp))
                    } else {
                        FlipCard(char, style, scale)
                        if (index < text.lastIndex && text[index + 1] != ':') Spacer(Modifier.width((4f * scale).dp))
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- dials

    /** Two hands, a rim and nothing else. */
    private object AnalogStyle : BlockStyle {
        override val id = "analog"
        override val label = "Analog"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val colors = NulisTheme.colors
            BlockBox(modifier) {
                Canvas(Modifier.size(dialSize(block, s)).aspectRatio(1f)) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(colors.hairline, r - 1.dp.toPx(), c, style = Stroke(1.5.dp.toPx()))
                    // Four marks only: twelve, three, six, nine. Anything more is decoration.
                    repeat(4) { i ->
                        val a = (i * 90f - 90f).toRadians()
                        val outer = c + Offset(cos(a), sin(a)) * (r - 4.dp.toPx())
                        val inner = c + Offset(cos(a), sin(a)) * (r - 14.dp.toPx())
                        drawLine(colors.secondary, inner, outer, 2.dp.toPx())
                    }
                    drawHands(c, r, now.toLocalTime(), s.seconds, colors.onBackground, colors.secondary, colors.tertiary)
                }
            }
        }
    }

    /** The same dial rendered in dots: sixty round marks and hands made of squares. */
    private object AnalogDotStyle : BlockStyle {
        override val id = "analogdot"
        override val label = "Dot dial"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val colors = NulisTheme.colors
            BlockBox(modifier) {
                Canvas(Modifier.size(dialSize(block, s)).aspectRatio(1f)) {
                    val r = size.minDimension / 2f
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val unit = r / 22f
                    repeat(60) { i ->
                        val a = (i * 6f - 90f).toRadians()
                        val hour = i % 5 == 0
                        drawCircle(
                            color = if (hour) colors.secondary else colors.hairline,
                            radius = if (hour) unit * 1.5f else unit * 0.8f,
                            center = c + Offset(cos(a), sin(a)) * (r - unit * 2f),
                        )
                    }
                    val time = now.toLocalTime()
                    dottedHand(c, r * 0.52f, (time.hour % 12 + time.minute / 60f) * 30f, unit * 1.7f, colors.onBackground)
                    dottedHand(c, r * 0.74f, time.minute * 6f + time.second / 10f, unit * 1.2f, colors.onBackground)
                    if (s.seconds) dottedHand(c, r * 0.8f, time.second * 6f, unit * 0.7f, colors.tertiary)
                }
            }
        }
    }

    /** A ring of sixty ticks filling up as the minute passes, with the time inside it. */
    private object RingStyle : BlockStyle {
        override val id = "ring"
        override val label = "Seconds ring"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            // This skin is about the second hand, so it ticks whatever the option says.
            val now = rememberTickingTime(context.time, seconds = true)
            val colors = NulisTheme.colors
            val dial = dialSize(block, s)
            BlockBox(modifier) {
                Box(Modifier.size(dial), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(dial)) {
                        val r = size.minDimension / 2f
                        val c = Offset(size.width / 2f, size.height / 2f)
                        val tick = r * 0.14f
                        repeat(60) { i ->
                            val a = (i * 6f - 90f).toRadians()
                            val dir = Offset(cos(a), sin(a))
                            drawLine(
                                color = if (i <= now.second) colors.onBackground else colors.hairline,
                                start = c + dir * (r - tick),
                                end = c + dir * r,
                                strokeWidth = if (i % 5 == 0) 2.5.dp.toPx() else 1.5.dp.toPx(),
                            )
                        }
                    }
                    Text(
                        text = "${hourText(now.hour, s.hour24)}:${"%02d".format(now.minute)}",
                        style = displayStyle(block, s, 34f),
                        color = colors.onBackground,
                    )
                }
            }
        }
    }

    // ---------------------------------------------------------------- drawn skins

    /**
     * A true seven-segment display: each digit is seven bars, and the ones that are not lit are
     * still there, faintly, the way they are on a real panel. It is the one digit skin that does
     * not use a typeface at all, so it reads the same in both looks and in any font the user has
     * chosen - the shape is the point.
     */
    private object SegmentStyle : BlockStyle {
        override val id = "segment"
        override val label = "Segment"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val colors = NulisTheme.colors
            val scale = clockScale(block, s)
            val digits = clockText(now.toLocalTime(), s)
            val cellWidth = (26f * scale).dp
            val cellHeight = (46f * scale).dp
            val gap = (6f * scale).dp
            FitWidth(modifier.fillMaxWidth(), align = blockAlign().horizontal) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap), verticalAlignment = Alignment.CenterVertically) {
                    digits.forEach { char ->
                        if (char == ':') {
                            Canvas(Modifier.size(width = (8f * scale).dp, height = cellHeight)) {
                                val dot = size.width * 0.5f
                                drawCircle(colors.onBackground, dot / 2f, Offset(size.width / 2f, size.height * 0.32f))
                                drawCircle(colors.onBackground, dot / 2f, Offset(size.width / 2f, size.height * 0.68f))
                            }
                        } else {
                            Canvas(Modifier.size(cellWidth, cellHeight)) {
                                drawSevenSegment(char, colors.onBackground, colors.hairline)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The hour and the minute as two tracks that fill across the day and across the hour. A clock
     * you read the way you read a battery: not "what time is it" so much as "how far through".
     */
    private object BarsStyle : BlockStyle {
        override val id = "bars"
        override val label = "Bars"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val colors = NulisTheme.colors
            val scale = clockScale(block, s)
            val dotted = NulisTheme.look.lineStyle == LineStyle.DOTTED
            val time = now.toLocalTime()
            BlockColumn(modifier.fillMaxWidth()) {
                Track("Hour", hourText(time.hour, s.hour24), time.hour / 24f, scale, dotted, colors)
                Spacer(Modifier.height((10f * scale).dp))
                Track("Min", "%02d".format(time.minute), time.minute / 60f, scale, dotted, colors)
                if (s.seconds) {
                    Spacer(Modifier.height((10f * scale).dp))
                    Track("Sec", "%02d".format(time.second), time.second / 60f, scale, dotted, colors)
                }
            }
        }

        @Composable
        private fun Track(
            label: String,
            value: String,
            fraction: Float,
            scale: Float,
            dotted: Boolean,
            colors: com.nulis.launcher.ui.theme.NulisColors,
        ) {
            BlockCaptionRow(label, trailing = value)
            Spacer(Modifier.height((4f * scale).dp))
            Canvas(Modifier.fillMaxWidth().height((14f * scale).dp)) {
                if (dotted) {
                    // In the Dot look the track is cells, so it belongs to the same grid as the digits.
                    val cell = size.height
                    val count = (size.width / (cell * 1.4f)).toInt().coerceAtLeast(1)
                    val lit = (count * fraction).roundToInt()
                    repeat(count) { i ->
                        val x = i * (size.width / count)
                        drawRect(
                            color = if (i < lit) colors.onBackground else colors.hairline,
                            topLeft = Offset(x, 0f),
                            size = Size(cell, cell),
                        )
                    }
                } else {
                    val radius = CornerRadius(size.height / 2f, size.height / 2f)
                    drawRoundRect(colors.surfaceRaised, size = size, cornerRadius = radius)
                    drawRoundRect(
                        colors.onBackground,
                        size = Size(size.width * fraction.coerceAtLeast(0.02f), size.height),
                        cornerRadius = radius,
                    )
                }
            }
        }
    }

    /**
     * Three arcs, one inside the next: how far through the day, the hour and the minute. The
     * time sits in the middle for the moments when you want the number rather than the feeling.
     */
    private object RingsStyle : BlockStyle {
        override val id = "rings"
        override val label = "Rings"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val colors = NulisTheme.colors
            val dial = dialSize(block, s)
            val time = now.toLocalTime()
            BlockBox(modifier) {
                Box(Modifier.size(dial), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(dial)) {
                        val stroke = size.minDimension * 0.052f
                        val outer = size.minDimension / 2f - stroke / 2f
                        val arcs = listOf(
                            (time.hour + time.minute / 60f) / 24f to outer,
                            (time.minute + time.second / 60f) / 60f to outer - stroke * 1.9f,
                            (if (s.seconds) time.second / 60f else -1f) to outer - stroke * 3.8f,
                        )
                        arcs.forEach { (fraction, radius) ->
                            if (fraction < 0f || radius <= stroke) return@forEach
                            val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
                            val arcSize = Size(radius * 2f, radius * 2f)
                            drawArc(
                                color = colors.hairline,
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
                                sweepAngle = 360f * fraction,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                    Text(
                        text = "${hourText(time.hour, s.hour24)}:${"%02d".format(time.minute)}",
                        style = displayStyle(block, s, 30f),
                        color = colors.onBackground,
                    )
                }
            }
        }
    }

    /**
     * The day as a single arc from midnight to midnight, with a marker where you are on it. A
     * clock that answers "how much of today is left" without doing the subtraction for you - the
     * gap between the marker and the far end is the answer.
     */
    private object DayArcStyle : BlockStyle {
        override val id = "dayarc"
        override val label = "Day arc"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val colors = NulisTheme.colors
            val scale = clockScale(block, s)
            val time = now.toLocalTime()
            val fraction = (time.hour * 3600 + time.minute * 60 + time.second) / 86_400f
            val width = (240f * scale).dp
            BlockColumn(modifier.fillMaxWidth()) {
                Box(Modifier.width(width).height(width / 2f + (10f * scale).dp)) {
                    Canvas(Modifier.fillMaxSize()) {
                        val stroke = size.width * 0.035f
                        val radius = size.width / 2f - stroke
                        val centre = Offset(size.width / 2f, size.height - stroke)
                        val topLeft = Offset(centre.x - radius, centre.y - radius)
                        val arcSize = Size(radius * 2f, radius * 2f)
                        drawArc(
                            color = colors.hairline,
                            startAngle = 180f,
                            sweepAngle = 180f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                        drawArc(
                            color = colors.onBackground,
                            startAngle = 180f,
                            sweepAngle = 180f * fraction,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                        val angle = (180f + 180f * fraction).toRadians()
                        val marker = centre + Offset(cos(angle), sin(angle)) * radius
                        drawCircle(colors.background, stroke * 1.35f, marker)
                        drawCircle(colors.onBackground, stroke * 0.85f, marker)
                    }
                    Box(Modifier.align(Alignment.BottomCenter), contentAlignment = Alignment.Center) {
                        Text(
                            text = "${hourText(time.hour, s.hour24)}:${"%02d".format(time.minute)}",
                            style = displayStyle(block, s, 26f),
                            color = colors.onBackground,
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Caption(dayLeft(time))
            }
        }
    }

    // ---------------------------------------------------------------- words and numbers

    /** Hours and minutes as Roman numerals. On the hour it says only the hour. */
    private object RomanStyle : BlockStyle {
        override val id = "roman"
        override val label = "Roman"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val hour = now.hour % 12
            val text = if (now.minute == 0) {
                roman(if (hour == 0) 12 else hour)
            } else {
                "${roman(if (hour == 0) 12 else hour)} · ${roman(now.minute)}"
            }
            FitWidth(modifier, align = blockAlign().horizontal) {
                Text(
                    text = text,
                    style = displayStyle(block, s, 56f).copy(letterSpacing = 0.04.em),
                    color = NulisTheme.colors.onBackground,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

    /** Binary-coded decimal: one column per digit, one dot per bit, read top to bottom 8-4-2-1. */
    private object BinaryStyle : BlockStyle {
        override val id = "binary"
        override val label = "Binary"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val colors = NulisTheme.colors
            val hours = hourText(now.hour, s.hour24).padStart(2, '0')
            val digits = buildList {
                addAll(hours.map { it - '0' })
                addAll("%02d".format(now.minute).map { it - '0' })
                if (s.seconds) addAll("%02d".format(now.second).map { it - '0' })
            }
            val cell = (14f * clockScale(block, s)).dp
            BlockColumn(modifier) {
                Canvas(
                    Modifier
                        .width(cell * (digits.size * 2 - 1))
                        .height(cell * 7),
                ) {
                    val step = size.width / (digits.size * 2f - 1f)
                    val r = step * 0.3f
                    digits.forEachIndexed { column, value ->
                        val x = column * step * 2f + step / 2f
                        listOf(8, 4, 2, 1).forEachIndexed { row, bit ->
                            val y = size.height * (row + 0.5f) / 4f
                            if (value and bit != 0) {
                                drawCircle(colors.onBackground, r, Offset(x, y))
                            } else {
                                drawCircle(colors.hairline, r, Offset(x, y), style = Stroke(1.5.dp.toPx()))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Caption(clockText(now.toLocalTime(), s))
            }
        }
    }

    /** The time written out, like "six past seven". Wraps into short lines on purpose. */
    private object WordsStyle : BlockStyle {
        override val id = "words"
        override val label = "Words"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val words = timeInWords(context.time.toLocalTime())
            // A narrower column is what turns a sentence into three deliberate lines.
            val fraction = if (block.size == BlockSize.WIDE) 0.8f else 1f
            val column = blockArea().width * fraction
            val base = displayStyle(block, s, 44f)
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            // A column narrow enough to wrap is also narrow enough to break a word in half -
            // "twenty-thre / e to eight" - because a line break lands anywhere once a single
            // word is wider than the line. So the type steps down until the longest word fits,
            // which is what anybody setting this by hand would do, and the wrapping goes back to
            // happening at the spaces.
            val style = remember(words, base, column, density) {
                val longest = words.split(' ').maxByOrNull { it.length } ?: words
                val wanted = with(density) { measurer.measure(longest, base).size.width.toDp() }
                val factor = if (wanted > column && wanted > 0.dp) column / wanted else 1f
                val size = base.fontSize * factor
                base.copy(fontSize = size, lineHeight = size * 1.12f)
            }
            BlockBox(modifier) {
                Text(
                    text = words,
                    style = style,
                    color = NulisTheme.colors.onBackground,
                    textAlign = blockAlign().textAlign,
                    modifier = Modifier.fillMaxWidth(fraction),
                )
            }
        }
    }

    /** Here and somewhere else, one under the other. */
    private object DualStyle : BlockStyle {
        override val id = "dual"
        override val label = "Two zones"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val other = rememberZoned(now, s.secondZone)
            val colors = NulisTheme.colors
            BlockColumn(modifier) {
                FitWidth(align = blockAlign().horizontal) {
                    Text(
                        text = clockText(now.toLocalTime(), s),
                        style = displayStyle(block, s, 68f),
                        color = colors.onBackground,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Caption("Here")
                Spacer(Modifier.height(10.dp))
                if (other != null) {
                    FitWidth(align = blockAlign().horizontal) {
                        Text(
                            text = clockText(other.toLocalTime(), s),
                            style = displayStyle(block, s, 40f),
                            color = colors.secondary,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                    Caption(s.secondZoneLabel)
                }
            }
        }
    }

    /** A readout the size of a caption. For a page that wants almost nothing on it. */
    private object CornerStyle : BlockStyle {
        override val id = "corner"
        override val label = "Corner"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val s = clockSettings(block)
            val now = rememberTickingTime(context.time, s.seconds)
            val colors = NulisTheme.colors
            BlockRow(modifier) {
                Box(Modifier.size((5f * clockScale(block, s)).dp).background(colors.tertiary, NulisShapes.pill))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = clockText(now.toLocalTime(), s),
                    style = NulisTheme.type.labelL.copy(fontSize = (13f * clockScale(block, s)).sp),
                    color = colors.secondary,
                )
            }
        }
    }
}

// -------------------------------------------------------------------- shared pieces

/** "07:45", "7:45 seconds included", following the block's own options. */
private fun clockText(time: LocalTime, s: ClockSettings): String = buildString {
    append(hourText(time.hour, s.hour24))
    append(':')
    append("%02d".format(time.minute))
    if (s.seconds) {
        append(':')
        append("%02d".format(time.second))
    }
}

/** The display face at [baseSp], scaled by the block size and the user's own scale and weight. */
@Composable
private fun displayStyle(block: Block, s: ClockSettings, baseSp: Float): TextStyle {
    val base = NulisTheme.type.displayXl
    val size = (baseSp * clockScale(block, s)).sp
    return base.copy(
        fontSize = size,
        lineHeight = size * 1.02f,
        fontWeight = s.weight?.weight ?: base.fontWeight,
    )
}

/** Dial diameter for the round skins. */
@Composable
private fun dialSize(block: Block, s: ClockSettings) = (180f * clockScale(block, s)).dp

@Composable
private fun FlipCard(char: Char, style: TextStyle, scale: Float) {
    val colors = NulisTheme.colors
    var shown by remember { mutableStateOf(char) }
    val fall = remember { Animatable(0f) }
    LaunchedEffect(char) {
        if (char == shown) return@LaunchedEffect
        fall.snapTo(-90f)
        shown = char
        fall.animateTo(0f, tween(280))
    }
    Box(
        modifier = Modifier
            .background(colors.surface, NulisShapes.tile)
            .border(1.dp, colors.hairline, NulisShapes.tile)
            .padding(horizontal = (10f * scale).dp, vertical = (6f * scale).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = shown.toString(),
            style = style,
            color = colors.onBackground,
            modifier = Modifier.graphicsLayer {
                rotationX = fall.value
                cameraDistance = 16f * density
            },
        )
        // The seam every split-flap has, across the middle of the card.
        Box(Modifier.matchParentSize().padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(colors.background.copy(alpha = 0.7f)))
        }
    }
}

private fun Float.toRadians(): Float = (this * Math.PI / 180f).toFloat()

private operator fun Offset.times(scalar: Float) = Offset(x * scalar, y * scalar)

private fun DrawScope.drawHands(
    c: Offset,
    r: Float,
    time: LocalTime,
    seconds: Boolean,
    hourColor: Color,
    minuteColor: Color,
    secondColor: Color,
) {
    fun stroke(angleDeg: Float, length: Float, width: Float, color: Color) {
        val a = (angleDeg - 90f).toRadians()
        drawLine(color, c, c + Offset(cos(a), sin(a)) * length, width, cap = StrokeCap.Round)
    }
    stroke((time.hour % 12 + time.minute / 60f) * 30f, r * 0.5f, 3.5.dp.toPx(), hourColor)
    stroke(time.minute * 6f + time.second / 10f, r * 0.74f, 2.dp.toPx(), minuteColor)
    if (seconds) stroke(time.second * 6f, r * 0.82f, 1.dp.toPx(), secondColor)
    drawCircle(hourColor, 3.dp.toPx(), c)
}

/** A hand drawn as a line of square cells, so the dial belongs to the dot-matrix look. */
private fun DrawScope.dottedHand(c: Offset, length: Float, angleDeg: Float, cell: Float, color: Color) {
    val a = (angleDeg - 90f).toRadians()
    val dir = Offset(cos(a), sin(a))
    val steps = (length / (cell * 1.9f)).toInt().coerceAtLeast(1)
    for (i in 1..steps) {
        val p = c + dir * (i * length / steps)
        drawRect(color, Offset(p.x - cell / 2f, p.y - cell / 2f), Size(cell, cell))
    }
}

private val ROMAN = listOf(
    50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
)

/** Roman numerals for 1..59, which is all a clock ever needs. */
internal fun roman(value: Int): String {
    var left = value
    return buildString {
        for ((n, s) in ROMAN) {
            while (left >= n) {
                append(s)
                left -= n
            }
        }
    }
}

/** How much of today is still to come, said plainly. */
internal fun dayLeft(time: LocalTime): String {
    val minutes = 24 * 60 - (time.hour * 60 + time.minute)
    val hours = minutes / 60
    return when {
        minutes <= 0 -> "the day is done"
        hours <= 0 -> "$minutes min left today"
        else -> "${hours}h ${minutes % 60}m left today"
    }
}

/** Which of the seven bars are lit for each digit, in a-b-c-d-e-f-g order. */
private val SEGMENTS: Map<Char, String> = mapOf(
    '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
    '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
)

/**
 * One digit of a seven-segment panel. The unlit bars are drawn too, in the hairline colour: a
 * real panel shows its whole face, and without them the digits float.
 */
private fun DrawScope.drawSevenSegment(char: Char, on: Color, off: Color) {
    val lit = SEGMENTS[char].orEmpty()
    val thickness = size.height * 0.13f
    val inset = thickness * 0.9f
    val left = inset
    val right = size.width - inset
    val top = inset
    val middle = size.height / 2f
    val bottom = size.height - inset
    fun bar(name: Char, start: Offset, end: Offset) {
        drawLine(
            color = if (name in lit) on else off,
            start = start,
            end = end,
            strokeWidth = thickness,
            cap = StrokeCap.Round,
        )
    }
    bar('a', Offset(left, top), Offset(right, top))
    bar('b', Offset(right, top), Offset(right, middle))
    bar('c', Offset(right, middle), Offset(right, bottom))
    bar('d', Offset(left, bottom), Offset(right, bottom))
    bar('e', Offset(left, middle), Offset(left, bottom))
    bar('f', Offset(left, top), Offset(left, middle))
    bar('g', Offset(left, middle), Offset(right, middle))
}

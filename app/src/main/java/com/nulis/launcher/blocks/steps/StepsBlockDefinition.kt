// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.steps

import com.nulis.launcher.blocks.GridSpan

import android.annotation.SuppressLint
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.blocks.ScreenRequest
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.NulisSlider
import com.nulis.launcher.ui.components.PermissionScreen
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisShapes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.foundation.shape.RoundedCornerShape
import com.nulis.launcher.blocks.blockArea
import com.nulis.launcher.blocks.CompactBlockWidth
import com.nulis.launcher.ui.theme.NulisTheme
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sin
import com.nulis.launcher.ui.theme.LineStyle
import com.nulis.launcher.blocks.BlockBox
import com.nulis.launcher.blocks.chartHeight
import com.nulis.launcher.blocks.dialSize
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.size

/** Goal changes and permission re-checks, implemented by the view model. */
interface StepsActions {
    fun setGoal(goal: Int)
    fun permissionChanged()

    object None : StepsActions {
        override fun setGoal(goal: Int) = Unit
        override fun permissionChanged() = Unit
    }
}

object StepsBlockDefinition : BlockDefinition {
    override val type = "steps"
    override val label = "Steps"

    override val minSpan = GridSpan(1, 1)
    override val defaultSpan = GridSpan(3, 2)
    override val styles: List<BlockStyle> = listOf(BigNumberStyle, ProgressStyle, WeekStyle, WalkerStyle, RingStyle, DotsStyle, AverageStyle)

    @Composable
    override fun Screen(request: ScreenRequest, context: BlockContext, onClose: () -> Unit) {
        if (!context.steps.granted) ActivityRecognitionExplanation(context, onClose) else StepsScreen(context, onClose)
    }

    private fun format(steps: Int): String = "%,d".format(Locale.getDefault(), steps)

    override fun accessibilityLabel(block: Block, context: BlockContext): String =
        if (!context.steps.granted) {
            "Steps, waiting for permission"
        } else {
            "${format(context.steps.today)} steps today, goal ${format(context.steps.goal)}"
        }

    /** Today's count in the display face with the goal beneath. */
    private object BigNumberStyle : BlockStyle {
        override val id = "number"
        override val label = "Number"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val state = context.steps
            val wide = block.size == BlockSize.WIDE
            BlockColumn(modifier.open(context)) {
                BlockRow(verticalAlignment = Alignment.Bottom) {
                    Text(if (state.granted) format(state.today) else "--", style = if (wide) NulisTheme.type.displayL else NulisTheme.type.displayM, color = NulisTheme.colors.onBackground)
                    Spacer(Modifier.width(12.dp))
                    Caption("Steps", modifier = Modifier.padding(bottom = if (wide) 8.dp else 4.dp))
                }
                Spacer(Modifier.height(4.dp))
                Setup(context) { Caption("Goal ${format(state.goal)}") }
            }
        }
    }

    /** A progress bar towards the goal with the numbers as captions. */
    private object ProgressStyle : BlockStyle {
        override val id = "progress"
        override val label = "Progress"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val state = context.steps
            val wide = block.size == BlockSize.WIDE
            BlockColumn(modifier.open(context)) {
                BlockCaptionRow(
                    if (state.granted) "${format(state.today)} steps" else "Steps",
                    trailing = "${(state.progress * 100).roundToInt()}% of ${format(state.goal)}",
                )
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier.fillMaxWidth().height(if (wide) 10.dp else 6.dp).background(colors.surfaceRaised, NulisShapes.pill),
                    contentAlignment = blockAlign().box,
                ) {
                    Box(Modifier.fillMaxWidth(state.progress).height(if (wide) 10.dp else 6.dp).background(colors.onBackground, NulisShapes.pill))
                }
                Setup(context) { }
            }
        }
    }

    /** Seven columns, today on the right, the goal as a hairline across. */
    private object WeekStyle : BlockStyle {
        override val id = "week"
        override val label = "7 days"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val state = context.steps
            val wide = block.size == BlockSize.WIDE
            BlockColumn(modifier.open(context)) {
                BlockCaptionRow("Steps", trailing = if (state.granted) "${format(state.today)} today" else null)
                Spacer(Modifier.height(8.dp))
                // The chart is the block: it takes the whole rectangle it was given, across
                // and down, instead of sitting at one size in the middle of it.
                WeekChart(
                    state = state,
                    modifier = Modifier.fillMaxWidth().height(chartHeight(min = if (wide) 96.dp else 64.dp, reserved = 44.dp)),
                    // A number over a bar needs a bar wide enough to print one on.
                    values = blockArea().width >= CompactBlockWidth,
                )
                Setup(context) { }
            }
        }
    }

    /** A little figure walking along a track towards the goal; its legs move whenever the count goes up. */
    private object WalkerStyle : BlockStyle {
        override val id = "walker"
        override val label = "Walker"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val state = context.steps
            val wide = block.size == BlockSize.WIDE
            // Stride phase runs for a moment after each increase, then the figure stands still.
            var phase by remember { mutableFloatStateOf(0f) }
            LaunchedEffect(state.today) {
                if (state.today == 0) return@LaunchedEffect
                val start = withFrameNanos { it }
                while (true) {
                    val elapsed = withFrameNanos { it } - start
                    if (elapsed > 1_400_000_000L) { phase = 0f; break }
                    phase = (elapsed / 1e9f) * 2.6f
                }
            }
            val x by remember { Animatable(state.progress) }.also { LaunchedEffect(state.progress) { it.animateTo(state.progress) } }.asState()
            BlockColumn(modifier.open(context)) {
                BlockCaptionRow(if (state.granted) "${format(state.today)} steps" else "Steps", trailing = format(state.goal))
                Canvas(Modifier.fillMaxWidth().height(chartHeight(min = if (wide) 72.dp else 48.dp, reserved = 32.dp))) {
                    val h = size.height
                    val figure = h * 0.78f
                    val trackY = h - 1.dp.toPx()
                    drawLine(colors.hairline, Offset(0f, trackY), Offset(size.width, trackY), 2.dp.toPx(), StrokeCap.Round)
                    drawLine(colors.onBackground, Offset(0f, trackY), Offset(size.width * x, trackY), 2.dp.toPx(), StrokeCap.Round)
                    val cx = (figure * 0.3f) + (size.width - figure * 0.6f) * x
                    drawWalker(cx, h - 3.dp.toPx(), figure, phase, colors.onBackground, 2.dp.toPx())
                }
                Setup(context) { }
            }
        }
    }

    private fun DrawScope.drawWalker(cx: Float, ground: Float, height: Float, phase: Float, color: androidx.compose.ui.graphics.Color, stroke: Float) {
        val head = height * 0.16f
        val legLen = height * 0.36f
        val bodyTop = ground - height + head * 2
        val hip = ground - legLen
        val swing = sin(phase) * 0.5f
        drawCircle(color, head, Offset(cx, ground - height + head), style = Stroke(stroke))
        drawLine(color, Offset(cx, bodyTop), Offset(cx, hip), stroke, StrokeCap.Round)
        // Legs swing opposite to each other, arms opposite to the legs.
        drawLine(color, Offset(cx, hip), Offset(cx + legLen * sin(swing), ground), stroke, StrokeCap.Round)
        drawLine(color, Offset(cx, hip), Offset(cx - legLen * sin(swing), ground), stroke, StrokeCap.Round)
        val shoulder = bodyTop + (hip - bodyTop) * 0.2f
        val arm = legLen * 0.8f
        drawLine(color, Offset(cx, shoulder), Offset(cx - arm * sin(swing) * 0.8f, shoulder + arm * 0.9f), stroke, StrokeCap.Round)
        drawLine(color, Offset(cx, shoulder), Offset(cx + arm * sin(swing) * 0.8f, shoulder + arm * 0.9f), stroke, StrokeCap.Round)
    }


    /** The goal as a ring, the count inside it. The whole readout in one round shape. */
    private object RingStyle : BlockStyle {
        override val id = "ring"
        override val label = "Ring"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val state = context.steps
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val diameter = dialSize(min = if (wide) 132.dp else 84.dp, reserved = 4.dp)
            BlockBox(modifier.open(context)) {
                Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(diameter)) {
                        val stroke = size.minDimension * 0.085f
                        val radius = size.minDimension / 2f - stroke / 2f
                        val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
                        val arcSize = Size(radius * 2f, radius * 2f)
                        drawArc(colors.surfaceRaised, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                        drawArc(
                            color = colors.onBackground,
                            startAngle = -90f,
                            sweepAngle = 360f * state.progress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Setup(context) {
                            Text(
                                text = format(state.today),
                                style = if (wide) NulisTheme.type.displayM else NulisTheme.type.displayS,
                                color = colors.onBackground,
                            )
                            Caption("of ${format(state.goal)}")
                        }
                    }
                }
            }
        }
    }

    /**
     * One dot per five hundred steps, filling towards the goal. A count you read by area rather
     * than by digit - at a glance you know whether the day has been a walking one without
     * reading a number at all.
     */
    private object DotsStyle : BlockStyle {
        override val id = "dots"
        override val label = "Dots"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val state = context.steps
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val square = NulisTheme.look.lineStyle == LineStyle.DOTTED
            val total = (state.goal / STEPS_PER_DOT).coerceIn(8, 40)
            val lit = (state.today / STEPS_PER_DOT).coerceIn(0, total)
            BlockColumn(modifier.fillMaxWidth().open(context)) {
                BlockCaptionRow(
                    if (state.granted) "${format(state.today)} steps" else "Steps",
                    trailing = "${(state.progress * 100).roundToInt()}%",
                )
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.fillMaxWidth().height(chartHeight(min = if (wide) 20.dp else 14.dp, reserved = 44.dp))) {
                    // One row: sixteen dots spread thinly across two rows read as a pattern
                    // rather than as a count, and a count is what this is.
                    val step = size.width / total
                    val dot = minOf(step * 0.62f, size.height)
                    repeat(total) { index ->
                        val centre = Offset(step * index + step / 2f, size.height / 2f)
                        val color = if (index < lit) colors.onBackground else colors.hairline
                        if (square) {
                            drawRect(color, Offset(centre.x - dot / 2f, centre.y - dot / 2f), Size(dot, dot))
                        } else {
                            drawCircle(color, dot / 2f, centre)
                        }
                    }
                }
                Setup(context) { }
            }
        }
    }

    /**
     * Today against the seven-day average, as two bars and one sentence. The only steps skin
     * that answers "is this a normal day for me" rather than "how far to the goal", which is a
     * different and usually more interesting question.
     */
    private object AverageStyle : BlockStyle {
        override val id = "average"
        override val label = "Average"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val state = context.steps
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val past = state.history.dropLast(1).map { it.steps }.filter { it > 0 }
            val average = if (past.isEmpty()) 0 else past.sum() / past.size
            val peak = maxOf(state.today, average, 1)
            BlockColumn(modifier.fillMaxWidth().open(context)) {
                BlockCaptionRow("Today", trailing = if (state.granted) format(state.today) else "--")
                Spacer(Modifier.height(4.dp))
                Bar(state.today / peak.toFloat(), colors.onBackground, wide)
                Spacer(Modifier.height(10.dp))
                BlockCaptionRow("7-day average", trailing = if (average > 0) format(average) else "--")
                Spacer(Modifier.height(4.dp))
                Bar(average / peak.toFloat(), colors.tertiary, wide)
                Spacer(Modifier.height(8.dp))
                Setup(context) {
                    Caption(
                        when {
                            average <= 0 -> "Not enough days recorded yet"
                            state.today >= average -> "${format(state.today - average)} above your average"
                            else -> "${format(average - state.today)} below your average"
                        },
                        lines = 2,
                    )
                }
            }
        }

        @Composable
        private fun Bar(fraction: Float, color: androidx.compose.ui.graphics.Color, wide: Boolean) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(if (wide) 12.dp else 8.dp)
                    .background(NulisTheme.colors.surfaceRaised, NulisShapes.pill),
                contentAlignment = blockAlign().box,
            ) {
                // At zero the rounded ends of an empty pill look like a slider thumb, so
                // nothing is drawn at all until there is something to draw.
                if (fraction > 0.01f) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction.coerceIn(0.03f, 1f))
                            .height(if (wide) 12.dp else 8.dp)
                            .background(color, NulisShapes.pill),
                    )
                }
            }
        }
    }

    /** One dot is five hundred steps: small enough to move on a normal walk, big enough to count. */
    private const val STEPS_PER_DOT = 500

    @Composable
    private fun Setup(context: BlockContext, granted: @Composable () -> Unit) {
        val state = context.steps
        when {
            !state.sensorAvailable -> Text("No step sensor on this phone", style = NulisTheme.type.bodyM, color = NulisTheme.colors.secondary)
            !state.granted -> Text("Tap to allow step counting", style = NulisTheme.type.bodyM, color = NulisTheme.colors.secondary)
            else -> granted()
        }
    }

    @Composable
    private fun Modifier.open(context: BlockContext): Modifier = this
        .pressFeedback()
        .clickable(remember { MutableInteractionSource() }, indication = null) { context.openScreen(ScreenRequest(type)) }
}

/**
 * The last seven days.
 *
 * It used to be a big empty box with a row of three-pixel stubs along the bottom, because every
 * day the phone had no record of arrived as a zero and a zero drew a flat line. Four things fix
 * that, and they are all about the reader rather than the data:
 *
 * - **A day with no record says so.** A hollow outline and a dash instead of a number, which is
 *   the honest answer for the five days before you installed this.
 * - **A day with steps is always a bar you can see.** Two hundred steps against a goal of eight
 *   thousand is 2.5% of the height; the floor is [MinBarFraction], so a short day is short
 *   rather than invisible.
 * - **The number is above the bar**, because reading a height off an unlabelled axis is not
 *   reading.
 * - **The goal is a dashed line with the word GOAL on it**, rather than a hairline that looked
 *   like part of the frame.
 *
 * Today is drawn in the ink and named in the ink; every other day is quieter.
 */
@Composable
fun WeekChart(
    state: StepsState,
    modifier: Modifier = Modifier,
    labels: Boolean = false,
    /** The count over each bar. Off on a block too small to read one. */
    values: Boolean = false,
) {
    val colors = NulisTheme.colors
    val locale = Locale.getDefault()
    val days = state.history
    if (days.isEmpty()) return
    // The tallest thing on the chart is whichever is bigger, the best day or the goal, so the
    // goal line is always on the chart and a day that beat it is always above the line.
    val max = maxOf(state.goal, days.maxOf { it.steps }).coerceAtLeast(1)
    val goalFraction = (state.goal / max.toFloat()).coerceIn(0f, 1f)

    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            GoalLine(goalFraction, Modifier.fillMaxSize())
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                days.forEachIndexed { index, day ->
                    val today = index == days.lastIndex
                    val fraction = when {
                        !day.recorded -> MinBarFraction
                        else -> (day.steps / max.toFloat()).coerceIn(MinBarFraction, 1f)
                    }
                    Column(
                        Modifier.weight(1f).fillMaxHeight(),
                        verticalArrangement = Arrangement.Bottom,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // The spacer and the bar share the height by weight, so every column's
                        // number sits at the same distance above its own bar.
                        Spacer(Modifier.weight((1f - fraction).coerceAtLeast(0.0001f)))
                        if (values) {
                            Text(
                                text = if (day.recorded) shortCount(day.steps) else "–",
                                style = NulisTheme.type.label,
                                color = if (today) colors.onBackground else colors.tertiary,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(bottom = 3.dp),
                            )
                        }
                        Bar(
                            recorded = day.recorded,
                            today = today,
                            modifier = Modifier.fillMaxWidth().weight(fraction),
                        )
                    }
                }
            }
        }
        if (labels) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                days.forEachIndexed { index, day ->
                    val today = index == days.lastIndex
                    Text(
                        text = day.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).take(2).uppercase(locale),
                        style = NulisTheme.type.label,
                        color = if (today) colors.onBackground else colors.tertiary,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** A day that happened, or the outline of one that the phone has no record of. */
@Composable
private fun Bar(recorded: Boolean, today: Boolean, modifier: Modifier) {
    val colors = NulisTheme.colors
    if (!recorded) {
        Box(
            modifier.drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    color = colors.hairline,
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            },
        )
        return
    }
    Box(
        modifier.background(
            color = if (today) colors.onBackground else colors.tertiary,
            shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 1.dp, bottomEnd = 1.dp),
        ),
    )
}

/** The goal, dashed and named, drawn behind the bars. */
@Composable
private fun GoalLine(fraction: Float, modifier: Modifier) {
    val colors = NulisTheme.colors
    Column(modifier) {
        Spacer(Modifier.weight((1f - fraction).coerceAtLeast(0.0001f)))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.weight(1f).height(1.dp)) {
                drawLine(
                    color = colors.secondary,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                )
            }
            Text(
                text = "GOAL",
                style = NulisTheme.type.label,
                color = colors.secondary,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Spacer(Modifier.weight(fraction.coerceAtLeast(0.0001f)))
    }
}

/** How short a bar is allowed to get: visible as a bar, never mistaken for the baseline. */
private const val MinBarFraction = 0.06f

/** 8,240 steps is "8.2k" over a bar two fingers wide. Under a thousand it stays itself. */
private fun shortCount(steps: Int): String = when {
    steps >= 10_000 -> "${steps / 1_000}k"
    steps >= 1_000 -> "%.1fk".format(Locale.getDefault(), steps / 1_000f)
    else -> steps.toString()
}

@Composable
@SuppressLint("InlinedApi")
private fun ActivityRecognitionExplanation(context: BlockContext, onClose: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { context.stepsActions.permissionChanged() }
    PermissionScreen(
        title = "Steps",
        explanation = "To count your steps, Nulis needs the physical activity permission. Android will ask you next.",
        points = listOf(
            "Nulis reads the phone's step counter while it is on screen, plus once just before midnight. No background service, no location.",
            "Counts stay on this phone; the last two weeks are kept for the chart.",
            "Revoke it any time in app settings; the block simply stops counting.",
        ),
        onAllow = { launcher.launch(Manifest.permission.ACTIVITY_RECOGNITION) },
        onNotNow = onClose,
    )
}

@Composable
private fun StepsScreen(context: BlockContext, onClose: () -> Unit) {
    val state = context.steps
    val colors = NulisTheme.colors
    NulisScreen(label = "Steps today", title = "%,d".format(Locale.getDefault(), state.today), onBack = onClose) {
        Caption("${(state.progress * 100).roundToInt()}% of the goal")
        Spacer(Modifier.height(24.dp))
        val walked = state.history.count { it.recorded && it.steps > 0 }
        SectionLabel("Last 7 days")
        Spacer(Modifier.height(8.dp))
        // A chart is as tall as it has something to say. A week with one day on it in a box the
        // height of a hand is what made this read as broken rather than as new.
        WeekChart(
            state = state,
            modifier = Modifier.fillMaxWidth().height(
                when {
                    walked >= 4 -> 180.dp
                    walked > 0 -> 132.dp
                    else -> 96.dp
                },
            ),
            labels = true,
            values = true,
        )
        if (walked < state.history.size) {
            Spacer(Modifier.height(8.dp))
            Caption("A dashed outline is a day from before Nulis started counting", lines = 2)
        }
        Spacer(Modifier.height(24.dp))
        SectionLabel("Goal")
        Spacer(Modifier.height(8.dp))
        var goal by remember(state.goal) { mutableFloatStateOf(state.goal.toFloat()) }
        NulisSlider(
            label = "Steps per day",
            value = (goal - 1_000f) / 29_000f,
            onValueChange = {
                goal = (1_000f + it * 29_000f).let { g -> (g / 500f).roundToInt() * 500f }
                context.stepsActions.setGoal(goal.roundToInt())
            },
            readout = "%,d".format(Locale.getDefault(), goal.roundToInt()),
        )
        Spacer(Modifier.height(16.dp))
        // One sentence. The old one was three, and two of them were about how Nulis is built.
        Text(
            "Your phone counts every step you take; Nulis reads that count when you are on your home screen and once just before midnight, so each day's total lands on the right day.",
            style = NulisTheme.type.bodyS,
            color = colors.secondary,
        )
    }
}

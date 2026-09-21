// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.focus

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockBox
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.ScreenRequest
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.blocks.blockIsAnimating
import com.nulis.launcher.blocks.dialSize
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.NulisSlider
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisTheme
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A stretch of doing one thing. Start it from the block, watch a ring close, and when it is
 * finished the minutes are filed. The screen behind it breathes at four seconds in, six out,
 * which is a rate people can actually hold.
 *
 * Nothing here runs in the background: Nulis has no service and no notification. A session is
 * a wall-clock end time, so leaving and coming back picks up where it was.
 */
object FocusBlockDefinition : BlockDefinition {
    override val type = "focus"
    override val label = "Focus"

    override val minSpan = GridSpan(2, 2)
    override val defaultSpan = GridSpan(6, 3)
    override val styles: List<BlockStyle> = listOf(RingStyle, BreathStyle, MinutesStyle)
    override val previewHeight get() = 130.dp

    override fun accessibilityLabel(block: Block, context: BlockContext): String {
        val running = context.focus.running
        return when {
            running == null -> "Focus, ${context.focus.todayMinutes} mindful minutes today"
            running.isBreak -> "Break running"
            else -> "Focus session running"
        }
    }

    @Composable
    override fun Screen(request: ScreenRequest, context: BlockContext, onClose: () -> Unit) =
        FocusScreen(context, onClose)

    override fun tapAction(block: Block, context: BlockContext): () -> Unit =
        { context.openScreen(ScreenRequest(type)) }

    /** A ring that closes as the session runs, with the time left inside it. */
    private object RingStyle : BlockStyle {
        override val id = "ring"
        override val label = "Ring"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val state = context.focus
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val size = dialSize(min = if (wide) 150.dp else 96.dp, reserved = 4.dp)
            val remaining = rememberRemaining(state)
            val running = state.running
            BlockBox(modifier) {
                Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                    val fraction = if (running == null) 0f else {
                        1f - (remaining / (running.totalMinutes * 60f)).coerceIn(0f, 1f)
                    }
                    Canvas(Modifier.size(size)) {
                        val stroke = this.size.minDimension * 0.055f
                        val inset = stroke / 2f
                        drawArc(
                            color = colors.hairline,
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = Offset(inset, inset),
                            size = Size(this.size.width - stroke, this.size.height - stroke),
                            style = Stroke(stroke, cap = StrokeCap.Round),
                        )
                        if (fraction > 0f) {
                            drawArc(
                                color = if (running?.isBreak == true) colors.secondary else colors.onBackground,
                                startAngle = -90f,
                                sweepAngle = 360f * fraction,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(this.size.width - stroke, this.size.height - stroke),
                                style = Stroke(stroke, cap = StrokeCap.Round),
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (running == null) "${state.focusMinutes}" else clock(remaining),
                            style = NulisTheme.type.displayS.copy(fontSize = if (wide) 26.sp else 18.sp),
                            color = colors.onBackground,
                        )
                        Caption(
                            when {
                                running == null -> "min"
                                running.isBreak -> "break"
                                else -> "focus"
                            },
                        )
                    }
                }
            }
        }
    }

    /** A circle that grows and shrinks at a breathing pace, with the count underneath. */
    private object BreathStyle : BlockStyle {
        override val id = "breath"
        override val label = "Breath"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val state = context.focus
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val size = dialSize(min = if (wide) 150.dp else 96.dp, reserved = 40.dp)
            val phase = rememberBreath(state.running != null)
            BlockColumn(modifier) {
                BlockBox {
                    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
                        Canvas(Modifier.size(size)) {
                            val maxRadius = this.size.minDimension / 2f - 2.dp.toPx()
                            // Rests at two thirds so a still block does not look like an error.
                            val radius = maxRadius * (0.62f + 0.38f * phase)
                            drawCircle(colors.hairline, maxRadius, style = Stroke(1.5.dp.toPx()))
                            drawCircle(colors.surfaceRaised, radius)
                            drawCircle(colors.onBackground, radius, style = Stroke(2.dp.toPx()))
                        }
                        Text(
                            text = if (state.running == null) "Breathe" else breathWord(phase),
                            style = NulisTheme.type.label,
                            color = colors.secondary,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                BlockCaptionRow("Mindful minutes", trailing = state.todayMinutes.toString())
            }
        }
    }

    /** No timer at all: how much focus there has been today and this week. */
    private object MinutesStyle : BlockStyle {
        override val id = "minutes"
        override val label = "Minutes"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val state = context.focus
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            BlockColumn(modifier) {
                BlockRow(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = state.todayMinutes.toString(),
                        style = if (wide) NulisTheme.type.displayL else NulisTheme.type.displayM,
                        color = colors.onBackground,
                    )
                    Spacer(Modifier.width(12.dp))
                    Caption("mindful minutes")
                }
                Spacer(Modifier.height(4.dp))
                BlockCaptionRow(
                    label = if (state.todaySessions == 1) "1 session today" else "${state.todaySessions} sessions today",
                    trailing = "${state.weekMinutes} this week",
                )
            }
        }
    }

    // ------------------------------------------------------------------ shared

    /** Seconds left in the running session, ticking once a second and only while on screen. */
    @Composable
    private fun rememberRemaining(state: FocusState): Float {
        val running = state.running ?: return 0f
        val animating = blockIsAnimating()
        var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(running, animating) {
            if (!animating) return@LaunchedEffect
            while (true) {
                now = withFrameMillis { System.currentTimeMillis() }
                kotlinx.coroutines.delay(1_000L - System.currentTimeMillis() % 1_000L)
            }
        }
        return ((running.endsAtMillis - now) / 1000f).coerceAtLeast(0f)
    }

    /** 0 at the bottom of a breath, 1 at the top. Ten seconds a cycle; still when not running. */
    @Composable
    fun rememberBreath(running: Boolean): Float {
        val animating = blockIsAnimating() && running
        val clock = remember { Animatable(0f) }
        LaunchedEffect(animating) {
            if (!animating) {
                clock.snapTo(0f)
                return@LaunchedEffect
            }
            while (true) {
                clock.snapTo(0f)
                clock.animateTo(1f, tween(BreathCycleMillis, easing = LinearEasing))
            }
        }
        if (!animating) return 0f
        // Four seconds in, six out: the asymmetry is what makes it calming rather than a metronome.
        val t = clock.value
        return if (t < 0.4f) {
            sin((t / 0.4f) * (PI / 2)).toFloat()
        } else {
            sin((1f - (t - 0.4f) / 0.6f) * (PI / 2)).toFloat()
        }
    }

    private const val BreathCycleMillis = 10_000

    private fun breathWord(phase: Float): String = if (phase > 0.55f) "Hold" else "Breathe"

    fun clock(seconds: Float): String {
        val total = seconds.roundToInt()
        return "%d:%02d".format(total / 60, total % 60)
    }
}

/** Start, stop, lengths, and the ring at full size. */
@Composable
private fun FocusScreen(context: BlockContext, onClose: () -> Unit) {
    val state = context.focus
    val colors = NulisTheme.colors
    val running = state.running
    val phase = FocusBlockDefinition.rememberBreath(running != null)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(running) {
        if (running == null) return@LaunchedEffect
        while (true) {
            now = withFrameMillis { System.currentTimeMillis() }
            kotlinx.coroutines.delay(250)
        }
    }
    val remaining = if (running == null) 0f else ((running.endsAtMillis - now) / 1000f).coerceAtLeast(0f)
    // The session files itself the moment it reaches zero, without anyone having to be watching.
    LaunchedEffect(running, remaining <= 0f) {
        if (running != null && remaining <= 0f) context.focusActions.finish()
    }

    NulisScreen(
        label = if (running?.isBreak == true) "Break" else "Focus",
        title = if (running == null) "${state.todayMinutes} min today" else FocusBlockDefinition.clock(remaining),
        onBack = onClose,
    ) {
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(240.dp)) {
                val maxRadius = size.minDimension / 2f - 4.dp.toPx()
                val radius = maxRadius * (0.55f + 0.45f * phase)
                drawCircle(colors.hairline, maxRadius, style = Stroke(1.5.dp.toPx()))
                drawCircle(colors.surface, radius)
                drawCircle(colors.onBackground, radius, style = Stroke(2.dp.toPx()))
                if (running != null) {
                    val done = 1f - (remaining / (running.totalMinutes * 60f)).coerceIn(0f, 1f)
                    drawArc(
                        color = colors.onBackground,
                        startAngle = -90f,
                        sweepAngle = 360f * done,
                        useCenter = false,
                        topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                        size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
        }
        if (running == null) {
            SectionLabel("Lengths")
            NulisSlider(
                label = "Focus",
                value = (state.focusMinutes - 5) / 85f,
                onValueChange = { context.focusActions.setLengths((5 + it * 85).roundToInt(), state.breakMinutes) },
                readout = "${state.focusMinutes} min",
            )
            NulisSlider(
                label = "Break",
                value = (state.breakMinutes - 1) / 29f,
                onValueChange = { context.focusActions.setLengths(state.focusMinutes, (1 + it * 29).roundToInt()) },
                readout = "${state.breakMinutes} min",
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    text = "Start focus",
                    tone = PillTone.Primary,
                    onClick = { context.focusActions.start(isBreak = false) },
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = "Break",
                    onClick = { context.focusActions.start(isBreak = true) },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Caption("Leave the phone alone. Nothing runs in the background; the clock is the clock.", lines = 2)
            Spacer(Modifier.height(12.dp))
            PillButton(
                text = "Give up",
                tone = PillTone.Danger,
                onClick = { context.focusActions.stop() },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "${state.weekMinutes} minutes of focus in the last seven days.",
            style = NulisTheme.type.bodyS,
            color = colors.secondary,
        )
        Spacer(Modifier.height(16.dp))
    }
}

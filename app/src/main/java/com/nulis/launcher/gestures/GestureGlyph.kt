// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.gestures

import androidx.compose.animation.core.LinearEasing
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.preferredFrameRate
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.LineStyle
import com.nulis.launcher.ui.theme.NulisTheme
import kotlin.math.abs

/** Length of one demonstration loop. Every row in the list shares the same clock. */
private const val CycleMillis = 2200

/**
 * A finger crossing a 28dp square reads perfectly at 30 fps, and asking for it keeps a settings
 * screen that is only ever glanced at from driving the display at 90 Hz for as long as it is open.
 */
private const val DemoFrameRate = 30f

/** One clock for a whole list of gesture rows, so seven glyphs cost one animation. */
@Composable
fun rememberGesturePhase(): State<Float> {
    val transition = rememberInfiniteTransition(label = "gesture")
    return transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(CycleMillis, easing = LinearEasing)),
        label = "gesturePhase",
    )
}

/**
 * The same clock, run [cycles] times and then left at rest. For a demonstration on the home page
 * itself, where a loop that never ends would keep the display awake for as long as the page is
 * on screen: three times is enough to be seen, and after that it costs nothing.
 */
@Composable
fun rememberFiniteGesturePhase(key: Any?, cycles: Int = 3, running: Boolean = true): State<Float> {
    val phase = remember(key) { Animatable(0f) }
    LaunchedEffect(key, running) {
        // Something composed but not on screen (the drawer, kept warm behind the pages) waits.
        if (!running) return@LaunchedEffect
        repeat(cycles) {
            phase.snapTo(0f)
            phase.animateTo(1f, tween(CycleMillis, easing = LinearEasing))
        }
    }
    return phase.asState()
}

/**
 * A finger swiping across rather than up: paging sideways, which is not a gesture anyone can
 * rebind, so it has no [GestureTrigger] of its own.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SidewaysGlyph(phase: State<Float>, modifier: Modifier = Modifier) {
    val colors = NulisTheme.colors
    val dotted = NulisTheme.look.lineStyle == LineStyle.DOTTED
    Canvas(modifier.size(28.dp).graphicsLayer { }.preferredFrameRate(DemoFrameRate)) {
        val p = phase.value
        val travel = (p / 0.72f).coerceIn(0f, 1f)
        val eased = travel * travel * (3f - 2f * travel)
        val x = Bottom + (Top - Bottom) * eased
        val alpha = when {
            p < 0.06f -> p / 0.06f
            travel >= 1f -> (1f - (p - 0.72f) / 0.16f).coerceIn(0f, 1f)
            else -> 1f
        }
        if (dotted) {
            repeat(5) { i -> drawCircle(colors.tertiary, 1.1.dp.toPx(), at(Top + (Bottom - Top) * i / 4f, 0.5f)) }
        } else {
            drawLine(colors.tertiary, at(Top, 0.5f), at(Bottom, 0.5f), 1.dp.toPx())
        }
        drawCircle(colors.onBackground, 3.4.dp.toPx(), at(x, 0.5f), alpha = alpha)
    }
}

/**
 * A 28dp loop that acts out the gesture: a fingertip travelling along a track for the swipes,
 * pulses for the taps, a slowly filling ring for the long press. The track follows the Look, so
 * it is dotted in Dot and solid in Clean. [phase] is read inside the draw lambda only, so the
 * animation never recomposes the row it sits in.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun GestureGlyph(trigger: GestureTrigger, phase: State<Float>, modifier: Modifier = Modifier) {
    val colors = NulisTheme.colors
    val dotted = NulisTheme.look.lineStyle == LineStyle.DOTTED
    // Its own layer, so a looping glyph only ever dirties its own 28dp square: without one the
    // invalidation would travel to the root and repaint the whole settings screen every frame.
    Canvas(modifier.size(28.dp).graphicsLayer { }.preferredFrameRate(DemoFrameRate)) {
        val p = phase.value
        when (trigger) {
            GestureTrigger.SWIPE_UP -> swipe(p, up = true, xs = Center, dotted = dotted, colors.tertiary, colors.onBackground)
            GestureTrigger.SWIPE_DOWN -> swipe(p, up = false, xs = Center, dotted = dotted, colors.tertiary, colors.onBackground)
            GestureTrigger.TWO_FINGER_SWIPE_UP -> swipe(p, up = true, xs = Pair2, dotted = dotted, colors.tertiary, colors.onBackground)
            GestureTrigger.TWO_FINGER_SWIPE_DOWN -> swipe(p, up = false, xs = Pair2, dotted = dotted, colors.tertiary, colors.onBackground)
            GestureTrigger.DOUBLE_TAP -> doubleTap(p, colors.tertiary, colors.onBackground)
            GestureTrigger.LONG_PRESS -> longPress(p, colors.tertiary, colors.onBackground)
            GestureTrigger.DOUBLE_TAP_CLOCK -> {
                block(colors.tertiary)
                doubleTap(p, colors.tertiary, colors.onBackground, scale = 0.62f)
            }
        }
    }
}

private val Center = listOf(0.5f)
private val Pair2 = listOf(0.33f, 0.67f)

private const val Top = 0.2f
private const val Bottom = 0.8f

/** A fingertip running the length of the track, then lifting off for the rest of the loop. */
private fun DrawScope.swipe(phase: Float, up: Boolean, xs: List<Float>, dotted: Boolean, track: Color, tip: Color) {
    val travel = (phase / 0.72f).coerceIn(0f, 1f)
    val eased = travel * travel * (3f - 2f * travel)
    val y = if (up) Bottom + (Top - Bottom) * eased else Top + (Bottom - Top) * eased
    // Fades in as the finger lands and out as it lifts, so the loop has no hard restart.
    val alpha = when {
        phase < 0.06f -> phase / 0.06f
        travel >= 1f -> (1f - (phase - 0.72f) / 0.16f).coerceIn(0f, 1f)
        else -> 1f
    }
    xs.forEach { x ->
        if (dotted) {
            repeat(5) { i -> drawCircle(track, 1.1.dp.toPx(), at(x, Top + (Bottom - Top) * i / 4f)) }
        } else {
            drawLine(track, at(x, Top), at(x, Bottom), 1.dp.toPx())
        }
        drawCircle(tip, 3.4.dp.toPx(), at(x, y), alpha = alpha)
    }
}

/** Two quick pulses in the first half of the loop, then stillness. */
private fun DrawScope.doubleTap(phase: Float, ring: Color, tip: Color, scale: Float = 1f) {
    drawCircle(tip, 3.4.dp.toPx() * scale, at(0.5f, 0.5f))
    listOf(0f, 0.2f).forEach { start ->
        val t = (phase - start) / 0.17f
        if (t in 0f..1f) {
            drawCircle(ring, (4f + 6f * t).dp.toPx() * scale, at(0.5f, 0.5f), alpha = 1f - t, style = Stroke(1.2.dp.toPx()))
        }
    }
}

/** One ring closing slowly onto the fingertip, the way a press has to be held. */
private fun DrawScope.longPress(phase: Float, ring: Color, tip: Color) {
    val t = (phase / 0.7f).coerceIn(0f, 1f)
    val held = 1f - abs(1f - 2f * t.coerceAtMost(1f))
    drawCircle(tip, (3.2f + 1.4f * t).dp.toPx(), at(0.5f, 0.5f))
    drawCircle(ring, (10f - 4f * t).dp.toPx(), at(0.5f, 0.5f), alpha = 0.35f + 0.65f * held, style = Stroke(1.2.dp.toPx()))
}

/** The outline of a block, so the clock gesture reads as "on a block, not on the page". */
private fun DrawScope.block(color: Color) {
    val inset = 0.08f * size.width
    drawRoundRect(
        color = color,
        topLeft = Offset(inset, 0.24f * size.height),
        size = Size(size.width - inset * 2f, 0.52f * size.height),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
        style = Stroke(1.dp.toPx()),
    )
}

private fun DrawScope.at(x: Float, y: Float) = Offset(x * size.width, y * size.height)

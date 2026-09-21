// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.wellbeing

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.blocks.screentime.formatMinutes
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.sin

/** One app, waiting behind a breath. */
class PendingLaunch(
    val app: AppInfo,
    val decision: PauseDecision,
    val seconds: Int,
)

/**
 * The pause. A circle breathing at four seconds in and six out, the reason in one sentence, how
 * long the app has had today, and two ways out: wait and open it anyway, or think better of it.
 *
 * It cannot stop anything. There is no Accessibility Service here and no way to intercept an app
 * opened from anywhere but Nulis; this is a moment of friction the user asked for, not a lock.
 */
@Composable
fun MindfulPauseScreen(
    pending: PendingLaunch,
    onOpen: () -> Unit,
    onNeverMind: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NulisTheme.colors
    BackHandler(onBack = onNeverMind)

    // The countdown and the breath share one clock so the ring and the circle agree.
    var elapsed by remember(pending) { mutableFloatStateOf(0f) }
    LaunchedEffect(pending) {
        val start = withFrameNanos { it }
        while (true) {
            elapsed = (withFrameNanos { it } - start) / 1e9f
        }
    }
    val total = pending.seconds.toFloat()
    val remaining = (total - elapsed).coerceAtLeast(0f)
    val ready = remaining <= 0f
    val breath = breathPhase(elapsed)

    Box(
        modifier
            .fillMaxSize()
            .background(colors.background)
            // Nothing behind this gets a gesture while it is up.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false).consume()
                    while (true) {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                        if (event.changes.none { it.pressed }) break
                    }
                }
            },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = NulisSpacing.screenMargin),
        ) {
            Spacer(Modifier.height(8.dp))
            SectionLabel(reasonLabel(pending.decision.reason), withLine = false)
            Text(
                text = pending.app.label,
                style = NulisTheme.type.displayM,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = sentence(pending),
                style = NulisTheme.type.bodyM,
                color = colors.secondary,
            )

            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Canvas(Modifier.size(220.dp)) {
                        val maxRadius = size.minDimension / 2f - 4.dp.toPx()
                        val radius = maxRadius * (0.5f + 0.5f * breath)
                        drawCircle(colors.hairline, maxRadius, style = Stroke(1.5.dp.toPx()))
                        drawCircle(colors.surface, radius)
                        drawCircle(colors.onBackground, radius, style = Stroke(2.dp.toPx()))
                        // The ring closes as the wait runs out.
                        val done = (elapsed / total).coerceIn(0f, 1f)
                        drawArc(
                            color = colors.secondary,
                            startAngle = -90f,
                            sweepAngle = 360f * done,
                            useCenter = false,
                            topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
                            size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
                            style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Caption(if (ready) "Still want to?" else if (breath > 0.55f) "Hold" else "Breathe")
                }
            }

            Text(
                text = "Nothing is stopping you. This is a pause you asked for.",
                style = NulisTheme.type.bodyS,
                color = colors.tertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            )
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    text = "Never mind",
                    onClick = onNeverMind,
                    modifier = Modifier.weight(1f),
                )
                PillButton(
                    text = if (ready) "Open anyway" else "${ceil(remaining).toInt()}",
                    tone = PillTone.Primary,
                    enabled = ready,
                    onClick = onOpen,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 0 at the bottom of a breath, 1 at the top: four seconds in, six out. */
private fun breathPhase(seconds: Float): Float {
    val t = (seconds % 10f) / 10f
    return if (t < 0.4f) {
        sin((t / 0.4f) * (PI / 2)).toFloat()
    } else {
        sin((1f - (t - 0.4f) / 0.6f) * (PI / 2)).toFloat()
    }
}

private fun reasonLabel(reason: PauseReason?): String = when (reason) {
    PauseReason.FOCUS -> "Focus session"
    PauseReason.OVER_LIMIT -> "Past your limit"
    else -> "A moment first"
}

private fun sentence(pending: PendingLaunch): String {
    val minutes = pending.decision.minutesToday
    // Each branch is a whole sentence. Pasting "in this one" onto a fragment produced
    // "not opened yet today in this one.", which is not a sentence in any dialect.
    val used = if (minutes > 0) {
        "You have spent ${formatMinutes(minutes)} in it today."
    } else {
        "You have not opened it yet today."
    }
    return when (pending.decision.reason) {
        PauseReason.FOCUS -> "You are in a focus session. $used"
        PauseReason.OVER_LIMIT -> "You set yourself ${pending.decision.limit} minutes a day here. $used"
        else -> used
    }
}

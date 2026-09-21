// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.util.lerp
import com.nulis.launcher.ui.theme.NulisMotion
import kotlinx.coroutines.launch

/**
 * Pressed state that appears on the very first touch: the element scales to
 * [NulisMotion.pressScale] and dims to [NulisMotion.pressAlpha]. It never waits for a tap or
 * long-press timeout, and lets go as soon as the finger lifts, moves past touch slop, or a
 * parent scroll takes the gesture. Purely visual; pair it with a clickable for the action.
 *
 * Cheap enough for every list row: one animatable, no effects, and nothing recomposes because
 * the press amount is only read while drawing.
 */
@Composable
fun Modifier.pressFeedback(enabled: Boolean = true): Modifier {
    val press = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    return this
        .pointerInput(enabled) {
            if (!enabled) {
                scope.launch { press.snapTo(0f) }
                return@pointerInput
            }
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                scope.launch { press.animateTo(1f, NulisMotion.pressIn) }
                val slop = viewConfiguration.touchSlop
                while (true) {
                    // Final pass: by now a scrolling parent has consumed the move if it took over.
                    val event = awaitPointerEvent(PointerEventPass.Final)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    if (change.isConsumed && change.positionChanged()) break
                    if ((change.position - down.position).getDistance() > slop) break
                }
                scope.launch { press.animateTo(0f, NulisMotion.pressOut) }
            }
        }
        .graphicsLayer {
            val p = press.value
            val scale = lerp(1f, NulisMotion.pressScale, p)
            scaleX = scale
            scaleY = scale
            alpha = lerp(1f, NulisMotion.pressAlpha, p)
        }
}

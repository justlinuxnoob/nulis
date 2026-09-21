// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.mandatorySystemGestures
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import kotlin.math.abs

/**
 * Keeps Nulis's own gestures out of the strips along the edges of the screen that belong to the
 * system.
 *
 * With gesture navigation, a swipe up from the very bottom edge is home, holding it is recents,
 * and a swipe in from either side is back. The system takes those at the window level whatever
 * the app does, so a drawer that also starts opening there produces a launcher sliding up behind
 * a recents screen sliding in - two things answering one gesture. The fix is not to fight for
 * them: read [WindowInsets.systemGestures], and let a drag that starts inside those strips
 * belong to the system alone.
 *
 * Direction matters, so this gives back as much as it can. A drag that starts low on the screen
 * is only ignored while it is going up or down; sideways from the same spot still pages, because
 * the system wants nothing horizontal there. The same the other way round at the two sides.
 *
 * A **tap** is never taken: consuming starts only once the finger has passed the touch slop, so
 * a block, a button or an app icon in the bottom strip still opens when you tap it.
 */
@Composable
fun Modifier.outsideSystemGestures(): Modifier {
    val insets = WindowInsets.systemGestures
    // The two sides come through as systemGestures and the bottom bar as mandatorySystemGestures
    // - on a Pixel the bottom is only in the second one - so both are read and the larger of the
    // two bottoms is taken. The top is deliberately left alone: the shade lives up there, and
    // Nulis's own swipe down is meant to reach it.
    val mandatory = WindowInsets.mandatorySystemGestures
    val density = LocalDensity.current
    val direction = LocalLayoutDirection.current
    val left = maxOf(insets.getLeft(density, direction), mandatory.getLeft(density, direction))
    val right = maxOf(insets.getRight(density, direction), mandatory.getRight(density, direction))
    val bottom = maxOf(insets.getBottom(density), mandatory.getBottom(density))
    val bands = remember(left, right, bottom) { intArrayOf(left, right, bottom) }

    return pointerInput(bands) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            val nearBottom = down.position.y >= size.height - bands[2]
            val nearSide = down.position.x <= bands[0] || down.position.x >= size.width - bands[1]
            if (!nearBottom && !nearSide) return@awaitEachGesture

            val slop = viewConfiguration.touchSlop
            var consuming = false
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!consuming) {
                    val travel = change.position - down.position
                    if (travel.getDistance() > slop) {
                        val vertical = abs(travel.y) >= abs(travel.x)
                        // Upwards from the bottom edge is the one gesture both sides want, and
                        // the one place refusing it costs something. Android reads it as Home,
                        // and Home on a launcher that is already home does nothing at all - so
                        // the swipe was simply lost. Measured: ten of fifty scripted drawer
                        // swipes failed, and every one of them started inside this strip.
                        // Recents (up and hold) still reaches the system, which watches the
                        // same stream through its own spy window and takes the pointer over the
                        // moment it decides; that arrives here as a cancelled drag and the
                        // drawer settles back where it was.
                        val systemWantsIt = if (nearBottom && vertical) travel.y > 0f else nearSide && !vertical
                        consuming = systemWantsIt
                        // Going the way the system does not want: this gesture is ours after
                        // all, and nothing more needs watching.
                        if (!consuming) break
                    }
                }
                if (consuming) event.changes.forEach { it.consume() }
                if (event.changes.none { it.pressed }) break
            }
        }
    }
}

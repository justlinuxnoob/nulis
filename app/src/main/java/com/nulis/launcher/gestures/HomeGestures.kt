// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.gestures

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.components.SlideState
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisMotion
import kotlin.math.abs

/** How far a swipe must travel before it counts, when it is not dragging the drawer. */
private val SwipeDistance = 72.dp

/**
 * Every vertical gesture on a page, in one place.
 *
 * A swipe up mapped to the drawer keeps today's behaviour exactly: the drawer follows the finger
 * 1:1 and settles on release. Every other vertical swipe is a discrete gesture that fires once,
 * as soon as it has travelled far enough or been flicked hard enough, with the same
 * [NulisHaptics.threshold] tick the drawer gives when it commits.
 *
 * Horizontal movement is never touched, so the pager still owns side-to-side. Drags that start
 * inside a scrolling child reach that child first, as they did before.
 */
@Composable
fun Modifier.pageGestures(
    drawer: SlideState,
    gestures: GestureSettings,
    enabled: Boolean,
    onGesture: (GestureTrigger) -> Unit,
): Modifier {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val state = remember { PageGestureState() }
    state.distancePx = with(density) { SwipeDistance.toPx() }
    state.flingPx = with(density) { NulisMotion.flingThreshold.toPx() }
    state.drawer = drawer
    state.gestures = gestures
    state.onFire = { trigger ->
        if (gestures.isBound(trigger)) {
            haptics.performHapticFeedback(NulisHaptics.threshold)
            onGesture(trigger)
        }
    }

    return this
        // Watches the pointer count without ever consuming, so the drag loop below can tell a
        // two-finger swipe from a one-finger one.
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                state.maxPointers = 1
                while (true) {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val pressed = event.changes.count { it.pressed }
                    if (pressed > state.maxPointers) state.maxPointers = pressed
                    if (pressed == 0) break
                }
            }
        }
        .draggable(
            state = rememberDraggableState { delta -> state.onDelta(delta) },
            orientation = Orientation.Vertical,
            enabled = enabled,
            onDragStarted = { state.onStart() },
            onDragStopped = { velocity -> state.onStop(velocity) },
        )
}

private enum class DragMode { UNDECIDED, DRAWER, DISCRETE }

/**
 * Plain mutable state read only from gesture callbacks, never from composition, so a drag costs
 * nothing beyond the drawer's own draw invalidation.
 */
@Stable
private class PageGestureState {
    var distancePx: Float = 0f
    var flingPx: Float = 0f
    var drawer: SlideState? = null
    var gestures: GestureSettings = GestureSettings()
    var onFire: (GestureTrigger) -> Unit = {}

    /** Most fingers seen at once during the current gesture; set by the watcher above. */
    var maxPointers: Int = 1

    private var total = 0f
    private var mode = DragMode.UNDECIDED
    private var fired = false

    fun onStart() {
        total = 0f
        mode = DragMode.UNDECIDED
        fired = false
    }

    fun onDelta(delta: Float) {
        total += delta
        if (mode == DragMode.UNDECIDED) mode = decide(delta)
        if (mode == DragMode.DRAWER) {
            drawer?.dragBy(delta)
        } else if (!fired && abs(total) >= distancePx) {
            fire()
        }
    }

    suspend fun onStop(velocity: Float) {
        when (mode) {
            DragMode.DRAWER -> drawer?.settle(velocity)
            // A short flick still counts, as long as it is going the way the finger travelled.
            DragMode.DISCRETE -> if (!fired && abs(velocity) >= flingPx && velocity * total > 0f) fire()
            DragMode.UNDECIDED -> Unit
        }
        mode = DragMode.UNDECIDED
    }

    /**
     * Upwards belongs to the drawer whenever swipe up still opens it, so that gesture never loses
     * its 1:1 tracking; two fingers only take it away when the two-finger swipe means something.
     * Everything else is discrete.
     */
    private fun decide(delta: Float): DragMode {
        if (delta >= 0f) return DragMode.DISCRETE
        val stolen = maxPointers >= 2 && gestures.isBound(GestureTrigger.TWO_FINGER_SWIPE_UP)
        // Only when there is a sheet to drag. With the drawer on a page of its own there is
        // nothing under the finger to follow it, and tracking a sheet that is never drawn would
        // swallow the swipe and do nothing with it.
        val draggable = !gestures.drawerIsAPage && gestures.action(GestureTrigger.SWIPE_UP).opensDrawer
        return if (!stolen && draggable) DragMode.DRAWER else DragMode.DISCRETE
    }

    private fun fire() {
        fired = true
        val up = total < 0f
        val twoFingers = maxPointers >= 2
        val two = if (up) GestureTrigger.TWO_FINGER_SWIPE_UP else GestureTrigger.TWO_FINGER_SWIPE_DOWN
        val one = if (up) GestureTrigger.SWIPE_UP else GestureTrigger.SWIPE_DOWN
        // A two-finger swipe with nothing of its own falls back to the one-finger meaning.
        onFire(if (twoFingers && gestures.isBound(two)) two else one)
    }
}

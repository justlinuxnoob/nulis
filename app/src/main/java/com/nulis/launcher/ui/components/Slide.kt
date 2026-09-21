// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import com.nulis.launcher.ui.theme.NulisMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * A surface that slides in from the bottom and follows the finger 1:1. [progress] runs from
 * 0 (fully hidden) to 1 (fully shown); draw it with `translationY = (1 - progress) * height`.
 *
 * Dragging cancels any running animation instantly, so a settle can be caught mid-flight.
 * [settle] picks the side from velocity first (a flick always wins) and position second, then
 * animates there with the critically damped [NulisMotion.settle] spring.
 *
 * Read [progress] only inside draw lambdas (`graphicsLayer { }`) so nothing recomposes per frame.
 */
@Stable
class SlideState(
    private val scope: CoroutineScope,
    // Named apart from the properties they seed on purpose. A constructor parameter stays in
    // scope inside the object expression that builds [nestedScrollConnection] further down,
    // where it silently shadows the property of the same name - so `progress` in there read a
    // frozen 0 for the life of the surface, the connection swallowed every upward scroll delta
    // it was offered, and nothing inside the drawer or a sheet could be scrolled up at all.
    initialTarget: Boolean,
    initialProgress: Float,
    private val onSettle: (shown: Boolean) -> Unit = {},
) {
    var progress by mutableFloatStateOf(initialProgress)
        private set

    /** Where the surface is heading (or resting). Drive back handling and focus from this. */
    var target by mutableStateOf(initialTarget)
        private set

    /** True while a finger drives the surface. */
    var dragging by mutableStateOf(false)
        private set

    /** Distance in px between hidden and shown; set from the surface's measured height. */
    var travelPx: Float = 1f

    /** Velocity (px/s) that commits regardless of position; set from [NulisMotion.flingThreshold]. */
    var flingThresholdPx: Float = 1000f

    private var job: Job? = null

    /** Fully shown and at rest: safe to open a keyboard or run other heavy work. */
    val settledShown: Boolean by derivedStateOf { this.target && !this.dragging && this.progress >= 1f }

    /** Fully hidden and at rest: safe to leave the composition. */
    val settledHidden: Boolean by derivedStateOf { !this.target && !this.dragging && this.progress <= 0f }

    /** Moves the surface by a finger delta in px; positive is downwards (towards hidden). */
    fun dragBy(deltaPx: Float) {
        job?.cancel()
        dragging = true
        progress = (progress - deltaPx / travelPx).coerceIn(0f, 1f)
    }

    /** Lets go with a finger velocity in px/s (positive downwards) and animates to the winning side. */
    fun settle(velocityPxPerSec: Float) {
        val shown = when {
            velocityPxPerSec < -flingThresholdPx -> true
            velocityPxPerSec > flingThresholdPx -> false
            else -> progress > 0.5f
        }
        dragging = false
        animateTo(shown, initialVelocity = -velocityPxPerSec / travelPx)
    }

    fun show() = animateTo(true, 0f)

    fun hide() = animateTo(false, 0f)

    /** Jumps to hidden with no animation, e.g. when the launcher leaves the foreground. */
    fun snapHidden() {
        job?.cancel()
        dragging = false
        target = false
        progress = 0f
    }

    private fun animateTo(shown: Boolean, initialVelocity: Float) {
        val changed = shown != target
        target = shown
        job?.cancel()
        job = scope.launch {
            animate(
                initialValue = progress,
                targetValue = if (shown) 1f else 0f,
                initialVelocity = initialVelocity,
                animationSpec = NulisMotion.settle,
            ) { value, _ -> progress = value }
        }
        if (changed) onSettle(shown)
    }

    /**
     * Lets a scrolling child hand the surface its leftover drag.
     *
     * The content scrolls first and the surface only moves on what is left over: drag down with
     * a list already at its top and the surface follows the finger away; drag up while the
     * surface is part way down and it comes back before the list starts moving again. Nothing
     * here ever takes a delta the content could have used, which is the whole difference
     * between a sheet you can read and a sheet that shuts every time you try.
     */
    val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            // Only while the surface is actually part way down, and only to put it back.
            if (source != NestedScrollSource.UserInput || available.y >= 0f) return Offset.Zero
            if (progress >= 1f) return Offset.Zero
            val before = progress
            dragBy(available.y)
            // Give back exactly as much as the surface moved, so the rest reaches the content.
            return Offset(0f, -(progress - before) * travelPx)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
            dragBy(available.y)
            return Offset(0f, available.y)
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            // A fling only belongs to the surface when the surface is the thing that moved.
            if (!dragging || progress >= 1f) {
                dragging = false
                return Velocity.Zero
            }
            settle(available.y)
            return available
        }
    }
}

@Composable
fun rememberSlideState(
    target: Boolean,
    progress: Float = if (target) 1f else 0f,
    onSettle: (shown: Boolean) -> Unit = {},
): SlideState {
    val scope = rememberCoroutineScope()
    val thresholdPx = with(LocalDensity.current) { NulisMotion.flingThreshold.toPx() }
    return remember(scope) { SlideState(scope, target, progress, onSettle) }
        .also { it.flingThresholdPx = thresholdPx }
}

/** Vertical drag anywhere on this element drives [state] 1:1 and settles on release. */
@Composable
fun Modifier.slideDraggable(state: SlideState, enabled: Boolean = true): Modifier = this.draggable(
    state = rememberDraggableState { delta -> state.dragBy(delta) },
    orientation = Orientation.Vertical,
    enabled = enabled,
    onDragStopped = { velocity -> state.settle(velocity) },
)

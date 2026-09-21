// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.theme

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.dp

/**
 * Motion tokens. Tweens for state and screen changes; springs for anything the finger drives.
 * Every spring is critically damped: things settle fast and never overshoot or bounce.
 */
/**
 * True when the user has asked for less movement. Animated blocks hold still, screen changes
 * cross-fade instead of sliding, and anything decorative simply does not run. Finger-driven
 * motion - the drawer following a drag - is left alone: that is not decoration, it is the
 * gesture itself, and freezing it would feel broken rather than calm.
 */
val LocalReducedMotion = androidx.compose.runtime.staticCompositionLocalOf { false }

/** A screen-transition duration that collapses to nothing when motion is reduced. */
@androidx.compose.runtime.Composable
@androidx.compose.runtime.ReadOnlyComposable
fun screenMotion(): Int = if (LocalReducedMotion.current) 0 else NulisMotion.normal

object NulisMotion {
    /** Tween for small state changes (pressed, selected, tint). */
    const val quick = 150

    /** Tween for screen transitions. */
    const val normal = 200

    /**
     * Settle for the drawer, sheets and dropped blocks after the finger lets go. Runs in
     * 0..1 progress space, so the threshold is a fraction of the travel distance.
     */
    val settle: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = 900f,
        visibilityThreshold = 0.0005f,
    )

    /** Pressed state going in: near-instant. */
    val pressIn: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 4000f)

    /** Pressed state releasing. */
    val pressOut: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1200f)

    /** A block lifting off the page when picked up for reordering. */
    val lift: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)

    /** A dropped block gliding into its slot, in px. */
    val settleDrop: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 900f, visibilityThreshold = 0.5f)

    /** Neighbouring blocks sliding out of the way of a dragged block. */
    val slot: SpringSpec<Float> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 600f, visibilityThreshold = 0.5f)

    /** The same, in dp, for a block gliding from one grid rectangle to another. */
    val slotDp: SpringSpec<androidx.compose.ui.unit.Dp> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 600f, visibilityThreshold = androidx.compose.ui.unit.Dp.VisibilityThreshold)

    /** How far the page scales back when the editor opens: enough to read as a step away. */
    const val editScale = 0.92f

    /** Scale of a pressed control. */
    const val pressScale = 0.97f

    /** Alpha of a pressed control. */
    const val pressAlpha = 0.6f

    /** Scale of a block being dragged. */
    const val liftScale = 1.03f

    /** How far home scales back while the drawer covers it. */
    const val homeBehindDrawerScale = 0.94f

    /** How far home fades while the drawer covers it. */
    const val homeBehindDrawerAlpha = 0.4f

    /** Flick speed that commits a drawer or sheet regardless of how far it has travelled. */
    val flingThreshold = 400.dp

    /** Distance from a scroll edge at which a dragged block starts auto-scrolling the page. */
    val autoScrollEdge = 96.dp

    /** Auto-scroll speed at the very edge. */
    val autoScrollSpeed = 1200.dp
}

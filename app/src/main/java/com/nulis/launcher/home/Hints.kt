// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nulis.launcher.drawer.DrawerPlacement
import com.nulis.launcher.gestures.GestureGlyph
import com.nulis.launcher.gestures.GestureTrigger
import com.nulis.launcher.gestures.SidewaysGlyph
import com.nulis.launcher.gestures.rememberFiniteGesturePhase
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.theme.NulisMotion

/**
 * The three things a launcher never says out loud, and the one the drawer does not either.
 *
 * Somebody who has never had a launcher like this one sees a clock, a date and six icons, and
 * nothing on that screen says that everything else is one swipe up, that the page itself is
 * theirs to rearrange, or that there are more pages to either side. A tutorial is skipped; a
 * coach mark is dismissed unread. So each of these sits quietly along the bottom of the home
 * page, one at a time, in the order they matter - and goes away for good the first time
 * somebody actually does it, which is the only proof anyone has learned anything.
 */
enum class Hint(val key: String) {
    /** Where all the apps are. */
    DRAWER("drawer"),

    /** That a long press edits the page. */
    EDIT("edit"),

    /** That there is more than one page. */
    PAGES("pages"),

    /** Inside the drawer: that holding an app does something. */
    APP_MENU("app_menu"),
}

/** What one hint says and which gesture it acts out. */
data class HintLine(val hint: Hint, val text: String, val gesture: HintGesture)

enum class HintGesture { SWIPE_UP, HOLD, SIDEWAYS }

/**
 * The home page's next hint, or null when there is nothing left to say.
 *
 * Each is only offered while it is true: a drawer somebody has turned off is not advertised, and
 * nor is a long press they have bound to something else, or pages on a phone with only one.
 */
fun nextHomeHint(
    learned: Set<String>,
    drawerPlacement: DrawerPlacement,
    swipeUpOpensDrawer: Boolean,
    longPressEdits: Boolean,
    pageCount: Int,
): HintLine? {
    fun open(hint: Hint) = hint.key !in learned
    if (open(Hint.DRAWER)) {
        when (drawerPlacement) {
            DrawerPlacement.SWIPE_UP -> if (swipeUpOpensDrawer) return HintLine(Hint.DRAWER, "Swipe up for all your apps", HintGesture.SWIPE_UP)
            DrawerPlacement.LEFT_PAGE -> return HintLine(Hint.DRAWER, "All your apps are on the page to the left", HintGesture.SIDEWAYS)
            DrawerPlacement.RIGHT_PAGE -> return HintLine(Hint.DRAWER, "All your apps are on the page to the right", HintGesture.SIDEWAYS)
            DrawerPlacement.OFF -> Unit
        }
    }
    if (open(Hint.EDIT)) {
        return HintLine(
            Hint.EDIT,
            if (longPressEdits) "Hold anywhere to change this page" else "Hold a block to change this page",
            HintGesture.HOLD,
        )
    }
    if (open(Hint.PAGES) && pageCount > 1) return HintLine(Hint.PAGES, "Swipe sideways for your other pages", HintGesture.SIDEWAYS)
    return null
}

/**
 * One hint, acted out by a small gesture glyph beside it. The glyph plays three times and then
 * rests, so a hint left on screen all afternoon costs nothing after its first few seconds.
 */
@Composable
fun HomeHint(line: HintLine?, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = line,
        transitionSpec = { fadeIn(tween(NulisMotion.normal, delayMillis = NulisMotion.normal)) togetherWith fadeOut(tween(NulisMotion.quick)) },
        contentAlignment = Alignment.Center,
        modifier = modifier,
        label = "homeHint",
    ) { shown ->
        if (shown == null) {
            Box(Modifier)
            return@AnimatedContent
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
            val phase = rememberFiniteGesturePhase(shown.hint)
            // Smaller than in settings: this lives in the strip the page dots use, under the grid.
            val glyph = Modifier.size(20.dp)
            when (shown.gesture) {
                HintGesture.SWIPE_UP -> GestureGlyph(GestureTrigger.SWIPE_UP, phase, glyph)
                HintGesture.HOLD -> GestureGlyph(GestureTrigger.LONG_PRESS, phase, glyph)
                HintGesture.SIDEWAYS -> SidewaysGlyph(phase, glyph)
            }
            Spacer(Modifier.width(6.dp))
            // Two lines at most: at the largest font scale one line is not enough to say it.
            Caption(shown.text, lines = 2)
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.drawer

/**
 * Where the app drawer lives.
 *
 * A swipe up is what a launcher usually does, and it is still the default. But the swipe is the
 * most valuable gesture on the page - it is the one everybody's thumb already knows - and a
 * drawer that has a page of its own does not need it. Putting the drawer on a page also makes it
 * behave like a page: it is simply *there*, one swipe sideways, its list scrolls the way any list
 * does, and nothing is tracking a drag against a sheet.
 */
enum class DrawerPlacement(val id: String, val label: String, val hint: String) {
    /** The sheet that comes up from the bottom, tracking the finger. */
    SWIPE_UP("swipe_up", "Swipe up", "Pulls up over the page"),

    /** An extra page before the first one. */
    LEFT_PAGE("left", "Left page", "One swipe left of your first page"),

    /** An extra page after the last one. */
    RIGHT_PAGE("right", "Right page", "One swipe right of your last page"),

    /** Nowhere by itself; reachable only through a gesture you choose. */
    OFF("off", "Off", "Only from a gesture you set");

    /** True when the drawer is one of the pages the pager swipes through. */
    val isPage: Boolean get() = this == LEFT_PAGE || this == RIGHT_PAGE

    /**
     * True when the upward swipe is nobody's by default any more, so it can be mapped to
     * something else - which is half the reason to move the drawer in the first place.
     */
    val freesSwipeUp: Boolean get() = this != SWIPE_UP

    companion object {
        fun byId(id: String?): DrawerPlacement? = entries.firstOrNull { it.id == id }
    }
}

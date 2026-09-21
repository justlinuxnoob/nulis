// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * The three page ids Nulis has always shipped with. They are ordinary ids now - a page added
 * later gets one of its own - but these three are what a fresh install starts from and what
 * every backup written before pages could be added or removed refers to.
 */
object PageIds {
    const val LEFT = "left"
    const val HOME = "home"
    const val RIGHT = "right"

    /** The pages a fresh install starts with, in swipe order. */
    val all: List<String> = listOf(LEFT, HOME, RIGHT)
    val homeIndex: Int = all.indexOf(HOME)

    fun newId(): String = "page_" + UUID.randomUUID().toString().take(8)
}

/**
 * Which pages there are, in swipe order, and which of them is home.
 *
 * Home is a mark rather than a position: it is the page the Home key brings you to and the one
 * the launcher opens on, and it can be any of them. Pages either side of it are simply pages -
 * there is nothing special about "left" and "right" any more beyond their ids.
 */
@Serializable
data class PagesConfig(
    val ids: List<String> = PageIds.all,
    val homeId: String = PageIds.HOME,
) {
    val homeIndex: Int get() = ids.indexOf(homeId).coerceAtLeast(0)

    val canAdd: Boolean get() = ids.size < MAX

    /** Never the last one: a launcher with no page at all is a black screen with no way out. */
    val canRemove: Boolean get() = ids.size > 1

    /** Which page a swipe in each direction reaches, for the page name in the editor. */
    fun indexOf(pageId: String): Int = ids.indexOf(pageId)

    /** Tidied: no unknown home, no duplicates, never empty, never more than [MAX]. */
    fun sane(): PagesConfig {
        val unique = ids.distinct().filter { it.isNotBlank() }.take(MAX)
        val kept = unique.ifEmpty { listOf(PageIds.HOME) }
        return PagesConfig(kept, if (homeId in kept) homeId else kept[kept.size / 2])
    }

    companion object {
        /** Five is more pages than anybody swipes through, and the point of Nulis is fewer. */
        const val MAX = 5
    }
}

/**
 * What a page is called: where it sits relative to home.
 *
 * On the three pages a fresh install has, this says exactly what it always said - Left, Home,
 * Right - and it keeps saying something true when there are five of them, or when the home mark
 * has been moved to the end. A page's id is never part of its name, because an id outlives the
 * position that gave it its name.
 */
fun PagesConfig.nameOf(pageId: String): String {
    val index = indexOf(pageId)
    val distance = index - homeIndex
    return when {
        index < 0 -> "Page"
        distance == 0 -> "Home"
        distance == -1 -> "Left"
        distance == 1 -> "Right"
        distance < 0 -> "${-distance} left"
        else -> "$distance right"
    }
}

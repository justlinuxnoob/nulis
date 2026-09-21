// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.setups

import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PagesConfig
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.ui.theme.ColorTheme
import kotlinx.serialization.Serializable

/**
 * A whole phone kept under a name: every page with its blocks, styles, alignment and spacing,
 * plus the Look, the colours and the typefaces.
 *
 * This is what "Save my current setup" writes and what applying one puts back. Nulis ships none
 * of these - the things it ships are layouts and looks, which are each half of this - so every
 * one in the list is something the user made.
 *
 * Nothing outside a setup - notes, journal entries, tasks, gestures, icon overrides - is ever
 * touched by applying one.
 */
@Serializable
data class SavedSetup(
    val id: String,
    val name: String,
    /** One line shown under the name in the gallery. */
    val tagline: String = "",
    val lookId: String,
    val colorTheme: ColorTheme,
    /** ARGB background when [colorTheme] is CUSTOM. */
    val customBackground: Int? = null,
    /** ARGB text colour when [colorTheme] is CUSTOM, or null to let contrast decide. */
    val customInk: Int? = null,
    /** Font id for display text, or null for the Look's own face. */
    val displayFont: String? = null,
    /** Font id for body text, or null for Geist. */
    val bodyFont: String? = null,
    /** Global text size multiplier. */
    val textScale: Float = 1f,
    /** Whether mono labels are shouted in capitals. */
    val uppercaseLabels: Boolean = true,
    /** How apps are drawn in the drawer and every list outside a block, or null to leave it. */
    val drawerIcons: Map<String, String>? = null,
    /** Page id -> layout. A page missing from here is left empty. */
    val pages: Map<String, PageLayout> = emptyMap(),
    /** The pages in swipe order. Empty in a file written before pages could be added. */
    val pageOrder: List<String> = emptyList(),
    /** Which page carries the home mark. */
    val homeId: String = PageIds.HOME,
    /**
     * Never true any more: nothing ships as a setup. Kept only so a file written when Nulis had
     * built-in themes still decodes, and forced to false on the way in.
     */
    val builtIn: Boolean = false,
) {
    fun page(pageId: String): PageLayout = pages[pageId] ?: PageLayout(pageId)

    /** The pages in swipe order, falling back to whatever order the map happens to be in. */
    val order: List<String> get() = pageOrder.filter { it in pages.keys }.ifEmpty { pages.keys.toList() }

    val config: PagesConfig get() = PagesConfig(order, homeId).sane()

    /** The page a card shows: the one marked home. */
    val cover: PageLayout get() = page(config.homeId)
}

/** What the user decided to carry across when applying a setup. */
data class ApplyOptions(
    /** Copy the favourites out of the current Apps block into the setup's. */
    val keepApps: Boolean = true,
    /**
     * Keep any notes / journal / tasks blocks the setup does not include, appended to its first
     * page. The writing itself is never deleted either way; this is only about the blocks.
     */
    val keepWritingBlocks: Boolean = true,
)

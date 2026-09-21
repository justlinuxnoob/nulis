// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.layout

import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import java.util.UUID

/**
 * Layouts used when a page has never been saved. There is exactly one of them - the Minimal
 * preset - so "what a fresh install looks like" and "what the Minimal layout is" can never drift
 * apart, and improving one improves the other.
 */
object DefaultLayouts {

    /** The preset a fresh install starts from, and the first entry in the layout picker. */
    val first: LayoutPreset get() = LayoutPresets.Minimal

    fun forPage(pageId: String, favoriteIds: List<String> = emptyList()): PageLayout {
        val source = first.page(pageId)
        var appsSeen = false
        val blocks = source.blocks.map { block ->
            val fresh = block.copy(id = UUID.randomUUID().toString())
            if (favoriteIds.isNotEmpty() && fresh.type == AppsBlockDefinition.type && !appsSeen) {
                appsSeen = true
                AppsBlockDefinition.withFavoriteIds(fresh, favoriteIds)
            } else {
                fresh
            }
        }
        return source.copy(pageId = pageId, blocks = blocks)
    }
}

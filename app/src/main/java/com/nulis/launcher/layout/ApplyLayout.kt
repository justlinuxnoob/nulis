// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.layout

import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import java.util.UUID

/**
 * The pages a layout produces for this particular phone. Pure, so it can be tested and so
 * switching layout is one atomic write rather than a sequence the UI can catch halfway.
 *
 * A layout replaces the arrangement and nothing else: the Look, the colours, the typefaces,
 * the gestures, the notes, the journal, the tasks and the icon overrides are all somewhere else
 * and none of them is touched. The apps you picked are carried into the new layout's first
 * Apps block, because a home screen without your own apps on it is not a home screen.
 */
fun layoutPages(
    preset: LayoutPreset,
    current: Map<String, PageLayout?>,
    /**
     * The phone's own six - dialer, messages, camera, browser, gallery, clock - used when the
     * setup being replaced has no apps of its own. A layout applied on a fresh phone should hand
     * back a home screen you can use, not one asking to be filled in.
     */
    deviceDefaults: List<String> = emptyList(),
): Map<String, PageLayout> {
    return layoutPages(preset, current, deviceDefaults, keep = emptyList())
}

/**
 * The same, plus [keep]: page ids the layout does not mention and the user has asked to hold on
 * to, handed back exactly as they were.
 *
 * A layout says how many pages there are as well as what is on them, so applying a two-page
 * layout on a five-page phone throws three pages of somebody's work away. That is a fair thing
 * for a layout to do and a terrible thing to do without asking, so the picker asks and passes
 * the answer through here.
 */
fun layoutPages(
    preset: LayoutPreset,
    current: Map<String, PageLayout?>,
    deviceDefaults: List<String>,
    keep: List<String>,
): Map<String, PageLayout> {
    val favorites = current.values
        .filterNotNull()
        .flatMap { it.blocks }
        .firstOrNull { it.type == AppsBlockDefinition.type }
        ?.let { AppsBlockDefinition.favoriteIds(it) }
        ?.takeIf { it.isNotEmpty() }
        ?: deviceDefaults

    var appsSeen = false
    // The layout's own pages, not whatever was there: a layout says how many pages there are.
    val fromPreset = preset.order.associateWith { pageId ->
        val source = preset.page(pageId)
        val blocks = source.blocks.map { block ->
            val fresh = block.copy(id = UUID.randomUUID().toString())
            if (favorites.isNotEmpty() && fresh.type == AppsBlockDefinition.type && !appsSeen) {
                appsSeen = true
                AppsBlockDefinition.withFavoriteIds(fresh, favorites)
            } else {
                fresh
            }
        }
        source.copy(pageId = pageId, blocks = blocks).normalized()
    }
    val held = keep
        .filterNot { it in preset.order }
        .mapNotNull { pageId -> current[pageId]?.let { pageId to it.normalized() } }
    return fromPreset + held
}

/**
 * The pages this phone has that [preset] says nothing about, in swipe order. Empty when the
 * layout covers everything, which is when the picker has nothing to ask.
 */
fun extraPages(preset: LayoutPreset, order: List<String>): List<String> =
    order.filterNot { it in preset.order }

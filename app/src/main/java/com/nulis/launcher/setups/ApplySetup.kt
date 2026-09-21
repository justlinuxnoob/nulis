// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.setups

import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.roomFor
import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import java.util.UUID

/** Block types that show what the user has written. Data, not decoration. */
private val WritingTypes = listOf("notes", "journal", "tasks")

/**
 * The pages a theme produces for this particular phone. Pure, so the whole of it can be tested
 * and so applying a theme is one atomic write rather than a sequence the UI can catch halfway.
 *
 * Nothing here deletes anything: notes, journal entries, tasks, gestures and icon overrides live
 * in their own stores and a theme never touches them. [ApplyOptions] only decides which *blocks*
 * survive the swap.
 */
fun setupPages(
    setup: SavedSetup,
    current: Map<String, PageLayout?>,
    options: ApplyOptions,
): Map<String, PageLayout> {
    val favorites = current.values
        .filterNotNull()
        .flatMap { it.blocks }
        .firstOrNull { it.type == AppsBlockDefinition.type }
        ?.let { AppsBlockDefinition.favoriteIds(it) }
        .orEmpty()

    val carried = if (options.keepWritingBlocks) {
        val inTheme = setup.pages.values.flatMap { it.blocks }.map { it.type }.toSet()
        current.values
            .filterNotNull()
            .flatMap { it.blocks }
            .filter { it.type in WritingTypes && it.type !in inTheme }
            // One of each: two notes blocks from two different pages would just be noise.
            .distinctBy { it.type }
    } else {
        emptyList()
    }

    var appsSeen = false
    // The setup's own pages, in the setup's own order: a setup says how many pages there are.
    val pages = setup.order.associateWith { pageId ->
        val source = setup.page(pageId)
        val blocks = source.blocks.map { block ->
            val fresh = block.copy(id = UUID.randomUUID().toString())
            if (options.keepApps && favorites.isNotEmpty() && fresh.type == AppsBlockDefinition.type && !appsSeen) {
                appsSeen = true
                AppsBlockDefinition.withFavoriteIds(fresh, favorites)
            } else {
                fresh
            }
        }
        source.copy(pageId = pageId, blocks = blocks)
    }.toMutableMap()

    if (carried.isNotEmpty()) {
        // Writing the setup does not show goes to the first page, which is where writing lives.
        // Each carried block is placed in the first space that fits it, and one that will not
        // fit anywhere is dropped rather than laid on top of something.
        val firstId = setup.order.firstOrNull() ?: return pages
        var target = pages.getValue(firstId)
        carried.forEach { block ->
            val spot = target.roomFor(block.type) ?: return@forEach
            target = target.copy(
                blocks = target.blocks + block.copy(
                    id = UUID.randomUUID().toString(),
                    rect = spot,
                    pairNext = false,
                ),
            )
        }
        pages[firstId] = target
    }
    return pages
}

/** Turns the phone's current state into a theme the user can keep, apply again and share. */
fun captureSetup(
    id: String,
    name: String,
    lookId: String,
    colorTheme: com.nulis.launcher.ui.theme.ColorTheme,
    customBackground: Int?,
    customInk: Int?,
    displayFont: String?,
    bodyFont: String?,
    textScale: Float,
    uppercaseLabels: Boolean,
    pages: Map<String, PageLayout?>,
    /** The pages in swipe order, and which one is home. */
    order: List<String>,
    homeId: String,
): SavedSetup = SavedSetup(
    id = id,
    name = name,
    tagline = "Saved from your phone",
    lookId = lookId,
    colorTheme = colorTheme,
    customBackground = customBackground,
    customInk = customInk,
    displayFont = displayFont,
    bodyFont = bodyFont,
    textScale = textScale,
    uppercaseLabels = uppercaseLabels,
    // Favourites are this phone's apps, not part of the look; a saved theme starts them empty.
    pages = order.associateWith { pageId ->
        val layout = pages[pageId] ?: PageLayout(pageId)
        layout.copy(blocks = layout.blocks.map { stripPersonal(it) })
    },
    pageOrder = order,
    homeId = homeId,
    builtIn = false,
)

private fun stripPersonal(block: Block): Block =
    if (block.type == AppsBlockDefinition.type) AppsBlockDefinition.withFavoriteIds(block, emptyList()) else block

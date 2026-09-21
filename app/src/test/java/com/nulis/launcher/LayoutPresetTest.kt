// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.blocks.PageGrid
import com.nulis.launcher.blocks.PagesConfig
import com.nulis.launcher.blocks.glance.GlanceSettings
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.BlockWidth
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import com.nulis.launcher.layout.DefaultLayouts
import com.nulis.launcher.layout.LayoutPresets
import com.nulis.launcher.layout.layoutPages
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The kind of typo no amount of looking would catch: a preset naming a block type or a style id
 * that does not exist would build fine and draw an empty page on somebody's phone.
 */
class LayoutPresetTest {

    @Test
    fun `every block a preset names exists, with the style it asks for`() {
        LayoutPresets.all.forEach { preset ->
            preset.pages.forEach { (pageId, page) ->
                page.blocks.forEach { block ->
                    val definition = BlockRegistry.definition(block.type)
                    assertNotNull("${preset.id}/$pageId: no block type ${block.type}", definition)
                    assertTrue(
                        "${preset.id}/$pageId: ${block.type} has no style ${block.style}",
                        definition!!.styles.any { it.id == block.style },
                    )
                }
            }
        }
    }

    @Test
    fun `every preset has between one and five pages, with home on one of them`() {
        LayoutPresets.all.forEach { preset ->
            assertTrue("no pages in " + preset.id, preset.pages.isNotEmpty())
            assertTrue("too many pages in " + preset.id, preset.pages.size <= PagesConfig.MAX)
            assertTrue("home is not a page of " + preset.id, preset.homeId in preset.pages.keys)
            assertEquals(preset.config.ids, preset.order)
            assertTrue("${preset.id} has a nameless name", preset.name.isNotBlank())
            assertTrue("${preset.id} has no tagline", preset.tagline.isNotBlank())
        }
    }

    @Test
    fun `preset ids are unique`() {
        val ids = LayoutPresets.all.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun `at least one preset puts two blocks side by side`() {
        val packed = LayoutPresets.all.any { preset ->
            preset.pages.values.any { page ->
                page.blocks.groupBy { it.area.row }.values.any { it.size > 1 }
            }
        }
        assertTrue("no preset shares a row", packed)
        val halves = LayoutPresets.all.any { preset ->
            preset.pages.values.any { page -> page.blocks.any { it.area.cols == PageGrid.COLUMNS / 2 } }
        }
        assertTrue("no preset uses half-width blocks", halves)
    }

    /** Every shipped page is exactly one screen, with nothing on top of anything else. */
    @Test
    fun `every preset page fits the grid and does not overlap itself`() {
        LayoutPresets.all.forEach { preset ->
            preset.pages.forEach { (pageId, page) ->
                page.blocks.forEach { block ->
                    assertTrue("${preset.id}/$pageId/${block.type} is off the grid", block.area.fitsGrid)
                }
                page.blocks.forEachIndexed { index, one ->
                    page.blocks.drop(index + 1).forEach { other ->
                        assertTrue(
                            "${preset.id}/$pageId: ${one.type} overlaps ${other.type}",
                            !one.area.overlaps(other.area),
                        )
                    }
                }
            }
        }
    }

    @Test
    fun `a fresh install is the Minimal preset`() {
        val home = DefaultLayouts.forPage(PageIds.HOME)
        assertEquals(LayoutPresets.Minimal.page(PageIds.HOME).blocks.map { it.type }, home.blocks.map { it.type })
    }

    @Test
    fun `applying a layout carries the apps across and gives every block a new id`() {
        val current = mapOf(
            PageIds.HOME to AppsBlockDefinition
                .withFavoriteIds(BlockRegistry.newBlock(AppsBlockDefinition.type), listOf("a/1", "b/2"))
                .let { PageLayout(PageIds.HOME, listOf(it)) },
            PageIds.LEFT to null,
            PageIds.RIGHT to null,
        )
        val pages = layoutPages(LayoutPresets.Stats, current)
        val apps = pages.values.flatMap { it.blocks }.first { it.type == AppsBlockDefinition.type }
        assertEquals(listOf("a/1", "b/2"), AppsBlockDefinition.favoriteIds(apps))

        val ids = pages.values.flatMap { it.blocks }.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        assertEquals(LayoutPresets.Stats.order.toSet(), pages.keys)
        pages.forEach { (pageId, page) -> assertEquals(pageId, page.pageId) }
    }

    @Test
    fun `applying a layout with no apps yet leaves the preset's own apps block alone`() {
        val pages = layoutPages(LayoutPresets.Minimal, PageIds.all.associateWith { null })
        val apps = pages.values.flatMap { it.blocks }.first { it.type == AppsBlockDefinition.type }
        assertTrue(AppsBlockDefinition.favoriteIds(apps).isEmpty())
    }
}

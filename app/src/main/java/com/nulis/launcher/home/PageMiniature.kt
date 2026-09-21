// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.LocalBlockAlign
import com.nulis.launcher.blocks.PageGrid
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.ui.components.LocalPreviewStill
import com.nulis.launcher.ui.components.ScaledPreview
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme

/** The size the miniature pretends to be before it is scaled down: a phone, roughly. */
private val PageWidth = 360.dp
private val PageHeight = 720.dp

/**
 * The page itself, drawn small: the real grid, real blocks, real data, every block in the
 * rectangle it actually occupies. Used by the layout picker and the page options sheet, so every
 * choice is seen rather than described. Animated blocks hold still inside one
 * ([LocalPreviewStill]).
 */
@Composable
fun PageMiniature(
    layout: PageLayout,
    context: BlockContext,
    modifier: Modifier = Modifier,
    /**
     * True for a card whose blocks have never been on a page and have nothing in them yet. Each
     * block then falls back to its own `previewSettings`, so an Apps block shows real apps
     * instead of "long-press to pick your apps".
     */
    sample: Boolean = false,
    /**
     * Only draw the blocks that start in the first this many rows. A card that is a strip rather
     * than a page - the palette row in settings, where eight of them scroll past at once - asks
     * for the top of the page and pays for nothing below it.
     */
    maxRows: Int = PageGrid.ROWS,
) {
    val colors = NulisTheme.colors
    Box(
        modifier
            .clip(NulisShapes.tile)
            .background(colors.background)
            .border(1.dp, colors.hairline, NulisShapes.tile),
    ) {
        CompositionLocalProvider(LocalPreviewStill provides true) {
            ScaledPreview(Modifier.fillMaxWidth(), contentWidth = PageWidth) {
                val blocks = layout.blocks
                    .filter { it.area.row < maxRows }
                    .filterNot { BlockRegistry.definition(it.type)?.isHidden(it, context) == true }
                    .map { block ->
                        if (!sample) {
                            block
                        } else {
                            val definition = BlockRegistry.definition(block.type)
                            val filled = block.settings.filterValues { it.isNotBlank() }
                            block.copy(settings = (definition?.previewSettings(context) ?: emptyMap()) + filled)
                        }
                    }
                PageBoard(
                    blocks = blocks,
                    gutter = layout.density.gutter,
                    modifier = Modifier
                        .width(PageWidth)
                        .height(PageHeight)
                        .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 32.dp),
                ) { block, area ->
                    val align = layout.alignOf(block)
                    CompositionLocalProvider(LocalBlockAlign provides align) {
                        BlockFrame(area = area, align = align, modifier = Modifier.fillMaxSize()) {
                            BlockRegistry.definition(block.type)
                                ?.style(block)
                                ?.Render(block, context, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

/**
 * The first few blocks of a page, stacked, at a fixed height.
 *
 * Not a picture of the page: a picture of what a page is *made of*, for a card that is about
 * something else. The palette row in settings is eight of these scrolling past at once, and a
 * palette card is about the colours - where the blocks sit on the page is somebody else's
 * question, and not worth eight live grids to answer. A strip is a Column and nothing else.
 */
@Composable
fun BlockStrip(
    layout: PageLayout,
    context: BlockContext,
    modifier: Modifier = Modifier,
    /** How many blocks, in reading order. Two is enough to show a typeface and a colour. */
    blocks: Int = 2,
) {
    val colors = NulisTheme.colors
    Box(
        modifier
            .clip(NulisShapes.tile)
            .background(colors.background)
            .border(1.dp, colors.hairline, NulisShapes.tile),
    ) {
        CompositionLocalProvider(LocalPreviewStill provides true) {
            ScaledPreview(Modifier.fillMaxWidth(), contentWidth = PageWidth) {
                Column(
                    Modifier
                        .width(PageWidth)
                        .padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    layout.reading
                        .filterNot { BlockRegistry.definition(it.type)?.isHidden(it, context) == true }
                        .take(blocks)
                        .forEach { block ->
                            val definition = BlockRegistry.definition(block.type) ?: return@forEach
                            val filled = block.settings.filterValues { it.isNotBlank() }
                            val shown = block.copy(settings = definition.previewSettings(context) + filled)
                            CompositionLocalProvider(LocalBlockAlign provides layout.alignOf(block)) {
                                definition.style(shown).Render(shown, context, Modifier.fillMaxWidth())
                            }
                        }
                }
            }
        }
    }
}

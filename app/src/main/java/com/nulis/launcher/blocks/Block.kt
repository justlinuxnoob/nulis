// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks

import kotlinx.serialization.Serializable

/**
 * How much room a block takes.
 *
 * Kept only because saved layouts and backups carry it; the grid says how big a block is now.
 * Styles that still read it treat it as a hint about how much detail to draw.
 */
@Serializable
enum class BlockSize { SMALL, WIDE }

/**
 * One block instance on a page. [type] and [style] are ids from [BlockRegistry];
 * [settings] is block-specific and only understood by that block's [BlockDefinition].
 *
 * [rect] is where the block sits on the page's grid. It is nullable only so that a layout saved
 * before the grid existed still decodes; [PageLayout.normalized] gives every block a rectangle
 * once, on the way in, and nothing downstream ever sees a null one.
 */
@Serializable
data class Block(
    val id: String,
    val type: String,
    val style: String,
    val size: BlockSize = BlockSize.WIDE,
    val settings: Map<String, String> = emptyMap(),
    /** Which edge this block hangs from, or null to follow the page's default. */
    val align: BlockAlign? = null,
    /** Where this block sits on the grid, or null on a layout written before the grid. */
    val rect: GridRect? = null,
    /**
     * How much of a row this block took before pages were grids, kept only so a layout or a
     * backup written back then still reads. [PageLayout.normalized] turns it into a [GridRect]
     * once and nothing downstream of that ever looks at it again.
     */
    val width: BlockWidth = BlockWidth.FULL,
    /** The way a shared row was asked for before [width] existed. Folded in the same place. */
    val pairNext: Boolean = false,
) {
    /** The rectangle this block is drawn in. Safe after [PageLayout.normalized]. */
    val area: GridRect get() = rect ?: PageGrid.whole
}

/** One page: a grid of blocks that is exactly one screen tall, and never more. */
@Serializable
data class PageLayout(
    val pageId: String,
    val blocks: List<Block> = emptyList(),
    /**
     * Alignment for every block that does not override it. Centre is the default a fresh install
     * and every shipped layout start from; a saved page always carries its own value, because
     * layouts are written with `encodeDefaults = true`.
     */
    val align: BlockAlign = BlockAlign.CENTER,
    /**
     * Where the stack of blocks used to sit vertically. A grid says where every block is, so
     * this only survives to place the blocks of a page saved before the grid.
     */
    val anchor: PageAnchor = PageAnchor.TOP,
    /** How much air is left between cells. */
    val density: PageDensity = PageDensity.COMFORTABLE,
) {
    /** The alignment [block] is actually drawn with. */
    fun alignOf(block: Block): BlockAlign = block.align ?: align

    /** Which cells are taken, optionally pretending one block is not there. */
    fun occupancy(exceptId: String? = null): GridOccupancy =
        GridOccupancy(blocks.filter { it.id != exceptId }.map { it.area })

    /** The blocks [rect] would land on top of. */
    fun overlapping(rect: GridRect, exceptId: String? = null): List<Block> =
        blocks.filter { it.id != exceptId && it.area.overlaps(rect) }

    /** Where a block of [span] would go, or null when the page has no room for one. */
    fun firstFit(span: GridSpan): GridRect? = occupancy().firstFit(span)

    fun block(id: String): Block? = blocks.firstOrNull { it.id == id }

    fun replace(block: Block): PageLayout = copy(blocks = blocks.map { if (it.id == block.id) block else it })

    /** The same page with [id] moved or resized. Does not check for overlap; callers do. */
    fun withRect(id: String, rect: GridRect): PageLayout =
        copy(blocks = blocks.map { if (it.id == id) it.copy(rect = rect) else it })

    /**
     * Blocks in reading order - top to bottom, then left to right. The order a screen reader
     * walks the page in, and the order the block picker counts in.
     */
    val reading: List<Block>
        get() = blocks.sortedWith(compareBy({ it.area.row }, { it.area.col }))

    /**
     * Gives every block a rectangle, folding in whatever the page used to say instead: the
     * `pairNext` flag first, then widths, the vertical anchor and the spacing.
     *
     * Runs on everything that comes in from outside - a saved layout, a backup file, a preset -
     * and is idempotent, so running it twice costs nothing and changes nothing.
     */
    fun normalized(): PageLayout = foldLegacyWidths().onGrid().followingThePage()

    /**
     * A block whose own alignment is the page's alignment goes back to having none.
     *
     * Alignment on a block is meant to be an override - "this one hangs from the other edge" -
     * and the page is meant to be the default. Cycling a block's alignment round to where the
     * page already was left it with an explicit value that happened to look identical, and from
     * then on that block quietly ignored the page: change the page to the left and one block
     * stayed in the middle for no reason anybody could see. Folding it back to null changes
     * nothing about how the page is drawn today and everything about what it does next.
     */
    private fun followingThePage(): PageLayout {
        if (blocks.none { it.align == align }) return this
        return copy(blocks = blocks.map { if (it.align == align) it.copy(align = null) else it })
    }

    private fun foldLegacyWidths(): PageLayout {
        if (blocks.none { it.pairNext }) return this
        val out = blocks.toMutableList()
        var index = 0
        while (index < out.size) {
            val block = out[index]
            val next = out.getOrNull(index + 1)
            val pairs = block.pairNext && next != null &&
                block.size == BlockSize.SMALL && next.size == BlockSize.SMALL &&
                block.width == BlockWidth.FULL && next.width == BlockWidth.FULL
            if (pairs) {
                out[index] = block.copy(width = BlockWidth.HALF, pairNext = false)
                out[index + 1] = next!!.copy(width = BlockWidth.HALF, pairNext = false)
                index += 2
            } else {
                if (block.pairNext) out[index] = block.copy(pairNext = false)
                index += 1
            }
        }
        return copy(blocks = out)
    }

    /**
     * Blocks grouped into the rows they were drawn in before the grid, used only by the
     * migration: a run of neighbours asking for the same width filled a row up to that width's
     * capacity, and a block of a different width began a row of its own.
     */
    internal fun legacyRows(source: List<Block> = blocks): List<List<Block>> {
        val rows = ArrayList<List<Block>>(source.size)
        var index = 0
        while (index < source.size) {
            val width = source[index].width
            val row = ArrayList<Block>(width.perRow)
            while (row.size < width.perRow && index < source.size && source[index].width == width) {
                row += source[index]
                index += 1
            }
            rows += row
        }
        return rows
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks

/**
 * Turning a stack of blocks into a grid of rectangles.
 *
 * A page saved before the grid said three things about where its blocks went: each block's
 * width (full, half or third of a row), the order they were stacked in, and the page's vertical
 * anchor. That is enough to rebuild the same picture on the grid, and rebuilding it is the whole
 * job here - somebody's home screen must look the same the morning after an update as it did
 * the night before.
 *
 * Heights are the one thing the old model never said. Each block type declares how many rows it
 * wants ([BlockDefinition.defaultSpan]) and the fewest it can live in
 * ([BlockDefinition.minSpan]), and this shrinks rows towards the minimum, tallest first, until
 * the page fits the twelve it has.
 */
fun PageLayout.onGrid(): PageLayout {
    if (blocks.isEmpty()) return this
    // Already placed: the common path, and it must cost nothing.
    if (blocks.all { it.rect != null && it.rect.fitsGrid }) return this
    // A half-migrated page (one new block added to an old layout) is rare enough to redo whole.
    val placed = placeLegacy(blocks, align, anchor)
    return copy(blocks = placed)
}

/** How many columns a legacy width took on a six-column grid. */
private fun BlockWidth.columns(): Int = when (this) {
    BlockWidth.FULL -> PageGrid.COLUMNS
    BlockWidth.HALF -> PageGrid.COLUMNS / 2
    BlockWidth.THIRD -> PageGrid.COLUMNS / 3
}

private fun definitionSpans(block: Block): Pair<GridSpan, GridSpan> {
    val definition = BlockRegistry.definition(block.type)
    val min = definition?.minSpan ?: GridSpan(2, 1)
    val preferred = definition?.defaultSpan ?: GridSpan(PageGrid.COLUMNS, 2)
    return min to preferred
}

private fun placeLegacy(source: List<Block>, align: BlockAlign, anchor: PageAnchor): List<Block> {
    val rows = PageLayout("", source).legacyRows(source)

    // What each stacked row wants, and the least it can take. A block squeezed into half or a
    // third of a row was drawn shorter than the same block full width, so it asks for less here.
    val wanted = IntArray(rows.size)
    val least = IntArray(rows.size)
    rows.forEachIndexed { index, row ->
        var want = 1
        var min = 1
        row.forEach { block ->
            val (minSpan, preferred) = definitionSpans(block)
            val narrow = block.width != BlockWidth.FULL
            want = maxOf(want, if (narrow) minOf(preferred.rows, 2) else preferred.rows)
            min = maxOf(min, minSpan.rows)
        }
        wanted[index] = want
        least[index] = minOf(min, PageGrid.ROWS)
    }

    // A page that starts at the top used to begin with a little air above the first block.
    val wantsInset = anchor == PageAnchor.TOP
    var total = wanted.sum() + if (wantsInset) 1 else 0
    // Too tall: give a row back at a time, always from whichever row has the most to spare, so
    // one over-eager block shrinks before a page of modest ones all do.
    while (total > PageGrid.ROWS) {
        var worst = -1
        var slack = 0
        wanted.indices.forEach { index ->
            val available = wanted[index] - least[index]
            if (available > slack) {
                slack = available
                worst = index
            }
        }
        if (worst < 0) break
        wanted[worst] -= 1
        total -= 1
    }
    // Still too tall even at every minimum: drop the rows off the bottom that cannot fit, which
    // is what the old page did too - it simply drew them past the edge of the screen.
    var kept = rows.size
    while (kept > 0 && wanted.take(kept).sum() + (if (wantsInset) 1 else 0) > PageGrid.ROWS) kept -= 1

    val height = wanted.take(kept).sum()
    var row = when {
        kept == 0 -> 0
        anchor == PageAnchor.CENTER -> ((PageGrid.ROWS - height) / 2).coerceAtLeast(0)
        anchor == PageAnchor.BOTTOM -> (PageGrid.ROWS - height).coerceAtLeast(0)
        else -> if (wantsInset && height < PageGrid.ROWS) 1 else 0
    }

    val out = ArrayList<Block>(source.size)
    rows.take(kept).forEachIndexed { index, stackRow ->
        val rowHeight = wanted[index]
        val widths = stackRow.map { it.width.columns() }
        val used = widths.sum()
        // The leftover columns hang from the page's own alignment edge, exactly as the old
        // partly-filled row did: a lone half really sat in the middle of a centred page.
        val spare = (PageGrid.COLUMNS - used).coerceAtLeast(0)
        var col = when (align) {
            BlockAlign.LEFT -> 0
            BlockAlign.RIGHT -> spare
            BlockAlign.CENTER -> spare / 2
        }
        stackRow.forEachIndexed { position, block ->
            val cols = widths[position].coerceAtMost(PageGrid.COLUMNS - col).coerceAtLeast(1)
            out += block.copy(rect = GridRect(col, row, cols, rowHeight).clamped())
            col += cols
        }
        row += rowHeight
    }
    // Anything that did not fit keeps its settings and is parked wherever there is still room,
    // rather than vanishing from somebody's page without a word.
    if (kept < rows.size) {
        rows.drop(kept).flatten().forEach { block ->
            val (minSpan, _) = definitionSpans(block)
            // Rebuilt each time: a block parked on this pass is in the way of the next one.
            val spot = GridOccupancy(out.map { it.area }).firstFit(minSpan)
            if (spot != null) out += block.copy(rect = spot)
        }
    }
    return out
}

/**
 * Where a new block of [type] would go: its preferred rectangle if the page has room for one,
 * then the same rectangle shrunk a row and a column at a time down to the type's minimum, and
 * null when even that will not fit. Trying the preferred size first is what makes a clock
 * dropped onto an empty page arrive the size a clock wants to be.
 */
fun PageLayout.roomFor(type: String): GridRect? {
    val definition = BlockRegistry.definition(type) ?: return null
    val occupancy = occupancy()
    val min = definition.minSpan.coerced()
    val want = definition.defaultSpan.coerced()
    var cols = maxOf(want.cols, min.cols)
    var rows = maxOf(want.rows, min.rows)
    while (true) {
        occupancy.firstFit(GridSpan(cols, rows))?.let { return it }
        // Give back whichever dimension has the most to spare, so a wide short block loses width
        // before it loses the line of text it is made of.
        val spareCols = cols - min.cols
        val spareRows = rows - min.rows
        when {
            spareCols >= spareRows && spareCols > 0 -> cols -= 1
            spareRows > 0 -> rows -= 1
            else -> return null
        }
    }
}

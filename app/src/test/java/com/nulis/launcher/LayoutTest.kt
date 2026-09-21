// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockWidth
import com.nulis.launcher.blocks.GridRect
import com.nulis.launcher.blocks.PageAnchor
import com.nulis.launcher.blocks.PageDensity
import com.nulis.launcher.blocks.PageGrid
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PageLayout
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutTest {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun block(id: String, size: BlockSize = BlockSize.SMALL, pair: Boolean = false) =
        Block(id = id, type = "clock", style = "display", size = size, pairNext = pair)

    private fun wide(id: String, width: BlockWidth) =
        Block(id = id, type = "clock", style = "display", size = BlockSize.SMALL, width = width)

    private fun page(vararg blocks: Block) = PageLayout(PageIds.HOME, blocks.toList())

    // ------------------------------------------------------------------ the grid itself

    @Test
    fun `every block comes off the migration with a rectangle inside the grid`() {
        val layout = page(block("a"), block("b"), block("c")).normalized()
        assertTrue(layout.blocks.all { it.rect != null })
        assertTrue(layout.blocks.all { it.area.fitsGrid })
    }

    @Test
    fun `no two blocks land on the same cell`() {
        val layout = page(
            wide("a", BlockWidth.HALF),
            wide("b", BlockWidth.HALF),
            block("c"),
            wide("d", BlockWidth.THIRD),
            wide("e", BlockWidth.THIRD),
            wide("f", BlockWidth.THIRD),
        ).normalized()
        layout.blocks.forEachIndexed { i, one ->
            layout.blocks.drop(i + 1).forEach { other ->
                assertFalse("${one.id} overlaps ${other.id}", one.area.overlaps(other.area))
            }
        }
    }

    @Test
    fun `a page never runs off the bottom of the screen`() {
        // Nine blocks each asking for three rows is far more than twelve rows of grid.
        val many = (0 until 9).map { Block("b$it", "clock", "display") }
        val layout = PageLayout(PageIds.HOME, many).normalized()
        assertTrue(layout.blocks.all { it.area.bottom <= PageGrid.ROWS })
        assertTrue(layout.blocks.all { it.area.right <= PageGrid.COLUMNS })
    }

    @Test
    fun `two halves share a row, side by side`() {
        val layout = page(wide("a", BlockWidth.HALF), wide("b", BlockWidth.HALF), block("c")).normalized()
        val a = layout.block("a")!!.area
        val b = layout.block("b")!!.area
        assertEquals(a.row, b.row)
        assertEquals(a.right, b.col)
        assertEquals(PageGrid.COLUMNS / 2, a.cols)
        assertEquals(PageGrid.COLUMNS, layout.block("c")!!.area.cols)
        assertTrue(layout.block("c")!!.area.row >= a.bottom)
    }

    @Test
    fun `three thirds share a row and a fourth starts the next`() {
        val layout = PageLayout(PageIds.HOME, List(4) { wide("t$it", BlockWidth.THIRD) }).normalized()
        val rows = layout.blocks.groupBy { it.area.row }
        assertEquals(2, rows.size)
        assertEquals(3, rows.values.first { it.size > 1 }.size)
    }

    @Test
    fun `a lone half keeps half the row, and a centred page puts it in the middle`() {
        val layout = PageLayout(PageIds.HOME, listOf(wide("a", BlockWidth.HALF)), align = BlockAlign.CENTER)
            .normalized()
        val a = layout.block("a")!!.area
        assertEquals(PageGrid.COLUMNS / 2, a.cols)
        assertEquals((PageGrid.COLUMNS - a.cols) / 2, a.col)
    }

    @Test
    fun `a left-aligned page starts its rows at the left margin`() {
        val layout = PageLayout(PageIds.HOME, listOf(wide("a", BlockWidth.HALF)), align = BlockAlign.LEFT)
            .normalized()
        assertEquals(0, layout.block("a")!!.area.col)
    }

    @Test
    fun `a right-aligned page hangs its rows off the right margin`() {
        val layout = PageLayout(PageIds.HOME, listOf(wide("a", BlockWidth.HALF)), align = BlockAlign.RIGHT)
            .normalized()
        assertEquals(PageGrid.COLUMNS, layout.block("a")!!.area.right)
    }

    @Test
    fun `a bottom-anchored page ends at the bottom of the grid`() {
        val layout = PageLayout(PageIds.HOME, listOf(block("a"), block("b")), anchor = PageAnchor.BOTTOM)
            .normalized()
        assertEquals(PageGrid.ROWS, layout.blocks.maxOf { it.area.bottom })
    }

    @Test
    fun `a centred page leaves the same room above and below`() {
        val layout = PageLayout(PageIds.HOME, listOf(block("a")), anchor = PageAnchor.CENTER).normalized()
        val a = layout.block("a")!!.area
        // An odd number of spare rows cannot be split evenly; the extra one goes underneath.
        assertTrue(PageGrid.ROWS - a.bottom - a.row in 0..1)
    }

    @Test
    fun `the old pairing flag becomes a pair of halves`() {
        val layout = page(block("a", pair = true), block("b"), block("c")).normalized()
        val a = layout.block("a")!!.area
        val b = layout.block("b")!!.area
        assertEquals(a.row, b.row)
        assertEquals(PageGrid.COLUMNS / 2, a.cols)
        assertTrue(layout.blocks.none { it.pairNext })
    }

    @Test
    fun `old pairing needed both blocks small, and still does`() {
        val wideNext = page(block("a", pair = true), block("b", size = BlockSize.WIDE)).normalized()
        assertNotEquals(wideNext.block("a")!!.area.row, wideNext.block("b")!!.area.row)

        val wideSelf = page(block("a", size = BlockSize.WIDE, pair = true), block("b")).normalized()
        assertNotEquals(wideSelf.block("a")!!.area.row, wideSelf.block("b")!!.area.row)
    }

    @Test
    fun `three old paired blocks make a pair and a single, never a triple`() {
        val layout = page(block("a", pair = true), block("b", pair = true), block("c")).normalized()
        assertEquals(layout.block("a")!!.area.row, layout.block("b")!!.area.row)
        assertNotEquals(layout.block("a")!!.area.row, layout.block("c")!!.area.row)
    }

    @Test
    fun `normalising twice changes nothing`() {
        val once = page(block("a", pair = true), block("b")).normalized()
        assertEquals(once, once.normalized())
    }

    @Test
    fun `a page already on the grid is left exactly as it is`() {
        val placed = PageLayout(
            PageIds.HOME,
            listOf(block("a").copy(rect = GridRect(1, 7, 4, 3))),
        )
        assertEquals(GridRect(1, 7, 4, 3), placed.normalized().block("a")!!.rect)
    }

    @Test
    fun `reading order is top to bottom, then left to right`() {
        val layout = PageLayout(
            PageIds.HOME,
            listOf(
                block("bottom").copy(rect = GridRect(0, 6, 6, 2)),
                block("topright").copy(rect = GridRect(3, 0, 3, 2)),
                block("topleft").copy(rect = GridRect(0, 0, 3, 2)),
            ),
        )
        assertEquals(listOf("topleft", "topright", "bottom"), layout.reading.map { it.id })
    }

    // ------------------------------------------------------------------ placing new blocks

    @Test
    fun `the first fit is the top left corner of an empty page`() {
        val fit = PageLayout(PageIds.HOME).firstFit(com.nulis.launcher.blocks.GridSpan(3, 2))
        assertEquals(GridRect(0, 0, 3, 2), fit)
    }

    @Test
    fun `the first fit skips what is already there`() {
        val layout = PageLayout(PageIds.HOME, listOf(block("a").copy(rect = GridRect(0, 0, 4, 2))))
        val fit = layout.firstFit(com.nulis.launcher.blocks.GridSpan(2, 2))
        assertEquals(GridRect(4, 0, 2, 2), fit)
    }

    @Test
    fun `a full page has nowhere left to put anything`() {
        val layout = PageLayout(PageIds.HOME, listOf(block("a").copy(rect = PageGrid.whole)))
        assertNull(layout.firstFit(com.nulis.launcher.blocks.GridSpan(1, 1)))
        assertNotNull(PageLayout(PageIds.HOME).firstFit(com.nulis.launcher.blocks.GridSpan(1, 1)))
    }

    @Test
    fun `a rectangle knows what it lands on`() {
        val layout = PageLayout(
            PageIds.HOME,
            listOf(block("a").copy(rect = GridRect(0, 0, 3, 3)), block("b").copy(rect = GridRect(3, 0, 3, 3))),
        )
        assertEquals(listOf("a"), layout.overlapping(GridRect(2, 2, 1, 1)).map { it.id })
        assertEquals(listOf("a", "b"), layout.overlapping(GridRect(0, 0, 6, 1)).map { it.id })
        assertEquals(emptyList<String>(), layout.overlapping(GridRect(0, 4, 6, 1)).map { it.id })
    }

    @Test
    fun `a rectangle clamped to the grid keeps its span where it can`() {
        assertEquals(GridRect(3, 10, 3, 2), GridRect(5, 11, 3, 2).clamped())
        assertEquals(GridRect(0, 0, PageGrid.COLUMNS, PageGrid.ROWS), GridRect(0, 0, 99, 99).clamped())
    }

    // ------------------------------------------------------------------ alignment

    @Test
    fun `a block with no alignment follows the page`() {
        val layout = PageLayout(PageIds.HOME, listOf(block("a")), align = BlockAlign.CENTER)
        assertEquals(BlockAlign.CENTER, layout.alignOf(layout.blocks[0]))
    }

    @Test
    fun `a block with its own alignment keeps it`() {
        val own = block("a").copy(align = BlockAlign.RIGHT)
        val layout = PageLayout(PageIds.HOME, listOf(own), align = BlockAlign.CENTER)
        assertEquals(BlockAlign.RIGHT, layout.alignOf(own))
    }

    // ------------------------------------------------------------------ decoding old files

    /**
     * The migration that matters: every field added since has a default, a page saved before
     * alignment existed keeps the left edge it was drawn at rather than taking the new centre,
     * and a page saved before the grid comes out of it placed.
     */
    @Test
    fun `a layout saved before tonight still decodes`() {
        val old = """{"pageId":"home","blocks":[{"id":"x","type":"clock","style":"display","size":"WIDE","settings":{}}]}"""
        val decoded = com.nulis.launcher.layout.decodePageLayout(json, old)
        assertEquals(1, decoded.blocks.size)
        assertEquals(BlockAlign.LEFT, decoded.align)
        assertEquals(PageDensity.COMFORTABLE, decoded.density)
        assertEquals(null, decoded.blocks[0].align)
        assertEquals(false, decoded.blocks[0].pairNext)
        assertNotNull(decoded.blocks[0].rect)
        assertEquals(0, decoded.blocks[0].area.col)
    }

    /** A page that says where it hangs is taken at its word, in both directions. */
    @Test
    fun `a saved alignment survives the new centre default`() {
        val saved = """{"pageId":"home","blocks":[],"align":"LEFT","anchor":"TOP","density":"COMFORTABLE"}"""
        assertEquals(BlockAlign.LEFT, com.nulis.launcher.layout.decodePageLayout(json, saved).align)
        val right = """{"pageId":"home","blocks":[],"align":"RIGHT"}"""
        assertEquals(BlockAlign.RIGHT, com.nulis.launcher.layout.decodePageLayout(json, right).align)
    }

    /** A fresh page, and every shipped layout's page, starts centred. */
    @Test
    fun `a new page is centred`() {
        assertEquals(BlockAlign.CENTER, PageLayout(PageIds.HOME).align)
        assertEquals(BlockAlign.CENTER, com.nulis.launcher.layout.DefaultLayouts.forPage(PageIds.HOME).align)
    }

    @Test
    fun `a layout round-trips through json`() {
        val layout = PageLayout(
            pageId = PageIds.LEFT,
            blocks = listOf(
                block("a").copy(align = BlockAlign.RIGHT, rect = GridRect(0, 0, 3, 2)),
                block("b").copy(rect = GridRect(3, 0, 3, 2)),
            ),
            align = BlockAlign.CENTER,
            density = PageDensity.AIRY,
        )
        val decoded = json.decodeFromString<PageLayout>(json.encodeToString(layout))
        assertEquals(layout, decoded)
    }

    @Test
    fun `an unknown field in a saved layout is ignored rather than fatal`() {
        val future = """{"pageId":"home","blocks":[],"align":"LEFT","somethingNew":42}"""
        val decoded = json.decodeFromString<PageLayout>(future)
        assertNotEquals(null, decoded)
        assertEquals(0, decoded.blocks.size)
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks

import kotlinx.serialization.Serializable

/**
 * The grid a page is.
 *
 * A page is exactly one screen, and that screen is divided into [COLUMNS] by [ROWS] cells. A
 * block is a rectangle on it - where it starts and how many cells it spans - so a clock can sit
 * at the bottom with nothing above it, two readouts can share the top corner, and the empty
 * cells in between are empty on purpose rather than by accident.
 *
 * Six columns is the coarsest resolution that still gives both halves (3 + 3) and thirds
 * (2 + 2 + 2), which is what the old width control could say and what a tidy page actually
 * wants. Twelve rows makes a cell very nearly square on a phone - about 60 x 70 dp inside the
 * screen margins - so a one-by-one block is a small square rather than a sliver, and a block
 * that needs a line of text and no more does not have to take a sixth of the screen.
 */
object PageGrid {
    const val COLUMNS = 6
    const val ROWS = 12

    /** The whole page, for a block that wants all of it. */
    val whole = GridRect(0, 0, COLUMNS, ROWS)

    val cells: Int get() = COLUMNS * ROWS
}

/** How many cells a block wants, without saying where. */
@Serializable
data class GridSpan(val cols: Int, val rows: Int) {
    val cells: Int get() = cols * rows

    fun coerced(): GridSpan = GridSpan(cols.coerceIn(1, PageGrid.COLUMNS), rows.coerceIn(1, PageGrid.ROWS))
}

/**
 * Where a block sits and how much of the grid it takes. Column and row are zero-based from the
 * top left; [cols] and [rows] are spans, never coordinates.
 */
@Serializable
data class GridRect(val col: Int, val row: Int, val cols: Int, val rows: Int) {

    val right: Int get() = col + cols
    val bottom: Int get() = row + rows
    val span: GridSpan get() = GridSpan(cols, rows)
    val cells: Int get() = cols * rows

    fun overlaps(other: GridRect): Boolean =
        col < other.right && other.col < right && row < other.bottom && other.row < bottom

    fun holds(c: Int, r: Int): Boolean = c in col until right && r in row until bottom

    fun movedTo(c: Int, r: Int): GridRect = copy(col = c, row = r)

    fun resized(span: GridSpan): GridRect = copy(cols = span.cols, rows = span.rows)

    /** Inside the grid, keeping the span if it fits and trimming it if it cannot. */
    fun clamped(): GridRect {
        val w = cols.coerceIn(1, PageGrid.COLUMNS)
        val h = rows.coerceIn(1, PageGrid.ROWS)
        return GridRect(
            col = col.coerceIn(0, PageGrid.COLUMNS - w),
            row = row.coerceIn(0, PageGrid.ROWS - h),
            cols = w,
            rows = h,
        )
    }

    val fitsGrid: Boolean
        get() = col >= 0 && row >= 0 && cols >= 1 && rows >= 1 &&
            right <= PageGrid.COLUMNS && bottom <= PageGrid.ROWS
}

/**
 * Which cells of a page are taken, as one bit per cell. Cheap to build and cheap to ask, which
 * matters because a drag asks it on every frame.
 */
class GridOccupancy(rects: Iterable<GridRect>) {
    private val taken = BooleanArray(PageGrid.cells)

    init {
        rects.forEach { rect -> fill(rect) }
    }

    private fun fill(rect: GridRect) {
        for (r in rect.row until rect.bottom) {
            if (r !in 0 until PageGrid.ROWS) continue
            for (c in rect.col until rect.right) {
                if (c in 0 until PageGrid.COLUMNS) taken[r * PageGrid.COLUMNS + c] = true
            }
        }
    }

    fun free(rect: GridRect): Boolean {
        if (!rect.fitsGrid) return false
        for (r in rect.row until rect.bottom) {
            for (c in rect.col until rect.right) {
                if (taken[r * PageGrid.COLUMNS + c]) return false
            }
        }
        return true
    }

    /** The first free rectangle of [span], reading left to right and top to bottom. */
    fun firstFit(span: GridSpan): GridRect? {
        val s = span.coerced()
        for (r in 0..PageGrid.ROWS - s.rows) {
            for (c in 0..PageGrid.COLUMNS - s.cols) {
                val candidate = GridRect(c, r, s.cols, s.rows)
                if (free(candidate)) return candidate
            }
        }
        return null
    }

    /** How many cells are still empty, for the "no room" message. */
    fun freeCells(): Int = taken.count { !it }
}

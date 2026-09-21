// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.hapticfeedback.HapticFeedback
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.GridOccupancy
import com.nulis.launcher.blocks.GridRect
import com.nulis.launcher.blocks.GridSpan
import com.nulis.launcher.blocks.PageGrid
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.roomFor
import com.nulis.launcher.ui.theme.NulisHaptics
import java.util.UUID

/** How many steps back the editor can go. Enough to undo a mistake, not a session. */
private const val UndoDepth = 12

/**
 * Which knob of a block is being pulled.
 *
 * A grip owns the edges it can move and leaves the rest alone, which is what makes a resize feel
 * like stretching a rectangle rather than moving one. A corner owns one horizontal edge and one
 * vertical edge; an edge midpoint owns exactly one and says so with a null for the other, so
 * pulling the right-hand knob changes the width and cannot change the height by accident.
 *
 * [movesLeft] true means this grip drags the left edge, false the right edge, null neither.
 * [movesTop] reads the same way for the top and bottom edges.
 */
enum class ResizeGrip(val movesLeft: Boolean?, val movesTop: Boolean?) {
    TOP_START(movesLeft = true, movesTop = true),
    TOP(movesLeft = null, movesTop = true),
    TOP_END(movesLeft = false, movesTop = true),
    START(movesLeft = true, movesTop = null),
    END(movesLeft = false, movesTop = null),
    BOTTOM_START(movesLeft = true, movesTop = false),
    BOTTOM(movesLeft = null, movesTop = false),
    BOTTOM_END(movesLeft = false, movesTop = false);

    /** An edge midpoint is only worth drawing on an edge long enough to aim at. */
    val isCorner: Boolean get() = movesLeft != null && movesTop != null
}

/**
 * Everything the in-place editor is doing right now.
 *
 * The editor works on a draft of the page rather than on what is saved, so a block can follow a
 * finger across the screen without a disk write per frame, and so a move that turns out not to
 * fit can be taken back without ever having existed. Every change that finishes does get written
 * immediately, though - the page is the user's work, not a document with a save button.
 *
 * Drag positions are plain float state read only from gesture callbacks and from the graphics
 * layer, never from composition, so dragging a block invalidates a layer and nothing else.
 */
@Stable
class PageEditorState(private val haptics: HapticFeedback) {

    /** The page as the editor currently believes it to be. */
    var draft by mutableStateOf(PageLayout(""))
        private set

    var selectedId by mutableStateOf<String?>(null)

    private val undoStack = ArrayDeque<PageLayout>()

    /** How many steps can be taken back; read by the Undo button. */
    var undoDepth by mutableIntStateOf(0)
        private set

    /** Set by the board on every measure; null until the first one. */
    var metrics: GridMetrics? = null

    // ---------------------------------------------------------------- dragging

    var dragId by mutableStateOf<String?>(null)
        private set
    var dragX by mutableFloatStateOf(0f)
        private set
    var dragY by mutableFloatStateOf(0f)
        private set

    /** Where the dragged or resized block would land, or null when nothing is in the air. */
    var ghost by mutableStateOf<GridRect?>(null)
        private set

    /** True when the ghost is somewhere the block cannot go: drawn in the danger colour. */
    var refused by mutableStateOf(false)
        private set

    /**
     * The page as it would be if the finger let go now, including neighbours that have moved out
     * of the way. Null while nothing is being dragged, and null while the drop is refused - a
     * refused drop must leave the rest of the page exactly where it is.
     */
    var proposal by mutableStateOf<PageLayout?>(null)
        private set

    // ---------------------------------------------------------------- resizing

    var resizeId by mutableStateOf<String?>(null)
        private set
    private var resizeGrip = ResizeGrip.BOTTOM_END
    private var resizeFrom: GridRect? = null
    private var resizeDx = 0f
    private var resizeDy = 0f
    private var lastRect: GridRect? = null

    /** What the page draws: the proposal while one is in the air, otherwise the draft. */
    val shown: PageLayout get() = proposal ?: draft

    /**
     * Takes the saved page as the truth. Called when the editor opens and whenever the page
     * changes underneath it, but never mid-gesture - a write landing during a drag must not pull
     * the block out of the user's hand.
     */
    fun sync(layout: PageLayout) {
        if (dragId != null || resizeId != null) return
        if (draft.pageId != layout.pageId) {
            undoStack.clear()
            undoDepth = 0
            selectedId = null
        }
        if (draft != layout) draft = layout
    }

    // ---------------------------------------------------------------- editing

    /** Applies a change, remembering the page as it was so it can be taken back. */
    private fun commit(next: PageLayout, onWrite: (PageLayout) -> Unit) {
        if (next == draft) return
        undoStack.addLast(draft)
        while (undoStack.size > UndoDepth) undoStack.removeFirst()
        undoDepth = undoStack.size
        draft = next
        onWrite(next)
    }

    fun undo(onWrite: (PageLayout) -> Unit) {
        val previous = undoStack.removeLastOrNull() ?: return
        undoDepth = undoStack.size
        draft = previous
        // Nothing is selected after an undo: the block that was picked may not exist any more.
        if (selectedId != null && previous.block(selectedId!!) == null) selectedId = null
        haptics.performHapticFeedback(NulisHaptics.tick)
        onWrite(previous)
    }

    fun update(block: Block, onWrite: (PageLayout) -> Unit) = commit(draft.replace(block), onWrite)

    fun remove(id: String, onWrite: (PageLayout) -> Unit) {
        if (selectedId == id) selectedId = null
        haptics.performHapticFeedback(NulisHaptics.drop)
        commit(draft.copy(blocks = draft.blocks.filterNot { it.id == id }), onWrite)
    }

    /**
     * A new block of [type] in the first space that fits it, tried at the size that type would
     * like and then smaller. False when there is no room for even the smallest one.
     */
    fun addBlock(type: String, onWrite: (PageLayout) -> Unit): Boolean {
        val spot = draft.roomFor(type) ?: return false
        val fresh = BlockRegistry.newBlock(type).copy(rect = spot)
        haptics.performHapticFeedback(NulisHaptics.confirm)
        commit(draft.copy(blocks = draft.blocks + fresh), onWrite)
        selectedId = fresh.id
        return true
    }

    /**
     * Another of the same block, in the first space that fits it. Returns false when there is no
     * room, which the editor turns into the same offer the block picker makes.
     */
    fun duplicate(id: String, onWrite: (PageLayout) -> Unit): Boolean {
        val source = draft.block(id) ?: return false
        val definition = BlockRegistry.definition(source.type)
        val occupancy = draft.occupancy()
        val spot = occupancy.firstFit(source.area.span)
            ?: occupancy.firstFit(definition?.minSpan ?: GridSpan(2, 1))
            ?: return false
        val copy = source.copy(id = UUID.randomUUID().toString(), rect = spot)
        haptics.performHapticFeedback(NulisHaptics.confirm)
        commit(draft.copy(blocks = draft.blocks + copy), onWrite)
        selectedId = copy.id
        return true
    }

    // ---------------------------------------------------------------- the drag

    fun pickUp(id: String) {
        dragId = id
        dragX = 0f
        dragY = 0f
        selectedId = id
        ghost = draft.block(id)?.area
        refused = false
        proposal = null
        haptics.performHapticFeedback(NulisHaptics.pickup)
    }

    fun dragBy(dx: Float, dy: Float) {
        val id = dragId ?: return
        val block = draft.block(id) ?: return
        val grid = metrics ?: return
        dragX += dx
        dragY += dy
        val target = block.area.movedTo(
            c = block.area.col + grid.colsMoved(dragX),
            r = block.area.row + grid.rowsMoved(dragY),
        )
        if (target == ghost && proposal != null) return
        settleGhost(id, target)
    }

    fun drop(onWrite: (PageLayout) -> Unit) {
        val id = dragId ?: return
        val landed = proposal
        dragId = null
        dragX = 0f
        dragY = 0f
        ghost = null
        val wasRefused = refused
        refused = false
        proposal = null
        if (landed != null && !wasRefused) {
            haptics.performHapticFeedback(NulisHaptics.drop)
            commit(landed, onWrite)
        }
        selectedId = id
    }

    fun cancelDrag() {
        dragId = null
        dragX = 0f
        dragY = 0f
        ghost = null
        refused = false
        proposal = null
    }

    // ---------------------------------------------------------------- the resize

    fun startResize(id: String, grip: ResizeGrip) {
        val from = draft.block(id)?.area ?: return
        resizeId = id
        resizeGrip = grip
        resizeFrom = from
        resizeDx = 0f
        resizeDy = 0f
        selectedId = id
        lastRect = from
        ghost = from
        refused = false
        proposal = null
        haptics.performHapticFeedback(NulisHaptics.pickup)
    }

    /**
     * Moves the two edges the corner owns and leaves the other two where they are, so pulling
     * the top left grows a block upwards and leftwards rather than dragging the whole thing.
     */
    fun resizeBy(dx: Float, dy: Float) {
        val id = resizeId ?: return
        val from = resizeFrom ?: return
        val block = draft.block(id) ?: return
        val grid = metrics ?: return
        resizeDx += dx
        resizeDy += dy
        val min = BlockRegistry.definition(block.type)?.minSpan ?: GridSpan(1, 1)
        val dCols = grid.colsMoved(resizeDx)
        val dRows = grid.rowsMoved(resizeDy)

        var left = from.col
        var right = from.right
        var top = from.row
        var bottom = from.bottom
        // Each edge is clamped against the one opposite it, so a block can be squeezed down to
        // its minimum from either side without the far edge ever moving.
        when (resizeGrip.movesLeft) {
            true -> left = (from.col + dCols).coerceIn(0, right - min.cols)
            false -> right = (from.right + dCols).coerceIn(left + min.cols, PageGrid.COLUMNS)
            null -> Unit
        }
        when (resizeGrip.movesTop) {
            true -> top = (from.row + dRows).coerceIn(0, bottom - min.rows)
            false -> bottom = (from.bottom + dRows).coerceIn(top + min.rows, PageGrid.ROWS)
            null -> Unit
        }
        val rect = GridRect(left, top, right - left, bottom - top)
        if (rect == lastRect) return
        // A tick per cell crossed, the same one a segmented control gives: the size is a series
        // of decisions, and each one should be felt.
        haptics.performHapticFeedback(NulisHaptics.tick)
        lastRect = rect
        settleGhost(id, rect)
    }

    fun endResize(onWrite: (PageLayout) -> Unit) {
        val id = resizeId ?: return
        val landed = proposal
        val wasRefused = refused
        clearResize()
        if (landed != null && !wasRefused) commit(landed, onWrite)
        selectedId = id
    }

    fun cancelResize() = clearResize()

    private fun clearResize() {
        resizeId = null
        resizeFrom = null
        resizeDx = 0f
        resizeDy = 0f
        lastRect = null
        ghost = null
        refused = false
        proposal = null
    }

    // ---------------------------------------------------------------- the steppers

    /**
     * The page with [id] one cell wider, narrower, taller or shorter, or null when it cannot be.
     *
     * A stepper grows from the far edge - right for width, bottom for height - because that is
     * where a block has room in the common case of a page filled from the top left. When the far
     * edge is against the side of the grid it grows from the near one instead, so a block pinned
     * to the right-hand margin still gets wider rather than refusing. Shrinking always pulls the
     * far edge in, so a block never walks across the page by being made smaller.
     */
    private fun stepped(id: String, dCols: Int, dRows: Int): PageLayout? {
        val block = draft.block(id) ?: return null
        val from = block.area
        val min = BlockRegistry.definition(block.type)?.minSpan ?: GridSpan(1, 1)
        var left = from.col
        var right = from.right
        var top = from.row
        var bottom = from.bottom
        when {
            dCols > 0 -> if (right < PageGrid.COLUMNS) right += 1 else if (left > 0) left -= 1 else return null
            dCols < 0 -> if (right - left > min.cols) right -= 1 else return null
        }
        when {
            dRows > 0 -> if (bottom < PageGrid.ROWS) bottom += 1 else if (top > 0) top -= 1 else return null
            dRows < 0 -> if (bottom - top > min.rows) bottom -= 1 else return null
        }
        val rect = GridRect(left, top, right - left, bottom - top)
        if (rect == from) return null
        return makeRoom(draft, id, rect)
    }

    /** Whether a stepper has anywhere to go, so a button that cannot work is drawn as dimmed. */
    fun canStep(id: String?, dCols: Int, dRows: Int): Boolean =
        id != null && stepped(id, dCols, dRows) != null

    /**
     * One cell bigger or smaller, from a button rather than from a corner. Every resize in the
     * editor used to need a finger on a handle; this is the same operation for anyone who cannot
     * land on one, and for the blocks too small to carry eight knobs.
     */
    fun stepSize(id: String, dCols: Int, dRows: Int, onWrite: (PageLayout) -> Unit): Boolean {
        val next = stepped(id, dCols, dRows)
        if (next == null) {
            haptics.performHapticFeedback(NulisHaptics.reject)
            return false
        }
        haptics.performHapticFeedback(NulisHaptics.tick)
        commit(next, onWrite)
        return true
    }

    // ---------------------------------------------------------------- making room

    /** Works out what the page would look like with [id] at [target], and whether it can be. */
    private fun settleGhost(id: String, target: GridRect) {
        ghost = target
        val next = makeRoom(draft, id, target)
        refused = next == null
        proposal = next
        if (next == null) haptics.performHapticFeedback(NulisHaptics.reject)
    }
}

/**
 * The page with [id] moved to [target] and everything that was in the way moved somewhere it is
 * not, or null when there is nowhere for them to go.
 *
 * Two blocks of the same shape swap, because that is what dragging one onto the other looks like
 * it should do. Otherwise each displaced block takes the first free rectangle of its own size -
 * which usually means it slides into the space the dragged block just left - and if any of them
 * has nowhere to go at all, the whole move is refused rather than half-done.
 */
fun makeRoom(layout: PageLayout, id: String, target: GridRect): PageLayout? {
    if (!target.fitsGrid) return null
    val moving = layout.block(id) ?: return null
    if (moving.area == target) return layout
    val hit = layout.overlapping(target, id)
    if (hit.isEmpty()) return layout.withRect(id, target).validated()

    // The swap: one block in the way, and it fits exactly where this one came from. The two
    // checks are not the same one twice - the block being swapped out must clear every other
    // block on the page *and* the place the dragged block is going, which is not where it was.
    if (hit.size == 1) {
        val other = hit.first()
        val swapped = other.area.movedTo(moving.area.col, moving.area.row)
        val clearsTarget = !swapped.overlaps(target)
        val clearsRest = layout.overlapping(swapped, other.id).all { it.id == id }
        if (swapped.fitsGrid && clearsTarget && clearsRest) {
            return layout.withRect(id, target).withRect(other.id, swapped).validated()
        }
    }

    var out = layout.withRect(id, target)
    val settled = ArrayList<GridRect>()
    settled += target
    out.blocks.forEach { block ->
        if (block.id != id && hit.none { it.id == block.id }) settled += block.area
    }
    hit.forEach { block ->
        val spot = GridOccupancy(settled).firstFit(block.area.span) ?: return null
        out = out.withRect(block.id, spot)
        settled += spot
    }
    return out.validated()
}

/**
 * The page back again, or null if it is not one. Twelve blocks is nothing to check and the cost
 * of being wrong here is somebody's home screen quietly eating a block, so every answer the
 * editor is about to commit goes through this first.
 */
private fun PageLayout.validated(): PageLayout? {
    blocks.forEachIndexed { index, one ->
        if (!one.area.fitsGrid) return null
        blocks.drop(index + 1).forEach { other -> if (one.area.overlaps(other.area)) return null }
    }
    return this
}

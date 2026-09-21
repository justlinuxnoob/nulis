// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nulis.launcher.R
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.GridRect
import com.nulis.launcher.blocks.LocalBlockAlign
import com.nulis.launcher.blocks.PageGrid
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.GlyphIcon
import com.nulis.launcher.ui.components.LocalPreviewStill
import com.nulis.launcher.ui.components.NulisCard
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisMotion
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * The knobs you pull to resize.
 *
 * They were corner brackets: two hairlines at 18 dp, drawn in the ink over whatever the block was
 * already drawing, and nobody saw them. A knob is a filled disc with a ring of the background
 * around it, so it reads as an object sitting on the block rather than as part of it, on a photo
 * as readily as on black. [KnobTouch] is what the finger gets; it shrinks on a block too small to
 * hold four of them so that two knobs never fight over the same square millimetre.
 */
private val KnobSize = 16.dp
private val KnobTouch = 46.dp
private val KnobTouchMin = 26.dp

/**
 * The editor, which is the page.
 *
 * Long-press anywhere and this opens on top of the page you were looking at, drawn from the same
 * blocks with the same data, scaled back just far enough to read as a step away from it. The
 * grid appears, every block gets an outline, and from then on the page is the thing you
 * manipulate: drag a block and it follows your finger, let go and it lands on the nearest cells
 * with whatever was there sliding out of the way; pull the corner and it grows a cell at a time
 * with a tick for each one. Tap one and a small toolbar appears beside it.
 *
 * Nothing here is a list of cards describing the page. That was the old editor, and the whole
 * complaint about it was that it was a form, not a home screen.
 */
@Composable
fun PageEditor(
    layout: PageLayout,
    pageName: String,
    context: BlockContext,
    initialSelection: String?,
    onWrite: (PageLayout) -> Unit,
    onDone: () -> Unit,
    onAdd: () -> Unit,
    /** A block type the picker chose, to be placed on this page and then forgotten. */
    pendingAdd: String?,
    onAddHandled: () -> Unit,
    onOpenSettings: () -> Unit,
    onPageOptions: () -> Unit,
    onBlockOptions: (Block) -> Unit,
    /** A duplicate that would not fit: the block's type, so the page can offer a way out. */
    onNoRoom: (String) -> Unit,
    /** True until the first time anybody has been told what the editor does. */
    coachHint: Boolean,
    onCoachSeen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val state = remember(haptics) { PageEditorState(haptics) }

    LaunchedEffect(layout) { state.sync(layout) }
    LaunchedEffect(initialSelection) { if (initialSelection != null) state.selectedId = initialSelection }
    // The picker chooses; the editor places. Going through the editor rather than straight to
    // the repository is what puts a new block on the undo stack with everything else.
    LaunchedEffect(pendingAdd) {
        val type = pendingAdd ?: return@LaunchedEffect
        if (!state.addBlock(type, onWrite)) onNoRoom(type)
        onAddHandled()
    }
    BackHandler {
        when {
            state.selectedId != null -> state.selectedId = null
            else -> onDone()
        }
    }

    // The step away from the page. Animated in from 1, so opening the editor reads as the page
    // itself receding rather than as a different screen arriving.
    val scale by animateFloatAsState(NulisMotion.editScale, NulisMotion.settle, label = "editScale")

    val shown = state.shown
    val blocks = shown.blocks

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            // A tap on the empty part of the page puts the toolbar away, which is the only thing
            // that is ever "open" in here.
            .pointerInput(Unit) { detectTapGestures { state.selectedId = null } },
    ) {
        Column(Modifier.fillMaxSize()) {
            EditorHeader(
                pageName = pageName,
                onPageOptions = onPageOptions,
                onSettings = onOpenSettings,
                onDone = onDone,
            )

            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
            ) {
                // The same rule the live page uses. If the editor's grid were a different width
                // from the page's, a block dropped on the third column would land on the second.
                val boardWidth = maxPageWidth(maxHeight)
                CompositionLocalProvider(LocalPreviewStill provides true) {
                    PageBoard(
                        blocks = blocks,
                        gutter = shown.density.gutter,
                        onMetrics = { state.metrics = it },
                        animate = true,
                        modifier = Modifier
                            .widthIn(max = boardWidth)
                            .fillMaxSize()
                            .align(Alignment.Center)
                            // The same geometry the live page has, dots' room included, so a
                            // block put somewhere in here is where it will be out there.
                            .padding(start = NulisSpacing.screenMargin, end = NulisSpacing.screenMargin, top = 8.dp, bottom = PageDotsRoom)
                            .editorGrid(shown.density.gutter, colors.hairline)
                            .ghostOverlay(state, colors.onBackground, colors.danger),
                    ) { block, area ->
                        EditorBlock(
                            block = block,
                            area = area,
                            layout = shown,
                            context = context,
                            state = state,
                            onWrite = onWrite,
                            onOptions = { onBlockOptions(block) },
                        )
                    }
                }

                // The toolbar lives in the board's own space, so it follows the block it belongs
                // to as that block moves.
                if (state.resizeId != null) {
                    SizeReadout(
                        state = state,
                        metrics = state.metrics,
                        modifier = Modifier
                            .padding(horizontal = NulisSpacing.screenMargin, vertical = 8.dp)
                            .zIndex(4f),
                    )
                }

            }

            EditorBar(
                selected = state.selectedId?.let { shown.block(it) },
                state = state,
                onAdd = onAdd,
                onWrite = onWrite,
                onOptions = onBlockOptions,
                onNoRoom = onNoRoom,
                modifier = Modifier.padding(horizontal = NulisSpacing.screenMargin),
            )
        }

        if (coachHint) {
            CoachHint(
                onDismiss = onCoachSeen,
                modifier = Modifier.align(Alignment.Center).zIndex(5f),
            )
        }

        // Said once, quietly, at the moment it is true.
        AnimatedVisibility(
            visible = state.refused,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 72.dp),
        ) {
            NulisCard(contentPadding = 10.dp) {
                Text(
                    text = stringResource(R.string.edit_refused),
                    style = NulisTheme.type.label,
                    color = colors.danger,
                )
            }
        }
    }
    @Suppress("UNUSED_EXPRESSION") density
}

/** A dot at every cell corner: enough to read the grid, not enough to look at. */
private fun Modifier.editorGrid(gutter: Dp, color: Color): Modifier = drawBehind {
    val stepX = (size.width + gutter.toPx()) / PageGrid.COLUMNS
    val stepY = (size.height + gutter.toPx()) / PageGrid.ROWS
    val radius = 1.dp.toPx()
    for (c in 0..PageGrid.COLUMNS) {
        for (r in 0..PageGrid.ROWS) {
            val x = (c * stepX - gutter.toPx() / 2f).coerceIn(0f, size.width)
            val y = (r * stepY - gutter.toPx() / 2f).coerceIn(0f, size.height)
            drawCircle(color, radius, Offset(x, y), alpha = 0.75f)
        }
    }
}

/**
 * Where a dragged or pulled block would land, drawn on the grid under it.
 *
 * Drawn in the ink, never in the accent. The accent defaults to red, and red in the editor has
 * exactly one meaning - this will not go through. A ghost that was red while the drop was
 * perfectly fine said the opposite of what was true.
 */
private fun Modifier.ghostOverlay(state: PageEditorState, ink: Color, danger: Color): Modifier =
    drawBehind {
        val rect = state.ghost ?: return@drawBehind
        val metrics = state.metrics ?: return@drawBehind
        val x = metrics.x(rect.col).toPx()
        val y = metrics.y(rect.row).toPx()
        val w = metrics.width(rect.cols).toPx()
        val h = metrics.height(rect.rows).toPx()
        val color = if (state.refused) danger else ink
        drawRoundRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(w, h),
            cornerRadius = CornerRadius(10.dp.toPx()),
            alpha = 0.12f,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(x, y),
            size = Size(w, h),
            cornerRadius = CornerRadius(10.dp.toPx()),
            style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 5.dp.toPx()))),
        )
    }

/**
 * One block in the editor: the real block, outlined, draggable, and carrying its resize handle
 * while it is the selected one.
 */
@Composable
private fun EditorBlock(
    block: Block,
    area: DpSize,
    layout: PageLayout,
    context: BlockContext,
    state: PageEditorState,
    onWrite: (PageLayout) -> Unit,
    onOptions: () -> Unit,
) {
    val colors = NulisTheme.colors
    val definition = BlockRegistry.definition(block.type)
    val align = layout.alignOf(block)
    val selected = state.selectedId == block.id
    val dragging = state.dragId == block.id

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(if (dragging) 3f else if (selected) 1f else 0f)
            .graphicsLayer {
                if (dragging) {
                    translationX = state.dragX
                    translationY = state.dragY
                    scaleX = NulisMotion.liftScale
                    scaleY = NulisMotion.liftScale
                    shadowElevation = 0f
                    alpha = 0.92f
                }
            }
            // Selection is the ink, not the accent. The accent defaults to red and red in here
            // already means "this will not go through" - a selected block and a refused drop
            // must never look like the same thing.
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) colors.onBackground else colors.hairline,
                shape = NulisShapes.tile,
            )
            .background(if (dragging) colors.surfaceRaised else Color.Transparent, NulisShapes.tile),
    ) {
        CompositionLocalProvider(LocalBlockAlign provides align) {
            BlockFrame(area = area, align = align, modifier = Modifier.fillMaxSize().padding(2.dp)) {
                if (definition == null) {
                    Text(
                        stringResource(R.string.block_unknown),
                        style = NulisTheme.type.bodyM,
                        color = colors.secondary,
                    )
                } else {
                    definition.style(block).Render(block, context, Modifier.fillMaxWidth())
                }
            }
        }

        // The whole rectangle is the block, so the whole rectangle is the target. The block's
        // own content - app icons, transport buttons, a note's text field, a tappable chart -
        // is rendered above but sits *under* this sheet of glass, so in here none of it can
        // take a touch. Before this, a block whose content happened to be clickable could only
        // be picked up by the one or two millimetres of outline around it.
        Box(
            Modifier
                .matchParentSize()
                .pointerInput(block.id, selected) {
                    // First tap picks the block up into the toolbar; a second one on the same
                    // block goes straight to its options, which is where a tap on something
                    // already selected wants to go.
                    detectTapGestures { if (selected) onOptions() else state.selectedId = block.id }
                }
                .pointerInput(block.id) {
                    detectDragGestures(
                        onDragStart = { state.pickUp(block.id) },
                        onDrag = { change, amount ->
                            change.consume()
                            state.dragBy(amount.x, amount.y)
                        },
                        onDragEnd = { state.drop(onWrite) },
                        onDragCancel = { state.cancelDrag() },
                    )
                },
        )

        // Every knob the block has room for, on top of the glass, so a selected block says
        // "pull me" whichever way it can grow. Each one sits inside the block's own bounds: a
        // knob hanging off the edge would be drawn but not reachable, because hit testing stops
        // at the rectangle it belongs to.
        //
        // An edge midpoint only appears on an edge at least two cells long. On a one-cell edge
        // its touch target would be the same square millimetres as the two corners beside it,
        // and the bottom bar's steppers are the honest way to resize something that small.
        if (selected && !dragging) {
            val touch = listOf(KnobTouch, area.width / 2, area.height / 2)
                .min()
                .coerceAtLeast(KnobTouchMin)
            ResizeGrip.entries.forEach { grip ->
                val fits = when (grip) {
                    ResizeGrip.TOP, ResizeGrip.BOTTOM -> block.area.cols >= 2
                    ResizeGrip.START, ResizeGrip.END -> block.area.rows >= 2
                    else -> true
                }
                if (!fits) return@forEach
                ResizeKnob(
                    state = state,
                    blockId = block.id,
                    grip = grip,
                    touch = touch,
                    onWrite = onWrite,
                    modifier = Modifier.align(grip.alignment),
                )
            }
        }
    }
}

private val ResizeGrip.alignment: Alignment
    get() = when (this) {
        ResizeGrip.TOP_START -> Alignment.TopStart
        ResizeGrip.TOP -> Alignment.TopCenter
        ResizeGrip.TOP_END -> Alignment.TopEnd
        ResizeGrip.START -> Alignment.CenterStart
        ResizeGrip.END -> Alignment.CenterEnd
        ResizeGrip.BOTTOM_START -> Alignment.BottomStart
        ResizeGrip.BOTTOM -> Alignment.BottomCenter
        ResizeGrip.BOTTOM_END -> Alignment.BottomEnd
    }

private val ResizeGrip.label: Int
    get() = if (isCorner) R.string.edit_resize_corner else R.string.edit_resize_edge

/**
 * One knob: a disc of ink inside a ring of the background, so it stands off whatever the block is
 * drawing underneath - a photo, a chart, a wall of app icons - without needing a shadow.
 */
@Composable
private fun ResizeKnob(
    state: PageEditorState,
    blockId: String,
    grip: ResizeGrip,
    touch: Dp,
    onWrite: (PageLayout) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NulisTheme.colors
    val description = stringResource(grip.label)
    Box(
        modifier
            .size(touch)
            .semantics { contentDescription = description }
            .pointerInput(blockId, grip) {
                detectDragGestures(
                    onDragStart = { state.startResize(blockId, grip) },
                    onDrag = { change, amount ->
                        change.consume()
                        state.resizeBy(amount.x, amount.y)
                    },
                    onDragEnd = { state.endResize(onWrite) },
                    onDragCancel = { state.cancelResize() },
                )
            },
        contentAlignment = grip.alignment,
    ) {
        Canvas(Modifier.size(KnobSize)) {
            val r = size.minDimension / 2f
            drawCircle(colors.background, r, alpha = 0.9f)
            drawCircle(colors.onBackground, r - 2.dp.toPx())
        }
    }
}

/**
 * How many cells the block being pulled would take, said while it is being pulled and gone the
 * moment it lands. Without it a resize is a guess: the grid is dots, and counting dots under a
 * finger is not reading.
 */
@Composable
private fun SizeReadout(state: PageEditorState, metrics: GridMetrics?, modifier: Modifier = Modifier) {
    val rect = state.ghost ?: return
    if (metrics == null) return
    val y = (metrics.y(rect.row) - ReadoutHeight - 4.dp).coerceAtLeast(0.dp)
    val x = (metrics.x(rect.col) + metrics.width(rect.cols) / 2 - ReadoutWidth / 2)
        .coerceIn(0.dp, (metrics.pageWidth - ReadoutWidth).coerceAtLeast(0.dp))
    NulisCard(modifier = modifier.offset(x = x, y = y).width(ReadoutWidth), raised = true, contentPadding = 6.dp) {
        Text(
            text = "${rect.cols} x ${rect.rows}",
            style = NulisTheme.type.label,
            color = if (state.refused) NulisTheme.colors.danger else NulisTheme.colors.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private val ReadoutWidth = 76.dp
private val ReadoutHeight = 34.dp

/**
 * Said once, the first time anybody opens the editor, and never again. Three sentences for the
 * three things you can do to a block; a tap anywhere on it puts it away for good.
 */
@Composable
private fun CoachHint(onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NulisTheme.colors
    NulisCard(
        modifier = modifier.widthIn(max = 320.dp).padding(horizontal = NulisSpacing.screenMargin),
        raised = true,
        contentPadding = 14.dp,
        onClick = onDismiss,
    ) {
        Column {
            Text(
                text = stringResource(R.string.edit_coach),
                style = NulisTheme.type.bodyM,
                color = colors.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Caption(stringResource(R.string.edit_coach_dismiss))
        }
    }
}


@Composable
private fun EditorHeader(
    pageName: String,
    onPageOptions: () -> Unit,
    onSettings: () -> Unit,
    onDone: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(horizontal = NulisSpacing.screenMargin).padding(top = 8.dp, bottom = 4.dp)) {
        SectionLabel(stringResource(R.string.edit_label), withLine = false)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = pageName,
                style = NulisTheme.type.displayM,
                color = NulisTheme.colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            // What the whole page is set to - the edge its blocks hang from, how much air is
            // between them - which is the one thing in here that a drag cannot say.
            NulisIconButton(Glyph.Layout, onClick = onPageOptions, bordered = true)
            Spacer(Modifier.width(8.dp))
            NulisIconButton(Glyph.Gear, onClick = onSettings, bordered = true)
            Spacer(Modifier.width(8.dp))
            PillButton(
                text = stringResource(R.string.edit_done),
                onClick = {
                    haptics.performHapticFeedback(NulisHaptics.confirm)
                    onDone()
                },
                tone = PillTone.Primary,
            )
        }
    }
}

/**
 * The bar along the bottom, which is two bars.
 *
 * With nothing picked it is the page's own: Add, Undo, and how much room is left. Pick a block and
 * it becomes that block's - its name, its size, and everything you can do to it - and goes back
 * the moment you put the block down.
 *
 * The controls used to float beside the block they belonged to, and on a page with any two blocks
 * near each other that meant covering the neighbour: you could not see the thing you were about
 * to align against. Down here they cover nothing, they are always in the same place, and they are
 * within a thumb's reach, which a card pinned to the top of the page never was.
 *
 * Its height does not change between the two states. A bar that grew when you picked a block
 * would shrink the board above it and reflow every block on the page at the moment of selecting
 * one, which reads as the page flinching away from the finger.
 */
@Composable
private fun EditorBar(
    selected: Block?,
    state: PageEditorState,
    onAdd: () -> Unit,
    onWrite: (PageLayout) -> Unit,
    onOptions: (Block) -> Unit,
    onNoRoom: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(EditorBarHeight), contentAlignment = Alignment.Center) {
        if (selected == null) {
            PageBarContent(state = state, onAdd = onAdd, onWrite = onWrite)
        } else {
            BlockBarContent(
                block = selected,
                state = state,
                onWrite = onWrite,
                onOptions = { onOptions(selected) },
                onNoRoom = { onNoRoom(selected.type) },
            )
        }
    }
}

@Composable
private fun PageBarContent(
    state: PageEditorState,
    onAdd: () -> Unit,
    onWrite: (PageLayout) -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PillButton(
            text = stringResource(R.string.edit_add_block),
            onClick = onAdd,
            leading = { GlyphIcon(Glyph.Plus, NulisTheme.colors.onBackground, size = 14.dp) },
        )
        // Only once there is something to take back. A disabled bordered button is a hairline
        // pill with a glyph dimmed to the tertiary ink, and on black that is an empty grey pill
        // sitting on the bar saying nothing - which is exactly how it was read.
        if (state.undoDepth > 0) {
            Spacer(Modifier.width(8.dp))
            NulisIconButton(
                Glyph.Undo,
                onClick = { state.undo(onWrite) },
                bordered = true,
            )
        }
        Spacer(Modifier.weight(1f))
        Caption(
            if (state.draft.blocks.isEmpty()) {
                stringResource(R.string.edit_empty_page)
            } else {
                state.draft.occupancy().freeCells().let { free -> pluralStringResource(R.plurals.edit_free_cells, free, free) }
            },
            lines = 1,
        )
    }
}

@Composable
private fun BlockBarContent(
    block: Block,
    state: PageEditorState,
    onWrite: (PageLayout) -> Unit,
    onOptions: () -> Unit,
    onNoRoom: () -> Unit,
) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    val name = BlockRegistry.definition(block.type)?.label ?: stringResource(R.string.block_unknown)
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = NulisTheme.type.labelCase(name),
                style = NulisTheme.type.label,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Stepper(
                label = pluralStringResource(R.plurals.edit_size_width, block.area.cols, block.area.cols),
                lessDescription = stringResource(R.string.edit_size_narrower),
                moreDescription = stringResource(R.string.edit_size_wider),
                canLess = state.canStep(block.id, -1, 0),
                canMore = state.canStep(block.id, 1, 0),
                onLess = { state.stepSize(block.id, -1, 0, onWrite) },
                onMore = { state.stepSize(block.id, 1, 0, onWrite) },
            )
            Spacer(Modifier.width(4.dp))
            Stepper(
                label = pluralStringResource(R.plurals.edit_size_height, block.area.rows, block.area.rows),
                lessDescription = stringResource(R.string.edit_size_shorter),
                moreDescription = stringResource(R.string.edit_size_taller),
                canLess = state.canStep(block.id, 0, -1),
                canMore = state.canStep(block.id, 0, 1),
                onLess = { state.stepSize(block.id, 0, -1, onWrite) },
                onMore = { state.stepSize(block.id, 0, 1, onWrite) },
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Options first and in words, because it is the one that leads somewhere: every
            // style and every setting this block has. The rest do their thing on the spot.
            PillButton(
                text = stringResource(R.string.options_open),
                onClick = onOptions,
                compact = true,
                leading = { GlyphIcon(Glyph.Gear, colors.onBackground, size = 14.dp) },
            )
            Spacer(Modifier.width(4.dp))
            NulisIconButton(
                Glyph.Align,
                onClick = {
                    haptics.performHapticFeedback(NulisHaptics.tick)
                    // Cycles rather than opening anything: three choices, and the block in front
                    // of you shows each one as you pass it. Landing back on what the page itself
                    // says stores no override at all, so the block goes on following the page.
                    val page = state.draft.align
                    val current = state.draft.alignOf(block)
                    val next = BlockAlign.entries[(current.ordinal + 1) % BlockAlign.entries.size]
                    state.update(block.copy(align = next.takeIf { it != page }), onWrite)
                },
                size = 42.dp,
                contentDescription = stringResource(R.string.options_align),
            )
            NulisIconButton(
                Glyph.Copy,
                onClick = { if (!state.duplicate(block.id, onWrite)) onNoRoom() },
                size = 42.dp,
                contentDescription = stringResource(R.string.options_duplicate),
            )
            NulisIconButton(
                Glyph.Trash,
                onClick = { state.remove(block.id, onWrite) },
                size = 42.dp,
                tint = colors.danger,
                contentDescription = stringResource(R.string.options_remove),
            )
            Spacer(Modifier.weight(1f))
            // The way out that does not need you to find a bare patch of page to tap.
            NulisIconButton(
                Glyph.Check,
                onClick = { state.selectedId = null },
                size = 42.dp,
                bordered = true,
                contentDescription = stringResource(R.string.edit_deselect),
            )
        }
    }
}

/**
 * Width or height, a cell at a time, from buttons.
 *
 * The knobs are the direct way to resize and this is the certain one. A dimmed minus or plus is
 * an answer in itself: that edge has nowhere left to go, which a corner you are pulling against
 * a wall never says out loud.
 */
@Composable
private fun Stepper(
    label: String,
    lessDescription: String,
    moreDescription: String,
    canLess: Boolean,
    canMore: Boolean,
    onLess: () -> Unit,
    onMore: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        NulisIconButton(Glyph.Minus, onClick = onLess, enabled = canLess, size = 38.dp, contentDescription = lessDescription)
        // A floor, not a width. At 46 dp flat, "6 WIDE" was clipped to "6" on a 320 dpi phone -
        // the readout said nothing and the two steppers became four identical buttons. The floor
        // is only there to stop the row shuffling sideways as the number changes width.
        Text(
            text = NulisTheme.type.labelCase(label),
            style = NulisTheme.type.label,
            color = NulisTheme.colors.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.widthIn(min = 46.dp).padding(horizontal = 2.dp),
        )
        NulisIconButton(Glyph.Plus, onClick = onMore, enabled = canMore, size = 38.dp, contentDescription = moreDescription)
    }
}

private val EditorBarHeight = 96.dp

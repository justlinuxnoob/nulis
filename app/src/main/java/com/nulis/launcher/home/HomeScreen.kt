// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nulis.launcher.R
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.LocalBlockAlign
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.clock.ClockBlockDefinition
import com.nulis.launcher.gestures.GestureSettings
import com.nulis.launcher.gestures.GestureTrigger
import com.nulis.launcher.gestures.pageGestures
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.SlideState
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * How wide a page is allowed to be on a screen with more width than it needs.
 *
 * A flat cap made a tablet look like a phone lost in the middle of it: a 560 dp column of blocks
 * with a hand's width of nothing down either side. What actually matters is the shape of a cell -
 * six across and twelve down means a page about half as wide as it is tall keeps its cells
 * square - so the cap follows the height instead, and [MinPageWidth] keeps the old behaviour
 * everywhere it already fitted.
 */
private val MinPageWidth = 560.dp
private const val PageWidthOfHeight = 0.62f

/**
 * How wide the page may be inside a box [available] tall. The live page and the editor both ask
 * this, because a block placed in the editor has to land exactly where it was put.
 */
fun maxPageWidth(available: Dp): Dp = maxOf(MinPageWidth, available * PageWidthOfHeight)

/** Height kept clear at the bottom of every page for the page dots to sit in. */
val PageDotsRoom = 24.dp

/**
 * One page, drawn as the grid it is.
 *
 * A page is exactly one screen. Every block is a rectangle on a six by twelve grid and fills the
 * rectangle it was given, so nothing ever runs off the bottom and there is nothing to scroll.
 * Long-pressing anywhere - a block or the empty space beside it - opens the editor on this very
 * page; every other gesture is looked up in [gestures] and reported through [onGesture].
 *
 * @param layout null while the layout is still loading, so nothing flashes on start.
 * @param drawer the drawer's slide state, driven directly by drags on this page.
 */
@Composable
fun HomeScreen(
    layout: PageLayout?,
    context: BlockContext,
    drawer: SlideState,
    gestures: GestureSettings,
    onGesture: (GestureTrigger) -> Unit,
    /** Long press on a block: the editor opens with that block picked. */
    onEditBlock: (Block) -> Unit,
    /** Long press on empty space: the editor opens with nothing picked. */
    onEditPage: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    /** What the page paints behind its blocks. Translucent when the wallpaper shows through. */
    background: Color? = null,
    /**
     * A small gear in the bottom corner. Only the home page asks for it, and only when the user
     * has left it on: it is a shortcut, not the way in.
     */
    settingsGear: Boolean = false,
) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current

    // A clock block only listens for a double tap while that gesture actually does something.
    val doubleTapBlockType = if (gestures.isBound(GestureTrigger.DOUBLE_TAP_CLOCK)) ClockBlockDefinition.type else null

    // A block can take itself off the page (music with nothing playing).
    val blocks = layout?.blocks?.filterNot {
        BlockRegistry.definition(it.type)?.isHidden(it, context) == true
    } ?: emptyList()

    // Taps and long presses on anything that is not a block. Blocks consume their own long press
    // on the initial pass and their own taps on the main one, so this only ever sees the empty
    // parts of a page - which is why it can be one layer behind the grid rather than inside it.
    val emptySpace = Modifier.pointerInput(gestures) {
        detectTapGestures(
            onDoubleTap = if (gestures.isBound(GestureTrigger.DOUBLE_TAP)) {
                {
                    haptics.performHapticFeedback(NulisHaptics.threshold)
                    onGesture(GestureTrigger.DOUBLE_TAP)
                }
            } else {
                null
            },
            onLongPress = {
                haptics.performHapticFeedback(NulisHaptics.longPress)
                // A long press on empty space is how you get into the editor. A page can bind it
                // to something else, and then that wins - but the block long press still leads
                // here, so a page is never a dead end.
                if (gestures.isBound(GestureTrigger.LONG_PRESS)) onGesture(GestureTrigger.LONG_PRESS) else onEditPage()
            },
        )
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(background ?: colors.background)
            .pageGestures(drawer, gestures, enabled = true, onGesture = onGesture)
            // Bars and cutout only: home never hosts a keyboard, so it must not relayout while
            // one animates over the drawer.
            .windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))
            .then(emptySpace),
        contentAlignment = Alignment.TopCenter,
    ) {
        // A page with nothing on it would otherwise give no hint that a long press does anything.
        if (layout != null && blocks.isEmpty()) {
            Text(
                text = NulisTheme.type.labelCase(stringResource(R.string.page_empty_hint)),
                style = NulisTheme.type.label,
                color = colors.tertiary,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        if (layout != null) {
            val maxPageWidth = maxPageWidth(maxHeight)
            PageBoard(
                blocks = blocks,
                gutter = (layout.density).gutter,
                modifier = Modifier
                    // On a tablet a page of full-width text is unreadable; the grid stops
                    // growing and sits in the middle instead.
                    .widthIn(max = maxPageWidth)
                    .fillMaxSize()
                    // The bottom strip is the page dots' room. Without it the last row of a
                    // page lands underneath them, and the sixth app in a list reads as a
                    // mistake rather than as the last app in a list.
                    .padding(start = NulisSpacing.screenMargin, end = NulisSpacing.screenMargin, top = 8.dp, bottom = PageDotsRoom),
            ) { block, area ->
                key(block.id) {
                    LiveBlock(
                        block = block,
                        area = area,
                        layout = layout,
                        context = context,
                        onLongPress = { onEditBlock(block) },
                        onDoubleTap = if (block.type == doubleTapBlockType) ({ onGesture(GestureTrigger.DOUBLE_TAP_CLOCK) }) else null,
                    )
                }
            }
        }

        // After the grid, so it sits on top of the empty space that catches long presses.
        if (settingsGear) {
            NulisIconButton(
                glyph = Glyph.Gear,
                onClick = onOpenSettings,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp),
                tint = colors.tertiary,
            )
        }
    }
}

/** One block on a live page: its own taps, its own long press into the editor, nothing else. */
@Composable
private fun LiveBlock(
    block: Block,
    area: DpSize,
    layout: PageLayout,
    context: BlockContext,
    onLongPress: () -> Unit,
    onDoubleTap: (() -> Unit)?,
) {
    val haptics = LocalHapticFeedback.current
    val definition = BlockRegistry.definition(block.type)
    val align = layout.alignOf(block)
    if (definition == null) {
        Text(
            stringResource(R.string.block_unknown),
            style = NulisTheme.type.bodyM,
            color = NulisTheme.colors.secondary,
        )
        return
    }
    val tap = definition.tapAction(block, context)
    val spoken = definition.accessibilityLabel(block, context)
    CompositionLocalProvider(LocalBlockAlign provides align) {
        BlockFrame(
            area = area,
            align = align,
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (spoken == null) {
                        Modifier
                    } else {
                        Modifier.semantics {
                            contentDescription = spoken
                            if (tap != null) onClick(label = "Open", action = null)
                        }
                    },
                )
                .longPressAnywhere {
                    haptics.performHapticFeedback(NulisHaptics.longPress)
                    onLongPress()
                }
                .then(
                    // A tap on the block (the clock's clock app, the date's calendar) and the
                    // page's double-tap gesture share one detector, so they never race. The long
                    // press above runs on the Initial pass and consumes, so holding the block
                    // opens the editor without also firing a tap on release.
                    if (onDoubleTap == null && tap == null) {
                        Modifier
                    } else {
                        Modifier.pointerInput(onDoubleTap, tap) {
                            detectTapGestures(
                                onTap = if (tap == null) null else ({ tap() }),
                                onDoubleTap = if (onDoubleTap == null) {
                                    null
                                } else {
                                    {
                                        haptics.performHapticFeedback(NulisHaptics.threshold)
                                        onDoubleTap()
                                    }
                                },
                            )
                        }
                    },
                ),
        ) {
            definition.style(block).Render(block = block, context = context, modifier = Modifier.fillMaxWidth())
        }
    }
}

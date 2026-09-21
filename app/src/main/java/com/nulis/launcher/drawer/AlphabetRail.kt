// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.drawer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisMotion
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme
import kotlin.math.roundToInt

private val RailWidth = 28.dp
/** Minimum width of the bubble; it grows to fit the name under the letter. */
private val BubbleSize = 84.dp

/**
 * A-Z fast scroller hugging the right edge. The rail owns its touches, so the list never scrolls
 * underneath, and a large letter floats beside the thumb while it is held.
 *
 * **The list moves on touch and on release, and not once in between.** Dragging the rail used to
 * jump the list at every letter, and every jump composed a whole screenful of rows inside a
 * single frame: the one gesture in the launcher that was reliably over budget, at 18-20% of
 * frames and up to 35 ms. A drag over a 26-letter rail is a dozen apps per pixel travelled, so
 * what was being composed at that cost was a blur nobody could read. The bubble says where you
 * are the whole way down and the list arrives when you let go, which is both faster and easier
 * to aim.
 */
@Composable
fun AlphabetRail(
    letters: List<String>,
    onLetter: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    /** What sits under the letter in the bubble: the first app in that section, usually. */
    preview: (index: Int) -> String? = { null },
) {
    if (letters.isEmpty()) return
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    val currentOnLetter by rememberUpdatedState(onLetter)
    var active by remember { mutableStateOf(false) }
    var selected by remember { mutableIntStateOf(-1) }
    var thumbY by remember { mutableFloatStateOf(0f) }

    val density = LocalDensity.current
    var railHeightPx by remember { mutableFloatStateOf(1f) }
    var railTopPx by remember { mutableFloatStateOf(0f) }
    var boxHeightPx by remember { mutableFloatStateOf(0f) }
    // The bubble wraps its content, so its height is whatever the name under the letter needs.
    var bubblePx by remember { mutableFloatStateOf(with(density) { BubbleSize.toPx() }) }
    val maxItemPx = with(density) { 18.dp.roundToPx() }
    val reservePx = with(density) { 48.dp.roundToPx() }

    Box(modifier.fillMaxHeight().onSizeChanged { boxHeightPx = it.height.toFloat() }, contentAlignment = Alignment.CenterEnd) {
        // A plain layout (no subcomposition): letters share the height evenly, capped at 18dp each.
        Layout(
            content = {
                letters.forEachIndexed { index, letter ->
                    key(letter) {
                        Text(
                            text = letter,
                            style = NulisTheme.type.label,
                            color = if (index == selected) colors.onBackground else colors.tertiary,
                            maxLines = 1,
                        )
                    }
                }
            },
            modifier = Modifier
                .width(RailWidth)
                .fillMaxHeight()
                .pointerInput(letters) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        down.consume()
                        // Moving the thumb only ticks and moves the bubble; `jump` is the part
                        // that costs a screenful of composition, and it happens twice at most.
                        fun select(y: Float): Int {
                            thumbY = y
                            val index = (y / railHeightPx * letters.size).toInt().coerceIn(0, letters.lastIndex)
                            if (index != selected) {
                                selected = index
                                haptics.performHapticFeedback(NulisHaptics.frequentTick)
                            }
                            return index
                        }
                        active = true
                        select(down.position.y - railTopPx)
                        drag(down.id) { change ->
                            change.consume()
                            select(change.position.y - railTopPx)
                        }
                        // On release, and only then. A tap is a drag of no distance, so it lands
                        // on the letter under the finger the instant it lifts - which is as
                        // immediate as tapping ever felt - and a drag costs exactly one jump.
                        if (selected >= 0) currentOnLetter(selected)
                        active = false
                        selected = -1
                    }
                },
        ) { measurables, constraints ->
            val n = measurables.size.coerceAtLeast(1)
            val itemPx = minOf(maxItemPx, (constraints.maxHeight - reservePx) / n).coerceAtLeast(1)
            val railPx = itemPx * n
            val top = (constraints.maxHeight - railPx) / 2
            railHeightPx = railPx.toFloat()
            railTopPx = top.toFloat()
            val placeables = measurables.map { it.measure(Constraints()) }
            layout(constraints.maxWidth, constraints.maxHeight) {
                placeables.forEachIndexed { i, p ->
                    p.place((constraints.maxWidth - p.width) / 2, top + i * itemPx + (itemPx - p.height) / 2)
                }
            }
        }

        AnimatedVisibility(
            visible = active && selected >= 0,
            enter = fadeIn(tween(NulisMotion.quick)) + scaleIn(tween(NulisMotion.quick), initialScale = 0.9f),
            exit = fadeOut(tween(NulisMotion.quick)) + scaleOut(tween(NulisMotion.quick), targetScale = 0.9f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                // Thumb geometry is read here, in the placement lambda, so a drag moves the bubble
                // without recomposing the rail.
                .offset {
                    val y = (railTopPx + thumbY - bubblePx / 2f).coerceIn(0f, (boxHeightPx - bubblePx).coerceAtLeast(0f))
                    IntOffset(0, y.roundToInt())
                }
                .padding(end = RailWidth + 16.dp),
        ) {
            Box(
                modifier = Modifier
                    .onSizeChanged { bubblePx = it.height.toFloat() }
                    .widthIn(min = BubbleSize, max = 200.dp)
                    .background(colors.surfaceRaised, NulisShapes.card)
                    .border(1.dp, colors.hairline, NulisShapes.card)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = letters.getOrNull(selected) ?: "",
                        style = NulisTheme.type.displayL.copy(fontSize = 36.sp, lineHeight = 36.sp),
                        color = colors.onBackground,
                    )
                    // The list is not moving while you drag, so the bubble says what is down there.
                    val hint = preview(selected)
                    if (!hint.isNullOrBlank()) {
                        Text(
                            text = hint,
                            style = NulisTheme.type.label,
                            color = colors.secondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

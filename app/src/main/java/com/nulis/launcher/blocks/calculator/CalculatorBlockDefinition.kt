// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.calculator

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.ScreenRequest
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.FitWidth
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * Sums, without leaving the home screen. The block is a readout; tapping it opens a full keypad.
 * Handles the four operations, powers, parentheses, percentages and a few unit conversions
 * (`12 km in miles`, `30 c in f`), all of it computed here with no library and no network.
 */
object CalculatorBlockDefinition : BlockDefinition {
    override val type = "calculator"
    override val label = "Calculator"

    override val minSpan = GridSpan(3, 1)
    override val defaultSpan = GridSpan(6, 2)
    override val styles: List<BlockStyle> = listOf(StripStyle, PadStyle)
    override val previewHeight get() = 110.dp
    override val defaultSize get() = BlockSize.WIDE

    @Composable
    override fun Screen(request: ScreenRequest, context: BlockContext, onClose: () -> Unit) =
        CalculatorScreen(onClose)

    /**
     * The display and nothing else: what you typed over what it comes to, reading zero until you
     * have typed anything. A tap opens the full calculator.
     *
     * It used to idle as the sentence "tap to work something out" under the word Calculator,
     * which is an instruction where a calculator should be - two lines of digits are what the
     * thing is, and they say "calculator" without the label having to.
     */
    private object StripStyle : BlockStyle {
        override val id = "strip"
        override val label = "Strip"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val wide = block.size == BlockSize.WIDE
            BlockColumn(modifier.open(context)) {
                BlockCaptionRow("Calculator")
                Spacer(Modifier.height(4.dp))
                Readout(input = "", result = null, compact = !wide)
            }
        }
    }

    /** The keypad in miniature, right on the page, working. */
    private object PadStyle : BlockStyle {
        override val id = "pad"
        override val label = "Keypad"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            var input by rememberSaveable(block.id) { mutableStateOf("") }
            val wide = block.size == BlockSize.WIDE
            val result = remember(input) { Expression.evaluate(input) }
            BlockColumn(modifier) {
                Readout(input, result, compact = !wide)
                Spacer(Modifier.height(8.dp))
                Keypad(
                    compact = !wide,
                    onKey = { input = apply(input, it) },
                )
            }
        }
    }

    @Composable
    private fun Modifier.open(context: BlockContext): Modifier = this
        .pressFeedback()
        .clickable(remember { MutableInteractionSource() }, indication = null) { context.openScreen(ScreenRequest(type)) }

    /** The two lines every calculator has: what was typed, and what it comes to. */
    @Composable
    private fun Readout(input: String, result: Double?, compact: Boolean) {
        val colors = NulisTheme.colors
        // BlockColumn, not Column: FitWidth sizes itself to its content, so aligning inside it
        // aligns nothing. The column around it is what puts the display against the block's own
        // edge - and without that the digits sat in the top left corner of a centred block.
        BlockColumn(Modifier.fillMaxWidth()) {
            FitWidth(align = blockAlign().horizontal) {
                Text(
                    text = input.ifEmpty { "0" },
                    style = NulisTheme.type.mono.copy(fontSize = if (compact) 18.sp else 24.sp),
                    color = if (input.isEmpty()) colors.tertiary else colors.secondary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            FitWidth(align = blockAlign().horizontal) {
                Text(
                    text = result?.let { Expression.format(it) } ?: if (input.isEmpty()) "0" else "—",
                    style = NulisTheme.type.displayM.copy(fontSize = if (compact) 30.sp else 44.sp, lineHeight = if (compact) 34.sp else 48.sp),
                    color = colors.onBackground,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

    private val Rows = listOf(
        listOf("7", "8", "9", "÷"),
        listOf("4", "5", "6", "×"),
        listOf("1", "2", "3", "−"),
        listOf(".", "0", "C", "+"),
    )

    @Composable
    private fun Keypad(compact: Boolean, onKey: (String) -> Unit) {
        val haptics = LocalHapticFeedback.current
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
            Rows.forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (compact) 4.dp else 8.dp)) {
                    row.forEach { key ->
                        Key(key, compact, Modifier.weight(1f)) {
                            haptics.performHapticFeedback(NulisHaptics.tick)
                            onKey(key)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Key(key: String, compact: Boolean, modifier: Modifier, onClick: () -> Unit) {
        val colors = NulisTheme.colors
        val interaction = remember { MutableInteractionSource() }
        val operator = key !in setOf("0", "1", "2", "3", "4", "5", "6", "7", "8", "9", ".")
        Box(
            modifier = modifier
                .pressFeedback()
                .aspectRatio(if (compact) 1.7f else 1.4f)
                .background(if (operator) colors.surfaceRaised else colors.surface, NulisShapes.tile)
                .border(1.dp, colors.hairline, NulisShapes.tile)
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = key,
                style = NulisTheme.type.mono.copy(fontSize = if (compact) 16.sp else 20.sp),
                color = if (key == "C") colors.accent else colors.onBackground,
            )
        }
    }

    /** What a key press does to the typed string. */
    private fun apply(current: String, key: String): String = when (key) {
        "C" -> if (current.isEmpty()) "" else current.dropLast(1)
        "÷" -> "$current/"
        "×" -> "$current*"
        "−" -> "$current-"
        else -> current + key
    }

    /** The full-size version, with room for the things a strip has no space for. */
    @Composable
    private fun CalculatorScreen(onClose: () -> Unit) {
        var input by rememberSaveable { mutableStateOf("") }
        val result = remember(input) { Expression.evaluate(input) }
        val colors = NulisTheme.colors
        NulisScreen(label = "Calculator", title = result?.let { Expression.format(it) } ?: "—", onBack = onClose) {
            Column(Modifier.fillMaxSize()) {
                Text(
                    text = input.ifEmpty { "Type a sum" },
                    style = NulisTheme.type.mono.copy(fontSize = 22.sp),
                    color = if (input.isEmpty()) colors.tertiary else colors.secondary,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                )
                Spacer(Modifier.weight(1f))
                SectionLabel("Also understood")
                Caption("12 km in miles · 30 c in f · 2^10 · 15% · (3+4)*2", lines = 2)
                Spacer(Modifier.height(16.dp))
                Keypad(compact = false, onKey = { input = apply(input, it) })
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

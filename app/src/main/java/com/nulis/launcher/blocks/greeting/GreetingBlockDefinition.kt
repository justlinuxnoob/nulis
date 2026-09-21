// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.greeting

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.FitWidth
import com.nulis.launcher.ui.theme.NulisTheme
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import java.util.Locale

object GreetingBlockDefinition : BlockDefinition {
    override val type = "greeting"
    override val label = "Greeting"

    override val minSpan = GridSpan(3, 1)
    override val defaultSpan = GridSpan(6, 2)
    override val styles: List<BlockStyle> = listOf(StackedStyle, LineStyle, PlainStyle)

    fun greeting(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Good night"
    }

    /** Greeting in the display face with the date as a mono caption underneath. */
    private object StackedStyle : BlockStyle {
        override val id = "stacked"
        override val label = "Stacked"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val locale = Locale.getDefault()
            val date = remember(locale) { DateTimeFormatter.ofPattern("EEEE, d MMMM", locale) }
            val wide = block.size == BlockSize.WIDE
            BlockColumn(modifier) {
                // "Good afternoon" in the display face is wider than a phone at most text scales,
                // and a greeting that reads "Good afte..." is worse than a slightly smaller one.
                // Same treatment the clock digits get.
                FitWidth(Modifier.fillMaxWidth(), align = blockAlign().horizontal) {
                    Text(
                        greeting(context.time.hour),
                        style = if (wide) NulisTheme.type.displayL else NulisTheme.type.displayM,
                        color = NulisTheme.colors.onBackground,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.height(if (wide) 8.dp else 4.dp))
                Caption(context.time.format(date))
            }
        }
    }

    /** One quiet line in the body face: "Good evening · Sunday 20 September". */
    private object LineStyle : BlockStyle {
        override val id = "line"
        override val label = "Line"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val locale = Locale.getDefault()
            val date = remember(locale) { DateTimeFormatter.ofPattern("EEEE d MMMM", locale) }
            val wide = block.size == BlockSize.WIDE
            Text(
                "${greeting(context.time.hour)} · ${context.time.format(date)}",
                style = if (wide) NulisTheme.type.bodyXl else NulisTheme.type.bodyL,
                color = NulisTheme.colors.onBackground,
                textAlign = blockAlign().textAlign,
                modifier = modifier,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    /**
     * The greeting on its own, with no date under it.
     *
     * A greeting next to a date block says the date twice, and on a page that already has one
     * the date is the half worth dropping - so this is the style a layout reaches for when the
     * page next door is already telling you what day it is.
     */
    private object PlainStyle : BlockStyle {
        override val id = "plain"
        override val label = "Plain"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            FitWidth(modifier.fillMaxWidth(), align = blockAlign().horizontal) {
                Text(
                    text = greeting(context.time.hour),
                    style = if (block.size == BlockSize.WIDE) NulisTheme.type.displayL else NulisTheme.type.displayM,
                    color = NulisTheme.colors.onBackground,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }

}

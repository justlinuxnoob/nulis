// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.week

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.blocks.glance.groupThousands
import com.nulis.launcher.blocks.glance.shortDuration
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.FitWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * The way in to the weekly summary, on a page rather than three taps down in settings.
 *
 * The block itself shows today, because today is what Nulis already has in hand; the seven-day
 * charts are one tap away and are read then rather than kept warm behind a home screen.
 */
object WeekBlockDefinition : BlockDefinition {
    override val type = "week"
    override val label = "Your week"

    override val minSpan = GridSpan(3, 1)
    override val defaultSpan = GridSpan(6, 2)
    override val styles: List<BlockStyle> = listOf(CardStyle, LineStyle)
    override val defaultSize: BlockSize get() = BlockSize.WIDE
    override val previewHeight: Dp get() = 72.dp

    override fun tapAction(block: Block, context: BlockContext): () -> Unit = { context.openWeek() }

    override fun accessibilityLabel(block: Block, context: BlockContext): String =
        "Your week. Opens screen time, steps and mindful minutes for the last seven days."

    /** Today's two numbers, with the week behind them. */
    private object CardStyle : BlockStyle {
        override val id = "card"
        override val label = "Card"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val type = NulisTheme.type
            val wide = block.size == BlockSize.WIDE
            BlockColumn(modifier.fillMaxWidth()) {
                BlockCaptionRow("Your week", trailing = "7 days")
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = blockAlign().arrangement,
                    verticalAlignment = androidx.compose.ui.Alignment.Bottom,
                ) {
                    Reading(
                        value = if (context.screenTime.granted) shortDuration(context.screenTime.totalMinutes) else "--",
                        caption = "on screen today",
                        big = wide,
                    )
                    Spacer(Modifier.width(24.dp))
                    Reading(
                        value = if (context.steps.granted) groupThousands(context.steps.today) else "--",
                        caption = "steps today",
                        big = wide,
                    )
                }
                if (!context.screenTime.granted && !context.steps.granted) {
                    Spacer(Modifier.height(6.dp))
                    Caption("Needs usage access and the step permission", lines = 2)
                }
                // Seven marks for the seven days the tap leads to. An orphaned sentence saying
                // "tap for the last seven days" told you where the tap went and showed you
                // nothing; seven ticks are the week itself, and the block is tappable anyway.
                Spacer(Modifier.height(8.dp))
                WeekTicks()
            }
        }
    }

    /** One quiet line, for a page that is already full. */
    private object LineStyle : BlockStyle {
        override val id = "line"
        override val label = "Line"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            BlockColumn(modifier.fillMaxWidth()) {
                BlockCaptionRow(
                    "Your week",
                    trailing = if (context.screenTime.granted) shortDuration(context.screenTime.totalMinutes) + " today" else "7 days",
                )
            }
        }
    }

    @Composable
    private fun Reading(value: String, caption: String, big: Boolean) {
        Column(horizontalAlignment = blockAlign().horizontal) {
            FitWidth(align = blockAlign().horizontal) {
                Text(
                    text = value,
                    style = if (big) NulisTheme.type.displayM else NulisTheme.type.displayS,
                    color = NulisTheme.colors.onBackground,
                    maxLines = 1,
                )
            }
            Caption(caption)
        }
    }

    /**
     * Seven marks, today's one filled. Not a chart - the tap leads to the chart - but enough
     * that the card reads as a week rather than as two numbers and a caption about a tap.
     */
    @Composable
    private fun WeekTicks() {
        val colors = NulisTheme.colors
        val today = java.time.LocalDate.now().dayOfWeek.value
        BlockRow(verticalAlignment = Alignment.Bottom) {
            (1..7).forEach { day ->
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .size(width = 10.dp, height = if (day == today) 14.dp else 8.dp)
                        .background(if (day == today) colors.onBackground else colors.hairline, RoundedCornerShape(2.dp)),
                )
            }
        }
    }
}

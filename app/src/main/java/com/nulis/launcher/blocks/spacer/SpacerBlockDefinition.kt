// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.spacer

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockBox
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.ui.components.Hairline
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * Deliberate emptiness: a gap, a rule or a row of dots between two groups of blocks. Small is
 * a quiet break, wide is a full one. The line and dot styles honour alignment by drawing at
 * half width and hanging from the chosen edge.
 */
object SpacerBlockDefinition : BlockDefinition {
    override val type = "spacer"
    override val label = "Spacer"

    override val minSpan = GridSpan(1, 1)
    override val defaultSpan = GridSpan(6, 1)
    override val styles: List<BlockStyle> = listOf(GapStyle, LineStyle, DotsStyle)
    override val defaultSize: BlockSize get() = BlockSize.SMALL

    private object GapStyle : BlockStyle {
        override val id = "gap"
        override val label = "Gap"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            Box(modifier.fillMaxWidth().height(if (block.size == BlockSize.WIDE) 56.dp else 24.dp))
        }
    }

    private object LineStyle : BlockStyle {
        override val id = "line"
        override val label = "Line"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val wide = block.size == BlockSize.WIDE
            BlockBox(modifier) {
                Hairline(Modifier.fillMaxWidth(if (wide) 1f else 0.4f))
            }
        }
    }

    private object DotsStyle : BlockStyle {
        override val id = "dots"
        override val label = "Dots"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val color = NulisTheme.colors.tertiary
            val count = if (block.size == BlockSize.WIDE) 7 else 3
            val align = blockAlign()
            BlockBox(modifier) {
                Canvas(Modifier.fillMaxWidth(0.35f).height(12.dp)) {
                    val r = 1.6.dp.toPx()
                    val step = 10.dp.toPx()
                    val span = step * (count - 1)
                    val start = when (align) {
                        com.nulis.launcher.blocks.BlockAlign.LEFT -> r
                        com.nulis.launcher.blocks.BlockAlign.RIGHT -> size.width - span - r
                        else -> (size.width - span) / 2f
                    }
                    repeat(count) { drawCircle(color, r, Offset(start + it * step, size.height / 2f)) }
                }
            }
        }
    }
}

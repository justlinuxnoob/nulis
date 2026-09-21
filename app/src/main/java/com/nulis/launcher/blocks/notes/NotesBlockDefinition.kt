// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.notes

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.WritingLines
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.blocks.ScreenRequest
import com.nulis.launcher.blocks.writing.Note
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisCard
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisTheme

object NotesBlockDefinition : BlockDefinition {
    override val type = "notes"
    override val label = "Notes"

    override val minSpan = GridSpan(2, 2)
    override val defaultSpan = GridSpan(6, 3)
    override val styles: List<BlockStyle> = listOf(ListStyle, TilesStyle)

    @Composable
    override fun Screen(request: ScreenRequest, context: BlockContext, onClose: () -> Unit) =
        NotesScreen(initialNoteId = request.arg, context = context, onClose = onClose)

    /** Note titles stacked, pinned ones marked with a dot. Tap a note to open it, the label to see all. */
    private object ListStyle : BlockStyle {
        override val id = "list"
        override val label = "List"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val notes = context.writing.notes.take(if (wide) 5 else 3)
            BlockColumn(modifier) {
                Header("Notes", context.writing.notes.size, context)
                if (notes.isEmpty()) Hint(context)
                notes.forEach { note ->
                    BlockRow(
                        modifier = Modifier
                            .pressFeedback()
                            .clickable(remember { MutableInteractionSource() }, indication = null) { context.openScreen(ScreenRequest(type, note.id)) }
                            .padding(vertical = if (wide) 8.dp else 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (note.pinned) {
                            Box(Modifier.size(5.dp).background(colors.secondary, CircleShape))
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(
                            note.title.ifEmpty { "Empty note" },
                            style = if (wide) NulisTheme.type.bodyXl.copy(fontSize = NulisTheme.type.bodyL.fontSize * 1.3f, lineHeight = NulisTheme.type.bodyL.lineHeight * 1.3f) else NulisTheme.type.bodyL,
                            color = if (note.title.isEmpty()) colors.tertiary else colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    /** Small cards, two per row, with the title and a line of text. */
    private object TilesStyle : BlockStyle {
        override val id = "tiles"
        override val label = "Tiles"

        @OptIn(ExperimentalLayoutApi::class)
        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val notes = context.writing.notes.take(if (wide) 4 else 2)
            BlockColumn(modifier) {
                Header("Notes", context.writing.notes.size, context)
                if (notes.isEmpty()) Hint(context)
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    maxItemsInEachRow = 2,
                ) {
                    notes.forEach { note ->
                        NulisCard(
                            modifier = Modifier.weight(1f),
                            contentPadding = 12.dp,
                            onClick = { context.openScreen(ScreenRequest(type, note.id)) },
                        ) {
                            Column(Modifier.height(if (wide) 88.dp else 64.dp)) {
                                Text(
                                    note.title.ifEmpty { "Empty note" },
                                    style = NulisTheme.type.bodyM,
                                    color = if (note.title.isEmpty()) colors.tertiary else colors.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    note.text.lineSequence().drop(1).joinToString(" ").trim(),
                                    style = NulisTheme.type.bodyS,
                                    color = colors.secondary,
                                    maxLines = if (wide) 3 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun Header(label: String, count: Int, context: BlockContext) {
        BlockCaptionRow(
            label = label,
            trailing = if (count > 0) count.toString() else null,
            modifier = Modifier
                .pressFeedback()
                .clickable(remember { MutableInteractionSource() }, indication = null) { context.openScreen(ScreenRequest(type)) }
                .padding(bottom = 6.dp),
        )
    }

    /** No notes yet: a blank page, and tapping it starts one. */
    @Composable
    private fun Hint(context: BlockContext) {
        Box(
            Modifier
                .fillMaxWidth()
                .pressFeedback()
                .clickable(remember { MutableInteractionSource() }, indication = null) { context.openScreen(ScreenRequest(type, NEW)) }
                .padding(vertical = 8.dp),
        ) {
            WritingLines(lines = 3)
        }
    }

    /** Request arg meaning "open a fresh note". */
    const val NEW = "new"
}

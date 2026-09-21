// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.journal

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
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
import com.nulis.launcher.blocks.writing.formatStamp
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.Hairline
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.NulisTextField
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.components.fadeTop
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisTheme

object JournalBlockDefinition : BlockDefinition {
    override val type = "journal"
    override val label = "Journal"

    override val minSpan = GridSpan(2, 1)
    override val defaultSpan = GridSpan(6, 2)
    override val styles: List<BlockStyle> = listOf(LatestStyle, TimelineStyle)

    @Composable
    override fun Screen(request: ScreenRequest, context: BlockContext, onClose: () -> Unit) = JournalScreen(context, onClose)

    /** The newest entry with its time stamp. Tap anywhere to open the journal. */
    private object LatestStyle : BlockStyle {
        override val id = "latest"
        override val label = "Latest"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val latest = context.writing.journal.firstOrNull()
            BlockColumn(modifier.open(context)) {
                BlockCaptionRow("Journal", trailing = latest?.let { formatStamp(it.createdAt, context.time) })
                Spacer(Modifier.height(6.dp))
                // Nothing written yet looks like a page waiting to be written on, not like a
                // sentence telling you to write on it.
                if (latest == null) {
                    WritingLines(lines = if (wide) 3 else 2)
                } else {
                    Text(
                        latest.text,
                        style = if (wide) NulisTheme.type.bodyXl.copy(fontSize = NulisTheme.type.bodyL.fontSize * 1.3f, lineHeight = NulisTheme.type.bodyL.lineHeight * 1.3f) else NulisTheme.type.bodyL,
                        color = colors.onBackground,
                        textAlign = blockAlign().textAlign,
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = if (wide) 4 else 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    /** The last few entries, each with its stamp, separated by hairlines. */
    private object TimelineStyle : BlockStyle {
        override val id = "timeline"
        override val label = "Timeline"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val entries = context.writing.journal.take(if (wide) 4 else 3)
            BlockColumn(modifier.open(context)) {
                BlockCaptionRow("Journal")
                if (entries.isEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    WritingLines(lines = if (wide) 3 else 2)
                }
                entries.forEachIndexed { index, entry ->
                    if (index > 0) Hairline()
                    BlockRow(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Text(formatStamp(entry.createdAt, context.time).uppercase(), style = NulisTheme.type.label, color = colors.tertiary, modifier = Modifier.width(if (wide) 96.dp else 56.dp).padding(top = 3.dp), maxLines = 2)
                        Spacer(Modifier.width(8.dp))
                        Text(entry.text, style = NulisTheme.type.bodyM, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }

    @Composable
    private fun Modifier.open(context: BlockContext): Modifier = this
        .pressFeedback()
        .clickable(remember { MutableInteractionSource() }, indication = null) { context.openScreen(ScreenRequest(type)) }
}

/** Quick entry field on top, every entry below with its stamp; the x removes one. */
@Composable
private fun JournalScreen(context: BlockContext, onClose: () -> Unit) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    var draft by remember { mutableStateOf("") }
    fun submit() {
        if (draft.isBlank()) return
        haptics.performHapticFeedback(NulisHaptics.confirm)
        context.writingActions.addJournalEntry(draft.trim())
        draft = ""
    }
    NulisScreen(label = "Journal", title = formatStamp(System.currentTimeMillis(), context.time), onBack = onClose) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NulisTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = "What's on your mind",
                modifier = Modifier.weight(1f),
                imeAction = ImeAction.Send,
                onImeAction = { submit() },
            )
            Spacer(Modifier.width(12.dp))
            PillButton(text = "Add", onClick = { submit() }, tone = PillTone.Primary, compact = true, enabled = draft.isNotBlank())
        }
        Spacer(Modifier.height(8.dp))
        Hairline()
        LazyColumn(Modifier.fillMaxSize().fadeTop()) {
            items(context.writing.journal, key = { it.id }) { entry ->
                Column(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Caption(formatStamp(entry.createdAt, context.time))
                        Spacer(Modifier.weight(1f))
                        NulisIconButton(Glyph.Close, onClick = { context.writingActions.deleteJournalEntry(entry.id) })
                    }
                    Text(entry.text, style = NulisTheme.type.bodyL, color = colors.onBackground, modifier = Modifier.padding(bottom = 14.dp))
                    Hairline()
                }
            }
        }
    }
}

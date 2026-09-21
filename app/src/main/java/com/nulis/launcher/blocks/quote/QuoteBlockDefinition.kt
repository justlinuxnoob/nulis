// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.quote

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.WritingLines
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.NulisTextField
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * A line worth reading, changing once a day. Your own lines come first if you have added any;
 * otherwise a small bundled set of public-domain quotations. A tap moves to the next one.
 */
object QuoteBlockDefinition : BlockDefinition {
    override val type = "quote"
    override val label = "Quote"

    override val minSpan = GridSpan(2, 1)
    override val defaultSpan = GridSpan(6, 2)
    override val styles: List<BlockStyle> = listOf(QuietStyle, CardStyle, TickerStyle)
    override val previewHeight get() = 96.dp

    private const val KEY_MINE = "mine"
    private const val KEY_ONLY_MINE = "only_mine"
    private const val ROW = "\u001F"
    private const val FIELD = "\u001E"

    fun mine(block: Block): List<Quote> = block.settings[KEY_MINE]
        ?.split(ROW)
        ?.filter { it.isNotBlank() }
        ?.map { row ->
            val parts = row.split(FIELD)
            Quote(parts.getOrNull(0).orEmpty(), parts.getOrNull(1).orEmpty())
        }
        .orEmpty()

    fun withMine(block: Block, quotes: List<Quote>): Block = block.copy(
        settings = block.settings + (KEY_MINE to quotes.joinToString(ROW) { "${it.text}$FIELD${it.author}" }),
    )

    private fun onlyMine(block: Block): Boolean = block.settings[KEY_ONLY_MINE] == "1"

    /** Your own lines, plus the bundled ones unless you asked for only yours. */
    private fun pool(block: Block): List<Quote> {
        val ownQuotes = mine(block)
        return when {
            ownQuotes.isEmpty() -> BundledQuotes
            onlyMine(block) -> ownQuotes
            else -> ownQuotes + BundledQuotes
        }
    }

    override val hasOptions: Boolean get() = true

    @Composable
    override fun Options(block: Block, context: BlockContext, onUpdate: (Block) -> Unit) {
        val own = mine(block)
        var text by remember { mutableStateOf("") }
        var author by remember { mutableStateOf("") }
        Column(Modifier.fillMaxWidth()) {
            SectionLabel("Your own lines")
            own.forEachIndexed { index, quote ->
                ListRow(
                    title = quote.text,
                    subtitle = quote.author.ifBlank { "No attribution" },
                    divider = index != own.lastIndex,
                    trailing = { NulisIconButton(Glyph.Close, onClick = { onUpdate(withMine(block, own - quote)) }) },
                )
            }
            if (own.isEmpty()) Caption("None yet; the bundled set is in use")
            Spacer(Modifier.height(12.dp))
            NulisTextField(value = text, onValueChange = { text = it }, placeholder = "A line", modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                NulisTextField(value = author, onValueChange = { author = it }, placeholder = "Who said it", modifier = Modifier.weight(1f), singleLine = true)
                Spacer(Modifier.width(12.dp))
                PillButton(
                    text = "Add",
                    tone = PillTone.Primary,
                    compact = true,
                    enabled = text.isNotBlank(),
                    onClick = {
                        onUpdate(withMine(block, own + Quote(text.trim(), author.trim())))
                        text = ""
                        author = ""
                    },
                )
            }
            if (own.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                ListRow(
                    title = "Only my lines",
                    subtitle = "Leave the bundled quotations out",
                    onClick = { onUpdate(block.copy(settings = block.settings + (KEY_ONLY_MINE to if (onlyMine(block)) "0" else "1"))) },
                    trailing = {
                        NulisToggle(
                            checked = onlyMine(block),
                            onCheckedChange = { onUpdate(block.copy(settings = block.settings + (KEY_ONLY_MINE to if (it) "1" else "0"))) },
                        )
                    },
                    divider = false,
                )
            }
        }
    }

    /** The line alone, in the display face, at the size of something worth stopping for. */
    private object QuietStyle : BlockStyle {
        override val id = "quiet"
        override val label = "Quiet"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            Shown(block, context, modifier) { quote, wide ->
                Text(
                    text = quote.text,
                    style = NulisTheme.type.displayS.copy(
                        fontSize = if (wide) 24.sp else 17.sp,
                        lineHeight = if (wide) 32.sp else 23.sp,
                    ),
                    color = NulisTheme.colors.onBackground,
                    textAlign = blockAlign().textAlign,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    /** The line in italics with the name underneath, the way a book sets an epigraph. */
    private object CardStyle : BlockStyle {
        override val id = "card"
        override val label = "Epigraph"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            Shown(block, context, modifier) { quote, wide ->
                Column {
                    Text(
                        text = "“${quote.text}”",
                        style = NulisTheme.type.bodyL.copy(
                            fontStyle = FontStyle.Italic,
                            fontSize = if (wide) 19.sp else 15.sp,
                            lineHeight = if (wide) 27.sp else 21.sp,
                        ),
                        color = NulisTheme.colors.onBackground,
                        textAlign = blockAlign().textAlign,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (quote.author.isNotBlank()) {
                        Spacer(Modifier.height(8.dp))
                        BlockCaptionRow(quote.author)
                    }
                }
            }
        }
    }

    /** One short line in the label face; for a page that has room for almost nothing. */
    private object TickerStyle : BlockStyle {
        override val id = "ticker"
        override val label = "Ticker"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            Shown(block, context, modifier) { quote, wide ->
                Text(
                    text = NulisTheme.type.labelCase(quote.text),
                    style = if (wide) NulisTheme.type.labelL else NulisTheme.type.label,
                    color = NulisTheme.colors.secondary,
                    textAlign = blockAlign().textAlign,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    /**
     * The shared body: today's quote, a tap for the next one, and an honest empty state for the
     * only way this block can be empty - a user who turned the bundled set off and then deleted
     * their own lines.
     */
    @Composable
    private fun Shown(
        block: Block,
        context: BlockContext,
        modifier: Modifier,
        content: @Composable (Quote, Boolean) -> Unit,
    ) {
        val haptics = LocalHapticFeedback.current
        var shuffle by remember(block.id) { mutableIntStateOf(0) }
        val quotes = pool(block)
        val quote = quoteFor(context.time.toLocalDate(), quotes, shuffle)
        val wide = block.size == BlockSize.WIDE
        BlockColumn(
            modifier
                .pressFeedback()
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    haptics.performHapticFeedback(NulisHaptics.tick)
                    shuffle++
                },
            verticalArrangement = Arrangement.Top,
        ) {
            if (quote == null) {
                // The shape of a line of writing rather than an instruction about how to add
                // one - and the instruction was wrong anyway: a long press opens the editor.
                WritingLines(lines = 2)
            } else {
                content(quote, wide)
            }
        }
    }
}

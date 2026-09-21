// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.countdown

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.WritingLines
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.FitWidth
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisDatePicker
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.NulisTextField
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.theme.NulisShapes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One date the user is counting towards. Stored inside the block, so two blocks never collide. */
data class Countdown(val label: String, val date: LocalDate) {
    fun daysFrom(today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)
}

/**
 * Days until something. A block holds as many dates as the user likes; the Next style shows
 * only the soonest one that has not passed, the List style shows them all.
 */
object CountdownBlockDefinition : BlockDefinition {
    override val type = "countdown"
    override val label = "Countdown"

    override val minSpan = GridSpan(2, 1)
    override val defaultSpan = GridSpan(6, 2)
    override val styles: List<BlockStyle> = listOf(NextStyle, ListStyle, BarStyle)
    override val previewHeight get() = 100.dp

    override fun accessibilityLabel(block: Block, context: BlockContext): String {
        val next = items(block).firstOrNull { it.daysFrom(context.time.toLocalDate()) >= 0 }
            ?: return "Countdown, nothing set"
        val days = next.daysFrom(context.time.toLocalDate())
        return when (days) {
            0L -> "${next.label} is today"
            1L -> "${next.label} is tomorrow"
            else -> "$days days until ${next.label}"
        }
    }

    private const val KEY_ITEMS = "items"

    // ASCII unit and record separators: neither can be typed into a label on a phone keyboard.
    private const val ROW = "\u001F"
    private const val FIELD = "\u001E"

    fun items(block: Block): List<Countdown> = block.settings[KEY_ITEMS]
        ?.split(ROW)
        ?.filter { it.isNotBlank() }
        ?.mapNotNull { row ->
            val parts = row.split(FIELD)
            val date = parts.getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            Countdown(parts.getOrNull(0).orEmpty(), date)
        }
        ?.sortedBy { it.date }
        .orEmpty()

    fun withItems(block: Block, items: List<Countdown>): Block = block.copy(
        settings = block.settings + (KEY_ITEMS to items.sortedBy { it.date }.joinToString(ROW) { "${it.label}$FIELD${it.date}" }),
    )

    /** Previews get a date far enough away to look like a real one. */
    override fun previewSettings(context: BlockContext): Map<String, String> =
        withItems(Block("", type, "", settings = emptyMap()), listOf(Countdown("Midsummer", LocalDate.now().plusDays(37)))).settings

    override val hasOptions: Boolean get() = true

    @Composable
    override fun Options(block: Block, context: BlockContext, onUpdate: (Block) -> Unit) {
        val current = items(block)
        var adding by remember { mutableStateOf(false) }
        var draftLabel by remember { mutableStateOf("") }
        var draftDate by remember { mutableStateOf(LocalDate.now().plusDays(30)) }
        Column(Modifier.fillMaxWidth()) {
            current.forEachIndexed { index, item ->
                ListRow(
                    title = item.label.ifBlank { "Untitled" },
                    subtitle = item.date.toString(),
                    divider = index != current.lastIndex || adding,
                    trailing = {
                        NulisIconButton(Glyph.Close, onClick = { onUpdate(withItems(block, current - item)) })
                    },
                )
            }
            if (current.isEmpty() && !adding) Caption("No dates yet")
            Spacer(Modifier.height(12.dp))
            if (adding) {
                NulisTextField(
                    value = draftLabel,
                    onValueChange = { draftLabel = it },
                    placeholder = "What is it",
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Spacer(Modifier.height(12.dp))
                NulisDatePicker(selected = draftDate, onSelect = { draftDate = it })
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(text = "Cancel", onClick = { adding = false }, modifier = Modifier.weight(1f))
                    PillButton(
                        text = "Add",
                        tone = PillTone.Primary,
                        enabled = draftLabel.isNotBlank(),
                        onClick = {
                            onUpdate(withItems(block, current + Countdown(draftLabel.trim(), draftDate)))
                            draftLabel = ""
                            adding = false
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                PillButton(
                    text = "Add a date",
                    onClick = { adding = true },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    /** The soonest date still ahead, as a number of days that cannot be misread. */
    private object NextStyle : BlockStyle {
        override val id = "next"
        override val label = "Next"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val today = context.time.toLocalDate()
            val next = items(block).firstOrNull { it.daysFrom(today) >= 0 }
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            if (next == null) {
                Empty(block, context, modifier)
                return
            }
            val days = next.daysFrom(today)
            BlockColumn(modifier) {
                FitWidth(align = blockAlign().horizontal) {
                    Text(
                        text = when (days) {
                            0L -> "Today"
                            1L -> "Tomorrow"
                            else -> days.toString()
                        },
                        style = (if (wide) NulisTheme.type.displayXl else NulisTheme.type.displayL).let {
                            it.copy(fontSize = if (wide) 76.sp else 46.sp, lineHeight = if (wide) 76.sp else 46.sp)
                        },
                        color = colors.onBackground,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (days > 1) "days until ${next.label}" else next.label,
                    style = if (wide) NulisTheme.type.bodyL else NulisTheme.type.bodyM,
                    color = colors.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    /** Every date in the block, soonest first, with the days beside each. */
    private object ListStyle : BlockStyle {
        override val id = "list"
        override val label = "List"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val today = context.time.toLocalDate()
            val all = items(block)
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            if (all.isEmpty()) {
                Empty(block, context, modifier)
                return
            }
            BlockColumn(modifier) {
                BlockCaptionRow("Counting down", trailing = all.size.toString())
                Spacer(Modifier.height(4.dp))
                all.take(if (wide) 5 else 3).forEach { item ->
                    val days = item.daysFrom(today)
                    BlockRow(Modifier.padding(vertical = if (wide) 6.dp else 4.dp)) {
                        Text(
                            text = item.label.ifBlank { "Untitled" },
                            style = if (wide) NulisTheme.type.bodyL else NulisTheme.type.bodyM,
                            color = if (days < 0) colors.tertiary else colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(12.dp))
                        Caption(
                            when {
                                days < 0 -> "passed"
                                days == 0L -> "today"
                                days == 1L -> "tomorrow"
                                else -> "$days days"
                            },
                        )
                    }
                }
            }
        }
    }

    /** A bar that fills as the date approaches, from the day the countdown was added. */
    private object BarStyle : BlockStyle {
        override val id = "bar"
        override val label = "Bar"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val today = context.time.toLocalDate()
            val next = items(block).firstOrNull { it.daysFrom(today) >= 0 }
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            if (next == null) {
                Empty(block, context, modifier)
                return
            }
            val locale = Locale.getDefault()
            val formatter = remember(locale) { DateTimeFormatter.ofPattern("d MMM", locale) }
            val days = next.daysFrom(today)
            // A year out is a full empty bar; anything nearer fills from there.
            val fraction = (1f - (days / 365f)).coerceIn(0f, 1f)
            BlockColumn(modifier) {
                BlockCaptionRow(next.label.ifBlank { "Untitled" }, trailing = next.date.format(formatter))
                Spacer(Modifier.height(8.dp))
                Box(
                    Modifier
                        .fillMaxWidth(if (wide) 1f else 0.6f)
                        .height(if (wide) 10.dp else 6.dp)
                        .background(colors.surfaceRaised, NulisShapes.pill),
                    contentAlignment = blockAlign().box,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fraction)
                            .height(if (wide) 10.dp else 6.dp)
                            .background(colors.onBackground, NulisShapes.pill),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Caption(
                    when (days) {
                        0L -> "today"
                        1L -> "tomorrow"
                        else -> "$days days to go"
                    },
                )
            }
        }
    }

    /**
     * No date set: a countdown reading nothing, which is what a countdown with nothing to count
     * to looks like. Tapping it opens the block's options, where the date lives.
     */
    @Composable
    private fun Empty(block: Block, context: BlockContext, modifier: Modifier) {
        val colors = NulisTheme.colors
        BlockColumn(
            modifier
                .pressFeedback()
                .clickable(remember { MutableInteractionSource() }, indication = null) { context.openBlockOptions(block) },
        ) {
            BlockCaptionRow("Countdown")
            Spacer(Modifier.height(2.dp))
            FitWidth(align = blockAlign().horizontal) {
                Text(
                    text = "—",
                    style = NulisTheme.type.displayL,
                    color = colors.tertiary,
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Spacer(Modifier.height(4.dp))
            WritingLines(lines = 1)
        }
    }
}

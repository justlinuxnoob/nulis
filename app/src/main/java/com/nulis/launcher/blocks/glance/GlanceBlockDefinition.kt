// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.glance

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.FitWidth
import com.nulis.launcher.ui.components.Hairline
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.SegmentedPills
import com.nulis.launcher.ui.theme.NulisShapes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import com.nulis.launcher.blocks.GhostPills
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * One line that answers the four or five questions a phone gets picked up for, so nobody has to
 * stack five blocks to learn the time, the date and whether they need a charger.
 *
 * Every reading comes from a block Nulis already has, through the same permissions those blocks
 * ask for; a slot with nothing to say takes no room at all rather than showing a dash.
 */
object GlanceBlockDefinition : BlockDefinition {
    override val type = "glance"
    override val label = "Glance"

    override val minSpan = GridSpan(3, 1)
    override val defaultSpan = GridSpan(6, 1)
    override val styles: List<BlockStyle> = listOf(LineStyle, PillsStyle, ColumnsStyle, HeadlineStyle)
    override val defaultSize: BlockSize get() = BlockSize.WIDE
    override val previewHeight: Dp get() = 64.dp
    override val hasOptions: Boolean get() = true

    override fun defaultSettings(): Map<String, String> = GlanceSettings().toMap()

    override fun accessibilityLabel(block: Block, context: BlockContext): String {
        val readings = GlanceSettings.from(block).readings(context)
        if (readings.isEmpty()) return "Glance, nothing to show"
        return readings.joinToString(", ") { "${it.caption} ${it.value}" }
    }

    @Composable
    override fun Options(block: Block, context: BlockContext, onUpdate: (Block) -> Unit) {
        val settings = GlanceSettings.from(block)
        SectionLabel("Hours")
        SegmentedPills(
            options = listOf(true, false),
            selected = settings.hour24,
            label = { if (it) "24 h" else "12 h" },
            onSelect = { onUpdate(block.copy(settings = block.settings + settings.copy(hour24 = it).toMap())) },
        )
        Spacer(Modifier.height(8.dp))
        Caption("Turned on in the order you tap them, which is the order they are shown in.", lines = 2)
        Spacer(Modifier.height(4.dp))
        GlanceSlot.entries.forEach { slot ->
            val on = slot in settings.slots
            val toggle = {
                val slots = if (on) settings.slots - slot else settings.slots + slot
                onUpdate(block.copy(settings = block.settings + settings.copy(slots = slots).toMap()))
            }
            ListRow(
                title = slot.label,
                subtitle = subtitleFor(slot, context),
                onClick = toggle,
                trailing = { NulisToggle(checked = on, onCheckedChange = { toggle() }) },
                divider = slot != GlanceSlot.entries.last(),
            )
        }
    }

    /** Says why a slot would show nothing, so an empty row is never a mystery. */
    private fun subtitleFor(slot: GlanceSlot, context: BlockContext): String? = when (slot) {
        GlanceSlot.STEPS -> if (context.steps.granted) null else "Needs the step permission"
        GlanceSlot.SCREEN_TIME -> if (context.screenTime.granted) null else "Needs usage access"
        GlanceSlot.MUSIC -> if (context.music.granted) "Only while something is playing" else "Needs media access"
        GlanceSlot.ALARM -> "Only when an alarm is set"
        else -> null
    }

    /** Readings joined by a middot on a single line, shrunk rather than wrapped. */
    private object LineStyle : BlockStyle {
        override val id = "line"
        override val label = "Line"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val readings = GlanceSettings.from(block).readings(context)
            val align = blockAlign()
            if (readings.isEmpty()) {
                Empty(block, context, modifier)
                return
            }
            FitWidth(modifier.fillMaxWidth(), align = align.horizontal) {
                Text(
                    text = readings.joinToString("   ·   ") { it.inline },
                    style = if (block.size == BlockSize.WIDE) NulisTheme.type.bodyL else NulisTheme.type.bodyM,
                    color = NulisTheme.colors.onBackground,
                    maxLines = 1,
                )
            }
        }
    }

    /** Each reading in its own hairline pill, wrapping onto a second row when it has to. */
    private object PillsStyle : BlockStyle {
        override val id = "pills"
        override val label = "Pills"

        @OptIn(ExperimentalLayoutApi::class)
        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val readings = GlanceSettings.from(block).readings(context)
            val colors = NulisTheme.colors
            val align = blockAlign()
            if (readings.isEmpty()) {
                Empty(block, context, modifier)
                return
            }
            FlowRow(
                modifier.fillMaxWidth(),
                horizontalArrangement = align.arrangement,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                readings.forEach { reading ->
                    Box(
                        Modifier
                            .padding(end = 8.dp)
                            .border(1.dp, colors.hairline, NulisShapes.pill)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = reading.inline,
                            style = NulisTheme.type.bodyM,
                            color = colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }

    /** A readout strip: each value over the word that names it, spread between two hairlines. */
    private object ColumnsStyle : BlockStyle {
        override val id = "columns"
        override val label = "Columns"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val readings = GlanceSettings.from(block).readings(context)
            if (readings.isEmpty()) {
                Empty(block, context, modifier)
                return
            }
            BlockColumn(modifier.fillMaxWidth()) {
                Hairline()
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    readings.forEach { reading ->
                        androidx.compose.foundation.layout.Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            FitWidth(align = Alignment.CenterHorizontally) {
                                Text(
                                    text = reading.value,
                                    style = NulisTheme.type.displayS,
                                    color = NulisTheme.colors.onBackground,
                                    maxLines = 1,
                                )
                            }
                            Spacer(Modifier.height(2.dp))
                            Caption(reading.caption)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Hairline()
            }
        }
    }

    /** The first slot said properly, with the rest of them as a quiet line underneath. */
    private object HeadlineStyle : BlockStyle {
        override val id = "headline"
        override val label = "Headline"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val readings = GlanceSettings.from(block).readings(context)
            val head = readings.firstOrNull()
            val rest = readings.drop(1)
            if (head == null) {
                Empty(block, context, modifier)
                return
            }
            BlockColumn(modifier.fillMaxWidth()) {
                FitWidth(align = blockAlign().horizontal) {
                    Text(
                        text = head.value,
                        style = if (block.size == BlockSize.WIDE) NulisTheme.type.displayL else NulisTheme.type.displayM,
                        color = NulisTheme.colors.onBackground,
                        maxLines = 1,
                    )
                }
                if (rest.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    BlockRow {
                        Caption(rest.joinToString("   ·   ") { it.inline })
                    }
                }
            }
        }
    }

    /**
     * Nothing chosen yet: the empty slots the readings will sit in, and a tap that opens the
     * list of them. The old line said to long-press, which has opened the editor since the night
     * the editor became the page.
     */
    @Composable
    private fun Empty(block: Block, context: BlockContext, modifier: Modifier) {
        BlockColumn(
            modifier
                .fillMaxWidth()
                .pressFeedback()
                .clickable(remember { MutableInteractionSource() }, indication = null) { context.openBlockOptions(block) },
        ) {
            GhostPills(count = 3)
        }
    }
}

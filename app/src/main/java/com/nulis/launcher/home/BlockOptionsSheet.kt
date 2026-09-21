// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nulis.launcher.R
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockActions
import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisBottomSheet
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.bleed
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * What a block looks like and what it is set to: every style and every alignment as a live
 * preview of this very block, then whatever settings the block type has of its own.
 *
 * Where it sits and how big it is are not here - those are the page itself now, answered by
 * dragging the block and pulling its corner. This sheet is only for the decisions a drag cannot
 * make.
 */
@Composable
fun BlockOptionsSheet(
    block: Block,
    definition: BlockDefinition,
    layout: PageLayout,
    context: BlockContext,
    actions: BlockActions,
    onDismiss: () -> Unit,
) {
    val colors = NulisTheme.colors
    val index = layout.reading.indexOfFirst { it.id == block.id }
    val count = layout.blocks.size
    val effectiveAlign = layout.alignOf(block)
    NulisBottomSheet(onDismiss = onDismiss) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(definition.label, style = NulisTheme.type.displayM, color = colors.onBackground, modifier = Modifier.weight(1f))
            Caption("Block ${index + 1} / $count")
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel(stringResource(R.string.options_style))
        Spacer(Modifier.height(4.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth().bleed(NulisSpacing.screenMargin),
            contentPadding = PaddingValues(horizontal = NulisSpacing.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(definition.styles, key = { it.id }) { style ->
                BlockPreviewCard(
                    block = block.copy(style = style.id),
                    context = context,
                    label = style.label,
                    selected = style.id == block.style,
                    align = effectiveAlign,
                    previewHeight = definition.previewHeight,
                    onClick = { actions.update(block.copy(style = style.id)) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel(stringResource(R.string.options_align))
        Spacer(Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BlockAlign.entries.forEach { align ->
                BlockPreviewCard(
                    block = block,
                    context = context,
                    label = align.label,
                    selected = align == effectiveAlign,
                    align = align,
                    // Matching the page default is stored as "follow the page", so changing the
                    // page's alignment later still carries this block with it.
                    onClick = { actions.update(block.copy(align = if (align == layout.align) null else align)) },
                    width = null,
                    previewHeight = (definition.previewHeight * 0.8f),
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (definition.hasOptions) {
            SectionLabel(stringResource(R.string.options_settings))
            Spacer(Modifier.height(8.dp))
            definition.Options(block = block, context = context, onUpdate = actions::update)
            Spacer(Modifier.height(16.dp))
        }

    }
}

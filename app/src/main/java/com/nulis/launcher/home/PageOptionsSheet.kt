// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nulis.launcher.R
import com.nulis.launcher.blocks.BlockActions
import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.PageDensity
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisBottomSheet
import com.nulis.launcher.ui.components.NulisCard
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * How the whole page is laid out: which edge blocks hang from, where the stack sits vertically
 * and how much air there is between blocks. Every choice is a live miniature of this very page,
 * so the answer to "what does Airy look like" is on screen before it is applied.
 */
@Composable
fun PageOptionsSheet(
    layout: PageLayout,
    title: String,
    context: BlockContext,
    actions: BlockActions,
    onDismiss: () -> Unit,
) {
    val colors = NulisTheme.colors
    NulisBottomSheet(onDismiss = onDismiss) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Text(title, style = NulisTheme.type.displayM, color = colors.onBackground, modifier = Modifier.weight(1f))
            Caption(pluralStringResource(R.plurals.page_options_blocks, layout.blocks.size, layout.blocks.size))
        }
        Spacer(Modifier.height(16.dp))

        SectionLabel(stringResource(R.string.page_options_align))
        Spacer(Modifier.height(4.dp))
        ChoiceRow(
            options = BlockAlign.entries,
            selected = layout.align,
            label = { it.label },
            preview = { layout.copy(align = it) },
            context = context,
            onSelect = { choice -> actions.updatePage { it.copy(align = choice) } },
        )
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.page_options_density))
        Spacer(Modifier.height(4.dp))
        ChoiceRow(
            options = PageDensity.entries,
            selected = layout.density,
            label = { it.label },
            preview = { layout.copy(density = it) },
            context = context,
            onSelect = { choice -> actions.updatePage { it.copy(density = choice) } },
        )
        Spacer(Modifier.height(8.dp))
        Caption(stringResource(R.string.page_options_hint))
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    preview: (T) -> PageLayout,
    context: BlockContext,
    onSelect: (T) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            Column(Modifier.weight(1f)) {
                NulisCard(
                    modifier = Modifier.fillMaxWidth(),
                    selected = isSelected,
                    shape = NulisShapes.tile,
                    contentPadding = 8.dp,
                    onClick = {
                        if (!isSelected) {
                            haptics.performHapticFeedback(NulisHaptics.tick)
                            onSelect(option)
                        }
                    },
                ) {
                    PageMiniature(preview(option), context, Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                Caption(label(option))
            }
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.LocalBlockAlign
import androidx.compose.runtime.CompositionLocalProvider
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisCard
import com.nulis.launcher.ui.components.ScaledPreview
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes

/** A card that renders [block] live at small scale, with a mono label underneath. */
@Composable
fun BlockPreviewCard(
    block: Block,
    context: BlockContext,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp? = 148.dp,
    previewHeight: Dp = 80.dp,
    contentWidth: Dp = 330.dp,
    align: BlockAlign = BlockAlign.LEFT,
) {
    val haptics = LocalHapticFeedback.current
    val definition = BlockRegistry.definition(block.type)
    val pick = {
        if (!selected) haptics.performHapticFeedback(NulisHaptics.tick)
        onClick()
    }
    // The label under the card is part of the choice, so it has to be part of the target: tapping
    // the word "Countdown" and having nothing happen is the kind of small lie that makes a picker
    // feel broken. The card keeps its own handler for the pressed state; this one catches the
    // caption and the gap above it.
    val cell = (if (width != null) modifier.width(width) else modifier.fillMaxWidth())
        .clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = pick,
        )
    Column(modifier = cell) {
        NulisCard(
            modifier = Modifier.fillMaxWidth(),
            selected = selected,
            shape = NulisShapes.tile,
            contentPadding = 12.dp,
            onClick = pick,
        ) {
            Box(Modifier.fillMaxWidth().height(previewHeight), contentAlignment = Alignment.CenterStart) {
                ScaledPreview(modifier = Modifier.fillMaxWidth(), contentWidth = contentWidth) {
                    CompositionLocalProvider(LocalBlockAlign provides align) {
                        definition?.style(block)?.Render(block = block, context = context, modifier = Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Caption(label)
    }
}

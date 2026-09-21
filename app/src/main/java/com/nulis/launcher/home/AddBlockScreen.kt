// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nulis.launcher.R
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.dotGrid
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme

/** Two-column grid of live block previews. Tapping one adds it to the page. */
@Composable
fun AddBlockScreen(
    context: BlockContext,
    onAdd: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onClose)
    val colors = NulisTheme.colors
    val previews = remember(context.apps, context.homeApps) {
        BlockRegistry.definitions.map { definition ->
            val block = BlockRegistry.newBlock(definition.type)
            definition to block.copy(settings = definition.previewSettings(context))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .dotGrid()
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = NulisSpacing.screenMargin),
    ) {
        Spacer(Modifier.height(8.dp))
        SectionLabel(stringResource(R.string.add_label), withLine = false)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.add_title),
                style = NulisTheme.type.displayM,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            NulisIconButton(Glyph.Close, onClick = onClose, bordered = true)
        }
        Spacer(Modifier.height(16.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            items(previews, key = { it.first.type }) { (definition, block) ->
                BlockPreviewCard(
                    block = block,
                    context = context,
                    label = definition.label,
                    selected = false,
                    onClick = { onAdd(definition.type) },
                    width = null,
                    previewHeight = definition.previewHeight.coerceAtLeast(112.dp),
                )
            }
        }
    }
}

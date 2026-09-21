// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.nulis.launcher.R
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.icons.IconStyle
import com.nulis.launcher.icons.LocalIconLoader
import com.nulis.launcher.icons.MaxIconPx
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.NulisTextField
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme

/** Gives one app a name of the user's own. An empty field puts the app's own name back. */
@Composable
fun RenameAppScreen(app: AppInfo, onRename: (String?) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier) {
    var text by remember(app.id) { mutableStateOf(app.label) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(app.id) { focusRequester.requestFocus() }
    val colors = NulisTheme.colors
    NulisScreen(
        label = stringResource(R.string.rename_label),
        title = app.label,
        onBack = onClose,
        modifier = modifier.background(colors.background),
    ) {
        NulisTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = app.packageName,
            singleLine = true,
            imeAction = ImeAction.Done,
            onImeAction = { onRename(text); onClose() },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
        )
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                text = stringResource(R.string.rename_save),
                tone = PillTone.Primary,
                onClick = { onRename(text); onClose() },
            )
            PillButton(
                text = stringResource(R.string.rename_reset),
                onClick = { onRename(null); onClose() },
            )
        }
    }
}

/**
 * Every drawable the active icon pack names, as a grid. Picking one pins it to this app; the
 * first cell puts the app's normal icon back.
 */
@Composable
fun IconPickerScreen(
    app: AppInfo,
    style: IconStyle,
    onPick: (String?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val loader = LocalIconLoader.current
    val pack = loader.pack
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    var query by remember { mutableStateOf("") }
    val names = remember(pack, query) {
        val all = pack?.drawableNames.orEmpty()
        if (query.isBlank()) all else all.filter { it.contains(query.trim(), ignoreCase = true) }
    }
    NulisScreen(
        label = stringResource(R.string.choose_icon_label),
        title = app.label,
        onBack = onClose,
        modifier = modifier.background(colors.background),
    ) {
        NulisTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = stringResource(R.string.choose_icon_search),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            PillButton(text = stringResource(R.string.menu_reset_icon), compact = true, onClick = { onPick(null); onClose() })
            Spacer(Modifier.width(12.dp))
            Caption("${names.size}")
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(72.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(names, key = { it }) { name ->
                PackIconCell(name, style) {
                    haptics.performHapticFeedback(NulisHaptics.confirm)
                    onPick(name)
                    onClose()
                }
            }
        }
    }
}

/** One pack drawable, loaded only once its cell is on screen. */
@Composable
private fun PackIconCell(name: String, style: IconStyle, onClick: () -> Unit) {
    val loader = LocalIconLoader.current
    val density = LocalDensity.current
    val px = remember(density) { with(density) { 56.dp.roundToPx() }.coerceAtMost(MaxIconPx) }
    var icon by remember(name, px) { mutableStateOf(loader.cachedPackDrawable(name, px)) }
    LaunchedEffect(name, px) { if (icon == null) icon = loader.loadPackDrawable(name, px) }
    val colors = NulisTheme.colors
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .pressFeedback()
            .clip(NulisShapes.tile)
            .background(colors.surface)
            .border(1.dp, colors.hairline, NulisShapes.tile)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        icon?.let {
            androidx.compose.foundation.Image(
                bitmap = it.image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(0.68f),
            )
        }
    }
}

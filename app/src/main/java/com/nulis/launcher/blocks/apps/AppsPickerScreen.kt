// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.apps

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nulis.launcher.R
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.icons.AppGlyph
import com.nulis.launcher.icons.IconStyle
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.fadeTop
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme

/** Full-screen list of all apps with toggles to pick up to [maxFavorites] favorites. Saves on every change. */
@Composable
fun AppsPickerScreen(
    apps: List<AppInfo>,
    favoriteIds: Set<String>,
    maxFavorites: Int,
    /** Drawn beside each name, so the picker shows the apps as the block will. */
    iconStyle: IconStyle,
    onToggle: (AppInfo) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onDone)

    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    val isFull = favoriteIds.size >= maxFavorites

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = NulisSpacing.screenMargin),
    ) {
        Spacer(Modifier.height(8.dp))
        SectionLabel(stringResource(R.string.picker_label), withLine = false)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${favoriteIds.size} / $maxFavorites",
                style = NulisTheme.type.displayM,
                color = colors.onBackground,
                modifier = Modifier.weight(1f),
            )
            PillButton(
                text = stringResource(R.string.picker_done),
                onClick = { haptics.performHapticFeedback(NulisHaptics.confirm); onDone() },
                tone = PillTone.Primary,
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize().fadeTop()) {
            items(apps, key = { it.id }) { app ->
                val checked = app.id in favoriteIds
                val enabled = checked || !isFull
                ListRow(
                    title = app.label,
                    enabled = enabled,
                    onClick = { onToggle(app) },
                    leading = if (iconStyle.mode.hasGlyph) ({ AppGlyph(app, iconStyle) }) else null,
                    trailing = { NulisToggle(checked = checked, enabled = enabled, onCheckedChange = { onToggle(app) }) },
                )
            }
        }
    }
}

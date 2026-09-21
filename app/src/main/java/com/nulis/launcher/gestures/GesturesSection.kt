// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.gestures

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nulis.launcher.R
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.icons.AppGlyph
import com.nulis.launcher.icons.IconStyle
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisBottomSheet
import com.nulis.launcher.ui.components.NulisCard
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.fadeTop
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * The Gestures section of settings: one row per trigger, each acting out its gesture in a tiny
 * looping glyph, with the action it currently runs as a mono label on the right. Tapping a row
 * asks the owner to open the picker; the sheet itself lives at the top of the settings screen so
 * it can cover it.
 */
@Composable
fun ColumnScope.GesturesSection(
    /** False when the caller has already drawn the heading (so it can mark it for search). */
    showLabel: Boolean = true,
    gestures: GestureSettings,
    apps: List<AppInfo>,
    onPick: (GestureTrigger) -> Unit,
    onReset: () -> Unit,
) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    val phase = rememberGesturePhase()
    if (showLabel) SectionLabel(stringResource(R.string.settings_gestures))
    GestureTrigger.entries.forEach { trigger ->
        val binding = gestures[trigger]
        ListRow(
            title = trigger.label,
            subtitle = if (gestures.isEscapeHatch(trigger)) stringResource(R.string.settings_gestures_escape) else trigger.hint,
            onClick = { onPick(trigger) },
            leading = { GestureGlyph(trigger, phase) },
            trailing = { Caption(binding.describe(apps)) },
        )
    }
    ListRow(
        title = stringResource(R.string.settings_gestures_reset),
        titleColor = colors.danger,
        onClick = {
            haptics.performHapticFeedback(NulisHaptics.confirm)
            onReset()
        },
        divider = false,
    )
}

/** What the row reads on the right: the action, or the app it opens. */
fun GestureBinding.describe(apps: List<AppInfo>): String = when {
    action.needsApp -> apps.firstOrNull { it.id == appId }?.label ?: action.label
    else -> action.label
}

/**
 * Picks the action for one trigger. The fourteen actions are laid out as a two-column grid under
 * four headings, so the whole map is taken in at a glance instead of read as a fourteen-row list.
 * "Open an app" hands over to [GestureAppPicker].
 */
@Composable
fun GestureActionSheet(
    trigger: GestureTrigger,
    binding: GestureBinding,
    apps: List<AppInfo>,
    escapeHatch: Boolean,
    onBinding: (GestureBinding) -> Unit,
    onPickApp: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    NulisBottomSheet(onDismiss = onDismiss) {
        Text(trigger.label, style = NulisTheme.type.displayM, color = colors.onBackground)
        Spacer(Modifier.height(4.dp))
        Caption(if (escapeHatch) stringResource(R.string.settings_gestures_escape) else trigger.hint)
        Spacer(Modifier.height(16.dp))

        val choose: (GestureAction) -> Unit = { action ->
            haptics.performHapticFeedback(NulisHaptics.tick)
            if (action.needsApp) {
                onPickApp()
            } else {
                onBinding(GestureBinding(action))
                dismiss()
            }
        }
        val tile: @Composable (GestureAction, Modifier) -> Unit = { action, tileModifier ->
            ActionTile(
                action = action,
                selected = action == binding.action,
                subtitle = when {
                    action.fullVersion -> stringResource(R.string.settings_gestures_full_version)
                    action.needsApp -> apps.firstOrNull { it.id == binding.appId }?.label
                    else -> null
                },
                onClick = { choose(action) },
                modifier = tileModifier,
            )
        }
        GestureGroup.entries.filter { it != GestureGroup.NONE }.forEach { group ->
            SectionLabel(group.label)
            Spacer(Modifier.height(4.dp))
            // A plain chunked grid, not a lazy one: the sheet already scrolls.
            GestureAction.entries.filter { it.group == group }.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { action -> tile(action, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        GestureAction.entries.filter { it.group == GestureGroup.NONE }.forEach { action ->
            tile(action, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** One action in the grid: its name, an optional note, and the accent dot when it is the current one. */
@Composable
private fun ActionTile(
    action: GestureAction,
    selected: Boolean,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NulisTheme.colors
    NulisCard(
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
        selected = selected,
        shape = NulisShapes.tile,
        contentPadding = 12.dp,
        onClick = if (action.fullVersion) null else onClick,
    ) {
        Column(Modifier.align(Alignment.CenterStart).alpha(if (action.fullVersion) 0.4f else 1f)) {
            Text(action.label, style = NulisTheme.type.bodyM, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Spacer(Modifier.height(2.dp))
                Caption(subtitle)
            }
        }
    }
}

/** Full-screen single-choice app list for the "Open an app" action. */
@Composable
fun GestureAppPicker(
    trigger: GestureTrigger,
    apps: List<AppInfo>,
    iconStyle: IconStyle,
    selectedId: String?,
    onSelect: (AppInfo) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    BackHandler(onBack = onClose)
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = NulisSpacing.screenMargin),
    ) {
        Spacer(Modifier.height(8.dp))
        SectionLabel(trigger.label, withLine = false)
        Text(
            text = stringResource(R.string.settings_gestures_choose_app),
            style = NulisTheme.type.displayM,
            color = colors.onBackground,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )
        LazyColumn(Modifier.fillMaxSize().fadeTop()) {
            items(apps, key = { it.id }) { app ->
                ListRow(
                    title = app.label,
                    leading = if (iconStyle.mode.hasGlyph) ({ AppGlyph(app, iconStyle) }) else null,
                    onClick = {
                        haptics.performHapticFeedback(NulisHaptics.confirm)
                        onSelect(app)
                    },
                    trailing = {
                        if (app.id == selectedId) Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
                    },
                )
            }
        }
    }
}

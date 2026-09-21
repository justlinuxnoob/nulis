// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.wellbeing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.blocks.screentime.formatMinutes
import com.nulis.launcher.icons.AppGlyph
import com.nulis.launcher.icons.IconStyle
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.NulisSlider
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.SegmentedPills
import com.nulis.launcher.ui.components.fadeTop
import com.nulis.launcher.ui.theme.NulisTheme
import kotlin.math.roundToInt

/** What the wellbeing screen can change. */
class WellbeingActions(
    val onPaused: (String, Boolean) -> Unit,
    val onPauseSeconds: (Int) -> Unit,
    val onLimit: (String, Int) -> Unit,
    val onFocusAllowed: (String, Boolean) -> Unit,
    val onFocusHides: (Boolean) -> Unit,
    val onFocusGuards: (Boolean) -> Unit,
    val onWeeklySummary: () -> Unit,
)

private enum class Tab(val label: String) { PAUSE("Pause"), LIMITS("Limits"), FOCUS("Focus") }

/**
 * Three lists over the same app list: which apps get a breath first, which have a daily number
 * you would like to be reminded of, and which are allowed through during a focus session.
 *
 * Every one of these is a nudge. Nulis has no Accessibility Service and no way to intercept an
 * app opened from anywhere else; all of this only applies to a launch that starts here.
 */
@Composable
fun WellbeingScreen(
    apps: List<AppInfo>,
    iconStyle: IconStyle,
    wellbeing: Wellbeing,
    minutesToday: Map<String, Int>,
    usageGranted: Boolean,
    actions: WellbeingActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(Tab.PAUSE) }
    val launchable = remember(apps) { apps.filterNot { it.isNulisSettings }.distinctBy { it.packageName } }

    Box(modifier.fillMaxSize().background(NulisTheme.colors.background)) {
        NulisScreen(label = "Wellbeing", title = tab.label, onBack = onClose) {
            SegmentedPills(
                options = Tab.entries,
                selected = tab,
                label = { it.label },
                onSelect = { tab = it },
            )
            Spacer(Modifier.height(16.dp))
            when (tab) {
                Tab.PAUSE -> {
                    Caption("A breath before these apps open. Only from Nulis; nothing is blocked.", lines = 2)
                    Spacer(Modifier.height(12.dp))
                    NulisSlider(
                        label = "How long",
                        value = (wellbeing.pauseSeconds - 1) / 29f,
                        onValueChange = { actions.onPauseSeconds((1 + it * 29).roundToInt()) },
                        readout = "${wellbeing.pauseSeconds}s",
                    )
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("Apps", trailing = "${wellbeing.pausedPackages.size}")
                    LazyColumn(Modifier.fillMaxSize().fadeTop()) {
                        items(launchable, key = { it.id }) { app ->
                            val on = wellbeing.pauses(app.packageName)
                            ListRow(
                                title = app.label,
                                subtitle = minutesToday[app.packageName]?.takeIf { it > 0 }?.let { "${formatMinutes(it)} today" },
                                leading = if (iconStyle.mode.hasGlyph) ({ AppGlyph(app, iconStyle) }) else null,
                                onClick = { actions.onPaused(app.packageName, !on) },
                                trailing = { NulisToggle(checked = on, onCheckedChange = { actions.onPaused(app.packageName, it) }) },
                            )
                        }
                    }
                }
                Tab.LIMITS -> {
                    if (!usageGranted) {
                        Caption("Limits need usage access, which the screen time block asks for.", lines = 2)
                        Spacer(Modifier.height(12.dp))
                    }
                    Caption("A number you would like to be reminded of, not a lock.", lines = 2)
                    Spacer(Modifier.height(8.dp))
                    SectionLabel("Daily limits", trailing = "${wellbeing.limits.size}")
                    LazyColumn(Modifier.fillMaxSize().fadeTop()) {
                        items(launchable, key = { it.id }) { app ->
                            LimitRow(
                                app = app,
                                iconStyle = iconStyle,
                                limit = wellbeing.limitFor(app.packageName),
                                minutesToday = minutesToday[app.packageName] ?: 0,
                                onLimit = { actions.onLimit(app.packageName, it) },
                            )
                        }
                    }
                }
                Tab.FOCUS -> {
                    ListRow(
                        title = "Guard app launches",
                        subtitle = "During a focus session, ask before other apps",
                        onClick = { actions.onFocusGuards(!wellbeing.focusGuards) },
                        trailing = { NulisToggle(checked = wellbeing.focusGuards, onCheckedChange = actions.onFocusGuards) },
                    )
                    ListRow(
                        title = "Hide them instead of fading",
                        subtitle = "On the home page and in the drawer",
                        enabled = wellbeing.focusGuards,
                        onClick = { actions.onFocusHides(!wellbeing.focusHides) },
                        trailing = {
                            NulisToggle(
                                checked = wellbeing.focusHides,
                                enabled = wellbeing.focusGuards,
                                onCheckedChange = actions.onFocusHides,
                            )
                        },
                        divider = false,
                    )
                    Spacer(Modifier.height(12.dp))
                    SectionLabel("Always allowed", trailing = "${wellbeing.focusAllowed.size}")
                    LazyColumn(Modifier.fillMaxSize().fadeTop()) {
                        items(launchable, key = { it.id }) { app ->
                            val on = app.packageName in wellbeing.focusAllowed
                            ListRow(
                                title = app.label,
                                leading = if (iconStyle.mode.hasGlyph) ({ AppGlyph(app, iconStyle) }) else null,
                                onClick = { actions.onFocusAllowed(app.packageName, !on) },
                                trailing = { NulisToggle(checked = on, onCheckedChange = { actions.onFocusAllowed(app.packageName, it) }) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One app with a limit that can be stepped through a handful of sensible values. */
@Composable
private fun LimitRow(
    app: AppInfo,
    iconStyle: IconStyle,
    limit: Int?,
    minutesToday: Int,
    onLimit: (Int) -> Unit,
) {
    val colors = NulisTheme.colors
    var expanded by remember(app.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        ListRow(
            title = app.label,
            subtitle = when {
                limit == null && minutesToday > 0 -> "${formatMinutes(minutesToday)} today"
                limit == null -> null
                minutesToday >= limit -> "${formatMinutes(minutesToday)} today, past $limit min"
                else -> "${formatMinutes(minutesToday)} today of $limit min"
            },
            titleColor = if (limit != null && minutesToday >= limit) colors.danger else null,
            leading = if (iconStyle.mode.hasGlyph) ({ AppGlyph(app, iconStyle) }) else null,
            onClick = { expanded = !expanded },
            trailing = { Caption(limit?.let { "$it min" } ?: "None") },
            divider = !expanded,
        )
        if (expanded) {
            Spacer(Modifier.height(8.dp))
            SegmentedPills(
                options = LimitChoices,
                selected = limit ?: 0,
                label = { if (it == 0) "None" else "$it min" },
                onSelect = { onLimit(it) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
        }
    }
}

private val LimitChoices = listOf(0, 10, 20, 30, 45, 60, 90, 120)

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.backup

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Hairline
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisBottomSheet
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.theme.NulisTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** What the backup screen can do, wired to the view model by the route. */
class BackupActions(
    val onExport: (android.net.Uri) -> Unit,
    val onInspect: (android.net.Uri) -> Unit,
    val onRestore: () -> Unit,
    val onDiscard: () -> Unit,
    val onReset: () -> Unit,
    val suggestedName: () -> String,
)

/** What the view model is doing, so the screen can say so rather than sitting there. */
sealed interface BackupStatus {
    data object Idle : BackupStatus
    data object Working : BackupStatus
    data class Exported(val name: String) : BackupStatus
    data class Loaded(val preview: BackupPreview) : BackupStatus
    data class Restored(val preview: BackupPreview) : BackupStatus
    data class Problem(val message: String) : BackupStatus
}

/**
 * Export everything to one file, put one back, or start again.
 *
 * The file goes wherever the system picker puts it; Nulis has no internet permission and no way
 * to send it anywhere. Importing shows what is in the file before it changes anything.
 */
@Composable
fun BackupScreen(
    status: BackupStatus,
    actions: BackupActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NulisTheme.colors
    var confirmReset by remember { mutableStateOf(false) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let(actions.onExport)
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(actions.onInspect)
    }

    Box(modifier.fillMaxSize().background(colors.background)) {
        NulisScreen(label = "Backup", title = "Everything", onBack = onClose) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Text(
                    text = "One file with your pages, blocks, themes, settings, gestures, app names, " +
                        "categories, notes, journal, tasks and focus log in it. Plain JSON: you can open it " +
                        "and read it.",
                    style = NulisTheme.type.bodyM,
                    color = colors.secondary,
                )
                Spacer(Modifier.height(20.dp))
                PillButton(
                    text = "Export to a file",
                    tone = PillTone.Primary,
                    enabled = status != BackupStatus.Working,
                    onClick = { exporter.launch(actions.suggestedName()) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                PillButton(
                    text = "Import from a file",
                    enabled = status != BackupStatus.Working,
                    onClick = { importer.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                )

                when (status) {
                    BackupStatus.Working -> {
                        Spacer(Modifier.height(16.dp))
                        Caption("Working…")
                    }
                    is BackupStatus.Exported -> {
                        Spacer(Modifier.height(16.dp))
                        Caption("Saved as ${status.name}")
                    }
                    is BackupStatus.Restored -> {
                        Spacer(Modifier.height(16.dp))
                        Caption("Restored ${status.preview.blocks} blocks and ${status.preview.notes} notes")
                    }
                    is BackupStatus.Problem -> {
                        Spacer(Modifier.height(16.dp))
                        Text(status.message, style = NulisTheme.type.bodyM, color = colors.danger)
                    }
                    else -> Unit
                }

                Spacer(Modifier.height(32.dp))
                Hairline()
                Spacer(Modifier.height(16.dp))
                SectionLabel("Start again")
                Text(
                    text = "Puts Nulis back the way it came: default pages, default look, no themes of " +
                        "your own, no categories, no gestures, and none of your writing. There is no undo.",
                    style = NulisTheme.type.bodyM,
                    color = colors.secondary,
                )
                Spacer(Modifier.height(16.dp))
                PillButton(
                    text = if (confirmReset) "Yes, reset everything" else "Reset Nulis",
                    tone = PillTone.Danger,
                    onClick = {
                        if (confirmReset) {
                            actions.onReset()
                            confirmReset = false
                        } else {
                            confirmReset = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (confirmReset) {
                    Spacer(Modifier.height(8.dp))
                    Caption("Export first if you are not sure", lines = 2)
                    Spacer(Modifier.height(8.dp))
                    PillButton(text = "Cancel", onClick = { confirmReset = false }, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(40.dp))
            }
        }

        if (status is BackupStatus.Loaded) {
            PreviewSheet(
                preview = status.preview,
                onRestore = actions.onRestore,
                onDismiss = actions.onDiscard,
            )
        }
    }
}

/** What the file holds, before anything is changed. */
@Composable
private fun PreviewSheet(preview: BackupPreview, onRestore: () -> Unit, onDismiss: () -> Unit) {
    val colors = NulisTheme.colors
    NulisBottomSheet(onDismiss = onDismiss) {
        val close = ::dismiss
        Text("Restore this?", style = NulisTheme.type.displayM, color = colors.onBackground)
        Spacer(Modifier.height(4.dp))
        Caption(fileLine(preview), lines = 2)
        Spacer(Modifier.height(16.dp))
        SectionLabel("What it will replace")
        Line("Pages", "${preview.pages} with ${preview.blocks} blocks", preview.pages > 0)
        Line("Setups you saved", "${preview.setups}", preview.setups > 0)
        Line("Appearance settings", if (preview.hasPreferences) "yes" else "not in this file", preview.hasPreferences)
        Line("Gestures", "${preview.gestures} set", preview.gestures > 0)
        Line("Renamed apps", "${preview.renamedApps}", preview.renamedApps > 0)
        Line("Hidden apps", "${preview.hiddenApps}", preview.hiddenApps > 0)
        Line("Categories", "${preview.categories}", preview.categories > 0)
        Line("Notes", "${preview.notes}", preview.notes > 0)
        Line("Journal entries", "${preview.journal}", preview.journal > 0)
        Line("Tasks", "${preview.tasks}", preview.tasks > 0)
        Line("Focus sessions", "${preview.focusSessions}", preview.focusSessions > 0)
        Spacer(Modifier.height(12.dp))
        Caption("Anything not in the file is left exactly as it is now", lines = 2)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(text = "Cancel", onClick = close, modifier = Modifier.weight(1f))
            PillButton(
                text = "Restore",
                tone = PillTone.Primary,
                onClick = {
                    onRestore()
                    close()
                },
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Line(title: String, value: String, present: Boolean) {
    ListRow(
        title = title,
        enabled = present,
        trailing = { Caption(value) },
        divider = true,
    )
}

private fun fileLine(preview: BackupPreview): String {
    if (preview.createdAt <= 0L) return "No date in the file"
    val when_ = Instant.ofEpochMilli(preview.createdAt)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))
    return if (preview.appVersion.isBlank()) "Written $when_" else "Written $when_ by Nulis ${preview.appVersion}"
}

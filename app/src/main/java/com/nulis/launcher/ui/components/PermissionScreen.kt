// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * The explanation shown before any permission is requested: what Nulis wants, what it does
 * with it, what it never does. "Allow" continues to the system prompt; "Not now" simply closes.
 */
@Composable
fun PermissionScreen(
    title: String,
    explanation: String,
    points: List<String>,
    onAllow: () -> Unit,
    onNotNow: () -> Unit,
    allowLabel: String = "Allow",
) {
    val colors = NulisTheme.colors
    NulisScreen(label = "Permission", title = title, onBack = onNotNow) {
        Text(explanation, style = NulisTheme.type.bodyL, color = colors.onBackground)
        Spacer(Modifier.height(24.dp))
        Column(Modifier.fillMaxWidth()) {
            points.forEachIndexed { index, point ->
                if (index > 0) Hairline()
                Text(point, style = NulisTheme.type.bodyM, color = colors.secondary, modifier = Modifier.padding(vertical = 12.dp))
            }
        }
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            PillButton(text = allowLabel, onClick = onAllow, tone = PillTone.Primary, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            PillButton(text = "Not now", onClick = onNotNow, modifier = Modifier.weight(1f))
        }
    }
}

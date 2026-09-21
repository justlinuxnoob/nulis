// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.NulisTheme

/** Small uppercase mono label with a hairline running to the right edge, like a panel legend. */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    withLine: Boolean = true,
) {
    val colors = NulisTheme.colors
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(NulisTheme.type.labelCase(text), style = NulisTheme.type.label, color = colors.secondary, maxLines = 1)
        if (withLine) {
            Spacer(Modifier.width(12.dp))
            Hairline(Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(NulisTheme.type.labelCase(trailing), style = NulisTheme.type.label, color = colors.tertiary, maxLines = 1)
        }
    }
}

/**
 * Mono caption in the tertiary color, e.g. a count or a hint. One line by default, because most
 * captions sit beside something; pass [lines] for the few that are a whole sentence.
 */
@Composable
fun Caption(text: String, modifier: Modifier = Modifier, lines: Int = 1) {
    Text(
        text = NulisTheme.type.labelCase(text),
        style = NulisTheme.type.label,
        color = NulisTheme.colors.tertiary,
        modifier = modifier,
        maxLines = lines,
        overflow = TextOverflow.Ellipsis,
    )
}

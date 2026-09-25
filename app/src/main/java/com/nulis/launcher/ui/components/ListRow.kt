// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.NulisTheme

/** 56dp list row with optional leading/trailing slots and a hairline underneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    /**
     * A subtitle is a sentence, not a name, so it wraps rather than losing its last words to an
     * ellipsis. Two lines is enough for every string Nulis ships and keeps the row from becoming
     * a paragraph; pass 1 where a row must stay exactly 56dp tall.
     */
    subtitleLines: Int = Int.MAX_VALUE,
    titleColor: Color? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    divider: Boolean = true,
) {
    val colors = NulisTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val interactive = onClick != null || onLongClick != null
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .pressFeedback(enabled = enabled && interactive)
                .defaultMinSize(minHeight = 56.dp)
                .then(
                    if (interactive) {
                        Modifier.combinedClickableCompat(interaction, enabled, onClick, onLongClick)
                    } else {
                        Modifier
                    },
                )
                .alpha(if (enabled) 1f else 0.4f)
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = NulisTheme.type.bodyL,
                    color = titleColor ?: colors.onBackground,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = NulisTheme.type.bodyS,
                        color = colors.secondary,
                        maxLines = subtitleLines,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing()
            }
        }
        if (divider) Hairline()
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(
    interaction: MutableInteractionSource,
    enabled: Boolean,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
): Modifier = if (onLongClick != null) {
    combinedClickable(
        interactionSource = interaction,
        indication = null,
        enabled = enabled,
        onLongClick = onLongClick,
        onClick = onClick ?: {},
    )
} else {
    clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick ?: {})
}

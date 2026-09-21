// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nulis.launcher.R
import com.nulis.launcher.blocks.PagesConfig
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisBottomSheet
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * What a page says when it has run out of room.
 *
 * A page is exactly one screen, so "it does not fit" is a real answer rather than a bug - and an
 * answer is only useful with a way out attached. There are two: make something on this page
 * smaller, or start another page. The second one is offered only while there is a page left to
 * make, and when there is not, the sheet says so rather than showing a dead button.
 */
@Composable
fun NoRoomSheet(
    /** What could not be placed: a block's name, or null when a block is being grown instead. */
    blockName: String?,
    pages: PagesConfig,
    onShrink: () -> Unit,
    onNewPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    NulisBottomSheet(onDismiss = onDismiss) {
        val dismissSheet = ::dismiss
        SectionLabel(stringResource(R.string.no_room_label), withLine = false)
        Text(
            text = if (blockName == null) {
                stringResource(R.string.no_room_grow_title)
            } else {
                stringResource(R.string.no_room_title, blockName)
            },
            style = NulisTheme.type.displayM,
            color = NulisTheme.colors.onBackground,
        )
        Spacer(Modifier.height(12.dp))
        Caption(
            if (pages.canAdd) stringResource(R.string.no_room_body) else stringResource(R.string.no_room_body_full, PagesConfig.MAX),
            lines = 4,
        )
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                text = stringResource(R.string.no_room_shrink),
                onClick = { dismissSheet(); onShrink() },
                modifier = Modifier.weight(1f),
            )
            if (pages.canAdd) {
                PillButton(
                    text = stringResource(R.string.no_room_new_page),
                    onClick = { dismissSheet(); onNewPage() },
                    tone = PillTone.Primary,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

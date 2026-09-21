// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.home.PageMiniature
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisBottomSheet
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * The layout picker: one near-full-height live preview per layout, swiped through like the pages
 * themselves. Every preview is the real home page drawn with this phone's own data - its clock,
 * its battery, its apps - in whatever Look and colours are already set, because a layout never
 * decides those and the picker should not pretend otherwise.
 */
@Composable
fun LayoutPickerScreen(
    context: BlockContext,
    currentId: String?,
    /** How many pages this phone has, so the picker knows when it is about to remove some. */
    pageCount: Int,
    onApply: (preset: LayoutPreset, keepExtraPages: Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val presets = LayoutPresets.all
    val haptics = LocalHapticFeedback.current
    // A layout with fewer pages than the phone has is about to delete the difference. That is
    // what a layout does, and it is not something to do to somebody's pages quietly.
    var confirming by remember { mutableStateOf<LayoutPreset?>(null) }
    val start = remember(currentId) { presets.indexOfFirst { it.id == currentId }.coerceAtLeast(0) }
    val pager = rememberPagerState(initialPage = start) { presets.size }
    // A tick as each one comes into view, the same one a segmented control gives.
    LaunchedEffect(pager.settledPage) { haptics.performHapticFeedback(NulisHaptics.tick) }

    Box(modifier.fillMaxSize().background(NulisTheme.colors.background)) {
        NulisScreen(label = "Arrangement only", title = "Layouts", onBack = onClose) {
            Caption(
                "Which blocks are on which page, and how they sit. Colours and type stay as you set them.",
                lines = 2,
            )
            Spacer(Modifier.height(12.dp))
            HorizontalPager(
                state = pager,
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 28.dp),
                pageSpacing = 12.dp,
            ) { index ->
                PageMiniature(
                    layout = presets[index].page(PageIds.HOME),
                    context = context,
                    sample = true,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.height(16.dp))
            val preset = presets[pager.currentPage]
            Text(
                text = preset.name,
                style = NulisTheme.type.displayM,
                color = NulisTheme.colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = preset.tagline,
                style = NulisTheme.type.bodyM,
                color = NulisTheme.colors.secondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                presets.forEachIndexed { index, _ ->
                    Box(
                        Modifier
                            .size(if (index == pager.currentPage) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (index == pager.currentPage) NulisTheme.colors.onBackground else NulisTheme.colors.hairline),
                    )
                    if (index != presets.lastIndex) Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            val losesPages = pageCount > preset.pages.size
            PillButton(
                text = if (preset.id == currentId) "Use it again" else "Use this layout",
                tone = PillTone.Primary,
                onClick = { if (losesPages) confirming = preset else onApply(preset, false) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            // A layout says how many pages there are as well as what is on them, so this has to
            // say so: applying one on a phone with five pages can leave three.
            Caption(
                if (losesPages) {
                    "This layout has " + preset.pages.size + (if (preset.pages.size == 1) " page" else " pages") +
                        " and you have " + pageCount + ". It will ask what to do with the rest."
                } else {
                    "Replaces your pages with this layout's " + preset.pages.size +
                        ". Your notes, journal and tasks are not touched."
                },
                lines = 2,
            )
            Spacer(Modifier.height(16.dp))
        }

        confirming?.let { preset ->
            ExtraPagesSheet(
                preset = preset,
                pageCount = pageCount,
                onReplaceAll = { confirming = null; onApply(preset, false) },
                onKeepExtra = { confirming = null; onApply(preset, true) },
                onDismiss = { confirming = null },
            )
        }
    }
}

/**
 * The one question a layout has to ask: this arrangement is shorter than your phone, so are the
 * pages it does not mention going away or staying on the end?
 */
@Composable
private fun ExtraPagesSheet(
    preset: LayoutPreset,
    pageCount: Int,
    onReplaceAll: () -> Unit,
    onKeepExtra: () -> Unit,
    onDismiss: () -> Unit,
) {
    val extra = pageCount - preset.pages.size
    NulisBottomSheet(onDismiss = onDismiss) {
        Text(
            text = preset.name,
            style = NulisTheme.type.displayM,
            color = NulisTheme.colors.onBackground,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "This layout has " + preset.pages.size + " page" + (if (preset.pages.size == 1) "" else "s") +
                ", and you have " + pageCount + ". What should happen to the other " +
                (if (extra == 1) "one?" else "$extra?"),
            style = NulisTheme.type.bodyM,
            color = NulisTheme.colors.secondary,
        )
        Spacer(Modifier.height(20.dp))
        PillButton(
            text = "Keep my extra pages",
            tone = PillTone.Primary,
            onClick = onKeepExtra,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Caption("They follow the layout's pages, with their blocks as they are", lines = 2)
        Spacer(Modifier.height(20.dp))
        PillButton(
            text = "Replace all pages",
            tone = PillTone.Danger,
            onClick = onReplaceAll,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Caption("The blocks on those pages are removed", lines = 2)
        Spacer(Modifier.height(20.dp))
    }
}

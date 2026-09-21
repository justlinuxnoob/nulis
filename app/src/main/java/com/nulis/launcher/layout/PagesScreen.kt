// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.layout

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nulis.launcher.R
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.PagesConfig
import com.nulis.launcher.blocks.nameOf
import com.nulis.launcher.home.PageMiniature
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisTheme

/** What the pages screen can do. Implemented by the view model. */
interface PageActions {
    fun addPage(after: String?)
    fun removePage(pageId: String)
    fun movePage(pageId: String, delta: Int)
    fun setHomePage(pageId: String)
}

/**
 * Every page, in swipe order, as a row of live miniatures.
 *
 * Home is a mark rather than a position: whichever page carries it is the one the Home key comes
 * back to and the one the launcher opens on. Everything else here is order - left and right are
 * only ever "before home" and "after home".
 */
@Composable
fun PagesScreen(
    pages: PagesConfig,
    layouts: Map<String, PageLayout?>,
    context: BlockContext,
    actions: PageActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    // Removing a page takes its blocks with it and there is no undo for that, so a page with
    // anything on it asks once. An empty page goes without a word - there is nothing to lose.
    var confirming by remember { mutableStateOf<String?>(null) }
    NulisScreen(
        label = stringResource(R.string.pages_label),
        title = stringResource(R.string.pages_title),
        onBack = onClose,
        modifier = modifier,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            pages.ids.forEachIndexed { index, pageId ->
                val layout = layouts[pageId]
                val isHome = pageId == pages.homeId
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Box(Modifier.width(96.dp)) {
                        if (layout != null) {
                            PageMiniature(layout, context, Modifier.fillMaxWidth().height(180.dp))
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = pages.nameOf(pageId),
                            style = NulisTheme.type.displayS,
                            color = NulisTheme.colors.onBackground,
                        )
                        Spacer(Modifier.height(2.dp))
                        Caption(
                            if (isHome) {
                                stringResource(R.string.pages_is_home)
                            } else {
                                val count = layout?.blocks?.size ?: 0
                                androidx.compose.ui.res.pluralStringResource(R.plurals.page_options_blocks, count, count)
                            },
                            lines = 2,
                        )
                        Spacer(Modifier.height(12.dp))
                        // Order, then the home mark, then removal - left to right in the order
                        // somebody reaches for them, with the destructive one last and quiet.
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            NulisIconButton(
                                Glyph.ChevronLeft,
                                onClick = { haptics.performHapticFeedback(NulisHaptics.tick); actions.movePage(pageId, -1) },
                                enabled = index > 0,
                            )
                            NulisIconButton(
                                Glyph.ChevronRight,
                                onClick = { haptics.performHapticFeedback(NulisHaptics.tick); actions.movePage(pageId, +1) },
                                enabled = index < pages.ids.size - 1,
                            )
                            Spacer(Modifier.width(4.dp))
                            if (!isHome) {
                                PillButton(
                                    text = stringResource(R.string.pages_make_home),
                                    onClick = { haptics.performHapticFeedback(NulisHaptics.confirm); actions.setHomePage(pageId) },
                                    compact = true,
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            if (pages.canRemove && confirming != pageId) {
                                NulisIconButton(
                                    Glyph.Trash,
                                    onClick = {
                                        if ((layout?.blocks?.size ?: 0) == 0) {
                                            actions.removePage(pageId)
                                        } else {
                                            confirming = pageId
                                        }
                                    },
                                    tint = NulisTheme.colors.tertiary,
                                    contentDescription = stringResource(R.string.pages_remove),
                                )
                            }
                        }
                        if (confirming == pageId) {
                            Spacer(Modifier.height(8.dp))
                            (layout?.blocks?.size ?: 0).let { n -> Caption(pluralStringResource(R.plurals.pages_remove_warning, n, n), lines = 2) }
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PillButton(
                                    text = stringResource(R.string.pages_remove_keep),
                                    onClick = { confirming = null },
                                    compact = true,
                                )
                                PillButton(
                                    text = stringResource(R.string.pages_remove),
                                    onClick = { confirming = null; actions.removePage(pageId) },
                                    tone = PillTone.Danger,
                                    compact = true,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SectionLabel(stringResource(R.string.pages_add_label))
            Spacer(Modifier.height(8.dp))
            PillButton(
                text = stringResource(R.string.pages_add),
                onClick = { actions.addPage(pages.ids.lastOrNull()) },
                enabled = pages.canAdd,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Caption(
                if (pages.canAdd) {
                    stringResource(R.string.pages_add_hint)
                } else {
                    pluralStringResource(R.plurals.pages_add_full, PagesConfig.MAX, PagesConfig.MAX)
                },
                lines = 3,
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

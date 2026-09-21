// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nulis.launcher.R
import com.nulis.launcher.apps.AppCategory
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.apps.Categories
import com.nulis.launcher.icons.AppGlyph
import com.nulis.launcher.icons.IconStyle
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.NulisTextField
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.fadeTop
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * Make, name, order and fill the user's own app groups.
 *
 * Apps go in and out with a toggle rather than by dragging. A drag between two scrolling lists
 * is a lot of machinery for something a tap already does in one gesture, and the same toggle
 * list is how favourites and hidden apps already work here.
 */
@Composable
fun CategoriesScreen(
    categories: Categories,
    apps: List<AppInfo>,
    iconStyle: IconStyle,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAssign: (String, String?) -> Unit,
    onMove: (String, Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var editing by remember { mutableStateOf<String?>(null) }
    val open = categories.all.firstOrNull { it.id == editing }

    Box(modifier.fillMaxSize().background(NulisTheme.colors.background)) {
        if (open == null) {
            CategoryList(
                categories = categories,
                onCreate = onCreate,
                onOpen = { editing = it },
                onMove = onMove,
                onClose = onClose,
            )
        } else {
            CategoryEditor(
                category = open,
                categories = categories,
                apps = apps,
                iconStyle = iconStyle,
                onRename = { onRename(open.id, it) },
                onDelete = {
                    onDelete(open.id)
                    editing = null
                },
                onAssign = onAssign,
                onClose = { editing = null },
            )
        }
    }
}

@Composable
private fun CategoryList(
    categories: Categories,
    onCreate: (String) -> Unit,
    onOpen: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onClose: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    NulisScreen(
        label = stringResource(R.string.categories_title),
        title = "${categories.all.size}",
        onBack = onClose,
    ) {
        Caption(stringResource(R.string.categories_hint), lines = 3)
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NulisTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.categories_new),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(12.dp))
            PillButton(
                text = stringResource(R.string.categories_create),
                tone = PillTone.Primary,
                compact = true,
                enabled = name.isNotBlank(),
                onClick = {
                    onCreate(name.trim())
                    name = ""
                },
            )
        }
        Spacer(Modifier.height(16.dp))
        if (categories.all.isEmpty()) {
            Caption(stringResource(R.string.categories_empty))
        }
        LazyColumn(Modifier.fillMaxSize().fadeTop()) {
            items(categories.all, key = { it.id }) { category ->
                ListRow(
                    title = category.name,
                    subtitle = pluralStringResource(R.plurals.categories_apps, category.appIds.size, category.appIds.size),
                    onClick = { onOpen(category.id) },
                    trailing = {
                        Row {
                            NulisIconButton(Glyph.ChevronUp, onClick = { onMove(category.id, -1) })
                            NulisIconButton(Glyph.ChevronDown, onClick = { onMove(category.id, +1) })
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryEditor(
    category: AppCategory,
    categories: Categories,
    apps: List<AppInfo>,
    iconStyle: IconStyle,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onAssign: (String, String?) -> Unit,
    onClose: () -> Unit,
) {
    var name by remember(category.id) { mutableStateOf(category.name) }
    var confirmDelete by remember(category.id) { mutableStateOf(false) }
    val members = category.appIds.toSet()
    NulisScreen(
        label = stringResource(R.string.categories_title),
        title = category.name,
        onBack = onClose,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NulisTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = stringResource(R.string.categories_new),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(12.dp))
            PillButton(
                text = stringResource(R.string.rename_save),
                compact = true,
                enabled = name.isNotBlank() && name != category.name,
                onClick = { onRename(name.trim()) },
            )
        }
        Spacer(Modifier.height(12.dp))
        PillButton(
            text = if (confirmDelete) stringResource(R.string.categories_delete_confirm) else stringResource(R.string.categories_delete),
            tone = PillTone.Danger,
            modifier = Modifier.fillMaxWidth(),
            onClick = { if (confirmDelete) onDelete() else confirmDelete = true },
        )
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.categories_members), trailing = "${members.size}")
        LazyColumn(Modifier.fillMaxSize().fadeTop()) {
            items(apps, key = { it.id }) { app ->
                val inThis = app.id in members
                val elsewhere = categories.of(app.id)?.takeIf { it.id != category.id }
                ListRow(
                    title = app.label,
                    subtitle = elsewhere?.name,
                    enabled = inThis || elsewhere == null,
                    leading = if (iconStyle.mode.hasGlyph) ({ AppGlyph(app, iconStyle) }) else null,
                    onClick = { onAssign(app.id, if (inThis) null else category.id) },
                    trailing = {
                        NulisToggle(
                            checked = inThis,
                            enabled = inThis || elsewhere == null,
                            onCheckedChange = { onAssign(app.id, if (it) category.id else null) },
                        )
                    },
                )
            }
        }
    }
}

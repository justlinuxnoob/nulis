// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.setups

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.home.PageMiniature
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisBottomSheet
import com.nulis.launcher.ui.components.NulisCard
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.NulisTextField
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.theme.Fonts
import com.nulis.launcher.ui.theme.LocalLook
import com.nulis.launcher.ui.theme.LocalNulisColors
import com.nulis.launcher.ui.theme.LocalNulisTypography
import com.nulis.launcher.ui.theme.Looks
import com.nulis.launcher.ui.theme.NulisColors
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme
import com.nulis.launcher.ui.theme.NulisTypography
import com.nulis.launcher.ui.theme.colorsFor

/** Everything this screen can do, wired to the view model by the route. */
class SetupActions(
    val onApply: (SavedSetup, ApplyOptions) -> Unit,
    val onSaveCurrent: (String) -> Unit,
    val onRename: (String, String) -> Unit,
    val onDelete: (String) -> Unit,
)

/**
 * The setups the user has saved, each drawn as itself.
 *
 * There is no gallery of presets here and nothing ships in this list. Nulis has two things a
 * person chooses between - a **layout**, which says what is on which page, and a **look** with
 * its colours, which says how it is drawn - and this is the third thing: a way to keep the two
 * together, under a name, and come back to them.
 *
 * The miniature inside each card is that setup's own home page, in that setup's colours, Look
 * and typefaces, with this phone's real data in it. Nothing here is a screenshot or a mock-up.
 */
@Composable
fun SetupsScreen(
    setups: List<SavedSetup>,
    appliedId: String?,
    context: BlockContext,
    actions: SetupActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var applying by remember { mutableStateOf<SavedSetup?>(null) }
    var managing by remember { mutableStateOf<SavedSetup?>(null) }
    var saving by remember { mutableStateOf(false) }

    // One root box: the sheets are siblings of the screen inside it, so a zIndex the caller
    // gives this screen cannot end up stacking the screen on top of its own sheets.
    Box(modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().background(NulisTheme.colors.background)) {
            NulisScreen(
                label = "Saved setups",
                title = if (setups.isEmpty()) "None yet" else "${setups.size} saved",
                onBack = onClose,
                trailing = {
                    // With nothing saved, the one thing to do is the big button under the
                    // explanation; a second, smaller copy of it up here only squeezed the title
                    // until "Nothing" broke across two lines.
                    if (setups.isNotEmpty()) {
                        PillButton(text = "Save this one", onClick = { saving = true }, compact = true)
                        Spacer(Modifier.width(8.dp))
                    }
                    NulisIconButton(Glyph.Close, onClick = onClose, bordered = true)
                },
            ) {
                if (setups.isEmpty()) {
                    // A paragraph, so it is body text: six lines of shouted mono is a wall.
                    Text(
                        text = "A setup is your whole phone kept under a name: every page and " +
                            "every block, and the Look, the colours and the type they are drawn " +
                            "in.\n\nSave this one, change everything, and come back to it " +
                            "whenever you like.",
                        style = NulisTheme.type.bodyM,
                        color = NulisTheme.colors.secondary,
                    )
                    Spacer(Modifier.height(24.dp))
                    PillButton(
                        text = "Save this setup",
                        tone = PillTone.Primary,
                        onClick = { saving = true },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(bottom = 40.dp),
                    ) {
                        items(setups, key = { it.id }) { setup ->
                            SetupCard(
                                setup = setup,
                                selected = setup.id == appliedId,
                                context = context,
                                onClick = { applying = setup },
                                onManage = { managing = setup },
                            )
                        }
                    }
                }
            }
        }

        applying?.let { setup ->
            ApplySheet(
                setup = setup,
                onApply = { options ->
                    actions.onApply(setup, options)
                    applying = null
                    onClose()
                },
                onDismiss = { applying = null },
            )
        }

        managing?.let { setup ->
            ManageSheet(
                setup = setup,
                onRename = { actions.onRename(setup.id, it) },
                onDelete = {
                    actions.onDelete(setup.id)
                    managing = null
                },
                onDismiss = { managing = null },
            )
        }

        if (saving) {
            SaveSheet(
                onSave = {
                    actions.onSaveCurrent(it)
                    saving = false
                },
                onDismiss = { saving = false },
            )
        }
    }
}

/** A card showing the setup's home page rendered in the setup's own design tokens. */
@Composable
private fun SetupCard(
    setup: SavedSetup,
    selected: Boolean,
    context: BlockContext,
    onClick: () -> Unit,
    onManage: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column {
        NulisCard(
            modifier = Modifier.fillMaxWidth(),
            selected = selected,
            label = setup.name,
            shape = NulisShapes.tile,
            contentPadding = 8.dp,
            onClick = {
                haptics.performHapticFeedback(NulisHaptics.tick)
                onClick()
            },
        ) {
            SetupTokens(setup) {
                PageMiniature(setup.cover, context, Modifier.fillMaxWidth(), sample = true)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(setup.name, style = NulisTheme.type.bodyL, color = NulisTheme.colors.onBackground, maxLines = 1)
            Spacer(Modifier.width(8.dp))
            NulisIconButton(Glyph.Dots, onClick = onManage, modifier = Modifier.width(32.dp))
        }
        if (setup.tagline.isNotBlank()) {
            // A tagline is allowed two lines; Caption cuts at one and these are the only place
            // in the launcher where a sentence sits under a name.
            Text(
                text = NulisTheme.type.labelCase(setup.tagline),
                style = NulisTheme.type.label,
                color = NulisTheme.colors.tertiary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Runs [content] under the setup's own colours, Look and typefaces instead of the applied ones. */
@Composable
fun SetupTokens(setup: SavedSetup, content: @Composable () -> Unit) {
    val look = Looks.byId(setup.lookId)
    val colors: NulisColors = remember(setup) {
        colorsFor(
            setup.colorTheme,
            Color(setup.customBackground ?: 0xFF14213D.toInt()),
            setup.customInk?.let { Color(it) },
        )
    }
    val type = remember(setup, look) {
        NulisTypography(
            look = look,
            displayOverride = Fonts.byId(setup.displayFont),
            bodyOverride = Fonts.byId(setup.bodyFont),
            scale = setup.textScale,
            uppercaseLabels = setup.uppercaseLabels,
        )
    }
    CompositionLocalProvider(
        LocalLook provides look,
        LocalNulisColors provides colors,
        LocalNulisTypography provides type,
        content = content,
    )
}

/** Asks the two questions worth asking before a setup replaces every page. */
@Composable
private fun ApplySheet(setup: SavedSetup, onApply: (ApplyOptions) -> Unit, onDismiss: () -> Unit) {
    var keepApps by remember { mutableStateOf(true) }
    var keepWriting by remember { mutableStateOf(true) }
    NulisBottomSheet(onDismiss = onDismiss) {
        val close = ::dismiss
        Text(setup.name, style = NulisTheme.type.displayM, color = NulisTheme.colors.onBackground)
        Spacer(Modifier.height(4.dp))
        Caption("Replaces your pages with this setup's ${setup.pages.size}, and the look with its own")
        Spacer(Modifier.height(16.dp))
        SectionLabel("Bring across")
        ListRow(
            title = "The apps you picked",
            subtitle = "Put your own favourites into this setup's apps block",
            trailing = { NulisToggle(checked = keepApps, onCheckedChange = { keepApps = it }) },
            onClick = { keepApps = !keepApps },
        )
        ListRow(
            title = "Notes, journal and tasks blocks",
            subtitle = "Keep any this setup does not have. What you wrote is never deleted either way",
            trailing = { NulisToggle(checked = keepWriting, onCheckedChange = { keepWriting = it }) },
            onClick = { keepWriting = !keepWriting },
            divider = false,
        )
        Spacer(Modifier.height(20.dp))
        PillButton(
            text = "Use this setup",
            onClick = {
                close()
                onApply(ApplyOptions(keepApps = keepApps, keepWritingBlocks = keepWriting))
            },
            tone = PillTone.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun ManageSheet(
    setup: SavedSetup,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember(setup.id) { mutableStateOf(setup.name) }
    NulisBottomSheet(onDismiss = onDismiss) {
        val close = ::dismiss
        SectionLabel("Rename")
        Spacer(Modifier.height(8.dp))
        NulisTextField(value = name, onValueChange = { name = it }, placeholder = "Name", modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(
                text = "Save name",
                onClick = { onRename(name.trim().ifBlank { setup.name }); close() },
                modifier = Modifier.weight(1f),
            )
            PillButton(
                text = "Delete",
                onClick = { close(); onDelete() },
                tone = PillTone.Danger,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SaveSheet(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    NulisBottomSheet(onDismiss = onDismiss) {
        val close = ::dismiss
        Text("Save this setup", style = NulisTheme.type.displayM, color = NulisTheme.colors.onBackground)
        Spacer(Modifier.height(4.dp))
        Caption("Every page and block as they are now, with the Look, the colours and the type", lines = 2)
        Spacer(Modifier.height(16.dp))
        NulisTextField(
            value = name,
            onValueChange = { name = it },
            placeholder = "Name it",
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        PillButton(
            text = "Save",
            onClick = {
                close()
                onSave(name.trim().ifBlank { "My setup" })
            },
            tone = PillTone.Primary,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
    }
}

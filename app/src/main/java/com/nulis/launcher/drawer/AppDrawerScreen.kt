// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.drawer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nulis.launcher.R
import com.nulis.launcher.apps.AppCategory
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.apps.Categories
import com.nulis.launcher.blocks.writing.WritingState
import com.nulis.launcher.blocks.screentime.formatMinutes
import com.nulis.launcher.icons.AppGlyph
import com.nulis.launcher.icons.IconStyle
import com.nulis.launcher.icons.LocalIconLoader
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.GlyphIcon
import com.nulis.launcher.ui.components.Hairline
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisBottomSheet
import com.nulis.launcher.ui.components.NulisTextField
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme
import kotlinx.coroutines.launch

/** Everything the drawer can do beyond launching an app. */
class DrawerActions(
    val onToggleFavorite: (AppInfo) -> Unit,
    val onRename: (AppInfo, String?) -> Unit,
    val onSetIcon: (AppInfo, String?) -> Unit,
    val onSetHidden: (AppInfo, Boolean) -> Unit,
    val onAssignCategory: (AppInfo, String?) -> Unit,
    val onCreateCategory: (String) -> Unit,
    val onAppInfo: (AppInfo) -> Unit,
    val onUninstall: (AppInfo) -> Unit,
    val onOpenNote: (String) -> Unit,
    val onOpenTasks: () -> Unit,
    val onOpenSettings: (SettingsTarget) -> Unit,
)

/**
 * Full app list with a search field on top, the user's own groups, section letters in the
 * display face and an A-Z rail on the right. The owner slides it in and out; this screen only
 * reports when it wants to close (back, or a launch) and reacts to [open] / [settled].
 *
 * What the list actually contains is decided by [buildDrawer], which is pure and tested.
 *
 * @param open whether the drawer is heading towards or resting at open.
 * @param settled true once the drawer is fully open and at rest; only then does the keyboard come up.
 * @param onLaunch called with the tapped row's bounds in window coordinates.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppDrawerScreen(
    apps: List<AppInfo>,
    favoriteIds: Set<String>,
    /** Today's minutes per package, shown after the name; null hides them. */
    usageMinutes: Map<String, Int>?,
    /** How each app is drawn here; also used by every other app list outside a block. */
    iconStyle: IconStyle,
    /** Apps kept out of the list. They still turn up in search unless [searchHidden] is off. */
    hiddenIds: Set<String>,
    searchHidden: Boolean,
    /** Apps the drawer is resting because they have gone unused; always searchable. */
    autoHiddenIds: Set<String>,
    categories: Categories,
    categoryDisplay: CategoryDisplay,
    recents: List<AppInfo>,
    writing: WritingState,
    searchNotes: Boolean,
    searchSettings: Boolean,
    /** Packages a running focus session is guarding: faded, or left out when [muteHides]. */
    mutedPackages: Set<String>,
    muteHides: Boolean,
    hasAppsBlock: Boolean,
    canAddFavorite: Boolean,
    open: Boolean,
    settled: Boolean,
    focusSearch: Boolean,
    onLaunch: (AppInfo, Rect?) -> Unit,
    actions: DrawerActions,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NulisTheme.colors
    var query by remember { mutableStateOf("") }
    var menuApp by remember { mutableStateOf<AppInfo?>(null) }
    var renameApp by remember { mutableStateOf<AppInfo?>(null) }
    var iconPickerApp by remember { mutableStateOf<AppInfo?>(null) }
    var categoryApp by remember { mutableStateOf<AppInfo?>(null) }
    var collapsed by remember { mutableStateOf(emptySet<String>()) }

    val entries = remember(apps, query, hiddenIds, searchHidden, autoHiddenIds, categories, categoryDisplay, collapsed, recents, writing, searchNotes, searchSettings, mutedPackages, muteHides) {
        buildDrawer(
            DrawerInput(
                apps = apps,
                query = query,
                hiddenIds = hiddenIds,
                searchHidden = searchHidden,
                categories = categories,
                display = categoryDisplay,
                collapsed = collapsed,
                autoHiddenIds = autoHiddenIds,
                recents = recents,
                writing = writing,
                searchNotes = searchNotes,
                searchSettings = searchSettings,
                mutedPackages = mutedPackages,
                muteHides = muteHides,
            ),
        )
    }
    val firstApp = remember(entries) { entries.filterIsInstance<DrawerEntry.App>().firstOrNull()?.app }
    val letters = remember(entries) { entries.filterIsInstance<DrawerEntry.LetterHeader>().map { it.letter } }
    val letterIndices = remember(entries) {
        entries.withIndex().filter { it.value is DrawerEntry.LetterHeader }.map { it.index }
    }
    // The first app under each letter, for the bubble: the list is still while the rail is
    // dragged, so the bubble has to say what is waiting down there.
    val letterFirstApp = remember(entries, letterIndices) {
        letterIndices.map { header ->
            (entries.drop(header + 1).firstOrNull() as? DrawerEntry.App)?.app?.label
        }
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    BackHandler(enabled = open) { onClose() }

    // The keyboard only comes up once the drawer has settled, so the slide never stutters.
    LaunchedEffect(settled, focusSearch) {
        if (settled && focusSearch) {
            focusRequester.requestFocus()
            keyboard?.show()
        } else if (!settled) {
            keyboard?.hide()
            focusManager.clearFocus()
        }
    }
    // Reset once fully closed so the next open starts clean.
    LaunchedEffect(open) {
        if (!open) {
            query = ""
            menuApp = null
            listState.scrollToItem(0)
        }
    }
    LaunchedEffect(query) { listState.scrollToItem(0) }

    Box(modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.background)
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            SearchPill(
                query = query,
                onQueryChange = { query = it },
                onGo = { firstApp?.let { onLaunch(it, null) } },
                focusRequester = focusRequester,
                focusable = open,
                modifier = Modifier
                    .fillMaxWidth()
                    .scrollable(rememberScrollableState { 0f }, Orientation.Vertical)
                    .padding(horizontal = NulisSpacing.screenMargin, vertical = 12.dp),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    // Touching the list dismisses the keyboard at once, before any drag threshold, so the
                    // keyboard's own animation is mostly done by the time the list or drawer starts moving.
                    .pointerInput(keyboard, focusManager) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                            keyboard?.hide()
                            focusManager.clearFocus()
                        }
                    },
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(end = if (letters.isEmpty()) 0.dp else 24.dp, bottom = 24.dp),
                ) {
                    entries.forEach { entry ->
                        when (entry) {
                            is DrawerEntry.LetterHeader -> stickyHeader(key = entry.key) { SectionHeader(entry.letter, display = true) }
                            is DrawerEntry.Section -> item(key = entry.key) { SectionHeader(entry.title, display = false) }
                            is DrawerEntry.CategoryHeader -> item(key = entry.key) {
                                CategoryHeaderRow(entry) {
                                    collapsed = if (entry.category.id in collapsed) collapsed - entry.category.id else collapsed + entry.category.id
                                }
                            }
                            is DrawerEntry.Recents -> item(key = entry.key) {
                                RecentsRow(entry.apps, iconStyle, onLaunch)
                            }
                            is DrawerEntry.App -> item(key = entry.key) {
                                AppRow(
                                    app = entry.app,
                                    minutes = usageMinutes?.get(entry.app.packageName),
                                    iconStyle = iconStyle,
                                    muted = entry.app.packageName in mutedPackages,
                                    onLaunch = onLaunch,
                                    onLongClick = { menuApp = entry.app },
                                )
                            }
                            is DrawerEntry.Hit -> item(key = entry.key) {
                                HitRow(entry.hit, actions)
                            }
                        }
                    }
                }
                AlphabetRail(
                    letters = letters,
                    onLetter = { index ->
                        letterIndices.getOrNull(index)?.let { scope.launch { listState.scrollToItem(it) } }
                    },
                    preview = { index -> letterFirstApp.getOrNull(index) },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp),
                )
            }
        }

        menuApp?.let { app ->
            val isFavorite = app.id in favoriteIds
            val hasPack = LocalIconLoader.current.pack != null
            NulisBottomSheet(onDismiss = { menuApp = null }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (iconStyle.mode.hasGlyph) {
                        AppGlyph(app, iconStyle)
                        Spacer(Modifier.width(16.dp))
                    }
                    Column {
                        Text(app.label, style = NulisTheme.type.displayM, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // What somebody can use: whether it is the work copy, and how long it has had
                        // today. The package name was a developer's answer to a question nobody
                        // holding a phone asks; App info still shows it.
                        val minutes = usageMinutes?.get(app.packageName)
                        val facts = listOfNotNull(
                            "Work profile".takeIf { app.isWork },
                            minutes?.takeIf { it > 0 }?.let { formatMinutes(it) + " today" },
                        )
                        if (facts.isNotEmpty()) {
                            Spacer(Modifier.height(4.dp))
                            Caption(facts.joinToString(" · "))
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Hairline()
                if (isFavorite) {
                    ListRow(title = stringResource(R.string.menu_remove_from_home), onClick = { dismiss(); actions.onToggleFavorite(app) })
                } else {
                    val label = when {
                        !hasAppsBlock -> R.string.menu_no_apps_block
                        canAddFavorite -> R.string.menu_add_to_home
                        else -> R.string.menu_home_full
                    }
                    ListRow(title = stringResource(label), enabled = canAddFavorite, onClick = { dismiss(); actions.onToggleFavorite(app) })
                }
                ListRow(
                    title = stringResource(R.string.menu_category),
                    subtitle = categories.of(app.id)?.name ?: stringResource(R.string.menu_category_none),
                    onClick = { dismiss(); categoryApp = app },
                )
                ListRow(title = stringResource(R.string.menu_rename), onClick = { dismiss(); renameApp = app })
                if (hasPack) {
                    ListRow(title = stringResource(R.string.menu_choose_icon), onClick = { dismiss(); iconPickerApp = app })
                }
                // Nulis's own row is the way back into settings, so it can never be hidden.
                if (!app.isNulisSettings) {
                    val hidden = app.id in hiddenIds
                    ListRow(
                        title = stringResource(if (hidden) R.string.menu_unhide else R.string.menu_hide),
                        onClick = { dismiss(); actions.onSetHidden(app, !hidden) },
                    )
                    ListRow(title = stringResource(R.string.menu_app_info), onClick = { dismiss(); actions.onAppInfo(app) })
                    ListRow(title = stringResource(R.string.menu_uninstall), titleColor = colors.danger, divider = false, onClick = { dismiss(); actions.onUninstall(app) })
                }
            }
        }

        categoryApp?.let { app ->
            CategorySheet(
                app = app,
                categories = categories,
                onAssign = { actions.onAssignCategory(app, it) },
                onCreate = actions.onCreateCategory,
                onDismiss = { categoryApp = null },
            )
        }

        renameApp?.let { app ->
            RenameAppScreen(
                app = app,
                onRename = { actions.onRename(app, it) },
                onClose = { renameApp = null },
                modifier = Modifier.zIndex(2f),
            )
        }
        iconPickerApp?.let { app ->
            IconPickerScreen(
                app = app,
                style = iconStyle,
                onPick = { actions.onSetIcon(app, it) },
                onClose = { iconPickerApp = null },
                modifier = Modifier.zIndex(2f),
            )
        }
    }
}

/** Pick a group for one app, or make a new one without leaving the sheet. */
@Composable
private fun CategorySheet(
    app: AppInfo,
    categories: Categories,
    onAssign: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = NulisTheme.colors
    val current = categories.of(app.id)
    var newName by remember { mutableStateOf("") }
    NulisBottomSheet(onDismiss = onDismiss) {
        val close = ::dismiss
        Text(app.label, style = NulisTheme.type.displayM, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Caption(stringResource(R.string.menu_category))
        Spacer(Modifier.height(12.dp))
        ListRow(
            title = stringResource(R.string.menu_category_none),
            onClick = { onAssign(null); close() },
            trailing = { if (current == null) Box(Modifier.size(6.dp).background(colors.accent, NulisShapes.pill)) },
        )
        categories.all.forEach { category ->
            ListRow(
                title = category.name,
                subtitle = "${category.appIds.size} apps",
                onClick = { onAssign(category.id); close() },
                trailing = { if (category.id == current?.id) Box(Modifier.size(6.dp).background(colors.accent, NulisShapes.pill)) },
            )
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NulisTextField(
                value = newName,
                onValueChange = { newName = it },
                placeholder = stringResource(R.string.categories_new),
                modifier = Modifier.weight(1f),
                singleLine = true,
            )
            Spacer(Modifier.width(12.dp))
            PillButton(
                text = stringResource(R.string.categories_create),
                tone = PillTone.Primary,
                compact = true,
                enabled = newName.isNotBlank(),
                onClick = {
                    // The new group is made and the app goes straight into it; a sheet that made
                    // an empty group and then asked you to find it again would be worse than none.
                    onCreate(newName.trim())
                    onAssign(PENDING_NEW_CATEGORY)
                    close()
                },
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Sentinel meaning "the group that was just created". The view model resolves it, because only
 * it knows the id the repository handed out.
 */
const val PENDING_NEW_CATEGORY = "\u0000new"

@Composable
private fun SectionHeader(text: String, display: Boolean) {
    val colors = NulisTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.background)
            .padding(horizontal = NulisSpacing.screenMargin)
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (display) {
            Text(text, style = NulisTheme.type.displayM, color = colors.secondary)
        } else {
            Caption(text)
        }
        Spacer(Modifier.width(16.dp))
        Hairline(Modifier.weight(1f))
    }
}

@Composable
private fun CategoryHeaderRow(entry: DrawerEntry.CategoryHeader, onToggle: () -> Unit) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressFeedback()
            .clickable(interactionSource = interaction, indication = null) {
                haptics.performHapticFeedback(NulisHaptics.tick)
                onToggle()
            }
            .background(colors.background)
            .padding(horizontal = NulisSpacing.screenMargin)
            .padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(entry.category.name, style = NulisTheme.type.displayM, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.width(12.dp))
        Caption("${entry.count}")
        Spacer(Modifier.width(12.dp))
        Hairline(Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        GlyphIcon(if (entry.collapsed) Glyph.ChevronDown else Glyph.ChevronUp, colors.tertiary)
    }
}

/** The apps you opened most recently, as a row you can flick. */
@Composable
private fun RecentsRow(apps: List<AppInfo>, iconStyle: IconStyle, onLaunch: (AppInfo, Rect?) -> Unit) {
    val colors = NulisTheme.colors
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = NulisSpacing.screenMargin).padding(top = 8.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Caption(stringResource(R.string.drawer_recent))
            Spacer(Modifier.width(12.dp))
            Hairline(Modifier.weight(1f))
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = NulisSpacing.screenMargin),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            items(apps, key = { it.id }) { app ->
                var coordinates: LayoutCoordinates? = null
                val interaction = remember { MutableInteractionSource() }
                Column(
                    modifier = Modifier
                        .width(iconStyle.size.dp + 20.dp)
                        .onGloballyPositioned { coordinates = it }
                        .pressFeedback()
                        .clickable(interactionSource = interaction, indication = null) {
                            onLaunch(app, coordinates?.takeIf { it.isAttached }?.boundsInWindow())
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AppGlyph(app, iconStyle.copy(mode = com.nulis.launcher.icons.IconMode.ICON))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = app.label,
                        style = NulisTheme.type.bodyS,
                        color = colors.secondary,
                        maxLines = 1,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** A search result that is not an app: a sum, a note, a task, a settings section. */
@Composable
private fun HitRow(hit: SearchHit, actions: DrawerActions) {
    val colors = NulisTheme.colors
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current
    when (hit) {
        is SearchHit.Math -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NulisSpacing.screenMargin, vertical = 8.dp)
                .pressFeedback()
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    haptics.performHapticFeedback(NulisHaptics.confirm)
                    clipboard.setText(AnnotatedString(hit.result))
                }
                .background(colors.surface, NulisShapes.tile)
                .border(1.dp, colors.hairline, NulisShapes.tile)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(hit.expression, style = NulisTheme.type.mono, color = colors.secondary, maxLines = 1, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            Text(hit.result, style = NulisTheme.type.displayS, color = colors.onBackground, maxLines = 1)
        }
        is SearchHit.Note -> ListRow(
            title = hit.title,
            subtitle = hit.snippet,
            modifier = Modifier.padding(horizontal = NulisSpacing.screenMargin),
            onClick = { actions.onOpenNote(hit.id) },
        )
        is SearchHit.Task -> ListRow(
            title = hit.text,
            subtitle = stringResource(R.string.drawer_hit_task),
            modifier = Modifier.padding(horizontal = NulisSpacing.screenMargin),
            onClick = { actions.onOpenTasks() },
        )
        is SearchHit.Setting -> ListRow(
            title = hit.target.title,
            subtitle = stringResource(R.string.drawer_hit_setting),
            modifier = Modifier.padding(horizontal = NulisSpacing.screenMargin),
            onClick = { actions.onOpenSettings(hit.target) },
        )
    }
}

/**
 * One app. With names only it is a single text node with the same metrics as [ListRow] (56dp,
 * bodyL, press feedback), kept deliberately lean because a letter jump composes a screenful of
 * these in one frame. An icon adds exactly one more node, and the row grows to fit it.
 */
@Composable
private fun AppRow(
    app: AppInfo,
    minutes: Int?,
    iconStyle: IconStyle,
    muted: Boolean,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onLongClick: () -> Unit,
) {
    var coordinates: LayoutCoordinates? = null
    val interaction = remember { MutableInteractionSource() }
    val rowModifier = Modifier
        // Faded while a focus session guards it; still tappable, because this is a nudge.
        .alpha(if (muted) 0.3f else 1f)
        .padding(horizontal = NulisSpacing.screenMargin)
        .fillMaxWidth()
        .onGloballyPositioned { coordinates = it }
        .pressFeedback()
        .combinedClickable(
            interactionSource = interaction,
            indication = null,
            onLongClick = onLongClick,
            onClick = { onLaunch(app, coordinates?.takeIf { it.isAttached }?.boundsInWindow()) },
        )
        // A minimum, not a height: at a large system font scale the name must be able to wrap
        // the row taller rather than being clipped.
        .defaultMinSize(minHeight = maxOf(56.dp, iconStyle.size.dp + 12.dp))
        .padding(vertical = 6.dp)
    if (minutes == null && !iconStyle.mode.hasGlyph && !app.isWork) {
        Text(
            text = app.label,
            style = NulisTheme.type.bodyL,
            color = NulisTheme.colors.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = rowModifier.wrapContentHeight(Alignment.CenterVertically),
        )
    } else {
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
            if (iconStyle.mode.hasGlyph) {
                AppGlyph(app, iconStyle)
                Spacer(Modifier.width(16.dp))
            }
            Text(app.label, style = NulisTheme.type.bodyL, color = NulisTheme.colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // A work app is marked, because launching the wrong one of two Chromes is a real mistake.
            if (app.isWork) WorkBadge()
            if (minutes != null && minutes > 0) Caption(formatMinutes(minutes), modifier = Modifier.padding(start = 12.dp))
        }
    }
}

@Composable
private fun WorkBadge() {
    val colors = NulisTheme.colors
    Text(
        text = NulisTheme.type.labelCase(stringResource(R.string.drawer_work)),
        style = NulisTheme.type.label,
        color = colors.secondary,
        modifier = Modifier
            .padding(start = 12.dp)
            .background(colors.surfaceRaised, NulisShapes.pill)
            .border(1.dp, colors.hairline, NulisShapes.pill)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
private fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    onGo: () -> Unit,
    focusRequester: FocusRequester,
    focusable: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = NulisTheme.colors
    Box(modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A minimum, not a maximum: at a large text scale the field grows rather than
                // clipping what is inside it.
                .heightIn(min = NulisSpacing.touchTarget)
                .background(colors.surface, NulisShapes.pill)
                .border(1.dp, colors.hairline, NulisShapes.pill)
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.Search, colors.secondary, size = 16.dp)
            Spacer(Modifier.width(12.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                // Not focusable while the drawer is hidden, so a key event can never hand it focus (and the keyboard) offscreen.
                modifier = Modifier.weight(1f).focusRequester(focusRequester).focusProperties { canFocus = focusable },
                singleLine = true,
                textStyle = NulisTheme.type.bodyM.copy(color = colors.onBackground),
                cursorBrush = SolidColor(colors.onBackground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { onGo() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (query.isEmpty()) {
                            // One line, always: at 200% text this hint wrapped and the second
                            // line hung out of the bottom of the pill, cut in half.
                            Text(
                                text = NulisTheme.type.labelCase(stringResource(R.string.drawer_search_hint)),
                                style = NulisTheme.type.label,
                                color = colors.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }
    }
}


// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nulis.launcher.R
import com.nulis.launcher.apps.AppCustomization
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.sampleApp
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.apps.Categories
import com.nulis.launcher.blocks.clock.ClockBlockDefinition
import com.nulis.launcher.drawer.CategoryDisplay
import com.nulis.launcher.drawer.DrawerPlacement
import com.nulis.launcher.drawer.SettingsTarget
import com.nulis.launcher.ui.components.SegmentedPills
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import com.nulis.launcher.gestures.GestureActionSheet
import com.nulis.launcher.gestures.GestureAppPicker
import com.nulis.launcher.gestures.GestureAction
import com.nulis.launcher.gestures.GestureBinding
import com.nulis.launcher.gestures.GestureSettings
import com.nulis.launcher.gestures.GestureTrigger
import com.nulis.launcher.gestures.GesturesSection
import com.nulis.launcher.icons.AppGlyph
import com.nulis.launcher.icons.IconMode
import com.nulis.launcher.icons.IconPackInfo
import com.nulis.launcher.icons.IconStyle
import com.nulis.launcher.icons.IconStylePicker
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisBottomSheet
import com.nulis.launcher.ui.components.NulisCard
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.NulisSlider
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.ScaledPreview
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.components.fadeTop
import com.nulis.launcher.ui.theme.ColorTheme
import com.nulis.launcher.ui.theme.Look
import com.nulis.launcher.ui.theme.Looks
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisSpacing
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nulis.launcher.onboarding.isDefaultLauncher
import com.nulis.launcher.onboarding.rememberHomeRoleRequest
import androidx.compose.ui.platform.LocalContext
import com.nulis.launcher.ui.components.GlyphIcon
import com.nulis.launcher.ui.theme.NulisTheme
import com.nulis.launcher.ui.theme.NulisTypography
import com.nulis.launcher.ui.theme.toHsl
import com.nulis.launcher.ui.theme.customColors
import com.nulis.launcher.ui.theme.LocalLook
import com.nulis.launcher.ui.theme.LocalNulisColors
import com.nulis.launcher.ui.theme.LocalNulisTypography
import androidx.compose.runtime.CompositionLocalProvider
import kotlin.math.roundToInt

/** Everything the settings screen can change. Wired to the view model by the route. */
class SettingsActions(
    val onLook: (Look) -> Unit,
    val onTheme: (ColorTheme) -> Unit,
    val onCustomBackground: (Int) -> Unit,
    val onCustomInk: (Int?) -> Unit,
    val onAccent: (Int?) -> Unit,
    val onPalette: (com.nulis.launcher.ui.theme.Palette) -> Unit,
    val onHideStatusBar: (Boolean) -> Unit,
    val onWallpaper: (Boolean) -> Unit,
    val onWallpaperDim: (Float) -> Unit,
    val onSetups: () -> Unit,
    val onLayouts: () -> Unit,
    val onPages: () -> Unit,
    val onLeftPageGear: (Boolean) -> Unit,
    val onCategoryDisplay: (CategoryDisplay) -> Unit,
    val onAutoHideDays: (Int) -> Unit,
    val onShowRecents: (Boolean) -> Unit,
    val onSearchNotes: (Boolean) -> Unit,
    val onSearchSettings: (Boolean) -> Unit,
    val onCreateCategory: (String) -> Unit,
    val onRenameCategory: (String, String) -> Unit,
    val onDeleteCategory: (String) -> Unit,
    val onAssignCategory: (String, String?) -> Unit,
    val onMoveCategory: (String, Int) -> Unit,
    val onWellbeing: () -> Unit,
    val onWeeklySummary: () -> Unit,
    val onBackup: () -> Unit,
    val onSound: (Boolean) -> Unit,
    val onSoundVolume: (Float) -> Unit,
    val onReducedMotion: (Boolean) -> Unit,
    val onDisplayFont: (String?) -> Unit,
    val onBodyFont: (String?) -> Unit,
    val onTextScale: (Float) -> Unit,
    val onUppercaseLabels: (Boolean) -> Unit,
    val onShowAppUsage: (Boolean) -> Unit,
    val onDrawerIcons: (IconStyle) -> Unit,
    val onIconPack: (String?) -> Unit,
    val onSearchHidden: (Boolean) -> Unit,
    val onDrawerPlacement: (DrawerPlacement) -> Unit,
    val onNotificationDots: (Boolean) -> Unit,
    val onUnhide: (AppInfo) -> Unit,
    val onGesture: (GestureTrigger, GestureBinding) -> Unit,
    val onResetGestures: () -> Unit,
    /** Closes settings and puts the current page into edit mode. */
    val onEditPage: () -> Unit,
    /** Closes settings and starts the first-launch flow again. */
    val onRerunSetup: () -> Unit,
)

/** Curated starting points for a custom background: dark first, then light. */
private val Swatches = listOf(
    0xFF14213D, 0xFF0B2E26, 0xFF3A0F1E, 0xFF2B1B3D, 0xFF1F1F1F, 0xFF3B2A14,
    0xFFF2E8D5, 0xFFE8EEF2, 0xFFEDE3F5, 0xFFDDE8DA, 0xFFF5D6C6, 0xFFEAEAEA,
).map { Color(it) }

/**
 * Full-screen settings: look (live clock previews), background (black, white or a custom color
 * picked from swatches and HSL sliders; text picks its own contrast) and display options.
 */
@Composable
fun SettingsScreen(
    preferences: UiPreferences,
    gestures: GestureSettings,
    context: BlockContext,
    iconPacks: List<IconPackInfo>,
    customization: AppCustomization,
    categories: Categories,
    actions: SettingsActions,
    /** The real home page, so a palette is previewed as the user's own screen. */
    homeLayout: PageLayout?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    /** A section to scroll to on open, from a drawer search result. */
    scrollTo: SettingsTarget? = null,
    onScrolled: () -> Unit = {},
) {
    val colors = NulisTheme.colors
    // The gesture picker and its app list cover the whole screen, so they live outside the scroll.
    var pickerTrigger by remember { mutableStateOf<GestureTrigger?>(null) }
    var appPickerTrigger by remember { mutableStateOf<GestureTrigger?>(null) }
    var packSheetOpen by remember { mutableStateOf(false) }
    var hiddenAppsOpen by remember { mutableStateOf(false) }
    var licensesOpen by remember { mutableStateOf(false) }
    var categoriesOpen by remember { mutableStateOf(false) }
    // Which of the seven screens is open, or null for the index.
    var openSection by remember { mutableStateOf<SettingsSection?>(null) }
    // A drawer search result names a setting; open the screen it lives on and scroll to it there.
    LaunchedEffect(scrollTo) {
        scrollTo?.let { target ->
            // Backup is one screen with nothing in front of it, so its search result goes there.
            if (target.section == SettingsSection.BACKUP) actions.onBackup() else openSection = target.section
        }
    }
    BackHandler(
        enabled = pickerTrigger == null && appPickerTrigger == null && !hiddenAppsOpen && !licensesOpen && !categoriesOpen && openSection == null,
        onBack = onClose,
    )
    Box(modifier.fillMaxSize().background(colors.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { } }
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = NulisSpacing.screenMargin),
        ) {
            Spacer(Modifier.height(8.dp))
            SectionLabel(stringResource(R.string.settings_label), withLine = false)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.settings_title), style = NulisTheme.type.displayM, color = colors.onBackground, modifier = Modifier.weight(1f))
                NulisIconButton(Glyph.Close, onClick = onClose, bordered = true)
            }
            Spacer(Modifier.height(16.dp))
            // The rows scroll and the title and the footer do not.
            //
            // Seven rows fit a phone at its usual type size and were written on the assumption
            // that they always would. At a 200% font scale on a 720 x 1280 phone four of them
            // fit, and Backup and About - the two that get somebody out of trouble - were off
            // the bottom of a screen that did not move. Scrolling costs nothing when everything
            // already fits.
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                // Only when something is wrong. A launcher that is not the home app is a launcher
                // you have to go and find, and Android quietly hands the role back to whatever
                // was there before after some updates and every "reset app preferences" - so this
                // is checked every time Settings opens rather than once at first launch.
                HomeRoleRow()
                SettingsSection.entries.forEachIndexed { index, entry ->
                    ListRow(
                        title = entry.title,
                        subtitle = entry.summary,
                        // Backup's own screen already explains itself; a section holding one row
                        // that leads to it was a tap spent on nothing.
                        onClick = { if (entry == SettingsSection.BACKUP) actions.onBackup() else openSection = entry },
                        divider = index != SettingsSection.entries.lastIndex,
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
            Caption(stringResource(R.string.settings_footer), lines = 2)
            Spacer(Modifier.height(24.dp))
        }

        openSection?.let { section ->
            SettingsSectionScreen(
                section = section,
                preferences = preferences,
                gestures = gestures,
                context = context,
                iconPacks = iconPacks,
                customization = customization,
                categories = categories,
                actions = actions,
                homeLayout = homeLayout,
                scrollTo = scrollTo?.takeIf { it.section == section },
                onScrolled = onScrolled,
                onPickGesture = { pickerTrigger = it },
                onIconPack = { packSheetOpen = true },
                onHiddenApps = { hiddenAppsOpen = true },
                onCategories = { categoriesOpen = true },
                onLicenses = { licensesOpen = true },
                onBack = { openSection = null },
                modifier = Modifier.zIndex(1f),
            )
        }

        pickerTrigger?.let { trigger ->
            GestureActionSheet(
                trigger = trigger,
                binding = gestures[trigger],
                apps = context.apps,
                escapeHatch = gestures.isEscapeHatch(trigger),
                onBinding = { actions.onGesture(trigger, it) },
                onPickApp = { appPickerTrigger = trigger },
                onDismiss = { pickerTrigger = null },
            )
        }
        if (packSheetOpen) {
            IconPackSheet(
                packs = iconPacks,
                current = preferences.iconPack,
                onPick = actions.onIconPack,
                onDismiss = { packSheetOpen = false },
            )
        }
        if (categoriesOpen) {
            CategoriesScreen(
                categories = categories,
                apps = context.apps,
                iconStyle = preferences.drawerIcons,
                onCreate = actions.onCreateCategory,
                onRename = actions.onRenameCategory,
                onDelete = actions.onDeleteCategory,
                onAssign = actions.onAssignCategory,
                onMove = actions.onMoveCategory,
                onClose = { categoriesOpen = false },
                modifier = Modifier.zIndex(2f),
            )
        }
        if (licensesOpen) {
            FontLicensesScreen(onClose = { licensesOpen = false }, modifier = Modifier.zIndex(2f))
        }
        if (hiddenAppsOpen) {
            HiddenAppsScreen(
                apps = context.apps.filter { it.id in customization.hidden },
                iconStyle = preferences.drawerIcons,
                onUnhide = actions.onUnhide,
                onClose = { hiddenAppsOpen = false },
                modifier = Modifier.zIndex(2f),
            )
        }
        appPickerTrigger?.let { trigger ->
            GestureAppPicker(
                trigger = trigger,
                apps = context.apps,
                iconStyle = preferences.drawerIcons,
                selectedId = gestures[trigger].appId,
                onSelect = { app ->
                    actions.onGesture(trigger, GestureBinding(GestureAction.OPEN_APP, app.id))
                    appPickerTrigger = null
                    pickerTrigger = null
                },
                onClose = { appPickerTrigger = null },
                modifier = Modifier.zIndex(2f),
            )
        }
    }
}


/**
 * One of the seven settings screens. Each one keeps the `marks` machinery inside it, so a search
 * result still lands exactly on the setting it names rather than merely on the right screen.
 */
/**
 * "Nulis is not your home screen", with the fix beside it - and nothing at all when it is.
 *
 * Re-read whenever this screen comes back to the front, because the answer changes outside the
 * app: in Android's own settings, after an update, or when another launcher asks for the role.
 */
@Composable
private fun HomeRoleRow() {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var checked by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checked += 1
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val isDefault = remember(checked) { isDefaultLauncher(context) }
    val request = rememberHomeRoleRequest { checked += 1 }
    if (isDefault) return
    ListRow(
        title = stringResource(R.string.settings_not_home),
        subtitle = stringResource(R.string.settings_not_home_hint),
        onClick = request,
        trailing = { GlyphIcon(Glyph.ChevronRight, NulisTheme.colors.accent) },
    )
}

@Composable
private fun SettingsSectionScreen(
    section: SettingsSection,
    preferences: UiPreferences,
    gestures: GestureSettings,
    context: BlockContext,
    iconPacks: List<IconPackInfo>,
    customization: AppCustomization,
    categories: Categories,
    actions: SettingsActions,
    homeLayout: PageLayout?,
    scrollTo: SettingsTarget?,
    onScrolled: () -> Unit,
    onPickGesture: (GestureTrigger) -> Unit,
    onIconPack: () -> Unit,
    onHiddenApps: () -> Unit,
    onCategories: () -> Unit,
    onLicenses: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
                        ) {
    val colors = NulisTheme.colors
    val scrollState = rememberScrollState()
    val sectionOffsets = remember { mutableStateMapOf<SettingsTarget, Int>() }
    LaunchedEffect(scrollTo, sectionOffsets.size) {
        val target = scrollTo ?: return@LaunchedEffect
        val y = sectionOffsets[target] ?: return@LaunchedEffect
        scrollState.animateScrollTo(y)
        onScrolled()
    }
    Box(modifier.fillMaxSize().background(colors.background)) {
        NulisScreen(
            label = stringResource(R.string.settings_label),
            title = section.title,
            onBack = onBack,
            trailing = { NulisIconButton(Glyph.ArrowLeft, onClick = onBack, bordered = true) },
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .fadeTop()
                    .verticalScroll(scrollState),
            ) {
                when (section) {
                    SettingsSection.LAYOUT -> {
                        SectionLabel(stringResource(R.string.settings_arrangement), modifier = Modifier.marks(SettingsTarget.ARRANGEMENT, sectionOffsets))
                        ListRow(
                            title = stringResource(R.string.settings_layouts),
                            subtitle = stringResource(R.string.settings_layouts_hint),
                            onClick = actions.onLayouts,
                        )
                        ListRow(
                            title = stringResource(R.string.settings_pages),
                            subtitle = stringResource(R.string.settings_pages_hint),
                            onClick = actions.onPages,
                        )
                        ListRow(
                            title = stringResource(R.string.settings_setups),
                            subtitle = stringResource(R.string.settings_setups_hint),
                            onClick = actions.onSetups,
                            divider = false,
                        )
                        Spacer(Modifier.height(24.dp))
// Reachable even when every gesture has been unmapped, so edit mode can never be lost.
                        SectionLabel(stringResource(R.string.settings_page), modifier = Modifier.marks(SettingsTarget.PAGE, sectionOffsets))
                        ListRow(
                            title = stringResource(R.string.settings_edit_page),
                            subtitle = stringResource(R.string.settings_edit_page_hint),
                            onClick = actions.onEditPage,
                        )
                        ListRow(
                            title = stringResource(R.string.settings_rerun_setup),
                            subtitle = stringResource(R.string.settings_rerun_setup_hint),
                            onClick = actions.onRerunSetup,
                            divider = false,
                        )
                        Spacer(Modifier.height(24.dp))
                        SectionLabel("On the page")
                        ListRow(
                            title = stringResource(R.string.settings_left_gear),
                            subtitle = stringResource(R.string.settings_left_gear_hint),
                            onClick = { actions.onLeftPageGear(!preferences.leftPageGear) },
                            trailing = { NulisToggle(checked = preferences.leftPageGear, onCheckedChange = actions.onLeftPageGear) },
                            divider = false,
                        )
                    }
                    SettingsSection.LOOK -> {
                        SectionLabel(stringResource(R.string.settings_look), modifier = Modifier.marks(SettingsTarget.LOOK, sectionOffsets))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Looks.all.forEach { look ->
                                LookCard(
                                    look = look,
                                    selected = look.id == preferences.lookId,
                                    context = context,
                                    onClick = { actions.onLook(look) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(24.dp))
                        SectionLabel(stringResource(R.string.settings_background), modifier = Modifier.marks(SettingsTarget.BACKGROUND, sectionOffsets))
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ColorThemeCard(stringResource(R.string.theme_black), ColorTheme.BLACK, preferences, actions, Modifier.weight(1f))
                            ColorThemeCard(stringResource(R.string.theme_white), ColorTheme.WHITE, preferences, actions, Modifier.weight(1f))
                            ColorThemeCard(stringResource(R.string.theme_custom), ColorTheme.CUSTOM, preferences, actions, Modifier.weight(1f))
                        }
                        if (preferences.colorTheme == ColorTheme.CUSTOM) {
                            Spacer(Modifier.height(16.dp))
                            CustomColorPicker(
                                color = Color(preferences.customBackground),
                                ink = preferences.customInk?.let { Color(it) },
                                onChange = { actions.onCustomBackground(it.toArgb()) },
                                onInk = { actions.onCustomInk(it?.toArgb()) },
                            )
                            // The one readout a colour picker must have: a background and an ink
                            // can be chosen separately and be unreadable together.
                            ContrastGuard(
                                background = Color(preferences.customBackground),
                                ink = preferences.customInk?.let { Color(it) },
                                onUseReadable = { actions.onCustomInk(null) },
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        PaletteRow(
                            home = homeLayout,
                            context = context,
                            current = paletteOf(preferences),
                            onApply = actions.onPalette,
                        )
                        Spacer(Modifier.height(24.dp))
                        AccentRow(current = preferences.customAccent, onPick = actions.onAccent)
                        Spacer(Modifier.height(24.dp))
                        Box(Modifier.marks(SettingsTarget.TYPE, sectionOffsets))
                        TypographySection(
                            preferences = preferences,
                            actions = remember(actions) {
                                TypeActions(
                                    onDisplayFont = actions.onDisplayFont,
                                    onBodyFont = actions.onBodyFont,
                                    onTextScale = actions.onTextScale,
                                    onUppercaseLabels = actions.onUppercaseLabels,
                                )
                            },
                            onLicenses = onLicenses,
                        )
                        Spacer(Modifier.height(24.dp))
                        SectionLabel(stringResource(R.string.settings_display), modifier = Modifier.marks(SettingsTarget.DISPLAY, sectionOffsets))
                        ListRow(
                            title = stringResource(R.string.settings_hide_status_bar),
                            subtitle = stringResource(R.string.settings_hide_status_bar_hint),
                            onClick = { actions.onHideStatusBar(!preferences.hideStatusBar) },
                            trailing = { NulisToggle(checked = preferences.hideStatusBar, onCheckedChange = actions.onHideStatusBar) },
                        )
                        ListRow(
                            title = stringResource(R.string.settings_wallpaper),
                            subtitle = stringResource(R.string.settings_wallpaper_hint),
                            onClick = { actions.onWallpaper(!preferences.wallpaper) },
                            trailing = { NulisToggle(checked = preferences.wallpaper, onCheckedChange = actions.onWallpaper) },
                        )
                        if (preferences.wallpaper) {
                            NulisSlider(
                                stringResource(R.string.settings_wallpaper_dim),
                                preferences.wallpaperDim,
                                actions.onWallpaperDim,
                                readout = "${(preferences.wallpaperDim * 100).roundToInt()}%",
                            )
                        }
                        ListRow(
                            title = stringResource(R.string.settings_reduced_motion),
                            subtitle = stringResource(R.string.settings_reduced_motion_hint),
                            onClick = { actions.onReducedMotion(!preferences.reducedMotion) },
                            trailing = { NulisToggle(checked = preferences.reducedMotion, onCheckedChange = actions.onReducedMotion) },
                        )
                        ListRow(
                            title = stringResource(R.string.settings_show_app_usage),
                            subtitle = stringResource(R.string.settings_show_app_usage_hint),
                            onClick = { actions.onShowAppUsage(!preferences.showAppUsage) },
                            trailing = { NulisToggle(checked = preferences.showAppUsage, onCheckedChange = actions.onShowAppUsage) },
                            divider = false,
                        )
                        Spacer(Modifier.height(24.dp))
                        SectionLabel(stringResource(R.string.settings_sound), modifier = Modifier.marks(SettingsTarget.SOUND, sectionOffsets))
                        ListRow(
                            title = stringResource(R.string.settings_sound_row),
                            subtitle = stringResource(R.string.settings_sound_hint),
                            onClick = { actions.onSound(!preferences.sound) },
                            trailing = { NulisToggle(checked = preferences.sound, onCheckedChange = actions.onSound) },
                            divider = preferences.sound,
                        )
                        if (preferences.sound) {
                            NulisSlider(
                                stringResource(R.string.settings_sound_volume),
                                preferences.soundVolume,
                                actions.onSoundVolume,
                                readout = "${(preferences.soundVolume * 100).roundToInt()}%",
                            )
                        }
                    }
                    SettingsSection.APPS -> {
                        val sample = context.sampleApp
                        if (sample != null) {
                            SectionLabel(stringResource(R.string.settings_app_icons), modifier = Modifier.marks(SettingsTarget.APP_ICONS, sectionOffsets))
                            Caption(stringResource(R.string.settings_app_icons_hint))
                            Spacer(Modifier.height(8.dp))
                            // The drawer needs names, so the icon-only mode is not offered here.
                            IconStylePicker(
                                style = preferences.drawerIcons,
                                sample = sample,
                                modes = listOf(IconMode.TEXT, IconMode.MONOGRAM, IconMode.ICON_LABEL),
                                alwaysLabelled = true,
                                modeLabel = null,
                                onChange = actions.onDrawerIcons,
                            )
                            Spacer(Modifier.height(16.dp))
                            ListRow(
                                title = stringResource(R.string.settings_icon_pack),
                                subtitle = iconPacks.firstOrNull { it.packageName == preferences.iconPack }?.label
                                    ?: stringResource(R.string.settings_icon_pack_none),
                                onClick = onIconPack,
                            )
                            ListRow(
                                title = stringResource(R.string.settings_hidden_apps),
                                subtitle = if (customization.hidden.isEmpty()) stringResource(R.string.settings_hidden_apps_none) else "${customization.hidden.size}",
                                enabled = customization.hidden.isNotEmpty(),
                                onClick = onHiddenApps,
                            )
                            ListRow(
                                title = stringResource(R.string.settings_search_hidden),
                                subtitle = stringResource(R.string.settings_search_hidden_hint),
                                onClick = { actions.onSearchHidden(!preferences.searchHidden) },
                                trailing = { NulisToggle(checked = preferences.searchHidden, onCheckedChange = actions.onSearchHidden) },
                                divider = false,
                            )
                            Spacer(Modifier.height(24.dp))
                        }
                        SectionLabel(stringResource(R.string.settings_drawer), modifier = Modifier.marks(SettingsTarget.DRAWER, sectionOffsets))
                        Caption(stringResource(R.string.settings_drawer_placement))
                        Spacer(Modifier.height(4.dp))
                        SegmentedPills(
                            options = DrawerPlacement.entries,
                            selected = preferences.drawerPlacement,
                            label = { it.label },
                            onSelect = actions.onDrawerPlacement,
                        )
                        Spacer(Modifier.height(4.dp))
                        Caption(preferences.drawerPlacement.hint, lines = 2)
                        Spacer(Modifier.height(16.dp))
                        ListRow(
                            title = stringResource(R.string.settings_notification_dots),
                            subtitle = stringResource(R.string.settings_notification_dots_hint),
                            onClick = { actions.onNotificationDots(!preferences.notificationDots) },
                            trailing = {
                                NulisToggle(
                                    checked = preferences.notificationDots,
                                    onCheckedChange = actions.onNotificationDots,
                                )
                            },
                        )
                        ListRow(
                            title = stringResource(R.string.settings_categories),
                            subtitle = if (categories.all.isEmpty()) {
                                stringResource(R.string.categories_empty)
                            } else {
                                categories.all.joinToString(", ") { it.name }
                            },
                            onClick = onCategories,
                        )
                        if (categories.all.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Caption(stringResource(R.string.settings_category_display))
                            Spacer(Modifier.height(4.dp))
                            SegmentedPills(
                                options = CategoryDisplay.entries,
                                selected = runCatching { CategoryDisplay.valueOf(preferences.categoryDisplay) }.getOrDefault(CategoryDisplay.SECTIONS),
                                label = { it.label },
                                onSelect = actions.onCategoryDisplay,
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                        Caption(stringResource(R.string.settings_auto_hide))
                        Spacer(Modifier.height(4.dp))
                        // The labels are resolved here rather than in the lambda: `label` is a plain
                        // function, not a composable.
                        val offLabel = stringResource(R.string.settings_auto_hide_off)
                        val dayLabels = listOf(30, 60, 90).associateWith { pluralStringResource(R.plurals.settings_auto_hide_days, it, it) }
                        SegmentedPills(
                            options = listOf(0, 30, 60, 90),
                            selected = preferences.autoHideDays,
                            label = { days -> if (days == 0) offLabel else dayLabels[days] ?: "$days" },
                            onSelect = actions.onAutoHideDays,
                        )
                        Spacer(Modifier.height(4.dp))
                        Caption(stringResource(R.string.settings_auto_hide_hint), lines = 2)
                        Spacer(Modifier.height(8.dp))
                        ListRow(
                            title = stringResource(R.string.settings_recents),
                            subtitle = stringResource(R.string.settings_recents_hint),
                            onClick = { actions.onShowRecents(!preferences.showRecents) },
                            trailing = { NulisToggle(checked = preferences.showRecents, onCheckedChange = actions.onShowRecents) },
                        )
                        ListRow(
                            title = stringResource(R.string.settings_search_notes),
                            subtitle = stringResource(R.string.settings_search_notes_hint),
                            onClick = { actions.onSearchNotes(!preferences.searchNotes) },
                            trailing = { NulisToggle(checked = preferences.searchNotes, onCheckedChange = actions.onSearchNotes) },
                        )
                        ListRow(
                            title = stringResource(R.string.settings_search_settings),
                            subtitle = stringResource(R.string.settings_search_settings_hint),
                            onClick = { actions.onSearchSettings(!preferences.searchSettings) },
                            trailing = { NulisToggle(checked = preferences.searchSettings, onCheckedChange = actions.onSearchSettings) },
                            divider = false,
                        )
                    }
                    SettingsSection.GESTURES -> {
                        SectionLabel(stringResource(R.string.settings_gestures), modifier = Modifier.marks(SettingsTarget.GESTURES, sectionOffsets))
                        GesturesSection(
                            showLabel = false,
                            gestures = gestures,
                            apps = context.apps,
                            onPick = onPickGesture,
                            onReset = actions.onResetGestures,
                        )
                    }
                    SettingsSection.WELLBEING -> {
                        SectionLabel(stringResource(R.string.settings_wellbeing), modifier = Modifier.marks(SettingsTarget.WELLBEING, sectionOffsets))
                        ListRow(
                            title = stringResource(R.string.settings_wellbeing_apps),
                            subtitle = stringResource(R.string.settings_wellbeing_apps_hint),
                            onClick = actions.onWellbeing,
                        )
                        ListRow(
                            title = stringResource(R.string.settings_week),
                            subtitle = stringResource(R.string.settings_week_hint),
                            onClick = actions.onWeeklySummary,
                            divider = false,
                        )
                    }
                    SettingsSection.BACKUP -> {
                        SectionLabel(stringResource(R.string.settings_backup), modifier = Modifier.marks(SettingsTarget.BACKUP, sectionOffsets))
                        ListRow(
                            title = stringResource(R.string.settings_backup_row),
                            subtitle = stringResource(R.string.settings_backup_hint),
                            onClick = actions.onBackup,
                            divider = false,
                        )
                    }
                    SettingsSection.ABOUT -> AboutSection(onLicenses = onLicenses)
                }
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** Chooses the icon pack, or none at all. Rows, not tiles: packs are named, not seen. */
@Composable
private fun IconPackSheet(packs: List<IconPackInfo>, current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    NulisBottomSheet(onDismiss = onDismiss) {
        Text(stringResource(R.string.settings_icon_pack), style = NulisTheme.type.displayM, color = colors.onBackground)
        Spacer(Modifier.height(16.dp))
        val rows = listOf<Pair<String?, String>>(null to stringResource(R.string.settings_icon_pack_none)) +
            packs.map { it.packageName to it.label }
        rows.forEachIndexed { index, (packageName, label) ->
            ListRow(
                title = label,
                divider = index != rows.lastIndex,
                onClick = {
                    haptics.performHapticFeedback(NulisHaptics.tick)
                    onPick(packageName)
                    dismiss()
                },
                trailing = {
                    if (packageName == current) Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
                },
            )
        }
        if (packs.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Caption(stringResource(R.string.settings_icon_pack_empty))
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The apps kept out of the drawer, each one tap away from coming back. */
@Composable
private fun HiddenAppsScreen(
    apps: List<AppInfo>,
    iconStyle: IconStyle,
    onUnhide: (AppInfo) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NulisScreen(
        label = stringResource(R.string.settings_hidden_apps),
        title = "${apps.size}",
        onBack = onClose,
        modifier = modifier.background(NulisTheme.colors.background),
    ) {
        LazyColumn(Modifier.fillMaxSize().fadeTop()) {
            items(apps, key = { it.id }) { app ->
                ListRow(
                    title = app.label,
                    subtitle = app.packageName,
                    leading = if (iconStyle.mode.hasGlyph) ({ AppGlyph(app, iconStyle) }) else null,
                    onClick = { onUnhide(app) },
                    trailing = { PillButton(text = stringResource(R.string.menu_unhide), compact = true, onClick = { onUnhide(app) }) },
                )
            }
        }
    }
}

/** A live clock rendered in [look], regardless of the look currently applied. */
@Composable
private fun LookCard(look: Look, selected: Boolean, context: BlockContext, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val block = remember { BlockRegistry.newBlock(ClockBlockDefinition.type) }
    Column(modifier) {
        NulisCard(
            modifier = Modifier.fillMaxWidth(),
            selected = selected,
            shape = NulisShapes.tile,
            contentPadding = 12.dp,
            onClick = { if (!selected) haptics.performHapticFeedback(NulisHaptics.tick); onClick() },
        ) {
            Box(Modifier.fillMaxWidth().height(72.dp), contentAlignment = Alignment.CenterStart) {
                val type = remember(look) { NulisTypography(look) }
                CompositionLocalProvider(LocalLook provides look, LocalNulisTypography provides type) {
                    ScaledPreview(Modifier.fillMaxWidth()) {
                        ClockBlockDefinition.style(block).Render(block, context, Modifier.fillMaxWidth())
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Caption(look.label)
    }
}

/** A swatch of the theme's background with "Aa" in its text color, so the choice reads before it is applied. */
@Composable
private fun ColorThemeCard(label: String, theme: ColorTheme, preferences: UiPreferences, actions: SettingsActions, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val selected = preferences.colorTheme == theme
    val preview = if (theme == ColorTheme.CUSTOM) {
        customColors(Color(preferences.customBackground), preferences.customInk?.let { Color(it) })
    } else {
        preferences.copy(colorTheme = theme).colors
    }
    Column(modifier) {
        NulisCard(
            modifier = Modifier.fillMaxWidth(),
            selected = selected,
            shape = NulisShapes.tile,
            contentPadding = 12.dp,
            onClick = { if (!selected) haptics.performHapticFeedback(NulisHaptics.tick); actions.onTheme(theme) },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(preview.background, NulisShapes.tile)
                    .border(1.dp, preview.hairline, NulisShapes.tile),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(LocalNulisColors provides preview) {
                    Text("Aa", style = NulisTheme.type.displayS, color = preview.onBackground)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Caption(label)
    }
}

/** Inks worth offering: the phosphor colours, plus warm and cool whites. Auto is the default. */
private val Inks = listOf(
    0xFF4BE07A, 0xFFFFB000, 0xFF66D9FF, 0xFFFF6E6E, 0xFFF4EDE0, 0xFF111111,
).map { Color(it) }

@Composable
private fun CustomColorPicker(color: Color, ink: Color?, onChange: (Color) -> Unit, onInk: (Color?) -> Unit) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    // HSL is the editing model; the stored value is the resulting color.
    var hsl by remember(color) { mutableStateOf(color.toHsl()) }
    fun commit(h: Float = hsl[0], s: Float = hsl[1], l: Float = hsl[2]) {
        hsl = floatArrayOf(h, s, l)
        onChange(Color.hsl(h, s, l))
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Swatches.take(6).forEach { Swatch(it, it == color, colors) { haptics.performHapticFeedback(NulisHaptics.tick); onChange(it) } }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Swatches.drop(6).forEach { Swatch(it, it == color, colors) { haptics.performHapticFeedback(NulisHaptics.tick); onChange(it) } }
        }
        Spacer(Modifier.height(16.dp))
        NulisSlider(stringResource(R.string.settings_hue), hsl[0] / 360f, { commit(h = it * 360f) }, readout = "${hsl[0].roundToInt()}°")
        NulisSlider(stringResource(R.string.settings_saturation), hsl[1], { commit(s = it) }, readout = "${(hsl[1] * 100).roundToInt()}%")
        NulisSlider(stringResource(R.string.settings_lightness), hsl[2], { commit(l = it) }, readout = "${(hsl[2] * 100).roundToInt()}%")
        Spacer(Modifier.height(8.dp))
        // The ink is what turns a dark background into a terminal instead of a grey room.
        SectionLabel(stringResource(R.string.settings_ink), trailing = if (ink == null) stringResource(R.string.settings_ink_auto) else null)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AutoInkSwatch(selected = ink == null, colors = colors) {
                haptics.performHapticFeedback(NulisHaptics.tick)
                onInk(null)
            }
            Inks.take(5).forEach { candidate ->
                Swatch(candidate, candidate == ink, colors) {
                    haptics.performHapticFeedback(NulisHaptics.tick)
                    onInk(candidate)
                }
            }
        }
    }
}

/** The "let contrast decide" choice: a swatch split black and white. */
@Composable
private fun AutoInkSwatch(selected: Boolean, colors: com.nulis.launcher.ui.theme.NulisColors, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressFeedback()
            .size(40.dp)
            .background(Color.White, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape)) {
            Box(Modifier.fillMaxSize().background(Color.White))
            Box(Modifier.fillMaxWidth(0.5f).fillMaxSize().background(Color.Black))
        }
        Box(
            Modifier
                .size(40.dp)
                .border(if (selected) 2.dp else 1.dp, if (selected) colors.onBackground else colors.hairline, CircleShape),
        )
        if (selected) Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
    }
}

@Composable
private fun Swatch(color: Color, selected: Boolean, colors: com.nulis.launcher.ui.theme.NulisColors, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressFeedback()
            .size(40.dp)
            .background(color, CircleShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) colors.onBackground else colors.hairline, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(6.dp).background(colors.accent, CircleShape))
    }
}

/** Records where a section sits in the scroll, so a search result can jump to it. */
private fun Modifier.marks(target: SettingsTarget, offsets: MutableMap<SettingsTarget, Int>): Modifier =
    onGloballyPositioned { coordinates ->
        offsets[target] = coordinates.positionInParent().y.toInt()
    }

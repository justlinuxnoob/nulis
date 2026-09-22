// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.apps.openSystemApp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.LocalBlockActive
import androidx.compose.runtime.CompositionLocalProvider
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PagesConfig
import com.nulis.launcher.blocks.nameOf
import com.nulis.launcher.blocks.ScreenRequest
import com.nulis.launcher.blocks.screentime.ScreenTimeBlockDefinition
import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import com.nulis.launcher.blocks.clock.rememberCurrentTime
import com.nulis.launcher.blocks.music.MusicActions
import com.nulis.launcher.blocks.music.MusicBlockDefinition
import com.nulis.launcher.blocks.writing.WritingActions
import com.nulis.launcher.blocks.notes.NotesBlockDefinition
import com.nulis.launcher.apps.autoHiddenApps
import com.nulis.launcher.backup.BackupActions
import com.nulis.launcher.backup.BackupScreen
import com.nulis.launcher.drawer.AppDrawerScreen
import com.nulis.launcher.drawer.CategoryDisplay
import com.nulis.launcher.drawer.DrawerActions
import com.nulis.launcher.drawer.DrawerPlacement
import com.nulis.launcher.drawer.SettingsTarget
import com.nulis.launcher.gestures.GestureAction
import com.nulis.launcher.gestures.GestureTrigger
import com.nulis.launcher.gestures.expandNotifications
import com.nulis.launcher.gestures.outsideSystemGestures
import com.nulis.launcher.gestures.expandQuickSettings
import com.nulis.launcher.home.AddBlockScreen
import com.nulis.launcher.icons.LocalIconLoader
import com.nulis.launcher.icons.MaxIconPx
import com.nulis.launcher.home.BlockOptionsSheet
import com.nulis.launcher.home.HomeScreen
import com.nulis.launcher.home.NoRoomSheet
import com.nulis.launcher.home.PageEditor
import com.nulis.launcher.home.PageOptionsSheet
import com.nulis.launcher.layout.LayoutPickerScreen
import com.nulis.launcher.layout.PagesScreen
import com.nulis.launcher.layout.LayoutPresets
import com.nulis.launcher.notifications.LocalNotificationDots
import com.nulis.launcher.notifications.NotificationDots
import com.nulis.launcher.onboarding.OnboardingActions
import com.nulis.launcher.onboarding.OnboardingPermission
import com.nulis.launcher.onboarding.OnboardingScreen
import com.nulis.launcher.onboarding.rememberActivityRecognitionRequest
import com.nulis.launcher.blocks.music.openNotificationAccessSettings
import com.nulis.launcher.blocks.screentime.openUsageAccessSettings
import com.nulis.launcher.settings.SettingsActions
import com.nulis.launcher.setups.SetupActions
import com.nulis.launcher.wellbeing.MindfulPauseScreen
import com.nulis.launcher.wellbeing.PendingLaunch
import com.nulis.launcher.wellbeing.WeeklySummaryScreen
import com.nulis.launcher.wellbeing.WellbeingActions
import com.nulis.launcher.wellbeing.WellbeingScreen
import com.nulis.launcher.wellbeing.pauseDecision
import com.nulis.launcher.setups.SetupsScreen
import com.nulis.launcher.settings.SettingsScreen
import com.nulis.launcher.ui.components.rememberSlideState
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.LineStyle
import com.nulis.launcher.ui.theme.NulisMotion
import com.nulis.launcher.widgets.LocalWidgetHost
import com.nulis.launcher.ui.theme.ColorTheme
import com.nulis.launcher.ui.theme.Fonts
import com.nulis.launcher.ui.theme.Looks
import com.nulis.launcher.ui.theme.NulisTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Root of the launcher UI: three pages (left, home, right) in a finger-tracked pager, with the
 * drawer, edit mode, add-block screen, settings and block options layered on top.
 */
@Composable
fun LauncherRoute(
    viewModel: LauncherViewModel,
    onLaunch: (AppInfo, Rect?) -> Unit,
    onAppInfo: (AppInfo) -> Unit,
    onUninstall: (AppInfo) -> Unit,
) {
    val apps by viewModel.allApps.collectAsStateWithLifecycle()
    val layouts by viewModel.layouts.collectAsStateWithLifecycle()
    val pagesConfig by viewModel.pages.collectAsStateWithLifecycle()
    val newPage by viewModel.newPage.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val homeFavorites by viewModel.homeFavorites.collectAsStateWithLifecycle()
    val hasAppsBlock by viewModel.hasAppsBlock.collectAsStateWithLifecycle()
    val preferences by viewModel.uiPreferences.collectAsStateWithLifecycle()
    val battery by viewModel.battery.collectAsStateWithLifecycle()
    val nextAlarm by viewModel.nextAlarm.collectAsStateWithLifecycle()
    val homeRequests by viewModel.homeRequests.collectAsStateWithLifecycle()
    val writing by viewModel.writing.collectAsStateWithLifecycle()
    val screenTime by viewModel.screenTime.collectAsStateWithLifecycle()
    val steps by viewModel.steps.collectAsStateWithLifecycle()
    val music by viewModel.music.collectAsStateWithLifecycle()
    val calendar by viewModel.calendar.collectAsStateWithLifecycle()
    val focus by viewModel.focus.collectAsStateWithLifecycle()
    val wellbeing by viewModel.wellbeing.collectAsStateWithLifecycle()

    // A focus session guards every app that is not on the allow list.
    val focusRunning = focus.running?.isBreak == false
    val mutedPackages = remember(apps, wellbeing, focusRunning) {
        if (!focusRunning || !wellbeing.focusGuards) {
            emptySet()
        } else {
            apps.asSequence()
                .filterNot { it.isNulisSettings || it.packageName in wellbeing.focusAllowed }
                .map { it.packageName }
                .toSet()
        }
    }

    val storedGestures by viewModel.gestures.collectAsStateWithLifecycle()
    val customization by viewModel.customization.collectAsStateWithLifecycle()
    val iconPacks by viewModel.iconPacks.collectAsStateWithLifecycle()
    val iconLoader = LocalIconLoader.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val appContext = LocalContext.current
    val scope = rememberCoroutineScope()

    var resumeCount by remember { mutableIntStateOf(0) }
    // Nothing animates while the launcher is in the background.
    var resumed by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        resumed = true
        resumeCount++
        viewModel.refreshApps()
        viewModel.refreshScreenTime()
        viewModel.refreshMusicAccess()
        viewModel.setVisible(true)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) {
        resumed = false
        viewModel.setVisible(false)
    }
    val time = rememberCurrentTime(refreshKey = resumeCount)

    // The drawer is always composed and slides over the pages; a light tick marks it committing to open.
    val drawer = rememberSlideState(target = false) { shown ->
        if (shown) haptics.performHapticFeedback(NulisHaptics.threshold)
    }
    // Launching from the drawer leaves it open behind the reveal; it is gone by the time we come back.
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { drawer.snapHidden() }
    // The very first draw of the drawer costs the render thread ~50 ms (glyph atlas, shader
    // programs), which would land mid-slide on the first open. So once the app list exists, draw
    // the drawer for a couple of frames at its open position but behind the opaque pages.
    var warmingDrawer by remember { mutableStateOf(false) }
    LaunchedEffect(apps.isNotEmpty()) {
        if (apps.isEmpty()) return@LaunchedEffect
        warmingDrawer = true
        repeat(3) { withFrameNanos { } }
        warmingDrawer = false
    }

    // Every app's icon is processed in the background as soon as the list and the style are
    // known, so a fling only ever reads from memory. Cheap after the first run: the disk cache
    // turns each one into a file read.
    val drawerIcons = preferences.drawerIcons
    val drawerPixel = drawerIcons.pixel(dots = NulisTheme.look.lineStyle == LineStyle.DOTTED)
    LaunchedEffect(apps, drawerIcons.color, drawerIcons.shape, drawerIcons.size, drawerIcons.mode, drawerPixel, iconLoader.generation) {
        if (!drawerIcons.mode.hasIcon || apps.isEmpty()) return@LaunchedEffect
        val px = with(density) { drawerIcons.size.dp.roundToPx() }.coerceAtMost(MaxIconPx)
        iconLoader.prewarm(apps, drawerIcons.color, drawerIcons.shape, px, drawerPixel)
    }

    // Where the drawer lives, and what that does to the pager.
    //
    // On a page, the drawer is simply one more thing to swipe to: an extra pager page before the
    // first or after the last. Everything that used to index pages by their position in
    // PagesConfig therefore goes through [pageOffset] - one when the drawer sits on the left,
    // none otherwise - because a page's position on screen and its position in the user's page
    // list stopped being the same number.
    val drawerPlacement = preferences.drawerPlacement
    val drawerIsPage = drawerPlacement.isPage
    val pageOffset = if (drawerPlacement == DrawerPlacement.LEFT_PAGE) 1 else 0
    val pageCount = pagesConfig.ids.size + if (drawerIsPage) 1 else 0
    val drawerPageIndex = when (drawerPlacement) {
        DrawerPlacement.LEFT_PAGE -> 0
        DrawerPlacement.RIGHT_PAGE -> pageCount - 1
        else -> -1
    }
    val homeIndex = pagesConfig.homeIndex + pageOffset

    // The gestures the page actually obeys: the stored ones, plus what moving the drawer does
    // to the upward swipe and to whether this launcher can be locked out of itself at all.
    val gestures = remember(storedGestures, drawerPlacement) {
        storedGestures.copy(
            swipeUpIsFree = drawerPlacement.freesSwipeUp,
            drawerIsAPage = drawerIsPage,
        )
    }

    val pager = rememberPagerState(initialPage = PageIds.homeIndex) { pageCount }
    // The page list is read from disk, so the first composition uses the three-page default.
    // The moment the real one arrives, the launcher lands on whichever page is marked home -
    // once only, so a swipe made in that first moment is not undone underneath the finger.
    var homeSettled by remember { mutableStateOf(false) }
    LaunchedEffect(pagesConfig, drawerPlacement) {
        if (homeSettled) return@LaunchedEffect
        homeSettled = true
        if (pager.currentPage != homeIndex) pager.scrollToPage(homeIndex)
    }
    // Moving the drawer changes what index home is at; the launcher stays on home rather than
    // ending up one page to the side of wherever it was.
    var lastPlacement by remember { mutableStateOf(drawerPlacement) }
    LaunchedEffect(drawerPlacement) {
        if (drawerPlacement == lastPlacement) return@LaunchedEffect
        lastPlacement = drawerPlacement
        pager.scrollToPage(homeIndex.coerceIn(0, (pageCount - 1).coerceAtLeast(0)))
    }
    // A page just added is a page you want to be looking at.
    LaunchedEffect(newPage) {
        val id = newPage ?: return@LaunchedEffect
        val index = pagesConfig.indexOf(id)
        if (index >= 0) {
            pager.animateScrollToPage(index + pageOffset)
            viewModel.newPageShown()
        }
    }
    // The editor belongs to the page it was opened on; paging is off while it is on.
    var editPageId by rememberSaveable { mutableStateOf<String?>(null) }
    var editSelection by rememberSaveable { mutableStateOf<String?>(null) }
    var addBlockOpen by rememberSaveable { mutableStateOf(false) }
    // Held as an explicit state object so the launch interception below can be remembered.
    val settingsOpenState = rememberSaveable { mutableStateOf(false) }
    var setupsOpen by rememberSaveable { mutableStateOf(false) }
    var layoutsOpen by rememberSaveable { mutableStateOf(false) }
    var pagesOpen by rememberSaveable { mutableStateOf(false) }
    // A block type the current page had no room for, waiting for the user to say what to do.
    var noRoomType by rememberSaveable { mutableStateOf<String?>(null) }
    // A block type the picker chose, handed to whichever page's editor is open.
    var pendingAdd by rememberSaveable { mutableStateOf<String?>(null) }
    var settingsSection by remember { mutableStateOf<SettingsTarget?>(null) }
    var wellbeingOpen by rememberSaveable { mutableStateOf(false) }
    var weekOpen by rememberSaveable { mutableStateOf(false) }
    var backupOpen by rememberSaveable { mutableStateOf(false) }
    var settingsOpen by settingsOpenState
    var optionsBlockId by rememberSaveable { mutableStateOf<String?>(null) }
    var pageOptionsId by rememberSaveable { mutableStateOf<String?>(null) }
    var screenRequest by remember { mutableStateOf<ScreenRequest?>(null) }
    val currentPageId = pagesConfig.ids.getOrElse(pager.currentPage - pageOffset) { pagesConfig.homeId }
    val onDrawerPage = drawerIsPage && pager.currentPage == drawerPageIndex
    // The page the editor is open on, if it is open at all. Everything downstream asks this
    // rather than asking whether an id has been remembered.
    val editing = editPageId?.let { id -> layouts[id]?.let { id to it } }
    // Edit mode is the editor being *on screen*, not an id sitting in a variable.
    //
    // It used to be `editPageId != null`, and an id that named a page the launcher could not
    // resolve - one removed, one from a layout that has since been replaced, one saved into the
    // instance state of a process that has been restarted - left the whole launcher in an edit
    // mode with no editor in it: every block's context became the inert one, so taps did nothing,
    // apps would not launch, and there was no editor on screen to press Done in. A dead home
    // screen with no way out is the worst failure this app has, so the flag now says what is
    // true rather than what was intended.
    val editMode = editing != null
    // An unused widget id goes back to the system here and nowhere else.
    //
    // Deleting the block cannot release it: Undo puts the block back, and an id that has been
    // given away comes back as an empty rectangle. The undo stack lives and dies with the
    // editor, so once the editor is off the screen a widget id that no saved page and no saved
    // setup mentions is one nothing can reach. The wait is the editor's own fade: while it is
    // still fading the bar is still there to be tapped.
    val widgetHost = LocalWidgetHost.current
    LaunchedEffect(editMode, widgetHost) {
        if (!editMode) {
            delay(NulisMotion.quick.toLong() * 2)
            viewModel.releaseUnusedWidgetIds(widgetHost)
        }
    }
    // And the stale id itself goes, so the editor does not spring open later if that page
    // happens to come back.
    LaunchedEffect(editPageId, pagesConfig.ids) {
        val id = editPageId
        if (id != null && id !in pagesConfig.ids) {
            editPageId = null
            editSelection = null
        }
    }
    // Whether the drawer should raise the keyboard when it opens: decided by whatever opened it.
    var drawerSearch by remember { mutableStateOf(false) }

    // Nulis's own drawer row is not an activity: it opens settings. Every app list goes through
    // this, so the escape hatch works from the drawer, an Apps block or a gesture alike.
    val launch: (AppInfo, Rect?) -> Unit = remember(onLaunch, drawer, settingsOpenState) {
        { app, origin ->
            if (app.isNulisSettings) {
                drawer.hide()
                settingsOpenState.value = true
            } else {
                onLaunch(app, origin)
            }
        }
    }

    // A launch that starts in Nulis may get a breath first: an app the user asked to pause, one
    // past a daily number they set, or anything during a focus session. Nothing is ever blocked,
    // and an app opened from anywhere else never comes through here at all.
    var pending by remember { mutableStateOf<PendingLaunch?>(null) }
    val guardedLaunch: (AppInfo, Rect?) -> Unit = { app, origin ->
        val minutes = screenTime.minutesByPackage[app.packageName] ?: 0
        val decision = pauseDecision(
            packageName = app.packageName,
            wellbeing = wellbeing,
            minutesToday = minutes,
            focusRunning = focusRunning,
        )
        if (app.isNulisSettings || decision.reason == null) {
            launch(app, origin)
        } else {
            drawer.hide()
            pending = PendingLaunch(app, decision, wellbeing.pauseSeconds)
        }
    }

    val onGesture: (GestureTrigger) -> Unit = { trigger ->
        val binding = gestures[trigger]
        when (binding.action) {
            GestureAction.NOTHING, GestureAction.LOCK_SCREEN -> Unit
            // On a page, "open the drawer" is "swipe to the drawer"; everywhere else it is
            // still the sheet, which is what keeps search reachable when the drawer is Off.
            GestureAction.OPEN_DRAWER ->
                if (drawerIsPage) scope.launch { pager.animateScrollToPage(drawerPageIndex) }
                else { drawerSearch = false; drawer.show() }
            GestureAction.SEARCH_APPS ->
                if (drawerIsPage) scope.launch { pager.animateScrollToPage(drawerPageIndex) }
                else { drawerSearch = true; drawer.show() }
            GestureAction.NOTIFICATIONS -> expandNotifications(appContext)
            GestureAction.QUICK_SETTINGS -> expandQuickSettings(appContext)
            GestureAction.OPEN_APP -> apps.firstOrNull { it.id == binding.appId }?.let { guardedLaunch(it, null) }
            GestureAction.PAGE_LEFT -> scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) }
            GestureAction.PAGE_RIGHT -> scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(pageCount - 1)) }
            GestureAction.EDIT_MODE -> { editSelection = null; editPageId = currentPageId }
            GestureAction.SETTINGS -> settingsOpen = true
            GestureAction.TOGGLE_LOOK -> viewModel.setLook(Looks.all[(Looks.all.indexOfFirst { it.id == preferences.lookId } + 1) % Looks.all.size].id)
            GestureAction.TOGGLE_THEME -> viewModel.setColorTheme(if (preferences.colors.isDark) ColorTheme.WHITE else ColorTheme.BLACK)
            GestureAction.NEW_NOTE -> screenRequest = ScreenRequest(NotesBlockDefinition.type, NotesBlockDefinition.NEW)
        }
    }
    // The type row in Look and colours previews all six bundled faces, and a typeface is parsed
    // on the main thread the first time anything asks to draw with it. Six of those inside the
    // first frame of a screen transition is what made settings' first scroll the worst gesture
    // in the launcher. Opening the settings index - which is seven rows and costs nothing -
    // resolves them in the background, so by the time a section is tapped they are ready.
    val fontResolver = LocalFontFamilyResolver.current
    LaunchedEffect(settingsOpen) {
        if (!settingsOpen) return@LaunchedEffect
        Fonts.all.forEach { font -> runCatching { fontResolver.preload(font.family) } }
    }

    // A swipe that drags the drawer up itself never goes through [onGesture], so the search
    // decision is taken here instead, the moment a finger starts pulling a closed drawer open.
    LaunchedEffect(drawer.dragging) {
        if (drawer.dragging && !drawer.target) drawerSearch = gestures.action(GestureTrigger.SWIPE_UP) == GestureAction.SEARCH_APPS
    }

    val showAppUsage = preferences.showAppUsage
    val homeApps = remember(apps, homeFavorites) { homeFavorites.mapNotNull { id -> apps.firstOrNull { it.id == id } } }
    // A block that changes its own settings from the page - the apps block choosing its apps -
    // knows its id and nothing else, so the page it belongs to is found the same way the options
    // sheet finds it. Stable across recomposition so it does not re-make the context every frame.
    val updateAnyBlock: (Block) -> Unit = remember(viewModel) {
        { block ->
            val pageId = viewModel.pageIdOf(block.id)
            if (pageId != null) viewModel.actionsFor(pageId).update(block)
        }
    }
    val blockContext = remember(time, apps, homeApps, editMode, launch, battery, nextAlarm, writing, screenTime, showAppUsage, steps, music, drawerIcons, calendar, focus, mutedPackages, wellbeing.focusHides) {
        BlockContext(
            time = time,
            apps = apps,
            homeApps = homeApps,
            onLaunchApp = if (editMode) ({ _, _ -> }) else guardedLaunch,
            battery = battery,
            nextAlarm = nextAlarm.at,
            writing = writing,
            writingActions = if (editMode) WritingActions.None else viewModel,
            screenTime = screenTime,
            steps = steps,
            stepsActions = viewModel,
            calendar = calendar,
            calendarActions = viewModel,
            focus = focus,
            focusActions = if (editMode) com.nulis.launcher.blocks.focus.FocusActions.None else viewModel,
            music = music,
            musicActions = if (editMode) MusicActions.None else viewModel,
            appUsage = if (showAppUsage && screenTime.granted) screenTime.minutesByPackage else null,
            appIcons = drawerIcons,
            mutedPackages = mutedPackages,
            muteHides = wellbeing.focusHides,
            openScreen = if (editMode) ({ }) else ({ screenRequest = it }),
            openSystemApp = if (editMode) ({ }) else ({ target -> openSystemApp(appContext, target) }),
            openWeek = if (editMode) ({ }) else ({ weekOpen = true }),
            // Block ids are unique across pages, so the block itself says which page to write.
            updateBlock = if (editMode) ({ }) else updateAnyBlock,
            openBlockOptions = if (editMode) ({ }) else ({ block -> optionsBlockId = block.id }),
        )
    }

    // With the wallpaper showing through, the page's own background becomes the dim over it.
    val pageBackground = if (preferences.wallpaper) {
        NulisTheme.colors.background.copy(alpha = preferences.wallpaperDim)
    } else {
        NulisTheme.colors.background
    }

    val setups by viewModel.setups.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val weeklyScreenTime by viewModel.weeklyScreenTime.collectAsStateWithLifecycle()
    val backupStatus by viewModel.backupStatus.collectAsStateWithLifecycle()
    val appUsageState by viewModel.appUsage.collectAsStateWithLifecycle()
    val categoryDisplay = remember(preferences.categoryDisplay) {
        runCatching { CategoryDisplay.valueOf(preferences.categoryDisplay) }.getOrDefault(CategoryDisplay.SECTIONS)
    }
    // An app in a group or on the home page is never rested for going unused: the user has said
    // out loud that they want it there.
    val autoHiddenIds = remember(apps, appUsageState, preferences.autoHideDays, favoriteIds, categories) {
        autoHiddenApps(
            apps = apps,
            usage = appUsageState,
            days = preferences.autoHideDays,
            keepIds = favoriteIds + categories.byApp.keys,
        )
    }
    val recents = remember(apps, appUsageState, preferences.showRecents) {
        if (!preferences.showRecents || !appUsageState.granted) {
            emptyList()
        } else {
            val byPackage = apps.filterNot { it.isNulisSettings }.associateBy { it.packageName }
            appUsageState.recentPackages.mapNotNull { byPackage[it] }.take(8)
        }
    }

    val drawerActions = remember(viewModel) {
        DrawerActions(
            onToggleFavorite = viewModel::toggleFavorite,
            onRename = viewModel::renameApp,
            onSetIcon = viewModel::setAppIcon,
            onSetHidden = viewModel::setAppHidden,
            onAssignCategory = { app, categoryId -> viewModel.assignCategory(app.id, categoryId) },
            onCreateCategory = viewModel::createCategory,
            onAppInfo = onAppInfo,
            onUninstall = onUninstall,
            onOpenNote = { noteId ->
                drawer.hide()
                screenRequest = ScreenRequest(NotesBlockDefinition.type, noteId)
            },
            onOpenTasks = {
                drawer.hide()
                screenRequest = ScreenRequest("tasks")
            },
            onOpenSettings = { target ->
                drawer.hide()
                settingsSection = target
                settingsOpenState.value = true
            },
        )
    }

    // The drawer, once, as a slot. Two things can host it: the sheet that slides up over the
    // pages, and a page of its own inside the pager. They differ only in how they arrive and how
    // you leave, which is exactly the five parameters below.
    val drawerSlot: @Composable (Boolean, Boolean, Boolean, () -> Unit, Modifier) -> Unit =
        { drawerShown, drawerSettled, wantsSearch, closeDrawer, drawerModifier ->
            AppDrawerScreen(
                apps = apps,
                favoriteIds = favoriteIds,
                usageMinutes = blockContext.appUsage,
                iconStyle = preferences.drawerIcons,
                hiddenIds = customization.hidden,
                searchHidden = preferences.searchHidden,
                autoHiddenIds = autoHiddenIds,
                categories = categories,
                categoryDisplay = categoryDisplay,
                recents = recents,
                writing = writing,
                searchNotes = preferences.searchNotes,
                searchSettings = preferences.searchSettings,
                mutedPackages = mutedPackages,
                muteHides = wellbeing.focusHides,
                hasAppsBlock = hasAppsBlock,
                canAddFavorite = hasAppsBlock && favoriteIds.size < AppsBlockDefinition.MAX_FAVORITES,
                open = drawerShown,
                settled = drawerSettled,
                focusSearch = wantsSearch,
                onLaunch = guardedLaunch,
                actions = drawerActions,
                onClose = closeDrawer,
                modifier = drawerModifier,
            )
        }

    // The dots every app list draws, decided once: off unless the user asked for them and the
    // listener is actually connected, so revoking notification access clears them by itself.
    val liveDots by NotificationDots.packages.collectAsStateWithLifecycle()
    val notificationDots = if (preferences.notificationDots && music.granted) liveDots else emptySet()

    val drawerOpen = drawer.target
    val onHomePage = pager.currentPage == homeIndex && !pager.isScrollInProgress
    // Back on a side page returns to home; on home there is nowhere to go. Overlays register their own handlers.
    BackHandler(enabled = !drawerOpen && !editMode) { }
    LaunchedEffect(homeRequests) {
        if (homeRequests == 0) return@LaunchedEffect
        // The Home key means "show me my home page", and on a launcher that is the one gesture
        // everybody trusts to get them out of wherever they are. So everything standing in front
        // of the page goes with it: the drawer, every settings and block screen, every sheet, and
        // edit mode. Closing them in front-to-back order keeps a sheet from flashing over a screen
        // that is still there.
        drawer.hide()
        screenRequest = null
        optionsBlockId = null
        pageOptionsId = null
        addBlockOpen = false
        backupOpen = false
        weekOpen = false
        wellbeingOpen = false
        setupsOpen = false
        layoutsOpen = false
        pagesOpen = false
        settingsSection = null
        settingsOpen = false
        editPageId = null
        pager.animateScrollToPage(homeIndex)
    }
    BackHandler(enabled = !drawerOpen && !editMode && !onHomePage) { viewModel.requestHome() }

    CompositionLocalProvider(LocalNotificationDots provides notificationDots) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            // Before anything else in here sees a touch: the strips along the edges of the
            // screen belong to home, recents and back, and a launcher that also answers them is
            // two things answering one gesture.
            .outsideSystemGestures()
            .onSizeChanged { drawer.travelPx = it.height.toFloat().coerceAtLeast(1f) },
    ) {
        HorizontalPager(
            state = pager,
            userScrollEnabled = !editMode,
            beyondViewportPageCount = pageCount,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val p = drawer.progress
                    val scale = lerp(1f, NulisMotion.homeBehindDrawerScale, p)
                    scaleX = scale
                    scaleY = scale
                    alpha = if (p >= 1f) 0f else lerp(1f, NulisMotion.homeBehindDrawerAlpha, p)
                    // Fade each draw op instead of rendering the whole page through a full-screen layer.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        ) { index ->
            if (index == drawerPageIndex) {
                // The drawer as a page: no sheet, no scrim, no drag to track. It is open
                // whenever you are looking at it, its list scrolls the way any list does, and
                // Back goes home rather than closing anything.
                drawerSlot(
                    pager.currentPage == index,
                    pager.currentPage == index && !pager.isScrollInProgress,
                    false,
                    { scope.launch { pager.animateScrollToPage(homeIndex) } },
                    Modifier.fillMaxSize(),
                )
                return@HorizontalPager
            }
            val pageId = pagesConfig.ids.getOrElse(index - pageOffset) { pagesConfig.homeId }
            // Every page stays composed so paging never composes mid-gesture; an animated block
            // on a page you are not looking at must still cost nothing. While a swipe is in
            // flight every page counts as visible, because two of them are.
            CompositionLocalProvider(
                LocalBlockActive provides (
                    (pageId == currentPageId || pager.isScrollInProgress) &&
                        drawer.progress < 1f &&
                        resumed
                    ),
            ) {
            HomeScreen(
                layout = layouts[pageId],
                context = blockContext,
                drawer = drawer,
                gestures = gestures,
                onGesture = onGesture,
                // Both ways in lead to the same place: the editor, on this page. A block long
                // press arrives with that block picked, so a page with no empty space left on it
                // is never a dead end.
                onEditBlock = { block ->
                    editSelection = block.id
                    editPageId = pageId
                },
                onEditPage = {
                    editSelection = null
                    editPageId = pageId
                },
                onOpenSettings = { settingsOpen = true },
                background = pageBackground,
                settingsGear = preferences.leftPageGear && pageId == pagesConfig.ids.first(),
            )
            }
        }

        PageDots(pager, homeIndex = homeIndex, drawerIndex = drawerPageIndex, visible = true, modifier = Modifier.align(Alignment.BottomCenter))

        // The editor is the page: the same blocks, the same data, one step back. It sits over
        // the pager rather than inside it so that paging, the drawer and every page gesture stop
        // existing while it is open.
        AnimatedVisibility(
            visible = editing != null,
            enter = fadeIn(tween(NulisMotion.quick)),
            exit = fadeOut(tween(NulisMotion.quick)),
            // Over the pager, under everything that opens on top of the editor: the block
            // picker, the sheets, settings. Those are all declared after it.
            modifier = Modifier.zIndex(1f),
        ) {
            val page = editing ?: return@AnimatedVisibility
            PageEditor(
                layout = page.second,
                pageName = pagesConfig.nameOf(page.first),
                context = blockContext,
                initialSelection = editSelection,
                onWrite = { next -> viewModel.actionsFor(page.first).updatePage { next } },
                onDone = { editPageId = null; editSelection = null },
                onAdd = { addBlockOpen = true },
                pendingAdd = pendingAdd,
                onAddHandled = { pendingAdd = null },
                onOpenSettings = { settingsOpen = true },
                onPageOptions = { pageOptionsId = page.first },
                onBlockOptions = { optionsBlockId = it.id },
                onNoRoom = { type -> noRoomType = type },
                coachHint = !preferences.editCoachSeen,
                onCoachSeen = viewModel::setEditCoachSeen,
            )
        }

        noRoomType?.let { type ->
            NoRoomSheet(
                blockName = BlockRegistry.definition(type)?.label,
                pages = pagesConfig,
                onShrink = {
                    noRoomType = null
                    addBlockOpen = false
                    editSelection = null
                    editPageId = editPageId ?: currentPageId
                },
                onNewPage = {
                    viewModel.addBlockOnNewPage(type, currentPageId)
                    noRoomType = null
                    addBlockOpen = false
                },
                onDismiss = { noRoomType = null },
            )
        }

        AnimatedVisibility(
            visible = addBlockOpen,
            enter = fadeIn(tween(NulisMotion.normal)) + slideInVertically(tween(NulisMotion.normal)) { it / 8 },
            exit = fadeOut(tween(NulisMotion.quick)) + slideOutVertically(tween(NulisMotion.quick)) { it / 8 },
            // On the wrapper, not on the screen inside it: only a direct child of this Box gets
            // to say where it sits in the stack, and the editor is a direct child.
            modifier = Modifier.zIndex(2f),
        ) {
            AddBlockScreen(
                context = blockContext,
                onAdd = { type ->
                    // The editor places it, so that adding a block is one of the things Undo can
                    // take back. A page is exactly one screen, so this can honestly fail, and
                    // when it does the editor says so with a choice rather than a shrug.
                    pendingAdd = type
                    addBlockOpen = false
                },
                onClose = { addBlockOpen = false },
            )
        }

        AnimatedVisibility(
            visible = settingsOpen,
            enter = fadeIn(tween(NulisMotion.normal)) + slideInVertically(tween(NulisMotion.normal)) { it / 8 },
            exit = fadeOut(tween(NulisMotion.quick)) + slideOutVertically(tween(NulisMotion.quick)) { it / 8 },
            // On the wrapper, not on the screen inside it. A full-screen surface that
            // can be reached from the editor's header has to outrank the editor, and
            // only a direct child of this Box gets to say where it sits in the stack.
            modifier = Modifier.zIndex(FullScreenZ),
        ) {
            // Looking for installed packs touches the package manager; only do it when asked.
            LaunchedEffect(Unit) { viewModel.refreshIconPacks() }
            SettingsScreen(
                preferences = preferences,
                gestures = gestures,
                context = blockContext,
                iconPacks = iconPacks,
                customization = customization,
                categories = categories,
                homeLayout = layouts[PageIds.HOME],
                actions = remember(viewModel) {
                    SettingsActions(
                        onLook = { viewModel.setLook(it.id) },
                        onTheme = viewModel::setColorTheme,
                        onCustomBackground = viewModel::setCustomBackground,
                        onCustomInk = viewModel::setCustomInk,
                        onHideStatusBar = viewModel::setHideStatusBar,
                        onWallpaper = viewModel::setWallpaper,
                        onWallpaperDim = viewModel::setWallpaperDim,
                        onSetups = { setupsOpen = true },
                        onLayouts = { layoutsOpen = true },
                        onPages = { pagesOpen = true },
                        onLeftPageGear = viewModel::setLeftPageGear,
                        onAccent = viewModel::setCustomAccent,
                        onPalette = viewModel::applyPalette,
                        onCategoryDisplay = viewModel::setCategoryDisplay,
                        onAutoHideDays = viewModel::setAutoHideDays,
                        onShowRecents = viewModel::setShowRecents,
                        onSearchNotes = viewModel::setSearchNotes,
                        onSearchSettings = viewModel::setSearchSettings,
                        onCreateCategory = viewModel::createCategory,
                        onRenameCategory = viewModel::renameCategory,
                        onDeleteCategory = viewModel::deleteCategory,
                        onAssignCategory = viewModel::assignCategory,
                        onMoveCategory = viewModel::moveCategory,
                        onWellbeing = { wellbeingOpen = true },
                        onWeeklySummary = { weekOpen = true },
                        onBackup = { backupOpen = true },
                        onSound = viewModel::setSound,
                        onSoundVolume = viewModel::setSoundVolume,
                        onReducedMotion = viewModel::setReducedMotion,
                        onDisplayFont = viewModel::setDisplayFont,
                        onBodyFont = viewModel::setBodyFont,
                        onTextScale = viewModel::setTextScale,
                        onUppercaseLabels = viewModel::setUppercaseLabels,
                        onDrawerIcons = viewModel::setDrawerIcons,
                        onIconPack = viewModel::setIconPack,
                        onSearchHidden = viewModel::setSearchHidden,
                        onDrawerPlacement = viewModel::setDrawerPlacement,
                        // Turning the dots on without notification access sends you to the same
                        // explanation screen the music block uses: one grant, explained once.
                        onNotificationDots = { on ->
                            viewModel.setNotificationDots(on)
                            if (on && !viewModel.music.value.granted) {
                                screenRequest = ScreenRequest(MusicBlockDefinition.type, MusicBlockDefinition.PERMISSION)
                            }
                        },
                        onUnhide = { viewModel.setAppHidden(it, false) },
                        onShowAppUsage = { show ->
                            viewModel.setShowAppUsage(show)
                            if (show && !viewModel.hasUsageAccess()) screenRequest = ScreenRequest(ScreenTimeBlockDefinition.type, ScreenTimeBlockDefinition.PERMISSION)
                        },
                        onGesture = viewModel::setGesture,
                        onResetGestures = viewModel::resetGestures,
                        onEditPage = { settingsOpenState.value = false; editSelection = null; editPageId = currentPageId },
                        onRerunSetup = { settingsOpenState.value = false; viewModel.setOnboarded(false) },
                    )
                },
                scrollTo = settingsSection,
                onScrolled = { settingsSection = null },
                onClose = { settingsOpen = false },
            )
        }

        AnimatedVisibility(
            visible = layoutsOpen,
            enter = fadeIn(tween(NulisMotion.normal)) + slideInVertically(tween(NulisMotion.normal)) { it / 8 },
            exit = fadeOut(tween(NulisMotion.quick)) + slideOutVertically(tween(NulisMotion.quick)) { it / 8 },
            // On the wrapper, not on the screen inside it. A full-screen surface that
            // can be reached from the editor's header has to outrank the editor, and
            // only a direct child of this Box gets to say where it sits in the stack.
            modifier = Modifier.zIndex(FullScreenZ),
        ) {
            LayoutPickerScreen(
                context = blockContext,
                currentId = preferences.layoutId,
                pageCount = pagesConfig.ids.size,
                onApply = { preset, keepExtra ->
                    viewModel.applyLayout(preset, keepExtra)
                    layoutsOpen = false
                },
                onClose = { layoutsOpen = false },
            )
        }

        AnimatedVisibility(
            visible = pagesOpen,
            enter = fadeIn(tween(NulisMotion.normal)) + slideInVertically(tween(NulisMotion.normal)) { it / 8 },
            exit = fadeOut(tween(NulisMotion.quick)) + slideOutVertically(tween(NulisMotion.quick)) { it / 8 },
            // On the wrapper, not on the screen inside it. A full-screen surface that
            // can be reached from the editor's header has to outrank the editor, and
            // only a direct child of this Box gets to say where it sits in the stack.
            modifier = Modifier.zIndex(FullScreenZ),
        ) {
            PagesScreen(
                pages = pagesConfig,
                layouts = layouts,
                context = blockContext,
                actions = viewModel,
                onClose = { pagesOpen = false },
            )
        }

        AnimatedVisibility(
            visible = setupsOpen,
            enter = fadeIn(tween(NulisMotion.normal)) + slideInVertically(tween(NulisMotion.normal)) { it / 8 },
            exit = fadeOut(tween(NulisMotion.quick)) + slideOutVertically(tween(NulisMotion.quick)) { it / 8 },
            // On the wrapper, not on the screen inside it. A full-screen surface that
            // can be reached from the editor's header has to outrank the editor, and
            // only a direct child of this Box gets to say where it sits in the stack.
            modifier = Modifier.zIndex(FullScreenZ),
        ) {
            SetupsScreen(
                setups = setups,
                appliedId = preferences.setupId,
                context = blockContext,
                actions = remember(viewModel) {
                    SetupActions(
                        onApply = viewModel::applySetup,
                        onSaveCurrent = viewModel::saveCurrentSetup,
                        onRename = viewModel::renameSetup,
                        onDelete = viewModel::deleteSetup,
                    )
                },
                onClose = { setupsOpen = false },
            )
        }

        // A block's full-screen surface (note editor, journal, tasks), above the pages, below the drawer.
        val request = screenRequest
        AnimatedVisibility(
            visible = request != null,
            enter = fadeIn(tween(NulisMotion.normal)) + slideInVertically(tween(NulisMotion.normal)) { it / 8 },
            exit = fadeOut(tween(NulisMotion.quick)) + slideOutVertically(tween(NulisMotion.quick)) { it / 8 },
        ) {
            val shown = remember { mutableStateOf(request) }.also { if (request != null) it.value = request }
            shown.value?.let { r ->
                BlockRegistry.definition(r.blockType)?.Screen(request = r, context = blockContext, onClose = { screenRequest = null })
            }
        }

        if (!drawerIsPage) {
            drawerSlot(
                drawerOpen,
                drawer.settledShown,
                drawerSearch,
                { drawer.hide() },
                Modifier
                    .fillMaxSize()
                    .zIndex(if (warmingDrawer) -1f else 0f)
                    .graphicsLayer {
                        val p = drawer.progress
                        translationY = if (warmingDrawer) 0f else (1f - p) * size.height
                        // Skip drawing entirely while fully hidden.
                        alpha = if (warmingDrawer || p > 0f) 1f else 0f
                    }
                    .nestedScroll(drawer.nestedScrollConnection),
            )
        }

        pending?.let { waiting ->
            MindfulPauseScreen(
                pending = waiting,
                onOpen = {
                    pending = null
                    launch(waiting.app, null)
                },
                onNeverMind = { pending = null },
                modifier = Modifier.zIndex(5f),
            )
        }

        AnimatedVisibility(
            visible = backupOpen,
            enter = fadeIn(tween(NulisMotion.normal)) + slideInVertically(tween(NulisMotion.normal)) { it / 8 },
            exit = fadeOut(tween(NulisMotion.quick)) + slideOutVertically(tween(NulisMotion.quick)) { it / 8 },
            // On the wrapper, not on the screen inside it. A full-screen surface that
            // can be reached from the editor's header has to outrank the editor, and
            // only a direct child of this Box gets to say where it sits in the stack.
            modifier = Modifier.zIndex(FullScreenZ),
        ) {
            BackupScreen(
                status = backupStatus,
                actions = remember(viewModel) {
                    BackupActions(
                        onExport = viewModel::exportBackup,
                        onInspect = viewModel::inspectBackup,
                        onRestore = viewModel::restoreLoadedBackup,
                        onDiscard = viewModel::discardLoadedBackup,
                        onReset = viewModel::resetEverything,
                        suggestedName = viewModel::suggestedBackupName,
                    )
                },
                onClose = {
                    backupOpen = false
                    viewModel.discardLoadedBackup()
                },
            )
        }

        // The weekly summary, opened from settings or a gesture.
        AnimatedVisibility(
            visible = weekOpen,
            enter = fadeIn(tween(NulisMotion.normal)) + slideInVertically(tween(NulisMotion.normal)) { it / 8 },
            exit = fadeOut(tween(NulisMotion.quick)) + slideOutVertically(tween(NulisMotion.quick)) { it / 8 },
            // On the wrapper, not on the screen inside it. A full-screen surface that
            // can be reached from the editor's header has to outrank the editor, and
            // only a direct child of this Box gets to say where it sits in the stack.
            modifier = Modifier.zIndex(FullScreenZ),
        ) {
            LaunchedEffect(Unit) { viewModel.refreshWeek() }
            WeeklySummaryScreen(
                screenTime = weeklyScreenTime,
                steps = steps,
                focus = focus,
                onClose = { weekOpen = false },
            )
        }

        AnimatedVisibility(
            visible = wellbeingOpen,
            enter = fadeIn(tween(NulisMotion.normal)) + slideInVertically(tween(NulisMotion.normal)) { it / 8 },
            exit = fadeOut(tween(NulisMotion.quick)) + slideOutVertically(tween(NulisMotion.quick)) { it / 8 },
            // On the wrapper, not on the screen inside it. A full-screen surface that
            // can be reached from the editor's header has to outrank the editor, and
            // only a direct child of this Box gets to say where it sits in the stack.
            modifier = Modifier.zIndex(FullScreenZ),
        ) {
            WellbeingScreen(
                apps = apps,
                iconStyle = preferences.drawerIcons,
                wellbeing = wellbeing,
                minutesToday = screenTime.minutesByPackage,
                usageGranted = screenTime.granted,
                actions = remember(viewModel) {
                    WellbeingActions(
                        onPaused = viewModel::setPaused,
                        onPauseSeconds = viewModel::setPauseSeconds,
                        onLimit = viewModel::setLimit,
                        onFocusAllowed = viewModel::setFocusAllowed,
                        onFocusHides = viewModel::setFocusHides,
                        onFocusGuards = viewModel::setFocusGuards,
                        onWeeklySummary = { wellbeingOpen = false; weekOpen = true },
                    )
                },
                onClose = { wellbeingOpen = false },
            )
        }

        // First launch, or "run setup again": above everything, including the drawer.
        if (!preferences.onboarded) {
            val requestSteps = rememberActivityRecognitionRequest { viewModel.permissionChanged() }
            OnboardingScreen(
                layouts = LayoutPresets.all,
                appliedLayoutId = preferences.layoutId,
                lookId = preferences.lookId,
                colorTheme = preferences.colorTheme,
                apps = apps,
                favoriteIds = favoriteIds,
                maxFavorites = AppsBlockDefinition.MAX_FAVORITES,
                iconStyle = preferences.drawerIcons,
                context = blockContext,
                permissions = listOf(
                    OnboardingPermission(
                        title = "Screen time",
                        reason = "Today's minutes per app, for the screen time block",
                        granted = screenTime.granted,
                        request = { openUsageAccessSettings(appContext) },
                    ),
                    OnboardingPermission(
                        title = "Steps",
                        reason = "The phone's own step counter, for the steps block",
                        granted = steps.granted,
                        request = requestSteps,
                    ),
                    OnboardingPermission(
                        title = "Music",
                        reason = "What is playing right now, for the music block",
                        granted = music.granted,
                        request = { openNotificationAccessSettings(appContext) },
                    ),
                ),
                actions = remember(viewModel) {
                    OnboardingActions(
                        onPickLayout = viewModel::applyLayout,
                        onPickLook = { lookId, colors ->
                            viewModel.setLook(lookId)
                            viewModel.setColorTheme(colors)
                        },
                        onToggleApp = viewModel::toggleFavorite,
                        onFinish = { viewModel.setOnboarded(true) },
                        onRefreshPermissions = {
                            viewModel.refreshScreenTime()
                            viewModel.refreshMusicAccess()
                            viewModel.permissionChanged()
                        },
                    )
                },
                modifier = Modifier.zIndex(4f),
            )
        }

        // Block ids are unique across pages, so the options sheet finds its page by id.
        val optionsId = optionsBlockId
        val optionsPage = optionsId?.let { id -> layouts.entries.firstOrNull { (_, layout) -> layout?.blocks?.any { it.id == id } == true } }
        val optionsLayout = optionsPage?.value
        val optionsBlock = optionsLayout?.blocks?.firstOrNull { it.id == optionsId }
        val optionsDefinition = optionsBlock?.let { BlockRegistry.definition(it.type) }
        if (optionsPage != null && optionsLayout != null && optionsBlock != null && optionsDefinition != null) {
            BlockOptionsSheet(
                block = optionsBlock,
                definition = optionsDefinition,
                layout = optionsLayout,
                context = blockContext,
                actions = viewModel.actionsFor(optionsPage.key),
                onDismiss = { optionsBlockId = null },
            )
        }

        // Page layout: alignment, vertical anchor and spacing, each a live miniature of the page.
        val pageOptions = pageOptionsId?.let { id -> layouts[id]?.let { id to it } }
        if (pageOptions != null) {
            PageOptionsSheet(
                layout = pageOptions.second,
                title = pagesConfig.nameOf(pageOptions.first),
                context = blockContext,
                actions = viewModel.actionsFor(pageOptions.first),
                onDismiss = { pageOptionsId = null },
            )
        }
    }
    }
}



/**
 * Where each thing that can be on screen at once sits in the stack.
 *
 * The pager is the floor. The editor lifts off it, the block picker off the editor, and every
 * full-screen surface above both - because the editor's own header offers Settings and the page's
 * layout, and a Settings screen that opens *underneath* the editor is a button that does nothing.
 * That is exactly what the gear in the editor did: it set the flag, Settings composed, and the
 * editor drew over the top of it.
 *
 * These have to be on the wrapper of an AnimatedVisibility rather than on the screen inside it.
 * Only a direct child of the stacking Box gets a say, and the wrapper is the child.
 */
private const val FullScreenZ = 3f

/** Three dots above the navigation bar, shown only while paging and for a moment after. */
@Composable
private fun PageDots(
    pager: PagerState,
    homeIndex: Int,
    /** The drawer's page, or -1 when it does not have one. Drawn as a ring, not a dot. */
    drawerIndex: Int,
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = NulisTheme.colors
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(pager.isScrollInProgress) {
        if (pager.isScrollInProgress) {
            shown = true
        } else {
            delay(700)
            shown = false
        }
    }
    val alpha by animateFloatAsState(if (shown && visible) 1f else 0f, tween(NulisMotion.normal), label = "pageDots")
    Row(
        modifier = modifier
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
            .graphicsLayer { this.alpha = alpha },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(pager.pageCount) { index ->
            val current = index == pager.currentPage
            // Home is a mark rather than a position now, so the dots have to say which one it
            // is: a short bar instead of a dot, readable at a glance across five pages. The
            // drawer's page is a ring, because it is not one of your pages.
            val tint = if (current) colors.accent else colors.tertiary
            if (index == drawerIndex) {
                Box(
                    Modifier
                        .size(if (current) 7.dp else 6.dp)
                        .border(1.dp, tint, CircleShape),
                )
            } else {
                Box(
                    Modifier
                        .size(width = if (index == homeIndex) 12.dp else if (current) 6.dp else 5.dp, height = if (current) 6.dp else 5.dp)
                        .background(tint, CircleShape),
                )
            }
        }
    }
}

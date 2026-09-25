// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.nulis.launcher.apps.AppCustomization
import com.nulis.launcher.apps.AppCustomizationRepository
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.apps.AppRepository
import com.nulis.launcher.apps.AppUsage
import com.nulis.launcher.apps.AppUsageRepository
import com.nulis.launcher.apps.Categories
import com.nulis.launcher.apps.CategoryRepository
import com.nulis.launcher.drawer.DrawerPlacement
import com.nulis.launcher.drawer.CategoryDisplay
import com.nulis.launcher.drawer.PENDING_NEW_CATEGORY
import com.nulis.launcher.icons.IconLoader
import com.nulis.launcher.icons.IconPackInfo
import com.nulis.launcher.icons.IconStyle
import java.text.Collator
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockActions
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.GridRect
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PagesConfig
import com.nulis.launcher.blocks.roomFor
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import com.nulis.launcher.blocks.battery.BatteryRepository
import android.net.Uri
import com.nulis.launcher.backup.BackupResult
import com.nulis.launcher.backup.BackupRepository
import com.nulis.launcher.backup.BackupStatus
import com.nulis.launcher.backup.NulisBackup
import com.nulis.launcher.backup.message
import com.nulis.launcher.backup.preview
import com.nulis.launcher.blocks.battery.BatteryState
import com.nulis.launcher.blocks.glance.AlarmState
import com.nulis.launcher.blocks.glance.NextAlarmRepository
import com.nulis.launcher.blocks.calendar.CalendarActions
import com.nulis.launcher.blocks.calendar.CalendarRepository
import com.nulis.launcher.blocks.calendar.CalendarState
import com.nulis.launcher.blocks.focus.FocusActions
import com.nulis.launcher.blocks.focus.FocusRepository
import com.nulis.launcher.blocks.focus.FocusState
import com.nulis.launcher.blocks.focus.RunningFocus
import com.nulis.launcher.blocks.music.MusicActions
import com.nulis.launcher.blocks.music.MusicRepository
import com.nulis.launcher.blocks.music.MusicState
import com.nulis.launcher.blocks.screentime.ScreenTimeRepository
import com.nulis.launcher.blocks.screentime.ScreenTimeState
import com.nulis.launcher.blocks.screentime.WeeklyScreenTime
import com.nulis.launcher.wellbeing.Wellbeing
import com.nulis.launcher.wellbeing.WellbeingRepository
import com.nulis.launcher.blocks.steps.StepsActions
import com.nulis.launcher.blocks.steps.StepsRepository
import com.nulis.launcher.blocks.steps.StepsState
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import com.nulis.launcher.blocks.writing.JournalEntry
import com.nulis.launcher.blocks.writing.Note
import com.nulis.launcher.blocks.writing.Task
import com.nulis.launcher.blocks.writing.WritingActions
import com.nulis.launcher.blocks.writing.WritingRepository
import com.nulis.launcher.blocks.writing.WritingState
import com.nulis.launcher.gestures.GestureBinding
import com.nulis.launcher.gestures.GestureSettings
import com.nulis.launcher.gestures.GestureTrigger
import com.nulis.launcher.gestures.GesturesRepository
import com.nulis.launcher.layout.LayoutPreset
import com.nulis.launcher.layout.LayoutRepository
import com.nulis.launcher.layout.PageActions
import com.nulis.launcher.layout.extraPages
import com.nulis.launcher.layout.layoutPages
import com.nulis.launcher.setups.ApplyOptions
import com.nulis.launcher.setups.SetupRepository
import com.nulis.launcher.widgets.WidgetBlockDefinition
import com.nulis.launcher.widgets.WidgetHost
import com.nulis.launcher.setups.SavedSetup
import com.nulis.launcher.setups.captureSetup
import com.nulis.launcher.setups.setupPages
import com.nulis.launcher.settings.UiPreferences
import com.nulis.launcher.settings.UiPreferencesRepository
import androidx.compose.ui.graphics.toArgb
import com.nulis.launcher.ui.theme.ColorTheme
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Holds the installed-app list, every page layout and the appearance preferences. */
class LauncherViewModel(
    private val appRepository: AppRepository,
    private val appCustomizationRepository: AppCustomizationRepository,
    private val iconLoader: IconLoader,
    private val layoutRepository: LayoutRepository,
    private val uiPreferencesRepository: UiPreferencesRepository,
    initialPreferences: UiPreferences,
    batteryRepository: BatteryRepository,
    nextAlarmRepository: NextAlarmRepository,
    private val writingRepository: WritingRepository,
    private val screenTimeRepository: ScreenTimeRepository,
    private val stepsRepository: StepsRepository,
    private val musicRepository: MusicRepository,
    private val gesturesRepository: GesturesRepository,
    private val setupRepository: SetupRepository,
    private val calendarRepository: CalendarRepository,
    private val focusRepository: FocusRepository,
    private val categoryRepository: CategoryRepository,
    private val appUsageRepository: AppUsageRepository,
    private val wellbeingRepository: WellbeingRepository,
    private val backupRepository: BackupRepository,
) : ViewModel(), WritingActions, StepsActions, MusicActions, CalendarActions, FocusActions, PageActions {

    // ---------------------------------------------------------------- backup

    private val _backupStatus = MutableStateFlow<BackupStatus>(BackupStatus.Idle)
    val backupStatus: StateFlow<BackupStatus> = _backupStatus.asStateFlow()

    /** The file that was read and is waiting for a yes. */
    private var loadedBackup: NulisBackup? = null

    fun exportBackup(uri: Uri) {
        _backupStatus.value = BackupStatus.Working
        viewModelScope.launch {
            val backup = backupRepository.gather()
            val ok = backupRepository.write(uri, backup)
            _backupStatus.value = if (ok) {
                BackupStatus.Exported(uri.lastPathSegment?.substringAfterLast('/') ?: "the file you chose")
            } else {
                BackupStatus.Problem("That file could not be written.")
            }
        }
    }

    fun inspectBackup(uri: Uri) {
        _backupStatus.value = BackupStatus.Working
        viewModelScope.launch {
            when (val result = backupRepository.read(uri)) {
                is BackupResult.Ok -> {
                    loadedBackup = result.backup
                    _backupStatus.value = BackupStatus.Loaded(result.backup.preview())
                }
                is BackupResult.Failed -> {
                    loadedBackup = null
                    _backupStatus.value = BackupStatus.Problem(result.error.message())
                }
            }
        }
    }

    fun restoreLoadedBackup() {
        val backup = loadedBackup ?: return
        _backupStatus.value = BackupStatus.Working
        viewModelScope.launch {
            backupRepository.restore(backup)
            loadedBackup = null
            _backupStatus.value = BackupStatus.Restored(backup.preview())
        }
    }

    fun discardLoadedBackup() {
        loadedBackup = null
        _backupStatus.value = BackupStatus.Idle
    }

    fun resetEverything() {
        _backupStatus.value = BackupStatus.Working
        viewModelScope.launch {
            backupRepository.resetEverything()
            _backupStatus.value = BackupStatus.Idle
        }
    }

    fun suggestedBackupName(): String = backupRepository.suggestedFileName()

    // ---------------------------------------------------------------- wellbeing

    val wellbeing: StateFlow<Wellbeing> = wellbeingRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Wellbeing())

    private val weekRefresh = MutableStateFlow(0)

    /** Seven days of screen time. Only read when the weekly summary is on screen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val weeklyScreenTime: StateFlow<WeeklyScreenTime> = weekRefresh
        .flatMapLatest { flow { emit(screenTimeRepository.loadWeek()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeeklyScreenTime())

    fun refreshWeek() {
        weekRefresh.value++
    }

    fun setPaused(packageName: String, paused: Boolean) {
        viewModelScope.launch { wellbeingRepository.setPaused(packageName, paused) }
    }

    fun setPauseSeconds(seconds: Int) {
        viewModelScope.launch { wellbeingRepository.setPauseSeconds(seconds) }
    }

    fun setLimit(packageName: String, minutes: Int) {
        viewModelScope.launch { wellbeingRepository.setLimit(packageName, minutes) }
    }

    fun setFocusAllowed(packageName: String, allowed: Boolean) {
        viewModelScope.launch { wellbeingRepository.setFocusAllowed(packageName, allowed) }
    }

    fun setFocusHides(hides: Boolean) {
        viewModelScope.launch { wellbeingRepository.setFocusHides(hides) }
    }

    fun setFocusGuards(guards: Boolean) {
        viewModelScope.launch { wellbeingRepository.setFocusGuards(guards) }
    }

    // ---------------------------------------------------------------- drawer

    /** The user's own app groups. */
    val categories: StateFlow<Categories> = categoryRepository.categories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Categories())

    private val usageRefresh = MutableStateFlow(0)

    /** When each app was last opened. Only read when something actually wants it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val appUsage: StateFlow<AppUsage> = usageRefresh
        .flatMapLatest { flow { emit(appUsageRepository.load()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppUsage())

    fun createCategory(name: String) {
        viewModelScope.launch { pendingCategoryId = categoryRepository.create(name) }
    }

    /** The id of the group made by the last [createCategory], for the drawer's one-tap sheet. */
    private var pendingCategoryId: String? = null

    fun renameCategory(id: String, name: String) {
        viewModelScope.launch { categoryRepository.rename(id, name) }
    }

    fun deleteCategory(id: String) {
        viewModelScope.launch { categoryRepository.delete(id) }
    }

    /**
     * [PENDING_NEW_CATEGORY] means "the group that was just created"; only this class knows the
     * id the repository handed out, and the sheet that asked has already gone.
     */
    fun assignCategory(appId: String, categoryId: String?) {
        viewModelScope.launch {
            val resolved = if (categoryId == PENDING_NEW_CATEGORY) pendingCategoryId else categoryId
            categoryRepository.assign(appId, resolved)
        }
    }

    fun moveCategory(id: String, delta: Int) {
        viewModelScope.launch { categoryRepository.move(id, delta) }
    }

    fun setCategoryDisplay(display: CategoryDisplay) {
        viewModelScope.launch { uiPreferencesRepository.setCategoryDisplay(display.name) }
    }

    fun setAutoHideDays(days: Int) {
        viewModelScope.launch { uiPreferencesRepository.setAutoHideDays(days) }
    }

    fun setShowRecents(show: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setShowRecents(show) }
    }

    fun setSearchNotes(on: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setSearchNotes(on) }
    }

    fun setSearchSettings(on: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setSearchSettings(on) }
    }

    // ---------------------------------------------------------------- calendar

    private val calendarRefresh = MutableStateFlow(0)

    /** The diary, reloaded on every resume and whenever the permission may have changed. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val calendar: StateFlow<CalendarState> = calendarRefresh
        .flatMapLatest { flow { emit(calendarRepository.load()) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalendarState())

    fun refreshCalendar() {
        calendarRefresh.value++
    }

    override fun permissionChanged() {
        // Shared with steps: both are asked for from a block and both re-check on the way back.
        stepsListening.value = stepsListening.value.first to stepsListening.value.second + 1
        viewModelScope.launch { stepsRepository.setGoal(steps.value.goal) }
        refreshCalendar()
    }

    // ---------------------------------------------------------------- focus

    private val runningFocus = MutableStateFlow<RunningFocus?>(null)

    /** Finished minutes from the log, plus whatever session is running right now. */
    val focus: StateFlow<FocusState> = combine(focusRepository.state, runningFocus) { stored, running ->
        stored.copy(running = running)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FocusState())

    override fun start(isBreak: Boolean) {
        val minutes = if (isBreak) focus.value.breakMinutes else focus.value.focusMinutes
        runningFocus.value = RunningFocus(
            endsAtMillis = System.currentTimeMillis() + minutes * 60_000L,
            totalMinutes = minutes,
            isBreak = isBreak,
        )
    }

    /**
     * Giving up still counts what was actually done. Ten minutes of focus are ten minutes of
     * focus whether or not the timer agreed.
     */
    override fun stop() {
        val running = runningFocus.value ?: return
        runningFocus.value = null
        if (running.isBreak) return
        val elapsed = ((running.totalMinutes * 60_000L - (running.endsAtMillis - System.currentTimeMillis())) / 60_000L).toInt()
        if (elapsed >= 1) viewModelScope.launch { focusRepository.record(elapsed) }
    }

    override fun finish() {
        val running = runningFocus.value ?: return
        runningFocus.value = null
        if (!running.isBreak) viewModelScope.launch { focusRepository.record(running.totalMinutes) }
    }

    override fun setLengths(focusMinutes: Int, breakMinutes: Int) {
        viewModelScope.launch { focusRepository.setLengths(focusMinutes, breakMinutes) }
    }

    /** A session that ran out while Nulis was in the background is filed on the way back in. */
    private fun settleFocus() {
        val running = runningFocus.value ?: return
        if (System.currentTimeMillis() >= running.endsAtMillis) finish()
    }

    /** The setups the user saved. Nulis ships none: what it ships is layouts and looks. */
    val setups: StateFlow<List<SavedSetup>> = setupRepository.saved
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Swaps every page and the whole appearance in two writes. Nothing the user has written is
     * touched; [options] only decides which blocks survive.
     */
    fun applySetup(setup: SavedSetup, options: ApplyOptions) {
        val replaced = setupPages(setup, layouts.value, options)
        viewModelScope.launch {
            layoutRepository.replaceAll(replaced, order = setup.order, homeId = setup.config.homeId)
            uiPreferencesRepository.applySetupAppearance(
                lookId = setup.lookId,
                colorTheme = setup.colorTheme,
                customBackground = setup.customBackground,
                customInk = setup.customInk,
                displayFont = setup.displayFont,
                bodyFont = setup.bodyFont,
                textScale = setup.textScale,
                uppercaseLabels = setup.uppercaseLabels,
                setupId = setup.id,
            )
            setup.drawerIcons?.let { uiPreferencesRepository.setDrawerIcons(IconStyle.from(it)) }
        }
    }

    /**
     * Swaps every page for the layout's own - how many there are as well as what is on them -
     * and leaves the Look, the colours and the type exactly as they are, which is the whole
     * point of a layout being a smaller thing than an appearance.
     */
    /**
     * @param keepExtraPages true to hold on to the pages this layout says nothing about, which
     * is what the picker offers when the phone has more pages than the layout has. They keep
     * their blocks and follow the layout's own pages in swipe order.
     */
    fun applyLayout(preset: LayoutPreset, keepExtraPages: Boolean = false) {
        viewModelScope.launch {
            val order = pages.value.ids
            val extra = if (keepExtraPages) extraPages(preset, order) else emptyList()
            // A phone with no apps chosen yet gets its own six rather than a hint telling it to
            // pick some: a layout applied should look like a home screen straight away.
            val next = layoutPages(preset, layouts.value, layoutRepository.deviceDefaultFavorites(), extra)
            layoutRepository.replaceAll(
                next,
                // Kept pages stay where they were in the swipe order rather than being pushed to
                // the end: the point of keeping them is that nothing about them changed.
                order = if (extra.isEmpty()) {
                    preset.order
                } else {
                    order.filter { it in next } + preset.order.filterNot { it in order }
                },
                homeId = preset.config.homeId,
            )
            uiPreferencesRepository.setLayoutId(preset.id)
        }
    }

    fun saveCurrentSetup(name: String) {
        val prefs = uiPreferences.value
        val setup = captureSetup(
            id = SetupRepository.newId(),
            name = name,
            lookId = prefs.lookId,
            colorTheme = prefs.colorTheme,
            customBackground = prefs.customBackground,
            customInk = prefs.customInk,
            displayFont = prefs.displayFontId,
            bodyFont = prefs.bodyFontId,
            textScale = prefs.textScale,
            uppercaseLabels = prefs.uppercaseLabels,
            pages = layouts.value,
            order = pages.value.ids,
            homeId = pages.value.homeId,
        )
        viewModelScope.launch { setupRepository.save(setup) }
    }

    fun renameSetup(id: String, name: String) {
        viewModelScope.launch { setupRepository.rename(id, name) }
    }

    fun deleteSetup(id: String) {
        viewModelScope.launch { setupRepository.delete(id) }
    }

    /**
     * Gives the system back every widget id nothing refers to any more.
     *
     * Deleting a widget block does not release its id where it happens, because Undo can put the
     * block back and a released id comes back empty. What releases them is this: run once the
     * editor has left the screen and once when the launcher starts, never while the editor is
     * open, because the undo stack is the one thing that can still name a deleted block and it
     * is not written down anywhere - it dies with the editor, and only then is an id that no
     * page mentions really unreachable.
     *
     * Saved setups count too: applying one puts its widget blocks back, and an id released out
     * from under a setup would restore as an empty rectangle.
     *
     * If any part of that count cannot be read, nothing is released at all. Leaking an id costs
     * a row in a system table; releasing a live one costs the user the widget they were looking
     * at, and there is no way to bind it back without the picker.
     */
    fun releaseUnusedWidgetIds(host: WidgetHost?) {
        if (host == null) return
        viewModelScope.launch {
            val live = runCatching {
                WidgetBlockDefinition.idsIn(layoutRepository.allSavedLayouts()) +
                    WidgetBlockDefinition.idsIn(setupRepository.saved.first().flatMap { it.pages.values })
            }.getOrNull() ?: return@launch
            runCatching { host.releaseUnreferenced(live) }
        }
    }

    fun setDisplayFont(id: String?) {
        viewModelScope.launch { uiPreferencesRepository.setDisplayFont(id) }
    }

    fun setBodyFont(id: String?) {
        viewModelScope.launch { uiPreferencesRepository.setBodyFont(id) }
    }

    fun setTextScale(scale: Float) {
        viewModelScope.launch { uiPreferencesRepository.setTextScale(scale) }
    }

    fun setUppercaseLabels(uppercase: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setUppercaseLabels(uppercase) }
    }

    /** Ends (or restarts) the one-time setup flow. */
    fun setOnboarded(done: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setOnboarded(done) }
    }

    fun setEditCoachSeen() {
        viewModelScope.launch { uiPreferencesRepository.setEditCoachSeen(true) }
    }

    /** Somebody just did what [hint] describes; it is never shown again. */
    fun learnHint(hint: com.nulis.launcher.home.Hint) {
        if (hint.key in uiPreferences.value.learnedHints) return
        viewModelScope.launch { uiPreferencesRepository.learnHint(hint.key) }
    }

    /**
     * Applies a whole setup during onboarding: the appearance and a finished set of pages, with
     * whatever apps have already been picked carried over.
     */
    fun previewSetup(setup: SavedSetup) = applySetup(setup, ApplyOptions(keepApps = true, keepWritingBlocks = false))

    fun setSound(on: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setSound(on) }
    }

    fun setSoundVolume(volume: Float) {
        viewModelScope.launch { uiPreferencesRepository.setSoundVolume(volume) }
    }

    fun setReducedMotion(on: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setReducedMotion(on) }
    }

    fun setHighContrast(on: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setHighContrast(on) }
    }

    fun setCustomAccent(argb: Int?) {
        viewModelScope.launch { uiPreferencesRepository.setCustomAccent(argb) }
    }

    fun applyPalette(palette: com.nulis.launcher.ui.theme.Palette) {
        viewModelScope.launch {
            uiPreferencesRepository.applyPalette(
                background = palette.background.toArgb(),
                ink = palette.ink.toArgb(),
                accent = palette.accent.toArgb(),
            )
        }
    }

    fun setLeftPageGear(on: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setLeftPageGear(on) }
    }

    fun setWallpaper(show: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setWallpaper(show) }
    }

    fun setWallpaperDim(dim: Float) {
        viewModelScope.launch { uiPreferencesRepository.setWallpaperDim(dim) }
    }

    fun setCustomInk(argb: Int?) {
        viewModelScope.launch { uiPreferencesRepository.setCustomInk(argb) }
    }

    /** Per-app names, icons and hidden state. */
    val customization: StateFlow<AppCustomization> = appCustomizationRepository.state
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppCustomization())

    private val _iconPacks = MutableStateFlow<List<IconPackInfo>>(emptyList())

    /** Icon packs installed on the phone; loaded once the settings screen asks for them. */
    val iconPacks: StateFlow<List<IconPackInfo>> = _iconPacks.asStateFlow()

    init {
        // The loader needs the chosen pack and the per-app icon overrides to build its cache keys.
        combine(uiPreferencesRepository.preferences, customization) { prefs, custom -> prefs.iconPack to custom.icons }
            .onEach { (pack, icons) -> iconLoader.configure(pack, icons, viewModelScope) }
            .launchIn(viewModelScope)
    }

    fun refreshIconPacks() {
        viewModelScope.launch { _iconPacks.value = iconLoader.installedPacks() }
    }

    fun setDrawerIcons(style: IconStyle) {
        viewModelScope.launch { uiPreferencesRepository.setDrawerIcons(style) }
    }

    /** Switching pack throws away per-app icon choices: a drawable name only means something in its own pack. */
    fun setIconPack(packageName: String?) {
        viewModelScope.launch {
            appCustomizationRepository.clearIcons()
            uiPreferencesRepository.setIconPack(packageName)
        }
    }

    fun setSearchHidden(search: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setSearchHidden(search) }
    }

    fun setDrawerPlacement(placement: DrawerPlacement) {
        viewModelScope.launch { uiPreferencesRepository.setDrawerPlacement(placement) }
    }

    fun setNotificationDots(on: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setNotificationDots(on) }
    }

    fun renameApp(app: AppInfo, name: String?) {
        viewModelScope.launch { appCustomizationRepository.setName(app.id, name, appRepository.originalLabel(app)) }
    }

    fun setAppIcon(app: AppInfo, drawable: String?) {
        viewModelScope.launch { appCustomizationRepository.setIcon(app.id, drawable) }
    }

    /** The launcher's own settings row can never be hidden; it is the way back into settings. */
    fun setAppHidden(app: AppInfo, hidden: Boolean) {
        if (app.isNulisSettings) return
        viewModelScope.launch { appCustomizationRepository.setHidden(app.id, hidden) }
    }

    /** Trigger -> action map; triggers the user never touched read their defaults. */
    val gestures: StateFlow<GestureSettings> = gesturesRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, GestureSettings())

    fun setGesture(trigger: GestureTrigger, binding: GestureBinding) {
        viewModelScope.launch { gesturesRepository.set(trigger, binding) }
    }

    fun resetGestures() {
        viewModelScope.launch { gesturesRepository.reset() }
    }

    /** Bumped when notification access may have changed, so the media session flow restarts. */
    private val musicAccess = MutableStateFlow(0)

    /** What is playing now. Collected only while a music block is on screen. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val music: StateFlow<MusicState> = musicAccess
        .flatMapLatest { musicRepository.state }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicState(granted = musicRepository.hasNotificationAccess()))

    /** Called on resume: the user may have just granted or revoked notification access. */
    fun refreshMusicAccess() {
        musicAccess.value++
    }

    override fun playPause() = musicRepository.playPause()

    override fun skipPrevious() = musicRepository.skipPrevious()

    override fun skipNext() = musicRepository.skipNext()

    override fun openPlayer() = musicRepository.openPlayer()

    val steps: StateFlow<StepsState> = stepsRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StepsState(granted = stepsRepository.hasPermission()))

    /** (visible, permission generation): the step counter is listened to only while both allow it. */
    private val stepsListening = MutableStateFlow(false to 0)

    init {
        @OptIn(ExperimentalCoroutinesApi::class)
        stepsListening
            .flatMapLatest { (visible, _) -> if (visible && stepsRepository.hasPermission()) stepsRepository.counterReadings() else emptyFlow() }
            .onEach { stepsRepository.onCounter(it) }
            .launchIn(viewModelScope)
    }

    /** Called from the route on resume/pause: count steps only while the launcher is on screen. */
    fun setVisible(visible: Boolean) {
        if (visible) {
            settleFocus()
            refreshCalendar()
            usageRefresh.value++
        }
        stepsListening.value = visible to stepsListening.value.second
        // Alarms are lost on reboot, so re-book the nightly read every time we come to the front.
        if (visible) stepsRepository.scheduleDailyRead()
    }

    override fun setGoal(goal: Int) {
        viewModelScope.launch { stepsRepository.setGoal(goal) }
    }

    private val screenTimeRefresh = MutableStateFlow(0)

    /** Today's usage, reloaded every minute while something shows it and on every [refreshScreenTime]. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val screenTime: StateFlow<ScreenTimeState> = screenTimeRefresh
        .flatMapLatest {
            flow {
                while (true) {
                    emit(screenTimeRepository.load())
                    delay(60_000L)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ScreenTimeState(granted = screenTimeRepository.hasUsageAccess()))

    fun refreshScreenTime() {
        screenTimeRefresh.value++
    }

    fun hasUsageAccess(): Boolean = screenTimeRepository.hasUsageAccess()

    val writing: StateFlow<WritingState> = writingRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WritingState())

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())

    /**
     * Every launchable app, alphabetical, under whatever name the user gave it. Empty until the
     * first [refreshApps]. Hidden apps are still in here; the drawer decides when to show them.
     */
    val allApps: StateFlow<List<AppInfo>> = combine(_installedApps, customization) { apps, custom ->
        if (custom.names.isEmpty()) {
            apps
        } else {
            val collator = Collator.getInstance()
            apps.map { app -> custom.names[app.id]?.let { app.copy(label = it) } ?: app }
                .sortedWith { a, b -> collator.compare(a.label, b.label) }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Which pages there are, in swipe order, and which one is home. */
    val pages: StateFlow<PagesConfig> = layoutRepository.pages
        .stateIn(viewModelScope, SharingStarted.Eagerly, PagesConfig())

    /** Layout per page id, null until that page has been loaded (and migrated on first run). */
    val layouts: StateFlow<Map<String, PageLayout?>> = pages
        .flatMapLatest { config ->
            combine(config.ids.map { layoutRepository.layout(it) }) { loaded ->
                config.ids.zip(loaded.toList()).toMap()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PageIds.all.associateWith { null })

    /** A page added, so the pager can swipe to it the moment it exists. */
    private val _newPage = MutableStateFlow<String?>(null)
    val newPage: StateFlow<String?> = _newPage.asStateFlow()

    override fun addPage(after: String?) {
        viewModelScope.launch { _newPage.value = layoutRepository.addPage(after) }
    }

    /**
     * Puts a block on a page of its own, which is the way out offered when the page it was meant
     * for is full. The new page lands next to the one it came from, and the pager swipes to it.
     */
    fun addBlockOnNewPage(type: String, after: String?) {
        viewModelScope.launch {
            val id = layoutRepository.addPage(after) ?: return@launch
            layoutRepository.update(id) { page ->
                val spot = page.roomFor(type) ?: return@update page
                page.copy(blocks = page.blocks + BlockRegistry.newBlock(type).copy(rect = spot))
            }
            _newPage.value = id
        }
    }

    fun newPageShown() {
        _newPage.value = null
    }

    override fun removePage(pageId: String) {
        viewModelScope.launch { layoutRepository.removePage(pageId) }
    }

    override fun movePage(pageId: String, delta: Int) {
        viewModelScope.launch { layoutRepository.movePage(pageId, delta) }
    }

    override fun setHomePage(pageId: String) {
        viewModelScope.launch { layoutRepository.setHomePage(pageId) }
    }

    /** The first Apps block on any page (home first); the drawer's add/remove menu targets it. */
    private val appsBlock: StateFlow<Pair<String, Block>?> = layouts
        .map { pages -> findAppsBlock(pages) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** The home page's apps in the order they sit there, for previews that should look like this phone. */
    val homeFavorites: StateFlow<List<String>> = appsBlock
        .map { it?.second?.let { block -> AppsBlockDefinition.favoriteIds(block) } ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val favoriteIds: StateFlow<Set<String>> = appsBlock
        .map { it?.second?.let { block -> AppsBlockDefinition.favoriteIds(block).toSet() } ?: emptySet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val hasAppsBlock: StateFlow<Boolean> = appsBlock
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val uiPreferences: StateFlow<UiPreferences> = uiPreferencesRepository.preferences
        .stateIn(viewModelScope, SharingStarted.Eagerly, initialPreferences)

    val battery: StateFlow<BatteryState> = batteryRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BatteryState())

    val nextAlarm: StateFlow<AlarmState> = nextAlarmRepository.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlarmState())

    private val _homeRequests = MutableStateFlow(0)

    /** Increments every time the system asks the launcher to show home (Home key while already in front). */
    val homeRequests: StateFlow<Int> = _homeRequests.asStateFlow()

    private val pageActions = HashMap<String, BlockActions>()

    init {
        viewModelScope.launch {
            uiPreferencesRepository.ensureOnboardingFlag()
            layoutRepository.pages.first().ids.forEach { layoutRepository.ensureInitialized(it) }
        }
    }

    fun requestHome() {
        _homeRequests.value++
    }

    fun refreshApps() {
        viewModelScope.launch {
            val apps = appRepository.loadApps()
            // Same list as before (the common case on resume): keep the old instance so nothing recomposes.
            if (apps != _installedApps.value) _installedApps.value = apps
        }
    }

    fun setLook(lookId: String) {
        viewModelScope.launch { uiPreferencesRepository.setLook(lookId) }
    }

    fun setColorTheme(theme: ColorTheme) {
        viewModelScope.launch { uiPreferencesRepository.setColorTheme(theme) }
    }

    fun setCustomBackground(argb: Int) {
        viewModelScope.launch { uiPreferencesRepository.setCustomBackground(argb) }
    }

    fun setHideStatusBar(hide: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setHideStatusBar(hide) }
    }

    fun setShowAppUsage(show: Boolean) {
        viewModelScope.launch { uiPreferencesRepository.setShowAppUsage(show) }
    }

    /** Adds or removes [app] in the first Apps block. Does nothing if there is none. */
    fun toggleFavorite(app: AppInfo) {
        val (pageId, block) = appsBlock.value ?: return
        edit(pageId) { it.replace(AppsBlockDefinition.toggleFavorite(block, app.id)) }
    }

    /** Block actions scoped to one page. One instance per page, so composables can skip on it. */
    fun actionsFor(pageId: String): BlockActions = pageActions.getOrPut(pageId) { PageBlockActions(pageId) }

    /**
     * Which page a block sits on, for the few things that know a block and not its page - a block
     * changing its own settings from the page it is drawn on. Ids are unique across pages.
     */
    fun pageIdOf(blockId: String): String? =
        layouts.value.entries.firstOrNull { (_, layout) -> layout?.blocks?.any { it.id == blockId } == true }?.key

    private inner class PageBlockActions(private val pageId: String) : BlockActions {
        override fun update(block: Block) = edit(pageId) { it.replace(block) }

        override fun updatePage(transform: (PageLayout) -> PageLayout) = edit(pageId, transform)

        override fun remove(blockId: String) = edit(pageId) { layout ->
            layout.copy(blocks = layout.blocks.filterNot { it.id == blockId })
        }

        override fun add(type: String): GridRect? {
            val layout = layouts.value[pageId] ?: return null
            val spot = layout.roomFor(type) ?: return null
            edit(pageId) { current ->
                // Asked again against whatever is saved right now, in case the page changed
                // between the picker opening and the tap landing.
                val place = current.roomFor(type) ?: return@edit current
                current.copy(blocks = current.blocks + BlockRegistry.newBlock(type).copy(rect = place))
            }
            return spot
        }
    }

    // Writing actions. Ids are handed out synchronously so an editor can open before the save lands.

    override fun addNote(text: String): String {
        val id = writingRepository.newId()
        val now = System.currentTimeMillis()
        viewModelScope.launch { writingRepository.editNotes { it + Note(id = id, text = text, updatedAt = now) } }
        return id
    }

    override fun updateNote(id: String, text: String) {
        viewModelScope.launch {
            writingRepository.editNotes { notes ->
                val existing = notes.firstOrNull { it.id == id }
                when {
                    existing == null -> notes + Note(id = id, text = text, updatedAt = System.currentTimeMillis())
                    existing.text == text -> notes
                    else -> notes.map { if (it.id == id) it.copy(text = text, updatedAt = System.currentTimeMillis()) else it }
                }
            }
        }
    }

    override fun togglePin(id: String) {
        viewModelScope.launch { writingRepository.editNotes { notes -> notes.map { if (it.id == id) it.copy(pinned = !it.pinned) else it } } }
    }

    override fun deleteNote(id: String) {
        viewModelScope.launch { writingRepository.editNotes { notes -> notes.filterNot { it.id == id } } }
    }

    override fun addJournalEntry(text: String) {
        viewModelScope.launch { writingRepository.editJournal { it + JournalEntry(writingRepository.newId(), text, System.currentTimeMillis()) } }
    }

    override fun deleteJournalEntry(id: String) {
        viewModelScope.launch { writingRepository.editJournal { entries -> entries.filterNot { it.id == id } } }
    }

    override fun addTask(text: String) {
        viewModelScope.launch { writingRepository.editTasks { it + Task(writingRepository.newId(), text, createdAt = System.currentTimeMillis()) } }
    }

    override fun toggleTask(id: String) {
        viewModelScope.launch { writingRepository.editTasks { tasks -> tasks.map { if (it.id == id) it.copy(done = !it.done) else it } } }
    }

    override fun deleteTask(id: String) {
        viewModelScope.launch { writingRepository.editTasks { tasks -> tasks.filterNot { it.id == id } } }
    }

    override fun clearDoneTasks() {
        viewModelScope.launch { writingRepository.editTasks { tasks -> tasks.filterNot { it.done } } }
    }

    private fun edit(pageId: String, transform: (PageLayout) -> PageLayout) {
        viewModelScope.launch {
            layoutRepository.update(pageId, transform)
            // The page is the user's work now, not a saved setup's; the list stops claiming it.
            if (uiPreferences.value.setupId != null) uiPreferencesRepository.clearSetupId()
        }
    }

    private fun findAppsBlock(loaded: Map<String, PageLayout?>): Pair<String, Block>? {
        // Home first, then the rest in swipe order: the drawer's add-to-home menu should mean
        // the page marked home, whichever one that is.
        val config = pages.value
        for (pageId in listOf(config.homeId) + config.ids) {
            val block = loaded[pageId]?.blocks?.firstOrNull { it.type == AppsBlockDefinition.type } ?: continue
            return pageId to block
        }
        return null
    }

    private fun PageLayout.replace(block: Block): PageLayout =
        copy(blocks = blocks.map { if (it.id == block.id) block else it })

    companion object {
        fun factory(
            appRepository: AppRepository,
            appCustomizationRepository: AppCustomizationRepository,
            iconLoader: IconLoader,
            layoutRepository: LayoutRepository,
            uiPreferencesRepository: UiPreferencesRepository,
            initialPreferences: UiPreferences,
            batteryRepository: BatteryRepository,
            nextAlarmRepository: NextAlarmRepository,
            writingRepository: WritingRepository,
            screenTimeRepository: ScreenTimeRepository,
            stepsRepository: StepsRepository,
            musicRepository: MusicRepository,
            gesturesRepository: GesturesRepository,
            setupRepository: SetupRepository,
            calendarRepository: CalendarRepository,
            focusRepository: FocusRepository,
            categoryRepository: CategoryRepository,
            appUsageRepository: AppUsageRepository,
            wellbeingRepository: WellbeingRepository,
            backupRepository: BackupRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                LauncherViewModel(
                    appRepository, appCustomizationRepository, iconLoader, layoutRepository,
                    uiPreferencesRepository, initialPreferences, batteryRepository, nextAlarmRepository, writingRepository,
                    screenTimeRepository, stepsRepository, musicRepository, gesturesRepository,
                    setupRepository, calendarRepository, focusRepository,
                    categoryRepository, appUsageRepository, wellbeingRepository, backupRepository,
                )
            }
        }
    }
}

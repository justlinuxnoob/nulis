// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import androidx.core.graphics.drawable.toDrawable
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import android.content.Intent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalHapticFeedback
import com.nulis.launcher.ui.sound.HapticsWithSound
import com.nulis.launcher.ui.sound.LocalSoundPlayer
import com.nulis.launcher.ui.sound.SoundPlayer
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nulis.launcher.apps.AppCustomizationRepository
import com.nulis.launcher.apps.AppRepository
import com.nulis.launcher.apps.defaultFavoriteIds
import com.nulis.launcher.apps.AppUsageRepository
import com.nulis.launcher.apps.CategoryRepository
import com.nulis.launcher.apps.launchApp
import com.nulis.launcher.apps.openAppInfo
import com.nulis.launcher.apps.requestUninstall
import com.nulis.launcher.blocks.battery.BatteryRepository
import com.nulis.launcher.blocks.glance.NextAlarmRepository
import com.nulis.launcher.blocks.calendar.CalendarRepository
import com.nulis.launcher.blocks.focus.FocusRepository
import com.nulis.launcher.blocks.music.MusicRepository
import com.nulis.launcher.blocks.writing.WritingRepository
import com.nulis.launcher.blocks.screentime.ScreenTimeRepository
import com.nulis.launcher.blocks.steps.StepsRepository
import com.nulis.launcher.gestures.GesturesRepository
import com.nulis.launcher.icons.IconLoader
import com.nulis.launcher.icons.LocalIconLoader
import com.nulis.launcher.layout.LayoutRepository
import com.nulis.launcher.layout.LegacyFavoritesStore
import com.nulis.launcher.setups.SetupRepository
import com.nulis.launcher.backup.BackupRepository
import com.nulis.launcher.wellbeing.WellbeingRepository
import com.nulis.launcher.settings.UiPreferences
import com.nulis.launcher.settings.UiPreferencesRepository
import com.nulis.launcher.ui.theme.Looks
import com.nulis.launcher.ui.theme.LocalReducedMotion
import com.nulis.launcher.widgets.LocalWidgetHost
import com.nulis.launcher.widgets.WidgetHost
import com.nulis.launcher.ui.theme.NulisTheme

class MainActivity : ComponentActivity() {

    private val uiPreferencesRepository by lazy { UiPreferencesRepository(applicationContext) }

    // Read once, synchronously, so the first frame and the window behind it already match.
    private val initialPreferences by lazy { uiPreferencesRepository.readNow() }

    /** Lives as long as the activity: it owns the generated audio buffers. */
    private val soundPlayer = SoundPlayer()

    /** Lives as long as the process: it owns the icon memory cache. */
    private val iconLoader by lazy { IconLoader(applicationContext) }

    private val appRepository by lazy {
        AppRepository(applicationContext, packageManager, packageName, getString(R.string.nulis_settings))
    }

    private val layoutRepository by lazy {
        LayoutRepository(
            applicationContext,
            LegacyFavoritesStore(applicationContext),
            defaultFavorites = { defaultFavoriteIds(applicationContext, appRepository.loadApps()) },
        )
    }
    private val setupRepository by lazy { SetupRepository(applicationContext) }
    private val gesturesRepository by lazy { GesturesRepository(applicationContext) }
    private val appCustomizationRepository by lazy { AppCustomizationRepository(applicationContext) }
    private val writingRepository by lazy { WritingRepository(applicationContext) }
    private val categoryRepository by lazy { CategoryRepository(applicationContext) }
    private val wellbeingRepository by lazy { WellbeingRepository(applicationContext) }
    private val focusRepository by lazy { FocusRepository(applicationContext) }

    private val viewModel: LauncherViewModel by viewModels {
        LauncherViewModel.factory(
            appRepository = appRepository,
            setupRepository = setupRepository,
            calendarRepository = CalendarRepository(applicationContext),
            categoryRepository = categoryRepository,
            appUsageRepository = AppUsageRepository(applicationContext),
            backupRepository = BackupRepository(
                context = applicationContext,
                layoutRepository = layoutRepository,
                setupRepository = setupRepository,
                uiPreferencesRepository = uiPreferencesRepository,
                gesturesRepository = gesturesRepository,
                appCustomizationRepository = appCustomizationRepository,
                writingRepository = writingRepository,
                categoryRepository = categoryRepository,
                wellbeingRepository = wellbeingRepository,
                focusRepository = focusRepository,
                appVersion = BuildConfig.VERSION_NAME,
            ),
            wellbeingRepository = wellbeingRepository,
            focusRepository = focusRepository,
            appCustomizationRepository = appCustomizationRepository,
            iconLoader = iconLoader,
            layoutRepository = layoutRepository,
            uiPreferencesRepository = uiPreferencesRepository,
            initialPreferences = initialPreferences,
            batteryRepository = BatteryRepository(applicationContext),
            nextAlarmRepository = NextAlarmRepository(applicationContext),
            writingRepository = writingRepository,
            screenTimeRepository = ScreenTimeRepository(applicationContext),
            stepsRepository = StepsRepository(applicationContext),
            musicRepository = MusicRepository(applicationContext),
            gesturesRepository = gesturesRepository,
        )
    }

    /**
     * The window settings last asked for, so they can be asked for again.
     *
     * On Android 8 and 9, hiding the status bar is a `systemUiVisibility` flag rather than a
     * request the window remembers, and anything that takes focus away - the drawer's keyboard,
     * a permission dialog, a picker, another app and back - hands it straight back. The status
     * bar came up on those versions and stayed up, on a setting that is on by default.
     */
    private var windowPreferences: UiPreferences? = null

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) windowPreferences?.let(::applyWindow)
    }

    /**
     * The widget host, listening only while the launcher is on screen.
     *
     * Created here rather than in the view model because [android.appwidget.AppWidgetHost] is
     * tied to a window: its views are inflated into this activity and its configure screens are
     * started from it.
     */
    private val widgetHost by lazy { WidgetHost(this) }

    override fun onStart() {
        super.onStart()
        widgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        widgetHost.stopListening()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyWindow(initialPreferences)
        preferHighestRefreshRate()

        setContent {
            val preferences by viewModel.uiPreferences.collectAsStateWithLifecycle()
            LaunchedEffect(preferences.colors.background, preferences.hideStatusBar, preferences.wallpaper) { applyWindow(preferences) }
            LaunchedEffect(preferences.sound, preferences.soundVolume) {
                soundPlayer.configure(preferences.sound, preferences.soundVolume)
            }
            NulisTheme(
                look = Looks.byId(preferences.lookId),
                colors = preferences.colors,
                displayFont = preferences.displayFont,
                bodyFont = preferences.bodyFont,
                textScale = preferences.textScale,
                uppercaseLabels = preferences.uppercaseLabels,
            ) {
                // Sound follows the haptic vocabulary exactly, so no call site has to know it
                // exists; with sound off this is the platform's own implementation, untouched.
                val haptics = LocalHapticFeedback.current
                val withSound = remember(haptics, preferences.sound) {
                    if (preferences.sound) HapticsWithSound(haptics, soundPlayer) else haptics
                }
                CompositionLocalProvider(
                    LocalReducedMotion provides preferences.reducedMotion,
                    LocalIconLoader provides iconLoader,
                    LocalHapticFeedback provides withSound,
                    LocalSoundPlayer provides soundPlayer,
                    LocalWidgetHost provides widgetHost,
                ) {
                    LauncherRoute(
                        viewModel = viewModel,
                        onLaunch = { app, origin -> launchApp(this, app, origin) },
                        onAppInfo = { app -> openAppInfo(this, app) },
                        onUninstall = { app -> requestUninstall(this, app) },
                    )
                }
            }
        }
    }

    override fun onDestroy() {
        soundPlayer.release()
        super.onDestroy()
    }

    /** The Home key while Nulis is already in front: go back to the home page. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.requestHome()
    }

    /** Asks for the fastest display mode at the current resolution so gestures track at 90/120 Hz. */
    private fun preferHighestRefreshRate() {
        // A context with no display of its own throws rather than returning null; a refresh rate
        // is a nicety, never a reason not to start.
        @Suppress("DEPRECATION")
        val display: Display = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else windowManager.defaultDisplay
        }.getOrNull() ?: return
        val current = display.mode
        val fastest = display.supportedModes
            .filter { it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight }
            .maxByOrNull { it.refreshRate } ?: return
        if (fastest.modeId != current.modeId) {
            window.attributes = window.attributes.apply { preferredDisplayModeId = fastest.modeId }
        }
    }

    /**
     * Window background, system bar icon contrast and status bar visibility. The status bar is
     * hidden only by this window, so it is back the moment another app is in front.
     */
    private fun applyWindow(preferences: UiPreferences) {
        windowPreferences = preferences
        val colors = preferences.colors
        // With the wallpaper on, the system draws it behind this window and Nulis lays its own
        // background over it at whatever dim the user chose; with it off the window is opaque,
        // which is what keeps a cold start from flashing.
        if (preferences.wallpaper) {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            // The surface needs an alpha channel of its own, or the launcher is simply drawn
            // opaque over the wallpaper and the flag does nothing visible.
            window.setFormat(PixelFormat.TRANSLUCENT)
            window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            window.setFormat(PixelFormat.OPAQUE)
            window.setBackgroundDrawable(colors.background.toArgb().toDrawable())
        }
        val style = if (colors.isDark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (preferences.hideStatusBar) controller.hide(WindowInsetsCompat.Type.statusBars()) else controller.show(WindowInsetsCompat.Type.statusBars())
    }
}

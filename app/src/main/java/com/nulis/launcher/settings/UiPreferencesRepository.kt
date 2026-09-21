// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.settings

import com.nulis.launcher.data.replaceCorrupted
import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nulis.launcher.drawer.DrawerPlacement
import com.nulis.launcher.icons.IconStyle
import com.nulis.launcher.ui.theme.ColorTheme
import com.nulis.launcher.ui.theme.DefaultCustomBackground
import com.nulis.launcher.ui.theme.Fonts
import com.nulis.launcher.ui.theme.Looks
import com.nulis.launcher.ui.theme.NulisColors
import com.nulis.launcher.ui.theme.NulisFont
import com.nulis.launcher.ui.theme.colorsFor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.IOException

private val Context.uiPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "ui_preferences",
    corruptionHandler = replaceCorrupted("ui_preferences"),
)

data class UiPreferences(
    val lookId: String = Looks.Dot.id,
    val colorTheme: ColorTheme = ColorTheme.BLACK,
    /** ARGB background used when [colorTheme] is CUSTOM. */
    val customBackground: Int = DefaultCustomBackground.value.let { (it shr 32).toInt() },
    /** ARGB text colour used when [colorTheme] is CUSTOM, or null to let contrast decide. */
    val customInk: Int? = null,
    /** ARGB accent chosen by the user, or null for the one the background implies. */
    val customAccent: Int? = null,
    /** Hide the status bar while Nulis is on screen. */
    val hideStatusBar: Boolean = true,
    /** Show today's minutes next to app names in the drawer and Apps blocks. */
    val showAppUsage: Boolean = false,
    /** How apps are drawn in the drawer and every other app list outside a block. */
    val drawerIcons: IconStyle = IconStyle(),
    /** Package name of the chosen icon pack, or null for the apps' own icons. */
    val iconPack: String? = null,
    /** Whether apps hidden from the drawer still turn up when searching. */
    val searchHidden: Boolean = true,
    /** Where the app drawer lives: a swipe up, a page of its own, or nowhere. */
    val drawerPlacement: DrawerPlacement = DrawerPlacement.SWIPE_UP,
    /** A dot on apps with something waiting. Off until somebody asks for it. */
    val notificationDots: Boolean = false,
    /** Font id for display text, or null for the Look's own face. */
    val displayFontId: String? = null,
    /** Font id for body text, or null for Geist. */
    val bodyFontId: String? = null,
    /** Multiplies every text size in the launcher, on top of the system font scale. */
    val textScale: Float = 1f,
    /** Whether mono labels and captions are shouted in capitals. */
    val uppercaseLabels: Boolean = true,
    /** Show the phone's wallpaper behind the pages. */
    val wallpaper: Boolean = false,
    /** How far the background colour is laid back over the wallpaper, 0 clear to 1 opaque. */
    val wallpaperDim: Float = 0.55f,
    /** Id of the theme last applied, so the gallery can mark it. */
    val setupId: String? = null,
    /** Id of the layout last applied, so the picker can mark it. */
    val layoutId: String? = null,
    /** A small settings gear in the bottom corner of the left page. */
    val leftPageGear: Boolean = true,
    /** True once onboarding has run, so it never takes over a phone twice. */
    val onboarded: Boolean = false,
    /** True once the editor has said what a long press gives you. Said once, then never. */
    val editCoachSeen: Boolean = false,
    /** How the user's app groups are laid out in the drawer. */
    val categoryDisplay: String = "SECTIONS",
    /** Hide apps not opened for this many days; 0 is off. They stay searchable either way. */
    val autoHideDays: Int = 0,
    /** A row of the apps you opened most recently, at the top of the drawer. */
    val showRecents: Boolean = false,
    /** Whether the drawer's search also looks through notes and tasks. */
    val searchNotes: Boolean = true,
    /** Whether the drawer's search also finds settings. */
    val searchSettings: Boolean = true,
    /** Synthesized interface sounds. Off unless asked for. */
    val sound: Boolean = false,
    /** How loud those are, 0 to 1. */
    val soundVolume: Float = 0.4f,
    /** Calms springs and stills the animated blocks. */
    val reducedMotion: Boolean = false,
) {
    val colors: NulisColors get() = colorsFor(colorTheme, Color(customBackground), customInk?.let { Color(it) }, customAccent?.let { Color(it) })
    val displayFont: NulisFont? get() = Fonts.byId(displayFontId)
    val bodyFont: NulisFont? get() = Fonts.byId(bodyFontId)
}

/** Look, colors and display preferences, stored on-device. */
class UiPreferencesRepository(context: Context) {

    private val dataStore = context.applicationContext.uiPreferencesDataStore

    private val rawPreferences: Flow<Preferences> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }

    val preferences: Flow<UiPreferences> = rawPreferences.map { it.toUiPreferences() }

    /**
     * Blocking read for activity start, so the very first frame (and the window background behind
     * it) already has the right colors instead of flashing the defaults. The file is tiny.
     */
    fun readNow(): UiPreferences = runBlocking { rawPreferences.first().toUiPreferences() }

    suspend fun setLook(lookId: String) {
        dataStore.edit { it[KEY_LOOK] = lookId }
    }

    suspend fun setColorTheme(theme: ColorTheme) {
        dataStore.edit { it[KEY_THEME] = theme.name }
    }

    suspend fun setCustomBackground(argb: Int) {
        dataStore.edit { it[KEY_CUSTOM_BACKGROUND] = argb }
    }

    suspend fun setCustomInk(argb: Int?) {
        dataStore.edit { if (argb == null) it.remove(KEY_CUSTOM_INK) else it[KEY_CUSTOM_INK] = argb }
    }

    suspend fun setHideStatusBar(hide: Boolean) {
        dataStore.edit { it[KEY_HIDE_STATUS_BAR] = hide }
    }

    suspend fun setShowAppUsage(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_APP_USAGE] = show }
    }

    suspend fun setDrawerIcons(style: IconStyle) {
        dataStore.edit { prefs -> style.toMap().forEach { (key, value) -> prefs[stringPreferencesKey(key)] = value } }
    }

    suspend fun setIconPack(packageName: String?) {
        dataStore.edit { if (packageName == null) it.remove(KEY_ICON_PACK) else it[KEY_ICON_PACK] = packageName }
    }

    suspend fun setSearchHidden(search: Boolean) {
        dataStore.edit { it[KEY_SEARCH_HIDDEN] = search }
    }

    suspend fun setDisplayFont(id: String?) {
        dataStore.edit { if (id == null) it.remove(KEY_DISPLAY_FONT) else it[KEY_DISPLAY_FONT] = id }
    }

    suspend fun setBodyFont(id: String?) {
        dataStore.edit { if (id == null) it.remove(KEY_BODY_FONT) else it[KEY_BODY_FONT] = id }
    }

    suspend fun setTextScale(scale: Float) {
        dataStore.edit { it[KEY_TEXT_SCALE] = scale }
    }

    suspend fun setUppercaseLabels(uppercase: Boolean) {
        dataStore.edit { it[KEY_UPPERCASE_LABELS] = uppercase }
    }

    suspend fun setWallpaper(show: Boolean) {
        dataStore.edit { it[KEY_WALLPAPER] = show }
    }

    suspend fun setWallpaperDim(dim: Float) {
        dataStore.edit { it[KEY_WALLPAPER_DIM] = dim }
    }

    suspend fun setCategoryDisplay(display: String) {
        dataStore.edit { it[KEY_CATEGORY_DISPLAY] = display }
    }

    suspend fun setAutoHideDays(days: Int) {
        dataStore.edit { it[KEY_AUTO_HIDE_DAYS] = days.coerceIn(0, 365) }
    }

    suspend fun setShowRecents(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_RECENTS] = show }
    }

    suspend fun setSearchNotes(on: Boolean) {
        dataStore.edit { it[KEY_SEARCH_NOTES] = on }
    }

    suspend fun setSearchSettings(on: Boolean) {
        dataStore.edit { it[KEY_SEARCH_SETTINGS] = on }
    }

    suspend fun setSound(on: Boolean) {
        dataStore.edit { it[KEY_SOUND] = on }
    }

    suspend fun setSoundVolume(volume: Float) {
        dataStore.edit { it[KEY_SOUND_VOLUME] = volume.coerceIn(0f, 1f) }
    }

    suspend fun setReducedMotion(on: Boolean) {
        dataStore.edit { it[KEY_REDUCED_MOTION] = on }
    }

    suspend fun setOnboarded(done: Boolean) {
        dataStore.edit { it[KEY_ONBOARDED] = done }
    }

    suspend fun setEditCoachSeen(seen: Boolean) {
        dataStore.edit { it[KEY_EDIT_COACH] = seen }
    }

    /**
     * Decides once, on the first run of a version that has onboarding, whether this phone is a
     * fresh install or an upgrade. An empty preferences file means fresh; anything already in it
     * means the user has been using Nulis and must not be marched through setup.
     */
    suspend fun ensureOnboardingFlag() {
        dataStore.edit { prefs ->
            if (prefs[KEY_ONBOARDED] == null) prefs[KEY_ONBOARDED] = prefs.asMap().isNotEmpty()
        }
    }

    suspend fun setCustomAccent(argb: Int?) {
        dataStore.edit { prefs -> if (argb == null) prefs.remove(KEY_CUSTOM_ACCENT) else prefs[KEY_CUSTOM_ACCENT] = argb }
    }

    /** Background, ink and accent together: a palette is one decision, so it is one write. */
    suspend fun applyPalette(background: Int, ink: Int, accent: Int) {
        dataStore.edit { prefs ->
            prefs[KEY_THEME] = ColorTheme.CUSTOM.name
            prefs[KEY_CUSTOM_BACKGROUND] = background
            prefs[KEY_CUSTOM_INK] = ink
            prefs[KEY_CUSTOM_ACCENT] = accent
        }
    }

    /** Everything a theme decides, written in one edit so the UI never sees a half-applied theme. */
    suspend fun applySetupAppearance(
        lookId: String,
        colorTheme: ColorTheme,
        customBackground: Int?,
        customInk: Int?,
        displayFont: String?,
        bodyFont: String?,
        textScale: Float,
        uppercaseLabels: Boolean,
        setupId: String?,
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_LOOK] = lookId
            prefs[KEY_THEME] = colorTheme.name
            if (customBackground != null) prefs[KEY_CUSTOM_BACKGROUND] = customBackground
            if (customInk == null) prefs.remove(KEY_CUSTOM_INK) else prefs[KEY_CUSTOM_INK] = customInk
            // A theme decides its own colours; an accent picked for the last one does not follow.
            prefs.remove(KEY_CUSTOM_ACCENT)
            if (displayFont == null) prefs.remove(KEY_DISPLAY_FONT) else prefs[KEY_DISPLAY_FONT] = displayFont
            if (bodyFont == null) prefs.remove(KEY_BODY_FONT) else prefs[KEY_BODY_FONT] = bodyFont
            prefs[KEY_TEXT_SCALE] = textScale
            prefs[KEY_UPPERCASE_LABELS] = uppercaseLabels
            if (setupId == null) prefs.remove(KEY_THEME_ID) else prefs[KEY_THEME_ID] = setupId
        }
    }

    /**
     * Writes back whatever a backup carried, in one edit. A null means the file did not have
     * that setting, so what is on the phone is left alone.
     */
    @Suppress("LongParameterList")
    suspend fun restore(
        lookId: String?,
        colorTheme: ColorTheme?,
        customBackground: Int?,
        customInk: Int?,
        hideStatusBar: Boolean?,
        showAppUsage: Boolean?,
        drawerIcons: IconStyle?,
        iconPack: String?,
        searchHidden: Boolean?,
        drawerPlacement: String?,
        notificationDots: Boolean?,
        displayFontId: String?,
        bodyFontId: String?,
        textScale: Float?,
        uppercaseLabels: Boolean?,
        wallpaper: Boolean?,
        wallpaperDim: Float?,
        setupId: String?,
        categoryDisplay: String?,
        autoHideDays: Int?,
        showRecents: Boolean?,
        searchNotes: Boolean?,
        searchSettings: Boolean?,
        sound: Boolean?,
        soundVolume: Float?,
        reducedMotion: Boolean?,
        editCoachSeen: Boolean?,
        customAccent: Int?,
    ) {
        dataStore.edit { prefs ->
            lookId?.let { prefs[KEY_LOOK] = it }
            colorTheme?.let { prefs[KEY_THEME] = it.name }
            customBackground?.let { prefs[KEY_CUSTOM_BACKGROUND] = it }
            if (customInk == null) prefs.remove(KEY_CUSTOM_INK) else prefs[KEY_CUSTOM_INK] = customInk
            if (customAccent == null) prefs.remove(KEY_CUSTOM_ACCENT) else prefs[KEY_CUSTOM_ACCENT] = customAccent
            hideStatusBar?.let { prefs[KEY_HIDE_STATUS_BAR] = it }
            showAppUsage?.let { prefs[KEY_SHOW_APP_USAGE] = it }
            drawerIcons?.toMap()?.forEach { (key, value) -> prefs[stringPreferencesKey(key)] = value }
            if (iconPack == null) prefs.remove(KEY_ICON_PACK) else prefs[KEY_ICON_PACK] = iconPack
            searchHidden?.let { prefs[KEY_SEARCH_HIDDEN] = it }
            DrawerPlacement.byId(drawerPlacement)?.let { prefs[KEY_DRAWER_PLACEMENT] = it.id }
            notificationDots?.let { prefs[KEY_NOTIFICATION_DOTS] = it }
            if (displayFontId == null) prefs.remove(KEY_DISPLAY_FONT) else prefs[KEY_DISPLAY_FONT] = displayFontId
            if (bodyFontId == null) prefs.remove(KEY_BODY_FONT) else prefs[KEY_BODY_FONT] = bodyFontId
            textScale?.let { prefs[KEY_TEXT_SCALE] = it }
            uppercaseLabels?.let { prefs[KEY_UPPERCASE_LABELS] = it }
            wallpaper?.let { prefs[KEY_WALLPAPER] = it }
            wallpaperDim?.let { prefs[KEY_WALLPAPER_DIM] = it }
            if (setupId == null) prefs.remove(KEY_THEME_ID) else prefs[KEY_THEME_ID] = setupId
            categoryDisplay?.let { prefs[KEY_CATEGORY_DISPLAY] = it }
            autoHideDays?.let { prefs[KEY_AUTO_HIDE_DAYS] = it }
            showRecents?.let { prefs[KEY_SHOW_RECENTS] = it }
            searchNotes?.let { prefs[KEY_SEARCH_NOTES] = it }
            searchSettings?.let { prefs[KEY_SEARCH_SETTINGS] = it }
            sound?.let { prefs[KEY_SOUND] = it }
            soundVolume?.let { prefs[KEY_SOUND_VOLUME] = it }
            reducedMotion?.let { prefs[KEY_REDUCED_MOTION] = it }
            editCoachSeen?.let { prefs[KEY_EDIT_COACH] = it }
            // A restored phone has clearly been set up before.
            prefs[KEY_ONBOARDED] = true
        }
    }

    /** Back to the defaults, but still onboarded: a reset is not a fresh install. */
    suspend fun clearAll() {
        dataStore.edit { prefs ->
            prefs.clear()
            prefs[KEY_ONBOARDED] = true
        }
    }

    /**
     * Forgets which theme and which layout are applied; called as soon as the user edits a page
     * themselves. The pages are their work then, not a preset's.
     */
    suspend fun clearSetupId() {
        dataStore.edit { it.remove(KEY_THEME_ID); it.remove(KEY_LAYOUT_ID) }
    }

    /** Remembers which layout was applied. Only the arrangement; nothing about the look. */
    suspend fun setLayoutId(layoutId: String) {
        dataStore.edit { it[KEY_LAYOUT_ID] = layoutId }
    }

    suspend fun setLeftPageGear(on: Boolean) {
        dataStore.edit { it[KEY_LEFT_GEAR] = on }
    }

    suspend fun setDrawerPlacement(placement: DrawerPlacement) {
        dataStore.edit { it[KEY_DRAWER_PLACEMENT] = placement.id }
    }

    suspend fun setNotificationDots(on: Boolean) {
        dataStore.edit { it[KEY_NOTIFICATION_DOTS] = on }
    }

    private fun Preferences.toUiPreferences(): UiPreferences {
        val defaults = UiPreferences()
        return UiPreferences(
            lookId = this[KEY_LOOK] ?: defaults.lookId,
            colorTheme = this[KEY_THEME]?.let { runCatching { ColorTheme.valueOf(it) }.getOrNull() } ?: defaults.colorTheme,
            customBackground = this[KEY_CUSTOM_BACKGROUND] ?: defaults.customBackground,
            customInk = this[KEY_CUSTOM_INK],
            customAccent = this[KEY_CUSTOM_ACCENT],
            hideStatusBar = this[KEY_HIDE_STATUS_BAR] ?: defaults.hideStatusBar,
            showAppUsage = this[KEY_SHOW_APP_USAGE] ?: defaults.showAppUsage,
            drawerIcons = IconStyle.from(
                IconStyle().toMap().keys.mapNotNull { key -> this[stringPreferencesKey(key)]?.let { key to it } }.toMap(),
                defaults.drawerIcons,
            ),
            iconPack = this[KEY_ICON_PACK],
            searchHidden = this[KEY_SEARCH_HIDDEN] ?: defaults.searchHidden,
            drawerPlacement = DrawerPlacement.byId(this[KEY_DRAWER_PLACEMENT]) ?: defaults.drawerPlacement,
            notificationDots = this[KEY_NOTIFICATION_DOTS] ?: defaults.notificationDots,
            displayFontId = this[KEY_DISPLAY_FONT],
            bodyFontId = this[KEY_BODY_FONT],
            textScale = (this[KEY_TEXT_SCALE] ?: defaults.textScale).coerceIn(0.8f, 1.4f),
            uppercaseLabels = this[KEY_UPPERCASE_LABELS] ?: defaults.uppercaseLabels,
            wallpaper = this[KEY_WALLPAPER] ?: defaults.wallpaper,
            wallpaperDim = (this[KEY_WALLPAPER_DIM] ?: defaults.wallpaperDim).coerceIn(0f, 1f),
            setupId = this[KEY_THEME_ID],
            layoutId = this[KEY_LAYOUT_ID],
            leftPageGear = this[KEY_LEFT_GEAR] ?: defaults.leftPageGear,
            // Same rule as ensureOnboardingFlag, for the moment before it has run.
            onboarded = this[KEY_ONBOARDED] ?: asMap().isNotEmpty(),
            editCoachSeen = this[KEY_EDIT_COACH] ?: defaults.editCoachSeen,
            categoryDisplay = this[KEY_CATEGORY_DISPLAY] ?: defaults.categoryDisplay,
            autoHideDays = this[KEY_AUTO_HIDE_DAYS] ?: defaults.autoHideDays,
            showRecents = this[KEY_SHOW_RECENTS] ?: defaults.showRecents,
            searchNotes = this[KEY_SEARCH_NOTES] ?: defaults.searchNotes,
            searchSettings = this[KEY_SEARCH_SETTINGS] ?: defaults.searchSettings,
            sound = this[KEY_SOUND] ?: defaults.sound,
            soundVolume = (this[KEY_SOUND_VOLUME] ?: defaults.soundVolume).coerceIn(0f, 1f),
            reducedMotion = this[KEY_REDUCED_MOTION] ?: defaults.reducedMotion,
        )
    }

    private companion object {
        val KEY_DRAWER_PLACEMENT = stringPreferencesKey("drawer_placement")
        val KEY_NOTIFICATION_DOTS = booleanPreferencesKey("notification_dots")
        val KEY_LOOK = stringPreferencesKey("look")
        val KEY_THEME = stringPreferencesKey("color_theme")
        val KEY_CUSTOM_BACKGROUND = intPreferencesKey("custom_background")
        val KEY_CUSTOM_INK = intPreferencesKey("custom_ink")
        val KEY_CUSTOM_ACCENT = intPreferencesKey("custom_accent")
        val KEY_HIDE_STATUS_BAR = booleanPreferencesKey("hide_status_bar")
        val KEY_SHOW_APP_USAGE = booleanPreferencesKey("show_app_usage")
        val KEY_ICON_PACK = stringPreferencesKey("icon_pack")
        val KEY_SEARCH_HIDDEN = booleanPreferencesKey("search_hidden")
        val KEY_DISPLAY_FONT = stringPreferencesKey("display_font")
        val KEY_BODY_FONT = stringPreferencesKey("body_font")
        val KEY_TEXT_SCALE = floatPreferencesKey("text_scale")
        val KEY_UPPERCASE_LABELS = booleanPreferencesKey("uppercase_labels")
        val KEY_WALLPAPER = booleanPreferencesKey("wallpaper")
        val KEY_WALLPAPER_DIM = floatPreferencesKey("wallpaper_dim")
        val KEY_THEME_ID = stringPreferencesKey("theme_id")
        val KEY_LAYOUT_ID = stringPreferencesKey("layout_id")
        val KEY_LEFT_GEAR = booleanPreferencesKey("left_page_gear")
        val KEY_ONBOARDED = booleanPreferencesKey("onboarded")
        val KEY_EDIT_COACH = booleanPreferencesKey("edit_coach_seen")
        val KEY_CATEGORY_DISPLAY = stringPreferencesKey("category_display")
        val KEY_AUTO_HIDE_DAYS = intPreferencesKey("auto_hide_days")
        val KEY_SHOW_RECENTS = booleanPreferencesKey("show_recents")
        val KEY_SEARCH_NOTES = booleanPreferencesKey("search_notes")
        val KEY_SEARCH_SETTINGS = booleanPreferencesKey("search_settings")
        val KEY_SOUND = booleanPreferencesKey("sound")
        val KEY_SOUND_VOLUME = floatPreferencesKey("sound_volume")
        val KEY_REDUCED_MOTION = booleanPreferencesKey("reduced_motion")
    }
}

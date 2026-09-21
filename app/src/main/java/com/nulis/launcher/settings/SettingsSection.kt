// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.settings

import com.nulis.launcher.drawer.SettingsTarget

/**
 * The top level of settings: seven screens, each one thing.
 *
 * The old settings screen was a single column you scrolled through for a while, which is fine
 * when there are five settings and hopeless when there are fifty. Every one of these opens its
 * own screen, so the first thing anybody sees is a list of decisions rather than the first
 * decision.
 */
enum class SettingsSection(val title: String, val summary: String) {
    LAYOUT("Layout", "Layouts, pages, saved setups, editing"),
    LOOK("Look and colours", "Type, background, wallpaper, sound"),
    APPS("Apps and drawer", "Icons, categories, hidden apps, search"),
    GESTURES("Gestures", "What swipes, taps and long presses do"),
    WELLBEING("Wellbeing", "Pauses, daily limits, focus, your week"),
    BACKUP("Backup", "Export, import, start again"),
    ABOUT("About", "Version, what is stored, font licences"),
}

/**
 * Which screen a search result lives on now. The drawer's settings search still names a target
 * rather than a screen, so that the phrase somebody types keeps pointing at the same setting
 * however the screens are arranged.
 */
val SettingsTarget.section: SettingsSection
    get() = when (this) {
        SettingsTarget.ARRANGEMENT -> SettingsSection.LAYOUT
        SettingsTarget.PAGE -> SettingsSection.LAYOUT
        SettingsTarget.LOOK -> SettingsSection.LOOK
        SettingsTarget.BACKGROUND -> SettingsSection.LOOK
        SettingsTarget.TYPE -> SettingsSection.LOOK
        SettingsTarget.DISPLAY -> SettingsSection.LOOK
        SettingsTarget.SOUND -> SettingsSection.LOOK
        SettingsTarget.APP_ICONS -> SettingsSection.APPS
        SettingsTarget.CATEGORIES -> SettingsSection.APPS
        SettingsTarget.DRAWER -> SettingsSection.APPS
        SettingsTarget.GESTURES -> SettingsSection.GESTURES
        SettingsTarget.WELLBEING -> SettingsSection.WELLBEING
        SettingsTarget.BACKUP -> SettingsSection.BACKUP
    }

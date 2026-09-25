// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.backup

import android.content.Context
import android.net.Uri
import android.util.Log
import com.nulis.launcher.apps.AppCustomizationRepository
import com.nulis.launcher.apps.CategoryRepository
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.focus.FocusRepository
import com.nulis.launcher.blocks.writing.WritingRepository
import com.nulis.launcher.gestures.GesturesRepository
import com.nulis.launcher.icons.IconStyle
import com.nulis.launcher.layout.LayoutRepository
import com.nulis.launcher.settings.UiPreferencesRepository
import com.nulis.launcher.setups.SetupRepository
import com.nulis.launcher.ui.theme.ColorTheme
import com.nulis.launcher.wellbeing.Wellbeing
import com.nulis.launcher.wellbeing.WellbeingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Gathers everything into one file and puts it back again.
 *
 * Reading is deliberately forgiving and writing is deliberately plain: pretty-printed JSON that
 * a person can open, read and edit. Nothing leaves the phone - the file goes wherever the
 * system file picker puts it, and Nulis has no internet permission to send it anywhere.
 */
class BackupRepository(
    private val context: Context,
    private val layoutRepository: LayoutRepository,
    private val setupRepository: SetupRepository,
    private val uiPreferencesRepository: UiPreferencesRepository,
    private val gesturesRepository: GesturesRepository,
    private val appCustomizationRepository: AppCustomizationRepository,
    private val writingRepository: WritingRepository,
    private val categoryRepository: CategoryRepository,
    private val wellbeingRepository: WellbeingRepository,
    private val focusRepository: FocusRepository,
    private val appVersion: String,
) {

    /** Everything, as it is right now. */
    suspend fun gather(): NulisBackup {
        val pagesConfig = layoutRepository.pages.first()
        val preferences = uiPreferencesRepository.preferences.first()
        val customization = appCustomizationRepository.state.first()
        val writing = writingRepository.state.first()
        val focus = focusRepository.state.first()
        val wellbeing = wellbeingRepository.state.first()
        return NulisBackup(
            createdAt = System.currentTimeMillis(),
            appVersion = appVersion,
            pages = pagesConfig.ids.mapNotNull { id -> layoutRepository.layout(id).first()?.let { id to it } }.toMap(),
            pageOrder = pagesConfig.ids,
            homePage = pagesConfig.homeId,
            setups = setupRepository.saved.first(),
            preferences = BackupPreferences(
                lookId = preferences.lookId,
                colorTheme = preferences.colorTheme.name,
                customBackground = preferences.customBackground,
                customInk = preferences.customInk,
                customAccent = preferences.customAccent,
                hideStatusBar = preferences.hideStatusBar,
                showAppUsage = preferences.showAppUsage,
                drawerIcons = preferences.drawerIcons.toMap(),
                iconPack = preferences.iconPack,
                searchHidden = preferences.searchHidden,
                drawerPlacement = preferences.drawerPlacement.id,
                notificationDots = preferences.notificationDots,
                displayFontId = preferences.displayFontId,
                bodyFontId = preferences.bodyFontId,
                textScale = preferences.textScale,
                uppercaseLabels = preferences.uppercaseLabels,
                wallpaper = preferences.wallpaper,
                wallpaperDim = preferences.wallpaperDim,
                setupId = preferences.setupId,
                categoryDisplay = preferences.categoryDisplay,
                autoHideDays = preferences.autoHideDays,
                showRecents = preferences.showRecents,
                searchNotes = preferences.searchNotes,
                searchSettings = preferences.searchSettings,
                sound = preferences.sound,
                soundVolume = preferences.soundVolume,
                reducedMotion = preferences.reducedMotion,
                editCoachSeen = preferences.editCoachSeen,
            ),
            gestures = gesturesRepository.exportBindings(),
            appNames = customization.names,
            appIcons = customization.icons,
            hiddenApps = customization.hidden.toList(),
            categories = categoryRepository.categories.first().all,
            notes = writing.notes,
            journal = writing.journal,
            tasks = writing.tasks,
            habits = writing.habits,
            wellbeing = BackupWellbeing(
                pausedPackages = wellbeing.pausedPackages.toList(),
                pauseSeconds = wellbeing.pauseSeconds,
                limits = wellbeing.limits,
                focusAllowed = wellbeing.focusAllowed.toList(),
                focusHides = wellbeing.focusHides,
                focusGuards = wellbeing.focusGuards,
            ),
            focusSessions = focus.history,
            focusMinutes = focus.focusMinutes,
            breakMinutes = focus.breakMinutes,
        )
    }

    /** Writes [backup] to the file the user picked. Returns false if it could not be written. */
    suspend fun write(uri: Uri, backup: NulisBackup): Boolean = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(backup.encode().toByteArray())
            } ?: return@withContext false
            true
        } catch (e: IOException) {
            Log.w(TAG, "Could not write the backup", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Not allowed to write there", e)
            false
        }
    }

    /** Reads a file the user picked, saying plainly why if it cannot. */
    suspend fun read(uri: Uri): BackupResult = withContext(Dispatchers.IO) {
        val text = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: IOException) {
            Log.w(TAG, "Could not read the backup", e)
            return@withContext BackupResult.Failed(BackupError.Unreadable(e.message ?: "file error"))
        } catch (e: SecurityException) {
            return@withContext BackupResult.Failed(BackupError.Unreadable("no permission for that file"))
        }
        parseBackup(text)
    }

    /**
     * Puts a backup back. Sections the file does not carry are left exactly as they are, so a
     * file written by an older Nulis never wipes something it had never heard of.
     */
    suspend fun restore(backup: NulisBackup) {
        if (backup.pages.isNotEmpty()) {
            // A file from before pages could move carries no order; its three pages then go back
            // where they have always been, with the middle one as home.
            val order = backup.pageOrder.filter { it in backup.pages.keys }
                .ifEmpty { PageIds.all.filter { it in backup.pages.keys } }
                .ifEmpty { backup.pages.keys.toList() }
            layoutRepository.replaceAll(
                pages = backup.pages,
                order = order,
                homeId = backup.homePage?.takeIf { it in order } ?: PageIds.HOME.takeIf { it in order } ?: order[order.size / 2],
            )
        }
        setupRepository.replaceAll(backup.setups)
        categoryRepository.replaceAll(backup.categories)
        gesturesRepository.importBindings(backup.gestures)
        appCustomizationRepository.replaceAll(backup.appNames, backup.appIcons, backup.hiddenApps.toSet())
        writingRepository.replaceAll(backup.notes, backup.journal, backup.tasks, backup.habits)
        focusRepository.replaceAll(backup.focusSessions, backup.focusMinutes, backup.breakMinutes)
        backup.wellbeing?.let { stored ->
            wellbeingRepository.replaceAll(
                Wellbeing(
                    pausedPackages = stored.pausedPackages.toSet(),
                    pauseSeconds = stored.pauseSeconds,
                    limits = stored.limits,
                    focusAllowed = stored.focusAllowed.toSet(),
                    focusHides = stored.focusHides,
                    focusGuards = stored.focusGuards,
                ),
            )
        }
        backup.preferences?.let { stored ->
            uiPreferencesRepository.restore(
                lookId = stored.lookId,
                colorTheme = stored.colorTheme?.let { runCatching { ColorTheme.valueOf(it) }.getOrNull() },
                customBackground = stored.customBackground,
                customInk = stored.customInk,
                customAccent = stored.customAccent,
                hideStatusBar = stored.hideStatusBar,
                showAppUsage = stored.showAppUsage,
                drawerIcons = stored.drawerIcons.takeIf { it.isNotEmpty() }?.let { IconStyle.from(it) },
                iconPack = stored.iconPack,
                searchHidden = stored.searchHidden,
                drawerPlacement = stored.drawerPlacement,
                notificationDots = stored.notificationDots,
                displayFontId = stored.displayFontId,
                bodyFontId = stored.bodyFontId,
                textScale = stored.textScale,
                uppercaseLabels = stored.uppercaseLabels,
                wallpaper = stored.wallpaper,
                wallpaperDim = stored.wallpaperDim,
                setupId = stored.setupId,
                categoryDisplay = stored.categoryDisplay,
                autoHideDays = stored.autoHideDays,
                showRecents = stored.showRecents,
                searchNotes = stored.searchNotes,
                searchSettings = stored.searchSettings,
                sound = stored.sound,
                soundVolume = stored.soundVolume,
                reducedMotion = stored.reducedMotion,
                editCoachSeen = stored.editCoachSeen,
            )
        }
    }

    /**
     * Puts the launcher back the way it came out of the box: default pages, default appearance,
     * no themes of your own, no categories, no gestures, no per-app changes, and none of your
     * writing. There is no undo, which is why the screen asks twice.
     */
    suspend fun resetEverything() {
        writingRepository.replaceAll(emptyList(), emptyList(), emptyList())
        setupRepository.replaceAll(emptyList())
        categoryRepository.clear()
        gesturesRepository.reset()
        appCustomizationRepository.clearAll()
        focusRepository.clear()
        wellbeingRepository.clear()
        uiPreferencesRepository.clearAll()
        layoutRepository.resetToDefaults()
    }

    /** A filename with today's date in it, so a folder of backups sorts itself. */
    fun suggestedFileName(): String {
        val date = java.time.LocalDate.now()
        return "nulis-$date.json"
    }

    private companion object {
        const val TAG = "BackupRepository"
    }
}

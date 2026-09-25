// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.backup

import com.nulis.launcher.apps.AppCategory
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.focus.FocusSession
import com.nulis.launcher.blocks.writing.Habits
import com.nulis.launcher.blocks.writing.JournalEntry
import com.nulis.launcher.blocks.writing.Note
import com.nulis.launcher.blocks.writing.Task
import com.nulis.launcher.setups.SavedSetup
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One file with everything in it.
 *
 * Versioned by [format]: a reader refuses a file from a future format rather than guessing, and
 * every section has a default so a file from an older Nulis - or one that was written before a
 * feature existed - simply restores what it has. Nothing here is encrypted or obfuscated: it is
 * the user's own data and they should be able to read it in a text editor.
 *
 * Quotes and countdowns are not their own sections: they live inside the blocks that hold them,
 * which are inside [pages].
 */
@Serializable
data class NulisBackup(
    val format: Int = FORMAT,
    /** Millis, for the preview to say how old the file is. */
    val createdAt: Long = 0L,
    /** The Nulis that wrote it, for a human reading the file. */
    val appVersion: String = "",
    val pages: Map<String, PageLayout> = emptyMap(),
    /**
     * The pages in swipe order. Empty in a file written before pages could be added or removed;
     * a reader then falls back to the three Nulis always had, in the order it always had them.
     */
    val pageOrder: List<String> = emptyList(),
    /** Which page carries the home mark, or null in a file written before that could move. */
    val homePage: String? = null,
    /**
     * The setups the user saved. The field kept the name it was written with, so a backup made
     * when these were called themes restores into the same place.
     */
    @SerialName("themes")
    val setups: List<SavedSetup> = emptyList(),
    val preferences: BackupPreferences? = null,
    /** Gesture trigger id -> stored binding. */
    val gestures: Map<String, String> = emptyMap(),
    /** App id -> the name the user gave it. */
    val appNames: Map<String, String> = emptyMap(),
    /** App id -> chosen drawable from the active icon pack. */
    val appIcons: Map<String, String> = emptyMap(),
    val hiddenApps: List<String> = emptyList(),
    val categories: List<AppCategory> = emptyList(),
    val notes: List<Note> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val tasks: List<Task> = emptyList(),
    /** Absent from a file written before habits existed; restoring one leaves them alone. */
    val habits: Habits? = null,
    val wellbeing: BackupWellbeing? = null,
    val focusSessions: List<FocusSession> = emptyList(),
    val focusMinutes: Int = 25,
    val breakMinutes: Int = 5,
) {
    companion object {
        /** Bump only for a change a previous reader could not cope with. */
        const val FORMAT = 1
    }
}

/** The appearance settings, flattened. Everything optional, everything defaulted. */
@Serializable
data class BackupPreferences(
    val lookId: String? = null,
    val colorTheme: String? = null,
    val customBackground: Int? = null,
    val customInk: Int? = null,
    val customAccent: Int? = null,
    val hideStatusBar: Boolean? = null,
    val showAppUsage: Boolean? = null,
    val drawerIcons: Map<String, String> = emptyMap(),
    val iconPack: String? = null,
    val searchHidden: Boolean? = null,
    /** Id of a [com.nulis.launcher.drawer.DrawerPlacement]; absent in files written before it. */
    val drawerPlacement: String? = null,
    val notificationDots: Boolean? = null,
    val displayFontId: String? = null,
    val bodyFontId: String? = null,
    val textScale: Float? = null,
    val uppercaseLabels: Boolean? = null,
    val wallpaper: Boolean? = null,
    val wallpaperDim: Float? = null,
    val setupId: String? = null,
    val categoryDisplay: String? = null,
    val autoHideDays: Int? = null,
    val showRecents: Boolean? = null,
    val searchNotes: Boolean? = null,
    val searchSettings: Boolean? = null,
    val sound: Boolean? = null,
    val soundVolume: Float? = null,
    val reducedMotion: Boolean? = null,
    /** Whether the editor has already said what a long press gives you. */
    val editCoachSeen: Boolean? = null,
)

@Serializable
data class BackupWellbeing(
    val pausedPackages: List<String> = emptyList(),
    val pauseSeconds: Int = 5,
    val limits: Map<String, Int> = emptyMap(),
    val focusAllowed: List<String> = emptyList(),
    val focusHides: Boolean = false,
    val focusGuards: Boolean = true,
)

/** What a file would do if it were restored, counted for the preview. */
data class BackupPreview(
    val createdAt: Long,
    val appVersion: String,
    val pages: Int,
    val blocks: Int,
    val setups: Int,
    val gestures: Int,
    val renamedApps: Int,
    val hiddenApps: Int,
    val categories: Int,
    val notes: Int,
    val journal: Int,
    val tasks: Int,
    /** Null for a file written before habits existed. */
    val habits: Int? = null,
    val focusSessions: Int,
    val hasPreferences: Boolean,
    val hasWellbeing: Boolean,
)

fun NulisBackup.preview(): BackupPreview = BackupPreview(
    createdAt = createdAt,
    appVersion = appVersion,
    pages = pages.size,
    blocks = pages.values.sumOf { it.blocks.size },
    setups = setups.size,
    gestures = gestures.size,
    renamedApps = appNames.size,
    hiddenApps = hiddenApps.size,
    categories = categories.size,
    notes = notes.size,
    journal = journal.size,
    tasks = tasks.size,
    habits = habits?.habits?.size,
    focusSessions = focusSessions.size,
    hasPreferences = preferences != null,
    hasWellbeing = wellbeing != null,
)

/** Why a file could not be read, in words a person can act on. */
sealed interface BackupError {
    data object NotJson : BackupError
    data class TooNew(val format: Int) : BackupError
    data class Unreadable(val message: String) : BackupError
    data object Empty : BackupError
}

sealed interface BackupResult {
    data class Ok(val backup: NulisBackup) : BackupResult
    data class Failed(val error: BackupError) : BackupResult
}

fun BackupError.message(): String = when (this) {
    BackupError.NotJson -> "That file is not a Nulis backup."
    is BackupError.TooNew -> "That backup was written by a newer Nulis (format $format). Update first."
    is BackupError.Unreadable -> "That backup could not be read: $message"
    BackupError.Empty -> "That backup has nothing in it."
}

private val BackupJson = kotlinx.serialization.json.Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    prettyPrint = true
}

/** The file's text, ready to be written. Plain and pretty-printed on purpose. */
fun NulisBackup.encode(): String = BackupJson.encodeToString(this)

/**
 * Turns text into a backup, or says why not: a photo, a truncated file, a file from a future
 * Nulis, an empty object. Pure, so every refusal is tested rather than hoped for.
 */
fun parseBackup(text: String?): BackupResult {
    if (text.isNullOrBlank()) return BackupResult.Failed(BackupError.Empty)
    if (!text.trimStart().startsWith("{")) return BackupResult.Failed(BackupError.NotJson)
    val backup = try {
        BackupJson.decodeFromString<NulisBackup>(text)
    } catch (e: kotlinx.serialization.SerializationException) {
        return BackupResult.Failed(BackupError.Unreadable(e.message?.take(120) ?: "bad JSON"))
    } catch (e: IllegalArgumentException) {
        return BackupResult.Failed(BackupError.Unreadable(e.message?.take(120) ?: "bad data"))
    }
    if (backup.format > NulisBackup.FORMAT) return BackupResult.Failed(BackupError.TooNew(backup.format))
    val preview = backup.preview()
    val nothing = preview.pages == 0 && preview.setups == 0 && preview.notes == 0 &&
        preview.journal == 0 && preview.tasks == 0 && (preview.habits ?: 0) == 0 && preview.categories == 0 &&
        !preview.hasPreferences && preview.gestures == 0
    if (nothing) return BackupResult.Failed(BackupError.Empty)
    return BackupResult.Ok(backup)
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.drawer

import com.nulis.launcher.apps.AppCategory
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.apps.Categories
import com.nulis.launcher.blocks.calculator.Expression
import com.nulis.launcher.blocks.writing.WritingState
import java.util.Locale

/** How the user's own groups are laid out in the drawer. */
enum class CategoryDisplay(val label: String) {
    /** Each group is a section with its apps under it, then everything else A to Z. */
    SECTIONS("Sections"),

    /** Each group is one row that opens; everything else is A to Z below. */
    GROUPS("Groups"),
}

/** Where a settings search result goes. The id is the section's own label in settings. */
enum class SettingsTarget(val title: String, val keywords: List<String>) {
    // "Theme" is not a thing in Nulis any more, but it is still what people type, so the word
    // stays as a keyword pointing at the two halves it used to mean.
    ARRANGEMENT("Arrangement", listOf("layout", "arrangement", "preset", "setup", "save", "theme", "gallery")),
    LOOK("Look", listOf("look", "dot", "clean", "matrix")),
    BACKGROUND("Background", listOf("background", "colour", "color", "black", "white", "custom", "ink", "dark", "light")),
    TYPE("Type", listOf("type", "font", "typeface", "serif", "mono", "text size", "uppercase", "licence", "license")),
    APP_ICONS("App icons", listOf("icon", "pack", "shape", "monogram", "grayscale")),
    CATEGORIES("Categories", listOf("category", "categories", "folder", "folders", "group", "groups")),
    DRAWER("Drawer", listOf("drawer", "recent", "hide", "unused", "work", "profile")),
    GESTURES("Gestures", listOf("gesture", "swipe", "tap", "long press", "double tap")),
    DISPLAY("Display", listOf("status bar", "wallpaper", "dim", "minutes", "screen time", "contrast", "motion", "accessibility")),
    WELLBEING("Wellbeing", listOf("wellbeing", "pause", "limit", "focus", "breathing", "summary", "mindful")),
    BACKUP("Backup", listOf("backup", "export", "import", "restore", "reset")),
    SOUND("Sound", listOf("sound", "click", "tick", "audio", "volume")),
    PAGE("Page", listOf("page", "edit", "pages", "home page", "onboarding")),
}

/** One thing the search found that is not an app. */
sealed interface SearchHit {
    /** The search field is a calculator when what is typed is arithmetic. */
    data class Math(val expression: String, val result: String) : SearchHit

    /** [snippet] is null when it would only repeat the title, which is most one-line notes. */
    data class Note(val id: String, val title: String, val snippet: String?) : SearchHit

    data class Task(val id: String, val text: String) : SearchHit

    data class Setting(val target: SettingsTarget) : SearchHit
}

/** One row of the drawer list. Headers know their own key so letter jumps land exactly. */
sealed class DrawerEntry(val key: String) {
    class LetterHeader(val letter: String) : DrawerEntry("letter_$letter")
    class CategoryHeader(val category: AppCategory, val count: Int, val collapsed: Boolean) :
        DrawerEntry("cat_${category.id}")

    class Section(val title: String) : DrawerEntry("section_$title")
    class App(val app: AppInfo) : DrawerEntry(app.id)
    class Hit(val hit: SearchHit) : DrawerEntry("hit_${hit.hashCode()}")
    class Recents(val apps: List<AppInfo>) : DrawerEntry("recents")
}

/** Everything the drawer needs to decide what to show. Kept plain so all of it can be tested. */
class DrawerInput(
    val apps: List<AppInfo>,
    val query: String,
    val hiddenIds: Set<String> = emptySet(),
    val searchHidden: Boolean = true,
    val categories: Categories = Categories(),
    val display: CategoryDisplay = CategoryDisplay.SECTIONS,
    val collapsed: Set<String> = emptySet(),
    /** App ids the drawer is hiding because they have not been opened for a long time. */
    val autoHiddenIds: Set<String> = emptySet(),
    /** Most recently used apps, newest first; empty when the row is off. */
    val recents: List<AppInfo> = emptyList(),
    val writing: WritingState = WritingState(),
    val searchNotes: Boolean = true,
    val searchSettings: Boolean = true,
    /** Packages a running focus session is guarding; drawn faded, or left out when [muteHides]. */
    val mutedPackages: Set<String> = emptySet(),
    val muteHides: Boolean = false,
)

/**
 * Turns the app list and everything around it into the exact rows the drawer draws.
 *
 * Pure on purpose: this is where every rule about what the drawer shows lives - hidden apps,
 * apps hidden for going unused, the user's groups, the recents row, and what a search matches -
 * and none of those rules should need a phone to check.
 */
fun buildDrawer(input: DrawerInput): List<DrawerEntry> {
    val needle = input.query.trim()
    return if (needle.isEmpty()) browse(input) else search(input, needle)
}

private fun browse(input: DrawerInput): List<DrawerEntry> {
    // Out of the list: what the user hid, and what has gone unused for long enough. Both are
    // still findable by typing, which is the difference between hidden and deleted.
    val visible = input.apps
        .filterNot { it.id in input.hiddenIds || it.id in input.autoHiddenIds }
        // A focus session can take the rest of the apps off the list entirely, if that is what
        // the user asked for. Searching still finds them: this is a session, not a lock.
        .filterNot { input.muteHides && it.packageName in input.mutedPackages }
    val grouped = input.categories.byApp
    val loose = visible.filterNot { grouped.containsKey(it.id) }

    return buildList {
        if (input.recents.isNotEmpty()) add(DrawerEntry.Recents(input.recents))
        input.categories.all.forEach { category ->
            val members = visible.filter { it.id in category.appIds.toSet() }
            if (members.isEmpty()) return@forEach
            val isCollapsed = input.display == CategoryDisplay.GROUPS && category.id in input.collapsed
            add(DrawerEntry.CategoryHeader(category, members.size, isCollapsed))
            if (!isCollapsed) members.forEach { add(DrawerEntry.App(it)) }
        }
        // The alphabet only covers what is not in a group, so the rail never points at a letter
        // whose apps are somewhere else on the screen.
        loose.groupBy { sectionLetter(it.label) }.forEach { (letter, group) ->
            add(DrawerEntry.LetterHeader(letter))
            group.forEach { add(DrawerEntry.App(it)) }
        }
    }
}

private fun search(input: DrawerInput, needle: String): List<DrawerEntry> {
    val pool = if (input.searchHidden) input.apps else input.apps.filterNot { it.id in input.hiddenIds }
    // An app hidden for going unused is always searchable; that is the whole bargain.
    val matches = pool.filter { it.label.contains(needle, ignoreCase = true) }
        .sortedWith(
            compareBy(
                // A name that starts with what was typed beats one that merely contains it.
                { if (it.label.startsWith(needle, ignoreCase = true)) 0 else 1 },
                { it.label.lowercase(Locale.getDefault()) },
            ),
        )

    val hits = buildList {
        Expression.evaluate(needle)?.let { add(SearchHit.Math(needle, Expression.format(it))) }
        if (input.searchNotes) {
            input.writing.notes
                .filter { it.text.contains(needle, ignoreCase = true) }
                .take(3)
                .forEach { note ->
                    val title = note.title.ifBlank { "Empty note" }
                    // A short note is all title, and a result that says the same thing twice in
                    // two sizes looks like a bug rather than a snippet.
                    val body = snippet(note.text, needle).takeIf { it != title }
                    add(SearchHit.Note(note.id, title, body))
                }
            input.writing.tasks
                .filter { !it.done && it.text.contains(needle, ignoreCase = true) }
                .take(2)
                .forEach { add(SearchHit.Task(it.id, it.text)) }
        }
        if (input.searchSettings) {
            SettingsTarget.entries
                .filter { target ->
                    target.title.contains(needle, ignoreCase = true) ||
                        target.keywords.any { it.contains(needle, ignoreCase = true) }
                }
                .take(3)
                .forEach { add(SearchHit.Setting(it)) }
        }
    }

    return buildList {
        // A sum is the answer to what was typed, so it goes above everything.
        hits.filterIsInstance<SearchHit.Math>().forEach { add(DrawerEntry.Hit(it)) }
        matches.forEach { add(DrawerEntry.App(it)) }
        val rest = hits.filterNot { it is SearchHit.Math }
        if (rest.isNotEmpty()) {
            add(DrawerEntry.Section("Also"))
            rest.forEach { add(DrawerEntry.Hit(it)) }
        }
    }
}

/** Twenty or so characters of the note around the match, so a result shows why it matched. */
private fun snippet(text: String, needle: String): String {
    val at = text.indexOf(needle, ignoreCase = true)
    if (at < 0) return text.take(60)
    val from = (at - 20).coerceAtLeast(0)
    val to = (at + needle.length + 40).coerceAtMost(text.length)
    return buildString {
        if (from > 0) append('…')
        append(text.substring(from, to).replace('\n', ' '))
        if (to < text.length) append('…')
    }
}

fun sectionLetter(label: String): String {
    val first = label.trimStart().firstOrNull() ?: return "#"
    return if (first.isLetter()) first.uppercase(Locale.getDefault()) else "#"
}

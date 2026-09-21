// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.layout

import com.nulis.launcher.data.replaceCorrupted
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PagesConfig
import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import com.nulis.launcher.blocks.battery.BatteryBlockDefinition
import com.nulis.launcher.blocks.screentime.ScreenTimeBlockDefinition
import com.nulis.launcher.blocks.steps.StepsBlockDefinition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

private val Context.layoutDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "layout",
    corruptionHandler = replaceCorrupted("layout"),
)

/** Persists one [PageLayout] per page id as JSON, on-device only. */
class LayoutRepository(
    context: Context,
    private val legacyFavorites: LegacyFavoritesStore,
    /**
     * The apps a page that has never been saved starts with: the phone's own dialer, messages,
     * camera, browser, gallery and clock. Only consulted for a page being written for the first
     * time, so an existing install is never touched.
     */
    private val defaultFavorites: suspend () -> List<String> = { emptyList() },
) {

    /**
     * The phone's own dialer, messages, camera, browser, gallery and clock. Used for a page
     * being written for the first time, and by anything else that needs apps for an Apps block
     * that nobody has chosen any for yet.
     */
    suspend fun deviceDefaultFavorites(): List<String> = defaultFavorites()


    private val dataStore = context.applicationContext.layoutDataStore
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Which pages there are and which one is home. A fresh install has the three Nulis has
     * always had; anything the user adds, removes or reorders is written here and nowhere else.
     */
    val pages: Flow<PagesConfig> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val ids = prefs[KEY_PAGES]?.split(',')?.filter { it.isNotBlank() } ?: PageIds.all
            PagesConfig(ids, prefs[KEY_HOME_PAGE] ?: PageIds.HOME).sane()
        }
        .distinctUntilChanged()

    /** The saved layout for [pageId], or null until [ensureInitialized] has run for it. */
    fun layout(pageId: String): Flow<PageLayout?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[key(pageId)]?.let { decode(it, pageId) } }

    /**
     * Writes the default layout for [pageId] if none is saved, migrating pre-block favorites into
     * it, then applies one-off migrations to saved layouts (tracked by [KEY_VERSION]).
     */
    suspend fun ensureInitialized(pageId: String) {
        val carried = legacyFavorites.readOnce()
        // Resolving the phone's default apps is a handful of package-manager queries; only do it
        // for a fresh install, and only when there are no favourites to carry across already.
        val firstRun = dataStore.data.first()[key(pageId)] == null
        val favorites = when {
            carried.isNotEmpty() -> carried
            firstRun -> defaultFavorites()
            else -> emptyList()
        }
        dataStore.edit { prefs ->
            if (prefs[key(pageId)] == null) {
                prefs[key(pageId)] = json.encodeToString(DefaultLayouts.forPage(pageId, favorites))
            }
            val version = prefs[KEY_VERSION] ?: 1
            if (pageId == PageIds.HOME && version < 2) {
                // v2: the battery block joined the default home layout; add it once to layouts saved before that.
                val home = prefs[key(pageId)]?.let { decode(it, pageId) }
                if (home != null && home.blocks.none { it.type == BatteryBlockDefinition.type }) {
                    prefs[key(pageId)] = json.encodeToString(home.copy(blocks = listOf(BlockRegistry.newBlock(BatteryBlockDefinition.type)) + home.blocks))
                }
                prefs[KEY_VERSION] = 2
            }
            if (pageId == PageIds.LEFT && (prefs[KEY_VERSION] ?: 1) < 3) {
                // v3: the left page got a default layout; fill it in if it is still empty.
                val left = prefs[key(pageId)]?.let { decode(it, pageId) }
                if (left != null && left.blocks.isEmpty()) prefs[key(pageId)] = json.encodeToString(DefaultLayouts.forPage(pageId))
                prefs[KEY_VERSION] = 3
            }
            if (pageId == PageIds.RIGHT && (prefs[KEY_VERSION] ?: 1) < 4) {
                // v4: the right page got steps + screen time; add whichever is missing to a saved one.
                val right = prefs[key(pageId)]?.let { decode(it, pageId) }
                if (right != null) {
                    var blocks = right.blocks
                    if (blocks.none { it.type == StepsBlockDefinition.type }) blocks = listOf(BlockRegistry.newBlock(StepsBlockDefinition.type)) + blocks
                    if (blocks.none { it.type == ScreenTimeBlockDefinition.type }) blocks = blocks + BlockRegistry.newBlock(ScreenTimeBlockDefinition.type)
                    prefs[key(pageId)] = json.encodeToString(right.copy(blocks = blocks))
                }
                prefs[KEY_VERSION] = 4
            }
            // The Apps block's styles are layouts now and its icons are options; a flag per page,
            // because this one applies to all three and the shared version counter does not.
            val splitKey = booleanPreferencesKey("apps_style_split_$pageId")
            if (prefs[splitKey] != true) {
                prefs[key(pageId)]?.let { decode(it, pageId) }?.let { layout ->
                    val blocks = layout.blocks.map {
                        if (it.type == AppsBlockDefinition.type) AppsBlockDefinition.migrateLegacyStyle(it) else it
                    }
                    if (blocks != layout.blocks) prefs[key(pageId)] = json.encodeToString(layout.copy(blocks = blocks))
                }
                prefs[splitKey] = true
            }
        }
    }

    /**
     * Replaces every page at once, so applying a layout never shows a half-swapped home. The
     * pages written are the pages that then exist: a layout of three pages applied on a phone
     * with five leaves three, because a layout says what the pages are.
     */
    suspend fun replaceAll(pages: Map<String, PageLayout>, order: List<String>? = null, homeId: String? = null) {
        dataStore.edit { prefs ->
            val ids = (order ?: pages.keys.toList()).ifEmpty { PageIds.all }
            val config = PagesConfig(ids, homeId ?: PageIds.HOME.takeIf { it in ids } ?: ids[ids.size / 2]).sane()
            // Every page that is going away takes its saved layout with it, so a page id reused
            // later does not come back holding somebody's old blocks.
            existingPageKeys(prefs).forEach { pageId -> if (pageId !in config.ids) prefs.remove(key(pageId)) }
            // Normalised on the way in, so a layout or a backup written before the grid lands as
            // rectangles and nothing downstream ever sees a block without one.
            config.ids.forEach { pageId ->
                val layout = pages[pageId] ?: PageLayout(pageId)
                prefs[key(pageId)] = json.encodeToString(layout.normalized().copy(pageId = pageId))
            }
            writeConfig(prefs, config)
        }
    }

    // ------------------------------------------------------------------ adding and removing pages

    /**
     * A new empty page after [afterPageId], or null when there are already [PagesConfig.MAX] of
     * them. Returns the new page's id so the caller can swipe to it.
     */
    suspend fun addPage(afterPageId: String? = null): String? {
        var created: String? = null
        dataStore.edit { prefs ->
            val config = readConfig(prefs)
            if (!config.canAdd) return@edit
            val id = PageIds.newId()
            val at = config.ids.indexOf(afterPageId).let { if (it < 0) config.ids.size else it + 1 }
            val ids = config.ids.toMutableList().apply { add(at, id) }
            prefs[key(id)] = json.encodeToString(PageLayout(id))
            writeConfig(prefs, config.copy(ids = ids).sane())
            created = id
        }
        return created
    }

    /** Removes a page and everything on it. Refuses to remove the last one. */
    suspend fun removePage(pageId: String) {
        dataStore.edit { prefs ->
            val config = readConfig(prefs)
            if (!config.canRemove || pageId !in config.ids) return@edit
            val ids = config.ids - pageId
            prefs.remove(key(pageId))
            // Losing the home page moves the mark to whatever took its place, so there is always
            // exactly one and the Home key always has somewhere to go.
            val home = if (config.homeId == pageId) ids[config.ids.indexOf(pageId).coerceAtMost(ids.size - 1)] else config.homeId
            writeConfig(prefs, PagesConfig(ids, home).sane())
        }
    }

    /** Moves a page one place left or right in the swipe order. */
    suspend fun movePage(pageId: String, delta: Int) {
        dataStore.edit { prefs ->
            val config = readConfig(prefs)
            val from = config.ids.indexOf(pageId)
            val to = from + delta
            if (from < 0 || to !in config.ids.indices) return@edit
            val ids = config.ids.toMutableList()
            ids.add(to, ids.removeAt(from))
            writeConfig(prefs, config.copy(ids = ids).sane())
        }
    }

    suspend fun setHomePage(pageId: String) {
        dataStore.edit { prefs ->
            val config = readConfig(prefs)
            if (pageId in config.ids) writeConfig(prefs, config.copy(homeId = pageId))
        }
    }

    private fun readConfig(prefs: Preferences): PagesConfig = PagesConfig(
        ids = prefs[KEY_PAGES]?.split(',')?.filter { it.isNotBlank() } ?: PageIds.all,
        homeId = prefs[KEY_HOME_PAGE] ?: PageIds.HOME,
    ).sane()

    private fun writeConfig(prefs: androidx.datastore.preferences.core.MutablePreferences, config: PagesConfig) {
        prefs[KEY_PAGES] = config.ids.joinToString(",")
        prefs[KEY_HOME_PAGE] = config.homeId
    }

    /** Every page id that has a layout saved against it, whether or not it is still in the order. */
    private fun existingPageKeys(prefs: Preferences): List<String> =
        prefs.asMap().keys.map { it.name }.filter { it.startsWith("page_") }.map { it.removePrefix("page_") }

    /** Throws away every saved page and writes the defaults back. Used by "Reset Nulis". */
    suspend fun resetToDefaults() {
        dataStore.edit { prefs ->
            prefs.clear()
            PageIds.all.forEach { prefs[key(it)] = json.encodeToString(DefaultLayouts.forPage(it)) }
            writeConfig(prefs, PagesConfig())
        }
    }

    suspend fun update(pageId: String, transform: (PageLayout) -> PageLayout) {
        dataStore.edit { prefs ->
            val current = prefs[key(pageId)]?.let { decode(it, pageId) } ?: DefaultLayouts.forPage(pageId)
            prefs[key(pageId)] = json.encodeToString(transform(current))
        }
    }

    private fun decode(raw: String, pageId: String): PageLayout = try {
        decodePageLayout(json, raw)
    } catch (e: SerializationException) {
        Log.w(TAG, "Layout for $pageId is unreadable, falling back to default", e)
        DefaultLayouts.forPage(pageId)
    } catch (e: IllegalArgumentException) {
        Log.w(TAG, "Layout for $pageId is invalid, falling back to default", e)
        DefaultLayouts.forPage(pageId)
    }

    private fun key(pageId: String) = stringPreferencesKey("page_$pageId")

    private companion object {
        const val TAG = "LayoutRepository"
        val KEY_VERSION = intPreferencesKey("layout_version")
        val KEY_PAGES = stringPreferencesKey("pages")
        val KEY_HOME_PAGE = stringPreferencesKey("home_page")
    }
}

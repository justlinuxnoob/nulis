// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.icons

import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.util.Xml
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.text.Collator

/** An installed icon pack, as offered in settings. */
@Immutable
data class IconPackInfo(val packageName: String, val label: String)

/**
 * A loaded icon pack: the component -> drawable map from its `appfilter.xml` plus the pack's own
 * resources. Packs only ever cover part of the phone, so a miss falls back to the real icon.
 */
class IconPack(
    val packageName: String,
    private val resources: Resources,
    private val byComponent: Map<String, String>,
    /** Every drawable the pack names, for the per-app "pick a different icon" grid. */
    val drawableNames: List<String>,
) {
    /** The pack's drawable for `pkg/activity`, or null when the pack does not theme that app. */
    fun drawableFor(packageName: String, activityName: String): Drawable? =
        byComponent["ComponentInfo{$packageName/$activityName}"]?.let { drawable(it) }

    fun drawable(name: String): Drawable? = runCatching {
        // An icon pack addresses its art by drawable name and nothing else; this is the
        // contract, not a shortcut.
        @Suppress("DiscouragedApi")
        val id = resources.getIdentifier(name, "drawable", packageName)
        if (id == 0) null else ResourcesCompatGetDrawable(resources, id)
    }.getOrNull()

    @Suppress("FunctionName", "DEPRECATION")
    private fun ResourcesCompatGetDrawable(res: Resources, id: Int): Drawable? =
        res.getDrawable(id, null)
}

/**
 * Finds and reads icon packs in the long-standing ADW / Nova format: an app that answers one of
 * the theme intents and ships an `appfilter.xml` mapping components to drawable names.
 */
object IconPacks {

    private const val TAG = "IconPacks"

    private val SetupActions = listOf(
        "org.adw.launcher.THEMES",
        "com.novalauncher.THEME",
        "com.gau.go.launcherex.theme",
        "com.anddoes.launcher.THEME",
    )

    /** Every installed icon pack, alphabetical. Empty when none is installed. */
    suspend fun installed(pm: PackageManager): List<IconPackInfo> = withContext(Dispatchers.IO) {
        val found = LinkedHashMap<String, IconPackInfo>()
        SetupActions.forEach { action ->
            query(pm, Intent(action)).forEach { name ->
                found.getOrPut(name.packageName) { IconPackInfo(name.packageName, name.label) }
            }
        }
        // Some packs only declare the category on their launcher activity.
        query(pm, Intent(Intent.ACTION_MAIN).addCategory("com.anddoes.launcher.THEME")).forEach {
            found.getOrPut(it.packageName) { it }
        }
        val collator = Collator.getInstance()
        found.values.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun query(pm: PackageManager, intent: Intent): List<IconPackInfo> {
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(intent, 0)
        }
        return resolved.map { IconPackInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
    }

    /** Reads one pack's appfilter. Null when the pack is gone or unreadable. */
    suspend fun load(pm: PackageManager, packageName: String): IconPack? = withContext(Dispatchers.IO) {
        runCatching {
            val resources = pm.getResourcesForApplication(packageName)
            val byComponent = LinkedHashMap<String, String>()
            parse(resources, packageName, "appfilter") { parser ->
                if (parser.name == "item") {
                    val component = parser.getAttributeValue(null, "component")
                    val drawable = parser.getAttributeValue(null, "drawable")
                    if (!component.isNullOrBlank() && !drawable.isNullOrBlank()) byComponent[component] = drawable
                }
            }
            val names = LinkedHashSet<String>()
            // A pack's drawable.xml is the curated grid it wants shown; appfilter is the fallback.
            parse(resources, packageName, "drawable") { parser ->
                if (parser.name == "item") parser.getAttributeValue(null, "drawable")?.let { names += it }
            }
            if (names.isEmpty()) names += byComponent.values
            if (byComponent.isEmpty() && names.isEmpty()) null
            else IconPack(packageName, resources, byComponent, names.toList())
        }.onFailure { Log.w(TAG, "Could not read icon pack $packageName", it) }.getOrNull()
    }

    /** Runs [onTag] for every start tag of the pack's `<name>.xml`, from resources or assets. */
    private inline fun parse(resources: Resources, packageName: String, name: String, onTag: (XmlPullParser) -> Unit) {
        // An icon pack addresses its art by drawable name and nothing else; this is the
        // contract, not a shortcut.
        @Suppress("DiscouragedApi")
        val id = resources.getIdentifier(name, "xml", packageName)
        val parser: XmlPullParser = if (id != 0) {
            resources.getXml(id)
        } else {
            val stream = runCatching { resources.assets.open("$name.xml") }.getOrNull() ?: return
            Xml.newPullParser().apply { setInput(stream.reader()) }
        }
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) onTag(parser)
            event = parser.next()
        }
    }
}

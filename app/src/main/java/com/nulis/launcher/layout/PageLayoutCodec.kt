// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.layout

import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.blocks.PageLayout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Reads a saved page.
 *
 * [PageLayout.align] defaults to [BlockAlign.CENTER], which is what a fresh install and every
 * shipped theme start from. A page saved before alignment existed carries no `align` key at all,
 * and it was drawn left; decoding it against the new default would silently move somebody's whole
 * home screen into the middle. So a payload without the key of its own is pinned to the left,
 * and only a payload that never had the concept is affected - layouts are written with
 * `encodeDefaults = true`, so anything saved since alignment landed says what it wants.
 *
 * Also folds the old `pairNext` flag into [com.nulis.launcher.blocks.BlockWidth], so nothing past
 * this point has to know that shared rows were ever asked for another way.
 */
fun decodePageLayout(json: Json, raw: String): PageLayout {
    val element = json.parseToJsonElement(raw).jsonObject
    val layout = json.decodeFromJsonElement<PageLayout>(element).normalized()
    return if (element.containsKey("align")) layout else layout.copy(align = BlockAlign.LEFT)
}

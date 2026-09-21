// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * A complete colour decision: the background, the ink on it and the accent that goes with both.
 *
 * Palettes exist because the three are not independent in practice - amber text wants a warm
 * near-black behind it and an orange accent beside it, and picking each of the three separately
 * is three chances to get it wrong. Picking a palette is picking all three at once; every one of
 * them is then still editable, because a palette is a starting point and not a lock.
 *
 * Every pair here clears WCAG AA for body text (4.5:1) and every accent clears 3:1 against its
 * own background. `PaletteTest` asserts both, so a palette added in a hurry cannot ship unreadable.
 */
data class Palette(
    val id: String,
    val name: String,
    val background: Color,
    val ink: Color,
    val accent: Color,
) {
    val isDark: Boolean get() = background.luminance() < 0.179f
}

object Palettes {

    val AmberTerminal = Palette("amber", "Amber terminal", Color(0xFF0B0A06), Color(0xFFFFB84D), Color(0xFFFF7A29))
    val EInk = Palette("eink", "E-ink", Color(0xFFF3F1EA), Color(0xFF1A1A18), Color(0xFF9E3B2E))
    val MidnightBlue = Palette("midnight", "Midnight blue", Color(0xFF0A1424), Color(0xFFD5E2F5), Color(0xFF5C9BFF))
    val Forest = Palette("forest", "Forest", Color(0xFF0B1512), Color(0xFFCEE6D7), Color(0xFF63C07E))
    val Rose = Palette("rose", "Rose", Color(0xFF1A0E12), Color(0xFFF4DAE0), Color(0xFFE87590))
    val Sandstone = Palette("sandstone", "Sandstone", Color(0xFFF6EFE2), Color(0xFF33291D), Color(0xFFB35A22))
    val Slate = Palette("slate", "Slate", Color(0xFF13161A), Color(0xFFDCE3EB), Color(0xFF88B4D8))
    val Oat = Palette("oat", "Oat", Color(0xFFF7F4ED), Color(0xFF26231E), Color(0xFF4F7A5C))

    val all: List<Palette> = listOf(AmberTerminal, EInk, MidnightBlue, Forest, Rose, Sandstone, Slate, Oat)

    fun byId(id: String?): Palette? = all.firstOrNull { it.id == id }
}

/**
 * Curated accents, for the picker. Warm to cool, then a near-neutral for somebody who would
 * rather the accent barely register at all.
 */
val AccentSwatches: List<Color> = listOf(
    // No plain red here: the default swatch beside these already is one, and two identical
    // circles that mean different things is a worse picker than nine that all look distinct.
    Color(0xFFFF7A29),
    Color(0xFFFFB000),
    Color(0xFF63C07E),
    Color(0xFF2FB8A8),
    Color(0xFF5C9BFF),
    Color(0xFF9B7BFF),
    Color(0xFFE87590),
    Color(0xFFB08968),
    Color(0xFF9AA3AD),
)

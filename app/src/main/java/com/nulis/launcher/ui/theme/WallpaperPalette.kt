// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * A palette drawn from somebody's wallpaper: the wallpaper's own hue, laid very deep for the
 * background (or very pale, for a wallpaper light enough to want dark text), the same hue lifted
 * to an ink that reads at 4.5:1, and the wallpaper's second colour as the accent.
 *
 * Only the hue is borrowed. A wallpaper's colours are chosen to be looked at, not read on, so
 * the lightness and most of the saturation are Nulis's own - which is what keeps the result a
 * calm page in the wallpaper's colour rather than the wallpaper with text on it.
 */
fun wallpaperPalette(primary: Color, secondary: Color?, light: Boolean): Palette {
    val hsl = primary.toHsl()
    val hue = hsl[0]
    // A grey wallpaper has no hue worth borrowing; its page stays grey rather than turning red.
    val tint = (hsl[1] * 0.5f).coerceAtMost(0.32f)
    val background = if (light) Color.hsl(hue, tint, 0.94f) else Color.hsl(hue, tint, 0.07f)
    val inkBase = if (light) Color.hsl(hue, tint, 0.14f) else Color.hsl(hue, tint * 0.6f, 0.88f)
    val ink = atLeast(inkBase, if (light) Color.Black else Color.White, background, 4.5f)
    val accent = adaptAccent(background, secondary ?: primary)
    return Palette(WALLPAPER_PALETTE_ID, "Your wallpaper", background, ink, accent)
}

const val WALLPAPER_PALETTE_ID = "wallpaper"

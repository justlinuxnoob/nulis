// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import androidx.compose.ui.graphics.Color
import com.nulis.launcher.ui.theme.BlackColors
import com.nulis.launcher.ui.theme.ColorTheme
import com.nulis.launcher.ui.theme.WhiteColors
import com.nulis.launcher.ui.theme.colorsFor
import com.nulis.launcher.ui.theme.contrastRatio
import com.nulis.launcher.ui.theme.wallpaperPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WallpaperPaletteTest {

    private val wallpapers = listOf(
        Color(0xFF2E6FD8), Color(0xFFE8452C), Color(0xFFF5D90A), Color(0xFF3A9A60),
        Color(0xFF808080), Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF9B59B6),
    )

    @Test
    fun whatever_the_wallpaper_the_text_is_readable() {
        for (light in listOf(false, true)) {
            wallpapers.forEach { primary ->
                val palette = wallpaperPalette(primary, wallpapers.last(), light)
                val ink = contrastRatio(palette.ink, palette.background)
                assertTrue("ink on $primary (light=$light) is $ink", ink >= 4.5f)
                val accent = contrastRatio(palette.accent, palette.background)
                assertTrue("accent on $primary (light=$light) is $accent", accent >= 2.6f)
                assertEquals(!light, palette.isDark)
            }
        }
    }

    @Test
    fun auto_follows_the_phone() {
        assertEquals(BlackColors, colorsFor(ColorTheme.AUTO, systemDark = true))
        assertEquals(WhiteColors, colorsFor(ColorTheme.AUTO, systemDark = false))
    }
}

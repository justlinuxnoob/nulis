// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import androidx.compose.ui.graphics.Color
import com.nulis.launcher.ui.theme.AccentSwatches
import com.nulis.launcher.ui.theme.BlackColors
import com.nulis.launcher.ui.theme.ColorTheme
import com.nulis.launcher.ui.theme.Palettes
import com.nulis.launcher.ui.theme.WhiteColors
import com.nulis.launcher.ui.theme.adaptAccent
import com.nulis.launcher.ui.theme.colorsFor
import com.nulis.launcher.ui.theme.contrastRatio
import com.nulis.launcher.ui.theme.customColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A palette that ships unreadable is a bug nobody would find by looking, because the person who
 * picked it was looking at it on the machine it was designed on.
 */
class PaletteTest {

    @Test
    fun `every palette clears WCAG AA for body text`() {
        Palettes.all.forEach { palette ->
            val ratio = contrastRatio(palette.ink, palette.background)
            assertTrue("${palette.name}: ink is $ratio:1 on its background", ratio >= 4.5f)
        }
    }

    @Test
    fun `every palette accent clears the graphic minimum`() {
        Palettes.all.forEach { palette ->
            val ratio = contrastRatio(palette.accent, palette.background)
            assertTrue("${palette.name}: accent is $ratio:1 on its background", ratio >= 3f)
        }
    }

    @Test
    fun `every palette still reads once the full role set is derived`() {
        Palettes.all.forEach { palette ->
            val colors = customColors(palette.background, palette.ink).copy(accent = palette.accent)
            assertTrue(
                "${palette.name}: secondary text",
                contrastRatio(colors.secondary, colors.background) >= 3f,
            )
            assertEquals("${palette.name}: darkness", palette.isDark, colors.isDark)
        }
    }

    @Test
    fun `palette ids and names are unique`() {
        assertEquals(Palettes.all.size, Palettes.all.map { it.id }.distinct().size)
        assertEquals(Palettes.all.size, Palettes.all.map { it.name }.distinct().size)
        assertEquals(8, Palettes.all.size)
    }

    @Test
    fun `the palettes are not all dark, nor all light`() {
        assertTrue(Palettes.all.any { it.isDark })
        assertTrue(Palettes.all.any { !it.isDark })
    }

    @Test
    fun `a chosen accent is used, and pushed until it is visible`() {
        val invisible = Color(0xFF000000)
        val colors = colorsFor(ColorTheme.BLACK, customAccent = invisible)
        assertTrue(contrastRatio(colors.accent, colors.background) >= 3f)
        assertNotEquals(BlackColors.accent, colors.accent)
    }

    @Test
    fun `a chosen accent that already reads is left exactly alone`() {
        val green = Color(0xFF63C07E)
        assertEquals(green, colorsFor(ColorTheme.BLACK, customAccent = green).accent)
    }

    @Test
    fun `destructive stays red however the accent is chosen`() {
        val green = Color(0xFF63C07E)
        val colors = colorsFor(ColorTheme.BLACK, customAccent = green)
        assertEquals(BlackColors.danger, colors.danger)
        assertNotEquals(colors.accent, colors.danger)
        val light = colorsFor(ColorTheme.WHITE, customAccent = green)
        assertEquals(WhiteColors.danger, light.danger)
    }

    @Test
    fun `every curated accent is visible on both pure backgrounds`() {
        AccentSwatches.forEach { swatch ->
            listOf(BlackColors.background, WhiteColors.background).forEach { background ->
                val adapted = adaptAccent(background, swatch)
                assertTrue(
                    "accent $swatch on $background",
                    contrastRatio(adapted, background) >= 3f,
                )
            }
        }
    }
}

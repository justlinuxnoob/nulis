// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import com.nulis.launcher.blocks.clock.ClockScale
import com.nulis.launcher.blocks.clock.ClockSettings
import com.nulis.launcher.blocks.clock.ClockWeight
import com.nulis.launcher.blocks.clock.hourText
import com.nulis.launcher.blocks.clock.meridiem
import com.nulis.launcher.blocks.clock.roman
import com.nulis.launcher.blocks.clock.timeInWords
import com.nulis.launcher.blocks.screentime.formatMinutes
import com.nulis.launcher.gestures.GestureAction
import com.nulis.launcher.gestures.GestureBinding
import com.nulis.launcher.gestures.GestureSettings
import com.nulis.launcher.gestures.GestureTrigger
import com.nulis.launcher.icons.IconColor
import com.nulis.launcher.icons.IconMode
import com.nulis.launcher.icons.IconShape
import com.nulis.launcher.icons.IconSize
import com.nulis.launcher.icons.IconStyle
import com.nulis.launcher.layout.DefaultLayouts
import com.nulis.launcher.ui.theme.BlackColors
import com.nulis.launcher.ui.theme.Fonts
import com.nulis.launcher.ui.theme.Looks
import com.nulis.launcher.ui.theme.WhiteColors
import com.nulis.launcher.ui.theme.adaptAccent
import com.nulis.launcher.ui.theme.contrastRatio
import com.nulis.launcher.ui.theme.customColors
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** The small pure pieces every screen is built from. */
class TokensTest {

    // ------------------------------------------------------------------ stored settings

    @Test
    fun `an icon style round-trips through its stored form`() {
        val style = IconStyle(IconMode.ICON_LABEL, IconShape.SQUIRCLE, IconSize.LARGE, IconColor.ACCENT)
        assertEquals(style, IconStyle.from(style.toMap()))
    }

    @Test
    fun `an icon style with nothing stored falls back to the default it is given`() {
        val fallback = IconStyle(IconMode.MONOGRAM)
        assertEquals(fallback, IconStyle.from(emptyMap(), fallback))
    }

    @Test
    fun `an icon style with rubbish in it falls back rather than failing`() {
        val stored = mapOf("icon_mode" to "wat", "icon_shape" to "??", "icon_size" to "", "icon_color" to "nope")
        assertEquals(IconStyle(), IconStyle.from(stored))
    }

    @Test
    fun `a prefix keeps two icon styles apart in one settings map`() {
        val a = IconStyle(IconMode.TEXT)
        val b = IconStyle(IconMode.ICON)
        val combined = a.toMap("a_") + b.toMap("b_")
        assertEquals(a, IconStyle.from(combined, prefix = "a_"))
        assertEquals(b, IconStyle.from(combined, prefix = "b_"))
    }

    @Test
    fun `clock settings round-trip`() {
        val settings = ClockSettings(hour24 = false, seconds = true, scale = ClockScale.XL, weight = ClockWeight.BOLD, secondZone = "Asia/Tokyo")
        assertEquals(settings, ClockSettings.from(settings.toMap()))
    }

    @Test
    fun `a clock with no weight stored keeps the look's own`() {
        assertNull(ClockSettings.from(ClockSettings().toMap()).weight)
    }

    @Test
    fun `a gesture binding round-trips, with and without an app`() {
        val plain = GestureBinding(GestureAction.OPEN_DRAWER)
        assertEquals(plain, GestureBinding.parse(plain.store()))
        val withApp = GestureBinding(GestureAction.OPEN_APP, "com.example/Main")
        assertEquals(withApp, GestureBinding.parse(withApp.store()))
    }

    @Test
    fun `a gesture binding from a future version reads as nothing rather than crashing`() {
        assertNull(GestureBinding.parse("teleport"))
        assertNull(GestureBinding.parse(null))
        assertNull(GestureBinding.parse(""))
    }

    @Test
    fun `a trigger nobody touched reads its default`() {
        val empty = GestureSettings()
        GestureTrigger.entries.forEach { trigger ->
            assertEquals(trigger.default, empty[trigger])
        }
    }

    // ------------------------------------------------------------------ colours

    @Test
    fun `text on either pure theme clears the WCAG minimum for body text`() {
        assertTrue(contrastRatio(BlackColors.onBackground, BlackColors.background) >= 4.5f)
        assertTrue(contrastRatio(WhiteColors.onBackground, WhiteColors.background) >= 4.5f)
    }

    @Test
    fun `secondary text stays readable on both pure themes`() {
        assertTrue(contrastRatio(BlackColors.secondary, BlackColors.background) >= 3f)
        assertTrue(contrastRatio(WhiteColors.secondary, WhiteColors.background) >= 3f)
    }

    @Test
    fun `a custom background picks the text colour with the better contrast`() {
        assertEquals(Color.White, customColors(Color(0xFF101010)).onBackground)
        assertEquals(Color.Black, customColors(Color(0xFFF4F1EA)).onBackground)
    }

    @Test
    fun `a chosen ink is used as the text colour`() {
        val green = Color(0xFF4BE07A)
        assertEquals(green, customColors(Color(0xFF050A06), green).onBackground)
    }

    @Test
    fun `the accent stays visible on any background`() {
        listOf(0xFF000000, 0xFFFFFFFF, 0xFFC02D5F, 0xFF808080, 0xFFFF3B30).forEach { argb ->
            val background = Color(argb.toInt())
            val accent = adaptAccent(background, BlackColors.accent)
            assertTrue(
                "accent on ${argb.toString(16)} is ${contrastRatio(accent, background)}",
                contrastRatio(accent, background) >= 2.6f,
            )
        }
    }

    @Test
    fun `an accent that already reads well is left exactly alone`() {
        assertEquals(BlackColors.accent, adaptAccent(Color.Black, BlackColors.accent))
    }

    @Test
    fun `every custom palette keeps its hierarchy`() {
        listOf(0xFF14213D, 0xFFF2E8D5, 0xFF1F1F1F, 0xFFEDE3F5).forEach { argb ->
            val colors = customColors(Color(argb.toInt()))
            val text = contrastRatio(colors.onBackground, colors.background)
            val secondary = contrastRatio(colors.secondary, colors.background)
            val tertiary = contrastRatio(colors.tertiary, colors.background)
            val hairline = contrastRatio(colors.hairline, colors.background)
            assertTrue("text is not the strongest", text > secondary)
            assertTrue("secondary is not above tertiary", secondary > tertiary)
            assertTrue("tertiary is not above hairline", tertiary > hairline)
        }
    }

    // ------------------------------------------------------------------ words and numbers

    @Test
    fun `the time in words`() {
        assertEquals("seven o'clock", timeInWords(LocalTime.of(7, 0)))
        assertEquals("six past seven", timeInWords(LocalTime.of(7, 6)))
        assertEquals("quarter past seven", timeInWords(LocalTime.of(7, 15)))
        assertEquals("half past seven", timeInWords(LocalTime.of(7, 30)))
        assertEquals("quarter to eight", timeInWords(LocalTime.of(7, 45)))
        assertEquals("twenty to eight", timeInWords(LocalTime.of(7, 40)))
        assertEquals("twelve o'clock", timeInWords(LocalTime.of(0, 0)))
        assertEquals("five to one", timeInWords(LocalTime.of(12, 55)))
    }

    @Test
    fun `hours in both clocks`() {
        assertEquals("14", hourText(14, hour24 = true))
        assertEquals("2", hourText(14, hour24 = false))
        assertEquals("12", hourText(0, hour24 = false))
        assertEquals("00", hourText(0, hour24 = true))
        assertEquals("12", hourText(12, hour24 = false))
    }

    @Test
    fun `am and pm only exist on a twelve-hour clock`() {
        assertNull(meridiem(9, hour24 = true))
        assertEquals("AM", meridiem(9, hour24 = false))
        assertEquals("PM", meridiem(13, hour24 = false))
        assertEquals("AM", meridiem(0, hour24 = false))
        assertEquals("PM", meridiem(12, hour24 = false))
    }

    @Test
    fun `roman numerals for everything a clock needs`() {
        assertEquals("I", roman(1))
        assertEquals("IV", roman(4))
        assertEquals("IX", roman(9))
        assertEquals("XII", roman(12))
        assertEquals("XL", roman(40))
        assertEquals("LIX", roman(59))
        (1..59).forEach { assertTrue("roman($it) is empty", roman(it).isNotEmpty()) }
    }

    @Test
    fun `minutes read the way a person says them`() {
        assertEquals("0m", formatMinutes(0))
        assertEquals("48m", formatMinutes(48))
        assertEquals("1h 0m", formatMinutes(60))
        assertEquals("2h 14m", formatMinutes(134))
    }

    // ------------------------------------------------------------------ defaults and migration

    @Test
    fun `every default page is made of real blocks`() {
        PageIds.all.forEach { pageId ->
            val layout = DefaultLayouts.forPage(pageId)
            assertEquals(pageId, layout.pageId)
            assertTrue("$pageId is empty", layout.blocks.isNotEmpty())
            layout.blocks.forEach { block ->
                val definition = com.nulis.launcher.blocks.BlockRegistry.definition(block.type)
                assertNotNull("$pageId has an unknown block ${block.type}", definition)
                assertTrue("$pageId: ${block.type} has no style ${block.style}", definition!!.styles.any { it.id == block.style })
            }
        }
    }

    @Test
    fun `the default home carries whatever favourites were migrated in`() {
        val layout = DefaultLayouts.forPage(PageIds.HOME, listOf("a/A", "b/B"))
        val apps = layout.blocks.first { it.type == "apps" }
        assertEquals(listOf("a/A", "b/B"), AppsBlockDefinition.favoriteIds(apps))
    }

    @Test
    fun `the old circles and squares apps styles become the grid layout`() {
        val circles = Block("x", "apps", AppsBlockDefinition.LEGACY_CIRCLES, size = BlockSize.WIDE)
        val migrated = AppsBlockDefinition.migrateLegacyStyle(circles)
        assertEquals("grid", migrated.style)
        val style = AppsBlockDefinition.iconStyle(migrated)
        assertEquals(IconMode.MONOGRAM, style.mode)
        assertEquals(IconShape.CIRCLE, style.shape)
        assertEquals(IconSize.LARGE, style.size)

        val squares = Block("y", "apps", AppsBlockDefinition.LEGACY_SQUARES, size = BlockSize.SMALL)
        val migratedSquares = AppsBlockDefinition.migrateLegacyStyle(squares)
        assertEquals(IconShape.SQUARE, AppsBlockDefinition.iconStyle(migratedSquares).shape)
        assertEquals(IconSize.MEDIUM, AppsBlockDefinition.iconStyle(migratedSquares).size)
    }

    @Test
    fun `a block that is already migrated is left alone`() {
        val grid = Block("z", "apps", "grid")
        assertEquals(grid, AppsBlockDefinition.migrateLegacyStyle(grid))
    }

    @Test
    fun `favourites cannot grow past the maximum`() {
        var block = Block("x", "apps", "grid")
        repeat(AppsBlockDefinition.MAX_FAVORITES + 3) { index ->
            block = AppsBlockDefinition.toggleFavorite(block, "app$index/Main")
        }
        assertEquals(AppsBlockDefinition.MAX_FAVORITES, AppsBlockDefinition.favoriteIds(block).size)
    }

    @Test
    fun `toggling a favourite twice puts it back`() {
        val empty = Block("x", "apps", "grid")
        val once = AppsBlockDefinition.toggleFavorite(empty, "a/A")
        assertEquals(listOf("a/A"), AppsBlockDefinition.favoriteIds(once))
        assertTrue(AppsBlockDefinition.favoriteIds(AppsBlockDefinition.toggleFavorite(once, "a/A")).isEmpty())
    }

    // ------------------------------------------------------------------ the design package

    @Test
    fun `every look names a real font`() {
        Looks.all.forEach { look ->
            assertNotNull("${look.label} has no font", Fonts.byId(look.font.id))
        }
        assertEquals(Looks.all.size, Looks.all.map { it.id }.toSet().size)
    }

    @Test
    fun `an unknown look id falls back to the first one`() {
        assertEquals(Looks.Dot, Looks.byId("atlantis"))
        assertEquals(Looks.Dot, Looks.byId(null))
    }

    @Test
    fun `every bundled font has an id, a label and a licence file`() {
        Fonts.all.forEach { font ->
            assertTrue(font.id.isNotBlank())
            assertTrue(font.label.isNotBlank())
            assertTrue("${font.label} has no note", font.note.isNotBlank())
            assertTrue("${font.label} has no licence", font.licenseAsset.startsWith("licenses/"))
        }
        assertEquals(Fonts.all.size, Fonts.all.map { it.id }.toSet().size)
        assertFalse(Fonts.all.size < 6)
    }
}

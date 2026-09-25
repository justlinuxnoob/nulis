// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import androidx.compose.ui.graphics.Color
import com.nulis.launcher.ui.theme.BlackColors
import com.nulis.launcher.ui.theme.WhiteColors
import com.nulis.launcher.ui.theme.contrastRatio
import com.nulis.launcher.ui.theme.customColors
import com.nulis.launcher.ui.theme.withHigherContrast
import org.junit.Assert.assertTrue
import org.junit.Test

/** Captions are text: they have to be readable, and more readable still when asked. */
class ContrastTest {

    private val backgrounds = listOf(
        Color(0xFF14213D), Color(0xFF0B2E26), Color(0xFF3A0F1E), Color(0xFF808080),
        Color(0xFFF2E8D5), Color(0xFFEDE3F5), Color(0xFF4A90D9), Color(0xFFFFD84D),
    )
    private val every = listOf(BlackColors, WhiteColors) + backgrounds.map { customColors(it) }

    @Test
    fun captions_are_never_below_three_to_one() {
        every.forEach { c ->
            val ratio = contrastRatio(c.tertiary, c.background)
            assertTrue("caption on ${c.background} is $ratio", ratio >= 2.95f)
        }
    }

    @Test
    fun higher_contrast_reaches_body_text_levels_and_keeps_the_hierarchy() {
        every.map { it.withHigherContrast() }.forEach { c ->
            val text = contrastRatio(c.onBackground, c.background)
            val secondary = contrastRatio(c.secondary, c.background)
            val tertiary = contrastRatio(c.tertiary, c.background)
            assertTrue("caption on ${c.background} is $tertiary", tertiary >= 4.45f)
            assertTrue("secondary on ${c.background} is $secondary", secondary >= minOf(6.95f, text))
            assertTrue("secondary is not above caption on ${c.background}", secondary >= tertiary)
        }
    }
}

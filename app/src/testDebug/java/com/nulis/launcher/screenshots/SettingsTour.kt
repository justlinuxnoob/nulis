// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Every settings screen, on a very tall screen so a whole scrolling list fits in one image.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h1800dp-xxhdpi")
class SettingsTour : ScreenshotTest() {

    override val group = "settings"

    @Test
    fun tour() {
        prefs { setOnboarded(true) }
        launch {
            settle(1500)
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top + 50f) }
            settle(1500)
            tap("Nulis Settings")
            shot("00-index")
            listOf("Layout", "Look and colours", "Apps and drawer", "Gestures", "Wellbeing", "Backup", "About").forEachIndexed { i, section ->
                tap(section)
                shot("0${i + 1}-${section.lowercase().replace(' ', '-')}")
                back()
            }
            tap("Layout")
            tap("Layouts")
            shot("10-layouts")
            back()
            tap("Pages")
            shot("11-pages")
            back()
            tap("Saved setups")
            shot("12-setups")
            back()
            back()
            tap("Wellbeing")
            tap("Pauses, limits and focus")
            shot("20-wellbeing")
            back()
            tap("Your week")
            shot("21-week")
            back()
            back()
            tap("Backup")
        }
    }
}

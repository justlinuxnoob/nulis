// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The home page, the editor on it, the block picker and the drawer, past onboarding. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class HomeTour : ScreenshotTest() {

    override val group = "home"

    private fun at(fx: Float, fy: Float, long: Boolean = false) {
        compose.onRoot().performTouchInput {
            val p = Offset(width * fx, height * fy)
            if (long) longClick(p) else click(p)
        }
        settle(1000)
    }

    @Test
    fun tour() {
        prefs { setOnboarded(true) }
        launch {
            settle(2000)
            shot("01-home")
            at(0.5f, 0.2f, long = true)
            shot("02-editor-block")
            at(0.5f, 0.5f)
            shot("03-editor-coach-dismissed")
            at(0.5f, 0.3f)
            shot("04-editor-select")
            tap("Add block")
            settle(1000)
            shot("05-add-block")
            back()
            tap("Done")
            settle(1000)
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top + 50f) }
            settle(1500)
            at(0.3f, 0.3f, long = true)
            shot("10-drawer-app-sheet")
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.nulis.launcher.blocks.clock.NulisClock
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The store screenshots that are best taken from the running launcher: the drawer and the look. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class StoreScreenshotsApp : ScreenshotTest() {

    override val group = "store"

    private fun store(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("../docs/store/screenshots/$name.png")
    }

    @Test
    fun listing() {
        NulisClock.now = { DemoData.now }
        prefs { setOnboarded(true) }
        launch {
            settle(2000)
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top + 50f) }
            settle(1500)
            store("8-drawer")
        }
    }
}

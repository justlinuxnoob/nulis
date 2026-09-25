// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A 10-inch tablet held upright: the home page, the page beside it and the editor. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w800dp-h1280dp-xhdpi")
class TabletTour : ScreenshotTest() {

    override val group = "tablet"

    @Test
    fun tour() {
        prefs { setOnboarded(true) }
        launch {
            settle(2000)
            shot("01-home")
            compose.onRoot().performTouchInput { swipeLeft() }
            settle(1500)
            shot("02-right")
            compose.onRoot().performTouchInput { longClick(Offset(width * 0.5f, height * 0.9f)) }
            settle(1500)
            tapIfShown("Tap to dismiss")
            shot("03-editor")
        }
    }
}

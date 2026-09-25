// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A first-time user's first minute: onboarding from the first screen to the last, then the home
 * page, the pages either side, the editor, the drawer and settings.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class FirstRunTour : ScreenshotTest() {

    override val group = "first-run"

    @Test
    fun tour() = launch {
        shot("01-welcome")
        tap("Set up")
        shot("02-layout")
        tap("Next")
        shot("03-look")
        tap("Next")
        shot("04-apps")
        compose.onAllNodes(hasText("Next", substring = true, ignoreCase = true).or(hasText("Skip", ignoreCase = true)))[0].performClick()
        settle()
        shot("05-default-home")
        compose.onAllNodes(hasText("Skip", ignoreCase = true).or(hasText("Next", ignoreCase = true)))[0].performClick()
        settle()
        shot("06-permissions")
        tap("Done")
        shot("07-all-set")
        tap("Go to my home screen")
        settle(3000)
        shot("10-home-hint-drawer")

        compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.7f, endY = top + 50f) }
        settle(1500)
        shot("11-drawer-hint-hold")
        back()
        settle(1500)
        shot("12-home-hint-edit")

        compose.onRoot().performTouchInput { longClick(center.copy(y = bottom * 0.9f)) }
        settle(1500)
        shot("13-editor")
        tapIfShown("Tap to dismiss")
        tap("Done")
        settle(1500)
        shot("14-home-hint-pages")

        compose.onRoot().performTouchInput { swipeLeft() }
        settle(1500)
        shot("15-right-page")
        compose.onRoot().performTouchInput { swipeRight() }
        settle(1500)
        shot("16-home-no-hints")
    }
}

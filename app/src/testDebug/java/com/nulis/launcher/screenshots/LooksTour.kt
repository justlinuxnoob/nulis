// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import com.nulis.launcher.ui.theme.ColorTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Home, drawer and editor in every Look, on black and on white. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class LooksTour(private val lookId: String, private val theme: ColorTheme) : ScreenshotTest() {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params(): List<Array<Any>> = listOf("dot", "clean").flatMap { look ->
            listOf(ColorTheme.BLACK, ColorTheme.WHITE).map { arrayOf<Any>(look, it) }
        }
    }

    override val group = "looks"

    @Test
    fun tour() {
        prefs {
            setOnboarded(true)
            setLook(lookId)
            setColorTheme(theme)
        }
        val tag = "$lookId-${theme.name.lowercase()}"
        launch {
            settle(2000)
            shot("$tag-1-home")
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top + 50f) }
            settle(1500)
            shot("$tag-2-drawer")
            compose.onRoot().performTouchInput { swipeDown(startY = top + 400f, endY = bottom - 50f) }
            settle(1500)
            compose.onRoot().performTouchInput { longClick(Offset(width * 0.5f, height * 0.9f)) }
            settle(1500)
            tapIfShown("Tap to dismiss")
            shot("$tag-3-editor")
        }
    }
}

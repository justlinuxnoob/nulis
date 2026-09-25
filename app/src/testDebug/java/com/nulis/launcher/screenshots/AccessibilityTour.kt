// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * A small phone at the largest font scale Android offers, and a screen reader's view of every
 * screen on the way: anything that can be tapped has to say what it is.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w320dp-h640dp-xhdpi")
class AccessibilityTour : ScreenshotTest() {

    override val group = "accessibility"

    private val unlabelled = mutableListOf<String>()

    /** Every node a screen reader can activate, and the ones among them that would be silent. */
    private fun audit(screen: String) {
        compose.waitForIdle()
        fun label(node: SemanticsNode): Boolean {
            val c = node.config
            val text = c.getOrNull(SemanticsProperties.Text)?.joinToString("").orEmpty()
            val description = c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString("").orEmpty()
            val state = c.getOrNull(SemanticsProperties.StateDescription).orEmpty()
            val editable = c.getOrNull(SemanticsProperties.EditableText)?.text.orEmpty()
            return (text + description + state + editable).isNotBlank() || node.children.any(::label)
        }
        fun walk(node: SemanticsNode) {
            val c = node.config
            val activatable = SemanticsActions.OnClick in c || SemanticsActions.OnLongClick in c
            // A node with no size is not on screen, and a screen reader never lands on it.
            val onScreen = node.boundsInRoot.width > 0f && node.boundsInRoot.height > 0f
            if (activatable && onScreen && !label(node) && SemanticsProperties.InvisibleToUser !in c) {
                unlabelled += "$screen: ${node.boundsInRoot} ${c.getOrNull(SemanticsProperties.Role) ?: ""}"
            }
            node.children.forEach(::walk)
        }
        walk(compose.onRoot(useUnmergedTree = false).fetchSemanticsNode())
    }

    private fun both(name: String) {
        shot(name)
        audit(name)
    }

    @Test
    fun tour() {
        RuntimeEnvironment.setFontScale(2f)
        launch {
            both("01-welcome")
            tap("Set up")
            both("02-layout")
            tap("Next")
            both("03-look")
            tap("Next")
            both("04-apps")
            tapIfShown("Next (", substring = true)
            both("05-home-role")
            tapIfShown("Next")
            tapIfShown("Skip")
            both("06-permissions")
            tapIfShown("Done")
            both("07-all-set")
            tapIfShown("Go to my home screen")
            settle(2500)
            both("10-home")
            compose.onRoot().performTouchInput { swipeUp(startY = bottom * 0.8f, endY = top + 50f) }
            settle(1500)
            both("11-drawer")
            compose.onNode(androidx.compose.ui.test.hasSetTextAction()).performTextInput("nulis")
            settle()
            tap("Nulis Settings")
            both("12-settings")
            tap("Look and colours")
            both("13-look")
        }
        println("UNLABELLED:\n" + unlabelled.joinToString("\n"))
        assertTrue("Tappable things a screen reader cannot name:\n" + unlabelled.joinToString("\n"), unlabelled.isEmpty())
    }
}

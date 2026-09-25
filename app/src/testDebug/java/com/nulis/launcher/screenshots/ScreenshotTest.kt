// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import android.content.Context
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.nulis.launcher.MainActivity
import kotlinx.coroutines.test.StandardTestDispatcher
import com.nulis.launcher.settings.UiPreferencesRepository
import kotlinx.coroutines.runBlocking
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import org.junit.Rule

/**
 * The base of every screenshot test: a demo phone, the real launcher, and a way to save what is
 * on screen.
 *
 * Images are only written when Roborazzi is asked to record (`./gradlew recordRoborazziPlayDebug`);
 * an ordinary test run still drives every screen, so a crash in one fails the build either way.
 * They land in `app/build/screenshots/<group>/`.
 *
 * Every test in one JVM shares DataStore's in-memory state (its delegates are process-wide), so a
 * tour seeds whatever it depends on with [prefs] rather than assuming a fresh install, and taps a
 * first-time hint with [tapIfShown].
 */
@OptIn(ExperimentalTestApi::class)
abstract class ScreenshotTest {

    // A standard dispatcher, not the unconfined default: the pager's "wait for first layout"
    // resumes its waiters while iterating over them, which an unconfined dispatcher turns into a
    // list modified mid-loop. On a phone the main dispatcher posts, so this never happens there.
    @get:Rule
    val compose: ComposeTestRule = createEmptyComposeRule(effectContext = StandardTestDispatcher())

    protected val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Folder under `build/screenshots` for this test's images. */
    protected abstract val group: String

    protected fun shot(name: String) {
        compose.waitForIdle()
        compose.onRoot().captureRoboImage("build/screenshots/$group/$name.png")
    }

    /** Launches the launcher on the demo phone and runs [block] while it is on screen. */
    protected fun launch(block: ActivityScenario<MainActivity>.() -> Unit) {
        DemoPhone.install(context)
        // The clock only moves when a tour says so: several blocks animate forever (a walking
        // cat, the Game of Life), and a clock that advances by itself never lets Compose be idle.
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            settle()
            scenario.block()
        }
    }

    /** Seeds preferences before launch, so a tour can start past onboarding or in another look. */
    protected fun prefs(block: suspend UiPreferencesRepository.() -> Unit) {
        runBlocking { UiPreferencesRepository(context).block() }
    }

    protected fun ActivityScenario<MainActivity>.back() {
        onActivity { it.onBackPressedDispatcher.onBackPressed() }
        settle()
    }

    protected fun tap(text: String, index: Int = 0) {
        compose.onAllNodes(hasText(text, ignoreCase = true))[index].performClick()
        settle()
    }

    /**
     * Lets [ms] of animation time pass. Real time passes too, a little, because DataStore and the
     * package manager answer on background threads that the virtual clock does not drive.
     */
    /** Taps [text] if it is on screen at all; for hints that only show the first time. */
    protected fun tapIfShown(text: String, substring: Boolean = false) {
        val matcher = hasText(text, substring = substring, ignoreCase = true)
        if (compose.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty()) {
            compose.onAllNodes(matcher)[0].performClick()
            settle()
        }
    }

    protected fun settle(ms: Long = 600) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, 100L)
            compose.mainClock.advanceTimeBy(step)
            compose.waitForIdle()
            Thread.sleep(15)
            left -= step
        }
        compose.waitForIdle()
    }
}

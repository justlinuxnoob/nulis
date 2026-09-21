// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The three things that must work before anything else is worth testing: the home page draws,
 * the drawer opens and can be searched, and a long press gets into edit mode and back out.
 *
 * Driven through UiAutomator rather than the Compose test rule because Nulis is a launcher: the
 * interesting parts are the gestures over the whole window, not a single composable in isolation.
 *
 * Run with `./gradlew connectedDebugAndroidTest` on an unlocked phone. Instrumented tests use the
 * debug build even though perf is what ships: under instrumentation the test APK treats the app as
 * a library and keeps no second copy of anything, so R8 shrinking the app takes `androidx.tracing`
 * and parts of the Kotlin stdlib that androidx.test loads inside the app process with it. Keeping
 * those in the shipping APK to please a test is the wrong trade. These assertions are about
 * behaviour, not timing, so the slower build costs nothing that matters - measure feel with the
 * frame-stats gate on perf instead.
 */
@RunWith(AndroidJUnit4::class)
class SmokeTest {

    private lateinit var device: UiDevice

    private val width get() = device.displayWidth
    private val height get() = device.displayHeight

    @Before
    fun openNulis() {
        device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        device.wakeUp()
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)!!
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        assertTrue("Nulis did not come to the front", device.wait(Until.hasObject(By.pkg(PACKAGE).depth(0)), TIMEOUT))
        device.waitForIdle()
    }

    @Test
    fun home_draws_something() {
        // A home page always has at least one block on it, so there is always something to find.
        val root = device.findObject(By.pkg(PACKAGE).depth(0))
        assertTrue("home has no content", root != null && root.childCount > 0)
    }

    @Test
    fun drawer_opens_and_searches_and_closes() {
        swipeUp()
        val search = device.wait(Until.findObject(By.pkg(PACKAGE).clazz("android.widget.EditText")), TIMEOUT)
        assertTrue("the search field never appeared", search != null)

        search!!.text = "a"
        device.waitForIdle()
        // Whatever is on the phone, typing a letter must leave the drawer in one piece.
        assertTrue("the drawer emptied itself", device.findObject(By.pkg(PACKAGE).depth(0)).childCount > 0)

        device.pressBack()
        device.waitForIdle()
        assertTrue(
            "the drawer did not close",
            device.wait(Until.gone(By.pkg(PACKAGE).clazz("android.widget.EditText")), TIMEOUT),
        )
    }

    @Test
    fun long_press_enters_edit_mode_and_done_leaves_it() {
        longPressEmptySpace()
        val done = device.wait(Until.findObject(By.text("DONE").pkg(PACKAGE)), TIMEOUT)
            ?: device.wait(Until.findObject(By.text("Done").pkg(PACKAGE)), TIMEOUT)
        assertTrue("edit mode never opened", done != null)

        done!!.click()
        device.waitForIdle()
        assertTrue(
            "edit mode never closed",
            device.wait(Until.gone(By.text("DONE").pkg(PACKAGE)), TIMEOUT),
        )
    }

    @Test
    fun paging_left_and_right_comes_back_to_home() {
        val before = device.currentPackageName
        device.swipe(width * 4 / 5, height / 2, width / 5, height / 2, 10)
        device.waitForIdle()
        device.swipe(width / 5, height / 2, width * 4 / 5, height / 2, 10)
        device.waitForIdle()
        assertTrue("paging left the launcher", device.currentPackageName == before)
    }

    private fun swipeUp() {
        device.swipe(width / 2, height * 9 / 10, width / 2, height / 4, 12)
        device.waitForIdle()
    }

    /** Below the blocks and above the navigation bar: the part of a page that has nothing on it. */
    private fun longPressEmptySpace() {
        device.swipe(
            intArrayOf(width / 2, height * 4 / 5).let { arrayOf(android.graphics.Point(it[0], it[1]), android.graphics.Point(it[0], it[1])) },
            80,
        )
        device.waitForIdle()
    }

    private companion object {
        const val PACKAGE = "com.nulis.launcher"
        const val TIMEOUT = 5_000L
    }
}

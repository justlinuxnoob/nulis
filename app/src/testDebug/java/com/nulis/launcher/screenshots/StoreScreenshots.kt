// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.GridRect
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import com.nulis.launcher.gestures.GestureSettings
import com.nulis.launcher.home.AddBlockScreen
import com.nulis.launcher.home.HomeScreen
import com.nulis.launcher.home.PageEditor
import com.nulis.launcher.icons.IconLoader
import com.nulis.launcher.icons.LocalIconLoader
import com.nulis.launcher.layout.LayoutPresets
import com.nulis.launcher.ui.components.rememberSlideState
import com.nulis.launcher.ui.theme.ColorTheme
import com.nulis.launcher.ui.theme.Looks
import com.nulis.launcher.ui.theme.NulisTheme
import com.nulis.launcher.ui.theme.colorsFor
import com.nulis.launcher.widgets.LocalWidgetHost
import com.nulis.launcher.widgets.WidgetHost
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The phone screenshots on the store listing: 1080 x 2400, the default layout, a quarter to ten
 * on an ordinary day, and apps with names and icons that belong to nobody. Recorded straight
 * into docs/store/screenshots by `./gradlew recordRoborazziPlayDebug`.
 *
 * The pages are drawn from the real composables with demo data rather than from the running
 * launcher, because a JVM has no step counter and no usage history, and a store picture of an
 * empty steps block would be honest about the JVM and misleading about the app.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h800dp-xxhdpi")
class StoreScreenshots : ScreenshotTest() {

    override val group = "store"

    private val demo = DemoData.context
    private val preset = LayoutPresets.Minimal

    private fun page(id: String): PageLayout {
        val source = preset.page(id)
        return source.copy(
            blocks = source.blocks.map { block ->
                if (block.type == AppsBlockDefinition.type) AppsBlockDefinition.withFavoriteIds(block, DemoData.homeApps.map(AppInfo::id)) else block
            },
        )
    }

    /** A page of the things people keep: habits and the list, on the demo day. */
    private val habitsPage = PageLayout(
        pageId = "habits",
        blocks = listOf(
            BlockRegistry.newBlock("greeting").copy(style = "plain", rect = GridRect(0, 1, 6, 2)),
            BlockRegistry.newBlock("habits").copy(style = "week", rect = GridRect(0, 3, 6, 4)),
            BlockRegistry.newBlock("tasks").copy(style = "checklist", rect = GridRect(0, 7, 6, 4)),
        ),
    )

    private fun capture(scenario: ActivityScenario<ComponentActivity>, name: String, look: String = "dot", theme: ColorTheme = ColorTheme.BLACK, content: @Composable () -> Unit) {
        scenario.onActivity { activity ->
            val icons = IconLoader(activity)
            val widgets = WidgetHost(activity)
            activity.setContent {
                NulisTheme(look = Looks.byId(look), colors = colorsFor(theme)) {
                    CompositionLocalProvider(LocalIconLoader provides icons, LocalWidgetHost provides widgets) {
                        Box(Modifier.fillMaxSize().background(NulisTheme.colors.background)) { content() }
                    }
                }
            }
        }
        settle(1500)
        compose.onRoot().captureRoboImage("../docs/store/screenshots/$name.png")
    }

    @Composable
    private fun Page(layout: PageLayout) {
        HomeScreen(
            layout = layout,
            context = demo,
            drawer = rememberSlideState(target = false),
            gestures = GestureSettings(),
            onGesture = {},
            onEditBlock = {},
            onEditPage = {},
            onOpenSettings = {},
        )
    }

    @Test
    fun listing() {
        DemoPhone.install(context)
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            capture(scenario, "1-home") { Page(page(PageIds.HOME)) }
            capture(scenario, "2-stats-page") { Page(page(PageIds.RIGHT)) }
            capture(scenario, "3-writing-page") { Page(page(PageIds.LEFT)) }
            capture(scenario, "4-habits") { Page(habitsPage) }
            val home = page(PageIds.HOME)
            capture(scenario, "5-editor") {
                PageEditor(
                    layout = home,
                    pageName = "Home",
                    context = demo,
                    initialSelection = home.blocks.first().id,
                    onWrite = {},
                    onDone = {},
                    onAdd = {},
                    pendingAdd = null,
                    onAddHandled = {},
                    onOpenSettings = {},
                    onPageOptions = {},
                    onBlockOptions = {},
                    onNoRoom = {},
                    coachHint = false,
                    onCoachSeen = {},
                )
            }
            capture(scenario, "6-blocks") { AddBlockScreen(context = demo, onAdd = {}, onClose = {}) }
            capture(scenario, "7-clean-white", look = "clean", theme = ColorTheme.WHITE) { Page(page(PageIds.HOME)) }
        }
    }

}

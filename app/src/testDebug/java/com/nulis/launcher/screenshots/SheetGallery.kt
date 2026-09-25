// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.captureRoboImage
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockActions
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.GridRect
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.ScreenRequest
import com.nulis.launcher.home.BlockOptionsSheet
import com.nulis.launcher.icons.IconLoader
import com.nulis.launcher.icons.LocalIconLoader
import com.nulis.launcher.ui.theme.ColorTheme
import com.nulis.launcher.ui.theme.Looks
import com.nulis.launcher.ui.theme.NulisTheme
import com.nulis.launcher.ui.theme.colorsFor
import com.nulis.launcher.widgets.LocalWidgetHost
import com.nulis.launcher.widgets.WidgetHost
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Every block's options sheet, and every block's full-screen surface, with demo data. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w360dp-h1400dp-xxhdpi")
class SheetGallery(private val lookId: String, private val theme: ColorTheme) : ScreenshotTest() {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params(): List<Array<Any>> = listOf(arrayOf<Any>("dot", ColorTheme.BLACK), arrayOf<Any>("clean", ColorTheme.WHITE))
    }

    override val group = "sheets"

    private object NoActions : BlockActions {
        override fun update(block: Block) = Unit
        override fun updatePage(transform: (PageLayout) -> PageLayout) = Unit
        override fun remove(blockId: String) = Unit
        override fun add(type: String): GridRect? = null
    }

    private fun render(scenario: ActivityScenario<ComponentActivity>, name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        scenario.onActivity { activity ->
            val icons = IconLoader(activity)
            val widgets = WidgetHost(activity)
            activity.setContent {
                NulisTheme(look = Looks.byId(lookId), colors = colorsFor(theme)) {
                    CompositionLocalProvider(LocalIconLoader provides icons, LocalWidgetHost provides widgets) {
                        Box(Modifier.fillMaxSize().background(NulisTheme.colors.background)) { content() }
                    }
                }
            }
        }
        settle(1200)
        compose.onRoot().captureRoboImage("build/screenshots/$group/$name-$lookId-${theme.name.lowercase()}.png")
    }

    private fun blockOf(definition: BlockDefinition) = Block(
        id = "demo",
        type = definition.type,
        style = definition.styles.first().id,
        settings = definition.previewSettings(DemoData.context),
        rect = GridRect(0, 0, definition.defaultSpan.cols, definition.defaultSpan.rows),
    )

    @Test
    fun gallery() {
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            BlockRegistry.definitions.forEach { definition ->
                val block = blockOf(definition)
                val layout = PageLayout(pageId = "home", blocks = listOf(block))
                render(scenario, "options-${definition.type}") {
                    BlockOptionsSheet(block, definition, layout, DemoData.context, NoActions, onDismiss = {})
                }
            }
            listOf("notes", "journal", "tasks", "focus", "screentime", "steps", "calculator", "week").forEach { type ->
                val definition = BlockRegistry.definition(type) ?: return@forEach
                render(scenario, "screen-$type") {
                    definition.Screen(ScreenRequest(type), DemoData.context, onClose = {})
                }
            }
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.screenshots

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import com.github.takahirom.roborazzi.captureRoboImage
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.GridSpan
import com.nulis.launcher.blocks.LocalBlockAlign
import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.home.BlockFrame
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

/**
 * Every style of every block, at its default size and at its minimum, with demo data. One image
 * per block type, per look, per background.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "w400dp-h2400dp-xxhdpi")
class BlockGallery(private val lookId: String, private val theme: ColorTheme) : ScreenshotTest() {

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}-{1}")
        fun params(): List<Array<Any>> = listOf("dot", "clean").flatMap { look ->
            listOf(ColorTheme.BLACK, ColorTheme.WHITE).map { arrayOf<Any>(look, it) }
        }

        /** A cell on a 360 x 800 dp phone: 312 dp across six columns, about 60 dp a row. */
        val CellW = 52.dp
        val CellH = 60.dp
    }

    override val group = "blocks"

    @Test
    fun gallery() {
        compose.mainClock.autoAdvance = false
        val context = DemoData.context
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            BlockRegistry.definitions.forEach { definition ->
                scenario.onActivity { activity ->
                    val icons = IconLoader(activity)
                    val widgets = WidgetHost(activity)
                    activity.setContent {
                        NulisTheme(look = Looks.byId(lookId), colors = colorsFor(theme)) {
                            val colors = NulisTheme.colors
                            CompositionLocalProvider(LocalIconLoader provides icons, LocalWidgetHost provides widgets) {
                                Column(
                                    Modifier.background(colors.background).padding(16.dp).fillMaxWidth().wrapContentHeight(),
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Text("${definition.label} · ${definition.type}", style = NulisTheme.type.labelL, color = colors.secondary)
                                    definition.styles.forEach { style ->
                                        val block = Block(
                                            id = "demo-${style.id}",
                                            type = definition.type,
                                            style = style.id,
                                            settings = definition.previewSettings(context),
                                        )
                                        Text(style.label, style = NulisTheme.type.label, color = colors.tertiary)
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            listOf(definition.defaultSpan, definition.minSpan).distinct().forEach { span: GridSpan ->
                                                val size = DpSize(CellW * span.cols, CellH * span.rows)
                                                Box(Modifier.size(size).border(0.5.dp, colors.hairline)) {
                                                    CompositionLocalProvider(LocalBlockAlign provides BlockAlign.CENTER) {
                                                        BlockFrame(area = size, align = BlockAlign.CENTER) {
                                                            style.Render(block, context, Modifier.fillMaxWidth())
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                settle(800)
                compose.onRoot().captureRoboImage("build/screenshots/$group/${definition.type}-$lookId-${theme.name.lowercase()}.png")
            }
        }
    }
}

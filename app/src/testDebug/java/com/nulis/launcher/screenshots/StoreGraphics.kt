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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import com.nulis.launcher.home.PageMiniature
import com.nulis.launcher.icons.IconLoader
import com.nulis.launcher.icons.LocalIconLoader
import com.nulis.launcher.layout.LayoutPresets
import com.nulis.launcher.ui.theme.ColorTheme
import com.nulis.launcher.ui.theme.Looks
import com.nulis.launcher.ui.theme.NulisTheme
import com.nulis.launcher.ui.theme.colorsFor
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The store's feature graphic (1024 x 500), drawn by the app's own type, colours and pages, so
 * the listing can never drift from the thing it advertises.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StoreGraphics : ScreenshotTest() {

    override val group = "store"

    private fun home(preset: String = "minimal"): PageLayout {
        val source = LayoutPresets.all.first { it.id == preset }.page(PageIds.HOME)
        return source.copy(
            blocks = source.blocks.map { block ->
                if (block.type == AppsBlockDefinition.type) AppsBlockDefinition.withFavoriteIds(block, DemoData.homeApps.map(AppInfo::id)) else block
            },
        )
    }

    private fun draw(path: String, content: @Composable () -> Unit) {
        DemoPhone.install(context)
        compose.mainClock.autoAdvance = false
        ActivityScenario.launch(ComponentActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val icons = IconLoader(activity)
                activity.setContent { CompositionLocalProvider(LocalIconLoader provides icons) { content() } }
            }
            settle(1500)
            compose.onRoot().captureRoboImage(path)
        }
    }

    @Composable
    private fun Phone(look: String, theme: ColorTheme) {
        NulisTheme(look = Looks.byId(look), colors = colorsFor(theme)) {
            val colors = NulisTheme.colors
            Box(
                Modifier
                    .width(190.dp)
                    .height(410.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(colors.background)
                    .border(2.dp, Color(0xFF2A2A2A), RoundedCornerShape(28.dp))
                    .padding(horizontal = 10.dp, vertical = 18.dp),
            ) {
                PageMiniature(home(), DemoData.context, Modifier.fillMaxSize())
            }
        }
    }

    @Test
    @Config(sdk = [36], qualifiers = "w1024dp-h500dp-mdpi")
    fun feature_graphic() = draw("../docs/store/feature-graphic-1024x500.png") {
        NulisTheme(look = Looks.Dot, colors = colorsFor(ColorTheme.BLACK)) {
            Row(
                Modifier.fillMaxSize().background(Color.Black).padding(start = 72.dp, end = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Nulis", style = NulisTheme.type.displayXl.copy(fontSize = 88.sp, lineHeight = 92.sp), color = Color.White)
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "A home screen made of blocks.",
                        style = NulisTheme.type.bodyXl.copy(fontSize = 26.sp, lineHeight = 32.sp),
                        color = Color(0xFFDDDDDD),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        NulisTheme.type.labelCase("Offline forever. No internet permission."),
                        style = NulisTheme.type.labelL.copy(fontSize = 15.sp),
                        color = Color(0xFF9A9A9A),
                    )
                }
                Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Phone("dot", ColorTheme.BLACK)
                    Phone("clean", ColorTheme.WHITE)
                }
            }
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.graphics.Paint
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.view.View
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.GhostPhoto
import com.nulis.launcher.blocks.GridSpan
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.blocks.blockArea
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * One real Android widget, living inside a block's rectangle.
 *
 * Everything else on a page is Nulis drawing something it understands. This is somebody else's
 * view, updating on its own schedule, inside the grid - so the block owns exactly three things:
 * which widget it is, how big its rectangle is, and whether the colour is taken out of it.
 *
 * The id the system gives when a widget is bound is what the block stores. It outlives a reboot
 * and an app update because it is only a number in the page's own settings, and it is handed back
 * to the system the moment the block is deleted or the widget's app is uninstalled - an id nobody
 * gives back is an id nothing reclaims.
 */
object WidgetBlockDefinition : BlockDefinition {
    override val type = "widget"
    override val label = "Widget"

    override val minSpan = GridSpan(2, 2)
    override val defaultSpan = GridSpan(6, 3)
    override val styles: List<BlockStyle> = listOf(PlainStyle)
    override val previewHeight get() = 110.dp

    private const val KEY_ID = "widget_id"
    private const val KEY_GRAY = "widget_gray"

    fun widgetId(block: Block): Int = block.settings[KEY_ID]?.toIntOrNull() ?: WidgetHost.INVALID

    fun withWidgetId(block: Block, id: Int): Block =
        block.copy(settings = block.settings + (KEY_ID to id.toString()))

    private fun gray(block: Block): Boolean = block.settings[KEY_GRAY] == "true"

    override val hasOptions: Boolean get() = true

    @Composable
    override fun Options(block: Block, context: BlockContext, onUpdate: (Block) -> Unit) {
        val host = LocalWidgetHost.current
        val picker = rememberWidgetPicker(block, onUpdate)
        PillButton(
            text = if (widgetId(block) == WidgetHost.INVALID) "Choose a widget" else "Choose another",
            onClick = picker,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))
        com.nulis.launcher.ui.components.ListRow(
            title = "Take the colour out",
            subtitle = "Draws the widget in grey, to match the rest of the page",
            onClick = { onUpdate(block.copy(settings = block.settings + (KEY_GRAY to (!gray(block)).toString()))) },
            trailing = {
                NulisToggle(
                    checked = gray(block),
                    onCheckedChange = { on -> onUpdate(block.copy(settings = block.settings + (KEY_GRAY to on.toString()))) },
                )
            },
        )
        if (host == null) {
            Spacer(Modifier.height(8.dp))
            Caption("Widgets are not available here", lines = 2)
        }
    }

    override fun accessibilityLabel(block: Block, context: BlockContext): String? = null

    /**
     * The picker, the binding and the widget's own setup screen, as one thing to call.
     *
     * Three activities can happen in a row here and any of them can be cancelled, so the id is
     * allocated first and handed back to the system on every path that does not end with a widget
     * on the page. A half-bound id that nothing draws is the one way this block can leak.
     */
    @Composable
    private fun rememberWidgetPicker(block: Block, onUpdate: (Block) -> Unit): () -> Unit {
        val host = LocalWidgetHost.current
        val context = LocalContext.current
        val activity = context as? Activity
        var pending by remember { mutableStateOf(WidgetHost.INVALID) }

        val configure = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = pending
            pending = WidgetHost.INVALID
            if (result.resultCode == Activity.RESULT_OK && id != WidgetHost.INVALID) {
                onUpdate(withWidgetId(block, id))
            } else {
                host?.forget(id)
            }
        }

        val pick = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val id = result.data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, WidgetHost.INVALID)
                ?: pending
            if (result.resultCode != Activity.RESULT_OK || id == WidgetHost.INVALID || host == null) {
                host?.forget(pending)
                pending = WidgetHost.INVALID
                return@rememberLauncherForActivityResult
            }
            // The old id is only released once the new one is in hand, so a cancelled change
            // leaves the widget that was already there exactly where it was.
            val previous = widgetId(block)
            val info = host.providerFor(id)
            val setup = info?.configure
            if (setup != null && activity != null) {
                pending = id
                val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
                    .setComponent(setup)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                if (runCatching { configure.launch(intent) }.isFailure) {
                    // A provider that names a setup screen it does not actually export is not
                    // a reason to lose the widget; it is placed with its defaults.
                    pending = WidgetHost.INVALID
                    onUpdate(withWidgetId(block, id))
                    if (previous != id) host.forget(previous)
                }
            } else {
                pending = WidgetHost.INVALID
                onUpdate(withWidgetId(block, id))
                if (previous != id) host.forget(previous)
            }
        }

        return {
            if (host != null) {
                val id = host.allocateId()
                pending = id
                if (runCatching { pick.launch(host.pickIntent(id)) }.isFailure) {
                    host.forget(id)
                    pending = WidgetHost.INVALID
                }
            }
        }
    }

    /** The widget itself, filling the block. */
    private object PlainStyle : BlockStyle {
        override val id = "plain"
        override val label = "Widget"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val host = LocalWidgetHost.current
            val id = widgetId(block)
            val info = remember(host, id) { host?.providerFor(id) }
            if (host == null || info == null) {
                Empty(block, context, modifier)
                return
            }
            val area = blockArea()
            val grayscale = gray(block)
            // Keyed on the widget id. AndroidView builds its view once and keeps it for the life
            // of the composition node, so choosing a different widget for the same block left
            // the old one on the page: bound, updated by the system, and the wrong widget.
            key(id) {
            AndroidView(
                factory = { ctx ->
                    host.createView(ctx, id, info) ?: View(ctx)
                },
                update = { view ->
                    if (view is android.appwidget.AppWidgetHostView) {
                        host.setSize(view, area.width.value.toInt(), area.height.value.toInt())
                    }
                    // A hardware layer with a saturation-zero filter takes the colour out of the
                    // whole view tree in one step, including whatever the provider draws itself.
                    if (grayscale) {
                        view.setLayerType(View.LAYER_TYPE_HARDWARE, GrayPaint)
                    } else {
                        view.setLayerType(View.LAYER_TYPE_NONE, null)
                    }
                },
                // The block's own rectangle, not fillMaxSize. BlockFrame measures its content
                // with an unbounded height so that it can scale whatever overflows, and a hosted
                // widget asked how tall it would like to be answers with its minimum - which is
                // why a six-by-three analog clock came out as a pill two centimetres tall.
                modifier = modifier.fillMaxWidth().height(area.height),
            )
            }
        }
    }

    /**
     * No widget chosen, or the app that provided one is gone: the shape of a widget, and a tap
     * that opens the picker.
     */
    @Composable
    private fun Empty(block: Block, context: BlockContext, modifier: Modifier) {
        BlockColumn(
            modifier
                .fillMaxSize()
                .pressFeedback()
                .clickable(remember { MutableInteractionSource() }, indication = null) { context.openBlockOptions(block) },
        ) {
            GhostPhoto(Modifier.fillMaxWidth().height(blockArea().height * 0.6f))
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tap to pick a widget",
                style = NulisTheme.type.bodyM,
                color = NulisTheme.colors.secondary,
                textAlign = blockAlign().textAlign,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    /**
     * Lazy, and it has to stay that way. Every block definition is constructed when
     * [com.nulis.launcher.blocks.BlockRegistry] is first touched, and that happens in unit tests
     * too - where `android.graphics.Paint` is the unmocked stub that throws. Building it eagerly
     * took the whole registry down with it and the layout tests with that.
     */
    private val GrayPaint: Paint by lazy {
        Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
    }
}

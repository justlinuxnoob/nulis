// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.screentime

import com.nulis.launcher.blocks.GridSpan

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxWidth as fillWidthFraction
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.appFor
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.CompactBlockWidth
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.blocks.blockArea
import com.nulis.launcher.blocks.ScreenRequest
import com.nulis.launcher.icons.AppGlyph
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Hairline
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.PermissionScreen
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.components.fadeTop
import com.nulis.launcher.ui.theme.NulisShapes
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import com.nulis.launcher.icons.IconShape
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.nulis.launcher.ui.theme.NulisTheme
import com.nulis.launcher.ui.theme.LineStyle
import com.nulis.launcher.blocks.BlockBox
import com.nulis.launcher.blocks.chartHeight
import com.nulis.launcher.blocks.dialSize
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.Canvas

object ScreenTimeBlockDefinition : BlockDefinition {
    override val type = "screentime"
    override val label = "Screen time"

    override val minSpan = GridSpan(1, 1)
    override val defaultSpan = GridSpan(3, 2)
    override val styles: List<BlockStyle> = listOf(TotalStyle, TopAppsStyle, DonutStyle, SplitStyle, GridStyle)

    /** Request arg that opens the explanation screen directly, e.g. from settings. */
    const val PERMISSION = "permission"

    // The donut and the dot grid are drawn shapes and need more than a line of text's height.
    override val previewHeight get() = 112.dp

    override fun accessibilityLabel(block: Block, context: BlockContext): String =
        if (!context.screenTime.granted) {
            "Screen time, waiting for permission"
        } else {
            "Screen time today, ${formatMinutes(context.screenTime.totalMinutes)}"
        }

    @Composable
    override fun Screen(request: ScreenRequest, context: BlockContext, onClose: () -> Unit) {
        if (!context.screenTime.granted || request.arg == PERMISSION) {
            UsageAccessExplanation(onClose)
        } else {
            ScreenTimeScreen(context, onClose)
        }
    }

    /** Today's total in the display face. */
    private object TotalStyle : BlockStyle {
        override val id = "total"
        override val label = "Today"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val state = context.screenTime
            BlockColumn(modifier.open(context)) {
                BlockRow(verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (state.granted) formatMinutes(state.totalMinutes) else "--",
                        style = if (wide) NulisTheme.type.displayL else NulisTheme.type.displayM,
                        color = colors.onBackground,
                    )
                    // Too narrow for both: the reading is the half worth keeping, and a legend
                    // cut to "SCREEN ..." says less than no legend at all.
                    if (blockArea().width >= CompactBlockWidth) {
                        Spacer(Modifier.width(12.dp))
                        Caption("Screen time", modifier = Modifier.padding(bottom = if (wide) 8.dp else 4.dp))
                    }
                }
                if (!state.granted) {
                    Spacer(Modifier.height(4.dp))
                    Text("Tap to allow usage access", style = NulisTheme.type.bodyM, color = colors.secondary, textAlign = blockAlign().textAlign, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    /** The three most used apps today, each with a bar relative to the top one. */
    private object TopAppsStyle : BlockStyle {
        override val id = "top"
        override val label = "Top 3"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val state = context.screenTime
            val top = state.apps.take(3)
            val max = top.firstOrNull()?.minutes?.coerceAtLeast(1) ?: 1
            val align = blockAlign()
            BlockColumn(modifier.open(context)) {
                BlockCaptionRow("Screen time", trailing = if (state.granted) formatMinutes(state.totalMinutes) else null)
                Spacer(Modifier.height(8.dp))
                if (!state.granted) Text("Tap to allow usage access", style = NulisTheme.type.bodyM, color = colors.secondary, textAlign = align.textAlign, modifier = Modifier.fillMaxWidth())
                if (state.granted && top.isEmpty()) Text("Nothing yet today", style = NulisTheme.type.bodyM, color = colors.secondary, textAlign = align.textAlign, modifier = Modifier.fillMaxWidth())
                top.forEach { usage ->
                    Column(Modifier.fillMaxWidth().padding(vertical = if (wide) 6.dp else 4.dp)) {
                        if (align == BlockAlign.LEFT) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                UsageGlyph(usage, context)
                                Text(usage.label, style = if (wide) NulisTheme.type.bodyL else NulisTheme.type.bodyM, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(12.dp))
                                Caption(formatMinutes(usage.minutes))
                            }
                        } else {
                            BlockRow(verticalAlignment = Alignment.CenterVertically) {
                                if (align == BlockAlign.RIGHT) {
                                    Caption(formatMinutes(usage.minutes))
                                    Spacer(Modifier.width(12.dp))
                                }
                                UsageGlyph(usage, context)
                                Text(usage.label, style = if (wide) NulisTheme.type.bodyL else NulisTheme.type.bodyM, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                                if (align != BlockAlign.RIGHT) {
                                    Spacer(Modifier.width(12.dp))
                                    Caption(formatMinutes(usage.minutes))
                                }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        // The bar grows out of the block's own edge, so a right-aligned block reads right to left.
                        Box(
                            Modifier.fillMaxWidth().height(if (wide) 6.dp else 4.dp).background(colors.surfaceRaised, NulisShapes.pill),
                            contentAlignment = align.box,
                        ) {
                            Box(Modifier.fillWidthFraction(usage.minutes / max.toFloat()).height(if (wide) 6.dp else 4.dp).background(colors.onBackground, NulisShapes.pill))
                        }
                    }
                }
            }
        }
    }


    /**
     * The day's top apps as one ring, each app an arc as long as it was used. A pie without the
     * middle: the point is the proportions, not the exact minutes, and the total sits in the
     * hole where it is easiest to read.
     */
    private object DonutStyle : BlockStyle {
        override val id = "donut"
        override val label = "Donut"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val state = context.screenTime
            val wide = block.size == BlockSize.WIDE
            val top = state.apps.take(5)
            val total = state.totalMinutes.coerceAtLeast(1)
            val diameter = dialSize(min = if (wide) 136.dp else 92.dp, reserved = 4.dp)
            // Each slice one step quieter than the last, so the busiest app is also the loudest.
            val shades = listOf(1f, 0.78f, 0.58f, 0.42f, 0.3f)
            BlockBox(modifier.open(context)) {
                Box(Modifier.size(diameter), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(diameter)) {
                        val stroke = size.minDimension * 0.13f
                        val radius = size.minDimension / 2f - stroke / 2f
                        val topLeft = Offset(size.width / 2f - radius, size.height / 2f - radius)
                        val arcSize = Size(radius * 2f, radius * 2f)
                        drawArc(colors.surfaceRaised, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
                        var start = -90f
                        top.forEachIndexed { index, usage ->
                            val sweep = 360f * (usage.minutes / total.toFloat())
                            if (sweep <= 0.5f) return@forEachIndexed
                            drawArc(
                                color = colors.onBackground.copy(alpha = shades.getOrElse(index) { 0.25f }),
                                startAngle = start,
                                // A hairline of background between slices, so they read as separate.
                                sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(stroke),
                            )
                            start += sweep
                        }
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (state.granted) formatMinutes(state.totalMinutes) else "--",
                            style = if (wide) NulisTheme.type.displayM else NulisTheme.type.displayS,
                            color = colors.onBackground,
                        )
                        Caption(if (state.granted) "today" else "no access")
                    }
                }
            }
        }
    }

    /**
     * One bar across the block, split by app, with the names underneath. The same proportions as
     * the donut for a page that wants a line rather than a circle.
     */
    private object SplitStyle : BlockStyle {
        override val id = "split"
        override val label = "Split"

        @OptIn(ExperimentalLayoutApi::class)
        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val state = context.screenTime
            val wide = block.size == BlockSize.WIDE
            val top = state.apps.take(4)
            val total = state.totalMinutes.coerceAtLeast(1)
            val shades = listOf(1f, 0.74f, 0.52f, 0.34f)
            BlockColumn(modifier.fillMaxWidth().open(context)) {
                BlockCaptionRow("Screen time", trailing = if (state.granted) formatMinutes(state.totalMinutes) else null)
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(chartHeight(min = if (wide) 16.dp else 10.dp, reserved = 96.dp))
                        .clip(NulisShapes.pill)
                        .background(colors.surfaceRaised),
                ) {
                    top.forEachIndexed { index, usage ->
                        val share = (usage.minutes / total.toFloat()).coerceAtLeast(0.01f)
                        Box(
                            Modifier
                                .weight(share)
                                .fillMaxSize()
                                .background(colors.onBackground.copy(alpha = shades.getOrElse(index) { 0.3f })),
                        )
                        if (index != top.lastIndex) Box(Modifier.width(2.dp).fillMaxSize().background(colors.background))
                    }
                    // Whatever is left over keeps its share of the bar rather than being rounded away.
                    val used = top.sumOf { it.minutes } / total.toFloat()
                    if (used < 0.99f) Box(Modifier.weight(1f - used).fillMaxSize())
                }
                Spacer(Modifier.height(8.dp))
                if (!state.granted) {
                    Text("Tap to allow usage access", style = NulisTheme.type.bodyM, color = colors.secondary, textAlign = blockAlign().textAlign, modifier = Modifier.fillMaxWidth())
                } else {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = blockAlign().arrangement,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        top.forEachIndexed { index, usage ->
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                                Box(
                                    Modifier
                                        .size(7.dp)
                                        .background(colors.onBackground.copy(alpha = shades.getOrElse(index) { 0.3f }), NulisShapes.pill),
                                )
                                Spacer(Modifier.width(5.dp))
                                Caption(usage.label)
                            }
                        }
                        if (top.isEmpty()) Caption("Nothing yet today")
                    }
                }
            }
        }
    }

    /**
     * A dot for every five minutes on screen today, laid out as a grid that fills through the
     * day. Reading a number tells you how long; watching this fill tells you how it is going.
     */
    private object GridStyle : BlockStyle {
        override val id = "grid"
        override val label = "Grid"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val state = context.screenTime
            val wide = block.size == BlockSize.WIDE
            val square = NulisTheme.look.lineStyle == LineStyle.DOTTED
            val rows = if (wide) 4 else 3
            val columns = 24
            val cells = rows * columns
            val lit = (state.totalMinutes / MINUTES_PER_DOT).coerceIn(0, cells)
            BlockColumn(modifier.fillMaxWidth().open(context)) {
                BlockCaptionRow("Screen time", trailing = if (state.granted) formatMinutes(state.totalMinutes) else "--")
                Spacer(Modifier.height(8.dp))
                Canvas(Modifier.fillMaxWidth().height(chartHeight(min = if (wide) 52.dp else 36.dp, reserved = 44.dp))) {
                    val stepX = size.width / columns
                    val stepY = size.height / rows
                    val dot = minOf(stepX, stepY) * 0.5f
                    repeat(cells) { index ->
                        val centre = Offset(
                            stepX * (index % columns) + stepX / 2f,
                            stepY * (index / columns) + stepY / 2f,
                        )
                        val color = if (index < lit) colors.onBackground else colors.hairline
                        if (square) {
                            drawRect(color, Offset(centre.x - dot / 2f, centre.y - dot / 2f), Size(dot, dot))
                        } else {
                            drawCircle(color, dot / 2f, centre)
                        }
                    }
                }
                if (!state.granted) {
                    Spacer(Modifier.height(6.dp))
                    Text("Tap to allow usage access", style = NulisTheme.type.bodyM, color = colors.secondary, textAlign = blockAlign().textAlign, modifier = Modifier.fillMaxWidth())
                } else {
                    Spacer(Modifier.height(4.dp))
                    Caption("one dot is $MINUTES_PER_DOT minutes")
                }
            }
        }
    }

    /** Five minutes: long enough to be a real sitting, short enough that the grid moves. */
    private const val MINUTES_PER_DOT = 5

    @Composable
    private fun Modifier.open(context: BlockContext): Modifier = this
        .pressFeedback()
        .clickable(remember { MutableInteractionSource() }, indication = null) { context.openScreen(ScreenRequest(type)) }
}

/**
 * The mark beside a usage row, in whatever style app lists are set to.
 *
 * One treatment for every row, which is the point. The grouped Other row and any app the phone
 * cannot produce an icon for used to leave a blank gap the width of an icon, so a list of three
 * rows could show two icons and a hole and read as three different designs. They get a plain
 * outlined square of exactly the icon's size and shape instead.
 */
@Composable
private fun UsageGlyph(usage: AppUsage, context: BlockContext) {
    val style = context.appIcons
    if (!style.mode.hasGlyph) return
    val app = if (usage.isOther) null else context.appFor(usage.packageName)
    if (app == null) {
        val colors = NulisTheme.colors
        Box(
            Modifier
                .size(style.size.dp)
                .border(1.dp, colors.hairline, if (style.shape == IconShape.CIRCLE) CircleShape else NulisShapes.tile),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(style.size.dp / 6).background(colors.tertiary, NulisShapes.pill))
        }
    } else {
        AppGlyph(app, style)
    }
    Spacer(Modifier.width(12.dp))
}

@Composable
private fun UsageAccessExplanation(onClose: () -> Unit) {
    val activityContext = LocalContext.current
    PermissionScreen(
        title = "Screen time",
        explanation = "To show how long you use each app, Nulis needs usage access. Android asks you to turn it on in system settings.",
        points = listOf(
            "Nulis reads today's foreground time per app, nothing else.",
            "Everything stays on this phone. Nulis has no internet permission.",
            "Turn it off any time in the same settings screen; the blocks just go blank.",
        ),
        onAllow = { openUsageAccessSettings(activityContext) },
        onNotNow = onClose,
        allowLabel = "Open settings",
    )
}

@Composable
private fun ScreenTimeScreen(context: BlockContext, onClose: () -> Unit) {
    val colors = NulisTheme.colors
    val state = context.screenTime
    val max = state.apps.firstOrNull()?.minutes?.coerceAtLeast(1) ?: 1
    NulisScreen(label = "Screen time today", title = formatMinutes(state.totalMinutes), onBack = onClose) {
        // The Other row is the one that raises a question, so it is the one that answers it.
        var otherExplained by rememberSaveable { mutableStateOf(false) }
        LazyColumn(Modifier.fillMaxSize().fadeTop()) {
            items(state.apps, key = { it.packageName }) { usage ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (!usage.isOther) {
                                Modifier
                            } else {
                                Modifier
                                    .pressFeedback()
                                    .clickable(remember { MutableInteractionSource() }, indication = null) {
                                        otherExplained = !otherExplained
                                    }
                            },
                        )
                        .padding(vertical = 10.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        UsageGlyph(usage, context)
                        Text(usage.label, style = NulisTheme.type.bodyL, color = colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(12.dp))
                        Caption(formatMinutes(usage.minutes))
                    }
                    if (usage.isOther) {
                        Spacer(Modifier.height(4.dp))
                        Caption(
                            if (otherExplained) AppUsage.OTHER_EXPLANATION else "Tap to see what this is",
                            lines = 3,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(4.dp).background(colors.surfaceRaised, NulisShapes.pill)) {
                        Box(Modifier.fillWidthFraction(usage.minutes / max.toFloat()).height(4.dp).background(colors.onBackground, NulisShapes.pill))
                    }
                }
                Hairline()
            }
        }
    }
}

/** The system screen where usage access is granted. Shared with onboarding. */
fun openUsageAccessSettings(context: android.content.Context) {
    runCatching {
        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

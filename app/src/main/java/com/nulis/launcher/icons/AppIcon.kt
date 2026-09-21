// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.icons

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.ui.theme.LineStyle
import com.nulis.launcher.ui.theme.NulisMotion
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import com.nulis.launcher.notifications.LocalNotificationDots
import com.nulis.launcher.ui.theme.NulisTheme
import java.util.Locale

/** The one [IconLoader]; created next to the repositories and provided by the activity. */
val LocalIconLoader = staticCompositionLocalOf<IconLoader> { error("No IconLoader provided") }

/**
 * The square that stands for an app: a letter or its real icon, cut to the chosen shape. An icon
 * that does not reach its own edges - a logo on nothing, or a tinted silhouette - sits on the
 * launcher's own plate instead of floating, so a wall of mismatched icons lines up.
 *
 * Draws nothing at all in [IconMode.TEXT].
 */
@Composable
fun AppGlyph(app: AppInfo, style: IconStyle, modifier: Modifier = Modifier) {
    if (!style.mode.hasGlyph) return
    val colors = NulisTheme.colors
    val loader = LocalIconLoader.current
    val density = LocalDensity.current
    val px = remember(style.size, density) {
        with(density) { style.size.dp.roundToPx() }.coerceAtMost(MaxIconPx)
    }

    // A pixel icon is drawn as dots in the Dot look and as squares everywhere else, the same
    // decision that look makes about hairlines and the editor's grid.
    val pixel = style.pixel(dots = NulisTheme.look.lineStyle == LineStyle.DOTTED)

    val icon = if (style.mode.hasIcon) {
        // Read so a new pack or a changed override drops what this row is holding.
        val generation = loader.generation
        var loaded by remember(app.id, app.versionTag, style.color, style.shape, px, pixel, generation) {
            mutableStateOf(loader.cached(app, style.color, style.shape, px, pixel))
        }
        LaunchedEffect(app.id, app.versionTag, style.color, style.shape, px, pixel, generation) {
            if (loaded == null) loaded = loader.load(app, style.color, style.shape, px, pixel)
        }
        loaded
    } else {
        null
    }

    // A letter, an icon still loading, or art that does not reach its own edges gets a plate.
    val plated = style.shape != IconShape.NONE && (!style.mode.hasIcon || icon == null || icon.fit != IconFit.FILL)
    val tint = when (style.color) {
        IconColor.TEXT -> colors.onBackground
        IconColor.ACCENT -> colors.accent
        IconColor.PIXEL -> when (style.pixelInk) {
            PixelInk.MONO -> colors.onBackground
            PixelInk.ACCENT -> colors.accent
            // Colour and Gray both keep their own values in the bitmap.
            PixelInk.POSTER, PixelInk.GRAY -> null
        }
        else -> null
    }
    // The plate and its hairline are white stamps of the shape, tinted here: one texture draw
    // each instead of a path fill and a path stroke on every frame of a fling.
    val strokePx = with(density) { 1.dp.toPx() }
    val plate = if (plated) loader.stamp(style.shape, px, 0f) else null
    val hairline = if (plated) loader.stamp(style.shape, px, strokePx) else null

    Box(modifier.size(style.size.dp), contentAlignment = Alignment.Center) {
        if (plate != null) Image(plate, null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(colors.surface))
        if (hairline != null) Image(hairline, null, Modifier.fillMaxSize(), colorFilter = ColorFilter.tint(colors.hairline))
        // Created with the icon's current presence as its start value, so an icon that was
        // already in memory is drawn solid on the first frame and only a real load fades in.
        val fade by animateFloatAsState(if (icon != null) 1f else 0f, tween(NulisMotion.quick), label = "iconFade")
        if (style.mode.hasIcon) {
            icon?.let {
                Image(
                    bitmap = it.image,
                    contentDescription = null,
                    colorFilter = tint?.let { color -> ColorFilter.tint(color) },
                    modifier = Modifier
                        .fillMaxSize(if (plated) it.fit.fraction else 1f)
                        .graphicsLayer { alpha = fade },
                )
            }
        } else {
            Text(
                text = app.label.trimStart().firstOrNull()?.uppercase(Locale.getDefault()) ?: "?",
                style = NulisTheme.type.displayS.copy(
                    fontSize = (style.size.dp.value * 0.40f).sp,
                    lineHeight = (style.size.dp.value * 0.46f).sp,
                ),
                color = colors.onBackground,
            )
        }
        // Something is waiting in this app. A dot, and only a dot: no number, no name, nothing
        // about what it is. Drawn in the ink inside a ring of the background so it reads on a
        // white icon, a black one and a photograph alike - never in the accent, which in this
        // launcher means "selected" or "this deletes something".
        if (app.packageName in LocalNotificationDots.current) {
            val dot = style.size.dp * 0.20f
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = dot / 3, y = -dot / 3)
                    .size(dot)
                    .background(colors.background, CircleShape)
                    .padding(1.5.dp)
                    .background(colors.onBackground, CircleShape),
            )
        }
    }
}

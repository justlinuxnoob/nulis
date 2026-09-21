// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.icons

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import android.graphics.Path
import androidx.compose.ui.graphics.Path as ComposePath
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlin.math.pow
import kotlin.math.sign

/** How an app is represented in a list: by its name, a letter, its icon, or an icon and a name. */
enum class IconMode(val id: String, val label: String) {
    TEXT("text", "Text"),
    MONOGRAM("monogram", "Monogram"),
    ICON("icon", "Icon"),
    ICON_LABEL("icon_label", "Icon + name");

    /** True when a square glyph (a letter tile or a real icon) is drawn for the app. */
    val hasGlyph: Boolean get() = this != TEXT

    /** True when the real icon is drawn rather than a letter. */
    val hasIcon: Boolean get() = this == ICON || this == ICON_LABEL

    /** True when the app's name is written out. Lists that need names ignore this. */
    val hasLabel: Boolean get() = this == TEXT || this == ICON_LABEL

    companion object {
        fun byId(id: String?): IconMode? = entries.firstOrNull { it.id == id }
    }
}

/** The outline every glyph is cut to, so a wall of mismatched icons reads as one set. */
enum class IconShape(val id: String, val label: String) {
    CIRCLE("circle", "Circle"),
    SQUARE("square", "Rounded"),
    SQUIRCLE("squircle", "Squircle"),
    NONE("none", "Raw");

    /**
     * The same outline as [shape], as a path to draw into a bitmap. Cutting icons to shape in
     * the cache instead of clipping every icon on every frame is the difference between a free
     * drawer and one that spends five milliseconds of GPU time per frame on path clips.
     * Null for [NONE], which is never cut.
     */
    fun path(px: Int): Path? {
        val size = px.toFloat()
        return when (this) {
            NONE -> null
            CIRCLE -> Path().apply { addOval(0f, 0f, size, size, Path.Direction.CW) }
            SQUARE -> Path().apply {
                val radius = size * 0.28f
                addRoundRect(0f, 0f, size, size, radius, radius, Path.Direction.CW)
            }
            SQUIRCLE -> squirclePath(size)
        }
    }

    companion object {
        fun byId(id: String?): IconShape? = entries.firstOrNull { it.id == id }
    }
}

enum class IconSize(val id: String, val label: String, val dp: Dp) {
    SMALL("small", "Small", 40.dp),
    MEDIUM("medium", "Medium", 52.dp),
    LARGE("large", "Large", 64.dp);

    companion object {
        fun byId(id: String?): IconSize? = entries.firstOrNull { it.id == id }
    }
}

/**
 * What is done to a real icon's colours. [TEXT] and [ACCENT] are true monochrome: the app's
 * Android 13+ themed layer when it has one, and a silhouette lifted out of the plain icon when
 * it does not, so a row never mixes flat symbols with muddy squares.
 */
enum class IconColor(val id: String, val label: String) {
    // Short on purpose: all five sit in one row of previews that must never scroll, because a
    // colour you cannot see the end of is a colour nobody finds.
    ORIGINAL("original", "Original"),
    GRAYSCALE("gray", "Gray"),
    TEXT("text", "Text"),
    ACCENT("accent", "Accent"),
    PIXEL("pixel", "Pixel");

    /** True when the cached bitmap is a white silhouette tinted at draw time. */
    val isMask: Boolean get() = this == TEXT || this == ACCENT

    companion object {
        fun byId(id: String?): IconColor? = entries.firstOrNull { it.id == id }
    }
}

/**
 * How coarse a [IconColor.PIXEL] icon is. Twelve cells is a symbol, twenty-four is a small
 * picture; sixteen is the one where most app icons still read as themselves.
 */
enum class PixelGrid(val id: String, val label: String, val cells: Int) {
    COARSE("12", "12", 12),
    MEDIUM("16", "16", 16),
    FINE("24", "24", 24);

    companion object {
        fun byId(id: String?): PixelGrid? = entries.firstOrNull { it.id == id }
    }
}

/** What a [IconColor.PIXEL] icon is drawn in. */
enum class PixelInk(val id: String, val label: String) {
    /** The app's own colours, flattened to four levels a channel. */
    POSTER("poster", "Colour"),

    /** The same art with the colour taken out, flattened to a handful of greys. */
    GRAY("gray", "Gray"),

    /** One bit, ordered-dithered, tinted to the text colour. */
    MONO("mono", "1-bit"),

    /** The same one bit, tinted to the accent. */
    ACCENT("accent", "Accent");

    /**
     * True when the cached bitmap is a white stencil coloured at draw time.
     *
     * Gray is not one: a stencil has one value per cell and grey needs several, so it keeps the
     * real art and takes the colour out of it rather than lifting a silhouette.
     */
    val isMask: Boolean get() = this == MONO || this == ACCENT

    companion object {
        fun byId(id: String?): PixelInk? = entries.firstOrNull { it.id == id }
    }
}

/**
 * Everything the loader needs to draw a pixel icon, and everything that has to be part of its
 * cache key. [dots] comes from the Look rather than from the style: the Dot look draws every
 * cell as a dot, which is the same decision it makes about hairlines and the editor's grid.
 */
@Immutable
data class PixelSpec(val grid: PixelGrid, val ink: PixelInk, val dots: Boolean) {
    val key: String get() = "${grid.id}${ink.id}${if (dots) "d" else "s"}"
}

/** One complete icon treatment: what is drawn, in what outline, how big and in what colours. */
@Immutable
data class IconStyle(
    val mode: IconMode = IconMode.TEXT,
    val shape: IconShape = IconShape.SQUIRCLE,
    val size: IconSize = IconSize.MEDIUM,
    val color: IconColor = IconColor.ORIGINAL,
    /** Only read when [color] is [IconColor.PIXEL]; kept either way so switching back remembers. */
    val pixelGrid: PixelGrid = PixelGrid.MEDIUM,
    val pixelInk: PixelInk = PixelInk.POSTER,
) {
    /** Stored form for a block's settings map or the preferences store. */
    fun toMap(prefix: String = ""): Map<String, String> = mapOf(
        "${prefix}icon_mode" to mode.id,
        "${prefix}icon_shape" to shape.id,
        "${prefix}icon_size" to size.id,
        "${prefix}icon_color" to color.id,
        "${prefix}icon_pixel_grid" to pixelGrid.id,
        "${prefix}icon_pixel_ink" to pixelInk.id,
    )

    /** What the loader needs, or null when this style is not a pixel one. */
    fun pixel(dots: Boolean): PixelSpec? =
        if (color == IconColor.PIXEL) PixelSpec(pixelGrid, pixelInk, dots) else null

    companion object {
        fun from(settings: Map<String, String>, default: IconStyle = IconStyle(), prefix: String = ""): IconStyle = IconStyle(
            mode = IconMode.byId(settings["${prefix}icon_mode"]) ?: default.mode,
            shape = IconShape.byId(settings["${prefix}icon_shape"]) ?: default.shape,
            size = IconSize.byId(settings["${prefix}icon_size"]) ?: default.size,
            color = IconColor.byId(settings["${prefix}icon_color"]) ?: default.color,
            pixelGrid = PixelGrid.byId(settings["${prefix}icon_pixel_grid"]) ?: default.pixelGrid,
            pixelInk = PixelInk.byId(settings["${prefix}icon_pixel_ink"]) ?: default.pixelInk,
        )
    }
}

/**
 * A superellipse, |x|^4 + |y|^4 = 1: rounder than a rounded square in the corners and flatter
 * along the edges, which is what makes a grid of icons sit still.
 */
object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = ComposePath()
        forEachPoint(size.width, size.height) { i, x, y -> if (i == 0) path.moveTo(x, y) else path.lineTo(x, y) }
        path.close()
        return Outline.Generic(path)
    }
}

/** The same superellipse as [SquircleShape], as a path that can be drawn into a bitmap. */
private fun squirclePath(size: Float): Path = Path().apply {
    forEachPoint(size, size) { i, x, y -> if (i == 0) moveTo(x, y) else lineTo(x, y) }
    close()
}

private const val SquircleExponent = 4.0
private const val SquircleSteps = 64

private inline fun forEachPoint(width: Float, height: Float, point: (Int, Float, Float) -> Unit) {
    val rx = width / 2f
    val ry = height / 2f
    for (i in 0..SquircleSteps) {
        val t = (i.toFloat() / SquircleSteps) * 2f * Math.PI.toFloat()
        val c = kotlin.math.cos(t)
        val s = kotlin.math.sin(t)
        // Parametric superellipse; the sign keeps each quadrant on its own side.
        val x = rx + rx * c.absoluteValue.toDouble().pow(2.0 / SquircleExponent).toFloat() * c.sign
        val y = ry + ry * s.absoluteValue.toDouble().pow(2.0 / SquircleExponent).toFloat() * s.sign
        point(i, x, y)
    }
}

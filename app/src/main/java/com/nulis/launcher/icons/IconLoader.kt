// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.icons

import androidx.core.graphics.get
import androidx.core.graphics.createBitmap
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.LruCache
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nulis.launcher.apps.AppInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * How much of its tile a processed icon should take. This is what keeps a mixed set calm: art
 * that reaches its own edges is cut straight to the shape, while a logo on nothing or a lifted
 * silhouette sits on the launcher's own plate at a size that matches everything around it.
 */
enum class IconFit(val fraction: Float) {
    /** Adaptive icons and solid legacy tiles: they fill the shape. */
    FILL(1f),

    /** A logo on nothing, or a silhouette lifted from a flat icon. */
    INSET(0.64f),

    /** A themed or foreground layer, which already carries the adaptive icon's own padding. */
    WIDE(0.90f),
}

/** A processed icon, ready to draw. */
@Stable
class LoadedIcon(val bitmap: Bitmap, val fit: IconFit) {
    /** Wrapped once: [asImageBitmap] allocates, and these are drawn every frame of a fling. */
    val image: ImageBitmap by lazy(LazyThreadSafetyMode.NONE) { bitmap.asImageBitmap() }
}

/**
 * Loads app icons off the main thread and keeps them in memory and on disk.
 *
 * Everything expensive - asking the package manager for a drawable, rasterising it, desaturating
 * it, lifting a silhouette out of it - happens on a background dispatcher. The result is cached
 * in memory (an LRU of a few MB) and on disk under a key made of the app's version, the icon
 * pack, any per-app override and the treatment, so a reinstalled or updated app gets a fresh
 * icon and nothing else is ever processed twice.
 *
 * Colours that are a flat tint are cached as white silhouettes and coloured with a draw-time
 * filter, so switching theme or accent costs nothing.
 */
@Stable
class IconLoader(context: Context) {

    private val appContext = context.applicationContext
    private val packageManager = appContext.packageManager
    private val cacheDir = File(appContext.cacheDir, "app_icons")

    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(4)

    private val memory = object : LruCache<String, LoadedIcon>(memoryBudgetKb()) {
        override fun sizeOf(key: String, value: LoadedIcon): Int = value.bitmap.allocationByteCount / 1024
    }

    /** Bumped whenever the pack or the overrides change, so composables drop what they hold. */
    var generation by mutableIntStateOf(0)
        private set

    /** The active pack, for the "pick a different icon" grid. Null when none is chosen. */
    var pack by mutableStateOf<IconPack?>(null)
        private set

    private var packPackage: String? = null
    private var overrides: Map<String, String> = emptyMap()
    private var pruned = false
    private val stamps = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

    /**
     * Points the loader at the chosen pack and the per-app icon overrides. Reading the pack is
     * slow (it parses an XML with thousands of entries), so it happens on [scope].
     */
    fun configure(packPackage: String?, overrides: Map<String, String>, scope: CoroutineScope) {
        val packChanged = packPackage != this.packPackage
        val overridesChanged = overrides != this.overrides
        if (!packChanged && !overridesChanged) return
        this.overrides = overrides
        if (packChanged) {
            this.packPackage = packPackage
            memory.evictAll()
            pack = null
            if (packPackage == null) {
                generation++
            } else {
                scope.launch {
                    val loaded = IconPacks.load(packageManager, packPackage)
                    if (packPackage == this@IconLoader.packPackage) {
                        pack = loaded
                        generation++
                    }
                }
            }
        }
        if (overridesChanged) generation++
        if (!pruned) {
            pruned = true
            scope.launch(dispatcher) { prune() }
        }
    }

    /** One of the active pack's own drawables, for the "choose icon" grid. */
    fun cachedPackDrawable(name: String, px: Int): LoadedIcon? = memory.get(packKey(name, px))

    suspend fun loadPackDrawable(name: String, px: Int): LoadedIcon? = withContext(dispatcher) {
        val key = packKey(name, px)
        memory.get(key)?.let { return@withContext it }
        readDisk(key)?.let {
            memory.put(key, it)
            return@withContext it
        }
        val drawable = pack?.drawable(name) ?: return@withContext null
        val icon = runCatching { render(drawable, IconColor.ORIGINAL, IconShape.NONE, px, null) }.getOrNull() ?: return@withContext null
        memory.put(key, icon)
        runCatching { writeDisk(key, icon) }
        icon
    }

    private fun packKey(name: String, px: Int): String = "$PIPELINE_VERSION|pack|${packPackage ?: "-"}|$name|$px"

    /** Every icon pack installed on the phone, for the picker in settings. */
    suspend fun installedPacks(): List<IconPackInfo> = IconPacks.installed(packageManager)

    /** The icon if it is already in memory. Cheap enough to call while composing a list row. */
    fun cached(app: AppInfo, color: IconColor, shape: IconShape, px: Int, pixel: PixelSpec? = null): LoadedIcon? =
        memory.get(key(app, color, shape, px, pixel))

    /** Loads, processes and caches the icon. Never touches the main thread. */
    suspend fun load(app: AppInfo, color: IconColor, shape: IconShape, px: Int, pixel: PixelSpec? = null): LoadedIcon? = withContext(dispatcher) {
        val key = key(app, color, shape, px, pixel)
        memory.get(key)?.let { return@withContext it }
        readDisk(key)?.let {
            memory.put(key, it)
            return@withContext it
        }
        val drawable = runCatching { resolveDrawable(app) }.getOrNull() ?: return@withContext null
        val icon = runCatching { render(drawable, color, shape, px, pixel) }.getOrNull() ?: return@withContext null
        memory.put(key, icon)
        runCatching { writeDisk(key, icon) }
        icon
    }

    /**
     * Processes every app's icon in the background so a fling finds them all in memory. Cheap
     * after the first run: the disk cache turns each one into a file read.
     */
    suspend fun prewarm(apps: List<AppInfo>, color: IconColor, shape: IconShape, px: Int, pixel: PixelSpec? = null) {
        // The loop itself runs off the main thread too, so a drawer fling never waits on it.
        withContext(Dispatchers.Default) {
            apps.forEach { app ->
                if (!isActive) return@withContext
                runCatching { load(app, color, shape, px, pixel) }
            }
        }
    }

    // --- keys and caches ---

    private fun key(app: AppInfo, color: IconColor, shape: IconShape, px: Int, pixel: PixelSpec?): String = buildString {
        append(PIPELINE_VERSION); append('|')
        append(app.id); append('|')
        append(app.versionTag); append('|')
        append(color.name); append('|')
        append(shape.id); append('|')
        append(px); append('|')
        append(packPackage ?: "-"); append('|')
        append(overrides[app.id] ?: "-"); append('|')
        append(pixel?.key ?: "-")
    }

    /** The fit is part of the file name, so the three candidates are tried in turn. */
    private fun readDisk(key: String): LoadedIcon? {
        val hash = hash(key)
        for (fit in IconFit.entries) {
            val file = File(cacheDir, "$hash-${fit.ordinal}.png")
            if (!file.exists()) continue
            val bitmap = runCatching { android.graphics.BitmapFactory.decodeFile(file.path) }.getOrNull() ?: continue
            file.setLastModified(System.currentTimeMillis())
            return LoadedIcon(bitmap, fit)
        }
        return null
    }

    private fun writeDisk(key: String, icon: LoadedIcon) {
        if (!cacheDir.exists() && !cacheDir.mkdirs()) return
        val file = File(cacheDir, "${hash(key)}-${icon.fit.ordinal}.png")
        file.outputStream().use { icon.bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Drops cached bitmaps nothing has asked for in a month, e.g. for uninstalled apps. */
    private fun prune() {
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        cacheDir.listFiles()?.forEach { if (it.lastModified() < cutoff) it.delete() }
    }

    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

    // --- drawables ---

    private fun resolveDrawable(app: AppInfo): Drawable? {
        overrides[app.id]?.let { name -> pack?.drawable(name)?.let { return it } }
        if (!app.isNulisSettings) {
            pack?.drawableFor(app.packageName, app.activityName)?.let { return it }
            runCatching { packageManager.getActivityIcon(ComponentName(app.packageName, app.activityName)) }
                .getOrNull()?.let { return it }
        }
        return runCatching { packageManager.getApplicationIcon(app.packageName) }.getOrNull()
    }

    // --- rendering ---

    private fun render(drawable: Drawable, color: IconColor, shape: IconShape, px: Int, pixel: PixelSpec?): LoadedIcon {
        val icon = when {
            color == IconColor.PIXEL && pixel != null -> return pixelate(drawable, px, pixel, shape)
            color.isMask -> mask(drawable, px)
            color == IconColor.GRAYSCALE -> {
                val (bitmap, fit) = rasterize(drawable, px)
                LoadedIcon(filtered(bitmap, grayscaleFilter()), fit)
            }
            else -> rasterize(drawable, px).let { (bitmap, fit) -> LoadedIcon(bitmap, fit) }
        }
        // Art that fills its tile is cut to the shape here, once, instead of being clipped to a
        // path on every frame. Anything that sits inset on a plate never reaches an edge, so
        // there is nothing to cut.
        if (icon.fit != IconFit.FILL) return icon
        return cut(icon.bitmap, shape)?.let { LoadedIcon(it, IconFit.FILL) } ?: icon
    }

    /**
     * The Pixel treatment: the icon read at a dozen-odd samples across and drawn back as that
     * many flat cells.
     *
     * Downsampling a logo straight from 160 px to 16 is how you get sixteen shades of the same
     * grey, so the art is pushed apart first - saturation and contrast up, which is what
     * "simplify" means at this size - and then every cell is the *average* of the pixels under
     * it rather than a sample of one of them. A twelfth of an icon is one clock hand or one
     * letter stroke, and the average is what keeps it.
     *
     * Colour is flattened to four levels a channel, which is a sixty-four colour palette that
     * still tells Spotify from Slack. Gray is the same art with the colour taken out and
     * flattened to [GRAY_LEVELS] greys, which is enough for a logo to keep its light and shade
     * on a page that has no colour anywhere else. The one-bit inks instead take the silhouette
     * the tinted treatments already lift out of an icon, and dither its coverage against a
     * 4 x 4 ordered matrix, so a soft edge becomes a checker rather than a hard step. Every ink
     * is drawn as squares, or as dots when the Look asks for dots.
     */
    private fun pixelate(drawable: Drawable, px: Int, spec: PixelSpec, shape: IconShape): LoadedIcon {
        val n = spec.grid.cells
        val out = blank(px)
        val canvas = Canvas(out)
        val paint = Paint(if (spec.dots) Paint.ANTI_ALIAS_FLAG else 0)

        // The same lift the flat tints use for the one-bit inks, so a picture-in-a-plate icon
        // still becomes a symbol rather than a solid block of dither; the real art otherwise.
        val (raw, fit) = if (spec.ink.isMask) {
            mask(drawable, px).let { it.bitmap to it.fit }
        } else {
            rasterize(drawable, px)
        }
        // A wordmark is four or five times wider than it is tall. Grown until its long side
        // nearly fills the tile - which is right for a round logo on nothing - it ends up two
        // rows of a sixteen-row grid: a sliver floating in the dark that reads as a rendering
        // fault rather than as an icon. Art that lopsided goes on the launcher's own plate,
        // exactly like every other icon that does not reach its own edges, and the plate is what
        // gives it a square to be an icon in.
        // Asked of the art itself rather than of the fit: an adaptive icon whose foreground is a
        // wordmark reports FILL and is still a sliver. Asked at [SOLID_ALPHA] rather than at the
        // faintest trace of a pixel, because an icon with a five-per-cent wash across its whole
        // tile is a wordmark with a wash behind it, not a full-bleed square.
        val onPlate = opaqueBounds(raw, SOLID_ALPHA)?.let(::lopsided) == true
        // On a plate the art is grown against its solid bounds first and then drawn back at the
        // plate's own inset, which is how every other icon in the launcher is sized.
        val source = if (onPlate) fillTile(raw, IconFit.INSET, SOLID_ALPHA) else fillTile(raw, fit)
        // Dots are cut to the chosen outline by leaving whole cells out rather than by clipping
        // the finished art: a squircle sliced through a row of dots leaves half-dots, which read
        // as a mistake. Squares are cut afterwards, where an anti-aliased edge is the right one.
        // Nothing is cut on a plate, because nothing on one reaches an edge.
        val keep = if (spec.dots && !onPlate) shapeCells(shape, px, n) else null

        if (spec.ink.isMask) {
            val coverage = boxDownsampleAlpha(source, n)
            for (row in 0 until n) {
                for (col in 0 until n) {
                    if (keep != null && !keep[row * n + col]) continue
                    val value = coverage[row * n + col]
                    if (value <= BAYER[(row and 3) * 4 + (col and 3)]) continue
                    paint.color = Color.WHITE
                    drawCell(canvas, paint, px, n, col, row, spec.dots)
                }
            }
        } else {
            val gray = spec.ink == PixelInk.GRAY
            val cells = boxDownsample(filtered(source, if (gray) pixelGrayFilter() else pixelBoostFilter()), n)
            for (row in 0 until n) {
                for (col in 0 until n) {
                    if (keep != null && !keep[row * n + col]) continue
                    val cell = cells[row * n + col]
                    if (((cell ushr 24) and 0xFF) < 128) continue
                    paint.color = if (gray) posterizeGray(cell) else posterize(cell)
                    drawCell(canvas, paint, px, n, col, row, spec.dots)
                }
            }
        }
        // WIDE rather than INSET: the art has already been grown to the tile, so it wants most
        // of the plate rather than the deep padding a small logo gets.
        if (onPlate) return LoadedIcon(out, IconFit.WIDE)
        val cut = if (spec.dots) null else cut(out, shape)
        return LoadedIcon(cut ?: out, IconFit.FILL)
    }

    /** True when a bounding box is far enough from square that filling a tile would flatten it. */
    private fun lopsided(bounds: IntArray): Boolean {
        val w = bounds[2] - bounds[0]
        val h = bounds[3] - bounds[1]
        if (w <= 0 || h <= 0) return false
        return maxOf(w, h).toFloat() / minOf(w, h) >= WORDMARK_ASPECT
    }

    /**
     * Art that does not reach its own edges, grown until it nearly does.
     *
     * A wordmark or a logo on nothing occupies a third of its tile, and a third of a sixteen-cell
     * grid is five cells - a sliver next to a wall of full icons. Everywhere else in the launcher
     * that art gets a plate to sit on instead; a pixel icon has no plate, so it gets the size.
     * Aspect is kept, so a wide wordmark stays wide.
     */
    private fun fillTile(source: Bitmap, fit: IconFit, minAlpha: Int = FAINT_ALPHA): Bitmap {
        if (fit == IconFit.FILL) return source
        val bounds = opaqueBounds(source, minAlpha) ?: return source
        val (left, top, right, bottom) = bounds
        val w = right - left
        val h = bottom - top
        if (w <= 0 || h <= 0) return source
        val px = source.width
        val scale = (px * PIXEL_INSET) / maxOf(w, h)
        if (scale <= 1.02f) return source
        val out = blank(px)
        val matrix = android.graphics.Matrix().apply {
            setTranslate(-left.toFloat(), -top.toFloat())
            postScale(scale, scale)
            postTranslate((px - w * scale) / 2f, (px - h * scale) / 2f)
        }
        Canvas(out).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    /** The smallest rectangle holding everything at least [minAlpha] opaque. */
    private fun opaqueBounds(bitmap: Bitmap, minAlpha: Int = FAINT_ALPHA): IntArray? {
        val px = bitmap.width
        val pixels = IntArray(px * px)
        bitmap.getPixels(pixels, 0, px, 0, 0, px, px)
        var left = px; var top = px; var right = 0; var bottom = 0
        for (y in 0 until px) {
            for (x in 0 until px) {
                if (((pixels[y * px + x] ushr 24) and 0xFF) < minAlpha) continue
                if (x < left) left = x
                if (x + 1 > right) right = x + 1
                if (y < top) top = y
                if (y + 1 > bottom) bottom = y + 1
            }
        }
        return if (right <= left || bottom <= top) null else intArrayOf(left, top, right, bottom)
    }

    private operator fun IntArray.component4(): Int = this[3]

    /** Which cells of an n x n grid have their centre inside [shape]. Null when every one does. */
    private fun shapeCells(shape: IconShape, px: Int, n: Int): BooleanArray? {
        val path = shape.path(px) ?: return null
        val region = android.graphics.Region()
        region.setPath(path, android.graphics.Region(0, 0, px, px))
        val half = px / (2 * n)
        return BooleanArray(n * n) { index ->
            val col = index % n
            val row = index / n
            region.contains(col * px / n + half, row * px / n + half)
        }
    }

    /** One cell, on exact integer boundaries so a wall of squares has no seams in it. */
    private fun drawCell(canvas: Canvas, paint: Paint, px: Int, n: Int, col: Int, row: Int, dots: Boolean) {
        val x0 = col * px / n
        val x1 = (col + 1) * px / n
        val y0 = row * px / n
        val y1 = (row + 1) * px / n
        if (dots) {
            val radius = minOf(x1 - x0, y1 - y0) * 0.42f
            canvas.drawCircle((x0 + x1) / 2f, (y0 + y1) / 2f, radius, paint)
        } else {
            canvas.drawRect(x0.toFloat(), y0.toFloat(), x1.toFloat(), y1.toFloat(), paint)
        }
    }

    /** Every cell the straight average of the pixels under it, alpha weighted. */
    private fun boxDownsample(source: Bitmap, n: Int): IntArray {
        val px = source.width
        val pixels = IntArray(px * px)
        source.getPixels(pixels, 0, px, 0, 0, px, px)
        val out = IntArray(n * n)
        for (row in 0 until n) {
            val y0 = row * px / n
            val y1 = ((row + 1) * px / n).coerceAtLeast(y0 + 1)
            for (col in 0 until n) {
                val x0 = col * px / n
                val x1 = ((col + 1) * px / n).coerceAtLeast(x0 + 1)
                var a = 0L; var r = 0L; var g = 0L; var b = 0L; var count = 0
                for (y in y0 until y1) {
                    for (x in x0 until x1) {
                        val p = pixels[y * px + x]
                        val alpha = (p ushr 24) and 0xFF
                        a += alpha
                        // Weighted by alpha, so a transparent corner does not wash the cell out.
                        r += (((p shr 16) and 0xFF) * alpha).toLong()
                        g += (((p shr 8) and 0xFF) * alpha).toLong()
                        b += ((p and 0xFF) * alpha).toLong()
                        count++
                    }
                }
                out[row * n + col] = if (a == 0L) {
                    0
                } else {
                    (((a / count).toInt() and 0xFF) shl 24) or
                        (((r / a).toInt() and 0xFF) shl 16) or
                        (((g / a).toInt() and 0xFF) shl 8) or
                        ((b / a).toInt() and 0xFF)
                }
            }
        }
        return out
    }

    /** The same average, alpha only, as a 0..1 coverage per cell. */
    private fun boxDownsampleAlpha(source: Bitmap, n: Int): FloatArray {
        val cells = boxDownsample(source, n)
        return FloatArray(n * n) { (((cells[it] ushr 24) and 0xFF)) / 255f }
    }

    /** Four levels a channel: a sixty-four colour palette, and a flat one. */
    private fun posterize(pixel: Int): Int {
        fun level(v: Int): Int = (Math.round(v * (POSTER_LEVELS - 1f) / 255f) * 255f / (POSTER_LEVELS - 1f)).toInt().coerceIn(0, 255)
        return lifted(level((pixel shr 16) and 0xFF), level((pixel shr 8) and 0xFF), level(pixel and 0xFF))
    }

    /**
     * A cell that is there is drawn as something you can see.
     *
     * Half the app icons on a phone are art on a black or near-black tile, and a black cell drawn
     * on a black page is not drawn at all: a wordmark on black came out as a row of light letters
     * floating in the dark with no icon around them, which is what a "sliver" looks like from the
     * outside. Lifting the darkest cells to [PIXEL_FLOOR] - as a whole, so the hue survives -
     * gives that tile a body again: a grid of dark dots with the logo bright inside it.
     */
    private fun lifted(r: Int, g: Int, b: Int): Int {
        val peak = maxOf(r, g, b)
        val lift = if (peak < PIXEL_FLOOR) PIXEL_FLOOR - peak else 0
        return (0xFF shl 24) or
            ((r + lift).coerceIn(0, 255) shl 16) or
            ((g + lift).coerceIn(0, 255) shl 8) or
            (b + lift).coerceIn(0, 255)
    }

    /** Luminance to one of [GRAY_LEVELS] greys. The alpha is kept: a cell is there or it is not. */
    private fun posterizeGray(pixel: Int): Int {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val luma = (r * 0.299f + g * 0.587f + b * 0.114f)
        val step = Math.round(luma * (GRAY_LEVELS - 1f) / 255f)
        val value = (step * 255f / (GRAY_LEVELS - 1f)).toInt().coerceIn(0, 255)
        return lifted(value, value, value)
    }

    /** Colour out, contrast up: the same push the colour ink gets, without the saturation. */
    private fun pixelGrayFilter(): ColorMatrix {
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        val s = PIXEL_CONTRAST
        val t = 127.5f * (1f - s)
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    s, 0f, 0f, 0f, t,
                    0f, s, 0f, 0f, t,
                    0f, 0f, s, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        return matrix
    }

    /** Saturation and contrast up before the averaging, so a small grid keeps its differences. */
    private fun pixelBoostFilter(): ColorMatrix {
        val matrix = ColorMatrix().apply { setSaturation(PIXEL_SATURATION) }
        val s = PIXEL_CONTRAST
        val t = 127.5f * (1f - s)
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    s, 0f, 0f, 0f, t,
                    0f, s, 0f, 0f, t,
                    0f, 0f, s, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        return matrix
    }

    /** Anti-aliased shape cut: the outline is drawn first, then the art is masked into it. */
    private fun cut(source: Bitmap, shape: IconShape): Bitmap? {
        val path = shape.path(source.width) ?: return null
        val out = blank(source.width)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        canvas.drawPath(path, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(source, 0f, 0f, paint)
        return out
    }

    /**
     * A white [shape] the size of a tile, filled or stroked, ready to be tinted at draw time.
     * One per shape and size for the whole launcher, so plates and their hairlines cost a texture
     * draw rather than a path.
     */
    fun stamp(shape: IconShape, px: Int, strokePx: Float): ImageBitmap? {
        if (shape == IconShape.NONE || px <= 0) return null
        val key = "${shape.id}|$px|$strokePx"
        stamps[key]?.let { return it }
        val path = shape.path(px) ?: return null
        val bitmap = blank(px)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if (strokePx > 0f) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokePx
            // Half the stroke would fall outside the tile; pull the outline in by that much.
            val inset = strokePx / 2f
            val matrix = android.graphics.Matrix().apply {
                setScale((px - strokePx) / px, (px - strokePx) / px)
                postTranslate(inset, inset)
            }
            path.transform(matrix)
        }
        Canvas(bitmap).drawPath(path, paint)
        val image = bitmap.asImageBitmap()
        stamps[key] = image
        return image
    }

    /**
     * Draws the icon into a square bitmap. Adaptive icons are drawn layer by layer rather than
     * through the drawable itself, so the system's own mask never shows through ours.
     */
    private fun rasterize(drawable: Drawable, px: Int): Pair<Bitmap, IconFit> {
        val bitmap = blank(px)
        val canvas = Canvas(bitmap)
        if (drawable is AdaptiveIconDrawable) {
            drawLayers(canvas, px, listOfNotNull(drawable.background, drawable.foreground))
            return bitmap to IconFit.FILL
        }
        drawable.setBounds(0, 0, px, px)
        drawable.draw(canvas)
        return bitmap to if (isFullBleed(bitmap)) IconFit.FILL else IconFit.INSET
    }

    /**
     * Adaptive layers are authored on a 108dp canvas of which the middle 72dp is visible, so the
     * layer is drawn 1.5x the output size and centred.
     */
    private fun drawLayers(canvas: Canvas, px: Int, layers: List<Drawable>) {
        val extent = (px * ADAPTIVE_SCALE).roundToInt()
        val offset = (px - extent) / 2
        layers.forEach {
            it.setBounds(offset, offset, offset + extent, offset + extent)
            it.draw(canvas)
        }
    }

    /**
     * A true monochrome silhouette, white with the shape in its alpha. The themed layer when the
     * app ships one; the adaptive foreground when that is a logo on nothing; and otherwise the
     * logo lifted out of a plain icon by grayscale contrast against its own background, so a flat
     * tinted row never mixes clean symbols with solid muddy squares.
     */
    private fun mask(drawable: Drawable, px: Int): LoadedIcon {
        if (drawable is AdaptiveIconDrawable) {
            val monochrome = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) drawable.monochrome else null
            monochrome?.let { layer ->
                val bitmap = blank(px)
                drawLayers(Canvas(bitmap), px, listOf(layer.mutate().apply { setTint(Color.WHITE) }))
                symbolFrom(bitmap)?.let { return LoadedIcon(it, IconFit.WIDE) }
            }
            drawable.foreground?.let { layer ->
                val bitmap = blank(px)
                drawLayers(Canvas(bitmap), px, listOf(layer))
                symbolFrom(bitmap)?.let { return LoadedIcon(it, IconFit.WIDE) }
            }
        }
        return LoadedIcon(silhouette(rasterize(drawable, px).first), IconFit.INSET)
    }

    /**
     * Reads one layer as a symbol. A layer that reaches its own edges, or that is almost solid,
     * is a plate with the symbol punched out of it - a very common way to draw an adaptive icon,
     * and the one thing that would put a single solid block in a row of clean symbols. Those are
     * turned inside out, which is exactly the symbol the app meant to show. Returns null when
     * there is no symbol in there either way, so the caller can lift one out of the flat icon.
     */
    private fun symbolFrom(layer: Bitmap): Bitmap? {
        val plate = isFullBleed(layer) || coverage(layer) > KNOCKOUT_COVERAGE
        val symbol = if (plate) invertAlpha(layer) else whiten(layer)
        return symbol.takeIf { coverage(it) in 0.015f..0.86f }
    }

    /**
     * Lifts the logo out of a plain icon: grayscale it, take its own background colour from the
     * border, and keep only what stands far enough away from that colour.
     */
    private fun silhouette(source: Bitmap): Bitmap {
        val px = source.width
        val pixels = IntArray(px * px)
        source.getPixels(pixels, 0, px, 0, 0, px, px)
        val paper = borderColor(pixels, px)
        val paperLuminance = luminance(paper)
        var covered = 0
        val out = IntArray(pixels.size)
        for (i in pixels.indices) {
            val alpha = (pixels[i] ushr 24) and 0xFF
            if (alpha == 0) continue
            val distance = maxOf(
                abs(luminance(pixels[i]) - paperLuminance),
                CHROMA_WEIGHT * rgbDistance(pixels[i], paper),
            )
            val keep = smoothstep(LIFT_LOW, LIFT_HIGH, distance)
            val outAlpha = (alpha * keep).roundToInt().coerceIn(0, 255)
            if (outAlpha > 128) covered++
            out[i] = (outAlpha shl 24) or 0x00FFFFFF
        }
        // Nothing stood out (a single flat colour): fall back to the icon's own outline.
        if (covered.toFloat() / pixels.size < 0.03f) return whiten(source)
        return Bitmap.createBitmap(out, px, px, Bitmap.Config.ARGB_8888)
    }

    private fun blank(px: Int): Bitmap = createBitmap(px, px)

    private fun filtered(source: Bitmap, matrix: ColorMatrix): Bitmap {
        val out = blank(source.width)
        Canvas(out).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
        return out
    }

    /** No saturation, then a small contrast lift so a desaturated icon does not read as sludge. */
    private fun grayscaleFilter(): ColorMatrix {
        val matrix = ColorMatrix().apply { setSaturation(0f) }
        val s = GRAY_CONTRAST
        val t = 127.5f * (1f - s)
        matrix.postConcat(
            ColorMatrix(
                floatArrayOf(
                    s, 0f, 0f, 0f, t,
                    0f, s, 0f, 0f, t,
                    0f, 0f, s, 0f, t,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        return matrix
    }

    /** Turns a knocked-out plate inside out, so what was punched through becomes the symbol. */
    private fun invertAlpha(source: Bitmap): Bitmap {
        val px = source.width
        val pixels = IntArray(px * px)
        source.getPixels(pixels, 0, px, 0, 0, px, px)
        for (i in pixels.indices) {
            val alpha = 255 - ((pixels[i] ushr 24) and 0xFF)
            pixels[i] = (alpha shl 24) or 0x00FFFFFF
        }
        return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
    }

    /** Keeps the alpha channel and throws the colours away, ready to be tinted at draw time. */
    private fun whiten(source: Bitmap): Bitmap {
        val px = source.width
        val pixels = IntArray(px * px)
        source.getPixels(pixels, 0, px, 0, 0, px, px)
        for (i in pixels.indices) pixels[i] = (pixels[i] and 0xFF000000.toInt()) or 0x00FFFFFF
        return Bitmap.createBitmap(pixels, px, px, Bitmap.Config.ARGB_8888)
    }

    /** Fraction of the bitmap that is more than half opaque. */
    private fun coverage(bitmap: Bitmap): Float {
        val px = bitmap.width
        val pixels = IntArray(px * px)
        bitmap.getPixels(pixels, 0, px, 0, 0, px, px)
        var covered = 0
        for (pixel in pixels) if (((pixel ushr 24) and 0xFF) > 128) covered++
        return covered.toFloat() / pixels.size
    }

    /** True when the artwork reaches its own edges, so cutting it to a shape looks deliberate. */
    private fun isFullBleed(bitmap: Bitmap): Boolean {
        val px = bitmap.width
        if (px < 4) return false
        var opaque = 0
        var total = 0
        for (i in 0 until BORDER_SAMPLES) {
            val p = (((i + 0.5f) / BORDER_SAMPLES) * (px - 1)).toInt()
            intArrayOf(
                bitmap[p, 1],
                bitmap[p, px - 2],
                bitmap[1, p],
                bitmap[px - 2, p],
            ).forEach {
                total++
                if (((it ushr 24) and 0xFF) > 220) opaque++
            }
        }
        return opaque.toFloat() / total > 0.85f
    }

    /** The icon's own background: the median of its border ring, channel by channel. */
    private fun borderColor(pixels: IntArray, px: Int): Int {
        val reds = ArrayList<Int>(px * 4)
        val greens = ArrayList<Int>(px * 4)
        val blues = ArrayList<Int>(px * 4)
        fun add(x: Int, y: Int) {
            val pixel = pixels[y * px + x]
            if (((pixel ushr 24) and 0xFF) < 128) return
            reds += (pixel shr 16) and 0xFF
            greens += (pixel shr 8) and 0xFF
            blues += pixel and 0xFF
        }
        for (i in 0 until px) {
            add(i, 0); add(i, px - 1); add(0, i); add(px - 1, i)
        }
        if (reds.isEmpty()) return 0
        fun median(values: ArrayList<Int>): Int = values.sorted()[values.size / 2]
        return (0xFF shl 24) or (median(reds) shl 16) or (median(greens) shl 8) or median(blues)
    }

    private fun luminance(pixel: Int): Float {
        val r = ((pixel shr 16) and 0xFF) / 255f
        val g = ((pixel shr 8) and 0xFF) / 255f
        val b = (pixel and 0xFF) / 255f
        return 0.299f * r + 0.587f * g + 0.114f * b
    }

    /** Euclidean RGB distance, normalised to 0..1. */
    private fun rgbDistance(a: Int, b: Int): Float {
        val dr = (((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)) / 255f
        val dg = (((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)) / 255f
        val db = ((a and 0xFF) - (b and 0xFF)) / 255f
        return sqrt(dr * dr + dg * dg + db * db) / 1.7320508f
    }

    private fun smoothstep(low: Float, high: Float, value: Float): Float {
        val t = ((value - low) / (high - low)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun memoryBudgetKb(): Int =
        ((Runtime.getRuntime().maxMemory() / 1024L) / 8L).toInt().coerceIn(4 * 1024, 24 * 1024)

    private companion object {
        /** Bumped whenever the processing changes, so old bitmaps on disk are never reused. */
        const val PIPELINE_VERSION = 12

        /** 108dp of layer for 72dp of visible icon. */
        const val ADAPTIVE_SCALE = 1.5f
        const val GRAY_CONTRAST = 1.18f
        const val CHROMA_WEIGHT = 0.55f
        const val LIFT_LOW = 0.10f
        const val LIFT_HIGH = 0.34f
        const val BORDER_SAMPLES = 16
        /** Above this, a themed layer is a filled plate rather than a symbol. */
        const val KNOCKOUT_COVERAGE = 0.80f

        /** Levels per channel a pixel icon's colours are flattened to. */
        const val POSTER_LEVELS = 4
        const val PIXEL_SATURATION = 1.45f

        /** How much of the tile grown art is allowed to take. */
        const val PIXEL_INSET = 0.94f
        const val PIXEL_CONTRAST = 1.30f

        /** How many greys the Gray ink flattens to. Five reads as shading; three as a poster. */
        const val GRAY_LEVELS = 5

        /**
         * How lopsided art has to be before it goes on a plate instead of being grown to fill
         * the tile. 1.6 catches wordmarks and leaves a slightly-oblong logo alone.
         */
        const val WORDMARK_ASPECT = 1.6f

        /** The faintest pixel that still counts as art when measuring where the art is. */
        const val FAINT_ALPHA = 24

        /** Properly there, as opposed to a wash across the tile behind a logo. */
        const val SOLID_ALPHA = 128

        /** The darkest a drawn cell gets, so a black tile is a dark grid and not a hole. */
        const val PIXEL_FLOOR = 46

        /**
         * A 4 x 4 ordered (Bayer) matrix as thresholds in 0..1. Dithering coverage against this
         * turns a soft edge into a checker instead of a step, which is what lets a twelve-cell
         * icon still show a curve.
         */
        val BAYER = floatArrayOf(
            0f, 8f, 2f, 10f,
            12f, 4f, 14f, 6f,
            3f, 11f, 1f, 9f,
            15f, 7f, 13f, 5f,
        ).map { (it + 0.5f) / 16f }.toFloatArray()
    }
}

/** The largest bitmap an icon is ever rasterised to, so a big icon size cannot blow the cache. */
const val MaxIconPx = 160

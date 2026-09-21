// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.photo

import com.nulis.launcher.blocks.GridSpan

import androidx.core.graphics.get
import androidx.core.graphics.createBitmap
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisTextField
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.SegmentedPills
import com.nulis.launcher.ui.theme.NulisShapes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.nulis.launcher.blocks.GhostPhoto
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** How a picture is drawn. None of these touch the original file; the treatment is Nulis's own. */
enum class PhotoFilter(val id: String, val label: String) {
    ORIGINAL("original", "Original"),
    GRAYSCALE("gray", "Grayscale"),
    DITHER("dither", "Dither"),
    DOTS("dots", "Dot matrix"),
}

/**
 * One picture from the phone, kept as a URI with a lasting read grant.
 *
 * Deliberately the system file picker rather than the photo picker: the photo picker's grant
 * does not survive a reboot, and a picture that vanishes overnight is worse than no picture.
 * Nulis never reads the gallery, only the one file the user handed it.
 */
object PhotoBlockDefinition : BlockDefinition {
    override val type = "photo"
    override val label = "Photo"

    override val minSpan = GridSpan(2, 2)
    override val defaultSpan = GridSpan(6, 4)
    override val styles: List<BlockStyle> = listOf(FullStyle, FramedStyle)
    override val previewHeight get() = 120.dp

    private const val KEY_URI = "uri"
    private const val KEY_FILTER = "filter"
    private const val KEY_CAPTION = "caption"

    private fun uri(block: Block): Uri? = block.settings[KEY_URI]?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    private fun filter(block: Block): PhotoFilter =
        PhotoFilter.entries.firstOrNull { it.id == block.settings[KEY_FILTER] } ?: PhotoFilter.GRAYSCALE

    private fun caption(block: Block): String = block.settings[KEY_CAPTION].orEmpty()

    override val hasOptions: Boolean get() = true

    @Composable
    override fun Options(block: Block, context: BlockContext, onUpdate: (Block) -> Unit) {
        val appContext = LocalContext.current
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked ->
            if (picked == null) return@rememberLauncherForActivityResult
            // Without this the grant dies with the process and the block goes blank on reboot.
            runCatching {
                appContext.contentResolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }.onFailure { Log.w(TAG, "No persistable grant for $picked", it) }
            onUpdate(block.copy(settings = block.settings + (KEY_URI to picked.toString())))
        }
        var captionDraft by remember(block.id) { mutableStateOf(caption(block)) }
        Column(Modifier.fillMaxWidth()) {
            PillButton(
                text = if (uri(block) == null) "Choose a picture" else "Choose another",
                onClick = { picker.launch(arrayOf("image/*")) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            SectionLabel("Treatment")
            SegmentedPills(
                options = PhotoFilter.entries,
                selected = filter(block),
                label = { it.label },
                onSelect = { onUpdate(block.copy(settings = block.settings + (KEY_FILTER to it.id))) },
            )
            Spacer(Modifier.height(16.dp))
            SectionLabel("Caption")
            NulisTextField(
                value = captionDraft,
                onValueChange = {
                    captionDraft = it
                    onUpdate(block.copy(settings = block.settings + (KEY_CAPTION to it)))
                },
                placeholder = "Optional",
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }

    /** The picture, edge to edge, in the block's own corner radius. */
    private object FullStyle : BlockStyle {
        override val id = "full"
        override val label = "Full"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val wide = block.size == BlockSize.WIDE
            BlockColumn(modifier) {
                Picture(
                    block = block,
                    context = context,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (wide) 1f else 1.9f)
                        .clip(NulisShapes.card),
                )
                if (caption(block).isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    BlockCaptionRow(caption(block))
                }
            }
        }
    }

    /** The picture inside a hairline frame with room around it, the way a print is hung. */
    private object FramedStyle : BlockStyle {
        override val id = "framed"
        override val label = "Framed"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            BlockColumn(modifier) {
                Box(
                    Modifier
                        .fillMaxWidth(if (wide) 1f else 0.7f)
                        .background(colors.surface, NulisShapes.tile)
                        .border(1.dp, colors.hairline, NulisShapes.tile)
                        .padding(10.dp),
                ) {
                    Picture(
                        block = block,
                        context = context,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(if (wide) 1.2f else 1.6f)
                            .clip(NulisShapes.tile),
                    )
                }
                if (caption(block).isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    BlockCaptionRow(caption(block))
                }
            }
        }
    }

    /**
     * Decodes and filters off the main thread, at the size it will actually be drawn, and
     * remembers the result for that (file, treatment, size). Nothing here happens on a frame.
     */
    @Composable
    private fun Picture(block: Block, context: BlockContext, modifier: Modifier) {
        val appContext = LocalContext.current
        val density = LocalDensity.current
        val colors = NulisTheme.colors
        val source = uri(block)
        val treatment = filter(block)
        if (source == null) {
            // A frame with a horizon in it, not a sentence: the block says what it holds, and
            // the tap goes where the picture is chosen. "Long-press" stopped meaning that the
            // night a long press anywhere started opening the editor.
            Box(
                modifier
                    .background(colors.surface)
                    .border(1.dp, colors.hairline, NulisShapes.tile)
                    .pressFeedback()
                    .clickable(remember { MutableInteractionSource() }, indication = null) { context.openBlockOptions(block) },
                contentAlignment = Alignment.Center,
            ) {
                GhostPhoto(Modifier.fillMaxSize())
            }
            return
        }
        val targetPx = with(density) { 640.dp.roundToPx() }
        val bitmap by produceState<Bitmap?>(initialValue = null, source, treatment, targetPx) {
            value = withContext(Dispatchers.Default) { load(appContext, source, treatment, targetPx) }
        }
        val image = bitmap
        when {
            image == null -> Box(modifier.background(colors.surface))
            treatment == PhotoFilter.DOTS -> DotMatrix(image, modifier)
            else -> Image(
                bitmap = image.asImageBitmap(),
                contentDescription = caption(block).ifBlank { "Photo" },
                modifier = modifier,
                contentScale = ContentScale.Crop,
                filterQuality = if (treatment == PhotoFilter.DITHER) FilterQuality.None else FilterQuality.Low,
                colorFilter = if (treatment == PhotoFilter.GRAYSCALE) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else {
                    null
                },
            )
        }
    }

    /**
     * The dot-matrix treatment: the picture is reduced to a grid of brightnesses once, off the
     * main thread, and what is drawn every frame is one circle per cell sized by that number.
     * No bitmap sampling and no shader on the draw path - the same idea as the Dot look's own
     * hairlines, applied to a photograph.
     */
    @Composable
    private fun DotMatrix(source: Bitmap, modifier: Modifier) {
        val color = NulisTheme.colors.onBackground
        val background = NulisTheme.colors.surface
        // One pass over the small bitmap, kept for as long as the picture is the same.
        val grid = remember(source) {
            val columns = DotColumns
            val rows = (columns * source.height / source.width.coerceAtLeast(1)).coerceIn(1, 64)
            val values = FloatArray(columns * rows)
            for (y in 0 until rows) {
                for (x in 0 until columns) {
                    val px = source[
                        (x * source.width / columns).coerceIn(0, source.width - 1),
                        (y * source.height / rows).coerceIn(0, source.height - 1),
                    ]
                    values[y * columns + x] =
                        ((px shr 16 and 0xFF) * 0.299f + (px shr 8 and 0xFF) * 0.587f + (px and 0xFF) * 0.114f) / 255f
                }
            }
            DotGridData(columns, rows, values)
        }
        Canvas(modifier.background(background)) {
            val cellW = size.width / grid.columns
            val cellH = size.height / grid.rows
            val cell = minOf(cellW, cellH)
            for (y in 0 until grid.rows) {
                for (x in 0 until grid.columns) {
                    val radius = cell * 0.5f * grid.values[y * grid.columns + x]
                    if (radius > 0.3f) {
                        drawCircle(color, radius, Offset((x + 0.5f) * cellW, (y + 0.5f) * cellH))
                    }
                }
            }
        }
    }

    /** How many dots across a dot-matrix photo is drawn with. */
    private const val DotColumns = 44

    private class DotGridData(val columns: Int, val rows: Int, val values: FloatArray)

    /** Decode at a sane size, then apply whichever treatment needs real pixel work. */
    private fun load(context: Context, uri: Uri, filter: PhotoFilter, targetPx: Int): Bitmap? = try {
        // The dot grid only ever needs a thumbnail; the dither wants pixels it can actually see.
        val target = if (filter == PhotoFilter.DOTS) 128 else targetPx
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > target) decoder.setTargetSampleSize(sampleSize(longest, target))
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            decodeLegacy(context, uri, target)
        }
        if (decoded != null && filter == PhotoFilter.DITHER) dither(decoded) else decoded
    } catch (e: Exception) {
        // A picture that has been deleted, moved or had its grant revoked must not crash a home
        // screen; the block shows its empty plate instead.
        Log.w(TAG, "Could not read $uri", e)
        null
    }

    /** A power of two, which is the only sample size a decoder actually honours. */
    private fun sampleSize(longest: Int, target: Int): Int {
        var sample = 1
        while (longest / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** Android 8 has no ImageDecoder; the old path does the same job with a sample size. */
    private fun decodeLegacy(context: Context, uri: Uri, target: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        val options = BitmapFactory.Options().apply {
            inSampleSize = if (longest > target) sampleSize(longest, target) else 1
        }
        return context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    /**
     * Floyd-Steinberg error diffusion to pure black and white. Slow per pixel and so done once,
     * off the main thread, on an already-small bitmap.
     */
    private fun dither(bitmap: Bitmap): Bitmap {
        val width = bitmap.width
        val height = bitmap.height
        val gray = FloatArray(width * height)
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        for (i in pixels.indices) {
            val p = pixels[i]
            gray[i] = ((p shr 16 and 0xFF) * 0.299f + (p shr 8 and 0xFF) * 0.587f + (p and 0xFF) * 0.114f)
        }
        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                val old = gray[index]
                val new = if (old < 128f) 0f else 255f
                val error = old - new
                gray[index] = new
                fun spread(dx: Int, dy: Int, weight: Float) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until height) gray[ny * width + nx] += error * weight
                }
                spread(1, 0, 7 / 16f)
                spread(-1, 1, 3 / 16f)
                spread(0, 1, 5 / 16f)
                spread(1, 1, 1 / 16f)
            }
        }
        for (i in pixels.indices) {
            val v = gray[i].coerceIn(0f, 255f).toInt()
            pixels[i] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
        }
        val out = createBitmap(width, height)
        out.setPixels(pixels, 0, width, 0, 0, width, height)
        return out
    }

    private const val TAG = "PhotoBlock"
}

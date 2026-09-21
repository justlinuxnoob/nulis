// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.music

import com.nulis.launcher.blocks.GridSpan

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.BlockBox
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.blocks.ScreenRequest
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.GlyphIcon
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.LocalBlockPreview
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.PermissionScreen
import com.nulis.launcher.ui.components.dotGrid
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisMotion
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import com.nulis.launcher.blocks.blockIsAnimating
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.animation.core.Animatable

/**
 * Whatever is playing, from any player on the phone. Tapping the block opens that player;
 * the transport buttons act on the session directly.
 */
object MusicBlockDefinition : BlockDefinition {
    override val type = "music"
    override val label = "Music"

    override val minSpan = GridSpan(2, 1)
    override val defaultSpan = GridSpan(6, 2)
    override val styles: List<BlockStyle> = listOf(BarsStyle, MinimalStyle, ArtStyle, RowStyle, VinylStyle, MarqueeStyle)
    override val defaultSize = BlockSize.WIDE
    override val hasOptions: Boolean get() = true

    /** Request arg that opens the explanation screen. */
    const val PERMISSION = "permission"

    /** Public so a layout can ship a music block that takes no room until something plays. */
    const val KEY_HIDE_IDLE = "hide_idle"
    private const val KEY_COLOR_ART = "color_art"

    private fun hideWhenIdle(block: Block): Boolean = block.settings[KEY_HIDE_IDLE] == "1"

    private fun colorArt(block: Block): Boolean = block.settings[KEY_COLOR_ART] == "1"

    /**
     * With "hide when nothing is playing" on, the block takes no room at all between tracks.
     * Never hidden before notification access is granted, or the prompt could not be tapped.
     */
    override fun isHidden(block: Block, context: BlockContext): Boolean =
        hideWhenIdle(block) && context.music.granted && !context.music.hasTrack

    @Composable
    override fun Screen(request: ScreenRequest, context: BlockContext, onClose: () -> Unit) {
        NotificationAccessExplanation(onClose)
    }

    @Composable
    override fun Options(block: Block, context: BlockContext, onUpdate: (Block) -> Unit) {
        ListRow(
            title = "Hide when nothing is playing",
            subtitle = "The block disappears between tracks",
            trailing = {
                NulisToggle(
                    checked = hideWhenIdle(block),
                    onCheckedChange = { onUpdate(block.setting(KEY_HIDE_IDLE, it)) },
                )
            },
        )
        ListRow(
            title = "Album art in color",
            subtitle = "Off: the art is rendered in grayscale",
            divider = false,
            trailing = {
                NulisToggle(
                    checked = colorArt(block),
                    onCheckedChange = { onUpdate(block.setting(KEY_COLOR_ART, it)) },
                )
            },
        )
    }

    private fun Block.setting(key: String, on: Boolean): Block =
        copy(settings = settings + (key to if (on) "1" else "0"))

    /** Columns that dance while a track plays and freeze the moment it is paused. */
    private object BarsStyle : BlockStyle {
        override val id = "bars"
        override val label = "Beat bars"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val state = context.music.orSample()
            val wide = block.size == BlockSize.WIDE
            BlockColumn(modifier.openPlayer(context)) {
                Header(state)
                if (state.hasTrack) {
                    Spacer(Modifier.height(if (wide) 8.dp else 4.dp))
                    Title(state, if (wide) NulisTheme.type.bodyXl else NulisTheme.type.bodyL)
                    Subtitle(state)
                }
                Spacer(Modifier.height(if (wide) 14.dp else 10.dp))
                // Resting, the bars are a single flat row of dots. Keeping the playing height for
                // it left a hand's width of dead air between the caption and that row, which read
                // as a block that had failed to draw rather than as one with nothing to say.
                val barsHeight = when {
                    !state.hasTrack -> if (wide) 20.dp else 14.dp
                    wide -> 72.dp
                    else -> 32.dp
                }
                BeatBars(
                    playing = state.playing,
                    resting = !state.hasTrack,
                    track = state.title + state.artist,
                    cell = if (wide) 5.dp else 3.5.dp,
                    modifier = Modifier.fillMaxWidth().height(barsHeight),
                )
                if (wide && state.hasTrack) {
                    Spacer(Modifier.height(12.dp))
                    Transport(state, context)
                }
                Setup(state)
            }
        }
    }

    /** One line, one button. For a page that should stay almost empty. */
    private object MinimalStyle : BlockStyle {
        override val id = "minimal"
        override val label = "Minimal"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val state = context.music.orSample()
            val wide = block.size == BlockSize.WIDE
            BlockRow(modifier.openPlayer(context), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), horizontalAlignment = blockAlign().horizontal) {
                    if (state.hasTrack) {
                        Title(state, if (wide) NulisTheme.type.bodyXl else NulisTheme.type.bodyL)
                        Subtitle(state)
                    } else {
                        Caption("Music")
                        Spacer(Modifier.height(4.dp))
                        Text(idleText(state), style = NulisTheme.type.bodyM, color = colors.secondary)
                    }
                }
                if (state.hasTrack) {
                    Spacer(Modifier.width(12.dp))
                    NulisIconButton(
                        glyph = if (state.playing) Glyph.Pause else Glyph.Play,
                        onClick = { context.musicActions.playPause() },
                        bordered = true,
                        enabled = state.canPlayPause,
                    )
                }
            }
        }
    }

    /** The album art, grayscale by default, with the title lying across the bottom of it. */
    private object ArtStyle : BlockStyle {
        override val id = "art"
        override val label = "Art"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val state = context.music.orSample()
            val color = colorArt(block)
            if (block.size == BlockSize.WIDE) {
                BlockColumn(modifier.openPlayer(context)) {
                    Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(NulisShapes.card)) {
                        Artwork(state, color, Modifier.fillMaxSize())
                        if (state.hasTrack) {
                            // A scrim only where the words are, so the art keeps its own light.
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0.42f to Color.Transparent,
                                            0.76f to colors.background.copy(alpha = 0.66f),
                                            1f to colors.background.copy(alpha = 0.96f),
                                        ),
                                    ),
                            )
                            Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(20.dp), horizontalAlignment = blockAlign().horizontal) {
                                Title(state, NulisTheme.type.displayM)
                                Subtitle(state)
                            }
                        }
                    }
                    if (state.hasTrack) {
                        Spacer(Modifier.height(12.dp))
                        Transport(state, context)
                    } else {
                        Spacer(Modifier.height(8.dp))
                        Setup(state)
                    }
                }
            } else {
                BlockRow(modifier.openPlayer(context), verticalAlignment = Alignment.CenterVertically) {
                    Artwork(state, color, Modifier.size(72.dp).clip(NulisShapes.tile))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f), horizontalAlignment = blockAlign().horizontal) {
                        if (state.hasTrack) {
                            Title(state, NulisTheme.type.bodyL)
                            Subtitle(state)
                        } else {
                            Caption("Music")
                            Spacer(Modifier.height(4.dp))
                            Text(idleText(state), style = NulisTheme.type.bodyM, color = colors.secondary)
                        }
                    }
                    if (state.hasTrack) {
                        Spacer(Modifier.width(12.dp))
                        NulisIconButton(
                            glyph = if (state.playing) Glyph.Pause else Glyph.Play,
                            onClick = { context.musicActions.playPause() },
                            bordered = true,
                            enabled = state.canPlayPause,
                        )
                    }
                }
            }
        }
    }


    /**
     * The classic now-playing row: small art, the track, and the three transport buttons, all on
     * one line. The compact skin for a page that already has plenty on it.
     */
    private object RowStyle : BlockStyle {
        override val id = "row"
        override val label = "Row"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val state = context.music.orSample()
            val wide = block.size == BlockSize.WIDE
            BoxWithConstraints(modifier.openPlayer(context)) {
                // A row this narrow cannot hold art, a title and three buttons: the title is the
                // thing worth keeping, so the rest is dropped in that order as the room runs out.
                val showArt = maxWidth >= 200.dp
                val showSkip = maxWidth >= 300.dp
                val art = if (wide) 56.dp else 44.dp
                BlockRow(verticalAlignment = Alignment.CenterVertically) {
                    if (showArt) {
                        Artwork(state, colorArt(block), Modifier.size(art).clip(NulisShapes.tile))
                        Spacer(Modifier.width(12.dp))
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = blockAlign().horizontal) {
                        if (state.hasTrack) {
                            Title(state, if (wide) NulisTheme.type.bodyL else NulisTheme.type.bodyM)
                            Subtitle(state)
                        } else {
                            Caption("Music")
                            Spacer(Modifier.height(2.dp))
                            Text(idleText(state), style = NulisTheme.type.bodyM, color = colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (state.hasTrack) {
                        Spacer(Modifier.width(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (showSkip) {
                                NulisIconButton(Glyph.Previous, { context.musicActions.skipPrevious() }, enabled = state.canSkipPrevious)
                            }
                            NulisIconButton(
                                glyph = if (state.playing) Glyph.Pause else Glyph.Play,
                                onClick = { context.musicActions.playPause() },
                                bordered = true,
                                enabled = state.canPlayPause,
                            )
                            if (showSkip) {
                                NulisIconButton(Glyph.Next, { context.musicActions.skipNext() }, enabled = state.canSkipNext)
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * The record itself: the art on a disc that turns while the music plays and stops dead when
     * it is paused, which is the whole of the feedback. Like every animated block it renders
     * nothing at all on a page you are not looking at.
     */
    private object VinylStyle : BlockStyle {
        override val id = "vinyl"
        override val label = "Vinyl"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val state = context.music.orSample()
            val wide = block.size == BlockSize.WIDE
            val disc = if (wide) 132.dp else 84.dp
            val spinning = state.playing && blockIsAnimating()
            val angle = remember { Animatable(0f) }
            LaunchedEffect(spinning) {
                if (!spinning) return@LaunchedEffect
                // One turn every six seconds: slow enough to be calm, fast enough to be alive.
                while (true) {
                    withFrameNanos { }
                    angle.snapTo((angle.value + 0.6f) % 360f)
                }
            }
            BlockColumn(modifier.fillMaxWidth().openPlayer(context)) {
                Header(state)
                Spacer(Modifier.height(10.dp))
                BlockBox {
                    Box(Modifier.size(disc), contentAlignment = Alignment.Center) {
                        Box(
                            Modifier
                                .size(disc)
                                .graphicsLayer { rotationZ = angle.value }
                                .clip(CircleShape)
                                .background(colors.surfaceRaised),
                        ) {
                            Artwork(state, colorArt(block), Modifier.fillMaxSize().clip(CircleShape))
                            Canvas(Modifier.fillMaxSize()) {
                                // The grooves, and the hole that makes it a record and not a coaster.
                                val centre = Offset(size.width / 2f, size.height / 2f)
                                listOf(0.94f, 0.82f, 0.70f).forEach { ring ->
                                    drawCircle(
                                        color = colors.background.copy(alpha = 0.22f),
                                        radius = size.minDimension / 2f * ring,
                                        center = centre,
                                        style = Stroke(1.dp.toPx()),
                                    )
                                }
                                drawCircle(colors.background, size.minDimension * 0.055f, centre)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                if (state.hasTrack) {
                    Title(state, if (wide) NulisTheme.type.bodyL else NulisTheme.type.bodyM)
                    Subtitle(state)
                } else {
                    Text(idleText(state), style = NulisTheme.type.bodyM, color = colors.secondary, textAlign = blockAlign().textAlign, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    /**
     * The song as a poster: the title big, in the Look's own display face, marqueeing if it is
     * long, and nothing else but the artist underneath. No art, no buttons - a page that says
     * what is on rather than a thing you operate.
     */
    private object MarqueeStyle : BlockStyle {
        override val id = "marquee"
        override val label = "Marquee"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val state = context.music.orSample()
            val wide = block.size == BlockSize.WIDE
            BlockColumn(modifier.fillMaxWidth().openPlayer(context)) {
                Header(state)
                Spacer(Modifier.height(6.dp))
                if (state.hasTrack) {
                    Title(state, if (wide) NulisTheme.type.displayM else NulisTheme.type.displayS)
                    Subtitle(state)
                } else {
                    Text(idleText(state), style = NulisTheme.type.bodyM, color = colors.secondary, textAlign = blockAlign().textAlign, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }

    // Shared pieces.

    @Composable
    private fun Header(state: MusicState) {
        BlockCaptionRow(
            label = if (state.playing) "Now playing" else "Music",
            trailing = state.playerLabel.takeIf { state.hasTrack && it.isNotBlank() },
        )
    }

    /** Long titles scroll rather than getting cut off. */
    @Composable
    private fun Title(state: MusicState, style: androidx.compose.ui.text.TextStyle) {
        Text(
            text = state.title.ifBlank { state.playerLabel.ifBlank { "Playing" } },
            style = style,
            color = NulisTheme.colors.onBackground,
            maxLines = 1,
            softWrap = false,
            textAlign = blockAlign().textAlign,
            overflow = TextOverflow.Visible,
            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 1_800),
        )
    }

    @Composable
    private fun Subtitle(state: MusicState) {
        val subtitle = state.subtitle
        if (subtitle.isBlank()) return
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle.uppercase(),
            style = NulisTheme.type.label,
            color = NulisTheme.colors.secondary,
            maxLines = 1,
            softWrap = false,
            textAlign = blockAlign().textAlign,
            overflow = TextOverflow.Visible,
            modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE, repeatDelayMillis = 1_800),
        )
    }

    @Composable
    private fun Transport(state: MusicState, context: BlockContext) {
        BlockBox {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                NulisIconButton(
                    glyph = Glyph.Previous,
                    onClick = { context.musicActions.skipPrevious() },
                    enabled = state.canSkipPrevious,
                )
                NulisIconButton(
                    glyph = if (state.playing) Glyph.Pause else Glyph.Play,
                    onClick = { context.musicActions.playPause() },
                    bordered = true,
                    enabled = state.canPlayPause,
                )
                NulisIconButton(
                    glyph = Glyph.Next,
                    onClick = { context.musicActions.skipNext() },
                    enabled = state.canSkipNext,
                )
            }
        }
    }

    @Composable
    private fun Artwork(state: MusicState, color: Boolean, modifier: Modifier) {
        val colors = NulisTheme.colors
        val art = state.art
        if (art == null) {
            // No cover: a quiet frame in the Look's own motif rather than a broken image.
            Box(
                modifier
                    .background(colors.surface)
                    .dotGrid(16.dp)
                    .border(1.dp, colors.hairline, NulisShapes.tile),
            )
            return
        }
        val filter = remember(color) {
            if (color) null else ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
        }
        Image(
            bitmap = art,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = filter,
            modifier = modifier,
        )
    }

    /** The one line that explains why the block is empty, if it is. */
    @Composable
    private fun Setup(state: MusicState) {
        if (state.hasTrack) return
        Spacer(Modifier.height(8.dp))
        Text(idleText(state), style = NulisTheme.type.bodyM, color = NulisTheme.colors.secondary)
    }

    private fun idleText(state: MusicState): String =
        if (state.granted) "Nothing playing" else "Tap to allow media access"

    /** Tapping the block opens the player, or the explanation screen if access is still missing. */
    @Composable
    private fun Modifier.openPlayer(context: BlockContext): Modifier {
        val actions = context.musicActions
        val granted = context.music.granted
        return this
            .pressFeedback()
            .clickable(remember { MutableInteractionSource() }, indication = null) {
                if (granted) actions.openPlayer() else context.openScreen(ScreenRequest(type, PERMISSION))
            }
    }

    /**
     * Inside a picker with nothing playing, show a sample track so the cards are not three
     * variations of "Nothing playing". Everywhere else this returns the real state untouched.
     */
    @Composable
    private fun MusicState.orSample(): MusicState =
        if (hasTrack || !LocalBlockPreview.current) this
        else copy(granted = true, title = "Midnight Ferry", artist = "Halcyon Pines", playing = true, canSkipNext = true, canSkipPrevious = true)
}

/**
 * Simulated spectrum columns, at rest when nothing is playing at all. Never the microphone: Nulis asks for no audio permission and this
 * is arithmetic, not listening.
 *
 * The motion is built from a beat the track's own name decides (84-132 BPM, stable for as long as
 * that track plays), a kick envelope that drops the low columns on every beat, a faster envelope
 * that flicks the high ones on the offbeat, two slow sines that keep neighbours from ever matching,
 * and an eight-beat swell. Pausing stops the clock, so the columns hold exactly the shape they
 * had and fade to the tertiary colour; they pick that shape back up when the track resumes.
 */
@Composable
fun BeatBars(playing: Boolean, resting: Boolean, track: String, cell: Dp, modifier: Modifier = Modifier) {
    val colors = NulisTheme.colors
    val dotted = NulisTheme.look.dotMotif
    // Read only inside the draw lambda, so a moving column costs a redraw and never a recomposition.
    val clock = remember { mutableFloatStateOf(0f) }
    val bpm = remember(track) { 84f + (abs(track.hashCode()) % 49) }
    // Several page miniatures on screen at once must not each drive an animation, an off-screen
    // page must drive none, and reduced motion stops them all.
    val moving = com.nulis.launcher.blocks.blockIsAnimating()
    LaunchedEffect(playing, moving) {
        if (!playing || !moving) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            clock.floatValue += ((now - previous) / 1_000_000_000.0).toFloat()
            previous = now
        }
    }
    val tint by animateColorAsState(
        if (playing) colors.onBackground else colors.tertiary,
        tween(NulisMotion.quick),
        label = "beatBars",
    )
    Canvas(modifier) {
        val side = cell.toPx()
        val gapX = side * 0.6f
        val columns = ((size.width + gapX) / (side + gapX)).toInt().coerceAtLeast(4)
        val pitchX = (size.width + gapX) / columns
        val gapY = side * 0.5f
        val rows = ((size.height + gapY) / (side + gapY)).toInt().coerceAtLeast(3)
        val pitchY = (size.height + gapY) / rows
        val time = clock.floatValue
        for (i in 0 until columns) {
            val level = if (resting) restLevel(i) else barLevel(i, columns, time, bpm)
            val x = i * pitchX
            if (dotted) {
                // Dot look: a column of square cells lighting up from the bottom, like a matrix display.
                val lit = (level * rows).roundToInt().coerceAtLeast(1)
                for (row in 0 until lit) {
                    drawRect(tint, Offset(x, size.height - (row + 1) * pitchY + gapY), Size(side, side))
                }
            } else {
                val height = (size.height * level).coerceAtLeast(side)
                drawRoundRect(
                    color = tint,
                    topLeft = Offset(x, size.height - height),
                    size = Size(side, height),
                    cornerRadius = CornerRadius(side / 2f),
                )
            }
        }
    }
}

/** A low, barely uneven line: what the block looks like when no player has anything loaded. */
private fun restLevel(i: Int): Float = 0.08f + 0.06f * (sin(i * 12.9898f) * 43758.5453f).let { it - floor(it) }

/**
 * Height of column [i] of [count] at [time] seconds into a track running at [bpm], 0..1.
 *
 * It is a three-piece drum kit rather than noise: a kick on every beat that drops the low
 * columns, a snare on the backbeat that pushes the middle ones, hats on the offbeats that flick
 * the high end, then two slow sines per column so neighbours never move together, an eight-beat
 * swell, and a fixed per-column grain so the spectrum is a ragged line instead of a clean ramp.
 */
private fun barLevel(i: Int, count: Int, time: Float, bpm: Float): Float {
    val band = if (count <= 1) 0f else i / (count - 1f)
    val beats = time * bpm / 60f
    val kick = exp(-(beats - floor(beats)) * 3.2f)
    val hat = exp(-((beats * 2f).let { it - floor(it) }) * 5f)
    // Beats 2 and 4 of every four: the backbeat.
    val sinceSnare = ((beats + 3f) * 0.5f).let { (it - floor(it)) * 2f }
    val snare = exp(-sinceSnare * 4f)
    val low = (1f - band).pow(1.4f)
    val mid = exp(-((band - 0.45f) * 3f).pow(2))
    val high = band.pow(1.1f)
    val wobble = 0.20f * sin(time * (2.1f + i * 0.83f) + i * 1.7f) + 0.12f * sin(time * 0.37f + i)
    val swell = 0.88f + 0.12f * sin(time * 2f * PI.toFloat() * bpm / (60f * 8f))
    val grain = 0.86f + 0.28f * (sin(i * 12.9898f) * 43758.5453f).let { it - floor(it) }
    val body = 0.40f + 0.45f * low * kick + 0.38f * mid * snare + 0.40f * high * hat
    return ((body + wobble) * swell * grain).coerceIn(0.05f, 1f)
}

@Composable
private fun NotificationAccessExplanation(onClose: () -> Unit) {
    val activityContext = LocalContext.current
    PermissionScreen(
        title = "Music",
        explanation = "To show what is playing, Nulis needs notification access. That is the only way Android lets an app read the media controls any player puts on your lock screen.",
        points = listOf(
            "Nulis reads the title, artist and cover of the current track, and nothing else about it.",
            "The same access powers the optional notification dot, and all that takes from a notification is which app it came from - never the sender, the text or how many there are. Nothing is stored and nothing is in your backups.",
            "Everything stays on this phone. Nulis has no internet permission.",
            "Turn it off any time in the same settings screen; the block goes quiet and the dots go out.",
        ),
        onAllow = { openNotificationAccessSettings(activityContext) },
        onNotNow = onClose,
        allowLabel = "Open settings",
    )
}

/**
 * Android 11+ can open Nulis's own row in notification-access settings; older versions get the
 * whole list. Shared with onboarding.
 */
fun openNotificationAccessSettings(context: android.content.Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS).putExtra(
            Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
            android.content.ComponentName(context, NulisNotificationListener::class.java).flattenToString(),
        )
    } else {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
    runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
}

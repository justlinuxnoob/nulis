// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.home.BlockStrip
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisCard
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.AccentSwatches
import com.nulis.launcher.ui.theme.ColorTheme
import com.nulis.launcher.ui.theme.LocalNulisColors
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme
import com.nulis.launcher.ui.theme.Palette
import com.nulis.launcher.ui.theme.Palettes
import com.nulis.launcher.ui.theme.contrastRatio
import com.nulis.launcher.ui.theme.customColors
import kotlin.math.roundToInt

/** WCAG AA for body text. Below this, text on a background is genuinely hard to read. */
private const val ReadableContrast = 4.5f

/**
 * The eight palettes, each drawn as the user's own home page in that palette's colours. A palette
 * is a background, an ink and an accent that were chosen together, so it is applied together.
 */
@Composable
fun PaletteRow(
    home: PageLayout?,
    context: BlockContext,
    current: Palette?,
    onApply: (Palette) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val layout = home ?: PageLayout(PageIds.HOME)
    SectionLabel("Palettes")
    Spacer(Modifier.height(4.dp))
    Caption("Background, text and accent, chosen together. Each one stays editable after.", lines = 2)
    Spacer(Modifier.height(8.dp))
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 4.dp),
    ) {
        items(Palettes.all, key = { it.id }) { palette ->
            val colors = remember(palette) { customColors(palette.background, palette.ink).copy(accent = palette.accent) }
            Column(Modifier.width(144.dp)) {
                NulisCard(
                    modifier = Modifier.fillMaxWidth(),
                    selected = palette.id == current?.id,
                    shape = NulisShapes.tile,
                    contentPadding = 8.dp,
                    onClick = {
                        haptics.performHapticFeedback(NulisHaptics.tick)
                        onApply(palette)
                    },
                ) {
                    CompositionLocalProvider(LocalNulisColors provides colors) {
                        // A strip rather than a page: eight live grids scrolling past in one
                        // row is what put this scroll back over the frame budget, and a palette
                        // card is about the colours, not about where the blocks are.
                        BlockStrip(
                            layout = layout,
                            context = context,
                            modifier = Modifier.fillMaxWidth().height(132.dp),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                // The ink and the accent as swatches: the background is the card itself, and an
                // accent never appears on a page at all, so without this the preview would be
                // telling two thirds of the truth.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    listOf(palette.ink, palette.accent).forEach { swatch ->
                        Box(
                            Modifier
                                .size(10.dp)
                                .background(swatch, CircleShape)
                                .border(1.dp, NulisTheme.colors.hairline, CircleShape),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        palette.name,
                        style = NulisTheme.type.bodyS,
                        color = NulisTheme.colors.onBackground,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The accent, on its own: the colour of a selection dot, a filled toggle and the current-page
 * dot. Destructive actions are deliberately not part of this - they stay red - so nobody can
 * pick a green that means both "chosen" and "delete".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentRow(current: Int?, onPick: (Int?) -> Unit) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    SectionLabel("Accent", trailing = if (current == null) "Default" else null)
    Spacer(Modifier.height(8.dp))
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        DefaultAccentSwatch(selected = current == null) {
            haptics.performHapticFeedback(NulisHaptics.tick)
            onPick(null)
        }
        AccentSwatches.forEach { swatch ->
            val argb = swatch.toArgb()
            ColorDot(swatch, selected = argb == current, ring = colors.onBackground, hairline = colors.hairline) {
                haptics.performHapticFeedback(NulisHaptics.tick)
                onPick(argb)
            }
        }
    }
    Spacer(Modifier.height(8.dp))
    Caption("Selection dots and filled toggles. Destructive actions stay red whatever this is.", lines = 2)
}

/**
 * Says out loud how readable the chosen text colour actually is, and offers the way out.
 *
 * A colour picker with no contrast readout is an invitation to make a launcher you cannot read
 * at midday, and the person who does it will not know why. The ratio is the WCAG one, and 4.5
 * is where the standard puts body text.
 */
@Composable
fun ContrastGuard(background: Color, ink: Color?, onUseReadable: () -> Unit) {
    val colors = NulisTheme.colors
    val text = ink ?: return
    val ratio = contrastRatio(text, background)
    val readable = ratio >= ReadableContrast
    Spacer(Modifier.height(8.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Caption(
            if (readable) {
                "Contrast ${format(ratio)}:1. Comfortable to read."
            } else {
                "Contrast ${format(ratio)}:1. Under 4.5:1 this is hard to read in daylight."
            },
            modifier = Modifier.weight(1f),
            lines = 2,
        )
        if (!readable) {
            Spacer(Modifier.width(8.dp))
            com.nulis.launcher.ui.components.PillButton(
                text = "Fix it",
                compact = true,
                onClick = onUseReadable,
            )
        }
    }
    if (!readable) {
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(colors.danger))
    }
}

private fun format(ratio: Float): String = (ratio * 10).roundToInt().let { "${it / 10}.${it % 10}" }

/** "Whatever the background implies": the swatch shows the accent that would be used. */
@Composable
private fun DefaultAccentSwatch(selected: Boolean, onClick: () -> Unit) {
    val colors = NulisTheme.colors
    ColorDot(colors.danger, selected = selected, ring = colors.onBackground, hairline = colors.hairline, onClick = onClick)
}

@Composable
private fun ColorDot(
    color: Color,
    selected: Boolean,
    ring: Color,
    hairline: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier
            .pressFeedback()
            .size(40.dp)
            .background(color, CircleShape)
            .border(if (selected) 2.dp else 1.dp, if (selected) ring else hairline, CircleShape)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Box(Modifier.size(8.dp).background(ring, CircleShape))
    }
}

/** The palette the current colours came from, if they still match one exactly. */
fun paletteOf(preferences: UiPreferences): Palette? {
    if (preferences.colorTheme != ColorTheme.CUSTOM) return null
    return Palettes.all.firstOrNull {
        it.background.toArgb() == preferences.customBackground &&
            it.ink.toArgb() == preferences.customInk &&
            it.accent.toArgb() == preferences.customAccent
    }
}

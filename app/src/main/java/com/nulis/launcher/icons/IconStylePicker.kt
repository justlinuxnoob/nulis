// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.icons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nulis.launcher.R
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.NulisCard
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.bleed
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * Picks a complete [IconStyle], every option drawn as the thing it produces: a real app in that
 * mode, that shape, that size, that colour. Shape, size and colour are only offered once the
 * style has something to draw, so a text list never asks about squircles.
 *
 * @param sample the app the previews are drawn from, so they show something real.
 * @param modes which modes to offer; the drawer leaves out the one with no name.
 * @param alwaysLabelled for lists that print the name whatever the mode says, like the drawer.
 * @param modeLabel heading over the mode row, or null when the caller already wrote one.
 */
@Composable
fun ColumnScope.IconStylePicker(
    style: IconStyle,
    sample: AppInfo,
    modes: List<IconMode> = IconMode.entries,
    alwaysLabelled: Boolean = false,
    modeLabel: String? = stringResource(R.string.icons_mode),
    onChange: (IconStyle) -> Unit,
) {
    if (modeLabel != null) {
        SectionLabel(modeLabel)
        Spacer(Modifier.height(4.dp))
    }
    OptionRow(modes, style.mode) { mode ->
        Option(mode.label, mode == style.mode, { onChange(style.copy(mode = mode)) }) {
            val labelled = alwaysLabelled || mode.hasLabel
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                // Text mode has no glyph at all; it is only the name it would print.
                if (mode.hasGlyph) AppGlyph(sample, style.copy(mode = mode, size = IconSize.SMALL))
                if (labelled) {
                    Text(
                        text = sample.label,
                        style = NulisTheme.type.bodyS,
                        color = NulisTheme.colors.onBackground,
                        maxLines = if (mode.hasGlyph) 1 else 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = if (mode.hasGlyph) 6.dp else 0.dp),
                    )
                }
            }
        }
    }

    if (!style.mode.hasGlyph) return

    Spacer(Modifier.height(16.dp))
    SectionLabel(stringResource(R.string.icons_shape))
    Spacer(Modifier.height(4.dp))
    OptionRow(IconShape.entries.toList(), style.shape) { shape ->
        Option(shape.label, shape == style.shape, { onChange(style.copy(shape = shape)) }) {
            AppGlyph(sample, style.copy(shape = shape, size = IconSize.MEDIUM))
        }
    }

    Spacer(Modifier.height(16.dp))
    SectionLabel(stringResource(R.string.icons_size))
    Spacer(Modifier.height(4.dp))
    OptionRow(IconSize.entries.toList(), style.size) { size ->
        Option(size.label, size == style.size, { onChange(style.copy(size = size)) }) {
            AppGlyph(sample, style.copy(size = size))
        }
    }

    if (!style.mode.hasIcon) return

    Spacer(Modifier.height(16.dp))
    SectionLabel(stringResource(R.string.icons_color))
    Spacer(Modifier.height(4.dp))
    // Every colour at once, drawn on the user's own app, in one row that cannot be scrolled
    // past. The scrolling version hid the last two behind the edge of the screen, which is how
    // you end up looking for the full-colour option and not finding it.
    FixedRow(IconColor.entries.toList()) { color ->
        Option(color.label, color == style.color, { onChange(style.copy(color = color)) }) {
            AppGlyph(sample, style.copy(color = color, size = IconSize.SMALL))
        }
    }

    if (style.color != IconColor.PIXEL) return

    Spacer(Modifier.height(16.dp))
    SectionLabel(stringResource(R.string.icons_pixel_grid))
    Spacer(Modifier.height(4.dp))
    FixedRow(PixelGrid.entries.toList()) { grid ->
        Option(grid.label, grid == style.pixelGrid, { onChange(style.copy(pixelGrid = grid)) }) {
            AppGlyph(sample, style.copy(pixelGrid = grid, size = IconSize.MEDIUM))
        }
    }

    Spacer(Modifier.height(16.dp))
    SectionLabel(stringResource(R.string.icons_pixel_ink))
    Spacer(Modifier.height(4.dp))
    FixedRow(PixelInk.entries.toList()) { ink ->
        Option(ink.label, ink == style.pixelInk, { onChange(style.copy(pixelInk = ink)) }) {
            AppGlyph(sample, style.copy(pixelInk = ink, size = IconSize.MEDIUM))
        }
    }
}

/** A row that always shows everything it has: equal shares of the width, no scrolling. */
@Composable
private fun <T> FixedRow(options: List<T>, card: @Composable (T) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { option -> Box(Modifier.weight(1f)) { card(option) } }
    }
}

/** A scrolling row of option cards that bleeds past the screen margin, like the style pickers. */
@Composable
private fun <T> OptionRow(options: List<T>, selected: T, card: @Composable (T) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth().bleed(NulisSpacing.screenMargin),
        contentPadding = PaddingValues(horizontal = NulisSpacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(options) { option -> card(option) }
    }
}

/** One option: the live preview in a card, its name underneath. */
@Composable
private fun Option(label: String, selected: Boolean, onClick: () -> Unit, preview: @Composable () -> Unit) {
    val haptics = LocalHapticFeedback.current
    Column(Modifier.widthIn(max = 88.dp)) {
        NulisCard(
            modifier = Modifier.fillMaxWidth(),
            selected = selected,
            label = label,
            shape = NulisShapes.tile,
            contentPadding = 8.dp,
            onClick = {
                if (!selected) haptics.performHapticFeedback(NulisHaptics.tick)
                onClick()
            },
        ) {
            Box(Modifier.fillMaxWidth().height(64.dp), contentAlignment = Alignment.Center) { preview() }
        }
        Spacer(Modifier.height(8.dp))
        Caption(label, lines = 2)
    }
}

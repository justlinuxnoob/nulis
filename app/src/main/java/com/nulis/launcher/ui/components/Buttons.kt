// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisMotion
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme

enum class PillTone { Default, Primary, Danger }

/** Pill-shaped button with a mono uppercase label. 48dp tall (40dp when compact). */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: PillTone = PillTone.Default,
    enabled: Boolean = true,
    compact: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val colors = NulisTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val alpha by animateFloatAsState(if (enabled) 1f else 0.35f, tween(NulisMotion.quick), label = "pillAlpha")
    val background = when (tone) {
        PillTone.Primary -> colors.onBackground
        else -> colors.surface
    }
    val foreground = when (tone) {
        PillTone.Primary -> colors.background
        // Destructive is red whatever the accent is, so "delete" never looks like "selected".
        PillTone.Danger -> colors.danger
        PillTone.Default -> colors.onBackground
    }
    Row(
        modifier = modifier
            .pressFeedback(enabled)
            // A minimum rather than a height: at a 200% system font scale the label has to be
            // allowed to make the button taller instead of being cut in half.
            .defaultMinSize(minHeight = if (compact) 40.dp else NulisSpacing.touchTarget)
            .alpha(alpha)
            .clip(NulisShapes.pill)
            .background(background)
            .border(1.dp, if (tone == PillTone.Primary) background else colors.hairline, NulisShapes.pill)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading()
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = NulisTheme.type.labelCase(text),
            style = NulisTheme.type.label,
            color = foreground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(vertical = 8.dp),
        )
    }
}

/** A 48 dp icon button drawing a [Glyph], or a smaller one where a row of them has to fit. */
@Composable
fun NulisIconButton(
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    bordered: Boolean = false,
    /** The whole button. Below the 48 dp target only inside something already hard to miss. */
    size: Dp = NulisSpacing.touchTarget,
    /** What a screen reader says. Defaults to the glyph's own name. */
    contentDescription: String = glyph.spokenName,
    /** Overrides the usual ink, for a button that is meant to be quiet. */
    tint: Color? = null,
) {
    val colors = NulisTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val ink by animateColorAsState(
        tint ?: if (enabled) colors.onBackground else colors.tertiary,
        tween(NulisMotion.quick),
        label = "iconTint",
    )
    Box(
        modifier = modifier
            .pressFeedback(enabled)
            .size(size)
            .clip(NulisShapes.pill)
            .then(if (bordered) Modifier.border(1.dp, colors.hairline, NulisShapes.pill) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            )
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, ink, size = size * 0.375f)
    }
}

/**
 * Pills where exactly one is selected. They flow onto a second line rather than running off the
 * edge, so a set of four or five choices still fits a sheet. Ticks on selection.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> SegmentedPills(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            PillButton(
                text = label(option),
                tone = if (option == selected) PillTone.Primary else PillTone.Default,
                compact = true,
                onClick = {
                    if (option != selected) {
                        haptics.performHapticFeedback(NulisHaptics.tick)
                        onSelect(option)
                    }
                },
            )
        }
    }
}

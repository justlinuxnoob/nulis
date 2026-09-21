// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisMotion
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme

/** Hardware-style toggle: a thumb that slides and lights a tiny accent dot when on. */
@Composable
fun NulisToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    /** What this toggle is for; the row's title, when there is one. */
    contentDescription: String? = null,
) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    val trackWidth = 46.dp
    val trackHeight = 26.dp
    val thumb = 18.dp
    val inset = 4.dp
    val offset = animateDpAsState(if (checked) trackWidth - thumb - inset else inset, tween(NulisMotion.quick), label = "thumb")
    val thumbColor by animateColorAsState(
        when {
            !enabled -> colors.hairline
            checked -> colors.onBackground
            else -> colors.tertiary
        },
        tween(NulisMotion.quick),
        label = "thumbColor",
    )
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .size(trackWidth, trackHeight)
            .background(colors.surfaceRaised, NulisShapes.pill)
            .border(1.dp, colors.hairline, NulisShapes.pill)
            .semantics {
                contentDescription?.let { this.contentDescription = it }
                stateDescription = if (checked) "On" else "Off"
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
            ) {
                haptics.performHapticFeedback(if (checked) NulisHaptics.toggleOff else NulisHaptics.toggleOn)
                onCheckedChange(!checked)
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .offset { IntOffset(offset.value.roundToPx(), 0) }
                .size(thumb)
                .background(thumbColor, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (checked && enabled) {
                Box(Modifier.size(4.dp).background(colors.accent, CircleShape))
            }
        }
    }
}

/** 22dp check box: hairline square, filled with a check glyph when done. Purely visual; put it in a clickable row. */
@Composable
fun NulisCheck(checked: Boolean, modifier: Modifier = Modifier) {
    // Purely visual: the row around it carries the meaning and the click.
    val colors = NulisTheme.colors
    val fill by animateColorAsState(if (checked) colors.onBackground else colors.surface, tween(NulisMotion.quick), label = "checkFill")
    Box(
        modifier = modifier
            .size(22.dp)
            .background(fill, NulisShapes.pill)
            .border(1.dp, if (checked) colors.onBackground else colors.hairline, NulisShapes.pill),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) GlyphIcon(Glyph.Check, colors.background, size = 14.dp)
    }
}

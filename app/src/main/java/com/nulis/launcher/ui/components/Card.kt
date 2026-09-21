// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.NulisMotion
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * Raised surface with a large radius and a hairline border. When [selected], the border
 * brightens and a small accent indicator dot appears in the corner. When [raised] (a block
 * being dragged), the surface steps up a level and the border brightens without the dot.
 */
@Composable
fun NulisCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    raised: Boolean = false,
    onClick: (() -> Unit)? = null,
    shape: Shape = NulisShapes.card,
    contentPadding: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val colors = NulisTheme.colors
    val border by animateColorAsState(
        when {
            selected -> colors.onBackground
            raised -> colors.secondary
            else -> colors.hairline
        },
        tween(NulisMotion.quick),
        label = "cardBorder",
    )
    val surface by animateColorAsState(if (raised) colors.surfaceRaised else colors.surface, tween(NulisMotion.quick), label = "cardSurface")
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .pressFeedback(enabled = onClick != null)
            .clip(shape)
            .background(surface)
            .border(1.dp, border, shape)
            .then(if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick) else Modifier)
            .padding(contentPadding),
    ) {
        content()
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .size(6.dp)
                    .background(colors.accent, CircleShape),
            )
        }
    }
}

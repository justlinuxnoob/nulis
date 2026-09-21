// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nulis.launcher.ui.theme.LineStyle
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme

/** Content scope of a [NulisBottomSheet]: a column that can also ask the sheet to slide away. */
class NulisSheetScope internal constructor(column: ColumnScope, private val onDismissRequest: () -> Unit) : ColumnScope by column {
    /** Slides the sheet down and calls the sheet's `onDismiss` once it has settled. */
    fun dismiss() = onDismissRequest()
}

/**
 * Modal bottom sheet on the raised surface with a look-specific drag handle. It rises with a
 * critically damped spring, follows the finger when dragged down, and the scrim fades with it.
 * Fills its parent, so call it from a container that covers the whole screen.
 */
@Composable
fun NulisBottomSheet(
    onDismiss: () -> Unit,
    content: @Composable NulisSheetScope.() -> Unit,
) {
    val colors = NulisTheme.colors
    val slide = rememberSlideState(target = true, progress = 0f)
    val scrim = Color.Black.copy(alpha = if (colors.isDark) 0.6f else 0.3f)

    LaunchedEffect(slide) { slide.show() }
    LaunchedEffect(slide.settledHidden) { if (slide.settledHidden) onDismiss() }
    BackHandler { slide.hide() }

    Box(Modifier.fillMaxSize().zIndex(1f)) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = slide.progress; compositingStrategy = CompositingStrategy.ModulateAlpha }
                .background(scrim)
                .pointerInput(slide) { detectTapGestures { slide.hide() } },
        )
        // The status bar is the sheet's ceiling. A sheet whose content runs past the bottom of
        // the screen used to start at y = 0, which put its drag handle under the clock.
        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { slide.travelPx = it.height.toFloat().coerceAtLeast(1f) }
                    .graphicsLayer { translationY = (1f - slide.progress) * size.height }
                    .clip(NulisShapes.sheet)
                    .background(colors.surface),
            ) {
                // The handle is a drag target in its own right, so there is always one place on
                // a sheet that pulls it away whatever the content underneath is doing.
                Box(Modifier.fillMaxWidth().slideDraggable(slide)) { DragHandle() }
                Column(
                    modifier = Modifier
                        .nestedScroll(slide.nestedScrollConnection)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = NulisSpacing.screenMargin)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp),
                ) {
                    val scope = remember(slide) { NulisSheetScope(this, onDismissRequest = { slide.hide() }) }
                    scope.content()
                }
            }
        }
    }
}

/** Five dots in the Dot look, a short bar in the Clean look. */
@Composable
fun DragHandle() {
    val colors = NulisTheme.colors
    Box(Modifier.fillMaxWidth().height(28.dp), contentAlignment = Alignment.Center) {
        if (NulisTheme.look.lineStyle == LineStyle.DOTTED) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(5) { Box(Modifier.size(3.dp).background(colors.tertiary, CircleShape)) }
            }
        } else {
            Box(Modifier.width(32.dp).height(3.dp).background(colors.tertiary, NulisShapes.pill))
        }
    }
}

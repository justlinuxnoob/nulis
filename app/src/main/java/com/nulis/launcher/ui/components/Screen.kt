// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * A full-screen surface with the standard header: a mono label, a display-face title and a
 * trailing action row (back or close icon buttons, pills). Keyboard-aware: it pads for the IME,
 * so editors inside can keep their field above the keyboard.
 */
@Composable
fun NulisScreen(
    label: String,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = { NulisIconButton(Glyph.Close, onClick = onBack, bordered = true) },
    content: @Composable ColumnScope.() -> Unit,
) {
    BackHandler(onBack = onBack)
    val colors = NulisTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .pointerInput(Unit) { detectTapGestures { } }
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = NulisSpacing.screenMargin),
    ) {
        Spacer(Modifier.height(8.dp))
        SectionLabel(label, withLine = false)
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = NulisTheme.type.displayM, color = colors.onBackground, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            trailing()
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}

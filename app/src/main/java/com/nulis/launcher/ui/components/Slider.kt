// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.LineStyle
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * A hairline track (dotted or solid per the Look) with a round thumb. [value] runs 0..1; the
 * mono label sits above with an optional readout on the right. 48dp tall touch area. Drags are
 * absolute: the thumb goes where the finger is, so there is nothing to "grab" first.
 */
@Composable
fun NulisSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    readout: String? = null,
) {
    val colors = NulisTheme.colors
    val dotted = NulisTheme.look.lineStyle == LineStyle.DOTTED
    val current by rememberUpdatedState(onValueChange)
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(NulisTheme.type.labelCase(label), style = NulisTheme.type.label, color = colors.secondary)
            Spacer(Modifier.weight(1f))
            if (readout != null) Text(NulisTheme.type.labelCase(readout), style = NulisTheme.type.label, color = colors.tertiary)
        }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(NulisSpacing.touchTarget)
                .pointerInput(Unit) {
                    detectTapGestures { current((it.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        current((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                },
        ) {
            val y = size.height / 2f
            val thumbR = 8.dp.toPx()
            val x = thumbR + (size.width - 2 * thumbR) * value.coerceIn(0f, 1f)
            if (dotted) {
                val dot = 1.5.dp.toPx()
                drawLine(colors.tertiary, Offset(dot, y), Offset(size.width - dot, y), dot, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(0.01f, 5.dp.toPx())))
            } else {
                drawLine(colors.hairline, Offset(0f, y), Offset(size.width, y), 1.dp.toPx())
            }
            // Filled part of the track, then the thumb.
            drawLine(colors.onBackground, Offset(thumbR, y), Offset(x, y), 2.dp.toPx(), StrokeCap.Round)
            drawCircle(colors.background, thumbR + 3.dp.toPx(), Offset(x, y))
            drawCircle(colors.onBackground, thumbR, Offset(x, y))
        }
    }
}

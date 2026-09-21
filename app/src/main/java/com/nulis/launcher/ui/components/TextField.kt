// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import com.nulis.launcher.ui.theme.NulisTheme

/** Bare text field in the body face with a mono placeholder; no box, no underline. */
@Composable
fun NulisTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    textStyle: TextStyle = NulisTheme.type.bodyL,
    imeAction: ImeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
    onImeAction: () -> Unit = {},
) {
    val colors = NulisTheme.colors
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = singleLine,
        textStyle = textStyle.copy(color = colors.onBackground),
        cursorBrush = SolidColor(colors.onBackground),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeAction() }, onSend = { onImeAction() }, onGo = { onImeAction() }),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.TopStart) {
                if (value.isEmpty()) Text(NulisTheme.type.labelCase(placeholder), style = NulisTheme.type.label, color = colors.tertiary)
                inner()
            }
        },
    )
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.nulis.launcher.R
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisCard
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.NulisSlider
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.bleed
import com.nulis.launcher.ui.theme.Fonts
import com.nulis.launcher.ui.theme.LocalNulisTypography
import com.nulis.launcher.ui.theme.Looks
import com.nulis.launcher.ui.theme.NulisFont
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme
import com.nulis.launcher.ui.theme.NulisTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import kotlin.math.roundToInt

/** What the typography section can change. */
class TypeActions(
    val onDisplayFont: (String?) -> Unit,
    val onBodyFont: (String?) -> Unit,
    val onTextScale: (Float) -> Unit,
    val onUppercaseLabels: (Boolean) -> Unit,
)

private const val ScaleMin = 0.8f
private const val ScaleMax = 1.4f

/**
 * The type controls: a display face, a body face, a size scale and whether labels shout. Every
 * choice is previewed in the face it would apply - a clock for display, a sentence for body -
 * so the decision is made by eye rather than by name.
 */
@Composable
fun TypographySection(preferences: UiPreferences, actions: TypeActions, onLicenses: () -> Unit) {
    val look = Looks.byId(preferences.lookId)

    SectionLabel(stringResource(R.string.settings_type))
    Caption(stringResource(R.string.settings_type_display))
    Spacer(Modifier.height(8.dp))
    FontRow(
        selectedId = preferences.displayFontId,
        defaultLabel = look.font.label,
        onPick = actions.onDisplayFont,
    ) { font ->
        val type = remember(font, preferences) {
            NulisTypography(look, displayOverride = font, bodyOverride = preferences.bodyFont, scale = 1f, uppercaseLabels = preferences.uppercaseLabels)
        }
        CompositionLocalProvider(LocalNulisTypography provides type) {
            Text(
                text = "09:41",
                style = NulisTheme.type.displayXl.copy(fontSize = 34.sp, lineHeight = 38.sp),
                color = NulisTheme.colors.onBackground,
                maxLines = 1,
            )
        }
    }

    Spacer(Modifier.height(16.dp))
    Caption(stringResource(R.string.settings_type_body))
    Spacer(Modifier.height(8.dp))
    FontRow(
        selectedId = preferences.bodyFontId,
        defaultLabel = Fonts.Geist.label,
        onPick = actions.onBodyFont,
    ) { font ->
        val type = remember(font, preferences) {
            NulisTypography(look, displayOverride = preferences.displayFont, bodyOverride = font, scale = 1f, uppercaseLabels = preferences.uppercaseLabels)
        }
        CompositionLocalProvider(LocalNulisTypography provides type) {
            Column {
                Text("Quick brown fox", style = NulisTheme.type.bodyL, color = NulisTheme.colors.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Text(NulisTheme.type.labelCase("Label 12:30"), style = NulisTheme.type.label, color = NulisTheme.colors.secondary, maxLines = 1)
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    NulisSlider(
        label = stringResource(R.string.settings_text_size),
        value = (preferences.textScale - ScaleMin) / (ScaleMax - ScaleMin),
        onValueChange = { actions.onTextScale(ScaleMin + it * (ScaleMax - ScaleMin)) },
        readout = "${(preferences.textScale * 100).roundToInt()}%",
    )
    ListRow(
        title = stringResource(R.string.settings_uppercase_labels),
        subtitle = stringResource(R.string.settings_uppercase_labels_hint),
        onClick = { actions.onUppercaseLabels(!preferences.uppercaseLabels) },
        trailing = { NulisToggle(checked = preferences.uppercaseLabels, onCheckedChange = actions.onUppercaseLabels) },
    )
    ListRow(
        title = stringResource(R.string.settings_font_licenses),
        subtitle = stringResource(R.string.settings_font_licenses_hint),
        onClick = onLicenses,
        divider = false,
    )
}

/** A scrolling row of face cards: "the Look's own" first, then every bundled face. */
@Composable
private fun FontRow(
    selectedId: String?,
    defaultLabel: String,
    onPick: (String?) -> Unit,
    sample: @Composable (NulisFont?) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    LazyRow(
        modifier = Modifier.fillMaxWidth().bleed(NulisSpacing.screenMargin),
        contentPadding = PaddingValues(horizontal = NulisSpacing.screenMargin),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "default") {
            FontCard(
                label = defaultLabel,
                note = "Default",
                selected = selectedId == null,
                onClick = {
                    haptics.performHapticFeedback(NulisHaptics.tick)
                    onPick(null)
                },
            ) { sample(null) }
        }
        items(Fonts.all, key = { it.id }) { font ->
            FontCard(
                label = font.label,
                note = font.note,
                selected = font.id == selectedId,
                onClick = {
                    haptics.performHapticFeedback(NulisHaptics.tick)
                    onPick(font.id)
                },
            ) { sample(font) }
        }
    }
}

@Composable
private fun FontCard(
    label: String,
    note: String,
    selected: Boolean,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.width(150.dp)) {
        NulisCard(
            modifier = Modifier.fillMaxWidth(),
            selected = selected,
            label = "$label, $note",
            shape = NulisShapes.tile,
            contentPadding = 12.dp,
            onClick = onClick,
        ) {
            Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.CenterStart) {
                content()
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, style = NulisTheme.type.bodyM, color = NulisTheme.colors.onBackground, maxLines = 1)
        Caption(note)
    }
}

/**
 * Every bundled face with its licence, read straight out of the APK's own assets. Nulis ships
 * only SIL OFL faces and this is where it proves it.
 */
@Composable
fun FontLicensesScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    var showing by remember { mutableStateOf<NulisFont?>(null) }
    val current = showing
    Box(modifier.fillMaxSize().background(NulisTheme.colors.background)) {
        if (current == null) {
            NulisScreen(
                label = stringResource(R.string.settings_font_licenses),
                title = "${Fonts.all.size} faces",
                onBack = onClose,
            ) {
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    Text(
                        stringResource(R.string.settings_font_licenses_blurb),
                        style = NulisTheme.type.bodyM,
                        color = NulisTheme.colors.secondary,
                    )
                    Spacer(Modifier.height(16.dp))
                    Fonts.all.forEachIndexed { index, font ->
                        ListRow(
                            title = font.label,
                            subtitle = "${font.category.name.lowercase().replaceFirstChar { it.uppercase() }} · ${font.note}",
                            onClick = { showing = font },
                            divider = index != Fonts.all.lastIndex,
                        )
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        } else {
            LicenseText(font = current, onClose = { showing = null })
        }
    }
}

@Composable
private fun LicenseText(font: NulisFont, onClose: () -> Unit) {
    val context = LocalContext.current
    val text by produceState(initialValue = "", key1 = font.licenseAsset) {
        value = withContext(Dispatchers.IO) {
            try {
                context.assets.open(font.licenseAsset).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                "Could not read ${font.licenseAsset}: ${e.message}"
            }
        }
    }
    NulisScreen(label = font.label, title = "Licence", onBack = onClose, modifier = Modifier.zIndex(1f)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(text, style = NulisTheme.type.bodyS, color = NulisTheme.colors.secondary)
            Spacer(Modifier.height(32.dp))
        }
    }
}

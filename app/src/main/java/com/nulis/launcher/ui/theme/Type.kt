// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * The type scale.
 *
 * Display styles come from the current [Look] unless the user picked a display face of their
 * own; body comes from the body face (Geist by default); labels are the mono face, small,
 * letter-spaced and - unless the user turned that off - uppercase.
 *
 * [scale] multiplies every size at once, on top of the system font scale, so someone can make
 * the whole launcher larger or smaller without touching their phone's accessibility settings.
 */
@Immutable
class NulisTypography(
    look: Look,
    displayOverride: NulisFont? = null,
    bodyOverride: NulisFont? = null,
    val scale: Float = 1f,
    /** Whether mono labels and captions are shouted in capitals. */
    val uppercaseLabels: Boolean = true,
) {
    private val displayFont: NulisFont = displayOverride ?: look.font
    private val bodyFont: NulisFont = bodyOverride ?: Fonts.Geist

    /**
     * The face used for small mono labels. A body face that is already mono keeps the whole
     * launcher in one voice - which is the entire point of the Terminal theme.
     */
    private val labelFont: NulisFont =
        if (bodyFont.category == FontCategory.MONO) bodyFont else Fonts.GeistMono

    private val displayFamily: FontFamily = displayFont.family
    private val bodyFamily: FontFamily = bodyFont.family
    private val labelFamily: FontFamily = labelFont.family

    private val displayWeight: FontWeight =
        if (displayOverride == null) look.displayWeight else displayOverride.lightestDisplayWeight
    private val displayWeightSmall: FontWeight =
        if (displayOverride == null) look.displayWeightSmall else FontWeight((displayOverride.lightestDisplayWeight.weight + 100).coerceAtMost(900))
    private val displayTracking: TextUnit =
        if (displayOverride == null) look.displayLetterSpacing else displayOverride.displayTracking.em

    private fun size(sp: Float) = (sp * scale).sp

    private fun display(sp: Float, lineSp: Float, weight: FontWeight) = TextStyle(
        fontFamily = displayFamily,
        fontWeight = weight,
        fontSize = size(sp),
        lineHeight = size(lineSp),
        letterSpacing = displayTracking,
    )

    /** Clock digits. */
    val displayXl = display(96f, 96f, displayWeight)

    /** Big numbers, time in words. */
    val displayL = display(56f, 56f, displayWeight)

    /** Screen titles and sheet titles. */
    val displayM = display(28f, 32f, displayWeightSmall)

    /** Date line, section letters, monograms. */
    val displayS = display(20f, 24f, displayWeightSmall)

    val bodyXl = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Medium, fontSize = size(32f), lineHeight = size(38f), letterSpacing = (-0.01).em)
    val bodyL = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, fontSize = size(18f), lineHeight = size(24f))
    val bodyM = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, fontSize = size(16f), lineHeight = size(22f))
    val bodyS = TextStyle(fontFamily = bodyFamily, fontWeight = FontWeight.Normal, fontSize = size(14f), lineHeight = size(20f))

    /** Small mono labels: section headers, pill buttons, captions. */
    val label = TextStyle(fontFamily = labelFamily, fontWeight = FontWeight.Medium, fontSize = size(11f), lineHeight = size(16f), letterSpacing = 0.12.em)
    val labelL = TextStyle(fontFamily = labelFamily, fontWeight = FontWeight.Medium, fontSize = size(13f), lineHeight = size(18f), letterSpacing = 0.1.em)

    /** Mono readouts: numbers that should feel like an instrument. */
    val mono = TextStyle(fontFamily = labelFamily, fontWeight = FontWeight.Normal, fontSize = size(16f), lineHeight = size(22f))

    /**
     * How a label's own text is cased. Every component that draws a mono label goes through
     * this instead of calling `uppercase()`, so the setting reaches all of them at once.
     */
    fun labelCase(text: String): String = if (uppercaseLabels) text.uppercase() else text
}

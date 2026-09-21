// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

enum class LineStyle { DOTTED, SOLID }

/**
 * A Look is the set of tokens that give the UI its character: the display typeface and the
 * decorative motif. Components read these instead of hardcoding a font or a divider style.
 * Adding a look means adding one more [Look] to [Looks.all].
 */
@Immutable
data class Look(
    val id: String,
    val label: String,
    /** The face this look reaches for by default; the user can override it in settings. */
    val font: NulisFont,
    /** Weight for large display text (clock digits). */
    val displayWeight: FontWeight,
    /** Weight for smaller display text (titles, labels in the display face). */
    val displayWeightSmall: FontWeight,
    val displayLetterSpacing: TextUnit,
    /** How dividers, drag handles and hairline separators are drawn. */
    val lineStyle: LineStyle,
    /** Whether backgrounds of editing surfaces carry a faint dot grid. */
    val dotMotif: Boolean,
)

object Looks {
    val Dot = Look(
        id = "dot",
        label = "Dot",
        font = Fonts.Doto,
        displayWeight = FontWeight.Bold,
        displayWeightSmall = FontWeight.SemiBold,
        displayLetterSpacing = 0.sp,
        lineStyle = LineStyle.DOTTED,
        dotMotif = true,
    )

    val Clean = Look(
        id = "clean",
        label = "Clean",
        font = Fonts.Geist,
        displayWeight = FontWeight.ExtraLight,
        displayWeightSmall = FontWeight.Light,
        displayLetterSpacing = (-0.02).em,
        lineStyle = LineStyle.SOLID,
        dotMotif = false,
    )

    val all: List<Look> = listOf(Dot, Clean)

    fun byId(id: String?): Look = all.firstOrNull { it.id == id } ?: Dot
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.nulis.launcher.R

/** Doto (SIL OFL): dot-matrix display face. Variable weight axis driven by [FontWeight]. */
val DotoFontFamily = FontFamily(
    Font(R.font.doto, FontWeight.Normal),
    Font(R.font.doto, FontWeight.Medium),
    Font(R.font.doto, FontWeight.SemiBold),
    Font(R.font.doto, FontWeight.Bold),
    Font(R.font.doto, FontWeight.ExtraBold),
)

/** Geist (SIL OFL): body face, and the display face of the Clean look. */
val GeistFontFamily = FontFamily(
    Font(R.font.geist, FontWeight.Thin),
    Font(R.font.geist, FontWeight.ExtraLight),
    Font(R.font.geist, FontWeight.Light),
    Font(R.font.geist, FontWeight.Normal),
    Font(R.font.geist, FontWeight.Medium),
    Font(R.font.geist, FontWeight.SemiBold),
    Font(R.font.geist, FontWeight.Bold),
)

/** Geist Mono (SIL OFL): small uppercase labels and readouts. */
val GeistMonoFontFamily = FontFamily(
    Font(R.font.geist_mono, FontWeight.Light),
    Font(R.font.geist_mono, FontWeight.Normal),
    Font(R.font.geist_mono, FontWeight.Medium),
    Font(R.font.geist_mono, FontWeight.SemiBold),
)

private val VariableWeights = listOf(
    FontWeight.Thin, FontWeight.ExtraLight, FontWeight.Light, FontWeight.Normal,
    FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold, FontWeight.ExtraBold,
)

private fun variable(resId: Int) = FontFamily(VariableWeights.map { Font(resId, it) })

/** Noto Sans (SIL OFL): a neutral grotesk, wider and warmer than Geist. */
val NotoSansFontFamily = variable(R.font.noto_sans)

/** Noto Serif (SIL OFL): the serif, for the paper-quiet themes. */
val NotoSerifFontFamily = variable(R.font.noto_serif)

/** Noto Sans Mono (SIL OFL): a typewriter mono, squarer than Geist Mono. */
val NotoSansMonoFontFamily = variable(R.font.noto_sans_mono)

/** What a face is for, so the picker can group them and a theme can ask for a kind. */
enum class FontCategory { DISPLAY, GROTESK, MONO, SERIF }

/**
 * One bundled typeface. Every face here is SIL OFL and its licence text is in
 * `assets/licenses`; Nulis never loads a font from anywhere else.
 */
@Immutable
data class NulisFont(
    val id: String,
    val label: String,
    val family: FontFamily,
    val category: FontCategory,
    /** How this face reads at a glance. Shown under its name in the picker. */
    val note: String,
    /** Extra tracking this face wants at display sizes, in em. */
    val displayTracking: Float = 0f,
    /** The lightest weight this face is worth using for giant digits. */
    val lightestDisplayWeight: FontWeight = FontWeight.Light,
    /** The file in `assets/licenses` carrying this face's SIL OFL text. */
    val licenseAsset: String = "licenses/noto-OFL.txt",
)

object Fonts {
    val Doto = NulisFont("doto", "Doto", DotoFontFamily, FontCategory.DISPLAY, "Dot matrix", lightestDisplayWeight = FontWeight.Medium, licenseAsset = "licenses/doto-OFL.txt")
    val Geist = NulisFont("geist", "Geist", GeistFontFamily, FontCategory.GROTESK, "Modern grotesk", displayTracking = -0.02f, lightestDisplayWeight = FontWeight.ExtraLight, licenseAsset = "licenses/geist-OFL.txt")
    val GeistMono = NulisFont("geist_mono", "Geist Mono", GeistMonoFontFamily, FontCategory.MONO, "Terminal", lightestDisplayWeight = FontWeight.Light, licenseAsset = "licenses/geist-mono-OFL.txt")
    val NotoSans = NulisFont("noto_sans", "Noto Sans", NotoSansFontFamily, FontCategory.GROTESK, "Neutral, wide", displayTracking = -0.01f)
    val NotoSerif = NulisFont("noto_serif", "Noto Serif", NotoSerifFontFamily, FontCategory.SERIF, "Bookish", displayTracking = -0.01f)
    val NotoSansMono = NulisFont("noto_sans_mono", "Noto Mono", NotoSansMonoFontFamily, FontCategory.MONO, "Typewriter")

    val all: List<NulisFont> = listOf(Doto, Geist, NotoSans, NotoSerif, GeistMono, NotoSansMono)

    fun byId(id: String?): NulisFont? = all.firstOrNull { it.id == id }
}

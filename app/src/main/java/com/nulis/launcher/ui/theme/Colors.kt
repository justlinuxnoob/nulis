// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import kotlinx.serialization.Serializable

/** BLACK and WHITE are the two pure presets; CUSTOM builds every role from one user-chosen background. */
@Serializable
enum class ColorTheme {
    BLACK,
    WHITE,
    CUSTOM,

    /** Black while the phone is in dark mode, white while it is not. */
    AUTO,
}

/**
 * Color tokens. Both themes are built from the same roles so every component works in either.
 *
 * [accent] is used very sparingly - selected states and tiny indicator dots - and is the one
 * role the user may choose. [danger] is not: it stays red whatever the accent is, because
 * "delete this" and "this is the selected one" must never look the same. Both are pushed to at
 * least 3:1 against the background, so neither can disappear on a dark red or a mid grey.
 */
@Immutable
data class NulisColors(
    val background: Color,
    /** Slightly raised surface for cards and sheets. */
    val surface: Color,
    /** One step above [surface]: tiles inside cards, toggle tracks. */
    val surfaceRaised: Color,
    val onBackground: Color,
    /** Secondary text. */
    val secondary: Color,
    /** Tertiary text, disabled glyphs, dotted dividers. */
    val tertiary: Color,
    /** 1dp low-contrast borders. */
    val hairline: Color,
    val accent: Color,
    /** Destructive actions. Always red, never the chosen accent. */
    val danger: Color,
    val isDark: Boolean,
)

val BlackColors = NulisColors(
    background = Color(0xFF000000),
    surface = Color(0xFF0B0B0B),
    surfaceRaised = Color(0xFF161616),
    onBackground = Color(0xFFFFFFFF),
    secondary = Color(0xFF8E8E8E),
    // 3.2:1. It was 0xFF585858, under 3:1, and it is the colour of every caption and hint.
    tertiary = Color(0xFF5E5E5E),
    hairline = Color(0xFF232323),
    accent = Color(0xFFFF3B30),
    danger = Color(0xFFFF3B30),
    isDark = true,
)

val WhiteColors = NulisColors(
    background = Color(0xFFFFFFFF),
    surface = Color(0xFFF6F6F6),
    surfaceRaised = Color(0xFFECECEC),
    onBackground = Color(0xFF000000),
    secondary = Color(0xFF6B6B6B),
    // 3.0:1. It was 0xFFA6A6A6, 2.4:1.
    tertiary = Color(0xFF949494),
    hairline = Color(0xFFE2E2E2),
    accent = Color(0xFFE0261C),
    danger = Color(0xFFE0261C),
    isDark = false,
)

fun colorsFor(
    theme: ColorTheme,
    customBackground: Color = DefaultCustomBackground,
    customInk: Color? = null,
    /** The user's own accent, or null for the one the theme ships with. */
    customAccent: Color? = null,
    /** Whether the phone is in dark mode right now; only [ColorTheme.AUTO] asks. */
    systemDark: Boolean = true,
): NulisColors {
    val base = when (theme) {
        ColorTheme.BLACK -> BlackColors
        ColorTheme.WHITE -> WhiteColors
        ColorTheme.AUTO -> if (systemDark) BlackColors else WhiteColors
        ColorTheme.CUSTOM -> customColors(customBackground, customInk)
    }
    if (customAccent == null) return base
    return base.copy(accent = adaptAccent(base.background, customAccent))
}

/** A deep blue-black, so switching to Custom looks intentional before the user picks anything. */
val DefaultCustomBackground = Color(0xFF14213D)

/**
 * Derives the full role set from one background, and optionally from a chosen ink.
 *
 * Without an ink, text is white or black, whichever has the higher WCAG contrast against the
 * background (white wins below luminance 0.179). With one - a terminal green, an amber - that
 * colour becomes the text and every other role is still a mix of it into the background, so a
 * coloured theme keeps exactly the same hierarchy as the two pure ones.
 */
fun customColors(background: Color, ink: Color? = null): NulisColors {
    val dark = background.luminance() < 0.179f
    val text = ink ?: if (dark) Color.White else Color.Black
    fun mix(amount: Float) = lerp(background, text, amount)
    return NulisColors(
        background = background,
        surface = mix(0.05f),
        surfaceRaised = mix(0.10f),
        onBackground = text,
        secondary = mix(if (dark) 0.55f else 0.60f),
        tertiary = atLeast(mix(if (dark) 0.34f else 0.38f), text, background, MinCaptionContrast),
        hairline = mix(0.14f),
        accent = adaptAccent(background, if (dark) BlackColors.accent else WhiteColors.accent),
        danger = adaptAccent(background, if (dark) BlackColors.danger else WhiteColors.danger),
        isDark = dark,
    )
}

/**
 * Captions are text, however quiet: never below 3:1, the WCAG floor for text large enough to
 * read at a glance, whatever the background.
 */
private const val MinCaptionContrast = 3f

/**
 * [color], moved towards [ink] only as far as it takes to reach [min] against [background]. A
 * colour that already clears it is returned untouched, so this never flattens a hierarchy that
 * was already legible.
 */
fun atLeast(color: Color, ink: Color, background: Color, min: Float): Color {
    if (contrastRatio(color, background) >= min) return color
    var amount = 0f
    var candidate = color
    while (amount < 1f && contrastRatio(candidate, background) < min) {
        amount += 0.02f
        candidate = lerp(color, ink, amount.coerceAtMost(1f))
    }
    return candidate
}

/**
 * The same roles, readable by anybody: captions at the 4.5:1 WCAG asks of body text, secondary
 * text at 7:1, and hairlines and outlines strong enough to find. The hierarchy survives - each
 * role is still quieter than the one above it - it is simply all turned up.
 */
fun NulisColors.withHigherContrast(): NulisColors = copy(
    secondary = atLeast(secondary, onBackground, background, 7f),
    tertiary = atLeast(tertiary, onBackground, background, 4.5f),
    hairline = atLeast(hairline, onBackground, background, 1.8f),
)

/** WCAG contrast ratio between two opaque colors, 1.0 (identical) to 21.0 (black on white). */
fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}

/** Accent dots and destructive labels must stay legible; 3:1 is the WCAG minimum for a graphic. */
private const val MinAccentContrast = 3f

/**
 * Keeps an accent readable on any background. Accents are drawn as 6dp indicator dots and as
 * destructive labels, so on a background close to their own colour they would simply disappear.
 *
 * Only the lightness moves, never the hue: an accent is a name for a meaning - selected, or
 * destructive, or the user's own colour - and one that drifted from green to yellow would stop
 * being that name. A background the accent already reads well against is left exactly as it is;
 * otherwise the lightness steps outwards from the original until it clears
 * [MinAccentContrast], taking the nearest value that does.
 */
fun adaptAccent(background: Color, base: Color): Color {
    if (contrastRatio(base, background) >= MinAccentContrast) return base
    val hsl = base.toHsl()
    // A washed-out accent would be legible but would no longer read as the accent.
    val saturation = maxOf(hsl[1], 0.62f)
    var best = base
    var bestContrast = contrastRatio(base, background)
    var step = 0.02f
    while (step <= 0.9f) {
        for (lightness in listOf(hsl[2] + step, hsl[2] - step)) {
            if (lightness !in 0.08f..0.96f) continue
            val candidate = Color.hsl(hsl[0], saturation, lightness)
            val contrast = contrastRatio(candidate, background)
            if (contrast >= MinAccentContrast) return candidate
            if (contrast > bestContrast) {
                bestContrast = contrast
                best = candidate
            }
        }
        step += 0.02f
    }
    // No red at any lightness clears the minimum: the most contrasting one is still the best answer.
    return best
}

/** Hue (0-360), saturation and lightness (0-1) of an opaque color. */
fun Color.toHsl(): FloatArray {
    val r = red; val g = green; val b = blue
    val max = maxOf(r, g, b); val min = minOf(r, g, b)
    val l = (max + min) / 2f
    if (max == min) return floatArrayOf(0f, 0f, l)
    val d = max - min
    val s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
    val h = when (max) {
        r -> ((g - b) / d + (if (g < b) 6f else 0f)) * 60f
        g -> ((b - r) / d + 2f) * 60f
        else -> ((r - g) / d + 4f) * 60f
    }
    return floatArrayOf(h, s, l)
}

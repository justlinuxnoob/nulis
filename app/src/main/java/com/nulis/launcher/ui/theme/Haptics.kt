// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.theme

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/**
 * Haptic vocabulary, mapped onto the platform constants so every device plays its native
 * waveform. Light ticks for choosing, firmer clicks for lifting, a confirm for finishing.
 */
object NulisHaptics {
    /** Choosing an option, changing a slot. Light. */
    val tick = HapticFeedbackType.SegmentTick

    /** Rapid ticks while sliding along a scale, e.g. the A-Z rail. Lighter still. */
    val frequentTick = HapticFeedbackType.SegmentFrequentTick

    /** A long press that lifts something off the page. Firm click. */
    val pickup = HapticFeedbackType.LongPress

    /** A long press that opens a menu. */
    val longPress = HapticFeedbackType.LongPress

    /** Letting go of something that was lifted. */
    val drop = HapticFeedbackType.GestureEnd

    /** A drawer or sheet committing to open. */
    val threshold = HapticFeedbackType.GestureThresholdActivate

    /** Finishing an edit: Done buttons. */
    val confirm = HapticFeedbackType.Confirm

    /** A move that will not go through: the drop that has nowhere to land. */
    val reject = HapticFeedbackType.Reject

    val toggleOn = HapticFeedbackType.ToggleOn
    val toggleOff = HapticFeedbackType.ToggleOff
}

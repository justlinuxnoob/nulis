// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.notifications

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Which apps have something waiting.
 *
 * A dot and nothing else: no count, no sender, no preview, no history. The set is a set of
 * package names held in memory for as long as the launcher process lives and written nowhere,
 * and Nulis has no internet permission to send it anywhere even if it wanted to.
 *
 * It is filled by the notification listener Nulis already has for the music block - the same
 * service, the same one grant, the same explanation screen - because asking twice for the same
 * permission to do two different things is how an app loses somebody's trust.
 *
 * A plain object rather than something injected: the listener is constructed by the system, not
 * by us, so there is nowhere to hand it a dependency.
 */
object NotificationDots {

    private val _packages = MutableStateFlow<Set<String>>(emptySet())

    /** Packages with at least one notification a person could act on. Empty when access is off. */
    val packages: StateFlow<Set<String>> = _packages.asStateFlow()

    fun update(packages: Set<String>) {
        if (_packages.value != packages) _packages.value = packages
    }

    fun clear() = update(emptySet())
}

/**
 * The dots an app list should draw right now: already filtered by the setting and by whether
 * access was ever granted, so a drawing composable only has to ask whether a package is in it.
 */
val LocalNotificationDots = staticCompositionLocalOf { emptySet<String>() }

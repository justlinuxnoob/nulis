// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.data

import android.util.Log
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences

/**
 * What to do when one of Nulis's files has been damaged - a half-written page after a power cut,
 * a file copied badly by a backup tool, a disk that lied.
 *
 * Without this, DataStore throws on every read for the rest of the install and the launcher is
 * unusable. With it, the damaged file is replaced by an empty one, that repository falls back to
 * its own defaults, and the user loses one file rather than their home screen. Every store in
 * Nulis uses it.
 */
fun replaceCorrupted(name: String) = ReplaceFileCorruptionHandler<Preferences> { error ->
    Log.w("NulisData", "The $name file was unreadable and has been reset", error)
    emptyPreferences()
}

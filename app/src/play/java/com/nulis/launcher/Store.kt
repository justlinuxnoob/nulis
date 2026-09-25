// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

/** The Google Play build. */
object Store {
    /**
     * None: Google Play's payments policy allows no tip or donation link outside Play's own
     * billing, so this build does not contain one at all - not hidden, simply absent.
     */
    val donateUrl: String? = null
}

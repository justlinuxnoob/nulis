// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

/** The build published on GitHub releases. */
object Store {
    /** Replace with the real page before the first GitHub release. */
    const val BUY_ME_A_COFFEE_URL = "https://buymeacoffee.com/REPLACE_ME"

    /** Where somebody who wants to say thanks can. Shown in About; the Play build has none. */
    val donateUrl: String? = BUY_ME_A_COFFEE_URL
}

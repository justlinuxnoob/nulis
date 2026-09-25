// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.settings

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.graphics.Color
import com.nulis.launcher.ui.theme.Palette
import com.nulis.launcher.ui.theme.wallpaperPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The colours Android has already worked out for the home-screen wallpaper.
 *
 * No permission and no image: the system computes these for every app that asks, and Nulis only
 * asks when Settings opens. Android 8.1 and up; before that there is simply no wallpaper palette.
 */
class WallpaperColorsRepository(private val context: Context) {

    suspend fun palette(): Palette? = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return@withContext null
        val colors = runCatching {
            WallpaperManager.getInstance(context).getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
        }.getOrNull() ?: return@withContext null
        val light = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0
        wallpaperPalette(
            primary = Color(colors.primaryColor.toArgb()),
            secondary = colors.secondaryColor?.let { Color(it.toArgb()) },
            light = light,
        )
    }
}

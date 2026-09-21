// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nulis.launcher.BuildConfig
import com.nulis.launcher.R
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Hairline
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.theme.Fonts
import com.nulis.launcher.ui.theme.NulisTheme

/**
 * What Nulis is, and the one claim worth being able to check: it does not hold the internet
 * permission, so the operating system will not let it open a socket even if the code tried.
 * Stated here rather than only in a file nobody reads, and phrased so anybody can verify it.
 */
@Composable
fun AboutSection(onLicenses: () -> Unit) {
    val colors = NulisTheme.colors
    val type = NulisTheme.type
    val packageName = LocalContext.current.packageName
    val styles = BlockRegistry.definitions.sumOf { it.styles.size }
    Column(Modifier.fillMaxWidth()) {
        SectionLabel(stringResource(R.string.about_what))
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_blurb),
            style = type.bodyM,
            color = colors.secondary,
        )
        Spacer(Modifier.height(16.dp))
        Hairline()
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.about_privacy))
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_privacy_body),
            style = type.bodyM,
            color = colors.secondary,
        )
        Spacer(Modifier.height(8.dp))
        Caption(stringResource(R.string.about_privacy_check, packageName), lines = 3)
        Spacer(Modifier.height(16.dp))
        ListRow(
            title = stringResource(R.string.settings_font_licenses),
            subtitle = pluralStringResource(R.plurals.about_fonts, Fonts.all.size, Fonts.all.size),
            onClick = onLicenses,
        )
        ListRow(
            title = stringResource(R.string.about_version),
            subtitle = BuildConfig.VERSION_NAME,
            divider = false,
        )
        Spacer(Modifier.height(16.dp))
        Caption(
            pluralStringResource(R.plurals.about_block_types, BlockRegistry.definitions.size, BlockRegistry.definitions.size) +
                ", " + pluralStringResource(R.plurals.about_block_styles, styles, styles),
            lines = 2,
        )
    }
}

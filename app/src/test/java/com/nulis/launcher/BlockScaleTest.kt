// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import androidx.compose.ui.unit.dp
import com.nulis.launcher.home.blockScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlockScaleTest {

    @Test
    fun every_phone_draws_blocks_exactly_as_before() {
        // A small phone, a Pixel-sized one, a big one, and a foldable's inner screen.
        listOf(46.dp to 52.dp, 54.dp to 64.dp, 64.dp to 70.dp, 78.dp to 57.dp).forEach { (w, h) ->
            assertEquals("cell $w x $h", 1f, blockScale(w, h))
        }
    }

    @Test
    fun a_tablet_grows_its_blocks_by_the_smaller_ratio() {
        val scale = blockScale(110.dp, 91.dp)
        assertEquals(91f / 72f, scale, 0.01f)
    }

    @Test
    fun a_landscape_tablet_with_short_cells_stays_at_one() {
        assertEquals(1f, blockScale(140.dp, 60.dp))
    }

    @Test
    fun growth_is_capped() {
        assertTrue(blockScale(400.dp, 400.dp) <= 2f)
    }
}

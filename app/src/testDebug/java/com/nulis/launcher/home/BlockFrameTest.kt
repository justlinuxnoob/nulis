// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nulis.launcher.blocks.BlockAlign
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** A block never breaks a word to fit; it shrinks instead, and sentences still wrap. */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class BlockFrameTest {

    @get:Rule
    val compose = createComposeRule()

    private fun lines(text: String, width: Int, fontSize: Int): Int {
        var result: TextLayoutResult? = null
        val area = DpSize(width.dp, 60.dp)
        compose.setContent {
            Box(Modifier.size(area)) {
                BlockFrame(area = area, align = BlockAlign.CENTER) {
                    Column { Text(text, fontSize = fontSize.sp, onTextLayout = { result = it }) }
                }
            }
        }
        compose.waitForIdle()
        return result!!.lineCount
    }

    @Test
    fun a_number_wider_than_its_block_stays_on_one_line() {
        assertEquals(1, lines("5,240", width = 60, fontSize = 48))
    }

    @Test
    fun a_sentence_still_wraps_between_words() {
        assertEquals(true, lines("Slow morning on the train", width = 120, fontSize = 16) > 1)
    }
}

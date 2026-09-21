// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher

import com.nulis.launcher.blocks.calculator.Expression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpressionTest {

    private fun eval(input: String): Double? = Expression.evaluate(input)

    private fun assertClose(expected: Double, input: String) {
        val actual = eval(input) ?: error("\"$input\" did not evaluate")
        assertTrue("$input gave $actual, expected about $expected", Math.abs(actual - expected) < 1e-6)
    }

    @Test
    fun `four operations and precedence`() {
        assertClose(14.0, "2+3*4")
        assertClose(20.0, "(2+3)*4")
        assertClose(2.5, "10/4")
        assertClose(-1.0, "3-4")
        assertClose(6.0, "2 * 3")
    }

    @Test
    fun `powers are right associative`() {
        assertClose(1024.0, "2^10")
        // 2^(3^2) = 512, not (2^3)^2 = 64.
        assertClose(512.0, "2^3^2")
    }

    @Test
    fun `unary minus and parentheses`() {
        assertClose(-5.0, "-5")
        assertClose(5.0, "-(-5)")
        assertClose(1.0, "-1+2")
    }

    @Test
    fun `percent is a hundredth`() {
        assertClose(0.15, "15%")
        assertClose(30.0, "200*15%")
    }

    @Test
    fun `thousands separators are ignored and decimals are points`() {
        assertClose(1200.5, "1,200.5")
    }

    @Test
    fun `constants`() {
        assertClose(Math.PI, "pi")
        assertClose(Math.PI * 2, "pi*2")
    }

    @Test
    fun `length conversions`() {
        assertClose(7.456454, "12 km in mi")
        assertClose(2.54, "1 in in cm")
        assertClose(1000.0, "1 km in m")
    }

    @Test
    fun `temperature conversions use their offsets`() {
        assertClose(86.0, "30 c in f")
        assertClose(37.777777, "100 f in c")
        assertClose(273.15, "0 c in k")
        assertClose(26.85, "300 k in c")
    }

    @Test
    fun `mass and data conversions`() {
        assertClose(2.204622, "1 kg in lb")
        assertClose(1024.0, "1 mb in kb")
    }

    @Test
    fun `conversions accept an expression on the left`() {
        assertClose(2.0, "1+1 km in km")
    }

    @Test
    fun `different families do not convert`() {
        assertNull(eval("1 kg in km"))
    }

    @Test
    fun `plain words are not sums`() {
        assertNull(eval("spotify"))
        assertNull(eval("messages"))
        assertNull(eval(""))
        assertNull(eval("   "))
    }

    @Test
    fun `broken input returns nothing rather than throwing`() {
        assertNull(eval("2+"))
        assertNull(eval("(2+3"))
        assertNull(eval("2**3"))
        assertNull(eval("1/0"))
    }

    @Test
    fun `formatting drops trailing zeros and groups thousands`() {
        assertEquals("14", Expression.format(14.0))
        assertEquals("1,024", Expression.format(1024.0))
        assertTrue(Expression.format(2.5).startsWith("2.5"))
    }
}

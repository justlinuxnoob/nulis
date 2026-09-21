// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.calculator

import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * A small recursive-descent evaluator for the arithmetic a person types on a phone: the four
 * operations, powers, parentheses, percentages, a handful of unit conversions and a few named
 * constants. Deliberately tiny and deliberately offline - there is no library here and nothing
 * is looked up anywhere.
 *
 * Returns null for anything it does not understand, so a caller (the drawer's search field)
 * can simply not show a result rather than showing an error.
 */
object Expression {

    private val constants = mapOf(
        "pi" to Math.PI,
        "e" to Math.E,
    )

    /**
     * Length, mass, temperature and data units, expressed against one base unit per family.
     * `5km in mi` works because both are in the same family.
     */
    private data class Unit(val family: String, val factor: Double, val offset: Double = 0.0)

    private val units: Map<String, Unit> = buildMap {
        fun put(unit: Unit, vararg names: String) = names.forEach { put(it, unit) }
        // Length, base metre.
        put(Unit("len", 0.001), "mm")
        put(Unit("len", 0.01), "cm")
        put(Unit("len", 1.0), "m", "metre", "meter", "metres", "meters")
        put(Unit("len", 1000.0), "km")
        put(Unit("len", 0.0254), "in", "inch", "inches")
        put(Unit("len", 0.3048), "ft", "foot", "feet")
        put(Unit("len", 0.9144), "yd", "yard", "yards")
        put(Unit("len", 1609.344), "mi", "mile", "miles")
        // Mass, base kilogram.
        put(Unit("mass", 0.001), "g", "gram", "grams")
        put(Unit("mass", 1.0), "kg", "kilo", "kilos")
        put(Unit("mass", 0.45359237), "lb", "lbs", "pound", "pounds")
        put(Unit("mass", 0.028349523), "oz", "ounce", "ounces")
        put(Unit("mass", 1000.0), "t", "tonne", "tonnes")
        // Data, base byte.
        put(Unit("data", 1.0), "b", "byte", "bytes")
        put(Unit("data", 1024.0), "kb")
        put(Unit("data", 1024.0 * 1024), "mb")
        put(Unit("data", 1024.0 * 1024 * 1024), "gb")
        put(Unit("data", 1024.0 * 1024 * 1024 * 1024), "tb")
        // Temperature, base Celsius, with offsets.
        put(Unit("temp", 1.0), "c", "celsius")
        put(Unit("temp", 5.0 / 9.0, offset = -32.0), "f", "fahrenheit")
        put(Unit("temp", 1.0, offset = -273.15), "k", "kelvin")
    }

    /** The answer to [input], or null when it is not arithmetic at all. */
    fun evaluate(input: String): Double? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        // "12 km in miles" / "30c to f": convert, then fall through to plain arithmetic.
        conversion(trimmed)?.let { return it }
        // Something with no digit in it is a search, not a sum.
        if (trimmed.none { it.isDigit() } && constants.keys.none { trimmed.contains(it) }) return null
        return runCatching { Parser(trimmed).parse() }.getOrNull()?.takeIf { it.isFinite() }
    }

    /** A result rendered the way a person would write it: no trailing zeros, thousands grouped. */
    fun format(value: Double): String {
        val rounded = (value * 1e10).roundToLong() / 1e10
        return if (abs(rounded) < 1e15 && rounded == rounded.toLong().toDouble()) {
            "%,d".format(Locale.getDefault(), rounded.toLong())
        } else {
            "%,.6f".format(Locale.getDefault(), rounded).trimEnd('0').trimEnd('.', ',')
        }
    }

    private val conversionRegex = Regex(
        """^(.+?)\s*([a-zA-Z°]+)\s+(?:in|to|as)\s+([a-zA-Z°]+)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private fun conversion(input: String): Double? {
        val match = conversionRegex.matchEntire(input) ?: return null
        val amount = runCatching { Parser(match.groupValues[1]).parse() }.getOrNull() ?: return null
        val from = units[match.groupValues[2].lowercase().removePrefix("°")] ?: return null
        val to = units[match.groupValues[3].lowercase().removePrefix("°")] ?: return null
        if (from.family != to.family) return null
        // Offsets come first on the way in and last on the way out, which is what makes
        // Fahrenheit and Kelvin behave.
        val base = (amount + from.offset) * from.factor
        return base / to.factor - to.offset
    }

    /**
     * expression := term (('+' | '-') term)*
     * term       := power (('*' | '/' | '%') power)*
     * power      := unary ('^' power)?
     * unary      := ('-' | '+')? primary ('%')?
     * primary    := number | constant | '(' expression ')'
     */
    private class Parser(private val source: String) {
        private var position = 0

        fun parse(): Double {
            val value = expression()
            skipSpace()
            require(position >= source.length) { "Trailing input at $position" }
            return value
        }

        private fun expression(): Double {
            var value = term()
            while (true) {
                skipSpace()
                when (peek()) {
                    '+' -> { position++; value += term() }
                    '-' -> { position++; value -= term() }
                    else -> return value
                }
            }
        }

        private fun term(): Double {
            var value = power()
            while (true) {
                skipSpace()
                when (peek()) {
                    '*', '×' -> { position++; value *= power() }
                    '/', '÷' -> { position++; value /= power() }
                    else -> return value
                }
            }
        }

        private fun power(): Double {
            val base = unary()
            skipSpace()
            return if (peek() == '^') {
                position++
                base.pow(power())
            } else {
                base
            }
        }

        private fun unary(): Double {
            skipSpace()
            val value = when (peek()) {
                '-' -> { position++; -unary() }
                '+' -> { position++; unary() }
                else -> primary()
            }
            skipSpace()
            // A trailing % means "of one hundred", which is how it is meant on a keypad.
            return if (peek() == '%') {
                position++
                value / 100.0
            } else {
                value
            }
        }

        private fun primary(): Double {
            skipSpace()
            val ch = peek() ?: throw IllegalArgumentException("Unexpected end")
            if (ch == '(') {
                position++
                val value = expression()
                skipSpace()
                require(peek() == ')') { "Missing )" }
                position++
                return value
            }
            if (ch.isDigit() || ch == '.' || ch == ',') {
                val start = position
                while (position < source.length && (source[position].isDigit() || source[position] == '.' || source[position] == ',')) position++
                // Commas are thousands separators here; a decimal point is a point.
                val text = source.substring(start, position).replace(",", "")
                return text.toDouble()
            }
            if (ch.isLetter()) {
                val start = position
                while (position < source.length && source[position].isLetter()) position++
                val name = source.substring(start, position).lowercase()
                return constants[name] ?: throw IllegalArgumentException("Unknown name $name")
            }
            throw IllegalArgumentException("Unexpected '$ch'")
        }

        private fun peek(): Char? = source.getOrNull(position)

        private fun skipSpace() {
            while (position < source.length && source[position].isWhitespace()) position++
        }
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.clock

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.text.font.FontWeight
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.ui.components.LocalPreviewStill
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** How big a clock is drawn, on top of whatever the style and the block size already decide. */
enum class ClockScale(val factor: Float, val label: String) {
    S(0.7f, "Small"),
    M(1f, "Normal"),
    L(1.35f, "Large"),
    XL(1.75f, "Huge"),
}

/** How heavy the digits are. The Look picks the face; this picks the stroke inside it. */
enum class ClockWeight(val weight: FontWeight, val label: String) {
    LIGHT(FontWeight.Light, "Light"),
    REGULAR(FontWeight.Normal, "Regular"),
    MEDIUM(FontWeight.Medium, "Medium"),
    BOLD(FontWeight.Bold, "Bold"),
}

/**
 * A short, curated list of second time zones. Offered as cities rather than zone ids, because
 * "Europe/Vilnius" is a database key and "Vilnius" is a place someone you know lives.
 */
val SecondZones: List<Pair<String, String>> = listOf(
    "UTC" to "UTC",
    "Europe/London" to "London",
    "Europe/Vilnius" to "Vilnius",
    "Europe/Berlin" to "Berlin",
    "Europe/Lisbon" to "Lisbon",
    "America/New_York" to "New York",
    "America/Chicago" to "Chicago",
    "America/Los_Angeles" to "Los Angeles",
    "America/Sao_Paulo" to "Sao Paulo",
    "Asia/Dubai" to "Dubai",
    "Asia/Kolkata" to "Kolkata",
    "Asia/Shanghai" to "Shanghai",
    "Asia/Tokyo" to "Tokyo",
    "Australia/Sydney" to "Sydney",
    "Pacific/Auckland" to "Auckland",
)

/** Everything a clock block remembers besides its style and size. */
data class ClockSettings(
    val hour24: Boolean = true,
    val seconds: Boolean = false,
    val scale: ClockScale = ClockScale.M,
    val weight: ClockWeight? = null,
    /** Zone id for the Dual style's second readout. */
    val secondZone: String = "UTC",
) {
    val secondZoneLabel: String get() = SecondZones.firstOrNull { it.first == secondZone }?.second ?: secondZone

    fun toMap(): Map<String, String> = mapOf(
        KEY_24 to hour24.toString(),
        KEY_SECONDS to seconds.toString(),
        KEY_SCALE to scale.name,
        KEY_WEIGHT to (weight?.name ?: ""),
        KEY_ZONE to secondZone,
    )

    companion object {
        const val KEY_24 = "hour24"
        const val KEY_SECONDS = "seconds"
        const val KEY_SCALE = "scale"
        const val KEY_WEIGHT = "weight"
        const val KEY_ZONE = "zone2"

        fun from(settings: Map<String, String>): ClockSettings {
            val defaults = ClockSettings()
            return ClockSettings(
                hour24 = settings[KEY_24]?.toBooleanStrictOrNull() ?: defaults.hour24,
                seconds = settings[KEY_SECONDS]?.toBooleanStrictOrNull() ?: defaults.seconds,
                scale = settings[KEY_SCALE]?.let { name -> ClockScale.entries.firstOrNull { it.name == name } } ?: defaults.scale,
                weight = settings[KEY_WEIGHT]?.let { name -> ClockWeight.entries.firstOrNull { it.name == name } },
                secondZone = settings[KEY_ZONE]?.takeIf { it.isNotBlank() } ?: defaults.secondZone,
            )
        }
    }
}

fun clockSettings(block: Block): ClockSettings = ClockSettings.from(block.settings)

fun Block.withClock(settings: ClockSettings): Block = copy(settings = this.settings + settings.toMap())

/** The block size's own contribution to the type size, before [ClockScale]. */
fun sizeFactor(block: Block): Float = if (block.size == BlockSize.WIDE) 1f else 0.6f

/** Type size for a clock: the style's own base, the block size and the user's scale. */
fun clockScale(block: Block, settings: ClockSettings): Float = sizeFactor(block) * settings.scale.factor

/**
 * The time, ticking once a second when [seconds] is on and once a minute otherwise. Frozen
 * inside a still preview, so a wall of page miniatures wakes nothing up.
 *
 * @param fallback the page's own minute-resolution time, used when this clock needs nothing more.
 */
@Composable
fun rememberTickingTime(fallback: LocalDateTime, seconds: Boolean): LocalDateTime {
    val still = LocalPreviewStill.current
    if (!seconds || still) return fallback
    val time by produceState(initialValue = LocalDateTime.now(), key1 = Unit) {
        while (true) {
            value = LocalDateTime.now()
            delay(1_000L - (System.currentTimeMillis() % 1_000L) + 10L)
        }
    }
    return time
}

/** The same instant in another zone, recomputed only when the minute (or the zone) changes. */
@Composable
fun rememberZoned(time: LocalDateTime, zoneId: String): ZonedDateTime? {
    val zone = remember(zoneId) { runCatching { ZoneId.of(zoneId) }.getOrNull() } ?: return null
    return remember(time, zone) { time.atZone(ZoneId.systemDefault()).withZoneSameInstant(zone) }
}

/** "14" or "2", following the block's 12/24-hour setting. */
fun hourText(hour: Int, hour24: Boolean): String = if (hour24) {
    "%02d".format(hour)
} else {
    val h = hour % 12
    (if (h == 0) 12 else h).toString()
}

/** "AM" / "PM", or null in 24-hour mode. */
fun meridiem(hour: Int, hour24: Boolean): String? = if (hour24) null else if (hour < 12) "AM" else "PM"

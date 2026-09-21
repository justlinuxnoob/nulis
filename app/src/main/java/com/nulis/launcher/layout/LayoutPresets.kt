// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.layout

import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockAlign
import com.nulis.launcher.blocks.BlockRegistry
import com.nulis.launcher.blocks.GridRect
import com.nulis.launcher.blocks.PageDensity
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.blocks.PageLayout
import com.nulis.launcher.blocks.PagesConfig
import com.nulis.launcher.blocks.apps.AppsBlockDefinition
import com.nulis.launcher.blocks.clock.ClockScale
import com.nulis.launcher.blocks.clock.ClockSettings
import com.nulis.launcher.blocks.clock.ClockWeight
import com.nulis.launcher.blocks.glance.GlanceSettings
import com.nulis.launcher.blocks.glance.GlanceSlot
import com.nulis.launcher.blocks.music.MusicBlockDefinition
import com.nulis.launcher.icons.IconColor
import com.nulis.launcher.icons.IconMode
import com.nulis.launcher.icons.IconShape
import com.nulis.launcher.icons.IconSize
import com.nulis.launcher.icons.IconStyle

/**
 * A ready-made arrangement of pages, and nothing else.
 *
 * A layout is deliberately smaller than the look: it says what is on each page and exactly where,
 * and says nothing at all about the Look, the colours or the typefaces. So any layout works with
 * any look - Terminal's pages on a white paper palette if that is what you want - and changing
 * one never quietly changes the other.
 *
 * A layout also says **how many pages there are**, because a page is exactly one screen and "how
 * much room is there" is part of an arrangement. Just a clock ships one page; everything else
 * ships three.
 *
 * ### The rules every one of these keeps
 *
 * - **Nothing is said twice**, on a page or on the page next door. A greeting carries the date,
 *   so a page with a date block uses the plain greeting. The weekly card carries today's steps
 *   and screen time, so it never sits beside either of them. A glance carrying a slot means no
 *   block for that slot within a swipe.
 * - **The whole screen is used**, or the emptiness is the point (Just a clock).
 * - **Nothing is cramped**: every block gets at least the rectangle its own content needs, and
 *   the pages below were checked one at a time on a phone rather than reasoned about.
 */
data class LayoutPreset(
    val id: String,
    val name: String,
    /** One line under the name in the picker. */
    val tagline: String,
    /** Page id -> layout, in swipe order. */
    val pages: Map<String, PageLayout>,
    /** Which of [pages] carries the home mark. */
    val homeId: String = PageIds.HOME,
) {
    fun page(pageId: String): PageLayout = pages[pageId] ?: PageLayout(pageId)

    val order: List<String> get() = pages.keys.toList()

    val config: PagesConfig get() = PagesConfig(order, homeId).sane()
}

object LayoutPresets {

    /** What a fresh install gets: a clock, the date, and the apps you actually open. */
    val Minimal = LayoutPreset(
        id = "minimal",
        name = "Minimal",
        tagline = "A clock, the date, six apps",
        pages = pages(
            left = page(
                // The plain greeting, because home next door is already holding the date.
                block("greeting", "plain").at(0, 1, 6, 2),
                block("journal", "latest").at(0, 4, 6, 3),
                block("notes", "list").at(0, 7, 6, 5),
            ),
            home = page(
                clock("display", ClockScale.M).at(0, 1, 6, 3),
                block("date", "uppercase").at(0, 4, 6, 1),
                apps("grid", IconStyle(IconMode.ICON, IconShape.SQUIRCLE, IconSize.MEDIUM, IconColor.GRAYSCALE))
                .at(0, 7, 6, 4),
            ),
            right = page(
                // Full width each. Side by side, "262 steps, 3% of 8,000" and "21m screen
                // time" both lose their legend to the cut - and the legend is the half that
                // says what the number is.
                block("steps", "progress").at(0, 1, 6, 2),
                block("screentime", "total").at(0, 4, 6, 2),
                block("battery", "pill").at(0, 7, 6, 2),
            ),
        ),
    )

    /** Every readout at once, each with room to be read. */
    val CompactDashboard = LayoutPreset(
        id = "compact_dashboard",
        name = "Compact dashboard",
        tagline = "Every number, one screen",
        pages = pages(
            left = page(
                block("tasks", "checklist").at(0, 0, 6, 6),
                block("notes", "list").at(0, 6, 3, 6),
                block("journal", "latest").at(3, 6, 3, 6),
                align = BlockAlign.LEFT,
                density = PageDensity.COMPACT,
            ),
            home = page(
                // Every number, on the page the phone opens on - which is what the name
                // promises. Time, date and the next alarm come as one line, so no clock and no
                // date block are needed here at all.
                glance("columns", GlanceSlot.TIME, GlanceSlot.DATE, GlanceSlot.ALARM).at(0, 0, 6, 2),
                block("battery", "pill").at(0, 2, 6, 1),
                block("steps", "progress").at(0, 3, 6, 2),
                block("screentime", "top").at(0, 5, 6, 3),
                music("minimal").at(0, 8, 6, 1),
                apps("grid", IconStyle(IconMode.ICON, IconShape.SQUIRCLE, IconSize.SMALL)).at(0, 9, 6, 3),
                align = BlockAlign.LEFT,
                density = PageDensity.COMPACT,
            ),
            right = page(
                // The longer view, and deliberately none of home's numbers: the year so far,
                // a focus timer and whatever is being counted down to.
                block("date", "yeardots").at(0, 0, 6, 3),
                block("focus", "ring").at(0, 4, 6, 4),
                block("countdown", "next").at(0, 9, 6, 3),
                align = BlockAlign.LEFT,
                density = PageDensity.COMPACT,
            ),
        ),
    )

    /** One block, dead centre, on the only page there is. */
    val JustAClock = LayoutPreset(
        id = "just_a_clock",
        name = "Just a clock",
        tagline = "One block. One page. Nothing else.",
        pages = mapOf(
            PageIds.HOME to page(clock("stacked", ClockScale.L, ClockWeight.BOLD).at(0, 3, 6, 6)),
        ),
    )

    /** Everything within a thumb's reach at the bottom of the screen. */
    val BottomDock = LayoutPreset(
        id = "bottom_dock",
        name = "Bottom dock",
        tagline = "All inside a thumb's reach",
        pages = pages(
            left = page(
                block("tasks", "checklist").at(0, 4, 6, 4),
                block("notes", "tiles").at(0, 8, 6, 4),
            ),
            home = page(
                clock("display", ClockScale.M, ClockWeight.LIGHT).at(0, 3, 6, 3),
                block("date", "uppercase").at(0, 6, 6, 1),
                music("minimal").at(0, 7, 6, 2),
                apps("grid", IconStyle(IconMode.ICON, IconShape.SQUIRCLE, IconSize.LARGE)).at(0, 9, 6, 3),
            ),
            right = page(
                block("steps", "walker").at(0, 4, 6, 3),
                block("battery", "pill").at(0, 8, 6, 1),
                block("screentime", "total").at(0, 9, 6, 2),
            ),
        ),
    )

    /** For somebody who wants to be told how the week is going. */
    val Stats = LayoutPreset(
        id = "stats",
        name = "Stats",
        tagline = "Steps, screen time, the year so far",
        pages = pages(
            left = page(
                // A ring and a bar side by side read as two different things at two different
                // sizes; a bar the full width and the rings underneath it read as a page.
                block("battery", "segments").at(0, 1, 6, 1),
                block("date", "yeardots").at(0, 3, 6, 3),
                block("focus", "ring").at(0, 7, 6, 4),
            ),
            home = page(
                glance("line", GlanceSlot.TIME, GlanceSlot.DATE).at(0, 0, 6, 1),
                block("steps", "week").at(0, 2, 6, 2),
                block("screentime", "top").at(0, 5, 6, 3),
                apps("list", IconStyle(IconMode.TEXT)).at(0, 8, 6, 4),
            ),
            right = page(
                block("tasks", "checklist").at(0, 1, 6, 5),
                block("notes", "list").at(0, 7, 6, 5),
            ),
        ),
    )

    /** A page to put words on: somewhere to write, before anything else. */
    val Writer = LayoutPreset(
        id = "writer",
        name = "Writer",
        tagline = "Somewhere to put the words",
        pages = pages(
            left = page(
                block("greeting", "plain").at(0, 1, 6, 2),
                block("journal", "timeline").at(0, 4, 6, 8),
                density = PageDensity.AIRY,
            ),
            home = page(
                clock("words", ClockScale.M).at(0, 0, 6, 2),
                block("date", "words").at(0, 2, 6, 2),
                block("notes", "list").at(0, 5, 6, 3),
                // Six names need four rows. At three the frame scaled the whole list down to
                // fit, which does not clip anything but reads as a mistake next to full-size
                // text - the backstop working is not the same as the layout being right.
                apps("list", IconStyle(IconMode.TEXT)).at(0, 8, 6, 4),
                density = PageDensity.AIRY,
            ),
            right = page(
                block("tasks", "checklist").at(0, 1, 6, 6),
                block("countdown", "next").at(0, 8, 6, 3),
                density = PageDensity.AIRY,
            ),
        ),
    )

    /** A console: left-aligned, compact, seconds ticking. */
    val Terminal = LayoutPreset(
        id = "terminal",
        name = "Terminal",
        tagline = "Left-aligned, compact, seconds on",
        pages = pages(
            left = page(
                block("tasks", "checklist").at(0, 0, 6, 4),
                block("notes", "list").at(0, 4, 6, 4),
                block("journal", "timeline").at(0, 8, 6, 4),
                align = BlockAlign.LEFT,
                density = PageDensity.COMPACT,
            ),
            home = page(
                clock("mono", ClockScale.M, ClockWeight.LIGHT, seconds = true).at(0, 0, 6, 2),
                block("date", "iso").at(0, 2, 6, 1),
                block("spacer", "line").at(0, 3, 6, 1),
                // Battery and the next alarm only: steps and screen time have a page of
                // their own one swipe away, where there is room to actually read them.
                glance("line", GlanceSlot.BATTERY, GlanceSlot.ALARM).at(0, 4, 6, 1),
                apps("list", IconStyle(IconMode.TEXT)).at(0, 6, 6, 6),
                align = BlockAlign.LEFT,
                density = PageDensity.COMPACT,
            ),
            right = page(
                block("steps", "week").at(0, 0, 6, 5),
                block("screentime", "grid").at(0, 5, 6, 5),
                block("calculator", "strip").at(0, 10, 6, 2),
                align = BlockAlign.LEFT,
                density = PageDensity.COMPACT,
            ),
        ),
    )

    /** Quiet and wide-spaced, the time written out in words. */
    val Paper = LayoutPreset(
        id = "paper",
        name = "Paper",
        tagline = "Wide margins, the time in words",
        pages = pages(
            left = page(
                block("quote", "card").at(0, 1, 6, 4),
                block("journal", "timeline").at(0, 6, 6, 6),
                density = PageDensity.AIRY,
            ),
            home = page(
                block("date", "words").at(0, 1, 6, 2),
                clock("words", ClockScale.M).at(0, 3, 6, 3),
                apps("list", IconStyle(IconMode.TEXT)).at(0, 7, 6, 4),
                density = PageDensity.AIRY,
            ),
            right = page(
                block("notes", "list").at(0, 1, 6, 5),
                block("tasks", "checklist").at(0, 7, 6, 5),
                density = PageDensity.AIRY,
            ),
        ),
    )

    val all: List<LayoutPreset> = listOf(
        Minimal, CompactDashboard, JustAClock, BottomDock, Stats, Writer, Terminal, Paper,
    )

    fun byId(id: String?): LayoutPreset? = all.firstOrNull { it.id == id }
}

// ------------------------------------------------------------------ builders

private fun pages(left: PageLayout, home: PageLayout, right: PageLayout): Map<String, PageLayout> =
    mapOf(PageIds.LEFT to left, PageIds.HOME to home, PageIds.RIGHT to right)

private var pageCounter = 0

private fun page(
    vararg blocks: Block,
    align: BlockAlign = BlockAlign.CENTER,
    density: PageDensity = PageDensity.COMFORTABLE,
): PageLayout = PageLayout(
    // Rewritten when the layout is applied; only the contents matter here.
    pageId = "layout_${pageCounter++}",
    blocks = blocks.toList(),
    align = align,
    density = density,
)

/** Where this block sits on the page's six by twelve grid. */
private fun Block.at(col: Int, row: Int, cols: Int, rows: Int): Block =
    copy(rect = GridRect(col, row, cols, rows))

private fun block(
    type: String,
    style: String,
    settings: Map<String, String> = emptyMap(),
): Block {
    val fresh = BlockRegistry.newBlock(type)
    return fresh.copy(style = style, settings = fresh.settings + settings)
}

private fun clock(
    style: String,
    scale: ClockScale = ClockScale.M,
    weight: ClockWeight? = null,
    seconds: Boolean = false,
): Block = block("clock", style, settings = ClockSettings(scale = scale, weight = weight, seconds = seconds).toMap())

private fun glance(style: String, vararg slots: GlanceSlot): Block =
    block("glance", style, settings = GlanceSettings(slots = slots.toList()).toMap())

/** Takes no room at all while nothing is playing, which is what a home screen wants. */
private fun music(style: String): Block =
    block("music", style, settings = mapOf(MusicBlockDefinition.KEY_HIDE_IDLE to "1"))

private fun apps(style: String, icons: IconStyle): Block =
    block(AppsBlockDefinition.type, style, settings = icons.toMap())

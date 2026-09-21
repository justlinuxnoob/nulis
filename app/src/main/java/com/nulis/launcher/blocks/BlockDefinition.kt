// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.apps.SystemApp
import com.nulis.launcher.blocks.battery.BatteryState
import com.nulis.launcher.blocks.calendar.CalendarActions
import com.nulis.launcher.blocks.calendar.CalendarState
import com.nulis.launcher.blocks.focus.FocusActions
import com.nulis.launcher.blocks.focus.FocusState
import com.nulis.launcher.blocks.music.MusicActions
import com.nulis.launcher.blocks.music.MusicState
import com.nulis.launcher.blocks.screentime.ScreenTimeState
import com.nulis.launcher.blocks.steps.StepsActions
import com.nulis.launcher.blocks.steps.StepsState
import com.nulis.launcher.blocks.writing.WritingActions
import com.nulis.launcher.blocks.writing.WritingState
import com.nulis.launcher.icons.IconStyle
import java.time.LocalDateTime

/**
 * Everything a block may need from the outside world when rendering. Stable so blocks only
 * recompose when one of these actually changes; the route remembers one per (time, apps).
 *
 * [onLaunchApp] takes the tapped item's bounds in window coordinates (or null) so the app can
 * grow out of that spot.
 */
@Stable
class BlockContext(
    val time: LocalDateTime,
    val apps: List<AppInfo>,
    /**
     * The apps on the user's own home page, in the order they sit there. Every preview - an icon
     * shape, a block style, a layout card - is drawn with these rather than with whatever happens
     * to sort first alphabetically, so a picker shows somebody their own phone.
     */
    val homeApps: List<AppInfo> = emptyList(),
    val onLaunchApp: (AppInfo, Rect?) -> Unit,
    val battery: BatteryState = BatteryState(),
    val music: MusicState = MusicState(),
    val musicActions: MusicActions = MusicActions.None,
    val writing: WritingState = WritingState(),
    val writingActions: WritingActions = WritingActions.None,
    val screenTime: ScreenTimeState = ScreenTimeState(),
    val steps: StepsState = StepsState(),
    val stepsActions: StepsActions = StepsActions.None,
    val calendar: CalendarState = CalendarState(),
    val calendarActions: CalendarActions = CalendarActions.None,
    val focus: FocusState = FocusState(),
    /** When the phone's next alarm goes off, or null when none is set. No permission needed. */
    val nextAlarm: LocalDateTime? = null,
    val focusActions: FocusActions = FocusActions.None,
    /** Per-app minutes today for app lists, or null when that option is off. */
    val appUsage: Map<String, Int>? = null,
    /** How apps are drawn in lists a block does not style itself, e.g. screen time rows. */
    val appIcons: IconStyle = IconStyle(),
    /** Packages a running focus session is guarding: drawn faded, or not at all. */
    val mutedPackages: Set<String> = emptySet(),
    val muteHides: Boolean = false,
    /** Opens a block's full-screen [BlockDefinition.Screen]; a no-op in previews and edit mode. */
    val openScreen: (ScreenRequest) -> Unit = {},
    /**
     * Writes a block back to whichever page it is on; a no-op in previews and edit mode.
     *
     * For the handful of blocks whose settings are the thing you tap them to change - the apps
     * block's own list of apps being the one - so that choosing them does not mean finding the
     * editor, finding the block, and finding its options sheet.
     */
    val updateBlock: (Block) -> Unit = {},
    /**
     * Opens a block's own options sheet from the block; a no-op in previews and edit mode.
     *
     * For blocks whose content is a setting - the quote's lines, the countdown's date, the
     * people you pinned - so that an empty one is a way in rather than a dead rectangle with a
     * stale instruction printed on it.
     */
    val openBlockOptions: (Block) -> Unit = {},
    /** Sends the user to the phone's own clock or calendar; a no-op in previews and edit mode. */
    val openSystemApp: (SystemApp) -> Unit = {},
    /** Opens the weekly summary; a no-op in previews and edit mode. */
    val openWeek: () -> Unit = {},
)

/**
 * The app a preview should be drawn with: the first one on the home page, or the first launchable
 * app on a phone that has not chosen any yet.
 */
val BlockContext.sampleApp: AppInfo? get() = homeApps.firstOrNull() ?: apps.firstOrNull()

/** The apps a preview with room for several should use, in the user's own order. */
val BlockContext.sampleApps: List<AppInfo> get() = homeApps.ifEmpty { apps }

/** The installed app's label for a package, or the package name if it is gone. */
fun BlockContext.appLabel(packageName: String): String = appFor(packageName)?.label ?: packageName

/** The launchable entry for a package, for lists that only know a package name. */
fun BlockContext.appFor(packageName: String): AppInfo? = apps.firstOrNull { it.packageName == packageName }

/** A request to show a block type's full-screen surface, e.g. a note editor ([arg] = note id). */
data class ScreenRequest(val blockType: String, val arg: String? = null)

/** One visual style of a block. Adding a style means adding one of these to the definition's list. */
interface BlockStyle {
    val id: String
    val label: String

    @Composable
    fun Render(block: Block, context: BlockContext, modifier: Modifier)
}

/** One block type. Adding a type means implementing this and listing it in [BlockRegistry]. */
interface BlockDefinition {
    val type: String
    val label: String

    /** Available styles; the first one is the default for new blocks. */
    val styles: List<BlockStyle>

    val defaultSize: BlockSize get() = BlockSize.WIDE

    /**
     * The smallest rectangle this block can be drawn in and still say something. The editor
     * will not let a resize go below it and the picker will not place a new one smaller, so a
     * style never has to cope with a size it was never designed for.
     */
    val minSpan: GridSpan get() = GridSpan(cols = 2, rows = 1)

    /**
     * The rectangle a fresh one of these asks for, and what the migration gives a block that
     * only ever said how wide it was.
     */
    val defaultSpan: GridSpan get() = GridSpan(cols = PageGrid.COLUMNS, rows = 2)

    fun defaultSettings(): Map<String, String> = emptyMap()

    /**
     * How tall this block's preview cards are. Blocks whose styles include dials or stacked
     * digits need more room than a line of text; everything else keeps the default.
     */
    val previewHeight: Dp get() = 80.dp

    /** Settings that make a fresh block look meaningful in a preview, e.g. a few real apps. */
    fun previewSettings(context: BlockContext): Map<String, String> = defaultSettings()

    /**
     * True when this block has nothing to show right now and should take no room on the page.
     * Edit mode ignores it, so a hidden block can still be reached and configured.
     */
    fun isHidden(block: Block, context: BlockContext): Boolean = false

    /** Whether [Options] renders anything, so the sheet can label the section. */
    val hasOptions: Boolean get() = false

    /** Extra controls shown in the block's options sheet. Most blocks have none. */
    @Composable
    fun Options(block: Block, context: BlockContext, onUpdate: (Block) -> Unit) = Unit

    /**
     * What a screen reader says for this block. A clock is a time, a battery is a percentage,
     * a chart is a sentence; without this, a block drawn on a canvas is silent.
     */
    fun accessibilityLabel(block: Block, context: BlockContext): String? = null

    /**
     * What a single tap anywhere on this block does, or null for a block that has none. The page
     * folds this into the same detector as its own double-tap gesture, so a block never has to
     * fight the page for taps. Blocks with their own tappable rows (notes, tasks) return null.
     */
    fun tapAction(block: Block, context: BlockContext): (() -> Unit)? = null

    fun style(block: Block): BlockStyle = styles.firstOrNull { it.id == block.style } ?: styles.first()

    /**
     * The block type's full-screen surface (an editor, a list), shown by the route when a block
     * calls [BlockContext.openScreen]. Most blocks have none.
     */
    @Composable
    fun Screen(request: ScreenRequest, context: BlockContext, onClose: () -> Unit) = Unit
}

/** What the UI can do to a page's blocks. Implemented by the view model. */
@Stable
interface BlockActions {
    fun update(block: Block)

    /** Changes the page itself: alignment, spacing, every block at once. */
    fun updatePage(transform: (PageLayout) -> PageLayout)

    fun remove(blockId: String)

    /**
     * Puts a new block of [type] in the first space on the page that fits it, and says where it
     * went - or null when there is no room for even the smallest version of it, which is the
     * page's cue to offer to shrink something or start a new page.
     */
    fun add(type: String): GridRect?
}

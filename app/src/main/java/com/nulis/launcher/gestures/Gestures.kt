// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.gestures

import androidx.compose.runtime.Immutable

/**
 * Something the user does on a page. Every trigger is remappable; the launcher itself has no
 * hardcoded gesture except the ones a block owns (a long press on a block still opens its options).
 */
enum class GestureTrigger(val id: String, val label: String, val hint: String) {
    SWIPE_UP("swipe_up", "Swipe up", "Anywhere on a page"),
    SWIPE_DOWN("swipe_down", "Swipe down", "Anywhere on a page"),
    TWO_FINGER_SWIPE_UP("two_finger_up", "Two-finger swipe up", "Two fingers, upwards"),
    TWO_FINGER_SWIPE_DOWN("two_finger_down", "Two-finger swipe down", "Two fingers, downwards"),
    DOUBLE_TAP("double_tap", "Double tap", "On empty space"),
    LONG_PRESS("long_press", "Long-press", "On empty space"),
    DOUBLE_TAP_CLOCK("double_tap_clock", "Double tap the clock", "On a clock block");

    val default: GestureBinding get() = GestureBinding(defaultAction)

    private val defaultAction: GestureAction
        get() = when (this) {
            SWIPE_UP -> GestureAction.OPEN_DRAWER
            SWIPE_DOWN -> GestureAction.NOTIFICATIONS
            TWO_FINGER_SWIPE_DOWN -> GestureAction.QUICK_SETTINGS
            LONG_PRESS -> GestureAction.EDIT_MODE
            TWO_FINGER_SWIPE_UP, DOUBLE_TAP, DOUBLE_TAP_CLOCK -> GestureAction.NOTHING
        }

    companion object {
        fun byId(id: String?): GestureTrigger? = entries.firstOrNull { it.id == id }
    }
}

/** Actions are shown grouped, so 14 of them scan as four short lists instead of one long one. */
enum class GestureGroup(val label: String) {
    APPS("Apps"),
    PAGES("Pages"),
    SYSTEM("System"),
    NULIS("Nulis"),
    /** Rendered last, on its own, with no heading. */
    NONE(""),
}

/**
 * What a trigger does. [needsApp] actions carry a chosen app in the binding; [fullVersion] ones
 * need the Accessibility Service and are shown as disabled rows until that build flavor exists.
 */
enum class GestureAction(
    val id: String,
    val label: String,
    val group: GestureGroup,
    val needsApp: Boolean = false,
    val fullVersion: Boolean = false,
) {
    OPEN_DRAWER("drawer", "Open app drawer", GestureGroup.APPS),
    SEARCH_APPS("search", "Search apps", GestureGroup.APPS),
    OPEN_APP("app", "Open an app", GestureGroup.APPS, needsApp = true),
    PAGE_LEFT("page_left", "Go to left page", GestureGroup.PAGES),
    PAGE_RIGHT("page_right", "Go to right page", GestureGroup.PAGES),
    EDIT_MODE("edit", "Edit this page", GestureGroup.PAGES),
    NOTIFICATIONS("notifications", "Notification shade", GestureGroup.SYSTEM),
    QUICK_SETTINGS("quick_settings", "Quick settings", GestureGroup.SYSTEM),
    LOCK_SCREEN("lock", "Lock screen", GestureGroup.SYSTEM, fullVersion = true),
    SETTINGS("settings", "Nulis settings", GestureGroup.NULIS),
    TOGGLE_LOOK("toggle_look", "Switch look", GestureGroup.NULIS),
    TOGGLE_THEME("toggle_theme", "Switch black / white", GestureGroup.NULIS),
    NEW_NOTE("new_note", "New note", GestureGroup.NULIS),
    NOTHING("nothing", "Nothing", GestureGroup.NONE);

    /** True when this action pulls the drawer up, so a swipe can track it 1:1 instead of firing once. */
    val opensDrawer: Boolean get() = this == OPEN_DRAWER || this == SEARCH_APPS

    /**
     * True when this action can get the user back to settings or to editing a page. At least one
     * trigger must keep one of these, or a remapping could lock the launcher down for good.
     */
    val isEscape: Boolean get() = this == OPEN_DRAWER || this == SEARCH_APPS || this == SETTINGS || this == EDIT_MODE

    companion object {
        fun byId(id: String?): GestureAction? = entries.firstOrNull { it.id == id }
    }
}

/** One trigger's mapping. [appId] is an [com.nulis.launcher.apps.AppInfo.id], only for [GestureAction.OPEN_APP]. */
@Immutable
data class GestureBinding(val action: GestureAction, val appId: String? = null) {

    /** Stored form: the action id, or `app|<package>/<activity>`. */
    fun store(): String = if (action.needsApp && appId != null) "${action.id}|$appId" else action.id

    companion object {
        fun parse(stored: String?): GestureBinding? {
            val raw = stored ?: return null
            val action = GestureAction.byId(raw.substringBefore('|')) ?: return null
            val appId = raw.substringAfter('|', "").ifEmpty { null }
            return GestureBinding(action, appId)
        }
    }
}

/** Every trigger's mapping. Triggers the user has never touched fall back to their default. */
@Immutable
data class GestureSettings(
    val bindings: Map<GestureTrigger, GestureBinding> = emptyMap(),
    /**
     * True when the drawer has a page of its own or has been turned off, so the upward swipe is
     * no longer spoken for. Nothing is taken away: a swipe up the user has *bound* to something
     * keeps doing it. What changes is the default, which stops being "open the drawer" the
     * moment the drawer is somewhere else.
     */
    val swipeUpIsFree: Boolean = false,
    /**
     * True when the drawer is a page, and therefore always one sideways swipe away whatever the
     * gestures say. A launcher in that state cannot be locked out of itself.
     */
    val drawerIsAPage: Boolean = false,
) {

    /**
     * True when nothing the user has mapped would reach the drawer, settings or edit mode any
     * more. [ESCAPE_TRIGGER] then opens the drawer whatever it is set to, so there is always a
     * way back in; from the drawer, Nulis's own row opens settings.
     */
    val lockedOut: Boolean =
        !drawerIsAPage && GestureTrigger.entries.none { effective(bindings, swipeUpIsFree, it).action.isEscape }

    operator fun get(trigger: GestureTrigger): GestureBinding =
        if (lockedOut && trigger == ESCAPE_TRIGGER) GestureBinding(GestureAction.OPEN_DRAWER)
        else effective(bindings, swipeUpIsFree, trigger)

    /** True when this trigger is the one currently being held open as the way back in. */
    fun isEscapeHatch(trigger: GestureTrigger): Boolean = lockedOut && trigger == ESCAPE_TRIGGER

    fun action(trigger: GestureTrigger): GestureAction = get(trigger).action

    /** True when this trigger does something, so a detector is only attached when it is needed. */
    fun isBound(trigger: GestureTrigger): Boolean = action(trigger) != GestureAction.NOTHING

    private companion object {
        /** The trigger the escape hatch falls back to: the one every launcher user tries first. */
        val ESCAPE_TRIGGER = GestureTrigger.SWIPE_UP

        fun effective(
            bindings: Map<GestureTrigger, GestureBinding>,
            swipeUpIsFree: Boolean,
            trigger: GestureTrigger,
        ): GestureBinding = bindings[trigger]
            ?: if (swipeUpIsFree && trigger == GestureTrigger.SWIPE_UP) {
                GestureBinding(GestureAction.NOTHING)
            } else {
                trigger.default
            }
    }
}

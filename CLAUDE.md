# Nulis Launcher

## Vision

A minimalist but deeply customizable Android launcher.

- **A page is exactly one screen.** Each page is a 6 x 12 **grid**; a **block** is a rectangle on it (position plus column and row span). Empty cells are allowed, nothing scrolls, and nothing can be taller than the screen. Blocks: clock, date, music, steps, screen time, apps, notes, journal, tasks, meditation/focus, fun animations. Each has several **styles** and an alignment, a declared minimum size, and adapts its content to whatever rectangle it is given.
- **Pages.** Up to five, in swipe order, with **home** a mark on whichever one the user chooses. Add, remove and reorder them in Settings - Layout - Pages.
- **Editing happens on the real page.** Long-press anywhere - a block or empty space - and the page scales back, the grid appears and every block gets an outline. Drag to move (neighbours make room, or the drop is refused), pull the corner to resize, tap for a floating toolbar (style, align, duplicate, delete), Add for the picker, Undo for the last dozen actions.
- **Two things to choose between**, not three: a **Layout** (what is on which page, and how many pages) and a **Look** with its **Colours** (how it is drawn). "Save this setup" keeps the two together under a name. There are no themes.
- **App drawer.** Swipe up: search, A-Z jump, folders, hidden apps.

## Rules

- Kotlin + Jetpack Compose only. No XML layouts, no Java.
- Fully offline: never add `INTERNET`, no analytics or crash reporters, all data on-device.
- Every permission is opt-in behind an explanation screen the user can decline.
- Risky features (e.g. Accessibility Service) go in a later `full` build flavor, never the default one.
- Only open-source fonts (Doto, Geist, Geist Mono; OFL licenses in `assets/licenses`). Never another company's name, logo or fonts.
- No new dependencies or permissions without a clear need.

## Structure

- Root package `com.nulis.launcher`, one package per feature: `apps`, `blocks` (one sub-package per block type), `icons` (app icons), `layout` (page persistence, the page list, the shipped layouts), `setups` (a whole phone saved under a name), `home` (the page, the grid and the editor), `drawer`, `settings`, `gestures`, `ui/theme`, `ui/components`.
- Repositories load and persist data; composables never do. `*Screen.kt` composables take plain data and callbacks; `LauncherRoute.kt` wires them to `LauncherViewModel`.

## Design package

All UI is built from `ui/theme` tokens and `ui/components`. Never hardcode a font, color or divider style in a screen.

- **Looks** (`Look.kt`): display typeface + decorative motif. `Looks.all` ships DOT (Doto, dotted hairlines, dot-grid motif on editing surfaces) and CLEAN (thin Geist, solid hairlines). A new look is one new entry. Read via `NulisTheme.look`.
- **Fonts** (`Fonts.kt`): six bundled OFL faces with a category each. A user (or a theme) can override the display face and the body face; a mono body face takes the label styles with it.
- **Colors** (`Colors.kt`): roles background, surface, surfaceRaised, onBackground, secondary, tertiary, hairline, accent. BLACK and WHITE share the roles; CUSTOM derives them all from one background plus an optional ink. Red accent only for selection dots and destructive actions.
- **Type** (`Type.kt`): display XL/L/M/S in the Look's (or the chosen) face; body XL/L/M/S in the body face; `label`/`labelL` mono letter-spaced; `mono` for readouts. A global text scale multiplies everything. Components case labels through `type.labelCase()`, never `uppercase()`.
- **Components**: `NulisCard`, `PillButton` (default/primary/danger), `NulisIconButton` with hand-drawn `Glyph`s, `NulisToggle`, `NulisBottomSheet` with look-specific `DragHandle`, `SectionLabel`, `ListRow`, `Hairline`, `dotGrid()`, `SegmentedPills`, `FitWidth` (shrinks type that would not fit), `ScaledPreview`/`BlockPreviewCard`/`PageMiniature` for live previews. Pickers always show live block previews, never text chips.
- **Layout and feel**: 8dp grid, 24dp screen margins (`NulisSpacing`), 48dp touch targets. Tweens only: 150ms state, 200ms screens (`NulisMotion`). Haptic `SegmentTick` on selection, `ToggleOn/Off` on toggles. No Material ripples; pressed states dim to 60%.

## App icons

- An `IconStyle` (mode, shape, size, colour) says how an app is drawn. Each Apps block keeps its own in its settings; the drawer and every other app list share the one in settings.
- `IconLoader` is the only place icons are read or processed: off the main thread, cached in memory and on disk under app version + pack + override + treatment, never clipped or filtered on the draw path. Anything a list draws every frame must come out of its cache as a plain bitmap.
- Composables reach it through `LocalIconLoader` and draw with `AppGlyph`.

## Block system

- A `PageLayout` is a list of `Block`s (`type`, `style`, `settings` map, `align`, and a `rect` on the 6 x 12 `PageGrid`) plus the page's own `align` and `density`, stored as JSON in DataStore per page id. Every new field has a default, so old layouts decode unchanged; `PageLayout.normalized()` folds every older way of saying where a block went (`pairNext`, `width`, `anchor`) into a `GridRect`, once, on the way in.
- The page list itself (`PagesConfig`: ordered ids plus the home id, at most five) lives in the same store and is owned by `LayoutRepository`.
- `BlockRegistry.definitions` is the only registration point. A new type implements `BlockDefinition`; a new style implements `BlockStyle` and joins that definition's `styles` list. Nothing else changes.
- A definition alone reads and writes its settings, renders its `Options` in the sheet, supplies `previewSettings` so previews show real data, declares a `minSpan` and a `defaultSpan` in grid cells, and may declare a `tapAction` and a taller `previewHeight`.
- **Every style has to survive any legal rectangle.** `minSpan` is the floor the editor enforces; above it a style reflows, and `BlockFrame` scales anything that still overflows rather than letting it spill onto a neighbour.
- **Every style honours alignment**, through `BlockColumn` / `BlockRow` / `BlockBox` / `BlockCaptionRow` or a plain `textAlign` from `blockAlign()`. Bars and charts grow out of the aligned edge.

## Workflow

After every change:

1. **Build** `./gradlew assemblePerf` and fix every error. The `perf` build type is release-optimised (R8, not debuggable) but signed with the debug key; the debug build is 5-10x slower per frame and must never be used to judge feel.
2. **Install** on the USB phone if `adb devices` lists one; otherwise say so explicitly. Never fail silently.

   **Never lock or sleep the phone.** Do not send `KEYCODE_POWER`, `KEYCODE_SLEEP`,
   `input keyevent 26`, `svc power`, `cmd lock_settings`, `dpm lock-now`, or anything else that can
   turn the screen off - a locked phone needs a fingerprint and cuts off every on-device check for
   the rest of the session. It has happened once and cost a night of verification. To prove
   something does not render off-screen, swipe to another page or send the app to the background;
   never use the screen. Keep the screen awake with `stay_on_while_plugged_in` instead. Install the perf build and compile it with its baseline profile, which is what a Play install would do:
   ```
   adb install -r app/build/outputs/apk/perf/app-perf.apk
   adb shell am start -n com.nulis.launcher/.MainActivity   # then wait ~2 s
   adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -p com.nulis.launcher/androidx.profileinstaller.ProfileInstallReceiver   # then wait ~4 s
   adb shell cmd package compile -m speed-profile -f com.nulis.launcher
   adb shell am force-stop com.nulis.launcher                # the running process still holds
   adb shell input keyevent KEYCODE_HOME                     # the old, un-compiled code
   ```

   **The restart is not optional.** `cmd package compile` does nothing for a process that is
   already running, so measuring without it measures interpreted code: a gate run taken that way
   read 14-20 ms medians and 100% over budget on gestures that are really 5-8 ms.

   **Check that the compile actually happened.** The profile broadcast is asynchronous, and if
   `cmd package compile` runs before it lands the app is silently left at `status=verify` - which
   is interpreted code again, and reads two to three times slower. Verify and retry:
   ```
   adb shell dumpsys package com.nulis.launcher | grep -m1 status=   # wants status=speed-profile
   ```
3. **Verify lean.** Screenshot only the screens you changed, in the currently active look and theme. Prefer `uiautomator dump` hierarchy checks over screenshots when you only need to confirm something exists or works. Run a multi-look, multi-theme screenshot tour only when the user explicitly asks for a "full visual review". For anything that scrolls, pages or animates, check frame stats (`dumpsys gfxinfo com.nulis.launcher framestats` around one `input swipe`; budget is 11 ms at 90 Hz) on the perf build.
4. **Commit and push** to `main` with a short message. Never commit build outputs, `local.properties` or keystores.

Report the result of each step.

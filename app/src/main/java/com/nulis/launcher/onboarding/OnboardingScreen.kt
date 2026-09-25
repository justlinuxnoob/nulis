// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.onboarding

import android.annotation.SuppressLint
import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import android.app.role.RoleManager
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.PageIds
import com.nulis.launcher.home.PageMiniature
import com.nulis.launcher.layout.LayoutPreset
import com.nulis.launcher.icons.AppGlyph
import com.nulis.launcher.icons.IconMode
import com.nulis.launcher.icons.IconStyle
import com.nulis.launcher.ui.theme.ColorTheme
import com.nulis.launcher.ui.theme.LocalLook
import com.nulis.launcher.ui.theme.LocalNulisColors
import com.nulis.launcher.ui.theme.LocalNulisTypography
import com.nulis.launcher.ui.theme.Look
import com.nulis.launcher.ui.theme.Looks
import com.nulis.launcher.ui.theme.NulisTypography
import com.nulis.launcher.ui.theme.colorsFor
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Hairline
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisCard
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.fadeTop
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisMotion
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisSpacing
import com.nulis.launcher.ui.theme.NulisTheme

/** What onboarding can do, wired to the view model by the route. */
class OnboardingActions(
    val onPickLayout: (LayoutPreset) -> Unit,
    val onPickLook: (String, ColorTheme) -> Unit,
    val onToggleApp: (AppInfo) -> Unit,
    val onFinish: () -> Unit,
    /** Re-checks every permission; called whenever we come back from a system screen. */
    val onRefreshPermissions: () -> Unit,
)

/** One optional permission, with the single line that explains why Nulis would like it. */
class OnboardingPermission(
    val title: String,
    val reason: String,
    val granted: Boolean,
    val request: () -> Unit,
)

private enum class Step { WELCOME, LAYOUT, LOOK, APPS, DEFAULT, PERMISSIONS, DONE }

/**
 * First launch, once, and skippable at every step. It never leaves the user on a blank page: a
 * look is applied the moment it is chosen, so even someone who taps Skip four times lands on a
 * finished home.
 */
@Composable
fun OnboardingScreen(
    layouts: List<LayoutPreset>,
    appliedLayoutId: String?,
    lookId: String,
    colorTheme: ColorTheme,
    apps: List<AppInfo>,
    favoriteIds: Set<String>,
    maxFavorites: Int,
    iconStyle: IconStyle,
    context: BlockContext,
    permissions: List<OnboardingPermission>,
    actions: OnboardingActions,
    modifier: Modifier = Modifier,
) {
    var step by remember { mutableStateOf(Step.WELCOME) }
    val order = Step.entries
    var seen by remember { mutableIntStateOf(0) }
    val haptics = LocalHapticFeedback.current

    fun go(next: Step) {
        haptics.performHapticFeedback(NulisHaptics.tick)
        seen = maxOf(seen, order.indexOf(next))
        step = next
    }

    // Back steps backwards through the flow rather than dropping the user into a half-set-up
    // phone; on the first step it does nothing at all, because there is nothing behind this.
    BackHandler { if (step != Step.WELCOME) step = order[order.indexOf(step) - 1] }

    Box(modifier.fillMaxSize().background(NulisTheme.colors.background)) {
        // Swallows everything the flow itself does not handle, so a swipe on empty space cannot
        // reach the pages underneath and open the drawer mid-setup.
        Box(Modifier.matchParentSize().blockGestures())
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = NulisSpacing.screenMargin),
        ) {
            Spacer(Modifier.height(8.dp))
            StepDots(order.indexOf(step), order.size)
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = order.indexOf(targetState) > order.indexOf(initialState)
                    val width = if (forward) 1 else -1
                    (slideInHorizontally(tween(NulisMotion.normal)) { it / 6 * width } + fadeIn(tween(NulisMotion.normal)))
                        .togetherWith(slideOutHorizontally(tween(NulisMotion.quick)) { -it / 6 * width } + fadeOut(tween(NulisMotion.quick)))
                },
                label = "onboarding",
            ) { current ->
                when (current) {
                    Step.WELCOME -> Welcome(onNext = { go(Step.LAYOUT) }, onSkip = actions.onFinish)
                    Step.LAYOUT -> LayoutStep(
                        layouts = layouts,
                        appliedId = appliedLayoutId,
                        context = context,
                        onPick = actions.onPickLayout,
                        onNext = { go(Step.LOOK) },
                    )
                    Step.LOOK -> LookStep(
                        layout = layouts.firstOrNull { it.id == appliedLayoutId } ?: layouts.first(),
                        lookId = lookId,
                        colorTheme = colorTheme,
                        context = context,
                        onPick = actions.onPickLook,
                        onNext = { go(Step.APPS) },
                    )
                    Step.APPS -> AppsStep(
                        apps = apps,
                        favoriteIds = favoriteIds,
                        maxFavorites = maxFavorites,
                        iconStyle = iconStyle,
                        onToggle = actions.onToggleApp,
                        onNext = { go(Step.DEFAULT) },
                    )
                    Step.DEFAULT -> DefaultLauncherStep(
                        onRefresh = actions.onRefreshPermissions,
                        onNext = { go(Step.PERMISSIONS) },
                    )
                    Step.PERMISSIONS -> PermissionsStep(
                        permissions = permissions,
                        onNext = { go(Step.DONE) },
                    )
                    Step.DONE -> Done(onFinish = actions.onFinish)
                }
            }
        }
    }
}

/** Consumes any gesture that reaches it, so nothing behind this screen ever sees one. */
private fun Modifier.blockGestures(): Modifier = pointerInput(Unit) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false).consume()
        while (true) {
            val event = awaitPointerEvent()
            event.changes.forEach { it.consume() }
            if (event.changes.none { it.pressed }) break
        }
    }
}

@Composable
private fun StepDots(index: Int, count: Int) {
    val colors = NulisTheme.colors
    Row(
        Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == index) 7.dp else 5.dp)
                    .background(if (i == index) colors.onBackground else colors.hairline, CircleShape),
            )
        }
    }
}

/** A step's shared shape: a title, a line of explanation, the body, then the buttons. */
@Composable
private fun StepFrame(
    label: String,
    title: String,
    blurb: String,
    primary: String,
    onPrimary: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        SectionLabel(label, withLine = false)
        Text(title, style = NulisTheme.type.displayM, color = NulisTheme.colors.onBackground)
        Spacer(Modifier.height(8.dp))
        Text(blurb, style = NulisTheme.type.bodyM, color = NulisTheme.colors.secondary)
        Spacer(Modifier.height(20.dp))
        Box(Modifier.weight(1f)) { body() }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (secondary != null && onSecondary != null) {
                PillButton(text = secondary, onClick = onSecondary, modifier = Modifier.weight(1f))
            }
            PillButton(text = primary, onClick = onPrimary, tone = PillTone.Primary, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun Welcome(onNext: () -> Unit, onSkip: () -> Unit) {
    val colors = NulisTheme.colors
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Text("Nulis", style = NulisTheme.type.displayXl, color = colors.onBackground)
        Spacer(Modifier.height(16.dp))
        Text(
            "A home screen made of blocks. Pick a layout, pick a look, pick a few apps; change any of it later by long-pressing.",
            style = NulisTheme.type.bodyL,
            color = colors.secondary,
        )
        Spacer(Modifier.height(24.dp))
        Hairline()
        Spacer(Modifier.height(16.dp))
        Caption("Nothing here leaves your phone. Nulis has no internet permission.", lines = 2)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            PillButton(text = "Skip setup", onClick = onSkip, modifier = Modifier.weight(1f))
            PillButton(text = "Set up", onClick = onNext, tone = PillTone.Primary, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * Layouts come before the look, because the arrangement is the decision that changes what the
 * phone is for; the colours are a decision about how it feels, and they are independent.
 */
@Composable
private fun LayoutStep(
    layouts: List<LayoutPreset>,
    appliedId: String?,
    context: BlockContext,
    onPick: (LayoutPreset) -> Unit,
    onNext: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    StepFrame(
        label = "Step 1",
        title = "Pick a layout",
        blurb = "What goes on each page. Tap one to try it on; nothing here is permanent.",
        primary = "Next",
        onPrimary = onNext,
        body = {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(layouts, key = { it.id }) { preset ->
                    Column(Modifier.width(186.dp)) {
                        NulisCard(
                            modifier = Modifier.fillMaxWidth(),
                            selected = preset.id == appliedId,
                            shape = NulisShapes.tile,
                            contentPadding = 8.dp,
                            onClick = {
                                haptics.performHapticFeedback(NulisHaptics.tick)
                                onPick(preset)
                            },
                        ) {
                            PageMiniature(preset.page(PageIds.HOME), context, Modifier.fillMaxWidth(), sample = true)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(preset.name, style = NulisTheme.type.bodyM, color = NulisTheme.colors.onBackground, maxLines = 1)
                    }
                }
            }
        },
    )
}

/**
 * The look, on its own, drawn over the layout that was just chosen. Two typefaces and two
 * backgrounds, four live tiles: enough of a decision to make the phone feel like the user's,
 * and small enough not to be a second layout picker. Everything else is in settings.
 */
@Composable
private fun LookStep(
    layout: LayoutPreset,
    lookId: String,
    colorTheme: ColorTheme,
    context: BlockContext,
    onPick: (String, ColorTheme) -> Unit,
    onNext: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val choices = remember(layout) {
        Looks.all.flatMap { look -> listOf(ColorTheme.BLACK, ColorTheme.WHITE).map { look to it } }
    }
    StepFrame(
        label = "Step 2",
        title = "Pick a look",
        blurb = "The type and the background. It has nothing to do with the layout, so any pair works.",
        primary = "Next",
        onPrimary = onNext,
        body = {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
            ) {
                items(choices, key = { "${it.first.id}-${it.second}" }) { (look, colors) ->
                    val selected = look.id == lookId && colors == colorTheme
                    Column(Modifier.width(186.dp)) {
                        NulisCard(
                            modifier = Modifier.fillMaxWidth(),
                            selected = selected,
                            shape = NulisShapes.tile,
                            contentPadding = 8.dp,
                            onClick = {
                                haptics.performHapticFeedback(NulisHaptics.tick)
                                onPick(look.id, colors)
                            },
                        ) {
                            LookTokens(look, colors) {
                                PageMiniature(layout.page(PageIds.HOME), context, Modifier.fillMaxWidth(), sample = true)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "${look.label} \u00b7 ${if (colors == ColorTheme.BLACK) "dark" else "light"}",
                            style = NulisTheme.type.bodyM,
                            color = NulisTheme.colors.onBackground,
                            maxLines = 1,
                        )
                    }
                }
            }
        },
    )
}

/** The design tokens for one look and one background, for a preview of something not yet applied. */
@Composable
private fun LookTokens(look: Look, colorTheme: ColorTheme, content: @Composable () -> Unit) {
    val colors = remember(colorTheme) { colorsFor(colorTheme, Color(0xFF14213D), null) }
    val type = remember(look) { NulisTypography(look = look) }
    CompositionLocalProvider(
        LocalLook provides look,
        LocalNulisColors provides colors,
        LocalNulisTypography provides type,
        content = content,
    )
}

@Composable
private fun AppsStep(
    apps: List<AppInfo>,
    favoriteIds: Set<String>,
    maxFavorites: Int,
    iconStyle: IconStyle,
    onToggle: (AppInfo) -> Unit,
    onNext: () -> Unit,
) {
    val full = favoriteIds.size >= maxFavorites
    // Picking apps by name alone is picking from a phone book. Whatever the drawer is set to,
    // this list shows the real icons, because this is where somebody recognises their apps.
    val rowStyle = if (iconStyle.mode.hasIcon) iconStyle else iconStyle.copy(mode = IconMode.ICON)
    StepFrame(
        label = "Step 3",
        title = "Pick your apps",
        blurb = "The few you open every day, for the home page. Everything else is one swipe up, in the drawer.",
        primary = if (favoriteIds.isEmpty()) "Skip" else "Next (${favoriteIds.size})",
        onPrimary = onNext,
        body = {
            LazyColumn(Modifier.fillMaxSize().fadeTop()) {
                items(apps, key = { it.id }) { app ->
                    val checked = app.id in favoriteIds
                    val enabled = checked || !full
                    ListRow(
                        title = app.label,
                        enabled = enabled,
                        onClick = { onToggle(app) },
                        leading = { AppGlyph(app, rowStyle) },
                        trailing = { NulisToggle(checked = checked, enabled = enabled, onCheckedChange = { onToggle(app) }) },
                    )
                }
            }
        },
    )
}

@Composable
private fun DefaultLauncherStep(onRefresh: () -> Unit, onNext: () -> Unit) {
    val ctx = LocalContext.current
    var asked by remember { mutableStateOf(0) }
    val isDefault = remember(asked) { isDefaultLauncher(ctx) }
    val requestRole = rememberHomeRoleRequest { asked += 1 }
    StepFrame(
        label = "Step 4",
        // Around twenty characters is what one line of displayM holds on a phone; the old titles
        // ran to twenty-five and left "screen" stranded on a line of its own. The blurb underneath
        // already says what this step is about.
        title = if (isDefault) "Nulis is your home" else "Make Nulis your home",
        blurb = if (isDefault) {
            "Pressing Home already brings you here. Nothing else to do."
        } else {
            "Android asks you first. Nulis cannot set this for you, and no app can."
        },
        primary = if (isDefault) "Next" else "Make it my home",
        onPrimary = {
            if (isDefault) {
                onNext()
            } else {
                requestRole()
                onRefresh()
            }
        },
        secondary = if (isDefault) null else "Skip",
        onSecondary = if (isDefault) null else onNext,
        body = {
            Column {
                Hairline()
                Text(
                    "You can undo this at any time in Android's settings, under Default apps.",
                    style = NulisTheme.type.bodyM,
                    color = NulisTheme.colors.secondary,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
                Hairline()
                if (asked > 0 && !isDefault) {
                    Spacer(Modifier.height(12.dp))
                    Caption("Come back here when you have chosen")
                }
            }
        },
    )
}

@Composable
private fun PermissionsStep(permissions: List<OnboardingPermission>, onNext: () -> Unit) {
    StepFrame(
        label = "Step 5",
        title = "Optional permissions",
        blurb = "Every one of these is optional and each powers exactly one block. Skip them all if you like.",
        primary = "Done",
        onPrimary = onNext,
        body = {
            // Scrolls, because at a 200% font scale three rows of a title, a sentence and a
            // button are taller than what is left of a 720 x 1280 screen, and the third one -
            // whichever it is - simply was not there.
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                permissions.forEachIndexed { index, permission ->
                    ListRow(
                        title = permission.title,
                        subtitle = permission.reason,
                        onClick = { if (!permission.granted) permission.request() },
                        enabled = !permission.granted,
                        divider = index != permissions.lastIndex,
                        trailing = {
                            if (permission.granted) {
                                Caption("Allowed")
                            } else {
                                PillButton(text = "Allow", onClick = permission.request, compact = true)
                            }
                        },
                    )
                }
            }
        },
    )
}

@Composable
private fun Done(onFinish: () -> Unit) {
    val colors = NulisTheme.colors
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))
        Text("All set", style = NulisTheme.type.displayL, color = colors.onBackground)
        Spacer(Modifier.height(16.dp))
        Text(
            "Long-press anywhere to edit this page: drag blocks about, resize them, add and remove. Swipe left or right for the other pages.",
            style = NulisTheme.type.bodyL,
            color = colors.secondary,
        )
        Spacer(Modifier.height(24.dp))
        Caption("Settings live behind the gear in edit mode, or in the drawer", lines = 2)
        Spacer(Modifier.weight(1f))
        PillButton(
            text = "Go to my home screen",
            onClick = onFinish,
            tone = PillTone.Primary,
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        )
    }
}

/** True when pressing Home already opens Nulis. */
fun isDefaultLauncher(context: Context): Boolean {
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    @Suppress("DEPRECATION")
    val resolved = runCatching {
        context.packageManager.resolveActivity(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
    }.getOrNull()
    return resolved?.activityInfo?.packageName == context.packageName
}

/**
 * Asks to become the home app, the way Android would rather be asked.
 *
 * From Android 10 there is a role for this: the system puts up its own small dialog - "Make Nulis
 * your default Home app?" - and the answer is one tap. Before that, and on any build where the
 * role is not available or the request cannot be made, this falls back to opening the settings
 * screen where the home app is chosen, which is what Nulis always used to do: a trip into
 * Settings, three taps, and finding your way back.
 *
 * Either way Nulis never sets it. No app can, and that is exactly as it should be.
 */
@Composable
fun rememberHomeRoleRequest(onResult: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { onResult() }
    return {
        val intent = homeRoleIntent(context)
        val asked = intent != null && runCatching { launcher.launch(intent) }.isSuccess
        if (!asked) {
            openHomeSettings(context)
            onResult()
        }
    }
}

/** The system's own "make this your Home app" dialog, or null when this phone has no such thing. */
private fun homeRoleIntent(context: Context): Intent? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    return runCatching {
        val manager = context.getSystemService(RoleManager::class.java) ?: return null
        if (!manager.isRoleAvailable(RoleManager.ROLE_HOME)) return null
        if (manager.isRoleHeld(RoleManager.ROLE_HOME)) return null
        manager.createRequestRoleIntent(RoleManager.ROLE_HOME)
    }.getOrNull()
}

/**
 * Opens the system screen where the home app is chosen. Android gives no way for an app to set
 * itself as launcher, which is exactly as it should be.
 */
fun openHomeSettings(context: Context) {
    val attempts = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(Intent(Settings.ACTION_HOME_SETTINGS))
        add(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }
    for (intent in attempts) {
        val started = runCatching {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
        if (started) return
    }
}

/** The runtime permission onboarding can ask for directly; the rest are system screens. */
@SuppressLint("InlinedApi")
val ActivityRecognition: String = Manifest.permission.ACTIVITY_RECOGNITION

/** A launcher for the one runtime permission in the list, remembered by the caller. */
@Composable
fun rememberActivityRecognitionRequest(onResult: () -> Unit): () -> Unit {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onResult() }
    return { launcher.launch(ActivityRecognition) }
}

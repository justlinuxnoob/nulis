// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.apps

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nulis.launcher.R
import com.nulis.launcher.apps.AppInfo
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.GhostDiscs
import com.nulis.launcher.blocks.GridSpan
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.blocks.blockArea
import com.nulis.launcher.blocks.sampleApp
import com.nulis.launcher.blocks.sampleApps
import com.nulis.launcher.blocks.screentime.formatMinutes
import com.nulis.launcher.icons.AppGlyph
import com.nulis.launcher.icons.IconMode
import com.nulis.launcher.icons.IconShape
import com.nulis.launcher.icons.IconSize
import com.nulis.launcher.icons.IconStyle
import com.nulis.launcher.icons.IconStylePicker
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisTheme
import kotlin.math.ceil

/**
 * The apps you chose, stored in the block's settings as newline-separated app ids. The two styles
 * are the two layouts - a stack of rows or a grid of tiles - and what each app looks like inside
 * them is the block's own [IconStyle].
 *
 * How many it draws is a question about the rectangle, not about the block type: as many as fit,
 * and a line saying how many more are waiting for a bigger block. It used to be six, full stop,
 * which was wrong at both ends - six is more than a one-row block can show and far fewer than a
 * full page of icons can.
 */
object AppsBlockDefinition : BlockDefinition {
    override val type = "apps"
    override val label = "Apps"

    override val minSpan = GridSpan(1, 1)
    override val defaultSpan = GridSpan(6, 3)
    override val styles: List<BlockStyle> = listOf(ListStyle, GridStyle)

    /**
     * The most apps a block will hold. Not how many it draws: that is whatever fits, and the
     * rest are announced rather than silently dropped. Thirty is a screen of small icons, which
     * is the most a page can honestly show.
     */
    const val MAX_FAVORITES = 30
    private const val KEY_FAVORITES = "favorites"
    private const val SEPARATOR = "\n"

    /** Ids of the styles as they were before the layouts and the icon options were split apart. */
    const val LEGACY_CIRCLES = "circles"
    const val LEGACY_SQUARES = "squares"

    fun favoriteIds(block: Block): List<String> =
        block.settings[KEY_FAVORITES]?.split(SEPARATOR)?.filter { it.isNotBlank() } ?: emptyList()

    fun withFavoriteIds(block: Block, ids: List<String>): Block =
        block.copy(settings = block.settings + (KEY_FAVORITES to ids.take(MAX_FAVORITES).joinToString(SEPARATOR)))

    /** Adds the app if the block is not already full, or removes it if it is already there. */
    fun toggleFavorite(block: Block, appId: String): Block {
        val current = favoriteIds(block)
        val updated = when {
            appId in current -> current - appId
            current.size < MAX_FAVORITES -> current + appId
            else -> current
        }
        return withFavoriteIds(block, updated)
    }

    /** Favorites that are still installed, in user order. */
    fun favoriteApps(block: Block, apps: List<AppInfo>): List<AppInfo> {
        val byId = apps.associateBy { it.id }
        return favoriteIds(block).mapNotNull { byId[it] }
    }

    /**
     * The favourites this block draws, with anything a focus session is hiding taken out. Faded
     * apps stay: seeing what you are not doing is half the point of the session.
     */
    private fun shownApps(block: Block, context: BlockContext): List<AppInfo> {
        val all = favoriteApps(block, context.apps)
        return if (context.muteHides) all.filterNot { it.packageName in context.mutedPackages } else all
    }

    /** Text only, so an Apps block added today looks exactly like the one that was there before. */
    private val DefaultIcons = IconStyle(mode = IconMode.TEXT)

    fun iconStyle(block: Block): IconStyle = IconStyle.from(block.settings, DefaultIcons)

    fun withIconStyle(block: Block, style: IconStyle): Block = block.copy(settings = block.settings + style.toMap())

    /**
     * Turns a block saved before the layouts and the icon options were separate back into what
     * it looked like: the two monogram styles become the grid layout with that shape, at the
     * size their block size used to imply.
     */
    fun migrateLegacyStyle(block: Block): Block {
        val shape = when (block.style) {
            LEGACY_CIRCLES -> IconShape.CIRCLE
            LEGACY_SQUARES -> IconShape.SQUARE
            else -> return block
        }
        val style = IconStyle(
            mode = IconMode.MONOGRAM,
            shape = shape,
            size = if (block.size == BlockSize.WIDE) IconSize.LARGE else IconSize.MEDIUM,
        )
        return withIconStyle(block.copy(style = GridStyle.id), style)
    }

    /** Previews show the apps already on the home page, so a picker looks like this phone. */
    override fun previewSettings(context: BlockContext): Map<String, String> =
        mapOf(KEY_FAVORITES to context.sampleApps.take(4).joinToString(SEPARATOR) { it.id })

    override val hasOptions: Boolean get() = true

    @Composable
    override fun Options(block: Block, context: BlockContext, onUpdate: (Block) -> Unit) {
        var pickerOpen by remember { mutableStateOf(false) }
        val favorites = favoriteIds(block)
        Row(verticalAlignment = Alignment.CenterVertically) {
            PillButton(text = stringResource(R.string.apps_choose), onClick = { pickerOpen = true })
            Spacer(Modifier.width(16.dp))
            Caption("${favorites.size} / $MAX_FAVORITES")
        }
        val sample = favoriteApps(block, context.apps).firstOrNull() ?: context.sampleApp
        if (sample != null) {
            Spacer(Modifier.height(16.dp))
            Column {
                IconStylePicker(
                    style = iconStyle(block),
                    sample = sample,
                    onChange = { onUpdate(withIconStyle(block, it)) },
                )
            }
        }
        if (pickerOpen) {
            FavoritesPicker(block, context, onUpdate) { pickerOpen = false }
        }
    }

    /** One app per line, down the page, as many lines as the block is tall enough to hold. */
    private object ListStyle : BlockStyle {
        override val id = "list"
        override val label = "List"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) =
            AppsFrame(block, context, modifier) { favorites ->
            val icons = iconStyle(block)
            val wide = block.size == BlockSize.WIDE
            val type = NulisTheme.type
            val labelStyle = if (wide) type.bodyXl else type.bodyXl.copy(fontSize = 22.sp, lineHeight = 26.sp)
            val pad = if (wide) 8.dp else 5.dp
            val density = LocalDensity.current
            val rowHeight = with(density) {
                val glyph = if (icons.mode.hasGlyph) icons.size.dp else 0.dp
                val text = if (icons.mode.hasLabel) labelStyle.lineHeight.toDp() else 0.dp
                maxOf(glyph, text) + pad * 2
            }
            // The block's own rectangle, not the incoming constraints: BlockFrame measures its
            // content with an unbounded height on purpose, so that it can scale whatever
            // overflows. Asking the constraints how tall the block is would always answer
            // "as tall as you like", and the block would draw every app and then shrink.
            val room = blockArea().height
            val notice = noticeHeight()
            Box(modifier) {
                val plan = fitPlan(
                    total = favorites.size,
                    capacity = rowsThatFit(room * OverflowTolerance, rowHeight),
                    withNotice = rowsThatFit(room - notice, rowHeight),
                )
                BlockColumn(modifier = Modifier.fillMaxWidth()) {
                    favorites.take(plan.shown).forEach { app ->
                        val minutes = context.appUsage?.get(app.packageName)
                        BlockRow(
                            modifier = Modifier
                                .alpha(if (app.packageName in context.mutedPackages) MutedAlpha else 1f)
                                .launchable(app, context)
                                .padding(vertical = pad),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (icons.mode.hasGlyph) {
                                AppGlyph(app, icons)
                                if (icons.mode.hasLabel) Spacer(Modifier.width(16.dp))
                            }
                            if (icons.mode.hasLabel) {
                                Text(
                                    text = app.label,
                                    style = labelStyle,
                                    color = NulisTheme.colors.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                            }
                            if (minutes != null && minutes > 0) {
                                Caption(formatMinutes(minutes), modifier = Modifier.padding(start = 12.dp))
                            }
                        }
                    }
                    if (plan.hidden > 0) MoreNotice(plan.hidden)
                }
            }
        }
    }

    /** Apps flowing across the block, on as many lines as it is tall enough to hold. */
    private object GridStyle : BlockStyle {
        override val id = "grid"
        override val label = "Grid"

        @OptIn(ExperimentalLayoutApi::class)
        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) =
            AppsFrame(block, context, modifier) { favorites ->
            val icons = iconStyle(block)
            val wide = block.size == BlockSize.WIDE
            val gap = if (wide) 16.dp else 12.dp
            val density = LocalDensity.current
            val labelStyle = if (icons.mode.hasGlyph) NulisTheme.type.bodyS else NulisTheme.type.bodyL
            BoxWithConstraints(modifier.fillMaxWidth()) {
                val cell = when {
                    !icons.mode.hasGlyph -> 0.dp
                    icons.mode.hasLabel -> icons.size.dp + 24.dp
                    else -> icons.size.dp
                }
                val cellHeight = with(density) {
                    val glyph = if (icons.mode.hasGlyph) icons.size.dp else 0.dp
                    val text = if (icons.mode.hasLabel) labelStyle.lineHeight.toDp() + (if (icons.mode.hasGlyph) 6.dp else 0.dp) else 0.dp
                    glyph + text
                }
                val perRow = if (cell <= 0.dp) favorites.size else (((maxWidth + gap) / (cell + gap)).toInt()).coerceAtLeast(1)
                // See the list style: the height has to come from the block's rectangle.
                val room = blockArea().height + gap
                val notice = noticeHeight()
                val plan = fitPlan(
                    total = favorites.size,
                    capacity = perRow * rowsThatFit(room * OverflowTolerance, cellHeight + gap).coerceAtLeast(1),
                    withNotice = perRow * rowsThatFit(room - notice, cellHeight + gap).coerceAtLeast(1),
                )
                // Six apps on a row that fits five should be three and three, not five and a
                // lonely one. The rows are evened out rather than filled greedily.
                val rows = ceil(plan.shown / perRow.toFloat()).toInt().coerceAtLeast(1)
                val balanced = ceil(plan.shown / rows.toFloat()).toInt().coerceAtLeast(1)
                BlockColumn(Modifier.fillMaxWidth()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(gap, blockAlign().horizontal),
                        verticalArrangement = Arrangement.spacedBy(gap),
                        maxItemsInEachRow = balanced,
                    ) {
                        favorites.take(plan.shown).forEach { app ->
                            Column(
                                // With names only the cells are just the words, wrapping across the block.
                                modifier = Modifier
                                    .alpha(if (app.packageName in context.mutedPackages) MutedAlpha else 1f)
                                    .then(if (icons.mode.hasGlyph) Modifier.width(if (icons.mode.hasLabel) icons.size.dp + 24.dp else icons.size.dp) else Modifier)
                                    .launchable(app, context),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AppGlyph(app, icons)
                                if (icons.mode.hasLabel) {
                                    Text(
                                        text = app.label,
                                        style = labelStyle,
                                        color = NulisTheme.colors.onBackground,
                                        maxLines = 1,
                                        textAlign = TextAlign.Center,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = if (icons.mode.hasGlyph) 6.dp else 0.dp),
                                    )
                                }
                            }
                        }
                    }
                    if (plan.hidden > 0) MoreNotice(plan.hidden)
                }
            }
        }
    }

    /**
     * How many of the chosen apps a rectangle this size can hold, and how many are left over.
     *
     * When some are left over the notice takes the place of one of them, because a block that
     * silently drew five of your nine apps and said nothing was indistinguishable from a block
     * that had lost four.
     */
    private data class FitPlan(val shown: Int, val hidden: Int)

    /**
     * [capacity] is how many fit with the whole rectangle to play with, [withNotice] how many
     * still fit once the line about the rest has taken its share. Only the second one is a whole
     * app row shorter when the notice pushes past a row boundary, which is why both are asked
     * for rather than simply subtracting one.
     */
    /**
     * How much taller than its rectangle the content may be before the block starts leaving apps
     * out.
     *
     * Not zero, because [com.nulis.launcher.home.BlockFrame] already scales a block whose content
     * slightly overflows, and that is how every other block in the launcher fits - three of the
     * shipped layouts give a six-app text list four rows on purpose and have always relied on it.
     * Truncating at the first pixel of overflow turned those into "four apps and 2 MORE" out of
     * the box. A third over is a scale of about 77%, which still reads; past that the answer is
     * honestly that the block is too small.
     */
    private const val OverflowTolerance = 1.3f

    private fun fitPlan(total: Int, capacity: Int, withNotice: Int): FitPlan {
        if (capacity >= total) return FitPlan(total, 0)
        val shown = withNotice.coerceIn(0, total - 1)
        return FitPlan(shown, total - shown)
    }

    /** What [MoreNotice] costs: one line of the label face plus the air above it. */
    @Composable
    private fun noticeHeight(): Dp =
        with(LocalDensity.current) { NulisTheme.type.label.lineHeight.toDp() } + 4.dp

    /** Whole rows of [rowHeight] inside [room], never fewer than none and never a negative. */
    private fun rowsThatFit(room: Dp, rowHeight: Dp): Int =
        if (rowHeight <= 0.dp || room <= 0.dp) Int.MAX_VALUE else (room / rowHeight).toInt().coerceAtLeast(0)

    /** The one line the block says about itself: there are more, and this rectangle is too small. */
    @Composable
    private fun MoreNotice(hidden: Int) {
        // No fillMaxWidth: the column it sits in hangs it from the block's own alignment edge.
        Caption(
            text = pluralStringResource(R.plurals.apps_more_hidden, hidden, hidden),
            modifier = Modifier.padding(top = 4.dp),
        )
    }

    /** Instant press feedback plus a tap that launches [app] from the item's own on-screen bounds. */
    @Composable
    private fun Modifier.launchable(app: AppInfo, context: BlockContext): Modifier {
        var coordinates: LayoutCoordinates? = null
        return this
            .onGloballyPositioned { coordinates = it }
            .pressFeedback()
            // An icon on its own says nothing to a screen reader, so the app's name is always
            // the tile's name, whether or not it is written under it.
            .semantics { contentDescription = app.label }
            .clickable(onClickLabel = "Open") { context.onLaunchApp(app, coordinates?.takeIf { it.isAttached }?.boundsInWindow()) }
    }

    /** How far an app is faded while a focus session is guarding it. */
    private const val MutedAlpha = 0.3f

    /**
     * What every Apps style is wrapped in: the apps if there are any, the way to choose some if
     * there are not, and the chooser itself.
     *
     * The open-or-closed state of the chooser lives here, above the has-apps-or-not branch,
     * because choosing your first app takes the block out of its empty state - and a chooser
     * remembered inside that state would be pulled out of composition by the first tap, closing
     * itself the instant it was used.
     */
    @Composable
    private fun AppsFrame(
        block: Block,
        context: BlockContext,
        modifier: Modifier,
        content: @Composable (List<AppInfo>) -> Unit,
    ) {
        var pickerOpen by remember { mutableStateOf(false) }
        val favorites = shownApps(block, context)
        if (favorites.isEmpty()) EmptyState(modifier) { pickerOpen = true } else content(favorites)
        if (pickerOpen) {
            FavoritesPicker(
                block = block,
                context = context,
                onUpdate = context.updateBlock,
                onDone = { pickerOpen = false },
            )
        }
    }

    /**
     * What the block looks like before you have chosen anything, and the way in.
     *
     * The hint used to say "long-press to pick your apps", which stopped being true the night a
     * long press anywhere started opening the editor: following it got you the grid and an
     * outline, not a list of apps. A tap is what it costs now, and the tap works.
     */
    @Composable
    private fun EmptyState(modifier: Modifier = Modifier, onPick: () -> Unit) {
        BlockColumn(
            modifier = modifier.fillMaxWidth().pressFeedback().clickable(onClick = onPick),
        ) {
            // The slots first, so the block looks like an apps block before it is read as an
            // instruction, and the words second, because this is the one empty state where what
            // to do is not obvious from the shape.
            GhostDiscs(count = 3, size = 40.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.apps_hint_no_favorites),
                style = NulisTheme.type.bodyM,
                color = NulisTheme.colors.secondary,
                textAlign = blockAlign().textAlign,
            )
        }
    }

    /** The chooser, shared by the block's own empty state and by its options sheet. */
    @Composable
    private fun FavoritesPicker(
        block: Block,
        context: BlockContext,
        onUpdate: (Block) -> Unit,
        onDone: () -> Unit,
    ) {
        Dialog(
            onDismissRequest = onDone,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            AppsPickerScreen(
                apps = context.apps,
                favoriteIds = favoriteIds(block).toSet(),
                maxFavorites = MAX_FAVORITES,
                iconStyle = iconStyle(block),
                onToggle = { app -> onUpdate(toggleFavorite(block, app.id)) },
                onDone = onDone,
            )
        }
    }
}

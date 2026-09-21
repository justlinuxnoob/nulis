// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.tasks

import com.nulis.launcher.blocks.GridSpan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.ChecklistRows
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.blocks.ScreenRequest
import com.nulis.launcher.blocks.writing.Task
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.Hairline
import com.nulis.launcher.ui.components.NulisCheck
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.NulisTextField
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.components.fadeTop
import com.nulis.launcher.ui.theme.NulisHaptics
import androidx.compose.foundation.layout.Box
import com.nulis.launcher.ui.theme.NulisTheme

object TasksBlockDefinition : BlockDefinition {
    override val type = "tasks"
    override val label = "Tasks"

    override val minSpan = GridSpan(2, 1)
    override val defaultSpan = GridSpan(6, 3)
    override val styles: List<BlockStyle> = listOf(ChecklistStyle, CountStyle)

    @Composable
    override fun Screen(request: ScreenRequest, context: BlockContext, onClose: () -> Unit) = TasksScreen(context, onClose)

    /** Open tasks with check boxes; tapping one checks it off right on the page. */
    private object ChecklistStyle : BlockStyle {
        override val id = "checklist"
        override val label = "Checklist"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val open = context.writing.tasks.filter { !it.done }
            val shown = open.take(if (wide) 6 else 4)
            BlockColumn(modifier) {
                BlockCaptionRow(
                    label = "Tasks",
                    trailing = if (open.size > shown.size) "+${open.size - shown.size} more" else null,
                    modifier = Modifier
                        .pressFeedback()
                        .clickable(remember { MutableInteractionSource() }, indication = null) { context.openScreen(ScreenRequest(type)) }
                        .padding(bottom = 4.dp),
                )
                if (open.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .pressFeedback()
                            .clickable(remember { MutableInteractionSource() }, indication = null) { context.openScreen(ScreenRequest(type)) }
                            .padding(vertical = 6.dp),
                    ) {
                        // A list with nothing on it is empty rows; a list you have finished is
                        // the one thing worth saying in words.
                        if (context.writing.tasks.isEmpty()) {
                            ChecklistRows(rows = if (wide) 3 else 2)
                        } else {
                            Text(
                                "All done",
                                style = NulisTheme.type.bodyM,
                                color = colors.secondary,
                                textAlign = blockAlign().textAlign,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                shown.forEach { task -> TaskRow(task, context, wide) }
            }
        }
    }

    /** How many are left, big, with the next one underneath. */
    private object CountStyle : BlockStyle {
        override val id = "count"
        override val label = "Count"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val colors = NulisTheme.colors
            val wide = block.size == BlockSize.WIDE
            val open = context.writing.tasks.filter { !it.done }
            BlockColumn(
                modifier
                    .pressFeedback()
                    .clickable(remember { MutableInteractionSource() }, indication = null) { context.openScreen(ScreenRequest(type)) },
            ) {
                BlockRow(verticalAlignment = Alignment.Bottom) {
                    Text(open.size.toString(), style = if (wide) NulisTheme.type.displayL else NulisTheme.type.displayM, color = colors.onBackground)
                    Spacer(Modifier.width(12.dp))
                    Caption(if (open.size == 1) "Open task" else "Open tasks", modifier = Modifier.padding(bottom = if (wide) 8.dp else 4.dp))
                }
                open.firstOrNull()?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it.text, style = NulisTheme.type.bodyM, color = colors.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }

    @Composable
    private fun TaskRow(task: Task, context: BlockContext, wide: Boolean) {
        val colors = NulisTheme.colors
        val haptics = LocalHapticFeedback.current
        BlockRow(
            modifier = Modifier
                .pressFeedback()
                .clickable(remember { MutableInteractionSource() }, indication = null) {
                    haptics.performHapticFeedback(NulisHaptics.tick)
                    context.writingActions.toggleTask(task.id)
                }
                .padding(vertical = if (wide) 8.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NulisCheck(checked = task.done)
            Spacer(Modifier.width(14.dp))
            Text(
                task.text,
                style = if (wide) NulisTheme.type.bodyXl.copy(fontSize = NulisTheme.type.bodyL.fontSize * 1.2f, lineHeight = NulisTheme.type.bodyL.lineHeight * 1.2f) else NulisTheme.type.bodyL,
                color = colors.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Add field on top, open tasks then done ones; done tasks strike through and can be cleared at once. */
@Composable
private fun TasksScreen(context: BlockContext, onClose: () -> Unit) {
    val colors = NulisTheme.colors
    val haptics = LocalHapticFeedback.current
    val actions = context.writingActions
    var draft by remember { mutableStateOf("") }
    fun submit() {
        if (draft.isBlank()) return
        haptics.performHapticFeedback(NulisHaptics.confirm)
        actions.addTask(draft.trim())
        draft = ""
    }
    val open = context.writing.tasks.count { !it.done }
    val done = context.writing.tasks.size - open
    NulisScreen(label = "Tasks", title = if (open == 1) "1 open" else "$open open", onBack = onClose, trailing = {
        if (done > 0) {
            PillButton(text = "Clear done", onClick = { actions.clearDoneTasks() }, tone = PillTone.Danger, compact = true)
            Spacer(Modifier.width(8.dp))
        }
        NulisIconButton(Glyph.Close, onClick = onClose, bordered = true)
    }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NulisTextField(
                value = draft,
                onValueChange = { draft = it },
                placeholder = "Add a task",
                modifier = Modifier.weight(1f),
                singleLine = true,
                imeAction = ImeAction.Done,
                onImeAction = { submit() },
            )
            Spacer(Modifier.width(12.dp))
            PillButton(text = "Add", onClick = { submit() }, tone = PillTone.Primary, compact = true, enabled = draft.isNotBlank())
        }
        Spacer(Modifier.height(8.dp))
        Hairline()
        LazyColumn(Modifier.fillMaxSize().fadeTop()) {
            items(context.writing.tasks, key = { it.id }) { task ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .pressFeedback()
                        .clickable(remember { MutableInteractionSource() }, indication = null) {
                            haptics.performHapticFeedback(NulisHaptics.tick)
                            actions.toggleTask(task.id)
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NulisCheck(checked = task.done)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        task.text,
                        style = NulisTheme.type.bodyL.copy(textDecoration = if (task.done) TextDecoration.LineThrough else null),
                        color = if (task.done) colors.tertiary else colors.onBackground,
                        modifier = Modifier.weight(1f),
                    )
                    NulisIconButton(Glyph.Close, onClick = { actions.deleteTask(task.id) })
                }
                Hairline()
            }
        }
    }
}

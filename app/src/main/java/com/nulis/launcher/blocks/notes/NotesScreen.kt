// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.writing.formatStamp
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.GlyphIcon
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.NulisScreen
import com.nulis.launcher.ui.components.NulisTextField
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.PillTone
import com.nulis.launcher.ui.components.fadeTop
import com.nulis.launcher.ui.theme.NulisTheme
import kotlinx.coroutines.delay

/** The notes list, or one note's editor when [initialNoteId] is set (or [NotesBlockDefinition.NEW]). */
@Composable
fun NotesScreen(initialNoteId: String?, context: BlockContext, onClose: () -> Unit) {
    var editingId by remember(initialNoteId) {
        mutableStateOf(if (initialNoteId == NotesBlockDefinition.NEW) context.writingActions.addNote() else initialNoteId)
    }
    val fromList = initialNoteId == null
    val id = editingId
    if (id == null) {
        NotesList(context, onOpen = { editingId = it }, onNew = { editingId = context.writingActions.addNote() }, onClose = onClose)
    } else {
        NoteEditor(id, context, onBack = { if (fromList) editingId = null else onClose() })
    }
}

@Composable
private fun NotesList(context: BlockContext, onOpen: (String) -> Unit, onNew: () -> Unit, onClose: () -> Unit) {
    val colors = NulisTheme.colors
    NulisScreen(label = "Notes", title = "${context.writing.notes.size} notes", onBack = onClose, trailing = {
        PillButton(text = "New note", onClick = onNew, tone = PillTone.Primary, leading = { GlyphIcon(Glyph.Plus, colors.background, size = 14.dp) })
        Spacer(Modifier.width(8.dp))
        NulisIconButton(Glyph.Close, onClick = onClose, bordered = true)
    }) {
        if (context.writing.notes.isEmpty()) {
            Text("Nothing here yet.", style = NulisTheme.type.bodyM, color = colors.secondary)
        }
        LazyColumn(Modifier.fillMaxSize().fadeTop()) {
            items(context.writing.notes, key = { it.id }) { note ->
                ListRow(
                    title = note.title.ifEmpty { "Empty note" },
                    titleColor = if (note.title.isEmpty()) colors.tertiary else null,
                    subtitle = formatStamp(note.updatedAt, context.time),
                    onClick = { onOpen(note.id) },
                    leading = if (note.pinned) ({ Box(Modifier.size(6.dp).background(colors.secondary, CircleShape)) }) else null,
                )
            }
        }
    }
}

@Composable
private fun NoteEditor(id: String, context: BlockContext, onBack: () -> Unit) {
    val colors = NulisTheme.colors
    val note = context.writing.notes.firstOrNull { it.id == id }
    var text by remember(id) { mutableStateOf(note?.text ?: "") }
    val actions = context.writingActions
    val latest by rememberUpdatedState(text)
    // Deleting leaves the editor, and the save on the way out would write the note straight back.
    var deleted by remember(id) { mutableStateOf(false) }
    val isDeleted by rememberUpdatedState(deleted)
    // Save shortly after typing pauses, and once more on leaving.
    LaunchedEffect(text) {
        delay(300)
        if (!deleted && text != note?.text) actions.updateNote(id, text)
    }
    DisposableEffect(id) { onDispose { if (!isDeleted) actions.updateNote(id, latest) } }
    val focus = remember { FocusRequester() }
    LaunchedEffect(id) { if ((note?.text ?: "").isEmpty()) focus.requestFocus() }

    NulisScreen(
        label = if (note?.pinned == true) "Pinned note" else "Note",
        title = note?.let { formatStamp(it.updatedAt, context.time) } ?: "New",
        onBack = onBack,
        trailing = {
            PillButton(text = if (note?.pinned == true) "Unpin" else "Pin", onClick = { actions.togglePin(id) }, compact = true)
            Spacer(Modifier.width(8.dp))
            PillButton(text = "Delete", onClick = { deleted = true; actions.deleteNote(id); onBack() }, tone = PillTone.Danger, compact = true)
            Spacer(Modifier.width(8.dp))
            NulisIconButton(Glyph.ArrowLeft, onClick = onBack, bordered = true)
        },
    ) {
        NulisTextField(
            value = text,
            onValueChange = { text = it },
            placeholder = "Write",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .focusRequester(focus)
                .padding(bottom = 16.dp),
        )
    }
}

// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.writing

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import kotlinx.serialization.Serializable

@Serializable
data class Note(
    val id: String,
    val text: String = "",
    val pinned: Boolean = false,
    val updatedAt: Long,
) {
    /** First non-empty line, as the note's title in lists. */
    val title: String get() = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: ""
}

@Serializable
data class JournalEntry(val id: String, val text: String, val createdAt: Long)

@Serializable
data class Task(val id: String, val text: String, val done: Boolean = false, val createdAt: Long)

/** Everything the writing blocks show. Pinned notes first, newest journal entries first, open tasks first. */
@Immutable
data class WritingState(
    val notes: List<Note> = emptyList(),
    val journal: List<JournalEntry> = emptyList(),
    val tasks: List<Task> = emptyList(),
)

/** What the writing blocks and their editors can do. Implemented by the view model. */
@Stable
interface WritingActions {
    fun addNote(text: String = ""): String
    fun updateNote(id: String, text: String)
    fun togglePin(id: String)
    fun deleteNote(id: String)
    fun addJournalEntry(text: String)
    fun deleteJournalEntry(id: String)
    fun addTask(text: String)
    fun toggleTask(id: String)
    fun deleteTask(id: String)
    fun clearDoneTasks()

    object None : WritingActions {
        override fun addNote(text: String) = ""
        override fun updateNote(id: String, text: String) = Unit
        override fun togglePin(id: String) = Unit
        override fun deleteNote(id: String) = Unit
        override fun addJournalEntry(text: String) = Unit
        override fun deleteJournalEntry(id: String) = Unit
        override fun addTask(text: String) = Unit
        override fun toggleTask(id: String) = Unit
        override fun deleteTask(id: String) = Unit
        override fun clearDoneTasks() = Unit
    }
}

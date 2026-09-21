// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.blocks.contacts

import com.nulis.launcher.blocks.GridSpan

import androidx.core.net.toUri
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nulis.launcher.blocks.Block
import com.nulis.launcher.blocks.BlockCaptionRow
import com.nulis.launcher.blocks.BlockColumn
import com.nulis.launcher.blocks.BlockContext
import com.nulis.launcher.blocks.BlockDefinition
import com.nulis.launcher.blocks.BlockRow
import com.nulis.launcher.blocks.BlockSize
import com.nulis.launcher.blocks.BlockStyle
import com.nulis.launcher.blocks.GhostDiscs
import com.nulis.launcher.blocks.blockAlign
import com.nulis.launcher.ui.components.Caption
import com.nulis.launcher.ui.components.Glyph
import com.nulis.launcher.ui.components.ListRow
import com.nulis.launcher.ui.components.NulisIconButton
import com.nulis.launcher.ui.components.NulisToggle
import com.nulis.launcher.ui.components.PillButton
import com.nulis.launcher.ui.components.SectionLabel
import com.nulis.launcher.ui.components.pressFeedback
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme

/** A person the user pinned. Only what a shortcut needs: a name and a number. */
data class ContactShortcut(val name: String, val number: String) {
    val initials: String
        get() = name.split(' ', '-').filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }
            .ifEmpty { "?" }
}

/**
 * The three or four people you actually call.
 *
 * **No contacts permission.** Picking goes through Android's own contact picker, which hands
 * back one row with a temporary read grant; Nulis reads the name and number out of that row at
 * that moment and keeps only those two strings. It never has access to the address book.
 */
object ContactsBlockDefinition : BlockDefinition {
    override val type = "contacts"
    override val label = "People"

    override val minSpan = GridSpan(2, 1)
    override val defaultSpan = GridSpan(6, 2)
    override val styles: List<BlockStyle> = listOf(CirclesStyle, ListStyle)
    override val previewHeight get() = 110.dp

    private const val KEY_ITEMS = "people"
    private const val KEY_MESSAGE = "tap_message"
    // ASCII unit and record separators: neither can be typed into a contact name.
    private const val ROW = "\u001F"
    private const val FIELD = "\u001E"

    fun items(block: Block): List<ContactShortcut> = block.settings[KEY_ITEMS]
        ?.split(ROW)
        ?.filter { it.isNotBlank() }
        ?.mapNotNull { row ->
            val parts = row.split(FIELD)
            val number = parts.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ContactShortcut(parts.getOrNull(0).orEmpty().ifBlank { number }, number)
        }
        .orEmpty()

    fun withItems(block: Block, items: List<ContactShortcut>): Block = block.copy(
        settings = block.settings + (KEY_ITEMS to items.joinToString(ROW) { "${it.name}$FIELD${it.number}" }),
    )

    private fun tapMessages(block: Block): Boolean = block.settings[KEY_MESSAGE] == "1"

    override fun previewSettings(context: BlockContext): Map<String, String> = mapOf(
        KEY_ITEMS to listOf("Mum" to "1", "Sam" to "2", "Ana" to "3")
            .joinToString(ROW) { "${it.first}$FIELD${it.second}" },
    )

    override val hasOptions: Boolean get() = true

    @Composable
    override fun Options(block: Block, context: BlockContext, onUpdate: (Block) -> Unit) {
        val appContext = LocalContext.current
        val current = items(block)
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@rememberLauncherForActivityResult
            readPicked(appContext, uri)?.let { onUpdate(withItems(block, current + it)) }
        }
        Column(Modifier.fillMaxWidth()) {
            SectionLabel("People")
            current.forEachIndexed { index, person ->
                ListRow(
                    title = person.name,
                    subtitle = person.number,
                    divider = index != current.lastIndex,
                    trailing = { NulisIconButton(Glyph.Close, onClick = { onUpdate(withItems(block, current - person)) }) },
                )
            }
            if (current.isEmpty()) Caption("Nobody pinned yet")
            Spacer(Modifier.height(12.dp))
            PillButton(
                text = "Add someone",
                onClick = { picker.launch(pickIntent()) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Caption("Android's own picker. Nulis keeps only a name and a number.", lines = 2)
            Spacer(Modifier.height(8.dp))
            ListRow(
                title = "Tap sends a message",
                subtitle = "Instead of opening the dialler",
                onClick = { onUpdate(block.copy(settings = block.settings + (KEY_MESSAGE to if (tapMessages(block)) "0" else "1"))) },
                trailing = {
                    NulisToggle(
                        checked = tapMessages(block),
                        onCheckedChange = { onUpdate(block.copy(settings = block.settings + (KEY_MESSAGE to if (it) "1" else "0"))) },
                    )
                },
                divider = false,
            )
        }
    }

    /** Initials in a round plate, the way a person is drawn when you have no photo of them. */
    private object CirclesStyle : BlockStyle {
        override val id = "circles"
        override val label = "Circles"

        @OptIn(ExperimentalLayoutApi::class)
        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val appContext = LocalContext.current
            val people = items(block)
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            if (people.isEmpty()) {
                Empty(block, context, modifier)
                return
            }
            val size = if (wide) 60.dp else 44.dp
            BlockColumn(modifier) {
                FlowRow(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 10.dp, blockAlign().horizontal),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    people.forEach { person ->
                        Column(
                            modifier = Modifier
                                .width(size + 16.dp)
                                .pressFeedback()
                                .clickable(remember { MutableInteractionSource() }, indication = null) {
                                    open(appContext, person, tapMessages(block))
                                },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Box(
                                Modifier
                                    .size(size)
                                    .background(colors.surface, NulisShapes.pill)
                                    .border(1.dp, colors.hairline, NulisShapes.pill),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(person.initials, style = NulisTheme.type.displayS, color = colors.onBackground)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = person.name,
                                style = NulisTheme.type.bodyS,
                                color = colors.secondary,
                                maxLines = 1,
                                textAlign = TextAlign.Center,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }

    /** A row each, with the number underneath. */
    private object ListStyle : BlockStyle {
        override val id = "list"
        override val label = "List"

        @Composable
        override fun Render(block: Block, context: BlockContext, modifier: Modifier) {
            val appContext = LocalContext.current
            val people = items(block)
            val wide = block.size == BlockSize.WIDE
            val colors = NulisTheme.colors
            if (people.isEmpty()) {
                Empty(block, context, modifier)
                return
            }
            BlockColumn(modifier) {
                BlockCaptionRow("People", trailing = people.size.toString())
                people.take(if (wide) 5 else 3).forEach { person ->
                    BlockRow(
                        Modifier
                            .pressFeedback()
                            .clickable(remember { MutableInteractionSource() }, indication = null) {
                                open(appContext, person, tapMessages(block))
                            }
                            .padding(vertical = if (wide) 8.dp else 5.dp),
                    ) {
                        Text(
                            text = person.name,
                            style = if (wide) NulisTheme.type.bodyXl.copy(fontSize = 22.sp, lineHeight = 26.sp) else NulisTheme.type.bodyL,
                            color = colors.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                }
            }
        }
    }

    /** Nobody pinned yet: the empty slots they will sit in, and a tap that opens the picker. */
    @Composable
    private fun Empty(block: Block, context: BlockContext, modifier: Modifier) {
        BlockColumn(
            modifier
                .pressFeedback()
                .clickable(remember { MutableInteractionSource() }, indication = null) { context.openBlockOptions(block) },
        ) {
            BlockCaptionRow("People")
            Spacer(Modifier.height(8.dp))
            GhostDiscs(count = 3)
        }
    }

    /** Android's contact picker, restricted to rows that have a phone number. */
    private fun pickIntent(): Intent =
        Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)

    /**
     * Reads the one row the picker returned. The grant is temporary and covers only that row,
     * which is why this can work with no contacts permission at all.
     */
    private fun readPicked(context: Context, uri: Uri): ContactShortcut? = try {
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val number = cursor.getString(1)?.takeIf { it.isNotBlank() }
                number?.let { ContactShortcut(cursor.getString(0).orEmpty().ifBlank { it }, it) }
            } else {
                null
            }
        }
    } catch (e: SecurityException) {
        Log.w(TAG, "No grant for the picked contact", e)
        null
    }

    /**
     * Dials or messages. `ACTION_DIAL` puts the number in the dialler without calling it, which
     * is the right amount of commitment for a tap on a home screen and needs no permission.
     */
    private fun open(context: Context, person: ContactShortcut, message: Boolean) {
        val intent = if (message) {
            Intent(Intent.ACTION_SENDTO, "smsto:${Uri.encode(person.number)}".toUri())
        } else {
            Intent(Intent.ACTION_DIAL, "tel:${Uri.encode(person.number)}".toUri())
        }
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "Nothing handles $intent", e)
        }
    }

    private const val TAG = "ContactsBlock"
}

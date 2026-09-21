// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Nulis Launcher authors
package com.nulis.launcher.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nulis.launcher.ui.theme.NulisHaptics
import com.nulis.launcher.ui.theme.NulisShapes
import com.nulis.launcher.ui.theme.NulisTheme
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/**
 * A month grid in Nulis's own design rather than Material's dialogue: the month with arrows,
 * a row of weekday initials and the days as tappable cells, today outlined and the chosen day
 * filled. Used wherever a date has to be picked.
 */
@Composable
fun NulisDatePicker(
    selected: LocalDate,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = NulisTheme.colors
    val type = NulisTheme.type
    val locale = Locale.getDefault()
    val haptics = LocalHapticFeedback.current
    var month by remember(selected) { mutableStateOf(selected.withDayOfMonth(1)) }
    val monthName = remember(locale) { DateTimeFormatter.ofPattern("MMMM yyyy", locale) }
    val firstDay = remember(locale) { WeekFields.of(locale).firstDayOfWeek }
    val today = remember { LocalDate.now() }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            NulisIconButton(Glyph.ChevronUp, onClick = { month = month.minusMonths(1) })
            Text(
                text = month.format(monthName),
                style = type.displayS,
                color = colors.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            NulisIconButton(Glyph.ChevronDown, onClick = { month = month.plusMonths(1) })
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            repeat(7) { index ->
                val day = firstDay.plus(index.toLong())
                Text(
                    text = day.getDisplayName(TextStyle.NARROW, locale).uppercase(locale),
                    style = type.label,
                    color = colors.tertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        val lead = ((month.dayOfWeek.value - firstDay.value) + 7) % 7
        val days = month.lengthOfMonth()
        val rows = (lead + days + 6) / 7
        repeat(rows) { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(7) { column ->
                    val dayOfMonth = row * 7 + column - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (dayOfMonth in 1..days) {
                            val date = month.withDayOfMonth(dayOfMonth)
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selected,
                                onClick = {
                                    haptics.performHapticFeedback(NulisHaptics.tick)
                                    onSelect(date)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isToday: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    val colors = NulisTheme.colors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .pressFeedback()
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(if (isSelected) colors.onBackground else colors.surface, NulisShapes.pill)
            .border(
                width = if (isToday && !isSelected) 1.dp else 0.dp,
                color = if (isToday && !isSelected) colors.secondary else androidx.compose.ui.graphics.Color.Transparent,
                shape = NulisShapes.pill,
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            style = NulisTheme.type.bodyM,
            color = if (isSelected) colors.background else colors.onBackground,
            maxLines = 1,
        )
    }
}

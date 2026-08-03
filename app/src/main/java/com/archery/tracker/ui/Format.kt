package com.archery.tracker.ui

import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val monthDay = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
private val monthDayYear = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())

/** ISO yyyy-MM-dd -> "Today" / "Yesterday" / "Aug 3" / "Aug 3, 2025". Falls back to the raw string. */
fun friendlyDate(iso: String): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    val today = LocalDate.now()
    return when (date) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> if (date.year == today.year) date.format(monthDay) else date.format(monthDayYear)
    }
}

/** ISO yyyy-MM-dd -> "Aug 3, 2026". Always explicit (no Today/Yesterday) — for date input fields. */
fun fullDate(iso: String): String {
    val date = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return iso
    return date.format(monthDayYear)
}

val SessionType.label: String
    get() = name.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }

val TimeOfDay.label: String
    get() = name.lowercase().replaceFirstChar { it.titlecase(Locale.getDefault()) }

val TargetPosition.label: String
    get() = name

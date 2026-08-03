package com.archery.tracker.ui.sessiondetail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.archery.tracker.core.Arrow

fun arrowLabel(arrow: Arrow): String = when {
    arrow.isX -> "X"
    arrow.value == 0 -> "M"
    else -> arrow.value.toString()
}

fun descendingEnd(end: List<Arrow>): List<Arrow> =
    end.sortedWith(compareByDescending<Arrow> { it.value }.thenByDescending { it.isX })

/** Colors follow archery target-face convention: gold for the center, red, blue, then a neutral miss. */
fun arrowChipColor(label: String): Color = when (label) {
    "X", "10" -> Color(0xFFB8860B)
    "9", "8" -> Color(0xFFC62828)
    "7", "6" -> Color(0xFF1565C0)
    "5" -> Color(0xFF37474F)
    else -> Color(0xFF9E9E9E)
}

/** Gold and grey are too light for white text to meet WCAG AA contrast; everything else passes with white. */
fun arrowChipContentColor(label: String): Color = when (label) {
    "X", "10", "M" -> Color(0xFF1A1A1A)
    else -> Color.White
}

private fun accessibleArrowDescription(label: String): String = when (label) {
    "M" -> "Miss"
    "–" -> "Not yet scored"
    else -> label
}

@Composable
fun ArrowChip(label: String, modifier: Modifier = Modifier, size: Int = 36) {
    val filled = label != "–"
    val background = if (filled) arrowChipColor(label) else MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier
            .size(size.dp)
            .background(background, CircleShape)
            .clearAndSetSemantics { contentDescription = "Arrow: ${accessibleArrowDescription(label)}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (filled) arrowChipContentColor(label) else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

package com.archery.tracker.ui.sessiondetail

import com.archery.tracker.core.Arrow

fun arrowLabel(arrow: Arrow): String = when {
    arrow.isX -> "X"
    arrow.value == 0 -> "M"
    else -> arrow.value.toString()
}

fun descendingEnd(end: List<Arrow>): List<Arrow> =
    end.sortedWith(compareByDescending<Arrow> { it.value }.thenByDescending { it.isX })

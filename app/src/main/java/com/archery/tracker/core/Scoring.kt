package com.archery.tracker.core

fun ends(arrows: List<Arrow>): List<List<Arrow>> =
    arrows.chunked(ARROWS_PER_END)

fun roundTotal(arrows: List<Arrow>): Int =
    arrows.sumOf { it.value }

fun endTotals(arrows: List<Arrow>): List<Int> =
    ends(arrows).map { roundTotal(it) }

fun runningTotals(arrows: List<Arrow>): List<Int> {
    var carried = 0
    return endTotals(arrows).map { total -> carried += total; carried }
}

fun xCount(arrows: List<Arrow>): Int =
    arrows.count { it.isX }

fun tenCount(arrows: List<Arrow>): Int =
    arrows.count { it.value == 10 }

fun isRoundComplete(arrows: List<Arrow>): Boolean =
    arrows.size == ARROWS_PER_ROUND

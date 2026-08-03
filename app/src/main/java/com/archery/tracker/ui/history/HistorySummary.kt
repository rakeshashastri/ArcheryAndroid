package com.archery.tracker.ui.history

import com.archery.tracker.core.SessionWithRounds
import com.archery.tracker.core.isRoundComplete
import com.archery.tracker.core.roundTotal
import com.archery.tracker.core.xCount

data class SessionSummary(
    val total: Int,
    val xCount: Int,
    val roundCount: Int,
    val hasIncompleteRound: Boolean,
)

fun summarise(sessionWithRounds: SessionWithRounds): SessionSummary {
    val rounds = sessionWithRounds.rounds
    return SessionSummary(
        total = rounds.sumOf { roundTotal(it.arrows) },
        xCount = rounds.sumOf { xCount(it.arrows) },
        roundCount = rounds.size,
        hasIncompleteRound = rounds.any { !isRoundComplete(it.arrows) },
    )
}

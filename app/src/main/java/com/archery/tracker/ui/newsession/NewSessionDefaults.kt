package com.archery.tracker.ui.newsession

import com.archery.tracker.core.SessionType
import com.archery.tracker.core.SessionWithRounds

data class SessionDefaults(val arrowSet: String, val poundage: Double)

val FALLBACK_DEFAULTS = SessionDefaults(arrowSet = "", poundage = 50.0)

private fun mostRecent(sessions: List<SessionWithRounds>): SessionWithRounds? =
    sessions.maxByOrNull { it.session.date }

fun deriveDefaults(sessions: List<SessionWithRounds>, type: SessionType): SessionDefaults {
    val latestOfType = mostRecent(sessions.filter { it.session.type == type })
    val latestOverall = mostRecent(sessions)
    return SessionDefaults(
        arrowSet = latestOfType?.session?.arrowSet ?: FALLBACK_DEFAULTS.arrowSet,
        poundage = latestOverall?.session?.poundage ?: FALLBACK_DEFAULTS.poundage,
    )
}

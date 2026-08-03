package com.archery.tracker.ui.livescoring

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archery.tracker.core.ARROWS_PER_END
import com.archery.tracker.core.ARROWS_PER_ROUND
import com.archery.tracker.core.Arrow
import com.archery.tracker.core.ArrowValue
import com.archery.tracker.core.Round
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.endTotals
import com.archery.tracker.core.roundTotal
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.sync.enqueueSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

data class LiveScoringUiState(
    val arrows: List<Arrow> = emptyList(),
    val currentEndTotal: Int = 0,
    val roundTotal: Int = 0,
    val roundIndex: Int = 1,
    val loaded: Boolean = false,
)

class LiveScoringViewModel(
    application: Application,
    private val repository: ArcheryRepository,
    private val sessionId: String,
    private val roundId: String,
) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(LiveScoringUiState())
    val uiState: StateFlow<LiveScoringUiState> = _uiState.asStateFlow()

    private var roundIndex = 1
    private var targetPosition = TargetPosition.A

    init {
        viewModelScope.launch {
            val sessionWithRounds = repository.sessions().first().first { it.session.id == sessionId }
            val round = sessionWithRounds.rounds.first { it.id == roundId }
            roundIndex = round.index
            targetPosition = round.targetPosition
            updateState(round.arrows)
            _uiState.value = _uiState.value.copy(loaded = true, roundIndex = roundIndex)
        }
    }

    private fun updateState(arrows: List<Arrow>) {
        _uiState.value = _uiState.value.copy(
            arrows = arrows,
            currentEndTotal = endTotals(arrows).lastOrNull() ?: 0,
            roundTotal = roundTotal(arrows),
        )
    }

    fun add(value: ArrowValue, isX: Boolean) {
        val current = _uiState.value.arrows
        if (current.size >= ARROWS_PER_ROUND) return
        val next = current + Arrow(value, isX)
        persist(next)
    }

    fun undo() {
        val current = _uiState.value.arrows
        if (current.isEmpty()) return
        persist(current.dropLast(1))
    }

    private fun persist(next: List<Arrow>) {
        updateState(next)
        viewModelScope.launch {
            val round = Round(
                id = roundId, sessionId = sessionId, index = roundIndex, targetPosition = targetPosition,
                arrows = next, notes = null, updatedAt = Instant.now().toString(),
            )
            repository.saveRound(round)
            if (next.size % ARROWS_PER_END == 0 && next.isNotEmpty()) {
                enqueueSync(getApplication())
            }
        }
    }
}

package com.archery.tracker.ui.sessiondetail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archery.tracker.core.ROUNDS_PER_SESSION
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class SessionDetailUiState(
    val session: Session? = null,
    val rounds: List<Round> = emptyList(),
    val canAddRound: Boolean = false,
)

class SessionDetailViewModel(
    private val repository: ArcheryRepository,
    private val sessionId: String,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sessions().collect { sessions ->
                val match = sessions.firstOrNull { it.session.id == sessionId } ?: return@collect
                val limit = ROUNDS_PER_SESSION.getValue(match.session.type)
                _uiState.value = SessionDetailUiState(
                    session = match.session,
                    rounds = match.rounds.sortedBy { it.index },
                    canAddRound = match.rounds.size < limit,
                )
            }
        }
    }

    suspend fun addRound(): String {
        val state = _uiState.value
        requireNotNull(state.session)
        val nextIndex = state.rounds.size + 1
        val newRoundId = UUID.randomUUID().toString()
        val round = Round(
            id = newRoundId, sessionId = sessionId, index = nextIndex,
            targetPosition = state.rounds.firstOrNull()?.targetPosition ?: TargetPosition.A,
            arrows = emptyList(), notes = null, updatedAt = Instant.now().toString(),
        )
        repository.saveRound(round)
        return newRoundId
    }

    fun deleteSession(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val result = repository.deleteSession(sessionId)
            if (result.isSuccess) onDeleted()
        }
    }
}

package com.archery.tracker.ui.newsession

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

data class NewSessionUiState(
    val sessionId: String = UUID.randomUUID().toString(),
    val roundId: String = UUID.randomUUID().toString(),
    val type: SessionType = SessionType.PRACTICE,
    val date: String = Instant.now().toString().substring(0, 10),
    val timeOfDay: TimeOfDay = TimeOfDay.MORNING,
    val targetPosition: TargetPosition = TargetPosition.A,
    val arrowSet: String = "",
    val poundage: Double = 50.0,
    val error: String? = null,
    val started: Boolean = false,
)

class NewSessionViewModel(private val repository: ArcheryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(NewSessionUiState())
    val uiState: StateFlow<NewSessionUiState> = _uiState.asStateFlow()

    private var arrowSetEdited = false
    private var poundageEdited = false

    init {
        loadDefaults()
    }

    private fun loadDefaults() {
        viewModelScope.launch {
            val sessions = repository.sessions().first()
            val defaults = deriveDefaults(sessions, _uiState.value.type)
            _uiState.value = _uiState.value.copy(
                arrowSet = if (arrowSetEdited) _uiState.value.arrowSet else defaults.arrowSet,
                poundage = if (poundageEdited) _uiState.value.poundage else defaults.poundage,
            )
        }
    }

    fun updateType(type: SessionType) { _uiState.value = _uiState.value.copy(type = type) }
    fun updateDate(date: String) { _uiState.value = _uiState.value.copy(date = date) }
    fun updateTimeOfDay(timeOfDay: TimeOfDay) { _uiState.value = _uiState.value.copy(timeOfDay = timeOfDay) }
    fun updateTargetPosition(position: TargetPosition) { _uiState.value = _uiState.value.copy(targetPosition = position) }
    fun updateArrowSet(arrowSet: String) {
        arrowSetEdited = true
        _uiState.value = _uiState.value.copy(arrowSet = arrowSet)
    }
    fun updatePoundage(poundage: Double) {
        poundageEdited = true
        _uiState.value = _uiState.value.copy(poundage = poundage)
    }

    fun start() {
        viewModelScope.launch {
            val state = _uiState.value
            val now = Instant.now().toString()
            val session = Session(
                id = state.sessionId, date = state.date, type = state.type, timeOfDay = state.timeOfDay,
                arrowSet = state.arrowSet, poundage = state.poundage, notes = null, updatedAt = now,
            )
            val round = Round(
                id = state.roundId, sessionId = state.sessionId, index = 1,
                targetPosition = state.targetPosition, arrows = emptyList(), notes = null, updatedAt = now,
            )
            try {
                repository.createSessionWithFirstRound(session, round)
                _uiState.value = _uiState.value.copy(error = null, started = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Could not start the session. Check your connection and try again.")
            }
        }
    }
}

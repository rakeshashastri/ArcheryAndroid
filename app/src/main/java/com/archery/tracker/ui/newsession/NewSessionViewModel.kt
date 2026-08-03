package com.archery.tracker.ui.newsession

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.archery.tracker.core.Round
import com.archery.tracker.core.Session
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.data.repository.ArcheryRepository
import com.archery.tracker.sync.enqueueSync
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class NewSessionUiState(
    val sessionId: String = UUID.randomUUID().toString(),
    val roundId: String = UUID.randomUUID().toString(),
    val type: SessionType = SessionType.PRACTICE,
    val date: String = LocalDate.now().toString(),
    val timeOfDay: TimeOfDay = TimeOfDay.MORNING,
    val targetPosition: TargetPosition = TargetPosition.A,
    val arrowSet: String = "",
    val arrowSetSuggestions: List<String> = emptyList(),
    val poundage: Double = 50.0,
    val error: String? = null,
    val started: Boolean = false,
)

class NewSessionViewModel(
    application: Application,
    private val repository: ArcheryRepository,
) : AndroidViewModel(application) {

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
            val suggestions = sessions
                .sortedByDescending { it.session.date }
                .map { it.session.arrowSet }
                .filter { it.isNotBlank() }
                .distinct()
            _uiState.value = _uiState.value.copy(
                arrowSet = if (arrowSetEdited) _uiState.value.arrowSet else defaults.arrowSet,
                arrowSetSuggestions = suggestions,
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
        if (_uiState.value.arrowSet.isBlank()) {
            _uiState.value = _uiState.value.copy(error = "Enter an arrow set before starting.")
            return
        }
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
                enqueueSync(getApplication())
                _uiState.value = _uiState.value.copy(error = null, started = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = "Could not start the session. Check your connection and try again.")
            }
        }
    }
}

package com.archery.tracker.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archery.tracker.core.Session
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HistoryRow(val session: Session, val summary: SessionSummary, val isDirty: Boolean)

class HistoryViewModel(private val repository: ArcheryRepository) : ViewModel() {

    private val _rows = MutableStateFlow<List<HistoryRow>>(emptyList())
    val rows: StateFlow<List<HistoryRow>> = _rows.asStateFlow()

    init {
        viewModelScope.launch {
            repository.sessionList().collect { entries ->
                _rows.value = entries
                    .sortedByDescending { it.sessionWithRounds.session.date }
                    .map { entry ->
                        HistoryRow(
                            session = entry.sessionWithRounds.session,
                            summary = summarise(entry.sessionWithRounds),
                            isDirty = entry.isDirty,
                        )
                    }
            }
        }
    }
}

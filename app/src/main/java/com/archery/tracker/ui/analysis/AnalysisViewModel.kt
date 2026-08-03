package com.archery.tracker.ui.analysis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.archery.tracker.data.remote.StatsResponseDto
import com.archery.tracker.data.repository.ArcheryRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AnalysisUiState(val stats: StatsResponseDto? = null, val error: String? = null)

class AnalysisViewModel(private val repository: ArcheryRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AnalysisUiState())
    val uiState: StateFlow<AnalysisUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.value = AnalysisUiState()
            repository.stats().fold(
                onSuccess = { stats -> _uiState.value = AnalysisUiState(stats = stats) },
                onFailure = { _uiState.value = AnalysisUiState(error = "Could not load your statistics.") },
            )
        }
    }
}

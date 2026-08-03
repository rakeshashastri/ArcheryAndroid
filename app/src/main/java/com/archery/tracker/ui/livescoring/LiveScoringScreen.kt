package com.archery.tracker.ui.livescoring

import android.app.Application
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.di.AppContainer

private data class Key(val label: String, val value: Int, val isX: Boolean)

private val KEYS = listOf(
    Key("X", 10, true), Key("10", 10, false), Key("9", 9, false),
    Key("8", 8, false), Key("7", 7, false), Key("6", 6, false),
    Key("5", 5, false), Key("M", 0, false),
)

@Composable
fun LiveScoringScreen(container: AppContainer, sessionId: String, roundId: String, navController: NavController) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = viewModel<LiveScoringViewModel>(key = "$sessionId-$roundId") {
        LiveScoringViewModel(application, container.repository, sessionId, roundId)
    }
    LiveScoringScreenContent(viewModel)
}

@Composable
fun LiveScoringScreenContent(viewModel: LiveScoringViewModel) {
    val state by viewModel.uiState.collectAsState()

    if (!state.loaded) {
        Text("Loading…")
        return
    }

    Column(Modifier.padding(16.dp)) {
        Text("Round ${state.roundIndex}")
        Text("End total: ${state.currentEndTotal}")
        Text("Round total: ${state.roundTotal}")

        LazyVerticalGrid(columns = GridCells.Fixed(3)) {
            items(KEYS) { key ->
                Button(onClick = { viewModel.add(key.value, key.isX) }) { Text(key.label) }
            }
        }
        Row {
            Button(onClick = viewModel::undo) { Text("Undo") }
        }
    }
}

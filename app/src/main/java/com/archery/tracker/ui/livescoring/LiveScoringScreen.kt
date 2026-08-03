package com.archery.tracker.ui.livescoring

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.core.ARROWS_PER_END
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.sessiondetail.ArrowChip
import com.archery.tracker.ui.sessiondetail.arrowChipColor
import com.archery.tracker.ui.sessiondetail.arrowChipContentColor
import com.archery.tracker.ui.sessiondetail.arrowLabel

private data class Key(val label: String, val value: Int, val isX: Boolean)

private val KEYS = listOf(
    Key("X", 10, true), Key("10", 10, false), Key("9", 9, false),
    Key("8", 8, false), Key("7", 7, false), Key("6", 6, false),
    Key("5", 5, false), Key("M", 0, false),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveScoringScreen(container: AppContainer, sessionId: String, roundId: String, navController: NavController) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = viewModel<LiveScoringViewModel>(key = "$sessionId-$roundId") {
        LiveScoringViewModel(application, container.repository, sessionId, roundId)
    }
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.loaded) "Round ${state.roundIndex}" else "Live scoring") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LiveScoringScreenContent(viewModel, showRoundTitle = false)
        }
    }
}

@Composable
fun LiveScoringScreenContent(viewModel: LiveScoringViewModel, showRoundTitle: Boolean = true) {
    val state by viewModel.uiState.collectAsState()

    if (!state.loaded) {
        Text("Loading…", modifier = Modifier.padding(16.dp))
        return
    }

    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (showRoundTitle) {
            Text("Round ${state.roundIndex}", style = MaterialTheme.typography.titleLarge)
        }

        val currentEndArrows = state.arrows.drop((state.arrows.size / ARROWS_PER_END) * ARROWS_PER_END)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(ARROWS_PER_END) { slot ->
                ArrowChip(currentEndArrows.getOrNull(slot)?.let(::arrowLabel) ?: "–", size = 44)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("End total: ${state.currentEndTotal}", style = MaterialTheme.typography.headlineSmall)
            Text("Round total: ${state.roundTotal}", style = MaterialTheme.typography.headlineSmall)
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(KEYS) { key ->
                Button(
                    onClick = { viewModel.add(key.value, key.isX) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = arrowChipColor(key.label),
                        contentColor = arrowChipContentColor(key.label),
                    ),
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) { Text(key.label, style = MaterialTheme.typography.titleMedium) }
            }
        }
        OutlinedButton(onClick = viewModel::undo, modifier = Modifier.fillMaxWidth()) { Text("Undo") }
    }
}

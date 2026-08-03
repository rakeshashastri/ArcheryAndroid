package com.archery.tracker.ui.sessiondetail

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.core.roundTotal
import com.archery.tracker.core.ends
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.liveScoringRoute
import kotlinx.coroutines.launch

@Composable
fun SessionDetailScreen(container: AppContainer, sessionId: String, navController: NavController) {
    val viewModel = viewModel<SessionDetailViewModel>(key = sessionId) {
        SessionDetailViewModel(container.repository, sessionId)
    }
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val session = state.session
    if (session == null) {
        Text("Loading…")
        return
    }

    Column(Modifier.padding(16.dp)) {
        Text("${session.date} · ${session.type} · ${session.timeOfDay} · ${session.arrowSet} · ${session.poundage} lb")

        state.rounds.forEach { round ->
            Text("Round ${round.index} · position ${round.targetPosition} · ${roundTotal(round.arrows)}")
            ends(round.arrows).forEachIndexed { i, end ->
                val sorted = descendingEnd(end).joinToString(" ") { arrowLabel(it) }
                Text("End ${i + 1}: $sorted")
            }
            Button(onClick = { navController.navigate(liveScoringRoute(sessionId, round.id)) }) {
                Text("Edit round ${round.index}")
            }
        }

        if (state.canAddRound) {
            Button(onClick = {
                scope.launch {
                    val newRoundId = viewModel.addRound()
                    navController.navigate(liveScoringRoute(sessionId, newRoundId))
                }
            }) { Text("Add round ${state.rounds.size + 1}") }
        }

        Button(onClick = { showDeleteConfirm = true }) { Text("Delete session") }

        if (showDeleteConfirm) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                title = { Text("Delete this session and all of its rounds?") },
                text = { Text("This cannot be undone.") },
                confirmButton = {
                    Button(onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteSession { navController.popBackStack() }
                    }) { Text("Delete") }
                },
                dismissButton = {
                    Button(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                },
            )
        }
    }
}

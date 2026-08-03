package com.archery.tracker.ui.sessiondetail

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.core.roundTotal
import com.archery.tracker.core.ends
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.liveScoringRoute
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(container: AppContainer, sessionId: String, navController: NavController) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = viewModel<SessionDetailViewModel>(key = sessionId) {
        SessionDetailViewModel(application, container.repository, sessionId)
    }
    val state by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Session detail") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        val session = state.session
        if (session == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "${session.date} · ${session.type} · ${session.timeOfDay} · ${session.arrowSet} · ${session.poundage} lb",
                style = MaterialTheme.typography.titleMedium,
            )

            state.rounds.forEach { round ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Round ${round.index} · position ${round.targetPosition} · ${roundTotal(round.arrows)}",
                            style = MaterialTheme.typography.titleSmall,
                        )
                        ends(round.arrows).forEachIndexed { i, end ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("End ${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    descendingEnd(end).forEach { arrow ->
                                        ArrowChip(arrowLabel(arrow), size = 28)
                                    }
                                }
                            }
                        }
                        OutlinedButton(
                            onClick = { navController.navigate(liveScoringRoute(sessionId, round.id)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Edit round ${round.index}") }
                    }
                }
            }

            if (state.canAddRound) {
                Button(
                    onClick = {
                        scope.launch {
                            val newRoundId = viewModel.addRound()
                            navController.navigate(liveScoringRoute(sessionId, newRoundId))
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Add round ${state.rounds.size + 1}") }
            }

            state.deleteError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(
                onClick = { showDeleteConfirm = true },
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Delete session") }

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
}

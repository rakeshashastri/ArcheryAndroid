package com.archery.tracker.ui.sessiondetail

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import com.archery.tracker.core.Round
import com.archery.tracker.core.endTotals
import com.archery.tracker.core.ends
import com.archery.tracker.core.roundTotal
import com.archery.tracker.core.xCount
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.friendlyDate
import com.archery.tracker.ui.label
import com.archery.tracker.ui.liveScoringRoute
import com.archery.tracker.ui.theme.Spacing
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
                title = { Text("Session") },
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

        val total = state.rounds.sumOf { roundTotal(it.arrows) }
        val xTotal = state.rounds.sumOf { xCount(it.arrows) }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.screen)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(friendlyDate(session.date), style = MaterialTheme.typography.titleLarge)
                Text(
                    "${session.type.label} · ${session.timeOfDay.label} · ${session.arrowSet} · ${session.poundage} lb",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text("$total", style = MaterialTheme.typography.displaySmall)
                Text(
                    "pts · $xTotal X · ${state.rounds.size} ${if (state.rounds.size == 1) "round" else "rounds"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.s),
                )
            }

            state.rounds.forEach { round -> RoundCard(round) { navController.navigate(liveScoringRoute(sessionId, round.id)) } }

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

@Composable
private fun RoundCard(round: Round, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Round ${round.index}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${roundTotal(round.arrows)} · pos ${round.targetPosition.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val endGroups = ends(round.arrows)
            val totals = endTotals(round.arrows)
            if (endGroups.isEmpty()) {
                Text("No arrows yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                endGroups.forEachIndexed { i, end ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            descendingEnd(end).forEach { arrow -> ArrowChip(arrowLabel(arrow), size = 26) }
                        }
                        Text("= ${totals[i]}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text("Edit round ${round.index}") }
        }
    }
}

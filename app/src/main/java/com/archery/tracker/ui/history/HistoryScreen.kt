package com.archery.tracker.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.ROUTE_NEW_SESSION
import com.archery.tracker.ui.sessionDetailRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(container: AppContainer, navController: NavController) {
    val viewModel = viewModel<HistoryViewModel> { HistoryViewModel(container.repository) }
    val rows by viewModel.rows.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Archery Tracker") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate(ROUTE_NEW_SESSION) }) {
                Icon(Icons.Filled.Add, contentDescription = "New session")
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No sessions yet. Log your first round to get started.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding() + 12.dp, start = 16.dp, end = 16.dp, bottom = 96.dp),
            ) {
                items(rows, key = { it.session.id }) { row ->
                    Card(
                        onClick = { navController.navigate(sessionDetailRoute(row.session.id)) },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                "${row.session.date} — ${row.session.type}",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                "${row.summary.total} (${row.summary.xCount} X)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (row.summary.hasIncompleteRound || row.isDirty) {
                                Column(Modifier.padding(top = 4.dp)) {
                                    if (row.summary.hasIncompleteRound) {
                                        Text(
                                            "incomplete",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                    if (row.isDirty) {
                                        Text(
                                            "not yet synced",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

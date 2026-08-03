package com.archery.tracker.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.sessionDetailRoute

@Composable
fun HistoryScreen(container: AppContainer, navController: NavController) {
    val viewModel = viewModel<HistoryViewModel> { HistoryViewModel(container.repository) }
    val rows by viewModel.rows.collectAsState()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("newSession") }) {
                Icon(Icons.Filled.Add, contentDescription = "New session")
            }
        },
    ) { padding ->
        if (rows.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                Text("No sessions yet. Log your first round to get started.")
            }
        } else {
            LazyColumn(Modifier.padding(padding)) {
                items(rows, key = { it.session.id }) { row ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { navController.navigate(sessionDetailRoute(row.session.id)) }
                            .padding(16.dp),
                    ) {
                        Text("${row.session.date} — ${row.session.type}")
                        Text("${row.summary.total} (${row.summary.xCount} X)")
                        if (row.summary.hasIncompleteRound) Text("incomplete")
                        if (row.isDirty) Text("not yet synced")
                    }
                }
            }
        }
    }
}

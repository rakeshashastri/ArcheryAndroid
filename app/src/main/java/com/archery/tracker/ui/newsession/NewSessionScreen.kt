package com.archery.tracker.ui.newsession

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.liveScoringRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionScreen(container: AppContainer, navController: NavController) {
    val viewModel = viewModel<NewSessionViewModel> { NewSessionViewModel(container.repository) }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.started) {
        if (state.started) {
            navController.navigate(liveScoringRoute(state.sessionId, state.roundId))
        }
    }

    Column(Modifier.padding(16.dp)) {
        Text("New session")
        state.error?.let { Text(it) }

        var typeExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = typeExpanded, onExpandedChange = { typeExpanded = it }) {
            OutlinedTextField(
                value = state.type.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Type") },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
            ExposedDropdownMenu(expanded = typeExpanded, onDismissRequest = { typeExpanded = false }) {
                SessionType.entries.forEach { type ->
                    DropdownMenuItem(text = { Text(type.name) }, onClick = { viewModel.updateType(type); typeExpanded = false })
                }
            }
        }

        var timeOfDayExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = timeOfDayExpanded, onExpandedChange = { timeOfDayExpanded = it }) {
            OutlinedTextField(
                value = state.timeOfDay.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Time of day") },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeOfDayExpanded)
            ExposedDropdownMenu(expanded = timeOfDayExpanded, onDismissRequest = { timeOfDayExpanded = false }) {
                TimeOfDay.entries.forEach { timeOfDay ->
                    DropdownMenuItem(text = { Text(timeOfDay.name) }, onClick = { viewModel.updateTimeOfDay(timeOfDay); timeOfDayExpanded = false })
                }
            }
        }

        var targetPositionExpanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(expanded = targetPositionExpanded, onExpandedChange = { targetPositionExpanded = it }) {
            OutlinedTextField(
                value = state.targetPosition.name,
                onValueChange = {},
                readOnly = true,
                label = { Text("Target position") },
                modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable),
            )
            ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetPositionExpanded)
            ExposedDropdownMenu(expanded = targetPositionExpanded, onDismissRequest = { targetPositionExpanded = false }) {
                TargetPosition.entries.forEach { position ->
                    DropdownMenuItem(text = { Text(position.name) }, onClick = { viewModel.updateTargetPosition(position); targetPositionExpanded = false })
                }
            }
        }

        OutlinedTextField(value = state.date, onValueChange = viewModel::updateDate, label = { Text("Date") })
        OutlinedTextField(value = state.arrowSet, onValueChange = viewModel::updateArrowSet, label = { Text("Arrow set") })
        var poundageText by remember { mutableStateOf(state.poundage.toString()) }
        var poundageEditedLocally by remember { mutableStateOf(false) }
        LaunchedEffect(state.poundage) {
            if (!poundageEditedLocally) poundageText = state.poundage.toString()
        }
        OutlinedTextField(
            value = poundageText,
            onValueChange = { text ->
                poundageText = text
                poundageEditedLocally = true
                text.toDoubleOrNull()?.let(viewModel::updatePoundage)
            },
            label = { Text("Poundage") },
        )

        Button(onClick = viewModel::start) { Text("Start round 1") }
    }
}

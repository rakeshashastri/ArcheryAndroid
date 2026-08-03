package com.archery.tracker.ui.newsession

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.core.SessionType
import com.archery.tracker.core.TargetPosition
import com.archery.tracker.core.TimeOfDay
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.ROUTE_NEW_SESSION
import com.archery.tracker.ui.fullDate
import com.archery.tracker.ui.label
import com.archery.tracker.ui.liveScoringRoute
import com.archery.tracker.ui.theme.Spacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSessionScreen(container: AppContainer, navController: NavController) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel = viewModel<NewSessionViewModel> { NewSessionViewModel(application, container.repository) }
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(state.started) {
        if (state.started) {
            navController.navigate(liveScoringRoute(state.sessionId, state.roundId)) {
                popUpTo(ROUTE_NEW_SESSION) { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New session") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(Spacing.screen)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.l),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }

            EnumDropdown("Type", state.type.label, SessionType.entries, { it.label }, viewModel::updateType)
            EnumDropdown("Time of day", state.timeOfDay.label, TimeOfDay.entries, { it.label }, viewModel::updateTimeOfDay)
            EnumDropdown(
                "Target position",
                state.targetPosition.label,
                TargetPosition.entries,
                { it.label },
                viewModel::updateTargetPosition,
                supporting = "Your spot on the target boss (A–D)",
            )

            DateField(state.date, viewModel::updateDate)

            ArrowSetField(state.arrowSet, state.arrowSetSuggestions, viewModel::updateArrowSet)

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
                suffix = { Text("lb") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::start,
                enabled = state.arrowSet.isNotBlank(),
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.s),
            ) { Text("Start round 1") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    selectedLabel: String,
    options: Iterable<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    supporting: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            supportingText = supporting?.let { { Text(it) } },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(optionLabel(option)) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(iso: String, onDate: (String) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = fullDate(iso),
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text("Date") },
            trailingIcon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Box(Modifier.matchParentSize().clickable { showPicker = true })
    }

    if (showPicker) {
        val initialMillis = runCatching { LocalDate.parse(iso) }.getOrNull()
            ?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        onDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString())
                    }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Cancel") } },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ArrowSetField(value: String, suggestions: List<String>, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val matches = suggestions.filter { it.contains(value, ignoreCase = true) && it != value }
    ExposedDropdownMenuBox(expanded = expanded && matches.isNotEmpty(), onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value,
            onValueChange = { onChange(it); expanded = true },
            label = { Text("Arrow set") },
            supportingText = { Text("e.g. Easton X10, ACC — which arrows you shot") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable),
        )
        if (matches.isNotEmpty()) {
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                matches.forEach { suggestion ->
                    DropdownMenuItem(text = { Text(suggestion) }, onClick = { onChange(suggestion); expanded = false })
                }
            }
        }
    }
}

package com.archery.tracker.ui.livescoring

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.archery.tracker.core.ARROWS_PER_END
import com.archery.tracker.core.ARROWS_PER_ROUND
import com.archery.tracker.core.ENDS_PER_ROUND
import com.archery.tracker.core.ends
import com.archery.tracker.core.endTotals
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.sessiondetail.ArrowChip
import com.archery.tracker.ui.sessiondetail.arrowChipColor
import com.archery.tracker.ui.sessiondetail.arrowChipContentColor
import com.archery.tracker.ui.sessiondetail.arrowLabel
import com.archery.tracker.ui.theme.Spacing

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
            LiveScoringScreenContent(viewModel, onDone = { navController.popBackStack() })
        }
    }
}

@Composable
fun LiveScoringScreenContent(viewModel: LiveScoringViewModel, onDone: () -> Unit = {}) {
    val state by viewModel.uiState.collectAsState()
    val haptics = LocalHapticFeedback.current

    if (!state.loaded) {
        Text("Loading…", modifier = Modifier.padding(Spacing.l))
        return
    }

    val completedEnds = state.arrows.size / ARROWS_PER_END
    val isComplete = state.arrows.size >= ARROWS_PER_ROUND
    val currentEndNumber = (completedEnds + 1).coerceAtMost(ENDS_PER_ROUND)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.l),
        verticalArrangement = Arrangement.spacedBy(Spacing.l),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
            StatBlock("Round", state.roundTotal, hero = true)
            if (!isComplete) StatBlock("End", state.currentEndTotal, hero = false)
        }

        if (isComplete) {
            RoundCompleteCard(state.roundTotal, onDone)
            Text("Made a mistake? Undo the last arrow.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.undo() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Undo") }
        } else {
            Text(
                "End $currentEndNumber of $ENDS_PER_ROUND",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val currentEndArrows = state.arrows.drop(completedEnds * ARROWS_PER_END)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                repeat(ARROWS_PER_END) { slot ->
                    ArrowChip(currentEndArrows.getOrNull(slot)?.let(::arrowLabel) ?: "–", size = 44)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                horizontalArrangement = Arrangement.spacedBy(Spacing.s),
                verticalArrangement = Arrangement.spacedBy(Spacing.s),
                userScrollEnabled = false,
                modifier = Modifier.height(200.dp),
            ) {
                items(KEYS) { key ->
                    Button(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            viewModel.add(key.value, key.isX)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = arrowChipColor(key.label),
                            contentColor = arrowChipContentColor(key.label),
                        ),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    ) { Text(key.label, style = MaterialTheme.typography.titleMedium) }
                }
            }
            OutlinedButton(
                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); viewModel.undo() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Undo") }
        }

        if (completedEnds > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
                Text("Earlier ends", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val endGroups = ends(state.arrows).take(completedEnds)
                val totals = endTotals(state.arrows)
                endGroups.forEachIndexed { i, end ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                            end.forEach { arrow -> ArrowChip(arrowLabel(arrow), size = 26) }
                        }
                        Text("= ${totals[i]}", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatBlock(label: String, value: Int, hero: Boolean) {
    Column(
        Modifier.clearAndSetSemantics { contentDescription = "$label total: $value" },
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "$value",
            style = if (hero) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineSmall,
        )
    }
}

@Composable
private fun RoundCompleteCard(roundTotal: Int, onDone: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Text("Round complete", style = MaterialTheme.typography.titleMedium)
            Text("You scored $roundTotal.", style = MaterialTheme.typography.bodyMedium)
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

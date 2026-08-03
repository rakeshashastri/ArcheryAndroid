package com.archery.tracker.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archery.tracker.data.remote.ConsistencyViewDto
import com.archery.tracker.data.remote.GapViewDto
import com.archery.tracker.data.remote.PatternsViewDto
import com.archery.tracker.data.remote.TrendViewDto
import com.archery.tracker.di.AppContainer

private fun fmt(value: Double?): String = value?.let { "%.1f".format(it) } ?: "—"
private fun fmt(value: Double): String = "%.1f".format(value)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun InsufficientData(message: String) {
    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Bar(fraction: Float) {
    Box(
        Modifier
            .fillMaxWidth(fraction.coerceIn(0f, 1f))
            .height(10.dp)
            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp)),
    )
}

@Composable
private fun AnalysisCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun GapCard(view: GapViewDto) = AnalysisCard {
    SectionTitle("Practice vs competition")
    if (view.insufficient != null) { InsufficientData(view.insufficient); return@AnalysisCard }
    Text(fmt(view.gap), style = MaterialTheme.typography.headlineSmall)
    Text(
        "Practice ${fmt(view.practiceAverage)} · Competition ${fmt(view.competitionAverage)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (view.arrowSetMismatch) {
        Text(
            "You shoot different arrows in practice and competition.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun TrendCard(view: TrendViewDto) = AnalysisCard {
    SectionTitle("Score trend")
    if (view.insufficient != null) { InsufficientData(view.insufficient); return@AnalysisCard }
    Text(
        "Best ever — practice ${view.bestEver.practice?.total ?: "—"}, competition ${view.bestEver.competition?.total ?: "—"}",
        style = MaterialTheme.typography.bodyMedium,
    )
    Text(
        "Best in the last 12 months — practice ${view.bestLast12Months.practice?.total ?: "—"}, competition ${view.bestLast12Months.competition?.total ?: "—"}",
        style = MaterialTheme.typography.bodyMedium,
    )
}

@Composable
private fun ConsistencyCard(view: ConsistencyViewDto) = AnalysisCard {
    SectionTitle("Consistency")
    if (view.insufficient != null) { InsufficientData(view.insufficient); return@AnalysisCard }
    Text(
        "X rate ${fmt(view.xRate)}% · 10+X rate ${fmt(view.tenPlusXRate)}% · average arrow ${fmt(view.averageArrowValue)}",
        style = MaterialTheme.typography.bodyMedium,
    )
    val maxCount = (view.distribution.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    view.distribution.forEach { bucket ->
        val label = if (bucket.value == 0) "M" else bucket.value.toString()
        Text("$label: ${bucket.count}", style = MaterialTheme.typography.labelMedium)
        Bar(bucket.count.toFloat() / maxCount)
    }
}

@Composable
private fun PatternsCard(view: PatternsViewDto) = AnalysisCard {
    SectionTitle("Within-session patterns")
    if (view.insufficient != null) { InsufficientData(view.insufficient); return@AnalysisCard }
    Text("Average by end position", style = MaterialTheme.typography.labelLarge)
    view.byEndPosition.forEach { entry ->
        Text("End ${entry.position}: ${fmt(entry.average)}", style = MaterialTheme.typography.labelMedium)
        Bar((entry.average / 60).toFloat())
    }
    Text("Average by round position", style = MaterialTheme.typography.labelLarge)
    view.byRoundPosition.forEach { entry ->
        Text("Round ${entry.position}: ${fmt(entry.average)}", style = MaterialTheme.typography.labelMedium)
        Bar((entry.average / 360).toFloat())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(container: AppContainer) {
    val viewModel = viewModel<AnalysisViewModel> { AnalysisViewModel(container.repository) }
    val state by viewModel.uiState.collectAsState()

    Scaffold(topBar = { TopAppBar(title = { Text("Analysis") }) }) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = viewModel::load, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) { Text("Try again") }
            }
            state.stats?.let { stats ->
                GapCard(stats.gap)
                TrendCard(stats.trend)
                ConsistencyCard(stats.consistency)
                PatternsCard(stats.patterns)
            }
        }
    }
}

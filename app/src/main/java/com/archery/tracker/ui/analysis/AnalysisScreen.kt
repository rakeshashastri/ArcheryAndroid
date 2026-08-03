package com.archery.tracker.ui.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archery.tracker.data.remote.ConsistencyViewDto
import com.archery.tracker.data.remote.GapViewDto
import com.archery.tracker.data.remote.PatternsViewDto
import com.archery.tracker.data.remote.TrendViewDto
import com.archery.tracker.di.AppContainer

@Composable
private fun Bar(fraction: Float) {
    Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(12.dp).background(Color(0xFFFFB020)))
}

@Composable
private fun GapCard(view: GapViewDto) {
    Text("Practice vs competition")
    if (view.insufficient != null) { Text(view.insufficient); return }
    Text("${view.gap}")
    Text("Practice ${view.practiceAverage} · Competition ${view.competitionAverage}")
    if (view.arrowSetMismatch) Text("You shoot different arrows in practice and competition.")
}

@Composable
private fun TrendCard(view: TrendViewDto) {
    Text("Score trend")
    if (view.insufficient != null) { Text(view.insufficient); return }
    Text("Best ever — practice ${view.bestEver.practice?.total ?: "—"}, competition ${view.bestEver.competition?.total ?: "—"}")
    Text("Best in the last 12 months — practice ${view.bestLast12Months.practice?.total ?: "—"}, competition ${view.bestLast12Months.competition?.total ?: "—"}")
}

@Composable
private fun ConsistencyCard(view: ConsistencyViewDto) {
    Text("Consistency")
    if (view.insufficient != null) { Text(view.insufficient); return }
    Text("X rate ${view.xRate}% · 10+X rate ${view.tenPlusXRate}% · average arrow ${view.averageArrowValue}")
    val maxCount = (view.distribution.maxOfOrNull { it.count } ?: 0).coerceAtLeast(1)
    view.distribution.forEach { bucket ->
        val label = if (bucket.value == 0) "M" else bucket.value.toString()
        Text("$label: ${bucket.count}")
        Bar(bucket.count.toFloat() / maxCount)
    }
}

@Composable
private fun PatternsCard(view: PatternsViewDto) {
    Text("Within-session patterns")
    if (view.insufficient != null) { Text(view.insufficient); return }
    Text("Average by end position")
    view.byEndPosition.forEach { entry ->
        Text("End ${entry.position}: ${entry.average}")
        Bar((entry.average / 60).toFloat())
    }
    Text("Average by round position")
    view.byRoundPosition.forEach { entry ->
        Text("Round ${entry.position}: ${entry.average}")
        Bar((entry.average / 360).toFloat())
    }
}

@Composable
fun AnalysisScreen(container: AppContainer) {
    val viewModel = viewModel<AnalysisViewModel> { AnalysisViewModel(container.repository) }
    val state by viewModel.uiState.collectAsState()

    Column(Modifier.padding(16.dp)) {
        Text("Analysis")
        state.error?.let {
            Text(it)
            Button(onClick = viewModel::load) { Text("Try again") }
        }
        state.stats?.let { stats ->
            GapCard(stats.gap)
            TrendCard(stats.trend)
            ConsistencyCard(stats.consistency)
            PatternsCard(stats.patterns)
        }
    }
}

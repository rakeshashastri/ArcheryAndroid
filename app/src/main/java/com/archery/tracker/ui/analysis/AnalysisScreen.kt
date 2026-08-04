package com.archery.tracker.ui.analysis

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.archery.tracker.data.remote.ConsistencyViewDto
import com.archery.tracker.data.remote.GapViewDto
import com.archery.tracker.data.remote.PatternsViewDto
import com.archery.tracker.data.remote.PositionAverageDto
import com.archery.tracker.data.remote.StatsResponseDto
import com.archery.tracker.data.remote.TrendViewDto
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.sessiondetail.arrowChipColor
import com.archery.tracker.ui.theme.Spacing

private const val MIN_ROUNDS_FOR_ANALYSIS = 3
private val Practice = Color(0xFFE0A800)
private val Competition = Color(0xFF5AC8FA)
private val Up = Color(0xFF35C07A)

private fun fmt(value: Double?): String = value?.let { "%.1f".format(it) } ?: "—"
private fun fmt(value: Double): String = "%.1f".format(value)

private fun allTotals(trend: TrendViewDto): List<Int> =
    (trend.practice + trend.competition).sortedBy { it.date }.map { it.total }

private fun recentAverage(trend: TrendViewDto): Double? =
    trend.let { allTotals(it).takeLast(5).ifEmpty { null }?.average() }

private fun trendDelta(trend: TrendViewDto): Double? {
    val all = allTotals(trend)
    val recent = all.takeLast(5)
    val prior = all.dropLast(5).takeLast(5)
    return if (recent.isEmpty() || prior.isEmpty()) null else recent.average() - prior.average()
}

private fun personalBest(trend: TrendViewDto): Int? =
    listOfNotNull(trend.bestEver.practice?.total, trend.bestEver.competition?.total).maxOrNull()

@Composable
private fun SectionTitle(text: String) = Text(text, style = MaterialTheme.typography.titleMedium)

@Composable
private fun InsufficientData(message: String) =
    Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun Takeaway(text: String) = Text(
    text,
    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
)

@Composable
private fun Bar(fraction: Float, color: Color = MaterialTheme.colorScheme.secondary) {
    Box(
        Modifier
            .fillMaxWidth(fraction.coerceIn(0f, 1f))
            .height(12.dp)
            .background(color, RoundedCornerShape(6.dp)),
    )
}

@Composable
private fun AnalysisCard(content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(Spacing.l), verticalArrangement = Arrangement.spacedBy(Spacing.s)) { content() }
    }
}

@Composable
private fun StatTile(label: String, value: String, sub: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(modifier, elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
        Column(Modifier.padding(Spacing.m)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineSmall, color = valueColor)
            Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Headline(stats: StatsResponseDto) {
    val avg = recentAverage(stats.trend) ?: return
    val best = personalBest(stats.trend)
    val delta = trendDelta(stats.trend)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        StatTile("Recent avg", "${avg.toInt()}", "per round", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        if (best != null) StatTile("Best", "$best", "a round", MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        if (delta != null) {
            val d = delta.toInt()
            StatTile(
                "Trend",
                (if (d > 0) "▲ +$d" else if (d < 0) "▼ $d" else "■ $d"),
                "vs prev 5",
                if (d > 0) Up else if (d < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Sparkline(practice: List<Double>, competition: List<Double>) {
    val all = practice + competition
    if (all.size < 2) return
    val min = all.min()
    val max = all.max()
    val range = (max - min).coerceAtLeast(1.0)
    Canvas(Modifier.fillMaxWidth().height(56.dp)) {
        fun draw(series: List<Double>, color: Color) {
            if (series.size < 2) return
            val stepX = size.width / (series.size - 1)
            val path = Path()
            series.forEachIndexed { i, v ->
                val x = i * stepX
                val y = size.height - ((v - min) / range).toFloat() * size.height
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round))
        }
        draw(practice, Practice)
        draw(competition, Competition)
    }
}

@Composable
private fun GapCard(view: GapViewDto) = AnalysisCard {
    SectionTitle("Practice vs competition")
    if (view.insufficient != null) { InsufficientData(view.insufficient); return@AnalysisCard }
    Text(fmt(view.gap), style = MaterialTheme.typography.displaySmall)
    Text(
        "Practice ${fmt(view.practiceAverage)} · Competition ${fmt(view.competitionAverage)}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    view.gap?.let { gap ->
        Takeaway(
            if (gap <= 2) "Your competition scores hold up — pressure isn't costing you points."
            else "You drop about ${gap.toInt()} points per round in competition versus practice.",
        )
    }
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
    trendDelta(view)?.let { d ->
        val n = d.toInt()
        Takeaway(
            when {
                n >= 3 -> "Trending up — about +$n per round versus your previous five."
                n <= -3 -> "Dipping lately — about $n per round versus your previous five."
                else -> "Holding steady over your recent rounds."
            },
        )
    }
    Sparkline(view.practiceRollingAverage.map { it.value }, view.competitionRollingAverage.map { it.value })
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.l)) {
        Text("● Practice", style = MaterialTheme.typography.labelMedium, color = Practice)
        Text("● Competition", style = MaterialTheme.typography.labelMedium, color = Competition)
    }
    Text(
        "Best ever — practice ${view.bestEver.practice?.total ?: "—"}, competition ${view.bestEver.competition?.total ?: "—"}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        Bar(bucket.count.toFloat() / maxCount, color = arrowChipColor(label))
    }
}

@Composable
private fun ScaledBars(entries: List<PositionAverageDto>, label: (Int) -> String) {
    val values = entries.map { it.average }
    val min = values.minOrNull() ?: 0.0
    val max = values.maxOrNull() ?: 0.0
    val weakest = entries.minByOrNull { it.average }?.position
    entries.forEach { entry ->
        val frac = if (max == min) 1f else (0.2 + 0.8 * ((entry.average - min) / (max - min))).toFloat()
        Text("${label(entry.position)}: ${fmt(entry.average)}", style = MaterialTheme.typography.labelMedium)
        Bar(frac, color = if (entry.position == weakest) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
    }
}

@Composable
private fun PatternsCard(view: PatternsViewDto) = AnalysisCard {
    SectionTitle("Within-session patterns")
    if (view.insufficient != null) { InsufficientData(view.insufficient); return@AnalysisCard }
    view.byEndPosition.minByOrNull { it.average }?.let { weakest ->
        Takeaway("You score lowest on end ${weakest.position} (${fmt(weakest.average)}) — watch for fatigue or rushing.")
    }
    Text("Average by end position", style = MaterialTheme.typography.labelLarge)
    ScaledBars(view.byEndPosition) { "End $it" }
    Text("Average by round position", style = MaterialTheme.typography.labelLarge)
    ScaledBars(view.byRoundPosition) { "Round $it" }
}

@Composable
private fun NotEnoughData(roundCount: Int) = AnalysisCard {
    SectionTitle("Keep logging")
    val remaining = MIN_ROUNDS_FOR_ANALYSIS - roundCount
    Text(
        "You've logged $roundCount ${if (roundCount == 1) "round" else "rounds"}. " +
            "Log $remaining more ${if (remaining == 1) "round" else "rounds"} to unlock your analysis.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Bar(roundCount.toFloat() / MIN_ROUNDS_FOR_ANALYSIS)
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
                .padding(Spacing.screen)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.m),
        ) {
            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = viewModel::load, modifier = Modifier.padding(top = Spacing.s)) { Text("Try again") }
            }
            state.stats?.let { stats ->
                if (stats.roundCount < MIN_ROUNDS_FOR_ANALYSIS) {
                    NotEnoughData(stats.roundCount)
                } else {
                    Headline(stats)
                    GapCard(stats.gap)
                    TrendCard(stats.trend)
                    ConsistencyCard(stats.consistency)
                    PatternsCard(stats.patterns)
                }
            }
        }
    }
}

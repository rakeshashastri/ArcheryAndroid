package com.archery.tracker.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.archery.tracker.di.AppContainer
import com.archery.tracker.ui.analysis.AnalysisScreen
import com.archery.tracker.ui.history.HistoryScreen
import com.archery.tracker.ui.livescoring.LiveScoringScreen
import com.archery.tracker.ui.newsession.NewSessionScreen
import com.archery.tracker.ui.sessiondetail.SessionDetailScreen

private const val ROUTE_HISTORY = "history"
private const val ROUTE_ANALYSIS = "analysis"
private const val ROUTE_NEW_SESSION = "newSession"
private const val ROUTE_LIVE_SCORING = "liveScoring/{sessionId}/{roundId}"
private const val ROUTE_SESSION_DETAIL = "sessionDetail/{sessionId}"

fun liveScoringRoute(sessionId: String, roundId: String) = "liveScoring/$sessionId/$roundId"
fun sessionDetailRoute(sessionId: String) = "sessionDetail/$sessionId"

@Composable
fun AppNav(container: AppContainer) {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            val backStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = backStackEntry?.destination?.hierarchy?.firstOrNull()?.route
            NavigationBar {
                NavigationBarItem(
                    selected = currentRoute == ROUTE_HISTORY,
                    onClick = { navController.navigate(ROUTE_HISTORY) },
                    icon = { Icon(Icons.Filled.History, contentDescription = "History") },
                    label = { Text("History") },
                )
                NavigationBarItem(
                    selected = currentRoute == ROUTE_ANALYSIS,
                    onClick = { navController.navigate(ROUTE_ANALYSIS) },
                    icon = { Icon(Icons.Filled.BarChart, contentDescription = "Analysis") },
                    label = { Text("Analysis") },
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = ROUTE_HISTORY,
            modifier = Modifier.padding(padding),
        ) {
            composable(ROUTE_HISTORY) { HistoryScreen(container, navController) }
            composable(ROUTE_ANALYSIS) { AnalysisScreen(container) }
            composable(ROUTE_NEW_SESSION) { NewSessionScreen(container, navController) }
            composable(ROUTE_LIVE_SCORING) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
                val roundId = backStackEntry.arguments?.getString("roundId").orEmpty()
                LiveScoringScreen(container, sessionId, roundId, navController)
            }
            composable(ROUTE_SESSION_DETAIL) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString("sessionId").orEmpty()
                SessionDetailScreen(container, sessionId, navController)
            }
        }
    }
}

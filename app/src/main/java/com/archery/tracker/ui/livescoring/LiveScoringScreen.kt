package com.archery.tracker.ui.livescoring

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.archery.tracker.di.AppContainer

@Composable
fun LiveScoringScreen(
    container: AppContainer,
    sessionId: String,
    roundId: String,
    navController: NavController,
) {
    Text("Live scoring")
}

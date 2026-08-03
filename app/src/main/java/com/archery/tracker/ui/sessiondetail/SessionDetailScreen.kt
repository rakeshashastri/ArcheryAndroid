package com.archery.tracker.ui.sessiondetail

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.archery.tracker.di.AppContainer

@Composable
fun SessionDetailScreen(container: AppContainer, sessionId: String, navController: NavController) {
    Text("Session detail")
}

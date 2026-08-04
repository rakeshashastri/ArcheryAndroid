package com.archery.tracker.ui

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.archery.tracker.ArcheryApplication
import com.archery.tracker.ui.theme.ArcheryTheme
import com.archery.tracker.ui.theme.LocalThemeController
import com.archery.tracker.ui.theme.ThemeController
import com.archery.tracker.ui.theme.ThemeMode

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ArcheryApplication).container
        val prefs = getSharedPreferences("settings", Context.MODE_PRIVATE)
        setContent {
            var mode by remember {
                mutableStateOf(runCatching { ThemeMode.valueOf(prefs.getString("theme_mode", null) ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM))
            }
            val controller = ThemeController(mode) { next ->
                mode = next
                prefs.edit().putString("theme_mode", next.name).apply()
            }
            ArcheryTheme(mode) {
                CompositionLocalProvider(LocalThemeController provides controller) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        AppNav(container)
                    }
                }
            }
        }
    }
}

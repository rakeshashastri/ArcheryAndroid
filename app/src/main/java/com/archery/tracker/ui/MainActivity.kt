package com.archery.tracker.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.archery.tracker.ArcheryApplication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val container = (application as ArcheryApplication).container
        setContent {
            MaterialTheme {
                Surface {
                    AppNav(container)
                }
            }
        }
    }
}

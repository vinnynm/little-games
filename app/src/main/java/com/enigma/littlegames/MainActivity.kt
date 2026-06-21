package com.enigma.littlegames

// ─────────────────────────────────────────────────────────────────────────────
// MainActivity — single-activity host for Compose
// ─────────────────────────────────────────────────────────────────────────────

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import com.enigma.littlegames.common.GameHubApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // Minimal MaterialTheme wrapper so Material3 components render
            MaterialTheme {
                Surface {
                    GameHubApp()
                }
            }
        }
    }
}
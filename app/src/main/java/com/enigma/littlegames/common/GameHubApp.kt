package com.enigma.littlegames.common

// ─────────────────────────────────────────────────────────────────────────────
// Root composable — wires navigation, theme, achievement toast
// ─────────────────────────────────────────────────────────────────────────────

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.enigma.littlegames.ui.home.HomeScreen
import com.enigma.littlegames.domain.AchievementsScreen
import com.enigma.littlegames.ui.settings.SettingsScreen
import com.enigma.littlegames.ui.games.lightsout.LightsOutScreen
import com.enigma.littlegames.ui.games.pipeflow.PipeFlowScreen
import com.enigma.littlegames.ui.games.killerSudoku.KillerSudokuScreen

@Composable
fun GameHubApp(hub: HubViewModel = viewModel()) {
    val ui     by hub.ui.collectAsStateWithLifecycle()
    val screen by hub.screen.collectAsStateWithLifecycle()
    val theme   = ui.theme

    CompositionLocalProvider(LocalGameTheme provides theme) {
        Box(
            Modifier
                .fillMaxSize()
                .background(theme.background)
        ) {
            // ── Screen router ─────────────────────────────────────────────────
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val enter = slideInHorizontally(tween(300)) { it } + fadeIn(tween(300))
                    val exit  = slideOutHorizontally(tween(300)) { -it } + fadeOut(tween(300))
                    enter togetherWith exit
                },
                label = "hub_nav",
            ) { s ->
                when (s) {
                    HubScreen.Home               -> HomeScreen(hub)
                    HubScreen.Settings           -> SettingsScreen(hub)
                    HubScreen.AchievementsScreen -> AchievementsScreen(hub)
                    HubScreen.LightsOut          -> LightsOutScreen(hub)
                    HubScreen.PipeFlow           -> PipeFlowScreen(hub)
                    HubScreen.KillerSudoku       -> KillerSudokuScreen(hub)
                }
            }

            // ── Achievement toast (floats above every screen) ─────────────────
            AchievementToast(
                achievement = ui.newAchievement,
                onDismiss   = hub::dismissAchievement,
                modifier    = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

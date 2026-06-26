package com.enigma.littlegames.common

// ─────────────────────────────────────────────────────────────────────────────
// Root composable — Phase 4d FINAL: all 13 navigable screens
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
import com.enigma.littlegames.ui.games.sudoku.SudokuScreen
import com.enigma.littlegames.ui.games.kakuro.KakuroScreen
import com.enigma.littlegames.ui.games.simon.SimonScreen
import com.enigma.littlegames.ui.games.twentyfortyeight.TwentyFortyEightScreen
import com.enigma.littlegames.ui.games.sliding.SlidingPuzzleScreen
import com.enigma.littlegames.ui.games.nonogram.NonogramScreen
import com.enigma.littlegames.ui.games.sokoban.SokobanScreen
import com.enigma.littlegames.ui.games.flowfree.FlowFreeScreen
import com.enigma.littlegames.ui.games.wordle.WordleScreen
import com.enigma.littlegames.ui.games.minesweeper.MinesweeperScreen

@Composable
fun GameHubApp(hub: HubViewModel = viewModel()) {
    val ui     by hub.ui.collectAsStateWithLifecycle()
    val screen by hub.screen.collectAsStateWithLifecycle()
    val theme   = ui.theme

    CompositionLocalProvider(LocalGameTheme provides theme) {
        Box(Modifier.fillMaxSize().background(theme.background)) {
            AnimatedContent(
                targetState = screen,
                transitionSpec = {
                    val enter = slideInHorizontally(tween(280)) { it } + fadeIn(tween(280))
                    val exit  = slideOutHorizontally(tween(280)) { -it } + fadeOut(tween(280))
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
                    HubScreen.Sudoku             -> SudokuScreen(hub)
                    HubScreen.Kakuro             -> KakuroScreen(hub)
                    HubScreen.Simon              -> SimonScreen(hub)
                    HubScreen.TwentyFortyEight   -> TwentyFortyEightScreen(hub)
                    HubScreen.SlidingPuzzle      -> SlidingPuzzleScreen(hub)
                    HubScreen.Nonogram           -> NonogramScreen(hub)
                    HubScreen.Sokoban            -> SokobanScreen(hub)
                    HubScreen.FlowFree           -> FlowFreeScreen(hub)
                    HubScreen.Wordle             -> WordleScreen(hub)
                    HubScreen.Minesweeper        -> MinesweeperScreen(hub)
                }
            }

            AchievementToast(
                achievement = ui.newAchievement,
                onDismiss   = hub::dismissAchievement,
                modifier    = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

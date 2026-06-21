package com.enigma.littlegames.common

// ─────────────────────────────────────────────────────────────────────────────
// HubViewModel — Phase 3: adds Classic Sudoku + Kakuro to navigation,
// wires their win events, and tracks all-games achievement for 5 games.
// ─────────────────────────────────────────────────────────────────────────────

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enigma.littlegames.data.PreferencesRepository
import com.enigma.littlegames.domain.Achievement
import com.enigma.littlegames.domain.AchievementTracker
import com.enigma.littlegames.domain.Achievements
import com.enigma.littlegames.domain.SoundEngine
import com.enigma.littlegames.domain.Sfx
import com.enigma.littlegames.domain.ThemeAmbient
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

// ── Navigation screens ────────────────────────────────────────────────────────

sealed class HubScreen {
    object Home               : HubScreen()
    object LightsOut          : HubScreen()
    object PipeFlow           : HubScreen()
    object KillerSudoku       : HubScreen()
    object Sudoku             : HubScreen()   // NEW — Classic Sudoku
    object Kakuro             : HubScreen()   // NEW — Kakuro
    object Settings           : HubScreen()
    object AchievementsScreen : HubScreen()
}

// ── UI state ──────────────────────────────────────────────────────────────────

data class HubUiState(
    val theme: GameTheme          = GameThemes.CYBER,
    val pipeLevel: Int             = 1,
    val pipeStars: Int             = 0,
    val loBestMoves: Int?          = null,
    val sudokuWins: Int            = 0,
    val soundEnabled: Boolean      = true,
    val unlockedIds: Set<String>   = emptySet(),
    val newAchievement: Achievement? = null,
)

// ── ViewModel ─────────────────────────────────────────────────────────────────

class HubViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PreferencesRepository(app.applicationContext)
    val sound = SoundEngine(app.applicationContext)

    private val _screen = MutableStateFlow<HubScreen>(HubScreen.Home)
    val screen: StateFlow<HubScreen> = _screen.asStateFlow()

    private val _ui = MutableStateFlow(HubUiState())
    val ui: StateFlow<HubUiState> = _ui.asStateFlow()

    private lateinit var tracker: AchievementTracker

    init {
        viewModelScope.launch {
            repo.prefsFlow.collect { prefs ->
                val theme = GameThemes.byId(prefs.themeId)
                if (!::tracker.isInitialized) {
                    tracker = AchievementTracker(emptySet()) { achievement ->
                        _ui.update { it.copy(newAchievement = achievement) }
                        sound.play(Sfx.ACHIEVEMENT)
                        persistAchievements()
                    }
                    tracker.loadFromJson(prefs.achievementsJson)
                }
                _ui.update {
                    it.copy(
                        theme        = theme,
                        pipeLevel    = prefs.pipeLevel,
                        pipeStars    = prefs.pipeStars,
                        loBestMoves  = prefs.loBestMoves,
                        sudokuWins   = prefs.sudokuWins,
                        soundEnabled = prefs.soundEnabled,
                        unlockedIds  = tracker.unlockedIds.toSet(),
                    )
                }
                sound.enabled = prefs.soundEnabled
                if (prefs.soundEnabled) {
                    sound.startAmbient(getApplication(), ThemeAmbient.resForTheme(theme.id))
                }
            }
        }
    }

    // ── Navigation ────────────────────────────────────────────────────────────
    fun navigate(s: HubScreen) { _screen.value = s }

    // ── Theme ─────────────────────────────────────────────────────────────────
    fun setTheme(theme: GameTheme) {
        viewModelScope.launch {
            repo.saveTheme(theme.id)
            sound.startAmbient(getApplication(), ThemeAmbient.resForTheme(theme.id))
        }
    }

    // ── Sound ─────────────────────────────────────────────────────────────────
    fun setSoundEnabled(on: Boolean) {
        sound.enabled = on
        if (!on) sound.stopAmbient()
        viewModelScope.launch { repo.saveSoundEnabled(on) }
    }

    // ── Lights Out ────────────────────────────────────────────────────────────
    fun recordLightsOutWin(moves: Int, difficulty: String, usedHint: Boolean) {
        val best = _ui.value.loBestMoves
        if (best == null || moves < best) {
            viewModelScope.launch { repo.saveLOBest(moves) }
        }
        sound.play(Sfx.VICTORY)
        tryUnlock(Achievements.LO_FIRST_WIN)
        if (best == null || moves <= (_ui.value.loBestMoves ?: Int.MAX_VALUE)) tryUnlock(Achievements.LO_PERFECT)
        if (difficulty == "Expert") tryUnlock(Achievements.LO_EXPERT)
        if (moves <= 10) tryUnlock(Achievements.LO_SPEED)
        if (!usedHint && (difficulty == "Hard" || difficulty == "Expert")) tryUnlock(Achievements.LO_NO_HINT)
        checkAllGames()
    }

    // ── Pipe Flow ─────────────────────────────────────────────────────────────
    fun recordPipeWin(level: Int, stars: Int, totalStars: Int, usedAutoSolve: Boolean) {
        sound.play(Sfx.VICTORY)
        viewModelScope.launch { repo.savePipeProgress(level, totalStars) }
        tryUnlock(Achievements.PIPE_FIRST_WIN)
        if (stars == 3) tryUnlock(Achievements.PIPE_3STARS)
        if (level >= 10) tryUnlock(Achievements.PIPE_LEVEL10)
        if (level >= 24) tryUnlock(Achievements.PIPE_LEVEL24)
        checkAllGames()
    }

    // ── Killer Sudoku + Classic Sudoku + Kakuro (shared counter) ─────────────
    fun recordSudokuWin(difficulty: String, errorCount: Int, elapsedSecs: Long) {
        sound.play(Sfx.VICTORY)
        val newWins = _ui.value.sudokuWins + 1
        viewModelScope.launch { repo.saveSudokuWins(newWins) }
        tryUnlock(Achievements.SK_FIRST_WIN)
        if (errorCount == 0) tryUnlock(Achievements.SK_NO_ERROR)
        if (difficulty.contains("Expert")) tryUnlock(Achievements.SK_EXPERT)
        if (newWins >= 5) tryUnlock(Achievements.SK_FIVE_WINS)
        if (difficulty.contains("Medium") && elapsedSecs < 180) tryUnlock(Achievements.SK_SPEED_RUN)
        checkAllGames()
    }

    private fun checkAllGames() {
        val hasLO     = tracker.isUnlocked(Achievements.LO_FIRST_WIN.id)
        val hasPipe   = tracker.isUnlocked(Achievements.PIPE_FIRST_WIN.id)
        val hasSudoku = tracker.isUnlocked(Achievements.SK_FIRST_WIN.id)
        if (hasLO && hasPipe && hasSudoku) tryUnlock(Achievements.ALL_GAMES)
    }

    private fun tryUnlock(a: Achievement) {
        if (!::tracker.isInitialized) return
        tracker.unlock(a)
        _ui.update { it.copy(unlockedIds = tracker.unlockedIds.toSet()) }
    }

    private fun persistAchievements() {
        viewModelScope.launch { repo.saveAchievements(tracker.toJson()) }
    }

    fun dismissAchievement() { _ui.update { it.copy(newAchievement = null) } }

    override fun onCleared() {
        super.onCleared()
        sound.release()
    }
}

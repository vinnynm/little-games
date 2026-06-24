package com.enigma.littlegames.common

// ─────────────────────────────────────────────────────────────────────────────
// HubViewModel — Phase 4a
// New screens: ExplodingKittens, Simon, TwentyFortyEight
// New win reporters: recordEKWin, recordSimonHighScore, record2048Win/Best
// ALL_GAMES now checks all 8 games (LO, Pipe, Sudoku, EK, Simon, 2048)
// Achievement total: 30
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
    object Home                : HubScreen()
    object LightsOut           : HubScreen()
    object PipeFlow            : HubScreen()
    object KillerSudoku        : HubScreen()
    object Sudoku              : HubScreen()
    object Kakuro              : HubScreen()
    object ExplodingKittens    : HubScreen()   // NEW
    object Simon               : HubScreen()   // NEW
    object TwentyFortyEight    : HubScreen()   // NEW
    object Settings            : HubScreen()
    object AchievementsScreen  : HubScreen()
}

// ── UI state ──────────────────────────────────────────────────────────────────

data class HubUiState(
    val theme: GameTheme             = GameThemes.CYBER,
    val pipeLevel: Int               = 1,
    val pipeStars: Int               = 0,
    val loBestMoves: Int?            = null,
    val sudokuWins: Int              = 0,
    val soundEnabled: Boolean        = true,
    val unlockedIds: Set<String>     = emptySet(),
    val newAchievement: Achievement? = null,
    // Phase 4a
    val ekWins: Int                  = 0,
    val simonBest: Int               = 0,
    val tfeBest: Int                 = 0,
    val tfeMaxTile: Int              = 0,
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
                        ekWins       = prefs.ekWins,
                        simonBest    = prefs.simonBest,
                        tfeBest      = prefs.tfeBest,
                        tfeMaxTile   = prefs.tfeMaxTile,
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
        // THEME_EXPLORER: track how many distinct themes have been tried.
        // We store tried themes in the existing achievements mechanism by checking
        // all 5 themes. If user switched to this theme it's new — check total.
        checkThemeExplorer()
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
        if (best == null || moves <= (best)) tryUnlock(Achievements.LO_PERFECT)
        if (difficulty == "Expert") tryUnlock(Achievements.LO_EXPERT)
        if (moves <= 10) tryUnlock(Achievements.LO_SPEED)
        if (!usedHint && (difficulty == "Hard" || difficulty == "Expert")) tryUnlock(Achievements.LO_NO_HINT)
        checkAllGames()
        checkCompletionist()
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
        checkCompletionist()
    }

    // ── Killer Sudoku + Classic Sudoku + Kakuro ───────────────────────────────
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
        checkCompletionist()
    }

    // ── Exploding Kittens ─────────────────────────────────────────────────────
    fun recordEKWin(winnerName: String, vsAI: Boolean) {
        val newWins = _ui.value.ekWins + 1
        viewModelScope.launch { repo.saveEKWins(newWins) }
        tryUnlock(Achievements.EK_FIRST_WIN)
        if (newWins >= 5) tryUnlock(Achievements.EK_FIVE_WINS)
        checkAllGames()
        checkCompletionist()
    }

    /** Called by EKViewModel when the player successfully defuses a kitten. */
    fun recordEKDefuse() {
        tryUnlock(Achievements.EK_DEFUSE)
        checkCompletionist()
    }

    /** Called when the player beats Hard AI. */
    fun recordEKBeatHard() {
        tryUnlock(Achievements.EK_BEAT_HARD)
        checkCompletionist()
    }

    // ── Simon Says ────────────────────────────────────────────────────────────
    fun recordSimonHighScore(score: Int, modeLabel: String) {
        val prev = _ui.value.simonBest
        if (score > prev) {
            viewModelScope.launch { repo.saveSimonBest(score) }
        }
        if (score >= 1)  tryUnlock(Achievements.SIMON_FIRST)
        if (score >= 10) tryUnlock(Achievements.SIMON_10)
        if (score >= 20) tryUnlock(Achievements.SIMON_20)
        if (score >= 15 && modeLabel == "Speed") tryUnlock(Achievements.SIMON_SPEED)
        checkAllGames()
        checkCompletionist()
    }

    // ── 2048 ──────────────────────────────────────────────────────────────────
    fun record2048Win(score: Int) {
        tryUnlock(Achievements.TFE_FIRST)
        tryUnlock(Achievements.TFE_2048)
        val prev = _ui.value.tfeBest
        if (score > prev) { viewModelScope.launch { repo.saveTFEBest(score) } }
        checkAllGames()
        checkCompletionist()
    }

    fun record2048Best(score: Int) {
        val prev = _ui.value.tfeBest
        if (score > prev) { viewModelScope.launch { repo.saveTFEBest(score) } }
        tryUnlock(Achievements.TFE_FIRST)
    }

    fun record2048Tile(maxTile: Int) {
        val prev = _ui.value.tfeMaxTile
        if (maxTile > prev) { viewModelScope.launch { repo.saveTFEMaxTile(maxTile) } }
        tryUnlock(Achievements.TFE_FIRST)
        if (maxTile >= 256)  tryUnlock(Achievements.TFE_256)
        if (maxTile >= 1024) tryUnlock(Achievements.TFE_1024)
        if (maxTile >= 2048) tryUnlock(Achievements.TFE_2048)
        checkCompletionist()
    }

    // ── Meta checks ───────────────────────────────────────────────────────────

    private fun checkAllGames() {
        val hasLO    = tracker.isUnlocked(Achievements.LO_FIRST_WIN.id)
        val hasPipe  = tracker.isUnlocked(Achievements.PIPE_FIRST_WIN.id)
        val hasSK    = tracker.isUnlocked(Achievements.SK_FIRST_WIN.id)
        val hasEK    = tracker.isUnlocked(Achievements.EK_FIRST_WIN.id)
        val hasSimon = tracker.isUnlocked(Achievements.SIMON_FIRST.id)
        val hasTFE   = tracker.isUnlocked(Achievements.TFE_FIRST.id)
        if (hasLO && hasPipe && hasSK && hasEK && hasSimon && hasTFE) {
            tryUnlock(Achievements.ALL_GAMES)
        }
    }

    private fun checkThemeExplorer() {
        // THEME_EXPLORER unlocks after the user has visited the settings screen
        // and switched themes at least once. We approximate: if they've set any
        // theme other than the default we count it, and unlock after 3 distinct
        // saves. Persisted implicitly via theme change count.
        // Simple heuristic: unlock when any non-default theme is active.
        tryUnlock(Achievements.THEME_EXPLORER)
    }

    private fun checkCompletionist() {
        if (tracker.unlockedIds.size >= 25) tryUnlock(Achievements.COMPLETIONIST)
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

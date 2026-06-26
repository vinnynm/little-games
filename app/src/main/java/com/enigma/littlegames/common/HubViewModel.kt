package com.enigma.littlegames.common

// ─────────────────────────────────────────────────────────────────────────────
// HubViewModel — Phase 4d (FINAL): adds Flow Free, Wordle, Minesweeper.
// Hub now contains 13 navigable screens (10 distinct game families).
// ALL_GAMES requires wins in all 10 families.
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
    object Sudoku             : HubScreen()
    object Kakuro             : HubScreen()
    object Simon              : HubScreen()
    object TwentyFortyEight   : HubScreen()
    object SlidingPuzzle      : HubScreen()
    object Nonogram           : HubScreen()
    object Sokoban            : HubScreen()
    object FlowFree           : HubScreen()   // NEW
    object Wordle             : HubScreen()   // NEW
    object Minesweeper        : HubScreen()   // NEW
    object Settings           : HubScreen()
    object AchievementsScreen : HubScreen()
}

// ── UI state ──────────────────────────────────────────────────────────────────

data class HubUiState(
    val theme: GameTheme             = GameThemes.CYBER,
    val pipeLevel: Int               = 1,
    val pipeStars: Int               = 0,
    val loBestMoves: Int?            = null,
    val sudokuWins: Int              = 0,
    val simonBest: Int               = 0,
    val tfeScore: Int                = 0,
    val slidingWins: Int             = 0,
    val nonogramWins: Int            = 0,
    val sokobanLevel: Int            = 1,
    val flowWins: Int                = 0,
    val wordleWins: Int              = 0,
    val mineWins: Int                = 0,
    val ekWins: Int                  = 0,
    val soundEnabled: Boolean        = true,
    val unlockedIds: Set<String>     = emptySet(),
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
                        simonBest    = prefs.simonBest,
                        tfeScore     = prefs.tfeBest,
                        ekWins       = prefs.ekWins,
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

    fun navigate(s: HubScreen) { _screen.value = s }

    fun setTheme(theme: GameTheme) {
        viewModelScope.launch {
            repo.saveTheme(theme.id)
            sound.startAmbient(getApplication(), ThemeAmbient.resForTheme(theme.id))
        }
        tryUnlock(Achievements.THEME_EXPLORER)
    }

    fun setSoundEnabled(on: Boolean) {
        sound.enabled = on
        if (!on) sound.stopAmbient()
        viewModelScope.launch { repo.saveSoundEnabled(on) }
    }

    // ── Game record methods ───────────────────────────────────────────────────

    fun recordLightsOutWin(moves: Int, difficulty: String, usedHint: Boolean) {
        val best = _ui.value.loBestMoves
        if (best == null || moves < best) viewModelScope.launch { repo.saveLOBest(moves) }
        sound.play(Sfx.VICTORY)
        tryUnlock(Achievements.LO_FIRST_WIN)
        if (best == null || moves <= (_ui.value.loBestMoves ?: Int.MAX_VALUE)) tryUnlock(Achievements.LO_PERFECT)
        if (difficulty == "Expert") tryUnlock(Achievements.LO_EXPERT)
        if (moves <= 10) tryUnlock(Achievements.LO_SPEED)
        if (!usedHint && (difficulty == "Hard" || difficulty == "Expert")) tryUnlock(Achievements.LO_NO_HINT)
        checkAllGames()
    }

    fun recordPipeWin(level: Int, stars: Int, totalStars: Int, usedAutoSolve: Boolean) {
        sound.play(Sfx.VICTORY)
        viewModelScope.launch { repo.savePipeProgress(level, totalStars) }
        tryUnlock(Achievements.PIPE_FIRST_WIN)
        if (stars == 3) tryUnlock(Achievements.PIPE_3STARS)
        if (level >= 10) tryUnlock(Achievements.PIPE_LEVEL10)
        if (level >= 24) tryUnlock(Achievements.PIPE_LEVEL24)
        checkAllGames()
    }

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

    fun recordSimonScore(score: Int) {
        if (score > _ui.value.simonBest) _ui.update { it.copy(simonBest = score) }
        tryUnlock(Achievements.SIMON_FIRST)
        if (score >= 10) tryUnlock(Achievements.SIMON_TEN)
        if (score >= 25) tryUnlock(Achievements.SIMON_MASTER)
        checkAllGames()
    }

    fun recordTFEScore(score: Int, maxTile: Int) {
        if (score > _ui.value.tfeScore) _ui.update { it.copy(tfeScore = score) }
        tryUnlock(Achievements.TFE_FIRST)
        if (maxTile >= 512)  tryUnlock(Achievements.TFE_512)
        if (maxTile >= 2048) tryUnlock(Achievements.TFE_2048)
        checkAllGames()
    }

    fun recordSlidingWin(size: Int, moves: Int) {
        _ui.update { it.copy(slidingWins = it.slidingWins + 1) }
        tryUnlock(Achievements.SLIDE_FIRST)
        if (size >= 4) tryUnlock(Achievements.SLIDE_FOUR)
        if (size >= 5) tryUnlock(Achievements.SLIDE_EXPERT)
        checkAllGames()
    }

    fun recordNonogramWin(difficulty: String, errorCount: Int, elapsedSecs: Long, size: Int) {
        _ui.update { it.copy(nonogramWins = it.nonogramWins + 1) }
        tryUnlock(Achievements.NONO_FIRST)
        if (errorCount == 0) tryUnlock(Achievements.NONO_PERFECT)
        if (size >= 20) tryUnlock(Achievements.NONO_EXPERT)
        if (size == 10 && elapsedSecs < 300) tryUnlock(Achievements.NONO_SPEED)
        checkAllGames()
    }

    fun recordSokobanWin(level: Int, moves: Int) {
        if (level > _ui.value.sokobanLevel) _ui.update { it.copy(sokobanLevel = level) }
        tryUnlock(Achievements.SOKO_FIRST)
        if (level >= 10) tryUnlock(Achievements.SOKO_TEN)
        if (level >= 30) tryUnlock(Achievements.SOKO_MASTER)
        checkAllGames()
    }

    fun recordFlowFreeWin(difficulty: String, moves: Int) {
        _ui.update { it.copy(flowWins = it.flowWins + 1) }
        tryUnlock(Achievements.FLOW_FIRST)
        if (difficulty == "Expert") tryUnlock(Achievements.FLOW_EXPERT)
        checkAllGames()
    }

    fun recordWordleWin(attempts: Int, hardMode: Boolean) {
        _ui.update { it.copy(wordleWins = it.wordleWins + 1) }
        tryUnlock(Achievements.WORD_FIRST)
        if (attempts == 1) tryUnlock(Achievements.WORD_ACE)
        if (hardMode) tryUnlock(Achievements.WORD_HARD)
        checkAllGames()
    }

    fun recordMinesweeperWin(difficulty: String, elapsedSecs: Long) {
        _ui.update { it.copy(mineWins = it.mineWins + 1) }
        tryUnlock(Achievements.MINE_FIRST)
        if (difficulty == "Expert") tryUnlock(Achievements.MINE_EXPERT)
        if (elapsedSecs < 60) tryUnlock(Achievements.MINE_SPEED)
        checkAllGames()
    }

    fun recordEKWin(winner: String, vsAI: Boolean) {
        _ui.update { it.copy(ekWins = it.ekWins + 1) }
        viewModelScope.launch { repo.saveEKWins(_ui.value.ekWins) }
        checkAllGames()
    }

    // ── ALL_GAMES — all 10 distinct families ──────────────────────────────────
    private fun checkAllGames() {
        val allDone = listOf(
            Achievements.LO_FIRST_WIN.id,
            Achievements.PIPE_FIRST_WIN.id,
            Achievements.SK_FIRST_WIN.id,
            Achievements.SIMON_FIRST.id,
            Achievements.TFE_FIRST.id,
            Achievements.SLIDE_FIRST.id,
            Achievements.NONO_FIRST.id,
            Achievements.SOKO_FIRST.id,
            Achievements.FLOW_FIRST.id,
            Achievements.WORD_FIRST.id,
        ).all { tracker.isUnlocked(it) }
        if (allDone) tryUnlock(Achievements.ALL_GAMES)
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

    override fun onCleared() { super.onCleared(); sound.release() }
}

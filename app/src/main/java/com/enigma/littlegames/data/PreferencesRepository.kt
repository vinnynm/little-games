package com.enigma.littlegames.data

// ─────────────────────────────────────────────────────────────────────────────
// Phase 2 · DataStore persistence
// Persists: theme, pipe campaign level/stars, LO best moves, sudoku wins,
//           sound toggle, and unlocked achievement IDs (JSON array string).
// ─────────────────────────────────────────────────────────────────────────────

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "enigma_prefs")

object PrefsKeys {
    val THEME_ID         = stringPreferencesKey("theme_id")
    val PIPE_LEVEL       = intPreferencesKey("pipe_level")
    val PIPE_STARS       = intPreferencesKey("pipe_stars")
    val LO_BEST_MOVES    = intPreferencesKey("lo_best_moves")
    val SUDOKU_WINS      = intPreferencesKey("sudoku_wins")
    val SOUND_ENABLED    = booleanPreferencesKey("sound_enabled")
    val ACHIEVEMENTS     = stringPreferencesKey("achievements_json")
}

data class AppPreferences(
    val themeId: String         = "cyber",
    val pipeLevel: Int          = 1,
    val pipeStars: Int          = 0,
    val loBestMoves: Int?       = null,
    val sudokuWins: Int         = 0,
    val soundEnabled: Boolean   = true,
    val achievementsJson: String = "[]",
)

class PreferencesRepository(private val context: Context) {

    val prefsFlow: Flow<AppPreferences> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppPreferences(
                themeId          = p[PrefsKeys.THEME_ID] ?: "cyber",
                pipeLevel        = p[PrefsKeys.PIPE_LEVEL] ?: 1,
                pipeStars        = p[PrefsKeys.PIPE_STARS] ?: 0,
                loBestMoves      = p[PrefsKeys.LO_BEST_MOVES],
                sudokuWins       = p[PrefsKeys.SUDOKU_WINS] ?: 0,
                soundEnabled     = p[PrefsKeys.SOUND_ENABLED] ?: true,
                achievementsJson = p[PrefsKeys.ACHIEVEMENTS] ?: "[]",
            )
        }

    suspend fun saveTheme(id: String)           = context.dataStore.edit { it[PrefsKeys.THEME_ID]      = id    }
    suspend fun savePipeProgress(lvl: Int, stars: Int) = context.dataStore.edit {
        it[PrefsKeys.PIPE_LEVEL] = lvl; it[PrefsKeys.PIPE_STARS] = stars
    }
    suspend fun saveLOBest(moves: Int)          = context.dataStore.edit { it[PrefsKeys.LO_BEST_MOVES]  = moves }
    suspend fun saveSudokuWins(wins: Int)       = context.dataStore.edit { it[PrefsKeys.SUDOKU_WINS]    = wins  }
    suspend fun saveSoundEnabled(on: Boolean)   = context.dataStore.edit { it[PrefsKeys.SOUND_ENABLED]  = on    }
    suspend fun saveAchievements(json: String)  = context.dataStore.edit { it[PrefsKeys.ACHIEVEMENTS]   = json  }
}

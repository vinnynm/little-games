package com.enigma.littlegames.data

// ─────────────────────────────────────────────────────────────────────────────
// Phase 4a — DataStore persistence
// Added: ekWins, simonBest, tfeBest
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
    val THEME_ID      = stringPreferencesKey("theme_id")
    val PIPE_LEVEL    = intPreferencesKey("pipe_level")
    val PIPE_STARS    = intPreferencesKey("pipe_stars")
    val LO_BEST_MOVES = intPreferencesKey("lo_best_moves")
    val SUDOKU_WINS   = intPreferencesKey("sudoku_wins")
    val SOUND_ENABLED = booleanPreferencesKey("sound_enabled")
    val ACHIEVEMENTS  = stringPreferencesKey("achievements_json")
    // Phase 4a
    val EK_WINS       = intPreferencesKey("ek_wins")
    val SIMON_BEST    = intPreferencesKey("simon_best")
    val TFE_BEST      = intPreferencesKey("tfe_best")
    val TFE_MAX_TILE  = intPreferencesKey("tfe_max_tile")
}

data class AppPreferences(
    val themeId: String          = "cyber",
    val pipeLevel: Int           = 1,
    val pipeStars: Int           = 0,
    val loBestMoves: Int?        = null,
    val sudokuWins: Int          = 0,
    val soundEnabled: Boolean    = true,
    val achievementsJson: String = "[]",
    // Phase 4a
    val ekWins: Int              = 0,
    val simonBest: Int           = 0,
    val tfeBest: Int             = 0,
    val tfeMaxTile: Int          = 0,
)

class PreferencesRepository(private val context: Context) {

    val prefsFlow: Flow<AppPreferences> = context.dataStore.data
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { p ->
            AppPreferences(
                themeId          = p[PrefsKeys.THEME_ID]      ?: "cyber",
                pipeLevel        = p[PrefsKeys.PIPE_LEVEL]    ?: 1,
                pipeStars        = p[PrefsKeys.PIPE_STARS]    ?: 0,
                loBestMoves      = p[PrefsKeys.LO_BEST_MOVES],
                sudokuWins       = p[PrefsKeys.SUDOKU_WINS]   ?: 0,
                soundEnabled     = p[PrefsKeys.SOUND_ENABLED] ?: true,
                achievementsJson = p[PrefsKeys.ACHIEVEMENTS]  ?: "[]",
                ekWins           = p[PrefsKeys.EK_WINS]       ?: 0,
                simonBest        = p[PrefsKeys.SIMON_BEST]    ?: 0,
                tfeBest          = p[PrefsKeys.TFE_BEST]      ?: 0,
                tfeMaxTile       = p[PrefsKeys.TFE_MAX_TILE]  ?: 0,
            )
        }

    suspend fun saveTheme(id: String)       = context.dataStore.edit { it[PrefsKeys.THEME_ID]      = id    }
    suspend fun savePipeProgress(lvl: Int, stars: Int) = context.dataStore.edit {
        it[PrefsKeys.PIPE_LEVEL] = lvl; it[PrefsKeys.PIPE_STARS] = stars
    }
    suspend fun saveLOBest(moves: Int)      = context.dataStore.edit { it[PrefsKeys.LO_BEST_MOVES]  = moves }
    suspend fun saveSudokuWins(wins: Int)   = context.dataStore.edit { it[PrefsKeys.SUDOKU_WINS]    = wins  }
    suspend fun saveSoundEnabled(on: Boolean) = context.dataStore.edit { it[PrefsKeys.SOUND_ENABLED] = on   }
    suspend fun saveAchievements(json: String) = context.dataStore.edit { it[PrefsKeys.ACHIEVEMENTS] = json }
    suspend fun saveEKWins(wins: Int)       = context.dataStore.edit { it[PrefsKeys.EK_WINS]        = wins  }
    suspend fun saveSimonBest(best: Int)    = context.dataStore.edit { it[PrefsKeys.SIMON_BEST]     = best  }
    suspend fun saveTFEBest(best: Int)      = context.dataStore.edit { it[PrefsKeys.TFE_BEST]       = best  }
    suspend fun saveTFEMaxTile(tile: Int)   = context.dataStore.edit { it[PrefsKeys.TFE_MAX_TILE]   = tile  }
}

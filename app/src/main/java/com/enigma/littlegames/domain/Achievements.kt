package com.enigma.littlegames.domain

// ─────────────────────────────────────────────────────────────────────────────
// Phase 2 · Achievement system
// Achievements are unlocked in-memory and persisted as a JSON array of IDs
// via PreferencesRepository.  No Room DB dependency needed for this scope.
// ─────────────────────────────────────────────────────────────────────────────

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val hidden: Boolean = false,   // hidden achievements don't show until earned
)

// ── Master list of all achievements ──────────────────────────────────────────

object Achievements {

    // Lights Out
    val LO_FIRST_WIN    = Achievement("lo_first_win",    "First Light Out",   "Solve your first Lights Out puzzle",             "💡")
    val LO_PERFECT      = Achievement("lo_perfect",      "Perfect Darkness",  "Solve a puzzle in the minimum number of moves",  "✦")
    val LO_EXPERT       = Achievement("lo_expert",       "Dark Arts",         "Solve an Expert puzzle",                         "💀", hidden = true)
    val LO_SPEED        = Achievement("lo_speed",        "Speed of Light",    "Solve any puzzle in under 10 moves",             "⚡", hidden = true)
    val LO_NO_HINT      = Achievement("lo_no_hint",      "Blind Tactician",   "Solve a Hard or Expert puzzle without hints",    "🙈", hidden = true)

    // Pipe Flow
    val PIPE_FIRST_WIN  = Achievement("pipe_first_win",  "Sealed Circuit",    "Complete your first Pipe Flow level",            "🔧")
    val PIPE_3STARS     = Achievement("pipe_3stars",     "Perfect Flow",      "Earn 3 stars on any level",                     "⭐")
    val PIPE_LEVEL10    = Achievement("pipe_level10",    "Pressure Master",   "Reach campaign level 10",                       "💧")
    val PIPE_LEVEL24    = Achievement("pipe_level24",    "Grand Engineer",    "Complete the full 24-level campaign",            "🏗️", hidden = true)
    val PIPE_NO_SOLVE   = Achievement("pipe_no_solve",   "Pure Instinct",     "Complete 5 levels without using Auto-Solve",     "🧠", hidden = true)

    // Killer Sudoku
    val SK_FIRST_WIN    = Achievement("sk_first_win",    "Cage Breaker",      "Complete your first Killer Sudoku puzzle",       "🔢")
    val SK_NO_ERROR     = Achievement("sk_no_error",     "Flawless Logic",    "Finish a puzzle with zero errors",              "🎯")
    val SK_EXPERT       = Achievement("sk_expert",       "Sudoku Overlord",   "Finish an Expert puzzle",                       "💀", hidden = true)
    val SK_FIVE_WINS    = Achievement("sk_five_wins",    "Cage Connoisseur",  "Solve 5 puzzles total",                         "🏆")
    val SK_SPEED_RUN    = Achievement("sk_speed_run",    "Lightning Mind",    "Finish a Medium puzzle in under 3 minutes",     "⏱️", hidden = true)

    // Hub / meta
    val ALL_GAMES       = Achievement("all_games",       "Triple Threat",     "Win at least one puzzle in every game",         "🎮", hidden = true)
    val THEME_EXPLORER  = Achievement("theme_explorer",  "Chromatic",         "Try all 5 themes",                              "🎨", hidden = true)

    val all = listOf(
        LO_FIRST_WIN, LO_PERFECT, LO_EXPERT, LO_SPEED, LO_NO_HINT,
        PIPE_FIRST_WIN, PIPE_3STARS, PIPE_LEVEL10, PIPE_LEVEL24, PIPE_NO_SOLVE,
        SK_FIRST_WIN, SK_NO_ERROR, SK_EXPERT, SK_FIVE_WINS, SK_SPEED_RUN,
        ALL_GAMES, THEME_EXPLORER,
    )

    fun byId(id: String): Achievement? = all.firstOrNull { it.id == id }
}

// ── In-memory tracker (owned by HubViewModel) ─────────────────────────────────

class AchievementTracker(
    initialUnlocked: Set<String> = emptySet(),
    private val onUnlock: (Achievement) -> Unit,
) {
    private val _unlocked = initialUnlocked.toMutableSet()
    val unlockedIds: Set<String> get() = _unlocked

    /** Returns true and fires callback if this achievement is newly unlocked. */
    fun unlock(a: Achievement): Boolean {
        if (_unlocked.contains(a.id)) return false
        _unlocked.add(a.id)
        onUnlock(a)
        return true
    }

    fun isUnlocked(id: String) = _unlocked.contains(id)

    /** Load from persisted JSON list  ["id1","id2",...] */
    fun loadFromJson(json: String) {
        _unlocked.clear()
        // Simple JSON parse without a library dependency
        json.trim().removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
            .forEach { _unlocked.add(it) }
    }

    fun toJson(): String = _unlocked.joinToString(",") { "\"$it\"" }.let { "[$it]" }
}

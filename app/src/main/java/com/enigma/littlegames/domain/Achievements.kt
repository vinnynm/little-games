package com.enigma.littlegames.domain

// ─────────────────────────────────────────────────────────────────────────────
// Phase 4a — Achievement system expanded to 30 achievements
// New games: Exploding Kittens (4), Simon Says (4), 2048 (4)
// Hub meta achievements updated to reflect 8 games
// ─────────────────────────────────────────────────────────────────────────────

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val hidden: Boolean = false,
)

object Achievements {

    // ── Lights Out (5) ────────────────────────────────────────────────────────
    val LO_FIRST_WIN = Achievement("lo_first_win",  "First Light Out",   "Solve your first Lights Out puzzle",                "💡")
    val LO_PERFECT   = Achievement("lo_perfect",    "Perfect Darkness",  "Solve a puzzle in the minimum number of moves",     "✦")
    val LO_EXPERT    = Achievement("lo_expert",     "Dark Arts",         "Solve an Expert puzzle",                            "💀", hidden = true)
    val LO_SPEED     = Achievement("lo_speed",      "Speed of Light",    "Solve any puzzle in under 10 moves",                "⚡", hidden = true)
    val LO_NO_HINT   = Achievement("lo_no_hint",    "Blind Tactician",   "Solve a Hard or Expert puzzle without hints",       "🙈", hidden = true)

    // ── Pipe Flow (5) ─────────────────────────────────────────────────────────
    val PIPE_FIRST_WIN = Achievement("pipe_first_win", "Sealed Circuit",  "Complete your first Pipe Flow level",              "🔧")
    val PIPE_3STARS    = Achievement("pipe_3stars",    "Perfect Flow",    "Earn 3 stars on any level",                        "⭐")
    val PIPE_LEVEL10   = Achievement("pipe_level10",   "Pressure Master", "Reach campaign level 10",                          "💧")
    val PIPE_LEVEL24   = Achievement("pipe_level24",   "Grand Engineer",  "Complete the full 24-level campaign",              "🏗️", hidden = true)
    val PIPE_NO_SOLVE  = Achievement("pipe_no_solve",  "Pure Instinct",   "Complete 5 levels without using Auto-Solve",       "🧠", hidden = true)

    // ── Killer Sudoku + Classic Sudoku + Kakuro (5) ───────────────────────────
    val SK_FIRST_WIN = Achievement("sk_first_win",  "Cage Breaker",    "Complete your first Killer Sudoku puzzle",           "🔢")
    val SK_NO_ERROR  = Achievement("sk_no_error",   "Flawless Logic",  "Finish a puzzle with zero errors",                   "🎯")
    val SK_EXPERT    = Achievement("sk_expert",     "Sudoku Overlord", "Finish an Expert puzzle",                            "💀", hidden = true)
    val SK_FIVE_WINS = Achievement("sk_five_wins",  "Cage Connoisseur","Solve 5 puzzles across Sudoku games",                "🏆")
    val SK_SPEED_RUN = Achievement("sk_speed_run",  "Lightning Mind",  "Finish a Medium puzzle in under 3 minutes",          "⏱️", hidden = true)

    // ── Exploding Kittens (4) ─────────────────────────────────────────────────
    val EK_FIRST_WIN  = Achievement("ek_first_win",  "Survived!",       "Win your first Exploding Kittens game",             "😸")
    val EK_DEFUSE     = Achievement("ek_defuse",     "Bomb Disposal",   "Defuse an Exploding Kitten",                        "🛡️")
    val EK_BEAT_HARD  = Achievement("ek_beat_hard",  "Cat Whisperer",   "Beat the Hard AI",                                  "😼", hidden = true)
    val EK_FIVE_WINS  = Achievement("ek_five_wins",  "Nine Lives",      "Win 5 Exploding Kittens games",                     "🐱", hidden = true)

    // ── Simon Says (4) ────────────────────────────────────────────────────────
    val SIMON_FIRST   = Achievement("simon_first",   "Memory Spark",    "Complete your first Simon Says round",              "🧠")
    val SIMON_10      = Achievement("simon_10",      "Sharp Mind",      "Reach a sequence of 10",                            "🔟")
    val SIMON_20      = Achievement("simon_20",      "Photographic",    "Reach a sequence of 20",                            "📸", hidden = true)
    val SIMON_SPEED   = Achievement("simon_speed",   "Lightning Recall","Reach 15 in Speed mode",                            "⚡", hidden = true)

    // ── 2048 (4) ─────────────────────────────────────────────────────────────
    val TFE_FIRST     = Achievement("tfe_first",     "First Merge",     "Play your first game of 2048",                      "2️⃣")
    val TFE_256       = Achievement("tfe_256",       "Getting There",   "Reach the 256 tile",                                "🔢")
    val TFE_1024      = Achievement("tfe_1024",      "Thousand!",       "Reach the 1024 tile",                               "🔥", hidden = true)
    val TFE_2048      = Achievement("tfe_2048",      "2048 Master",     "Reach the 2048 tile",                               "🏆", hidden = true)

    // ── Hub / meta (3) ────────────────────────────────────────────────────────
    val ALL_GAMES       = Achievement("all_games",       "Full House",      "Win at least one game in every hub game",        "🎮", hidden = true)
    val THEME_EXPLORER  = Achievement("theme_explorer",  "Chromatic",       "Try all 5 themes",                              "🎨", hidden = true)
    val COMPLETIONIST   = Achievement("completionist",   "Completionist",   "Unlock 25 achievements",                        "💎", hidden = true)

    val all = listOf(
        LO_FIRST_WIN, LO_PERFECT, LO_EXPERT, LO_SPEED, LO_NO_HINT,
        PIPE_FIRST_WIN, PIPE_3STARS, PIPE_LEVEL10, PIPE_LEVEL24, PIPE_NO_SOLVE,
        SK_FIRST_WIN, SK_NO_ERROR, SK_EXPERT, SK_FIVE_WINS, SK_SPEED_RUN,
        EK_FIRST_WIN, EK_DEFUSE, EK_BEAT_HARD, EK_FIVE_WINS,
        SIMON_FIRST, SIMON_10, SIMON_20, SIMON_SPEED,
        TFE_FIRST, TFE_256, TFE_1024, TFE_2048,
        ALL_GAMES, THEME_EXPLORER, COMPLETIONIST,
    )

    val total = all.size   // 30

    fun byId(id: String): Achievement? = all.firstOrNull { it.id == id }
}

// ── In-memory tracker ─────────────────────────────────────────────────────────

class AchievementTracker(
    initialUnlocked: Set<String> = emptySet(),
    private val onUnlock: (Achievement) -> Unit,
) {
    private val _unlocked = initialUnlocked.toMutableSet()
    val unlockedIds: Set<String> get() = _unlocked

    fun unlock(a: Achievement): Boolean {
        if (_unlocked.contains(a.id)) return false
        _unlocked.add(a.id)
        onUnlock(a)
        return true
    }

    fun isUnlocked(id: String) = _unlocked.contains(id)

    fun loadFromJson(json: String) {
        _unlocked.clear()
        json.trim().removePrefix("[").removeSuffix("]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }
            .forEach { _unlocked.add(it) }
    }

    fun toJson(): String = _unlocked.joinToString(",") { "\"$it\"" }.let { "[$it]" }
}

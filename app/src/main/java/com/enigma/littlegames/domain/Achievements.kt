package com.enigma.littlegames.domain

// ─────────────────────────────────────────────────────────────────────────────
// Phase 4d (FINAL) · Achievements — 41 total
// Adds Flow Free (2), Wordle (3), Minesweeper (3) groups.
// ─────────────────────────────────────────────────────────────────────────────

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val hidden: Boolean = false,
)

object Achievements {

    // ── Lights Out ────────────────────────────────────────────────────────────
    val LO_FIRST_WIN   = Achievement("lo_first_win",   "First Light Out",     "Solve your first Lights Out puzzle",             "💡")
    val LO_PERFECT     = Achievement("lo_perfect",     "Perfect Darkness",    "Solve in the minimum number of moves",           "✦")
    val LO_EXPERT      = Achievement("lo_expert",      "Dark Arts",           "Solve an Expert puzzle",                         "💀", hidden = true)
    val LO_SPEED       = Achievement("lo_speed",       "Speed of Light",      "Solve any puzzle in under 10 moves",             "⚡", hidden = true)
    val LO_NO_HINT     = Achievement("lo_no_hint",     "Blind Tactician",     "Solve Hard or Expert without hints",             "🙈", hidden = true)

    // ── Pipe Flow ─────────────────────────────────────────────────────────────
    val PIPE_FIRST_WIN = Achievement("pipe_first_win", "Sealed Circuit",      "Complete your first Pipe Flow level",            "🔧")
    val PIPE_3STARS    = Achievement("pipe_3stars",    "Perfect Flow",        "Earn 3 stars on any level",                     "⭐")
    val PIPE_LEVEL10   = Achievement("pipe_level10",   "Pressure Master",     "Reach campaign level 10",                       "💧")
    val PIPE_LEVEL24   = Achievement("pipe_level24",   "Grand Engineer",      "Complete the full 24-level campaign",            "🏗️", hidden = true)
    val PIPE_NO_SOLVE  = Achievement("pipe_no_solve",  "Pure Instinct",       "Complete 5 levels without Auto-Solve",           "🧠", hidden = true)

    // ── Sudoku family ─────────────────────────────────────────────────────────
    val SK_FIRST_WIN   = Achievement("sk_first_win",   "Cage Breaker",        "Complete your first Sudoku puzzle",              "🔢")
    val SK_NO_ERROR    = Achievement("sk_no_error",    "Flawless Logic",      "Finish a puzzle with zero errors",              "🎯")
    val SK_EXPERT      = Achievement("sk_expert",      "Sudoku Overlord",     "Finish an Expert puzzle",                       "💀", hidden = true)
    val SK_FIVE_WINS   = Achievement("sk_five_wins",   "Cage Connoisseur",    "Solve 5 Sudoku puzzles",                        "🏆")
    val SK_SPEED_RUN   = Achievement("sk_speed_run",   "Lightning Mind",      "Finish a Medium puzzle in under 3 minutes",     "⏱️", hidden = true)

    // ── Simon Says ────────────────────────────────────────────────────────────
    val SIMON_FIRST    = Achievement("simon_first",    "Pay Attention",       "Complete your first Simon Says sequence",        "👁")
    val SIMON_TEN      = Achievement("simon_ten",      "Memory Champ",        "Reach level 10 in Simon Says",                  "🧠", hidden = true)
    val SIMON_MASTER   = Achievement("simon_master",   "Photographic Memory", "Reach level 25 in Simon Says",                  "🌟", hidden = true)

    // ── 2048 ──────────────────────────────────────────────────────────────────
    val TFE_FIRST      = Achievement("tfe_first",      "Tile Pusher",         "Play your first game of 2048",                  "🀄")
    val TFE_512        = Achievement("tfe_512",        "Halfway There",       "Reach the 512 tile",                            "🔥", hidden = true)
    val TFE_2048       = Achievement("tfe_2048",       "Power of 11",         "Reach the 2048 tile!",                          "👑", hidden = true)

    // ── Sliding Puzzle ────────────────────────────────────────────────────────
    val SLIDE_FIRST    = Achievement("slide_first",    "First Shuffle",       "Solve your first Sliding Puzzle",               "🧩")
    val SLIDE_FOUR     = Achievement("slide_four",     "Patience",            "Solve a 4×4 puzzle",                            "🔲", hidden = true)
    val SLIDE_EXPERT   = Achievement("slide_expert",   "5×5 Conqueror",       "Solve the brutal 5×5 puzzle",                   "💀", hidden = true)

    // ── Nonogram ─────────────────────────────────────────────────────────────
    val NONO_FIRST     = Achievement("nono_first",     "Pixel Pioneer",       "Complete your first Nonogram",                  "🖼️")
    val NONO_PERFECT   = Achievement("nono_perfect",   "Clean Canvas",        "Finish any Nonogram with zero wrong marks",     "✨", hidden = true)
    val NONO_EXPERT    = Achievement("nono_expert",    "20×20 Vision",        "Complete a 20×20 Expert Nonogram",              "💀", hidden = true)
    val NONO_SPEED     = Achievement("nono_speed",     "Fast Brush",          "Complete a 10×10 in under 5 minutes",           "⚡", hidden = true)

    // ── Sokoban ───────────────────────────────────────────────────────────────
    val SOKO_FIRST     = Achievement("soko_first",     "Box Mover",           "Complete your first Sokoban level",             "📦")
    val SOKO_TEN       = Achievement("soko_ten",       "Warehouse Worker",    "Complete 10 Sokoban levels",                    "🏭", hidden = true)
    val SOKO_MASTER    = Achievement("soko_master",    "Sokoban Master",      "Complete all 30 levels",                        "🥇", hidden = true)

    // ── Flow Free ─────────────────────────────────────────────────────────────
    val FLOW_FIRST     = Achievement("flow_first",     "Colour Connector",    "Solve your first Flow Free puzzle",             "🌈")
    val FLOW_EXPERT    = Achievement("flow_expert",    "Flow Master",         "Solve an Expert 10×10 Flow puzzle",             "🔗", hidden = true)

    // ── Wordle ────────────────────────────────────────────────────────────────
    val WORD_FIRST     = Achievement("word_first",     "Word Finder",         "Guess your first Wordle word",                  "📝")
    val WORD_ACE       = Achievement("word_ace",       "Hole in One",         "Guess the word on the first try",               "🎯", hidden = true)
    val WORD_HARD      = Achievement("word_hard",      "Linguist",            "Win a game on Hard Mode",                       "💀", hidden = true)

    // ── Minesweeper ───────────────────────────────────────────────────────────
    val MINE_FIRST     = Achievement("mine_first",     "Safe Steps",          "Clear your first Minesweeper board",            "💎")
    val MINE_EXPERT    = Achievement("mine_expert",    "Defusal Expert",      "Clear an Expert hex grid",                      "💣", hidden = true)
    val MINE_SPEED     = Achievement("mine_speed",     "Speed Sweeper",       "Clear any board in under 60 seconds",           "⚡", hidden = true)

    // ── Hub / meta ────────────────────────────────────────────────────────────
    val ALL_GAMES      = Achievement("all_games",      "Decathlon",           "Win at least once in all 10 game families",     "🎮", hidden = true)
    val THEME_EXPLORER = Achievement("theme_explorer", "Chromatic",           "Try all 5 themes",                              "🎨", hidden = true)

    val all = listOf(
        LO_FIRST_WIN, LO_PERFECT, LO_EXPERT, LO_SPEED, LO_NO_HINT,
        PIPE_FIRST_WIN, PIPE_3STARS, PIPE_LEVEL10, PIPE_LEVEL24, PIPE_NO_SOLVE,
        SK_FIRST_WIN, SK_NO_ERROR, SK_EXPERT, SK_FIVE_WINS, SK_SPEED_RUN,
        SIMON_FIRST, SIMON_TEN, SIMON_MASTER,
        TFE_FIRST, TFE_512, TFE_2048,
        SLIDE_FIRST, SLIDE_FOUR, SLIDE_EXPERT,
        NONO_FIRST, NONO_PERFECT, NONO_EXPERT, NONO_SPEED,
        SOKO_FIRST, SOKO_TEN, SOKO_MASTER,
        FLOW_FIRST, FLOW_EXPERT,
        WORD_FIRST, WORD_ACE, WORD_HARD,
        MINE_FIRST, MINE_EXPERT, MINE_SPEED,
        ALL_GAMES, THEME_EXPLORER,
    )

    fun byId(id: String): Achievement? = all.firstOrNull { it.id == id }
}

class AchievementTracker(
    initialUnlocked: Set<String> = emptySet(),
    private val onUnlock: (Achievement) -> Unit,
) {
    private val _unlocked = initialUnlocked.toMutableSet()
    val unlockedIds: Set<String> get() = _unlocked

    fun unlock(a: Achievement): Boolean {
        if (_unlocked.contains(a.id)) return false
        _unlocked.add(a.id); onUnlock(a); return true
    }

    fun isUnlocked(id: String) = _unlocked.contains(id)

    fun loadFromJson(json: String) {
        _unlocked.clear()
        json.trim().removePrefix("[").removeSuffix("]")
            .split(",").map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotEmpty() }.forEach { _unlocked.add(it) }
    }

    fun toJson(): String = _unlocked.joinToString(",") { "\"$it\"" }.let { "[$it]" }
}

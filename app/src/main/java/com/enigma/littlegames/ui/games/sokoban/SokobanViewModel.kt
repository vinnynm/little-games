package com.enigma.littlegames.ui.games.sokoban

// ─────────────────────────────────────────────────────────────────────────────
// Bug fix (audit #2 — critical):
// loadLevel() used to call itself recursively (on the call stack, not via a
// loop) when parseLevel() threw, which:
//   • risked a StackOverflowError on a run of consecutive bad levels, and
//   • did nothing at all if the LAST level was the bad one — isLoading stayed
//     true forever with no recovery path, softlocking the screen.
// Fixed to use a bounded iterative scan across all levels, and to fall back
// to a small guaranteed-valid hardcoded level if literally every level in
// SOKOBAN_LEVELS fails to parse.
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.*

enum class SokoDir(val dr: Int, val dc: Int, val label: String) {
    UP   (-1,  0, "↑"),
    DOWN ( 1,  0, "↓"),
    LEFT ( 0, -1, "←"),
    RIGHT( 0,  1, "→"),
}

data class SokobanBoard(
    val playerRow: Int,
    val playerCol: Int,
    val playerDir: SokoDir = SokoDir.DOWN,
    val boxes: Set<Pair<Int, Int>>,
    val targets: Set<Pair<Int, Int>>,
    val walls: Set<Pair<Int, Int>>,
    val floor: Set<Pair<Int, Int>>,
    val width: Int,
    val height: Int,
    val moveCount: Int,
    val pushCount: Int,
    val history: List<Snapshot>,
    val isDeadlocked: Boolean = false,
) {
    val isComplete: Boolean get() = targets.isNotEmpty() && boxes.containsAll(targets)
}

data class Snapshot(
    val playerRow: Int,
    val playerCol: Int,
    val playerDir: SokoDir,
    val boxes: Set<Pair<Int, Int>>,
    val moveCount: Int,
    val pushCount: Int,
)

enum class StarRating { NONE, ONE, TWO, THREE }

data class LevelProgress(
    val bestMoves: Int? = null,
    val bestPushes: Int? = null,
    val completed: Boolean = false,
    val stars: StarRating = StarRating.NONE,
)

data class SokobanUiState(
    val board: SokobanBoard?               = null,
    val level: Int                         = 1,
    val totalLevels: Int                   = SOKOBAN_LEVELS.size,
    val isLoading: Boolean                 = true,
    val isComplete: Boolean                = false,
    val isDeadlocked: Boolean              = false,
    val progress: Map<Int, LevelProgress>  = emptyMap(),
    val showLevelSelect: Boolean           = false,
    val elapsedSeconds: Int                = 0,
    val levelName: String                  = "",
)

// ── Enhanced XSB Parser ──────────────────────────────────────────────────────

fun parseLevel(levelDef: LevelDef): SokobanBoard {
    val lines = levelDef.xsb.lines()
        .map { it.trimEnd() }
        .filter { it.isNotBlank() }

    if (lines.isEmpty()) error("Empty level")

    val minLeading = lines
        .filter { it.any { c -> c != ' ' } }
        .minOfOrNull { it.indexOfFirst { c -> c != ' ' }.let { if (it == -1) 0 else it } } ?: 0

    val strippedLines = lines.map { it.drop(minLeading) }
    val width  = strippedLines.maxOf { it.length }
    val height = strippedLines.size

    val walls   = mutableSetOf<Pair<Int, Int>>()
    val boxes   = mutableSetOf<Pair<Int, Int>>()
    val targets = mutableSetOf<Pair<Int, Int>>()
    var playerRow = 0
    var playerCol = 0

    strippedLines.forEachIndexed { r, line ->
        line.forEachIndexed { c, ch ->
            when (ch) {
                '#'  -> walls.add(r to c)
                '@'  -> { playerRow = r; playerCol = c }
                '+'  -> { playerRow = r; playerCol = c; targets.add(r to c) }
                '$'  -> boxes.add(r to c)
                '*'  -> { boxes.add(r to c); targets.add(r to c) }
                '.'  -> targets.add(r to c)
            }
        }
    }

    require(boxes.size == targets.size) {
        "Invalid level: ${boxes.size} boxes but ${targets.size} targets"
    }

    val floor = floodFillFloor(playerRow, playerCol, walls, width, height)

    return SokobanBoard(
        playerRow = playerRow,
        playerCol = playerCol,
        playerDir = SokoDir.DOWN,
        boxes = boxes,
        targets = targets,
        walls = walls,
        floor = floor,
        width = width,
        height = height,
        moveCount = 0,
        pushCount = 0,
        history = emptyList(),
    )
}

/** Tiny, hand-verified level used only if every entry in SOKOBAN_LEVELS
 *  somehow fails to parse — guarantees loadLevel() can never softlock. */
private val FALLBACK_LEVEL = LevelDef(
    xsb = """
        #####
        #@$.#
        #####
    """.trimIndent(),
    par = 1,
    par2 = 2,
    name = "Fallback",
)

private fun floodFillFloor(
    startR: Int, startC: Int,
    walls: Set<Pair<Int, Int>>,
    width: Int, height: Int
): Set<Pair<Int, Int>> {
    val floor = mutableSetOf<Pair<Int, Int>>()
    val queue = ArrayDeque<Pair<Int, Int>>()
    queue.add(startR to startC)
    floor.add(startR to startC)

    while (queue.isNotEmpty()) {
        val (r, c) = queue.removeFirst()
        for ((dr, dc) in listOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)) {
            val nr = r + dr
            val nc = c + dc
            val pos = nr to nc
            if (nr in 0 until height && nc in 0 until width &&
                pos !in walls && pos !in floor) {
                floor.add(pos)
                queue.add(pos)
            }
        }
    }
    return floor
}

// ── Deadlock Detection ───────────────────────────────────────────────────────

fun detectDeadlock(board: SokobanBoard): Boolean {
    for (box in board.boxes) {
        if (box in board.targets) continue

        val (r, c) = box
        val wallUp    = (r - 1 to c) in board.walls
        val wallDown  = (r + 1 to c) in board.walls
        val wallLeft  = (r to c - 1) in board.walls
        val wallRight = (r to c + 1) in board.walls

        // Corner deadlock
        if ((wallUp && wallLeft) || (wallUp && wallRight) ||
            (wallDown && wallLeft) || (wallDown && wallRight)) {
            return true
        }
    }

    // Wall-line deadlock
    for (box in board.boxes) {
        if (box in board.targets) continue

        val (r, c) = box

        if ((r - 1 to c) in board.walls || (r + 1 to c) in board.walls) {
            val wallDir = if ((r - 1 to c) in board.walls) -1 else 1
            if (isWallLineDeadlock(board, r, c, 0, wallDir)) return true
        }

        if ((r to c - 1) in board.walls || (r to c + 1) in board.walls) {
            val wallDir = if ((r to c - 1) in board.walls) -1 else 1
            if (isWallLineDeadlock(board, r, c, wallDir, 0)) return true
        }
    }

    return false
}

private fun isWallLineDeadlock(
    board: SokobanBoard, r: Int, c: Int, dr: Int, dc: Int
): Boolean {
    var hasTarget = false
    var allBlocked = true

    // Scan positive direction
    var cr = r + dr
    var cc = c + dc
    while (cr in 0 until board.height && cc in 0 until board.width) {
        val pos = cr to cc
        if (pos in board.walls) break
        if (pos in board.targets) hasTarget = true
        val perpendicularWall = (cr + (if (dc != 0) 0 else dr) to cc + (if (dr != 0) 0 else dc)) in board.walls
        if (pos !in board.boxes && !perpendicularWall) {
            allBlocked = false
            break
        }
        cr += dr
        cc += dc
    }

    // Scan negative direction
    cr = r - dr
    cc = c - dc
    while (cr in 0 until board.height && cc in 0 until board.width) {
        val pos = cr to cc
        if (pos in board.walls) break
        if (pos in board.targets) hasTarget = true
        val perpendicularWall = (cr + (if (dc != 0) 0 else dr) to cc + (if (dr != 0) 0 else dc)) in board.walls
        if (pos !in board.boxes && !perpendicularWall) {
            allBlocked = false
            break
        }
        cr -= dr
        cc -= dc
    }

    return allBlocked && !hasTarget
}

// ── Move Engine ──────────────────────────────────────────────────────────────

fun applyMove(board: SokobanBoard, dir: SokoDir): SokobanBoard {
    val nr = board.playerRow + dir.dr
    val nc = board.playerCol + dir.dc
    val dest = nr to nc

    if (nr < 0 || nr >= board.height || nc < 0 || nc >= board.width) return board
    if (dest in board.walls) return board

    val snapshot = Snapshot(
        board.playerRow, board.playerCol, board.playerDir,
        board.boxes, board.moveCount, board.pushCount
    )

    if (dest in board.boxes) {
        val br = nr + dir.dr
        val bc = nc + dir.dc
        val boxDest = br to bc

        if (br < 0 || br >= board.height || bc < 0 || bc >= board.width) return board
        if (boxDest in board.walls || boxDest in board.boxes) return board

        val newBoxes = board.boxes - dest + boxDest
        val newBoard = board.copy(
            playerRow = nr, playerCol = nc, playerDir = dir,
            boxes = newBoxes,
            moveCount = board.moveCount + 1,
            pushCount = board.pushCount + 1,
            history = board.history + snapshot,
        )
        val deadlocked = detectDeadlock(newBoard)
        return newBoard.copy(isDeadlocked = deadlocked)
    }

    return board.copy(
        playerRow = nr, playerCol = nc, playerDir = dir,
        moveCount = board.moveCount + 1,
        history = board.history + snapshot,
        isDeadlocked = false,
    )
}

fun undoMove(board: SokobanBoard): SokobanBoard {
    val snap = board.history.lastOrNull() ?: return board
    return board.copy(
        playerRow = snap.playerRow,
        playerCol = snap.playerCol,
        playerDir = snap.playerDir,
        boxes = snap.boxes,
        moveCount = snap.moveCount,
        pushCount = snap.pushCount,
        history = board.history.dropLast(1),
        isDeadlocked = false,
    )
}

// ── Star Calculation ─────────────────────────────────────────────────────────

fun calculateStars(moves: Int, levelDef: LevelDef): StarRating = when {
    moves <= levelDef.par  -> StarRating.THREE
    moves <= levelDef.par2 -> StarRating.TWO
    else                   -> StarRating.ONE
}

// ── ViewModel ────────────────────────────────────────────────────────────────

class SokobanViewModel : ViewModel() {
    private val _state = MutableStateFlow(SokobanUiState())
    val state: StateFlow<SokobanUiState> = _state.asStateFlow()

    private var originalBoard: SokobanBoard? = null

    init { loadLevel(1) }

    /**
     * Bug fix (audit #2): previously called itself recursively on parse
     * failure, which could stack-overflow on a run of bad levels and would
     * softlock entirely if the LAST level in the list was the bad one (the
     * old `if (lvl < SOKOBAN_LEVELS.size)` guard did nothing for that case).
     *
     * Now scans forward through the level list with a bounded loop (at most
     * SOKOBAN_LEVELS.size attempts, so it can never loop forever even if
     * every level is broken), and falls back to a small hardcoded
     * known-good level as an absolute last resort so the screen can never
     * get stuck on the loading spinner.
     */
    fun loadLevel(lvl: Int) {
        val startIdx = (lvl - 1).coerceIn(0, SOKOBAN_LEVELS.size - 1)
        var idx = startIdx
        var attempts = 0

        while (attempts < SOKOBAN_LEVELS.size) {
            val levelDef = SOKOBAN_LEVELS[idx]
            val board = try {
                parseLevel(levelDef)
            } catch (e: Exception) {
                attempts++
                idx = (idx + 1) % SOKOBAN_LEVELS.size
                null
            }
            if (board != null) {
                originalBoard = board
                _state.update {
                    it.copy(
                        board = board,
                        level = idx + 1,
                        isLoading = false,
                        isComplete = false,
                        isDeadlocked = false,
                        elapsedSeconds = 0,
                        levelName = levelDef.name,
                    )
                }
                return
            }
        }

        // Every level in the pack failed to parse — fall back to a
        // guaranteed-valid hardcoded level instead of leaving isLoading=true.
        val fallbackBoard = parseLevel(FALLBACK_LEVEL)
        originalBoard = fallbackBoard
        _state.update {
            it.copy(
                board = fallbackBoard,
                level = startIdx + 1,
                isLoading = false,
                isComplete = false,
                isDeadlocked = false,
                elapsedSeconds = 0,
                levelName = FALLBACK_LEVEL.name,
            )
        }
    }

    fun tickTimer() {
        val s = _state.value
        if (!s.isComplete && !s.isLoading && s.board != null) {
            _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
        }
    }

    fun move(dir: SokoDir) {
        val s = _state.value
        val b = s.board ?: return
        if (s.isComplete) return

        val newBoard = applyMove(b, dir)
        if (newBoard === b) return

        val complete = newBoard.isComplete
        if (complete) {
            val levelDef = SOKOBAN_LEVELS[s.level - 1]
            val stars = calculateStars(newBoard.moveCount, levelDef)
            val newProgress = s.progress.toMutableMap()
            val existing = newProgress[s.level]
            val updateProgress = LevelProgress(
                bestMoves = minOf(existing?.bestMoves ?: Int.MAX_VALUE, newBoard.moveCount),
                bestPushes = minOf(existing?.bestPushes ?: Int.MAX_VALUE, newBoard.pushCount),
                completed = true,
                stars = maxOf(existing?.stars ?: StarRating.NONE, stars),
            )
            newProgress[s.level] = updateProgress
            _state.update {
                it.copy(board = newBoard, isComplete = true, progress = newProgress)
            }
        } else {
            _state.update { it.copy(board = newBoard, isDeadlocked = newBoard.isDeadlocked) }
        }
    }

    fun undo() {
        val b = _state.value.board ?: return
        _state.update { it.copy(board = undoMove(b), isComplete = false, isDeadlocked = false) }
    }

    fun restart() {
        val orig = originalBoard ?: return
        _state.update { it.copy(board = orig, isComplete = false, isDeadlocked = false, elapsedSeconds = 0) }
    }

    fun nextLevel() {
        val next = (_state.value.level + 1).coerceAtMost(SOKOBAN_LEVELS.size)
        loadLevel(next)
    }

    fun prevLevel() {
        val prev = (_state.value.level - 1).coerceAtLeast(1)
        loadLevel(prev)
    }

    fun showLevelSelect() { _state.update { it.copy(showLevelSelect = true) } }
    fun hideLevelSelect() { _state.update { it.copy(showLevelSelect = false) } }
}

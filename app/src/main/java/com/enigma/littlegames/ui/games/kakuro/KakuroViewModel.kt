package com.enigma.littlegames.ui.games.kakuro

// ─────────────────────────────────────────────────────────────────────────────
// KakuroViewModel — procedural random Kakuro, integrated with Enigma Game Hub.
//
// Bug fixes applied (audit report):
//   #3 (high) — inputDigit() computed `wasErr` respecting `s.showMistakes`
//       but computed "is this now a conflict" with a hard-coded `true`. With
//       mistake-highlighting turned off, `wasErr` was always false, so every
//       keystroke on an already-conflicting cell counted as a brand-new
//       error, wildly inflating errorCount. Both checks now consistently use
//       `s.showMistakes`.
//   #4 (medium) — `countErrors(puzzle, newDigits, true)` was computed and
//       immediately discarded every keystroke (KakuroUiState has no `errors`
//       field to store it in). Removed the dead computation entirely.
//
// Changes from the standalone app:
//   • Uses KakuroGridSize / KakuroDifficulty (hub-package enums)
//   • State is now a single StateFlow<KakuroUiState> (hub pattern)
//   • Generation runs on Dispatchers.Default via viewModelScope
//   • Win-check validates clues, not the stored solution, so alternate
//     valid completions are accepted
//   • Hint/reveal reads from KakuroPuzzleData.solution
// ─────────────────────────────────────────────────────────────────────────────

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.random.Random

/** One undo-able edit. */
private data class UndoEntry(
    val cell: KPos,
    val previousDigit: Int?,
    val previousNotes: Set<Int>,
)

data class KakuroUiState(
    val puzzle: KakuroPuzzleData?              = null,
    val playerDigits: Map<KPos, Int>           = emptyMap(),
    val notes: Map<KPos, Set<Int>>             = emptyMap(),
    val selectedCell: KPos?                    = null,
    val notesMode: Boolean                     = false,
    val showMistakes: Boolean                  = true,
    val elapsedSecs: Int                       = 0,
    val hintsUsed: Int                         = 0,
    val isSolved: Boolean                      = false,
    val generating: Boolean                    = true,
    val gridSize: KakuroGridSize               = KakuroGridSize.SMALL,
    val difficulty: KakuroDifficulty           = KakuroDifficulty.MEDIUM,
    val canUndo: Boolean                       = false,
    val errorCount: Int                        = 0,
)

class KakuroViewModel : ViewModel() {

    private val _state = MutableStateFlow(KakuroUiState())
    val state: StateFlow<KakuroUiState> = _state.asStateFlow()

    private val undoStack = mutableListOf<UndoEntry>()
    private var timerJob: Job? = null

    init { newGame(KakuroGridSize.SMALL, KakuroDifficulty.MEDIUM) }

    // ── Game lifecycle ────────────────────────────────────────────────────────

    fun newGame(size: KakuroGridSize = _state.value.gridSize, diff: KakuroDifficulty = _state.value.difficulty) {
        timerJob?.cancel()
        undoStack.clear()
        _state.update { it.copy(
            generating   = true,
            gridSize     = size,
            difficulty   = diff,
            playerDigits = emptyMap(),
            notes        = emptyMap(),
            selectedCell = null,
            notesMode    = false,
            hintsUsed    = 0,
            isSolved     = false,
            elapsedSecs  = 0,
            canUndo      = false,
            errorCount   = 0,
        )}
        viewModelScope.launch(Dispatchers.Default) {
            val puzzle = KakuroGenerator.generate(
                size.innerRows, size.innerCols, diff.blackDensity, Random(System.nanoTime())
            )
            _state.update { it.copy(generating = false, puzzle = puzzle) }
        }
        startTimer()
    }

    fun resetProgress() {
        undoStack.clear()
        _state.update { it.copy(
            playerDigits = emptyMap(),
            notes        = emptyMap(),
            hintsUsed    = 0,
            isSolved     = false,
            elapsedSecs  = 0,
            canUndo      = false,
            errorCount   = 0,
        )}
        startTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                if (!_state.value.isSolved) _state.update { it.copy(elapsedSecs = it.elapsedSecs + 1) }
            }
        }
    }

    // ── Selection & input ─────────────────────────────────────────────────────

    fun selectCell(pos: KPos) {
        val puzzle = _state.value.puzzle ?: return
        if (puzzle.isWhite(pos.row, pos.col)) _state.update { it.copy(selectedCell = pos) }
    }

    fun toggleNotesMode() { _state.update { it.copy(notesMode = !it.notesMode) } }
    fun toggleShowMistakes() { _state.update { it.copy(showMistakes = !it.showMistakes) } }

    fun inputDigit(digit: Int) {
        val s      = _state.value
        val cell   = s.selectedCell ?: return
        val puzzle = s.puzzle ?: return
        if (s.isSolved) return

        if (s.notesMode) {
            if (s.playerDigits.containsKey(cell)) return
            recordUndo(s, cell)
            val current = s.notes[cell] ?: emptySet()
            _state.update { it.copy(
                notes   = it.notes + (cell to if (digit in current) current - digit else current + digit),
                canUndo = true,
            )}
        } else {
            recordUndo(s, cell)
            val newDigits = if (s.playerDigits[cell] == digit) {
                s.playerDigits - cell
            } else {
                s.playerDigits + (cell to digit)
            }
            val newNotes = s.notes - cell

            // Bug fix (audit #3): both the "was it an error before" and
            // "is it an error now" checks must use the SAME showMistakes
            // flag. The old code hard-coded `true` for the "now" check while
            // `wasErr` respected s.showMistakes — with mistake-highlighting
            // off, wasErr was always false, so every keystroke on an already
            // conflicting cell (even re-entering the same digit) counted as
            // a brand-new error and inflated errorCount indefinitely.
            val wasErr   = cellHasConflict(s.puzzle, s.playerDigits, s.showMistakes, cell)
            val isErrNow = cellHasConflict(puzzle, newDigits, s.showMistakes, cell)
            val newErrCnt = if (digit != 0 && isErrNow && !wasErr) s.errorCount + 1 else s.errorCount

            // Bug fix (audit #4): the old code also computed
            // `countErrors(puzzle, newDigits, true)` here and threw the
            // result away every keystroke (KakuroUiState has no field to
            // store it in, and KakuroWhiteCell recomputes conflicts live
            // per-cell instead). Removed the dead computation.

            _state.update { it.copy(
                playerDigits = newDigits,
                notes        = newNotes,
                canUndo      = true,
                errorCount   = newErrCnt,
                isSolved     = checkWin(puzzle, newDigits),
            )}
        }
    }

    fun eraseSelected() {
        val s    = _state.value
        val cell = s.selectedCell ?: return
        if (s.isSolved) return
        if (!s.playerDigits.containsKey(cell) && !s.notes.containsKey(cell)) return
        recordUndo(s, cell)
        _state.update { it.copy(
            playerDigits = it.playerDigits - cell,
            notes        = it.notes - cell,
            canUndo      = true,
        )}
    }

    fun undo() {
        val entry = undoStack.removeLastOrNull() ?: return
        _state.update { s ->
            val newDigits = if (entry.previousDigit != null)
                s.playerDigits + (entry.cell to entry.previousDigit)
            else s.playerDigits - entry.cell
            val newNotes  = if (entry.previousNotes.isNotEmpty())
                s.notes + (entry.cell to entry.previousNotes)
            else s.notes - entry.cell
            s.copy(
                playerDigits = newDigits,
                notes        = newNotes,
                isSolved     = false,
                canUndo      = undoStack.isNotEmpty(),
            )
        }
    }

    fun useHint() {
        val s      = _state.value
        val puzzle = s.puzzle ?: return
        if (s.isSolved) return

        val target = s.selectedCell?.takeIf { s.playerDigits[it] != puzzle.solution[it.row][it.col] }
            ?: puzzle.allWhiteCells.firstOrNull { s.playerDigits[it] != puzzle.solution[it.row][it.col] }
            ?: return

        recordUndo(s, target)
        val newDigits = s.playerDigits + (target to puzzle.solution[target.row][target.col])
        _state.update { it.copy(
            playerDigits = newDigits,
            notes        = it.notes - target,
            selectedCell = target,
            hintsUsed    = it.hintsUsed + 1,
            canUndo      = true,
            isSolved     = checkWin(puzzle, newDigits),
        )}
    }

    // ── Conflict detection ────────────────────────────────────────────────────

    fun cellHasConflict(
        puzzle: KakuroPuzzleData?,
        digits: Map<KPos, Int>,
        showMistakes: Boolean,
        cell: KPos,
    ): Boolean {
        if (!showMistakes || puzzle == null) return false
        return runConflict(puzzle.acrossRunAt[cell], digits) || runConflict(puzzle.downRunAt[cell], digits)
    }

    private fun runConflict(run: KRun?, digits: Map<KPos, Int>): Boolean {
        if (run == null) return false
        val values = run.cells.mapNotNull { digits[it] }
        return values.size != values.toSet().size || values.sum() > run.sum
    }

    private fun checkWin(puzzle: KakuroPuzzleData, digits: Map<KPos, Int>): Boolean {
        fun runOk(run: KRun): Boolean {
            val vals = run.cells.map { digits[it] ?: return false }
            return vals.toSet().size == vals.size && vals.sum() == run.sum
        }
        return puzzle.acrossRuns.all { runOk(it) } && puzzle.downRuns.all { runOk(it) }
    }

    // ── Undo bookkeeping ──────────────────────────────────────────────────────

    private fun recordUndo(s: KakuroUiState, cell: KPos) {
        undoStack.add(UndoEntry(cell, s.playerDigits[cell], s.notes[cell] ?: emptySet()))
        if (undoStack.size > 200) undoStack.removeAt(0)
    }

    override fun onCleared() { timerJob?.cancel() }
}

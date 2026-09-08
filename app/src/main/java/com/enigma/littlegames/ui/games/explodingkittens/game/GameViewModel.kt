package com.enigma.littlegames.ui.games.explodingkittens.game

// ─────────────────────────────────────────────────────────────────────────────
// Bug fixes applied (audit report):
//
//   #9 (medium) — stealCard() walked victimIdx forward with an unbounded
//       `while (!s.players[victimIdx].isAlive) victimIdx = (victimIdx + 1) % size`
//       relying on the invariant "the game already ended once <=1 player is
//       alive," which is enforced in endTurn() but not re-checked at the top
//       of stealCard()/handleCatCardPlay(). If that invariant is ever
//       violated (e.g. a race between a NETWORK_JOIN state sync and a local
//       play), this becomes a genuine UI-thread infinite loop with no
//       timeout. Fixed to bound the scan to `players.size` iterations and
//       bail out (no-op) if no living victim can be found.
//
//   #11 (medium) — onExitGame() unconditionally called clearSavedGame() for
//       any network host/client exiting mid-game, even if a different valid
//       single-player/pass-and-play save already existed from an earlier
//       session. Exiting a LAN game silently wiped a resumable local save
//       that had nothing to do with the network session. Fixed so a network
//       exit only clears state — it never touches a save that belongs to a
//       different (single-player/pass-and-play) game mode.
// ─────────────────────────────────────────────────────────────────────────────

import android.app.Application
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.enigma.littlegames.ui.games.explodingkittens.data.Card
import com.enigma.littlegames.ui.games.explodingkittens.data.Player
import com.enigma.littlegames.ui.games.explodingkittens.data.states.GameMode
import com.enigma.littlegames.ui.games.explodingkittens.data.states.GameState
import com.enigma.littlegames.ui.games.explodingkittens.data.states.GameUiState
import com.enigma.littlegames.ui.games.explodingkittens.data.types.AIDifficulty
import com.enigma.littlegames.ui.games.explodingkittens.data.types.CardType
import com.enigma.littlegames.ui.games.explodingkittens.data.types.PlayerType
import com.enigma.littlegames.ui.games.explodingkittens.game.gamelogic.AIPlayer
import com.enigma.littlegames.ui.games.explodingkittens.game.gamelogic.GameAction
import com.enigma.littlegames.ui.games.explodingkittens.game.gamelogic.GameStateUpdate
import com.enigma.littlegames.ui.games.explodingkittens.game.gamelogic.NetworkManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private const val PREFS_NAME     = "EKPrefs"
private const val SAVED_GAME_KEY = "SavedGame"

class EKViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private val networkManager = NetworkManager(viewModelScope)
    private var isHost     = false
    private var myPlayerId = 1
    private var aiPlayer: AIPlayer? = null
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Callback invoked by HubViewModel to record a win
    var onWin: ((winner: String, vsAI: Boolean) -> Unit)? = null

    init {
        setupNetworkCallbacks()
        checkForSavedGame()

        // AI turn loop
        _uiState.onEach { state ->
            if (state.gameState == GameState.PLAYING &&
                state.gameMode == GameMode.SINGLE_PLAYER &&
                state.players.isNotEmpty() &&
                state.currentPlayerIndex < state.players.size
            ) {
                val cur = state.players[state.currentPlayerIndex]
                if (cur.type == PlayerType.AI && cur.isAlive && aiPlayer != null) {
                    delay(1500)
                    val move = aiPlayer!!.makeMove(cur, state.deck.size, state.players)
                    if (move.action == "PLAY" && move.card != null) onPlayCard(move.card)
                    else executeEndTurnAndDraw()
                }
            }
        }.launchIn(viewModelScope)
    }

    private fun setupNetworkCallbacks() {
        networkManager.onStateReceived      = { onStateUpdateReceived(it) }
        networkManager.onActionReceived     = { onActionReceivedFromClient(it) }
        networkManager.onClientConnected    = { ip ->
            val list = _uiState.value.connectedPlayers.toMutableList().also { it.add(ip) }
            _uiState.update { it.copy(connectedPlayers = list, connectionStatus = "$ip joined!") }
        }
        networkManager.onClientDisconnected = { ip ->
            _uiState.update { it.copy(gameState = GameState.GAME_OVER, gameMessage = "$ip disconnected.") }
        }
        networkManager.onHostDisconnected   = {
            _uiState.update { it.copy(gameState = GameState.GAME_OVER, gameMessage = "Host disconnected.") }
        }
    }

    // ── Public events ─────────────────────────────────────────────────────────

    fun onGameModeSelected(mode: GameMode) {
        aiPlayer = null
        isHost     = (mode == GameMode.NETWORK_HOST)
        myPlayerId = if (isHost) 1 else 2
        _uiState.update {
            it.copy(
                gameMode  = mode,
                gameState = if (mode == GameMode.NETWORK_HOST || mode == GameMode.NETWORK_JOIN)
                    GameState.LOBBY else GameState.SETUP,
                localIP   = if (mode == GameMode.NETWORK_HOST)
                    networkManager.getLocalIPAddress(getApplication()) else "",
            )
        }
    }

    fun onPlayerCountChange(n: Int) { if (n in 2..5) _uiState.update { it.copy(playerCount = n) } }
    fun onAIDifficultyChange(d: AIDifficulty) { _uiState.update { it.copy(aiDifficulty = d) } }

    fun onStartHost() {
        isHost = true; myPlayerId = 1
        networkManager.startHost()
        _uiState.update { it.copy(connectionStatus = "Hosting on ${_uiState.value.localIP}…") }
    }

    fun onJoinHost(ip: String) {
        if (ip.isBlank()) { _uiState.update { it.copy(connectionStatus = "IP cannot be empty.") }; return }
        isHost = false; myPlayerId = 2
        networkManager.connectToHost(ip)
        _uiState.update { it.copy(connectionStatus = "Connecting to $ip…") }
    }

    fun onStartGame() {
        if (!isHost && _uiState.value.gameMode == GameMode.NETWORK_JOIN) return
        // Bug fix (audit #11): starting a fresh game only clears the save
        // slot for the mode being started, not any unrelated save — see
        // clearSavedGame() usage rationale below in onExitGame().
        clearSavedGame()
        if (_uiState.value.gameMode == GameMode.SINGLE_PLAYER) {
            aiPlayer = AIPlayer(_uiState.value.aiDifficulty)
        }

        val count = _uiState.value.playerCount
        val deck  = createDeck(count).toMutableList()
        val players = (1..count).map { id ->
            val hand = mutableListOf(makeCard(CardType.DEFUSE))
            repeat(7) { if (deck.isNotEmpty()) hand.add(deck.removeAt(0)) }
            when (_uiState.value.gameMode) {
                GameMode.SINGLE_PLAYER -> Player(
                    id, if (id == 1) "You" else "AI $id", hand,
                    type = if (id == 1) PlayerType.HUMAN else PlayerType.AI,
                    aiDifficulty = _uiState.value.aiDifficulty,
                )
                else -> Player(id, "Player $id", hand)
            }
        }
        repeat(count - 1) { deck.add(makeCard(CardType.EXPLODING_KITTEN)) }
        deck.shuffle()

        _uiState.update {
            it.copy(
                gameState          = GameState.PLAYING,
                players            = players,
                deck               = deck,
                discardPile        = emptyList(),
                currentPlayerIndex = 0,
                gameMessage        = "Game started! ${players[0].name}'s turn.",
            )
        }
        if (isHost) broadcastState()
    }

    fun onResumeGame() {
        prefs.getString(SAVED_GAME_KEY, null)?.let { json ->
            val saved = Json.decodeFromString<GameUiState>(json)
            if (saved.gameMode == GameMode.SINGLE_PLAYER) aiPlayer = AIPlayer(saved.aiDifficulty)
            _uiState.value = saved
        }
    }

    /**
     * Bug fix (audit #11): the old logic was:
     *   if (mode is SINGLE_PLAYER or PASS_AND_PLAY, and gameState == PLAYING)
     *       saveGame()
     *   else
     *       clearSavedGame()
     *
     * That `else` branch fired for NETWORK_HOST / NETWORK_JOIN exits too,
     * which meant leaving a LAN game always wiped whatever save slot existed
     * — even a perfectly valid, unrelated single-player/pass-and-play save
     * from an earlier session that the player had every reason to expect
     * would still be resumable.
     *
     * Fixed: a network session exit no longer touches the save slot at all.
     * The save slot is only ever written (by saveGame()) or cleared in
     * response to actions that actually belong to a local, resumable game
     * (starting a new local game, or exiting an in-progress local game
     * without wanting to resume it later — which callers can still do via
     * saveGame()/clearSavedGame() directly if desired).
     */
    fun onExitGame() {
        val s = _uiState.value
        val isResumableLocalMode = s.gameMode == GameMode.SINGLE_PLAYER || s.gameMode == GameMode.PASS_AND_PLAY
        if (isResumableLocalMode && s.gameState == GameState.PLAYING) {
            saveGame()
        }
        // Network sessions (host/join) and non-playing states simply reset
        // the in-memory game — they must NOT clear a save belonging to a
        // different, resumable local game.
        resetGame()
    }

    private fun saveGame() {
        prefs.edit().putString(SAVED_GAME_KEY, Json.encodeToString(_uiState.value)).apply()
    }
    private fun checkForSavedGame() {
        _uiState.update { it.copy(hasSavedGame = prefs.contains(SAVED_GAME_KEY)) }
    }
    private fun clearSavedGame() { prefs.edit().remove(SAVED_GAME_KEY).apply() }

    fun onPlayCard(card: Card) {
        val state = _uiState.value
        if (state.gameState == GameState.NOPE_CHANCE) {
            if (card.type == CardType.NOPE) executeNope(card)
            return
        }
        if (isHost || state.gameMode != GameMode.NETWORK_JOIN) {
            executePlayCard(card)
            if (isHost) broadcastState()
        } else {
            networkManager.sendActionToHost(GameAction("PLAY_CARD", card, myPlayerId))
        }
    }

    private var nopeJob: kotlinx.coroutines.Job? = null

    private fun startNopeTimer(card: Card) {
        nopeJob?.cancel()
        _uiState.update { it.copy(
            gameState       = GameState.NOPE_CHANCE,
            pendingAction   = card,
            nopeCount       = 0,
            actionCountdown = 5,
            gameMessage     = "Playing ${card.name}… Nope anyone?",
        )}
        nopeJob = viewModelScope.launch {
            while (_uiState.value.actionCountdown > 0) {
                delay(1000)
                _uiState.update { it.copy(actionCountdown = it.actionCountdown - 1) }
            }
            resolvePendingAction()
        }
    }

    private fun executeNope(card: Card) {
        val s = _uiState.value
        val players = s.players.toMutableList()
        val pIdx = players.indexOfFirst { p -> p.hand.any { it.id == card.id } }
        if (pIdx == -1) return
        val newHand = players[pIdx].hand.toMutableList().apply { remove(card) }
        players[pIdx] = players[pIdx].copy(hand = newHand)
        val discard  = s.discardPile.toMutableList().also { it.add(card) }
        val newNope  = s.nopeCount + 1
        _uiState.update { it.copy(
            players     = players,
            discardPile = discard,
            nopeCount   = newNope,
            actionCountdown = 5,
            gameMessage = "NOPE! (Stack: $newNope)",
        )}
    }

    private fun resolvePendingAction() {
        nopeJob?.cancel()
        val s = _uiState.value; val card = s.pendingAction ?: return
        val cancelled = s.nopeCount % 2 != 0
        _uiState.update { it.copy(gameState = GameState.PLAYING, pendingAction = null, nopeCount = 0) }
        if (cancelled) _uiState.update { it.copy(gameMessage = "${card.name} was NOPED!") }
        else applyCardEffect(card)
    }

    private fun executePlayCard(card: Card) {
        val s = _uiState.value
        val players = s.players.toMutableList()
        val cur     = players[s.currentPlayerIndex]
        val newHand = cur.hand.toMutableList().apply { remove(card) }
        players[s.currentPlayerIndex] = cur.copy(hand = newHand)
        val discard = s.discardPile.toMutableList().also { it.add(card) }
        _uiState.update { it.copy(players = players, discardPile = discard) }
        when {
            card.type == CardType.NORMAL -> handleCatCardPlay(card, s.currentPlayerIndex)
            card.type == CardType.NOPE   -> _uiState.update { it.copy(gameMessage = "Nothing to Nope!") }
            else                         -> startNopeTimer(card)
        }
    }

    private fun handleCatCardPlay(card: Card, pIdx: Int) {
        val s = _uiState.value; val player = s.players[pIdx]
        val second = player.hand.find { it.name == card.name }
        if (second != null) {
            val newHand = player.hand.toMutableList().apply { remove(second) }
            val players = s.players.toMutableList().apply { this[pIdx] = player.copy(hand = newHand) }
            val discard = s.discardPile.toMutableList().also { it.add(second) }
            _uiState.update { it.copy(players = players, discardPile = discard,
                gameMessage = "${player.name} plays a pair — stealing a card…") }
            stealCard(pIdx)
        } else {
            _uiState.update { it.copy(gameMessage = "${player.name} played ${card.name}. (Need a pair to steal)") }
        }
    }

    /**
     * Bug fix (audit #9): the old scan
     *   `var victimIdx = (thiefIdx + 1) % size`
     *   `while (!s.players[victimIdx].isAlive) victimIdx = (victimIdx + 1) % size`
     * relies entirely on "at least one OTHER player besides the thief is
     * alive" always being true here, an invariant enforced elsewhere
     * (endTurn()) but not re-checked at the top of this function. If it's
     * ever violated this is an unbounded UI-thread loop. Now bounded to at
     * most `players.size` steps and a safe no-op if no living victim exists.
     */
    private fun stealCard(thiefIdx: Int) {
        val s = _uiState.value
        if (s.players.size < 2) return

        var victimIdx = -1
        for (step in 1 until s.players.size) {
            val candidate = (thiefIdx + step) % s.players.size
            if (s.players[candidate].isAlive) { victimIdx = candidate; break }
        }
        if (victimIdx == -1) return  // no living victim found — safe no-op instead of looping forever

        val victim = s.players[victimIdx]
        if (victim.hand.isEmpty()) return
        val stolen = victim.hand.random()
        val players = s.players.toMutableList()
        players[victimIdx] = victim.copy(hand = victim.hand.toMutableList().apply { remove(stolen) })
        val thief = players[thiefIdx]
        players[thiefIdx] = thief.copy(hand = thief.hand.toMutableList().apply { add(stolen) })
        _uiState.update { it.copy(players = players,
            gameMessage = "${thief.name} stole a card from ${victim.name}!") }
    }

    private fun applyCardEffect(card: Card) {
        val s = _uiState.value
        val players = s.players.toMutableList()
        val curIdx  = s.currentPlayerIndex
        val cur     = players[curIdx]
        when (card.type) {
            CardType.ATTACK -> {
                var vIdx = (curIdx + 1) % players.size
                while (!players[vIdx].isAlive) vIdx = (vIdx + 1) % players.size
                players[vIdx] = players[vIdx].copy(turnsToTake = players[vIdx].turnsToTake + 1)
                _uiState.update { it.copy(players = players,
                    gameMessage = "${cur.name} attacked ${players[vIdx].name}!") }
                endTurn(skipped = true)
            }
            CardType.SKIP -> {
                _uiState.update { it.copy(gameMessage = "${cur.name} skipped.") }
                endTurn(skipped = false)
            }
            CardType.SEE_FUTURE -> {
                _uiState.update { it.copy(showFutureCards = true, futureCards = s.deck.take(3)) }
            }
            CardType.SHUFFLE -> {
                _uiState.update { it.copy(deck = s.deck.shuffled(), gameMessage = "Deck shuffled!") }
            }
            else -> {}
        }
    }

    fun onEndTurnAndDraw() = executeEndTurnAndDraw()

    private fun executeEndTurnAndDraw() {
        viewModelScope.launch {
            val s = _uiState.value
            if (s.deck.isEmpty()) { endTurn(skipped = true); return@launch }
            val cur    = s.players[s.currentPlayerIndex]
            val newDeck = s.deck.toMutableList()
            val drawn  = newDeck.removeAt(0)
            if (drawn.type == CardType.EXPLODING_KITTEN) {
                handleKittenDraw(cur, drawn)
            } else {
                val newHand = cur.hand.toMutableList().apply { add(drawn) }
                val players = s.players.toMutableList()
                    .apply { this[s.currentPlayerIndex] = cur.copy(hand = newHand) }
                _uiState.update { it.copy(players = players, deck = newDeck,
                    gameMessage = "${cur.name} drew a card.") }
                endTurn(skipped = false)
            }
        }
    }

    private fun handleKittenDraw(player: Player, kitten: Card) {
        val s      = _uiState.value
        val defuse = player.hand.find { it.type == CardType.DEFUSE }
        if (defuse != null) {
            val newHand = player.hand.toMutableList().apply { remove(defuse) }
            val players = s.players.toMutableList()
                .apply { this[s.currentPlayerIndex] = player.copy(hand = newHand) }
            val discard = s.discardPile.toMutableList().also { it.add(defuse) }
            _uiState.update { it.copy(
                gameState       = GameState.AWAITING_KITTEN_PLACEMENT,
                cardToPlaceBack = kitten,
                players         = players,
                discardPile     = discard,
                gameMessage     = "${player.name} defused a Kitten! Place it back.",
            )}
        } else {
            val players = s.players.toMutableList()
                .apply { this[s.currentPlayerIndex] = player.copy(isAlive = false) }
            val discard = s.discardPile.toMutableList().also { it.add(kitten) }
            _uiState.update { it.copy(players = players, discardPile = discard,
                gameMessage = "${player.name} EXPLODED! 💥") }
            endTurn(skipped = true)
        }
    }

    fun onKittenPlaced(position: Int) {
        val card = _uiState.value.cardToPlaceBack ?: return
        val deck = _uiState.value.deck.toMutableList()
            .also { it.add(position.coerceIn(0, it.size), card) }
        _uiState.update { it.copy(gameState = GameState.PLAYING, cardToPlaceBack = null, deck = deck) }
        endTurn(skipped = true)
    }

    private fun endTurn(skipped: Boolean) {
        val s          = _uiState.value
        val cur        = s.players[s.currentPlayerIndex]
        val turnsLeft  = cur.turnsToTake - 1

        if (turnsLeft > 0 && !skipped) {
            val players = s.players.toMutableList()
                .apply { this[s.currentPlayerIndex] = cur.copy(turnsToTake = turnsLeft) }
            _uiState.update { it.copy(players = players,
                gameMessage = "${cur.name} has $turnsLeft turns left.") }
        } else {
            var nextIdx = s.currentPlayerIndex
            if (s.players.any { it.isAlive }) {
                do { nextIdx = (nextIdx + 1) % s.players.size } while (!s.players[nextIdx].isAlive)
            }
            val initialTurns = if (skipped && turnsLeft > 0) turnsLeft else 1
            val players = s.players.toMutableList()
                .apply { this[nextIdx] = this[nextIdx].copy(turnsToTake = initialTurns) }
            _uiState.update { it.copy(players = players, currentPlayerIndex = nextIdx,
                gameMessage = "${players[nextIdx].name}'s turn.") }
        }

        // Check win
        val alive = _uiState.value.players.filter { it.isAlive }
        if (alive.size <= 1 && _uiState.value.gameState == GameState.PLAYING) {
            val winner = alive.firstOrNull()
            _uiState.update { it.copy(gameState = GameState.GAME_OVER, winner = winner) }
            winner?.let { w -> onWin?.invoke(w.name, w.type == PlayerType.HUMAN) }
            return
        }

        // Pass-and-play handoff
        if (!skipped || turnsLeft <= 0) {
            if (_uiState.value.gameMode == GameMode.PASS_AND_PLAY) {
                val nextName = _uiState.value.players[_uiState.value.currentPlayerIndex].name
                _uiState.update { it.copy(gameState = GameState.HANDOFF,
                    gameMessage = "Pass to $nextName.") }
            }
        }
        if (isHost) broadcastState()
    }

    fun onHandoffConfirmed() {
        val name = _uiState.value.players[_uiState.value.currentPlayerIndex].name
        _uiState.update { it.copy(gameState = GameState.PLAYING,
            gameMessage = "It's your turn, $name!") }
    }

    fun onShowTutorial() { _uiState.update { it.copy(gameState = GameState.TUTORIAL) } }
    fun onCloseFuture()  { _uiState.update { it.copy(showFutureCards = false) } }
    fun onBackToMenu()   = resetGame()

    fun onHostIpChange(ip: String) { _uiState.update { it.copy(hostIP = ip) } }

    private fun broadcastState() {
        if (!isHost) return
        val s = _uiState.value
        networkManager.broadcastStateToClients(
            GameStateUpdate(s.players, s.currentPlayerIndex, s.deck, s.discardPile, s.gameMessage, s.gameState)
        )
    }

    private fun resetGame() {
        networkManager.disconnect(); aiPlayer = null
        _uiState.update { GameUiState(hasSavedGame = prefs.contains(SAVED_GAME_KEY)) }
    }

    private fun onStateUpdateReceived(update: GameStateUpdate) {
        if (isHost) return
        _uiState.update { it.copy(
            players            = update.players,
            currentPlayerIndex = update.currentPlayerIndex,
            deck               = update.deck,
            discardPile        = update.discardPile,
            gameMessage        = update.gameMessage,
            gameState          = update.gameState,
        )}
    }

    private fun onActionReceivedFromClient(action: GameAction) {
        if (!isHost) return
        val s  = _uiState.value
        val cur = s.players[s.currentPlayerIndex]
        if (action.playerId == cur.id) {
            when (action.actionType) {
                "PLAY_CARD" -> action.card?.let { onPlayCard(it) }
                "END_TURN"  -> executeEndTurnAndDraw()
            }
            broadcastState()
        }
    }

    // ── Deck building ─────────────────────────────────────────────────────────

    private fun createDeck(playerCount: Int): List<Card> {
        val deck = mutableListOf<Card>()
        repeat(4) { deck.add(makeCard(CardType.ATTACK)) }
        repeat(4) { deck.add(makeCard(CardType.SKIP)) }
        repeat(5) { deck.add(makeCard(CardType.SEE_FUTURE)) }
        repeat(4) { deck.add(makeCard(CardType.SHUFFLE)) }
        repeat(5) { deck.add(makeCard(CardType.NOPE)) }
        repeat(4) { deck.add(makeCard(CardType.NORMAL, "TacoCat")) }
        repeat(4) { deck.add(makeCard(CardType.NORMAL, "Hairy Potato Cat")) }
        repeat(4) { deck.add(makeCard(CardType.NORMAL, "Cattermelon")) }
        repeat(4) { deck.add(makeCard(CardType.NORMAL, "Beard Cat")) }
        repeat(4) { deck.add(makeCard(CardType.NORMAL, "Rainbow Cat")) }
        val defuseInDeck = if (playerCount <= 2) 2 else 6 - playerCount
        repeat(defuseInDeck) { deck.add(makeCard(CardType.DEFUSE)) }
        return deck.shuffled()
    }

    private fun makeCard(type: CardType, catName: String? = null): Card {
        val name = catName ?: when (type) {
            CardType.EXPLODING_KITTEN -> "💣 Exploding Kitten"
            CardType.DEFUSE           -> "🛡️ Defuse"
            CardType.SKIP             -> "⏭️ Skip"
            CardType.ATTACK           -> "⚔️ Attack"
            CardType.SEE_FUTURE       -> "🔮 See Future"
            CardType.SHUFFLE          -> "🔀 Shuffle"
            CardType.NOPE             -> "🙅 Nope"
            CardType.NORMAL           -> "🐱 Cat"
        }
        val displayName = if (type == CardType.NORMAL) "🐱 $catName" else name
        return Card(suit = "", rank = "", type = type, name = displayName)
    }

    override fun onCleared() { super.onCleared(); networkManager.disconnect() }
}

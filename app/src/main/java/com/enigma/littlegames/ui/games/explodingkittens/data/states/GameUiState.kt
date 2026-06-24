package com.enigma.littlegames.ui.games.explodingkittens.data.states

import com.enigma.littlegames.ui.games.explodingkittens.data.Card
import com.enigma.littlegames.ui.games.explodingkittens.data.Player
import com.enigma.littlegames.ui.games.explodingkittens.data.types.AIDifficulty
import kotlinx.serialization.Serializable

@Serializable
data class GameUiState(
    val gameState: GameState         = GameState.MENU,
    val players: List<Player>        = emptyList(),
    val currentPlayerIndex: Int      = 0,
    val deck: List<Card>             = emptyList(),
    val discardPile: List<Card>      = emptyList(),
    val gameMessage: String          = "Welcome to Exploding Kittens!",
    val showFutureCards: Boolean     = false,
    val futureCards: List<Card>      = emptyList(),
    val winner: Player?              = null,
    val gameMode: GameMode?          = null,
    val playerCount: Int             = 2,
    val aiDifficulty: AIDifficulty   = AIDifficulty.MEDIUM,
    val hostIP: String               = "",
    val connectionStatus: String     = "",
    val localIP: String              = "",
    val connectedPlayers: List<String> = emptyList(),
    val cardToPlaceBack: Card?       = null,
    val pendingAction: Card?         = null,
    val nopeCount: Int               = 0,
    val actionCountdown: Int         = 0,
    val victimId: Int?               = null,
    val hasSavedGame: Boolean        = false,
) {
    val deckSize: Int = deck.size
}

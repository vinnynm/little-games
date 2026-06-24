package com.enigma.littlegames.ui.games.explodingkittens.game.gamelogic

import com.enigma.littlegames.ui.games.explodingkittens.data.Card
import com.enigma.littlegames.ui.games.explodingkittens.data.Player
import com.enigma.littlegames.ui.games.explodingkittens.data.states.GameState
import kotlinx.serialization.Serializable

@Serializable
data class GameStateUpdate(
    val players: List<Player>,
    val currentPlayerIndex: Int,
    val deck: List<Card>,
    val discardPile: List<Card>,
    val gameMessage: String,
    val gameState: GameState,
)

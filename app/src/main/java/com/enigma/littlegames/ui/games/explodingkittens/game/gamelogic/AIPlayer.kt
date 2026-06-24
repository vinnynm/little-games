package com.enigma.littlegames.ui.games.explodingkittens.game.gamelogic

import com.enigma.littlegames.ui.games.explodingkittens.data.Card
import com.enigma.littlegames.ui.games.explodingkittens.data.Player
import com.enigma.littlegames.ui.games.explodingkittens.data.types.AIDifficulty
import com.enigma.littlegames.ui.games.explodingkittens.data.types.CardType
import kotlin.random.Random

class AIPlayer(private val difficulty: AIDifficulty) {

    data class AIMove(val action: String, val card: Card?)

    fun makeMove(player: Player, deckSize: Int, allPlayers: List<Player>): AIMove {
        val playable = player.hand.filter {
            it.type in listOf(CardType.SKIP, CardType.ATTACK, CardType.SEE_FUTURE, CardType.SHUFFLE)
        }
        return when (difficulty) {
            AIDifficulty.EASY   -> easyMove(playable)
            AIDifficulty.MEDIUM -> mediumMove(player, deckSize, playable)
            AIDifficulty.HARD   -> hardMove(player, deckSize, allPlayers, playable)
        }
    }

    private fun easyMove(playable: List<Card>): AIMove =
        if (playable.isNotEmpty() && Random.nextFloat() < 0.3f) AIMove("PLAY", playable.random())
        else AIMove("DRAW", null)

    private fun mediumMove(player: Player, deckSize: Int, playable: List<Card>): AIMove {
        val defuse = player.hand.count { it.type == CardType.DEFUSE }
        playable.find { it.type == CardType.SEE_FUTURE }?.let { if (defuse <= 1) return AIMove("PLAY", it) }
        playable.find { it.type == CardType.ATTACK }?.let { if (player.hand.size > 6) return AIMove("PLAY", it) }
        playable.find { it.type == CardType.SHUFFLE }?.let { if (deckSize < 5) return AIMove("PLAY", it) }
        return AIMove("DRAW", null)
    }

    private fun hardMove(player: Player, deckSize: Int, allPlayers: List<Player>, playable: List<Card>): AIMove {
        val defuse = player.hand.count { it.type == CardType.DEFUSE }
        val alive  = allPlayers.count { it.isAlive }
        val risk   = if (deckSize > 0) (allPlayers.size - 1).toFloat() / deckSize else 0f

        if (risk > 0.2f && defuse == 0) {
            playable.find { it.type == CardType.SKIP }?.let { return AIMove("PLAY", it) }
            playable.find { it.type == CardType.SEE_FUTURE }?.let { return AIMove("PLAY", it) }
        }
        if (defuse >= 2 || risk < 0.1f) {
            playable.find { it.type == CardType.ATTACK }?.let { return AIMove("PLAY", it) }
        }
        if (alive <= 3) {
            playable.find { it.type == CardType.ATTACK }?.let { return AIMove("PLAY", it) }
        }
        return AIMove("DRAW", null)
    }
}

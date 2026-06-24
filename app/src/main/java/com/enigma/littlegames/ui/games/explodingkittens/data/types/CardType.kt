package com.enigma.littlegames.ui.games.explodingkittens.data.types

import kotlinx.serialization.Serializable

@Serializable
enum class CardType {
    EXPLODING_KITTEN,
    DEFUSE,
    SKIP,
    ATTACK,
    SEE_FUTURE,
    SHUFFLE,
    NORMAL,
    NOPE
}

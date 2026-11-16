package net.zodac.ludo.game

import kotlin.random.Random

/**
 * Who goes first: every player rolls once and the highest roll starts. Players tied on the highest roll roll again,
 * alone, until one is highest. [rounds] holds each round's rolls by player index, for the screen to show.
 */
data class RollOff(val rounds: List<Map<Int, Int>>, val first: Int) {
    companion object {
        /** Rolls off between [playerCount] players with a [dieFaces]-sided die drawn from [random]. */
        fun roll(playerCount: Int, dieFaces: Int, random: Random): RollOff =
            decide(playerCount) { random.nextInt(1, dieFaces + 1) }

        /** Rolls off between [playerCount] players, each roll from [die]. */
        fun decide(playerCount: Int, die: () -> Int): RollOff {
            require(playerCount >= 1) { "A roll-off needs a player" }
            val rounds = mutableListOf<Map<Int, Int>>()
            var rolling = (0..<playerCount).toList()
            while (rolling.size > 1) {
                val rolls = rolling.associateWith { die() }
                rounds += rolls
                val highest = rolls.values.max()
                rolling = rolling.filter { rolls.getValue(it) == highest }
            }
            return RollOff(rounds, rolling.single())
        }
    }
}

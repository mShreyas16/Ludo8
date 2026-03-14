package com.example.ludo8.shared.model

import kotlin.random.Random

/**
 * Dice abstraction for deterministic tests and platform-independent randomness.
 */
interface Dice {
    fun roll(): Int
}

/**
 * Default dice implementation: uniformly random 1..6.
 */
class RandomDice(
    private val random: Random = Random.Default,
) : Dice {
    override fun roll(): Int = random.nextInt(1, 7)
}

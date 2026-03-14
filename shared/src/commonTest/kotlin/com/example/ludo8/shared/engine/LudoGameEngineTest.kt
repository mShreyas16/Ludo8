package com.example.ludo8.shared.engine

import com.example.ludo8.shared.model.Dice
import com.example.ludo8.shared.model.PlayerColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class LudoGameEngineTest {
    private class FixedDice(private val value: Int) : Dice {
        override fun roll(): Int = value
    }

    private class SequenceDice(private val values: IntArray) : Dice {
        private var index: Int = 0
        override fun roll(): Int {
            val i = index.coerceAtMost(values.lastIndex)
            index++
            return values[i]
        }
    }

    @Test
    fun tokenEntersOnlyOnSix() {
        val engine = LudoGameEngine(playerCount = 2, dice = FixedDice(5))
        assertEquals(null, engine.state.diceValue)

        val movedWithoutDice = engine.moveToken(tokenId = 0)
        assertEquals(-1, movedWithoutDice.players[0].tokens[0].progress)

        engine.rollDice()
        assertEquals(null, engine.state.diceValue)
        assertEquals(1, engine.state.currentPlayerIndex)
        val after = engine.moveToken(0)
        assertEquals(-1, after.players[0].tokens[0].progress)
    }

    @Test
    fun winnerStartsNull() {
        val engine = LudoGameEngine(playerCount = 4)
        assertNull(engine.state.winner)
        assertNotNull(engine.state.players.firstOrNull())
    }

    @Test
    fun turnOrderIsClockwiseBlueYellowGreenRed() {
        val engine = LudoGameEngine(playerCount = 4, dice = FixedDice(1))
        assertEquals(
            listOf(PlayerColor.Blue, PlayerColor.Yellow, PlayerColor.Green, PlayerColor.Red),
            engine.state.players.map { it.color },
        )
        assertEquals(PlayerColor.Blue, engine.state.currentPlayer.color)

        engine.rollDice()
        assertEquals(PlayerColor.Yellow, engine.state.currentPlayer.color)

        engine.rollDice()
        assertEquals(PlayerColor.Green, engine.state.currentPlayer.color)

        engine.rollDice()
        assertEquals(PlayerColor.Red, engine.state.currentPlayer.color)

        engine.rollDice()
        assertEquals(PlayerColor.Blue, engine.state.currentPlayer.color)
    }

    @Test
    fun turnOrderTruncatesFor2And3Players() {
        val engine2 = LudoGameEngine(playerCount = 2, dice = FixedDice(1))
        assertEquals(listOf(PlayerColor.Blue, PlayerColor.Yellow), engine2.state.players.map { it.color })

        val engine3 = LudoGameEngine(playerCount = 3, dice = FixedDice(1))
        assertEquals(listOf(PlayerColor.Blue, PlayerColor.Yellow, PlayerColor.Green), engine3.state.players.map { it.color })
    }

    @Test
    fun autoMovesWhenOnlyOneTokenIsPlayable() {
        val engine = LudoGameEngine(playerCount = 2, dice = SequenceDice(intArrayOf(6, 3)))

        engine.rollDice()
        engine.moveToken(0)
        val afterEnter = engine.state
        assertEquals(0, afterEnter.currentPlayerIndex)
        assertEquals(0, afterEnter.currentPlayer.tokens[0].progress)

        engine.rollDice()
        val afterAuto = engine.state
        assertEquals(null, afterAuto.diceValue)
        assertEquals(PlayerColor.Yellow, afterAuto.currentPlayer.color)
        assertEquals(3, afterAuto.players.first { it.color == PlayerColor.Blue }.tokens[0].progress)
        assertNotNull(afterAuto.lastMove)
    }
}

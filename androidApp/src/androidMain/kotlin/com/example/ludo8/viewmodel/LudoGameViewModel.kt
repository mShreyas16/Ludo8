package com.example.ludo8.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.ludo8.shared.engine.LudoGameEngine
import com.example.ludo8.shared.model.GameState

class LudoGameViewModel(playerCount: Int) {
    private val engine = LudoGameEngine(playerCount = playerCount)

    var state: GameState by mutableStateOf(engine.state)
        private set

    fun reset(playerCount: Int) {
        state = engine.reset(playerCount)
    }

    fun rollDice() {
        state = engine.rollDice()
    }

    fun validMoves(): Set<Int> = engine.getValidMoves().toSet()

    fun moveToken(tokenId: Int) {
        state = engine.moveToken(tokenId)
    }

    fun consumeLastMove() {
        state = engine.consumeLastMove()
    }
}

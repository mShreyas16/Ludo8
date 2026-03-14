package com.example.ludo8.shared.engine

import com.example.ludo8.shared.model.GameState
import com.example.ludo8.shared.model.MoveResult
import com.example.ludo8.shared.model.Player
import com.example.ludo8.shared.model.PlayerColor
import com.example.ludo8.shared.model.Dice
import com.example.ludo8.shared.model.RandomDice
import com.example.ludo8.shared.model.Token
import com.example.ludo8.shared.model.TokenRef
import com.example.ludo8.shared.rules.LudoBoard

/**
 * Pure Kotlin Ludo game engine.
 *
 * Responsibilities:
 * - Maintains immutable [GameState]
 * - Applies standard Ludo rules (entry on 6, clockwise movement, capture, exact finish, win detection)
 * - Exposes a minimal API for platform UIs (Android/iOS) to drive the game
 */
class LudoGameEngine(
    playerCount: Int,
) {
    private var dice: Dice = RandomDice()
    private val clockwiseTurnOrder: List<PlayerColor> = listOf(
        PlayerColor.Blue,
        PlayerColor.Yellow,
        PlayerColor.Green,
        PlayerColor.Red,
    )
    private var colorsInGame: List<PlayerColor> = clockwiseTurnOrder.take(playerCount.coerceIn(2, 4))

    internal constructor(
        playerCount: Int,
        dice: Dice,
    ) : this(playerCount) {
        this.dice = dice
    }

    private var _state: GameState = newGame(colorsInGame)
    val state: GameState get() = _state

    /**
     * Starts a new game with [playerCount] players (2–4).
     */
    fun reset(playerCount: Int): GameState {
        colorsInGame = clockwiseTurnOrder.take(playerCount.coerceIn(2, 4))
        _state = newGame(colorsInGame)
        return _state
    }

    /**
     * Rolls a dice (1–6). If no moves are possible, advances the turn automatically.
     */
    fun rollDice(): GameState {
        val current = _state
        if (current.winner != null) return current
        if (current.diceValue != null) return current

        val value = dice.roll()
        val withDice = current.copy(
            diceValue = value,
            lastMove = null,
            message = "${current.currentPlayer.color} rolled $value",
        ).withComputedBoard()

        val valid = getValidMoves(withDice)
        _state = when {
            valid.isEmpty() -> nextTurn(withDice.copy(message = "${withDice.currentPlayer.color} rolled $value (no moves)"))
            valid.size == 1 -> {
                _state = withDice
                moveToken(valid.first())
            }

            else -> withDice
        }
        return _state
    }

    fun getValidMoves(): List<Int> = getValidMoves(_state)

    /**
     * Convenience for Swift: returns the current valid token IDs as an [IntArray].
     */
    fun validMoveTokenIds(): IntArray = getValidMoves(_state).toIntArray()

    /**
     * Moves the given token for the current player using the last rolled dice value.
     */
    fun moveToken(tokenId: Int): GameState {
        val current = _state
        val diceValue = current.diceValue ?: return current
        if (current.winner != null) return current

        val playerIndex = current.currentPlayerIndex
        val player = current.players[playerIndex]
        val token = player.tokens.firstOrNull { it.id == tokenId } ?: return current
        if (!canMove(token, diceValue)) return current

        val fromProgress = token.progress
        val toProgress = if (fromProgress < 0) 0 else fromProgress + diceValue

        val movedToken = token.copy(progress = toProgress)
        val updatedPlayer = player.copy(tokens = player.tokens.map { if (it.id == tokenId) movedToken else it })
        val playersAfterMove = current.players.mapIndexed { index, p -> if (index == playerIndex) updatedPlayer else p }

        val captured = captureIfAny(
            players = playersAfterMove,
            moverColor = player.color,
            moverToken = movedToken,
        )

        val playersAfterCapture = captured.players
        val winner = checkWinner(playersAfterCapture)

        val movePath = computeMovePath(player.color, token, diceValue)
        val moveResult = MoveResult(
            token = TokenRef(player.color, tokenId),
            path = movePath,
            captured = captured.captured,
            finishedThisMove = toProgress == LudoBoard.finishedProgress,
        )

        val withMove = current.copy(
            players = playersAfterCapture,
            diceValue = null,
            winner = winner,
            lastMove = moveResult,
            message = when {
                winner != null -> "${winner} wins!"
                captured.captured.isNotEmpty() -> "${player.color} captured ${captured.captured.size}"
                else -> "${player.color} moved"
            },
        ).withComputedBoard()

        _state = when {
            winner != null -> withMove
            diceValue == 6 -> withMove.copy(message = "${player.color} gets another turn").withComputedBoard()
            else -> nextTurn(withMove)
        }
        return _state
    }

    fun consumeLastMove(): GameState {
        _state = _state.copy(lastMove = null).withComputedBoard()
        return _state
    }

    private fun nextTurn(state: GameState): GameState {
        val next = (state.currentPlayerIndex + 1) % state.players.size
        return state.copy(
            currentPlayerIndex = next,
            diceValue = null,
            message = "${state.players[next].color}'s turn",
        ).withComputedBoard()
    }

    private fun getValidMoves(state: GameState): List<Int> {
        val dice = state.diceValue ?: return emptyList()
        val player = state.currentPlayer
        return player.tokens.filter { canMove(it, dice) }.map { it.id }
    }

    private fun canMove(token: Token, dice: Int): Boolean {
        if (token.progress >= LudoBoard.finishedProgress) return false
        if (token.progress < 0) return dice == 6
        return token.progress + dice <= LudoBoard.finishedProgress
    }

    private data class CaptureOutcome(val players: List<Player>, val captured: List<TokenRef>)

    private fun captureIfAny(
        players: List<Player>,
        moverColor: PlayerColor,
        moverToken: Token,
    ): CaptureOutcome {
        val landedTrackIndex = LudoBoard.tokenTrackIndex(moverColor, moverToken) ?: return CaptureOutcome(players, emptyList())
        if (landedTrackIndex in LudoBoard.safeTrackIndices) return CaptureOutcome(players, emptyList())

        val captured = mutableListOf<TokenRef>()
        val updatedPlayers = players.map { player ->
            if (player.color == moverColor) return@map player
            val updatedTokens = player.tokens.map { token ->
                val tokenIndex = LudoBoard.tokenTrackIndex(player.color, token)
                if (tokenIndex != null && tokenIndex == landedTrackIndex) {
                    captured += TokenRef(player.color, token.id)
                    token.copy(progress = -1)
                } else {
                    token
                }
            }
            player.copy(tokens = updatedTokens)
        }
        return CaptureOutcome(updatedPlayers, captured)
    }

    private fun checkWinner(players: List<Player>): PlayerColor? {
        return players.firstOrNull { player ->
            player.tokens.all { it.progress >= LudoBoard.finishedProgress }
        }?.color
    }

    private fun computeMovePath(
        playerColor: PlayerColor,
        token: Token,
        dice: Int,
    ): List<com.example.ludo8.shared.model.BoardPosition> {
        val from = token.progress
        val to = if (from < 0) 0 else from + dice

        val progressSteps = if (from < 0) {
            listOf(0)
        } else {
            (from + 1..to).toList()
        }

        return progressSteps.map { p ->
            LudoBoard.tokenBoardPosition(playerColor, Token(token.id, p))
        }
    }

    private fun GameState.withComputedBoard(): GameState {
        return copy(boardPositions = LudoBoard.computeBoardPositions(players))
    }

    private fun newGame(colors: List<PlayerColor>): GameState {
        val players = colors.map { color ->
            Player(
                color = color,
                tokens = (0 until 4).map { Token(id = it, progress = -1) },
            )
        }
        return GameState(
            players = players,
            currentPlayerIndex = 0,
            diceValue = null,
            boardPositions = LudoBoard.computeBoardPositions(players),
            winner = null,
            lastMove = null,
            message = "${players.first().color}'s turn",
        )
    }
}

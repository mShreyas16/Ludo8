package com.example.ludo8.shared.model

enum class PlayerColor {
    Red,
    Blue,
    Green,
    Yellow,
}

data class BoardPosition(
    val row: Int,
    val col: Int,
)

enum class TokenZone {
    Base,
    Track,
    HomeLane,
    Finished,
}

data class Token(
    val id: Int,
    val progress: Int,
)

data class Player(
    val color: PlayerColor,
    val tokens: List<Token>,
)

data class TokenRef(
    val playerColor: PlayerColor,
    val tokenId: Int,
)

data class MoveResult(
    val token: TokenRef,
    val path: List<BoardPosition>,
    val captured: List<TokenRef>,
    val finishedThisMove: Boolean,
)

data class TokenBoardPosition(
    val token: TokenRef,
    val position: BoardPosition,
    val zone: TokenZone,
)

data class GameState(
    val players: List<Player>,
    val currentPlayerIndex: Int,
    val diceValue: Int?,
    val boardPositions: List<TokenBoardPosition>,
    val winner: PlayerColor?,
    val lastMove: MoveResult?,
    val message: String,
) {
    val currentPlayer: Player get() = players[currentPlayerIndex]
}


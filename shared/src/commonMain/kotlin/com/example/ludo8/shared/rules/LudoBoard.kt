package com.example.ludo8.shared.rules

import com.example.ludo8.shared.model.BoardPosition
import com.example.ludo8.shared.model.PlayerColor
import com.example.ludo8.shared.model.Token
import com.example.ludo8.shared.model.TokenBoardPosition
import com.example.ludo8.shared.model.TokenRef
import com.example.ludo8.shared.model.TokenZone

internal object LudoBoard {
    private const val boardSize = 15
    const val trackLength = 52
    const val homeLaneLength = 6
    const val homeStartProgress = trackLength - 1
    const val finishedProgress = homeStartProgress + homeLaneLength

    private data class Cell(val x: Int, val y: Int)

    private enum class Corner { TopLeft, TopRight, BottomLeft, BottomRight }

    private fun Int.floorMod(mod: Int): Int {
        val r = this % mod
        return if (r < 0) r + mod else r
    }

    private data class ColorInfo(
        val color: PlayerColor,
        val baseCorner: Corner,
        val homeCorner: Corner,
        val startIndex: Int,
    )

    private val mainTrack: List<Cell> = listOf(
        Cell(6, 0),
        Cell(6, 1),
        Cell(6, 2),
        Cell(6, 3),
        Cell(6, 4),
        Cell(6, 5),
        Cell(5, 6),
        Cell(4, 6),
        Cell(3, 6),
        Cell(2, 6),
        Cell(1, 6),
        Cell(0, 6),
        Cell(0, 7),
        Cell(0, 8),
        Cell(1, 8),
        Cell(2, 8),
        Cell(3, 8),
        Cell(4, 8),
        Cell(5, 8),
        Cell(6, 9),
        Cell(6, 10),
        Cell(6, 11),
        Cell(6, 12),
        Cell(6, 13),
        Cell(6, 14),
        Cell(7, 14),
        Cell(8, 14),
        Cell(8, 13),
        Cell(8, 12),
        Cell(8, 11),
        Cell(8, 10),
        Cell(8, 9),
        Cell(9, 8),
        Cell(10, 8),
        Cell(11, 8),
        Cell(12, 8),
        Cell(13, 8),
        Cell(14, 8),
        Cell(14, 7),
        Cell(14, 6),
        Cell(13, 6),
        Cell(12, 6),
        Cell(11, 6),
        Cell(10, 6),
        Cell(9, 6),
        Cell(8, 5),
        Cell(8, 4),
        Cell(8, 3),
        Cell(8, 2),
        Cell(8, 1),
        Cell(8, 0),
        Cell(7, 0),
    )

    private val colorInfos: Map<PlayerColor, ColorInfo> = mapOf(
        PlayerColor.Yellow to ColorInfo(PlayerColor.Yellow, baseCorner = Corner.TopLeft, homeCorner = Corner.BottomLeft, startIndex = 10),
        PlayerColor.Blue to ColorInfo(PlayerColor.Blue, baseCorner = Corner.BottomLeft, homeCorner = Corner.BottomRight, startIndex = 23),
        PlayerColor.Red to ColorInfo(PlayerColor.Red, baseCorner = Corner.BottomRight, homeCorner = Corner.TopRight, startIndex = 36),
        PlayerColor.Green to ColorInfo(PlayerColor.Green, baseCorner = Corner.TopRight, homeCorner = Corner.TopLeft, startIndex = 49),
    )

    init {
        require(mainTrack.size == trackLength) {
            "Track length mismatch: expected $trackLength, got ${mainTrack.size}"
        }
    }

    val safeTrackIndices: Set<Int> = buildSet {
        colorInfos.values.forEach { info ->
            add(info.startIndex.floorMod(trackLength))
            add((info.startIndex - 8).floorMod(trackLength))
        }
    }

    internal fun safeBoardPositions(): List<BoardPosition> {
        return safeTrackIndices.toList().sorted().map { idx ->
            val cell = mainTrack[idx]
            BoardPosition(row = cell.y, col = cell.x)
        }
    }

    fun computeBoardPositions(
        players: List<com.example.ludo8.shared.model.Player>,
    ): List<TokenBoardPosition> {
        return players.flatMap { player ->
            player.tokens.map { token ->
                val ref = TokenRef(player.color, token.id)
                val position = tokenBoardPosition(player.color, token)
                val zone = tokenZone(token)
                TokenBoardPosition(
                    token = ref,
                    position = position,
                    zone = zone,
                )
            }
        }
    }

    fun tokenTrackIndex(playerColor: PlayerColor, token: Token): Int? {
        if (token.progress !in 0 until homeStartProgress) return null
        val start = colorInfos.getValue(playerColor).startIndex
        return (start - token.progress).floorMod(trackLength)
    }

    private fun tokenZone(token: Token): TokenZone {
        return when {
            token.progress < 0 -> TokenZone.Base
            token.progress < homeStartProgress -> TokenZone.Track
            token.progress < finishedProgress -> TokenZone.HomeLane
            else -> TokenZone.Finished
        }
    }

    fun tokenBoardPosition(playerColor: PlayerColor, token: Token): BoardPosition {
        val cell = tokenCell(playerColor, token)
        return BoardPosition(row = cell.y, col = cell.x)
    }

    private fun tokenCell(playerColor: PlayerColor, token: Token): Cell {
        val info = colorInfos.getValue(playerColor)
        return when {
            token.progress < 0 -> baseCells(info.baseCorner)[token.id.coerceIn(0, 3)]
            token.progress < homeStartProgress -> {
                val trackIndex = (info.startIndex - token.progress).floorMod(trackLength)
                mainTrack[trackIndex]
            }

            token.progress < finishedProgress -> {
                val homeIndex = (token.progress - homeStartProgress).coerceIn(0, homeLaneLength - 1)
                homeLaneCells(info.homeCorner)[homeIndex]
            }

            else -> Cell(7, 7)
        }
    }

    private fun baseCells(corner: Corner): List<Cell> {
        return when (corner) {
            Corner.TopLeft -> listOf(Cell(2, 2), Cell(4, 2), Cell(2, 4), Cell(4, 4))
            Corner.TopRight -> listOf(Cell(10, 2), Cell(12, 2), Cell(10, 4), Cell(12, 4))
            Corner.BottomRight -> listOf(Cell(10, 10), Cell(12, 10), Cell(10, 12), Cell(12, 12))
            Corner.BottomLeft -> listOf(Cell(2, 10), Cell(4, 10), Cell(2, 12), Cell(4, 12))
        }
    }

    private fun homeLaneCells(corner: Corner): List<Cell> {
        return when (corner) {
            Corner.TopLeft -> listOf(Cell(7, 1), Cell(7, 2), Cell(7, 3), Cell(7, 4), Cell(7, 5), Cell(7, 6))
            Corner.TopRight -> listOf(Cell(13, 7), Cell(12, 7), Cell(11, 7), Cell(10, 7), Cell(9, 7), Cell(8, 7))
            Corner.BottomRight -> listOf(Cell(7, 13), Cell(7, 12), Cell(7, 11), Cell(7, 10), Cell(7, 9), Cell(7, 8))
            Corner.BottomLeft -> listOf(Cell(1, 7), Cell(2, 7), Cell(3, 7), Cell(4, 7), Cell(5, 7), Cell(6, 7))
        }
    }
}

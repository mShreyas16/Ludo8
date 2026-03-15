package com.example.ludo8.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.ludo8.shared.model.BoardPosition
import com.example.ludo8.shared.model.PlayerColor
import com.example.ludo8.shared.model.TokenBoardPosition
import com.example.ludo8.shared.model.TokenRef
import com.example.ludo8.viewmodel.LudoGameViewModel
import kotlinx.coroutines.delay
import kotlin.math.min

@Composable
fun LudoApp() {
    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var playerCount by remember { mutableIntStateOf(2) }
            var started by remember { mutableStateOf(false) }
            var viewModel by remember { mutableStateOf<LudoGameViewModel?>(null) }

            if (!started) {
                StartScreen(
                    playerCount = playerCount,
                    onPlayerCountChange = { playerCount = it },
                    onStart = {
                        viewModel = LudoGameViewModel(playerCount)
                        started = true
                    },
                )
            } else {
                val vm = viewModel ?: return@Surface
                GameScreen(
                    viewModel = vm,
                    playerCount = playerCount,
                    onRestart = {
                        started = false
                        viewModel = null
                    },
                )
            }
        }
    }
}

@Composable
private fun StartScreen(
    playerCount: Int,
    onPlayerCountChange: (Int) -> Unit,
    onStart: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Ludo", style = MaterialTheme.typography.headlineMedium)
        Text("Select players", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(2, 3, 4).forEach { count ->
                Button(
                    onClick = { onPlayerCountChange(count) },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (playerCount == count) "✓ $count" else "$count")
                }
            }
        }
        Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
            Text("Start game")
        }
    }
}

@Composable
private fun GameScreen(
    viewModel: LudoGameViewModel,
    playerCount: Int,
    onRestart: () -> Unit,
) {
    val state = viewModel.state
    val dice = state.diceValue
    val validTokenIds = viewModel.validMoves()
    val movableTokens = remember(state.currentPlayer.color, validTokenIds) {
        validTokenIds.map { TokenRef(state.currentPlayer.color, it) }.toSet()
    }
    val animatedOverrides = remember { mutableStateMapOf<TokenRef, BoardPosition>() }
    val lastMove = state.lastMove
    val starPositions = remember(viewModel) { viewModel.starBoardPositions() }

    LaunchedEffect(lastMove) {
        val move = lastMove ?: return@LaunchedEffect
        val token = move.token
        move.path.forEach { p ->
            animatedOverrides[token] = p
            delay(110)
        }
        delay(220)
        animatedOverrides.remove(token)
        viewModel.consumeLastMove()
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Turn: ${state.currentPlayer.color}",
                modifier = Modifier.weight(1f),
            )
            Button(onClick = { viewModel.reset(playerCount) }) { Text("Restart") }
        }

        Text(state.message, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val diceScale = remember { Animatable(1f) }
            LaunchedEffect(dice) {
                if (dice != null) {
                    diceScale.snapTo(0.85f)
                    diceScale.animateTo(1f, tween(durationMillis = 220))
                }
            }
            Button(
                onClick = { viewModel.rollDice() },
                enabled = state.winner == null && state.diceValue == null,
                modifier = Modifier.weight(1f),
            ) {
                Text("Roll")
            }
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = dice?.toString() ?: "-", textAlign = TextAlign.Center)
            }
        }

        LudoBoard(
            positions = state.boardPositions,
            starPositions = starPositions,
            movableTokens = movableTokens,
            animatedOverrides = animatedOverrides,
            onTokenClick = { tokenRef ->
                if (state.winner != null) return@LudoBoard
                if (state.diceValue == null) return@LudoBoard
                if (tokenRef.playerColor != state.currentPlayer.color) return@LudoBoard
                if (tokenRef.tokenId in validTokenIds) viewModel.moveToken(tokenRef.tokenId)
            },
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(1.dp, MaterialTheme.colorScheme.outline),
        )

        Spacer(Modifier.height(16.dp))
    }

    val winner = state.winner
    if (winner != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Winner") },
            text = { Text("$winner wins!") },
            confirmButton = {
                Button(onClick = onRestart) { Text("New game") }
            },
        )
    }
}

@Composable
private fun LudoBoard(
    positions: List<TokenBoardPosition>,
    starPositions: List<BoardPosition>,
    movableTokens: Set<TokenRef>,
    animatedOverrides: Map<TokenRef, BoardPosition>,
    onTokenClick: (TokenRef) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.background(Color(0xFFF2F2F2))) {
        val cellDp = minOf(maxWidth, maxHeight) / 15f
        val tokenSizeDp = cellDp * 0.65f
        val tokenInsetDp = cellDp * 0.175f
        val density = LocalDensity.current
        val cellPx = with(density) { cellDp.toPx() }

        Canvas(modifier = Modifier.fillMaxSize()) {
            drawBoardGrid(cellPx)
            drawBoardZones(cellPx)
            drawStarCells(cellPx, starPositions)
        }

        val placements = positions.map { placed ->
            val token = placed.token
            val p = animatedOverrides[token] ?: placed.position
            TokenPlacement(token = token, position = p)
        }
        placements.groupBy { it.position }.values.forEach { stack ->
            val tokenToMove = stack.asSequence()
                .map { it.token }
                .filter { it in movableTokens }
                .sortedBy { it.tokenId }
                .firstOrNull()

            if (tokenToMove != null) {
                Box(
                    modifier = Modifier
                        .size(cellDp)
                        .cellOffset(cellDp, stack.first().position)
                        .clickable { onTokenClick(tokenToMove) },
                )
            }

            stack.forEachIndexed { index, placed ->
                val layout = tokenLayout(
                    index = index,
                    count = stack.size,
                    cellDp = cellDp,
                    tokenInsetDp = tokenInsetDp,
                    baseTokenSizeDp = tokenSizeDp,
                )
                TokenView(
                    color = placed.token.playerColor,
                    modifier = Modifier
                        .size(layout.size)
                        .tokenOffset(cellDp, placed.position, xInCell = layout.xInCell, yInCell = layout.yInCell),
                )
            }
        }
    }
}

private fun Modifier.cellOffset(
    cellDp: androidx.compose.ui.unit.Dp,
    p: BoardPosition,
): Modifier {
    val x = cellDp * p.col
    val y = cellDp * p.row
    return this.offset { IntOffset(x.roundToPx(), y.roundToPx()) }
}

private data class TokenPlacement(
    val token: TokenRef,
    val position: BoardPosition,
)

private data class TokenLayout(
    val size: androidx.compose.ui.unit.Dp,
    val xInCell: androidx.compose.ui.unit.Dp,
    val yInCell: androidx.compose.ui.unit.Dp,
)

private fun tokenLayout(
    index: Int,
    count: Int,
    cellDp: androidx.compose.ui.unit.Dp,
    tokenInsetDp: androidx.compose.ui.unit.Dp,
    baseTokenSizeDp: androidx.compose.ui.unit.Dp,
): TokenLayout {
    if (count <= 1) return TokenLayout(size = baseTokenSizeDp, xInCell = tokenInsetDp, yInCell = tokenInsetDp)

    val gap = cellDp * 0.06f
    val cols = when {
        count <= 4 -> 2
        count <= 9 -> 3
        else -> 4
    }
    val rows = ((count - 1) / cols) + 1

    val maxSize = baseTokenSizeDp * 0.56f
    val sizeByWidth = (cellDp - (gap * 2f) - (gap * (cols - 1).toFloat())) / cols.toFloat()
    val sizeByHeight = (cellDp - (gap * 2f) - (gap * (rows - 1).toFloat())) / rows.toFloat()
    val size = minOf(maxSize, sizeByWidth, sizeByHeight)

    val gridWidth = (size * cols.toFloat()) + (gap * (cols - 1).toFloat())
    val gridHeight = (size * rows.toFloat()) + (gap * (rows - 1).toFloat())
    val startX = (cellDp - gridWidth) / 2f
    val startY = (cellDp - gridHeight) / 2f

    val col = index % cols
    val row = index / cols
    val xInCell = startX + (size + gap) * col.toFloat()
    val yInCell = startY + (size + gap) * row.toFloat()

    return TokenLayout(size = size, xInCell = xInCell, yInCell = yInCell)
}

private fun Modifier.tokenOffset(
    cellDp: androidx.compose.ui.unit.Dp,
    p: BoardPosition,
    xInCell: androidx.compose.ui.unit.Dp,
    yInCell: androidx.compose.ui.unit.Dp,
): Modifier {
    val x = (cellDp * p.col) + xInCell
    val y = (cellDp * p.row) + yInCell
    return this.offset { IntOffset(x.roundToPx(), y.roundToPx()) }
}

@Composable
private fun TokenView(
    color: PlayerColor,
    modifier: Modifier = Modifier,
) {
    val fill = when (color) {
        PlayerColor.Red -> Color(0xFFC62828)
        PlayerColor.Blue -> Color(0xFF1565C0)
        PlayerColor.Green -> Color(0xFF2E7D32)
        PlayerColor.Yellow -> Color(0xFFF9A825)
    }
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(color = fill)
            drawCircle(color = Color.White, radius = size.minDimension / 2f, style = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.12f))
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoardGrid(cell: Float) {
    for (r in 0 until 15) {
        for (c in 0 until 15) {
            drawRect(
                color = Color(0xFFCCCCCC),
                topLeft = Offset(c * cell, r * cell),
                size = Size(cell, cell),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = cell * 0.03f),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBoardZones(cell: Float) {
    drawRect(Color(0xFFF9A825).copy(alpha = 0.14f), topLeft = Offset(0f, 0f), size = Size(6 * cell, 6 * cell))
    drawRect(Color(0xFF2E7D32).copy(alpha = 0.14f), topLeft = Offset(9 * cell, 0f), size = Size(6 * cell, 6 * cell))
    drawRect(Color(0xFFC62828).copy(alpha = 0.14f), topLeft = Offset(9 * cell, 9 * cell), size = Size(6 * cell, 6 * cell))
    drawRect(Color(0xFF1565C0).copy(alpha = 0.14f), topLeft = Offset(0f, 9 * cell), size = Size(6 * cell, 6 * cell))

    drawRect(Color.White, topLeft = Offset(6 * cell, 6 * cell), size = Size(3 * cell, 3 * cell))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStarCells(
    cell: Float,
    positions: List<BoardPosition>,
) {
    positions.forEach { p ->
        val center = Offset((p.col + 0.5f) * cell, (p.row + 0.5f) * cell)
        drawCircle(color = Color(0xFFFFD54F).copy(alpha = 0.25f), radius = cell * 0.30f, center = center)
        drawCircle(
            color = Color(0xFFF57F17).copy(alpha = 0.35f),
            radius = cell * 0.30f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = cell * 0.04f),
        )
    }
}

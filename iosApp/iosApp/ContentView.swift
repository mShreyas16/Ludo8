import SwiftUI
import Shared

struct ContentView: View {
    @State private var started = false
    @State private var playerCount = 2
    @StateObject private var viewModel = LudoViewModel(playerCount: 2)

    var body: some View {
        Group {
            if !started {
                startView
            } else {
                gameView
            }
        }
    }

    private var startView: some View {
        VStack(spacing: 16) {
            Text("Ludo").font(.largeTitle)
            Text("Select players").font(.headline)
            HStack(spacing: 12) {
                ForEach([2, 3, 4], id: \.self) { n in
                    Button(action: { playerCount = n }) {
                        Text(playerCount == n ? "✓ \(n)" : "\(n)")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                    }
                    .buttonStyle(.borderedProminent)
                }
            }
            Button("Start game") {
                viewModel.restart(playerCount: playerCount)
                started = true
            }
            .frame(maxWidth: .infinity)
            .buttonStyle(.borderedProminent)
        }
        .padding(16)
    }

    private var gameView: some View {
        let state = viewModel.state
        let winner = state.winner
        let diceValue = state.diceValue?.intValue

        return VStack(spacing: 12) {
            HStack {
                Text("Turn: \(currentPlayerColorLabel(state: state))")
                Spacer()
                Button("Restart") { started = false }
            }
            .padding(.horizontal, 16)

            Text(state.message)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 16)

            HStack(spacing: 12) {
                Button("Roll") { viewModel.roll() }
                    .disabled(winner != nil || state.diceValue != nil)
                    .frame(maxWidth: .infinity)
                    .buttonStyle(.borderedProminent)

                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color.gray.opacity(0.5), lineWidth: 1)
                        .background(RoundedRectangle(cornerRadius: 10).fill(Color.white))
                    Text(diceValue.map(String.init) ?? "-").font(.headline)
                }
                .frame(width: 56, height: 56)
            }
            .padding(.horizontal, 16)

            LudoBoardView(
                state: state,
                validMoves: viewModel.validMoveTokenIds(),
                onTapToken: { token in
                    guard winner == nil else { return }
                    guard state.diceValue != nil else { return }
                    guard token.playerColor == currentPlayerColor(state: state) else { return }
                    guard viewModel.validMoveTokenIds().contains(Int(token.tokenId)) else { return }
                    viewModel.move(tokenId: Int(token.tokenId))
                },
                onConsumeLastMove: { viewModel.consumeLastMove() }
            )
            .aspectRatio(1, contentMode: .fit)
            .padding(16)

            Spacer(minLength: 0)
        }
        .alert("Winner", isPresented: Binding(get: { winner != nil }, set: { _ in })) {
            Button("New game") { started = false }
        } message: {
            Text(winner != nil ? "\(winner!.description) wins!" : "")
        }
    }

    private func currentPlayerColor(state: SharedGameState) -> SharedPlayerColor {
        let players = state.players as NSArray
        let idx = Int(state.currentPlayerIndex)
        let player = players.object(at: idx) as! SharedPlayer
        return player.color
    }

    private func currentPlayerColorLabel(state: SharedGameState) -> String {
        "\(currentPlayerColor(state: state).description)"
    }
}

private final class LudoViewModel: ObservableObject {
    private let engine: SharedLudoGameEngine

    @Published var state: SharedGameState

    init(playerCount: Int) {
        self.engine = SharedLudoGameEngine(playerCount: Int32(playerCount))
        self.state = engine.state
    }

    func restart(playerCount: Int) {
        state = engine.reset(playerCount: Int32(playerCount))
    }

    func roll() {
        state = engine.rollDice()
    }

    func move(tokenId: Int) {
        state = engine.moveToken(tokenId: Int32(tokenId))
    }

    func consumeLastMove() {
        state = engine.consumeLastMove()
    }

    func validMoveTokenIds() -> Set<Int> {
        let arr = engine.validMoveTokenIds()
        var s = Set<Int>()
        for i in 0..<arr.size {
            s.insert(Int(arr.get(index: i)))
        }
        return s
    }
}

private struct LudoBoardView: View {
    let state: SharedGameState
    let validMoves: Set<Int>
    let onTapToken: (SharedTokenRef) -> Void
    let onConsumeLastMove: () -> Void

    @State private var overrides: [String: BoardPos] = [:]

    var body: some View {
        GeometryReader { geo in
            let side = min(geo.size.width, geo.size.height)
            let cell = side / 15.0
            let tokenSize = cell * 0.65
            let inset = cell * 0.175

            ZStack(alignment: .topLeading) {
                boardBackground(cell: cell)

                ForEach(tokenPlacements(), id: \.key) { placed in
                    let displayPos = overrides[placed.key] ?? placed.pos
                    TokenView(color: placed.color, enabled: placed.enabled)
                        .frame(width: tokenSize, height: tokenSize)
                        .position(
                            x: CGFloat(displayPos.col) * cell + inset + tokenSize / 2,
                            y: CGFloat(displayPos.row) * cell + inset + tokenSize / 2
                        )
                        .onTapGesture { onTapToken(placed.token) }
                }
            }
        }
        .task(id: lastMoveKey()) {
            guard let move = state.lastMove else { return }
            await animate(move: move)
            onConsumeLastMove()
        }
    }

    private func lastMoveKey() -> String {
        guard let move = state.lastMove else { return "" }
        let token = move.token
        return "\(token.playerColor.description)-\(token.tokenId)"
    }

    private func animate(move: SharedMoveResult) async {
        let token = move.token
        let key = tokenKey(token)
        let path = move.path as NSArray
        for item in path {
            guard let p = item as? SharedBoardPosition else { continue }
            overrides[key] = BoardPos(row: Int(p.row), col: Int(p.col))
            try? await Task.sleep(nanoseconds: 70_000_000)
        }
        try? await Task.sleep(nanoseconds: 120_000_000)
        overrides.removeValue(forKey: key)
    }

    private func tokenPlacements() -> [PlacedToken] {
        let currentColor = currentPlayerColor()
        let diceExists = state.diceValue != nil
        let positions = state.boardPositions as NSArray

        var out: [PlacedToken] = []
        for item in positions {
            guard let placed = item as? SharedTokenBoardPosition else { continue }
            let token = placed.token
            let row = Int(placed.position.row)
            let col = Int(placed.position.col)
            let enabled = diceExists && token.playerColor == currentColor && validMoves.contains(Int(token.tokenId))
            out.append(
                PlacedToken(
                    token: token,
                    color: token.playerColor,
                    pos: BoardPos(row: row, col: col),
                    key: tokenKey(token),
                    enabled: enabled
                )
            )
        }
        return out
    }

    private func tokenKey(_ token: SharedTokenRef) -> String {
        "\(token.playerColor.description)-\(token.tokenId)"
    }

    private func currentPlayerColor() -> SharedPlayerColor {
        let players = state.players as NSArray
        let idx = Int(state.currentPlayerIndex)
        let player = players.object(at: idx) as! SharedPlayer
        return player.color
    }

    @ViewBuilder
    private func boardBackground(cell: CGFloat) -> some View {
        ZStack(alignment: .topLeading) {
            Rectangle().fill(Color(white: 0.95))

            Rectangle()
                .fill(colorFor(.yellow).opacity(0.14))
                .frame(width: cell * 6, height: cell * 6)
                .position(x: cell * 3, y: cell * 3)

            Rectangle()
                .fill(colorFor(.green).opacity(0.14))
                .frame(width: cell * 6, height: cell * 6)
                .position(x: cell * 12, y: cell * 3)

            Rectangle()
                .fill(colorFor(.red).opacity(0.14))
                .frame(width: cell * 6, height: cell * 6)
                .position(x: cell * 12, y: cell * 12)

            Rectangle()
                .fill(colorFor(.blue).opacity(0.14))
                .frame(width: cell * 6, height: cell * 6)
                .position(x: cell * 3, y: cell * 12)

            Rectangle()
                .fill(Color.white)
                .frame(width: cell * 3, height: cell * 3)
                .position(x: cell * 7.5, y: cell * 7.5)

            Path { path in
                for r in 0..<15 {
                    for c in 0..<15 {
                        let rect = CGRect(x: CGFloat(c) * cell, y: CGFloat(r) * cell, width: cell, height: cell)
                        path.addRect(rect)
                    }
                }
            }
            .stroke(Color.gray.opacity(0.4), lineWidth: cell * 0.03)
        }
        .clipShape(Rectangle())
    }
}

private struct TokenView: View {
    let color: SharedPlayerColor
    let enabled: Bool

    var body: some View {
        let fill = colorFor(color)
        Circle()
            .fill(fill.opacity(enabled ? 1.0 : 0.55))
            .overlay(Circle().stroke(Color.white, lineWidth: 3))
            .shadow(radius: enabled ? 2 : 0)
    }
}

private struct BoardPos: Hashable {
    let row: Int
    let col: Int
}

private struct PlacedToken {
    let token: SharedTokenRef
    let color: SharedPlayerColor
    let pos: BoardPos
    let key: String
    let enabled: Bool
}

private func colorFor(_ c: SharedPlayerColor) -> Color {
    switch c {
    case .red:
        return Color(red: 0.78, green: 0.16, blue: 0.16)
    case .blue:
        return Color(red: 0.08, green: 0.35, blue: 0.75)
    case .green:
        return Color(red: 0.18, green: 0.55, blue: 0.22)
    case .yellow:
        return Color(red: 0.97, green: 0.65, blue: 0.15)
    default:
        return .gray
    }
}

package net.zodac.ludo.data.game

import net.zodac.ludo.data.JsonArray
import net.zodac.ludo.data.JsonNumber
import net.zodac.ludo.data.JsonObject
import net.zodac.ludo.data.JsonParseException
import net.zodac.ludo.data.array
import net.zodac.ludo.data.boolean
import net.zodac.ludo.data.buildJsonObject
import net.zodac.ludo.data.flag
import net.zodac.ludo.data.int
import net.zodac.ludo.data.intOrNull
import net.zodac.ludo.data.ints
import net.zodac.ludo.data.jsonInts
import net.zodac.ludo.data.long
import net.zodac.ludo.data.longs
import net.zodac.ludo.data.obj
import net.zodac.ludo.data.objOrNull
import net.zodac.ludo.data.objectList
import net.zodac.ludo.data.parseJson
import net.zodac.ludo.data.string
import net.zodac.ludo.data.toJson
import net.zodac.ludo.game.GameEvent
import net.zodac.ludo.game.GameState
import net.zodac.ludo.game.LudoEngine
import net.zodac.ludo.game.Move
import net.zodac.ludo.game.PawnRef
import net.zodac.ludo.game.PlayerState
import net.zodac.ludo.game.RollOff
import net.zodac.ludo.game.TurnPhase
import net.zodac.ludo.model.Difficulty
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.GameRules
import net.zodac.ludo.model.PawnPosition
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.PlayerConfig
import net.zodac.ludo.model.PlayerType
import net.zodac.ludo.model.StoredId
import net.zodac.ludo.model.storedIdOf
import net.zodac.ludo.ui.game.StartedGame

/**
 * The saved form of a game in progress ([StartedGame]), as JSON. Hand-written over the app's own [JsonObject] tree.
 *
 * The rules aren't written: a game follows its mode's ([GameMode.rules]) until modifiers come in Phase 9, which add
 * them under a new [VERSION]. The one setting that changes them, [GameRules.stopAtFirstWinner], is written when on. A roll waiting for a move is written as the roll alone; its moves are worked out again.
 * Anything that doesn't make a playable game fails [decode] with an [IllegalArgumentException] (a [JsonParseException]
 * for JSON that can't be read).
 */
object SavedGameJson {
    /** The format written. One from a newer app, with a higher version, isn't read. */
    const val VERSION = 1

    fun encode(game: StartedGame): String = buildJsonObject {
        put("version", VERSION)
        put("mode", game.mode.id)
        put("startedAt", game.startedAtEpochMillis)
        put("playMillis", game.playMillis)
        // Each finished player's finish time, keyed by player index.
        put("finishMillis", JsonObject(game.finishMillis.entries.associate { (player, millis) -> player.toString() to JsonNumber(millis) }))
        put("players", JsonArray(game.players.map(::encodePlayer)))
        put("rollOff", encodeRollOff(game.rollOff))
        if (game.state.rules.stopAtFirstWinner) put("stopAtFirstWinner", true)
        put("state", encodeState(game.state))
    }.toJson()

    fun decode(json: String): StartedGame {
        val obj = parseJson(json) as? JsonObject ?: throw JsonParseException("Not a JSON object")
        val version = obj.int("version")
        require(version in 1..VERSION) { "Saved game version $version isn't one this app reads" }
        val mode = storedId<GameMode>(obj.string("mode"))
        val players = obj.objectList("players").map(::decodePlayer)
        val rollOff = decodeRollOff(obj.obj("rollOff"), players.size)
        val rules = mode.rules.copy(stopAtFirstWinner = obj.flag("stopAtFirstWinner"))
        val state = decodeState(obj.obj("state"), mode, rules, players.size)
        val playMillis = obj.long("playMillis")
        require(playMillis >= 0) { "Negative play time" }
        val finishMillis = obj.obj("finishMillis").longs().entries.associate { (player, millis) ->
            val index = player.toIntOrNull()?.takeIf { it in players.indices } ?: throw JsonParseException("No player \"$player\"")
            require(state.players[index].isFinished(state.board) && millis in 0..playMillis) { "Player $index's finish time doesn't fit" }
            index to millis
        }
        require(finishMillis.keys == state.finishingOrder.toSet()) { "A finished player has no finish time" }
        return StartedGame(
            mode,
            players,
            state,
            rollOff,
            startedAtEpochMillis = obj.long("startedAt"),
            playMillis = playMillis,
            finishMillis = finishMillis,
        )
    }

    private fun encodePlayer(player: PlayerConfig): JsonObject = buildJsonObject {
        put("slot", player.slot)
        put("type", player.type.id)
        put("name", player.name)
        put("difficulty", player.difficulty.id)
        put("colour", player.colour.id)
    }

    private fun decodePlayer(obj: JsonObject): PlayerConfig = PlayerConfig(
        slot = obj.int("slot"),
        type = storedId(obj.string("type")),
        name = obj.string("name"),
        difficulty = storedId<Difficulty>(obj.string("difficulty")),
        colour = storedId<PlayerColour>(obj.string("colour")),
    )

    private fun encodeRollOff(rollOff: RollOff): JsonObject = buildJsonObject {
        // Each round as an object of rolls keyed by player index.
        put("rounds", JsonArray(rollOff.rounds.map { round -> JsonObject(round.entries.associate { (player, roll) -> player.toString() to JsonNumber(roll) }) }))
        put("first", rollOff.first)
    }

    private fun decodeRollOff(obj: JsonObject, playerCount: Int): RollOff {
        val rounds = obj.objectList("rounds").map { round ->
            round.ints().entries.associate { (player, roll) ->
                (player.toIntOrNull()?.takeIf { it in 0..<playerCount } ?: throw JsonParseException("No player \"$player\"")) to roll
            }
        }
        val first = obj.int("first")
        require(first in 0..<playerCount) { "No player $first to go first" }
        return RollOff(rounds, first)
    }

    private fun encodeState(state: GameState): JsonObject = buildJsonObject {
        put(
            "players",
            JsonArray(
                state.players.map { player ->
                    buildJsonObject {
                        put("seat", player.seat)
                        put("pawns", jsonInts(player.pawns))
                        put("turns", player.turns)
                        put("captures", player.captures)
                        put("timesCaptured", player.timesCaptured)
                    }
                },
            ),
        )
        put("current", state.current)
        // The roll waiting for a move, or null while waiting for a roll; a finished game is never saved.
        put("roll", (state.phase as? TurnPhase.AwaitingMove)?.roll)
        put("rollAgainRun", state.rollAgainRun)
        put("finishingOrder", jsonInts(state.finishingOrder))
        state.lastEvent?.let { put("lastEvent", encodeEvent(it)) }
    }

    private fun decodeState(obj: JsonObject, mode: GameMode, rules: GameRules, playerCount: Int): GameState {
        val board = rules.board
        val players = obj.objectList("players").map { player ->
            PlayerState(
                seat = player.int("seat"),
                pawns = player.array("pawns").ints(),
                turns = player.int("turns"),
                captures = player.int("captures"),
                timesCaptured = player.int("timesCaptured"),
            )
        }
        require(players.size == playerCount) { "${players.size} players' states for $playerCount players" }
        require(players.map { it.seat } == players.indices.toList()) { "Players aren't at their own seats" }
        players.forEach { player ->
            require(player.pawns.size == rules.pawnsPerPlayer) { "A player has ${player.pawns.size} pawns" }
            require(player.pawns.all { it in PawnPosition.YARD_PROGRESS..board.homeProgress }) { "A pawn is off the board" }
            require(player.turns >= 0 && player.captures >= 0 && player.timesCaptured >= 0) { "A negative count" }
        }
        val current = obj.int("current")
        require(current in 0..<playerCount) { "No player $current to play" }
        val finishingOrder = obj.array("finishingOrder").ints()
        require(finishingOrder.distinct() == finishingOrder && finishingOrder.all { players.getOrNull(it)?.isFinished(board) == true }) {
            "The finishing order isn't the finished players"
        }
        require(players.indices.filter { players[it].isFinished(board) }.toSet() == finishingOrder.toSet()) { "A finished player isn't placed" }
        require(finishingOrder.size < playerCount - 1) { "A finished game is never saved" }
        require(current !in finishingOrder) { "A finished player has the turn" }
        val waiting = GameState(
            mode = mode,
            rules = rules,
            players = players,
            current = current,
            phase = TurnPhase.AwaitingRoll,
            rollAgainRun = obj.int("rollAgainRun"),
            finishingOrder = finishingOrder,
            lastEvent = obj.objOrNull("lastEvent")?.let { decodeEvent(it, playerCount) },
        )
        require(waiting.rollAgainRun >= 0 && rules.rollAgainLimit?.let { waiting.rollAgainRun < it } != false) { "Rolled again too often" }
        val roll = obj.intOrNull("roll") ?: return waiting
        require(roll in 1..rules.dieFaces) { "A roll of $roll" }
        val moves = LudoEngine.legalMoves(waiting, roll)
        require(moves.isNotEmpty()) { "A roll of $roll waiting for a move has none" }
        return waiting.copy(phase = TurnPhase.AwaitingMove(roll, moves))
    }

    private fun encodeEvent(event: GameEvent): JsonObject = buildJsonObject {
        val (kind, roll) = when (event) {
            is GameEvent.Rolled -> "rolled" to event.roll
            is GameEvent.NoMove -> "noMove" to event.roll
            is GameEvent.RollAgainLimit -> "rollAgainLimit" to event.roll
            is GameEvent.Moved -> "moved" to event.roll
        }
        put("kind", kind)
        put("player", event.player)
        put("roll", roll)
        if (event is GameEvent.Moved) {
            put("pawn", event.move.pawn)
            put("from", event.move.from)
            put("to", event.move.to)
            put("captures", JsonArray(event.move.captures.map { jsonInts(listOf(it.player, it.pawn)) }))
            put("finished", event.finished)
        }
    }

    private fun decodeEvent(obj: JsonObject, playerCount: Int): GameEvent {
        val player = obj.int("player")
        require(player in 0..<playerCount) { "No player $player acted" }
        val roll = obj.int("roll")
        return when (val kind = obj.string("kind")) {
            "rolled" -> GameEvent.Rolled(player, roll)
            "noMove" -> GameEvent.NoMove(player, roll)
            "rollAgainLimit" -> GameEvent.RollAgainLimit(player, roll)
            "moved" -> GameEvent.Moved(
                player = player,
                roll = roll,
                move = Move(
                    pawn = obj.int("pawn"),
                    from = obj.int("from"),
                    to = obj.int("to"),
                    captures = obj.array("captures").items.map { item ->
                        val pair = (item as? JsonArray)?.ints()?.takeIf { it.size == 2 } ?: throw JsonParseException("A capture isn't a pair")
                        PawnRef(pair[0], pair[1])
                    },
                ),
                finished = obj.boolean("finished"),
            )
            else -> throw JsonParseException("Unknown event \"$kind\"")
        }
    }

    private inline fun <reified E> storedId(id: String): E where E : Enum<E>, E : StoredId =
        storedIdOf<E>(id) ?: throw JsonParseException("Unknown ${E::class.simpleName} \"$id\"")
}

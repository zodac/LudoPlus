package net.zodac.ludoplus.data.game

import net.zodac.ludoplus.data.JsonArray
import net.zodac.ludoplus.data.JsonNumber
import net.zodac.ludoplus.data.JsonObject
import net.zodac.ludoplus.data.JsonParseException
import net.zodac.ludoplus.data.JsonString
import net.zodac.ludoplus.data.array
import net.zodac.ludoplus.data.boolean
import net.zodac.ludoplus.data.buildJsonObject
import net.zodac.ludoplus.data.flag
import net.zodac.ludoplus.data.int
import net.zodac.ludoplus.data.intOrNull
import net.zodac.ludoplus.data.ints
import net.zodac.ludoplus.data.jsonInts
import net.zodac.ludoplus.data.long
import net.zodac.ludoplus.data.longs
import net.zodac.ludoplus.data.obj
import net.zodac.ludoplus.data.objOrNull
import net.zodac.ludoplus.data.objectList
import net.zodac.ludoplus.data.parseJson
import net.zodac.ludoplus.data.string
import net.zodac.ludoplus.data.stringOrNull
import net.zodac.ludoplus.data.stringList
import net.zodac.ludoplus.data.toJson
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.game.PlayerState
import net.zodac.ludoplus.game.RollOff
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.GameRules
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.StoredId
import net.zodac.ludoplus.model.storedIdOf
import net.zodac.ludoplus.model.withHouseRules
import net.zodac.ludoplus.ui.game.StartedGame

/**
 * The saved form of a game in progress ([StartedGame]), as JSON. Hand-written over the app's own [JsonObject] tree.
 *
 * The rules aren't written whole: a game follows its mode's ([GameMode.rules]), changed by what is written: the house
 * rules on, by id ([GameRules.houseRules], from [VERSION] 2), the teams, and [GameRules.stopAtFirstWinner] when on. A
 * roll waiting for a move is written as the roll alone; its moves are worked out again.
 * Anything that doesn't make a playable game fails [decode] with an [IllegalArgumentException] (a [JsonParseException]
 * for JSON that can't be read).
 */
object SavedGameJson {
    /** The format written. One from a newer app, with a higher version, isn't read. */
    const val VERSION = 2

    fun encode(game: StartedGame): String = buildJsonObject {
        put("version", VERSION)
        put("mode", game.mode.id)
        put("startedAt", game.startedAtEpochMillis)
        put("playMillis", game.playMillis)
        // Each finished player's finish time, keyed by player index.
        put("finishMillis", JsonObject(game.finishMillis.entries.associate { (player, millis) -> player.toString() to JsonNumber(millis) }))
        put("players", JsonArray(game.players.map(::encodePlayer)))
        put("rollOff", encodeRollOff(game.rollOff))
        // A custom mode's name, for the game to be shown under; absent for a built-in mode.
        game.customName?.let { put("customName", it) }
        // The game's id, for telling its copies apart from other games' in a merge; absent for a game without one.
        game.id?.let { put("id", it) }
        put("steps", game.steps)
        if (game.state.rules.stopAtFirstWinner) put("stopAtFirstWinner", true)
        // The house rules on, in their own order; absent for none.
        if (game.state.rules.hasHouseRules) put("houseRules", JsonArray(HouseRule.entries.filter { it in game.state.rules.houseRules }.map { JsonString(it.id) }))
        // A team game's teams and their two options; absent for a game without teams.
        game.state.rules.teams?.let { teams ->
            put("teams", jsonInts(teams))
            put("partnerCaptures", game.state.rules.partnerCaptures)
            put("rollForPartners", game.state.rules.rollForPartners)
        }
        put("state", encodeState(game.state))
    }.toJson()

    fun decode(json: String): StartedGame {
        val obj = parseJson(json) as? JsonObject ?: throw JsonParseException("Not a JSON object")
        val version = obj.int("version")
        require(version in 1..VERSION) { "Saved game version $version isn't one this app reads" }
        val mode = storedId<GameMode>(obj.string("mode"))
        val players = obj.objectList("players").map(::decodePlayer)
        val rollOff = decodeRollOff(obj.obj("rollOff"), players.size)
        val teams = if ("teams" in obj) obj.array("teams").ints() else null
        require(teams == null || teams.size == players.size) { "${teams?.size} teams for ${players.size} players" }
        val rules = mode.rules.forPlayers(players.size).copy(
            stopAtFirstWinner = obj.flag("stopAtFirstWinner"),
            teams = teams,
            partnerCaptures = teams != null && obj.boolean("partnerCaptures"),
            rollForPartners = teams == null || obj.boolean("rollForPartners"),
        ).withHouseRules((if ("houseRules" in obj) obj.stringList("houseRules").map { storedId<HouseRule>(it) }.toSet() else emptySet()))
        val state = decodeState(obj.obj("state"), mode, rules, players.size)
        val playMillis = obj.long("playMillis")
        require(playMillis >= 0) { "Negative play time" }
        val finishMillis = obj.obj("finishMillis").longs().entries.associate { (player, millis) ->
            val index = player.toIntOrNull()?.takeIf { it in players.indices } ?: throw JsonParseException("No player \"$player\"")
            require((state.isFinished || state.players[index].isFinished(state.board)) && millis in 0..playMillis) { "Player $index's finish time doesn't fit" }
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
            customName = obj.stringOrNull("customName"),
            id = obj.stringOrNull("id"),
            steps = obj.intOrNull("steps")?.also { require(it >= 0) { "Negative steps" } } ?: 0,
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
        // The roll waiting for a move, or null while waiting for a roll; a finished game is never saved. With two dice, the
        // dice still to move, and whether the roll has earned another once they are.
        val waiting = state.phase as? TurnPhase.AwaitingMove
        put("roll", waiting?.roll)
        if (waiting != null && state.rules.dice > 1) {
            put("dice", jsonInts(waiting.dice))
            put("rolled", jsonInts(waiting.rolled))
            if (waiting.earned) put("earned", true)
        }
        put("rollAgainRun", state.rollAgainRun)
        if (state.rollingAgain) put("rollingAgain", true)
        if (state.yardTriesUsed > 0) put("yardTriesUsed", state.yardTriesUsed)
        put("finishingOrder", jsonInts(state.finishingOrder))
        // Over: never saved, but a Nearby host sends its guests the game's end (`.claude/NETPLAY.md`).
        if (state.isFinished) put("finished", true)
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
        require(players.map { it.seat }.let { seats -> seats.zipWithNext().all { (a, b) -> a < b } && seats.all { it in 0..<board.seats } }) {
            "The players aren't seated clockwise round the board"
        }
        players.forEach { player ->
            require(player.pawns.size == rules.pawnsPerPlayer) { "A player has ${player.pawns.size} pawns" }
            require(player.pawns.all { it in PawnPosition.YARD_PROGRESS..board.homeProgress }) { "A pawn is off the board" }
            require(player.turns >= 0 && player.captures >= 0 && player.timesCaptured >= 0) { "A negative count" }
        }
        val current = obj.int("current")
        require(current in 0..<playerCount) { "No player $current to play" }
        val finishingOrder = obj.array("finishingOrder").ints()
        // An ended game: every player placed (the last ones as they stood, where it stopped at its first winner).
        if (obj.flag("finished")) {
            require(finishingOrder.sorted() == players.indices.toList()) { "An ended game hasn't placed every player" }
            return GameState(
                mode = mode,
                rules = rules,
                players = players,
                current = current,
                phase = TurnPhase.Finished,
                finishingOrder = finishingOrder,
                lastEvent = obj.objOrNull("lastEvent")?.let { decodeEvent(it, playerCount) },
            )
        }
        require(finishingOrder.distinct() == finishingOrder && finishingOrder.all { players.getOrNull(it)?.isFinished(board) == true }) {
            "The finishing order isn't the finished players"
        }
        // The placed players are those of every team that's home (a player alone is a team of one), and only them.
        val members = players.indices.groupBy(rules::teamOf)
        val homeTeams = members.filterValues { team -> team.all { players[it].isFinished(board) } }.keys
        require(finishingOrder.toSet() == homeTeams.flatMap { members.getValue(it) }.toSet()) { "The finishing order isn't the players home" }
        require(members.keys.count { it !in homeTeams } > 1) { "A finished game is never saved" }
        require(current !in finishingOrder) { "A finished player has the turn" }
        require(!(players[current].isFinished(board) && (rules.teams == null || !rules.rollForPartners))) { "A player with every pawn home has the turn" }
        val waiting = GameState(
            mode = mode,
            rules = rules,
            players = players,
            current = current,
            phase = TurnPhase.AwaitingRoll,
            rollAgainRun = obj.int("rollAgainRun"),
            rollingAgain = obj.flag("rollingAgain"),
            yardTriesUsed = obj.intOrNull("yardTriesUsed") ?: 0,
            finishingOrder = finishingOrder,
            lastEvent = obj.objOrNull("lastEvent")?.let { decodeEvent(it, playerCount) },
        )
        require(waiting.rollAgainRun >= 0 && rules.rollAgainLimit?.let { waiting.rollAgainRun < it } != false) { "Rolled again too often" }
        require(waiting.yardTriesUsed in 0..<rules.yardTries) { "Tried to leave the yard too often" }
        val roll = obj.intOrNull("roll") ?: return waiting
        val dice = if (rules.dice > 1) obj.array("dice").ints() else listOf(roll)
        require(dice.isNotEmpty() && dice.size <= rules.dice && dice.first() == roll && dice.all { it in 1..rules.dieFaces }) { "A roll of $dice" }
        val moves = LudoEngine.movesForDice(waiting, dice)
        require(moves.isNotEmpty()) { "A roll of $dice waiting for a move has none" }
        // One die's roll has earned another if it's a roll-again roll; two dice say so.
        val earned = if (rules.dice > 1) obj.flag("earned") else roll in rules.rollAgainRolls
        val rolled = if (rules.dice > 1) obj.array("rolled").ints() else dice
        require(rolled.size == rules.dice && dice.all { it in rolled }) { "Dice $dice aren't of the roll $rolled" }
        return waiting.copy(phase = TurnPhase.AwaitingMove(roll, moves, dice = dice, earned = earned, rolled = rolled))
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
        val second = when (event) {
            is GameEvent.Rolled -> event.second
            is GameEvent.NoMove -> event.second
            is GameEvent.RollAgainLimit -> event.second
            is GameEvent.Moved -> null
        }
        second?.let { put("second", it) }
        if (event is GameEvent.NoMove && event.triesLeft > 0) put("triesLeft", event.triesLeft)
        if (event is GameEvent.Moved) {
            put("pawn", event.move.pawn)
            put("from", event.move.from)
            put("to", event.move.to)
            put("captures", JsonArray(event.move.captures.map { jsonInts(listOf(it.player, it.pawn)) }))
            event.move.owner?.let { put("owner", it) }
            event.move.roll?.let { put("die", it) }
            put("finished", event.finished)
            if (event.rollsAgain) put("rollsAgain", true)
        }
    }

    private fun decodeEvent(obj: JsonObject, playerCount: Int): GameEvent {
        val player = obj.int("player")
        require(player in 0..<playerCount) { "No player $player acted" }
        val roll = obj.int("roll")
        return when (val kind = obj.string("kind")) {
            "rolled" -> GameEvent.Rolled(player, roll, obj.intOrNull("second"))
            "noMove" -> GameEvent.NoMove(player, roll, triesLeft = obj.intOrNull("triesLeft") ?: 0, second = obj.intOrNull("second"))
            "rollAgainLimit" -> GameEvent.RollAgainLimit(player, roll, obj.intOrNull("second"))
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
                    owner = obj.intOrNull("owner")?.also { require(it in 0..<playerCount) { "No player $it owns the pawn" } },
                    roll = obj.intOrNull("die"),
                ),
                finished = obj.boolean("finished"),
                rollsAgain = obj.flag("rollsAgain"),
            )
            else -> throw JsonParseException("Unknown event \"$kind\"")
        }
    }

    private inline fun <reified E> storedId(id: String): E where E : Enum<E>, E : StoredId =
        storedIdOf<E>(id) ?: throw JsonParseException("Unknown ${E::class.simpleName} \"$id\"")
}

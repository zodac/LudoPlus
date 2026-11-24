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
import net.zodac.ludoplus.game.Cataclysm
import net.zodac.ludoplus.game.GameEvent
import net.zodac.ludoplus.game.GameState
import net.zodac.ludoplus.game.GhostMove
import net.zodac.ludoplus.game.Ghosts
import net.zodac.ludoplus.game.LogEntry
import net.zodac.ludoplus.game.MoveLog
import net.zodac.ludoplus.game.LudoEngine
import net.zodac.ludoplus.game.Move
import net.zodac.ludoplus.game.PawnRef
import net.zodac.ludoplus.game.PlayerState
import net.zodac.ludoplus.game.PowerUpEffect
import net.zodac.ludoplus.game.TurnPhase
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.GameRules
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.PawnPosition
import net.zodac.ludoplus.model.PlayerColour
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.PowerUp
import net.zodac.ludoplus.model.StoredId
import net.zodac.ludoplus.model.storedIdOf
import net.zodac.ludoplus.model.withHouseRules
import net.zodac.ludoplus.ui.game.StartedGame

/**
 * The saved form of a game in progress ([StartedGame]), as JSON. Hand-written over the app's own [JsonObject] tree.
 *
 * The rules aren't written whole: a game follows its mode's ([GameMode.rules]), changed by what is written: the house
 * rules on, by id ([GameRules.houseRules], from [VERSION] 2), the pawns each where a house rule says, the teams, and [GameRules.stopAtFirstWinner] when on. A
 * roll waiting for a move is written as the roll alone; its moves are worked out again.
 * Anything that doesn't make a playable game fails [decode] with an [IllegalArgumentException] (a [JsonParseException]
 * for JSON that can't be read). Its move log ([StartedGame.log]) isn't needed to play: one that can't be read is dropped.
 */
object SavedGameJson {
    /** The format written. One from a newer app, with a higher version, isn't read. */
    const val VERSION = 2

    /** [game] as JSON, with its move log unless [withLog] is false (a Nearby message, kept small). */
    fun encode(game: StartedGame, withLog: Boolean = true): String = buildJsonObject {
        put("version", VERSION)
        put("mode", game.mode.id)
        put("startedAt", game.startedAtEpochMillis)
        put("playMillis", game.playMillis)
        // Each finished player's finish time, keyed by player index.
        put("finishMillis", JsonObject(game.finishMillis.entries.associate { (player, millis) -> player.toString() to JsonNumber(millis) }))
        put("players", JsonArray(game.players.map(::encodePlayer)))
        // Who went first, in the shape of the roll-off that once chose them (with no rounds), which 1.0.1 and older need.
        put("rollOff", buildJsonObject {
            put("rounds", JsonArray(emptyList()))
            put("first", game.first)
        })
        // A custom mode's name, for the game to be shown under; absent for a built-in mode.
        game.customName?.let { put("customName", it) }
        // The game's id, for telling its copies apart from other games' in a merge; absent for a game without one.
        game.id?.let { put("id", it) }
        put("steps", game.steps)
        if (game.state.rules.stopAtFirstWinner) put("stopAtFirstWinner", true)
        // The house rules on, in their own order; absent for none.
        if (game.state.rules.houseRules.isNotEmpty()) put("houseRules", JsonArray(HouseRule.entries.filter { it in game.state.rules.houseRules }.map { JsonString(it.id) }))
        if (game.state.rules.pawnsChanged) put("pawns", game.state.rules.pawnsPerPlayer)
        // A team game's teams and their two options; absent for a game without teams.
        game.state.rules.teams?.let { teams ->
            put("teams", jsonInts(teams))
            put("partnerCaptures", game.state.rules.partnerCaptures)
            put("rollForPartners", game.state.rules.rollForPartners)
        }
        put("state", encodeState(game.state))
        // Every move so far, first first; absent for none, and in a Nearby message.
        if (withLog && game.log.isNotEmpty()) put("log", JsonArray(game.log.map(::encodeLogEntry)))
    }.toJson()

    fun decode(json: String): StartedGame {
        val obj = parseJson(json) as? JsonObject ?: throw JsonParseException("Not a JSON object")
        val version = obj.int("version")
        require(version in 1..VERSION) { "Saved game version $version isn't one this app reads" }
        val mode = storedId<GameMode>(obj.string("mode"))
        val players = obj.objectList("players").map(::decodePlayer)
        val first = obj.obj("rollOff").int("first")
        require(first in players.indices) { "No player $first to go first" }
        val teams = if ("teams" in obj) obj.array("teams").ints() else null
        require(teams == null || teams.size == players.size) { "${teams?.size} teams for ${players.size} players" }
        val rules = mode.rules.forPlayers(players.size).copy(
            stopAtFirstWinner = obj.flag("stopAtFirstWinner"),
            teams = teams,
            partnerCaptures = teams != null && obj.boolean("partnerCaptures"),
            rollForPartners = teams == null || obj.boolean("rollForPartners"),
        ).withHouseRules((if ("houseRules" in obj) obj.stringList("houseRules").map { storedId<HouseRule>(it) }.toSet() else emptySet()))
            .withPawns(obj.intOrNull("pawns")?.also { require(it in GameRules.PAWN_CHOICES) { "$it pawns each" } })
        val state = decodeState(obj.obj("state"), mode, rules, players.size)
        val playMillis = obj.long("playMillis")
        require(playMillis >= 0) { "Negative play time" }
        val finishMillis = obj.obj("finishMillis").longs().entries.associate { (player, millis) ->
            val index = player.toIntOrNull()?.takeIf { it in players.indices } ?: throw JsonParseException("No player \"$player\"")
            require((state.isFinished || index in state.knockedOut || state.players[index].isFinished(state.board)) && millis in 0..playMillis) { "Player $index's finish time doesn't fit" }
            index to millis
        }
        require(finishMillis.keys == state.finishingOrder.toSet() + state.knockedOut) { "A finished player has no finish time" }
        return StartedGame(
            mode,
            players,
            state,
            first,
            startedAtEpochMillis = obj.long("startedAt"),
            playMillis = playMillis,
            finishMillis = finishMillis,
            customName = obj.stringOrNull("customName"),
            id = obj.stringOrNull("id"),
            steps = obj.intOrNull("steps")?.also { require(it >= 0) { "Negative steps" } } ?: 0,
            log = runCatching {
                if ("log" in obj) MoveLog.withTurns(obj.objectList("log").map { decodeLogEntry(it, players.size) }, state) else emptyList()
            }.getOrDefault(emptyList()),
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
                        // A power-up's, while they last.
                        if (player.doubledRolls > 0) put("doubledRolls", player.doubledRolls)
                        if (player.timesHaunted > 0) put("timesHaunted", player.timesHaunted)
                        if (player.shielded.isNotEmpty()) put("shielded", jsonInts(player.shielded.sorted()))
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
        // Sudden death's count of rounds, and the players with no pawn left; absent where the rules have neither.
        if (state.rules.suddenDeath) put("roundsPlayed", state.roundsPlayed)
        // The turns since sudden death's second stage, the squares the Cataclysm has taken, and those it has just taken (for a Nearby guest to see them go).
        if (state.lostSquares.isNotEmpty()) put("lostSquares", jsonInts(state.lostSquares.sorted()))
        if (state.justLost.isNotEmpty()) put("justLost", jsonInts(state.justLost))
        if (state.suddenDeathTurns > 0) put("suddenDeathTurns", state.suddenDeathTurns)
        if (state.knockedOut.isNotEmpty()) put("knockedOut", jsonInts(state.knockedOut))
        // Over: never saved, but a Nearby host sends its guests the game's end (`.claude/NETPLAY.md`).
        if (state.isFinished) put("finished", true)
        // The power-ups on the board, the turns until the next, and what their chances are drawn from; absent where the rules have none.
        if (state.rules.powerUps) {
            put("powerUps", JsonArray(state.powerUps.map { (square, powerUp) -> buildJsonObject { put("square", square); put("kind", powerUp.id) } }))
            put("turnsToPowerUp", state.turnsToPowerUp)
        }
        // The squares claimed, as (square, player) pairs.
        if (state.claimed.isNotEmpty()) put("claimed", JsonArray(state.claimed.entries.sortedBy { it.key }.map { (square, player) -> jsonInts(listOf(square, player)) }))
        // The ghosts, and the move one has just made, for a Nearby guest to see it made.
        if (state.rules.ghosts) {
            put("ghosts", jsonInts(state.ghosts))
            put("turnsSinceGhost", state.turnsSinceGhost)
            state.ghostMove?.let { move ->
                put("ghostMove", buildJsonObject {
                    put("ghost", move.ghost)
                    put("from", move.from)
                    put("steps", move.steps)
                    put("caught", encodePawns(move.caught))
                })
            }
        }
        if (state.rules.hasChances) put("seed", state.seed.toString())
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
                doubledRolls = player.intOrNull("doubledRolls") ?: 0,
                timesHaunted = player.intOrNull("timesHaunted") ?: 0,
                shielded = if ("shielded" in player) player.array("shielded").ints().toSet() else emptySet(),
            )
        }
        require(players.size == playerCount) { "${players.size} players' states for $playerCount players" }
        require(players.map { it.seat }.let { seats -> seats.zipWithNext().all { (a, b) -> a < b } && seats.all { it in 0..<board.seats } }) {
            "The players aren't seated clockwise round the board"
        }
        players.forEach { player ->
            require(player.pawns.size == rules.pawnsPerPlayer) { "A player has ${player.pawns.size} pawns" }
            require(player.pawns.all { if (rules.lastStanding) it == PawnPosition.OUT_PROGRESS || it in 0..<board.trackSteps else it in PawnPosition.YARD_PROGRESS..board.homeProgress }) {
                "A pawn is off the board"
            }
            require(rules.powerUps || (player.doubledRolls == 0 && player.shielded.isEmpty())) { "A power-up's effect without power-ups" }
            require(player.doubledRolls in 0..PowerUp.DOUBLED_ROLLS && player.shielded.all { it in player.pawns.indices }) { "A power-up's effect doesn't fit" }
            require(player.turns >= 0 && player.captures >= 0 && player.timesCaptured >= 0) { "A negative count" }
        }
        val current = obj.int("current")
        require(current in 0..<playerCount) { "No player $current to play" }
        val finishingOrder = obj.array("finishingOrder").ints()
        val roundsPlayed = if (rules.suddenDeath) obj.int("roundsPlayed") else 0
        require(roundsPlayed >= 0) { "Negative rounds" }
        val lostSquares = if ("lostSquares" in obj) obj.array("lostSquares").ints().toSet() else emptySet()
        val justLost = if ("justLost" in obj) obj.array("justLost").ints() else emptyList()
        val suddenDeathTurns = obj.intOrNull("suddenDeathTurns") ?: 0
        require(suddenDeathTurns == 0 || (suddenDeathTurns > 0 && rules.suddenDeath && roundsPlayed >= rules.suddenDeathRound(GameRules.NOWHERE_SAFE_STAGE, playerCount))) {
            "Turns of sudden death before it came"
        }
        require(lostSquares.isEmpty() || suddenDeathTurns >= Cataclysm.STARTS + Cataclysm.TURNS) { "Squares lost before the Cataclysm" }
        require(lostSquares.all { it in 0..<board.trackLength } && lostSquares.containsAll(justLost) && board.trackLength - lostSquares.size > rules.dieFaces) {
            "The squares lost don't fit the track"
        }
        require(players.all { side -> side.pawns.none { it in 0..<board.trackSteps && board.trackSquare(side.seat, it) in lostSquares } }) { "A pawn is on a lost square" }
        val knockedOut = if ("knockedOut" in obj) obj.array("knockedOut").ints() else emptyList()
        require(knockedOut.distinct() == knockedOut && knockedOut.toSet() == players.indices.filter { players[it].pawnsLeft == 0 }.toSet()) {
            "The players knocked out aren't those with no pawn left"
        }
        val powerUps = if (rules.powerUps) {
            obj.objectList("powerUps").associate { it.int("square") to storedId<PowerUp>(it.string("kind")) }
        } else {
            emptyMap()
        }
        require(powerUps.keys.all { it in 0..<board.trackLength && !board.isSafe(it) }) { "A power-up is off the track or on a safe square" }
        val turnsToPowerUp = if (rules.powerUps) obj.intOrNull("turnsToPowerUp") ?: PowerUp.MOST_TURNS else 0
        require(!rules.powerUps || turnsToPowerUp in 1..PowerUp.MOST_TURNS) { "A power-up is due at a time that doesn't fit" }
        val claimed = if ("claimed" in obj) {
            obj.array("claimed").items.associate { item ->
                val (square, player) = (item as? JsonArray)?.ints()?.takeIf { it.size == 2 } ?: throw JsonParseException("A claimed square isn't a pair")
                square to player
            }
        } else {
            emptyMap()
        }
        require(claimed.isEmpty() || rules.claims && claimed.all { (square, player) -> square in 0..<board.trackLength && !board.isSafe(square) && player in players.indices }) {
            "A square is claimed off the track, on a safe square, or by nobody"
        }
        val ghosts = if (rules.ghosts) obj.array("ghosts").ints() else emptyList()
        require(ghosts.size == (if (rules.ghosts) Ghosts.count(playerCount) else 0) && ghosts.all { it in 0..<board.trackLength }) { "The ghosts aren't on the track" }
        val turnsSinceGhost = if (rules.ghosts) obj.int("turnsSinceGhost") else 0
        require(turnsSinceGhost >= 0) { "Negative turns" }
        val ghostMove = obj.objOrNull("ghostMove")?.takeIf { rules.ghosts }?.let { move ->
            GhostMove(move.int("ghost"), move.int("from"), move.int("steps"), decodePawns(move.array("caught"))).also {
                require(it.ghost in ghosts.indices && it.steps != 0) { "A ghost's move doesn't fit" }
            }
        }
        val seed = if (rules.hasChances) obj.string("seed").toLongOrNull() ?: throw JsonParseException("The seed isn't a number") else 0
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
                roundsPlayed = roundsPlayed,
                knockedOut = knockedOut,
                powerUps = powerUps,
                turnsToPowerUp = turnsToPowerUp,
                claimed = claimed,
                ghosts = ghosts,
                turnsSinceGhost = turnsSinceGhost,
                ghostMove = ghostMove,
                lostSquares = lostSquares,
                justLost = justLost,
                suddenDeathTurns = suddenDeathTurns,
                seed = seed,
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
        require(members.values.count { team -> !knockedOut.containsAll(team) } > 1) { "A finished game is never saved" }
        require(current !in knockedOut) { "A player with no pawn left has the turn" }
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
            roundsPlayed = roundsPlayed,
            knockedOut = knockedOut,
            powerUps = powerUps,
            turnsToPowerUp = turnsToPowerUp,
                claimed = claimed,
            ghosts = ghosts,
            turnsSinceGhost = turnsSinceGhost,
            ghostMove = ghostMove,
            lostSquares = lostSquares,
            justLost = justLost,
            suddenDeathTurns = suddenDeathTurns,
            seed = seed,
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

    private fun encodeLogEntry(entry: LogEntry): JsonObject = buildJsonObject {
        put("event", encodeEvent(entry.event))
        put("at", entry.atEpochMillis)
        entry.place?.let { put("place", it) }
        if (entry.haunted.isNotEmpty()) put("haunted", encodePawns(entry.haunted))
        if (entry.vanished > 0) put("vanished", entry.vanished)
        entry.turn?.let { put("turn", it) }
    }

    private fun decodeLogEntry(obj: JsonObject, playerCount: Int): LogEntry = LogEntry(
        event = decodeEvent(obj.obj("event"), playerCount),
        atEpochMillis = obj.long("at"),
        place = obj.intOrNull("place")?.also { require(it in 1..playerCount) { "No place $it" } },
        haunted = decodePawnsIn(obj, "haunted").also { pawns -> require(pawns.all { it.player in 0..<playerCount }) { "No player in $pawns" } },
        vanished = obj.intOrNull("vanished") ?: 0,
        turn = obj.intOrNull("turn")?.also { require(it > 0) { "No turn $it" } },
    )

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
            put("captures", encodePawns(event.move.captures))
            event.move.owner?.let { put("owner", it) }
            event.move.roll?.let { put("die", it) }
            event.move.via?.let { put("via", it) }
            event.move.swap?.let { put("swap", encodePawns(listOf(it))) }
            if (event.move.skipped.isNotEmpty()) put("skipped", jsonInts(event.move.skipped))
            event.move.bouncedFrom?.let { put("bouncedFrom", it) }
            if (event.move.brokenShields.isNotEmpty()) put("brokenShields", encodePawns(event.move.brokenShields))
            event.effect?.let { effect ->
                put("effect", buildJsonObject {
                    put("kind", effect.powerUp.id)
                    effect.movedTo?.let { put("movedTo", it) }
                    if (effect.captures.isNotEmpty()) put("captures", encodePawns(effect.captures))
                    if (effect.brokenShields.isNotEmpty()) put("brokenShields", encodePawns(effect.brokenShields))
                    effect.swapped?.let { put("swapped", encodePawns(listOf(it))) }
                    effect.calledUp?.let { put("calledUp", it) }
                })
            }
            put("finished", event.finished)
            if (event.rollsAgain) put("rollsAgain", true)
            if (event.knockedOut.isNotEmpty()) put("knockedOut", jsonInts(event.knockedOut))
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
                    captures = decodePawns(obj.array("captures")),
                    owner = obj.intOrNull("owner")?.also { require(it in 0..<playerCount) { "No player $it owns the pawn" } },
                    roll = obj.intOrNull("die"),
                    via = obj.intOrNull("via"),
                    swap = decodePawnsIn(obj, "swap").firstOrNull()?.also { require(it.player in 0..<playerCount) { "No player ${it.player} to swap with" } },
                    skipped = if ("skipped" in obj) obj.array("skipped").ints() else emptyList(),
                    bouncedFrom = obj.intOrNull("bouncedFrom"),
                    brokenShields = decodePawnsIn(obj, "brokenShields"),
                ),
                finished = obj.boolean("finished"),
                rollsAgain = obj.flag("rollsAgain"),
                knockedOut = if ("knockedOut" in obj) obj.array("knockedOut").ints().also { out -> require(out.all { it in 0..<playerCount }) { "No player in $out" } } else emptyList(),
                effect = obj.objOrNull("effect")?.let(::decodeEffect),
            )
            else -> throw JsonParseException("Unknown event \"$kind\"")
        }
    }

    private fun decodeEffect(obj: JsonObject): PowerUpEffect = PowerUpEffect(
        powerUp = storedId(obj.string("kind")),
        movedTo = obj.intOrNull("movedTo"),
        captures = decodePawnsIn(obj, "captures"),
        brokenShields = decodePawnsIn(obj, "brokenShields"),
        swapped = decodePawnsIn(obj, "swapped").firstOrNull(),
        calledUp = obj.intOrNull("calledUp"),
    )

    /** Pawns as (player, pawn) pairs. */
    private fun encodePawns(pawns: List<PawnRef>): JsonArray = JsonArray(pawns.map { jsonInts(listOf(it.player, it.pawn)) })

    private fun decodePawns(array: JsonArray): List<PawnRef> = array.items.map { item ->
        val pair = (item as? JsonArray)?.ints()?.takeIf { it.size == 2 } ?: throw JsonParseException("A pawn isn't a pair")
        PawnRef(pair[0], pair[1])
    }

    /** [decodePawns] of [obj]'s [key]: none where it's absent. */
    private fun decodePawnsIn(obj: JsonObject, key: String): List<PawnRef> = if (key in obj) decodePawns(obj.array(key)) else emptyList()

    private inline fun <reified E> storedId(id: String): E where E : Enum<E>, E : StoredId =
        storedIdOf<E>(id) ?: throw JsonParseException("Unknown ${E::class.simpleName} \"$id\"")
}

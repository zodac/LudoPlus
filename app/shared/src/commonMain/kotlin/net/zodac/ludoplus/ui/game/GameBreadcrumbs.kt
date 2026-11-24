package net.zodac.ludoplus.ui.game

import net.zodac.ludoplus.app.Breadcrumbs
import net.zodac.ludoplus.game.GameEvent

// i18n: not translated - a crash report's developer text. Ids and numbers only, never a name.

/** Leaves a breadcrumb for each step a game takes: started or brought back, then every event in it. */
internal object GameBreadcrumbs {

    fun started(game: StartedGame) = Breadcrumbs.record("Started ${describe(game)}")

    fun resumed(game: StartedGame) = Breadcrumbs.record("Resumed ${describe(game)}")

    /** [event], unless it's [before]: the event there already was. */
    fun event(before: GameEvent?, event: GameEvent?) {
        if (event != null && event != before) Breadcrumbs.record(describe(event))
    }

    private fun describe(game: StartedGame): String {
        val houseRules = if (game.state.rules.hasHouseRules) " with house rules" else ""
        return "${game.mode.id}$houseRules: ${game.players.joinToString(", ") { it.type.id }}"
    }

    private fun describe(event: GameEvent): String {
        val player = "player ${event.player + 1}"
        return when (event) {
            is GameEvent.Rolled -> "Rolled ${dice(event.roll, event.second)}, $player"
            is GameEvent.NoMove -> "Rolled ${dice(event.roll, event.second)} with no move, $player"
            is GameEvent.RollAgainLimit -> "Rolled ${dice(event.roll, event.second)}, the roll-again limit, $player"
            is GameEvent.Moved -> {
                val move = event.move
                listOfNotNull(
                    "Moved pawn ${move.pawn + 1} from ${move.from} to ${move.to}, $player",
                    move.owner?.let { "for player ${it + 1}" },
                    move.via?.let { "by the portal at $it" },
                    move.bouncedFrom?.let { "bounced from $it" },
                    event.effect?.let { "took ${it.powerUp.name}" },
                    move.swap?.let { "swapped with pawn ${it.pawn + 1} of player ${it.player + 1}".takeUnless { event.keptPlace } ?: "kept its place" },
                    event.allCaptures.takeIf { it.isNotEmpty() }?.let { "captured ${it.size}" },
                ).joinToString(", ")
            }
        }
    }

    private fun dice(roll: Int, second: Int?): String = if (second == null) "$roll" else "$roll and $second"
}

package net.zodac.ludo.netplay

import net.zodac.ludo.data.JsonArray
import net.zodac.ludo.data.JsonObject
import net.zodac.ludo.data.buildJsonObject
import net.zodac.ludo.data.int
import net.zodac.ludo.data.intOrNull
import net.zodac.ludo.data.objectList
import net.zodac.ludo.data.parseJson
import net.zodac.ludo.data.string
import net.zodac.ludo.data.stringOrNull
import net.zodac.ludo.data.toJson
import net.zodac.ludo.model.PlayerColour
import net.zodac.ludo.model.storedIdOf

/**
 * The netplay protocol's version: a guest whose version isn't the host's is refused at [NetMessage.Hello], as neither
 * could read all of the other's messages. Bumped by any change an older app couldn't read.
 */
const val PROTOCOL_VERSION = 1

/** One message between a host and a guest, as [NetCodec] sends it. See `.claude/NETPLAY.md`, "Design". */
sealed interface NetMessage {

    // ---- Guest to host ----

    /**
     * A guest's first message: its protocol, and its player's name and colour, which the host may give another. With a
     * [token] (its [Welcome]'s), it's coming back to the seat it dropped out of.
     */
    data class Hello(val protocol: Int, val name: String, val colour: PlayerColour?, val token: String? = null) : NetMessage

    /** The guest's player rolls, on their turn. */
    data object Roll : NetMessage

    /** The guest's player moves [pawn] (of seat [owner], a partner's; null: their own) with [die] (null: the only one). */
    data class Move(val pawn: Int, val owner: Int?, val die: Int?) : NetMessage

    /** The guest is leaving: its seat opens again, or (once the game has started) is played by the CPU. */
    data object Leave : NetMessage

    // ---- Host to guest ----

    /** The guest's [seat], taken, and the [token] that takes it back after a dropped link. */
    data class Welcome(val seat: Int, val token: String) : NetMessage

    /** Not taken: why. The link closes after. */
    data class Refused(val reason: RefusedReason) : NetMessage

    /** The table as it stands: every seat, and who's in it. Sent whenever it changes, until the game starts. */
    data class Lobby(val seats: List<LobbySeat>) : NetMessage

    /** The whole game, as `SavedGameJson` writes it, the [number]th the host has sent: a guest shows the latest. */
    data class Game(val number: Int, val json: String) : NetMessage

    /** The host has ended the session: the game is over or abandoned. */
    data object Ended : NetMessage
}

/** Why a guest wasn't given a seat. */
enum class RefusedReason(val id: String) {
    /** No Nearby seat is open. */
    FULL("full"),

    /** The game has started without this guest. */
    STARTED("started"),

    /** The apps' protocols differ: one of them needs updating. */
    VERSION("version"),
}

/** Who a lobby seat is for. */
enum class SeatKind(val id: String) {
    /** A person at the host's phone. */
    HOST("host"),

    /** The computer, played by the host. */
    CPU("cpu"),

    /** A guest's phone: open while [LobbySeat.name] is null. */
    NEARBY("nearby"),
}

/** One seat at the table, in seat order: [name] is null for an open [SeatKind.NEARBY] seat. */
data class LobbySeat(val kind: SeatKind, val name: String?, val colour: PlayerColour) {
    val open: Boolean get() = kind == SeatKind.NEARBY && name == null
}

/**
 * [NetMessage]s as bytes: compact JSON, each with a `"type"`. A message this app can't read (another version's, or a
 * damaged one) is null rather than a throw, so a bad payload costs that message, not the session.
 */
object NetCodec {

    fun encode(message: NetMessage): ByteArray = when (message) {
        is NetMessage.Hello -> buildJsonObject {
            put("type", "hello")
            put("protocol", message.protocol)
            put("name", message.name)
            message.colour?.let { put("colour", it.id) }
            message.token?.let { put("token", it) }
        }
        NetMessage.Roll -> buildJsonObject { put("type", "roll") }
        is NetMessage.Move -> buildJsonObject {
            put("type", "move")
            put("pawn", message.pawn)
            message.owner?.let { put("owner", it) }
            message.die?.let { put("die", it) }
        }
        NetMessage.Leave -> buildJsonObject { put("type", "leave") }
        is NetMessage.Welcome -> buildJsonObject {
            put("type", "welcome")
            put("seat", message.seat)
            put("token", message.token)
        }
        is NetMessage.Refused -> buildJsonObject {
            put("type", "refused")
            put("reason", message.reason.id)
        }
        is NetMessage.Lobby -> buildJsonObject {
            put("type", "lobby")
            put(
                "seats",
                JsonArray(
                    message.seats.map { seat ->
                        buildJsonObject {
                            put("kind", seat.kind.id)
                            seat.name?.let { put("name", it) }
                            put("colour", seat.colour.id)
                        }
                    },
                ),
            )
        }
        is NetMessage.Game -> buildJsonObject {
            put("type", "game")
            put("number", message.number)
            put("game", message.json)
        }
        NetMessage.Ended -> buildJsonObject { put("type", "ended") }
    }.toJson().encodeToByteArray()

    /** The message in [bytes], or null if it isn't one this app can read. */
    fun decode(bytes: ByteArray): NetMessage? = runCatching {
        val obj = parseJson(bytes.decodeToString()) as JsonObject
        when (obj.string("type")) {
            "hello" -> NetMessage.Hello(
                obj.int("protocol"),
                obj.string("name"),
                obj.stringOrNull("colour")?.let { storedIdOf<PlayerColour>(it) },
                obj.stringOrNull("token"),
            )
            "roll" -> NetMessage.Roll
            "move" -> NetMessage.Move(obj.int("pawn"), obj.intOrNull("owner"), obj.intOrNull("die"))
            "leave" -> NetMessage.Leave
            "welcome" -> NetMessage.Welcome(obj.int("seat"), obj.string("token"))
            "refused" -> NetMessage.Refused(RefusedReason.entries.first { it.id == obj.string("reason") })
            "lobby" -> NetMessage.Lobby(
                obj.objectList("seats").map { seat ->
                    LobbySeat(
                        kind = SeatKind.entries.first { it.id == seat.string("kind") },
                        name = seat.stringOrNull("name"),
                        colour = checkNotNull(storedIdOf<PlayerColour>(seat.string("colour"))),
                    )
                },
            )
            "game" -> NetMessage.Game(obj.int("number"), obj.string("game"))
            "ended" -> NetMessage.Ended
            else -> null
        }
    }.getOrNull()
}

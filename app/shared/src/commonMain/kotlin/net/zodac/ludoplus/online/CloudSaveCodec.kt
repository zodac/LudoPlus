package net.zodac.ludoplus.online

import net.zodac.ludoplus.data.JsonArray
import net.zodac.ludoplus.data.JsonBoolean
import net.zodac.ludoplus.data.JsonNull
import net.zodac.ludoplus.data.JsonNumber
import net.zodac.ludoplus.data.JsonObject
import net.zodac.ludoplus.data.JsonString
import net.zodac.ludoplus.data.JsonValue
import net.zodac.ludoplus.data.Stamped
import net.zodac.ludoplus.data.achievements.CloudAchievements
import net.zodac.ludoplus.data.buildJsonObject
import net.zodac.ludoplus.data.intOrNull
import net.zodac.ludoplus.data.ints
import net.zodac.ludoplus.data.jsonInts
import net.zodac.ludoplus.data.jsonLongs
import net.zodac.ludoplus.data.long
import net.zodac.ludoplus.data.longs
import net.zodac.ludoplus.data.objOrNull
import net.zodac.ludoplus.data.parseJson
import net.zodac.ludoplus.data.records.GameResult
import net.zodac.ludoplus.data.settings.SettingValue
import net.zodac.ludoplus.data.toJson
import net.zodac.ludoplus.model.playerNameKey
import okio.Buffer
import okio.GzipSink
import okio.GzipSource
import okio.buffer
import okio.use

/**
 * A [CloudSave] as bytes: compact JSON, gzipped. A progress file seals these ([ProgressFileCodec]), and Play Games'
 * cloud save will keep them in its one snapshot, [SNAPSHOT_NAME].
 *
 * Every format, now and later, is gzipped JSON with a top-level `"version"`. Within a version, fields are only ever
 * added, and a reader skips the ones it doesn't know; a change an older app can't read past bumps [VERSION]. An app
 * reading a newer version takes what it can and never writes over it ([CloudSave.writableHere]).
 */
object CloudSaveCodec {

    /** This app's format. */
    const val VERSION = 1

    /** The snapshot's name on the online service. Permanent: another name would be another, empty, save. */
    const val SNAPSHOT_NAME = "ludo-progress"

    /** The most a save is read up to once inflated. Anything larger is [DecodedSave.TooLarge]. */
    internal const val MAX_INFLATED_BYTES = 32L * 1024 * 1024

    /** The most this app sends the online service, which stores 3 MB at most: a larger save stays on the device. */
    internal const val MAX_SAVE_BYTES = 3_000_000

    private const val ROW_SEPARATOR = ';'
    private const val FIELD_SEPARATOR = ','
    private const val FLAG_PRIMARY_PLAYER = 1
    private const val FLAG_UNMODIFIED = 2
    private const val FLAG_HOUSE_RULES = 4
    private const val FLAG_WON = 8

    fun encode(save: CloudSave): ByteArray {
        val json = buildJsonObject {
            // Never below this app's: what's written is this app's format, whichever save it was merged from.
            put("version", maxOf(save.version, VERSION))
            put("achievements", encode(save.achievements))
            put("records", encodeRecords(save.records))
            put("settings", JsonObject(save.settings.mapValues { (_, stamped) -> stamped(stamped, ::encodeSetting) }))
            put("game", stamped(save.inProgressGame) { game -> game?.let(::JsonString) ?: JsonNull })
        }
        val gzipped = Buffer()
        GzipSink(gzipped).buffer().use { it.writeUtf8(json.toJson()) }
        return gzipped.readByteArray()
    }

    /** The save in [bytes], or null if this app can't read it, for whichever reason [read] gives. */
    fun decode(bytes: ByteArray): CloudSave? = (read(bytes) as? DecodedSave.Readable)?.save

    /**
     * What [bytes] are: a save, one too large to read here, or not a save at all. A newer version whose fields can't be
     * read comes back empty but for its [CloudSave.version], so it is still never written over.
     */
    fun read(bytes: ByteArray): DecodedSave = try {
        val text = GzipSource(Buffer().write(bytes)).buffer().use { source ->
            // A few kilobytes can inflate to gigabytes: one that would is never read into the app's memory.
            if (source.request(MAX_INFLATED_BYTES + 1)) null else source.readUtf8()
        }
        if (text == null) DecodedSave.TooLarge else readJson(text)
    } catch (_: Exception) {
        DecodedSave.Corrupt
    } catch (_: Throwable) {
        // Out of memory, or the stack: a save this device can't hold says nothing about whether it is one.
        DecodedSave.TooLarge
    }

    private fun readJson(text: String): DecodedSave {
        val root = parseJson(text) as JsonObject
        val version = root.intOrNull("version") ?: return DecodedSave.Corrupt
        return try {
            DecodedSave.Readable(decodeBody(root, version))
        } catch (_: Exception) {
            if (version > VERSION) DecodedSave.Readable(CloudSave(version = version)) else DecodedSave.Corrupt
        }
    }

    private fun decodeBody(root: JsonObject, version: Int): CloudSave {
        val achievements = root.section("achievements")
        return CloudSave(
            version = version,
            achievements = CloudAchievements(
                unlockedAt = achievements.section("unlocked").longs(),
                deviceCounters = achievements.section("devices").fields.mapValues { (_, counters) -> (counters as JsonObject).ints() },
                latestCounters = achievements.section("latest").fields.mapValues { (_, value) -> decodeStamped(value) { (it as JsonNumber).toInt() } },
            ),
            records = decodeRecords(root.section("records")),
            settings = root.section("settings").fields.mapValues { (_, value) -> decodeStamped(value, ::decodeSetting) },
            inProgressGame = root.objOrNull("game")?.let { game -> decodeStamped(game) { (it as? JsonString)?.value } } ?: Stamped(null, 0),
        )
    }

    private fun encode(achievements: CloudAchievements): JsonObject = buildJsonObject {
        put("unlocked", jsonLongs(achievements.unlockedAt))
        put("devices", JsonObject(achievements.deviceCounters.mapValues { (_, counters) -> jsonInts(counters) }))
        put("latest", JsonObject(achievements.latestCounters.mapValues { (_, stamped) -> stamped(stamped) { JsonNumber(it) } }))
    }

    /**
     * The records, in far less than an object per row: `names` and `modes` hold each player name and mode id once, and
     * `table` is one string of rows, split by [ROW_SEPARATOR], each row's fields by [FIELD_SEPARATOR]: its uuid, its
     * name's and mode's places in those lists, the time since the row before, flags ([FLAG_PRIMARY_PLAYER] and the rest),
     * player count, place, play time, finish time, turns, captures, times captured, pawns home, squares, and squares moved
     * (empty when unknown). Fields are only added at a row's end, and a row without one (written before it was added) reads
     * it as 0, but squares moved as unknown. A row's
     * [GameResult.playerKey] is worked out again on reading.
     */
    private fun encodeRecords(rows: List<GameResult>): JsonObject {
        val names = rows.mapTo(LinkedHashSet()) { it.playerName }.withIndex().associate { (index, name) -> name to index }
        val modes = rows.mapTo(LinkedHashSet()) { it.gameModeId }.withIndex().associate { (index, mode) -> mode to index }
        val table = StringBuilder()
        var previousTime = 0L
        for (row in rows) {
            if (table.isNotEmpty()) table.append(ROW_SEPARATOR)
            val flags = (if (row.isPrimaryPlayer) FLAG_PRIMARY_PLAYER else 0) or
                (if (row.playedUnmodified) FLAG_UNMODIFIED else 0) or
                (if (row.houseRules) FLAG_HOUSE_RULES else 0) or
                (if (row.won) FLAG_WON else 0)
            listOf(
                row.uuid, names.getValue(row.playerName), modes.getValue(row.gameModeId), row.endedAtEpochMillis - previousTime, flags,
                row.playerCount, row.place, row.playMillis, row.finishMillis, row.turns, row.captures, row.timesCaptured, row.pawnsHome, row.squares,
                row.squaresMoved?.toString().orEmpty(),
            ).joinTo(table, FIELD_SEPARATOR.toString())
            previousTime = row.endedAtEpochMillis
        }
        return buildJsonObject {
            put("names", JsonArray(names.keys.map(::JsonString)))
            put("modes", JsonArray(modes.keys.map(::JsonString)))
            put("table", table.toString())
        }
    }

    private fun decodeRecords(records: JsonObject): List<GameResult> {
        val table = (records["table"] as? JsonString)?.value.orEmpty()
        if (table.isEmpty()) return emptyList()
        val names = (records["names"] as JsonArray).items.map { (it as JsonString).value }
        val modes = (records["modes"] as JsonArray).items.map { (it as JsonString).value }
        var time = 0L
        return table.split(ROW_SEPARATOR).map { row ->
            val fields = row.split(FIELD_SEPARATOR)
            fun int(index: Int) = fields.getOrNull(index)?.toInt() ?: 0
            fun long(index: Int) = fields.getOrNull(index)?.toLong() ?: 0
            val uuid = fields[0]
            require(UUID.matches(uuid)) { "Not a uuid: $uuid" }
            val name = names[fields[1].toInt()]
            time += fields[3].toLong()
            val flags = fields[4].toInt()
            GameResult(
                uuid = uuid,
                playerName = name,
                playerKey = playerNameKey(name),
                gameModeId = modes[fields[2].toInt()],
                endedAtEpochMillis = time,
                isPrimaryPlayer = flags and FLAG_PRIMARY_PLAYER != 0,
                playedUnmodified = flags and FLAG_UNMODIFIED != 0,
                houseRules = flags and FLAG_HOUSE_RULES != 0,
                won = flags and FLAG_WON != 0,
                playerCount = int(5),
                place = int(6),
                playMillis = long(7),
                finishMillis = long(8),
                turns = int(9),
                captures = int(10),
                timesCaptured = int(11),
                pawnsHome = int(12),
                squares = int(13),
                squaresMoved = fields.getOrNull(14)?.takeIf { it.isNotEmpty() }?.toInt(),
            )
        }
    }

    /** A row's uuid as [GameResult.uuid] holds it. */
    private val UUID = Regex("[0-9a-f]{32}")

    private fun encodeSetting(value: SettingValue?): JsonValue = when (value) {
        is SettingValue.Text -> JsonString(value.value)
        is SettingValue.Number -> JsonNumber(value.value)
        is SettingValue.Flag -> JsonBoolean(value.value)
        is SettingValue.TextSet -> JsonArray(value.value.sorted().map(::JsonString))
        null -> JsonNull
    }

    private fun decodeSetting(value: JsonValue): SettingValue? = when (value) {
        is JsonString -> SettingValue.Text(value.value)
        is JsonNumber -> SettingValue.Number(value.toInt())
        is JsonBoolean -> SettingValue.Flag(value.value)
        is JsonArray -> SettingValue.TextSet(value.items.map { (it as JsonString).value }.toSet())
        else -> null
    }

    private fun <T> stamped(stamped: Stamped<T>, encode: (T) -> JsonValue): JsonObject = buildJsonObject {
        put("value", encode(stamped.value))
        put("at", stamped.atEpochMillis)
    }

    private fun <T> decodeStamped(json: JsonValue, decode: (JsonValue) -> T): Stamped<T> {
        val stamped = json as JsonObject
        return Stamped(decode(stamped["value"] ?: JsonNull), stamped.long("at"))
    }

    /** A missing section reads as an empty one. */
    private fun JsonObject.section(key: String): JsonObject = objOrNull(key) ?: JsonObject(emptyMap())
}

/** What a save's bytes turned out to be ([CloudSaveCodec.read]). */
sealed interface DecodedSave {

    data class Readable(val save: CloudSave) : DecodedSave

    /** Not a save, or a broken one: it holds nothing to keep, so it may be written over. */
    data object Corrupt : DecodedSave

    /** Too large to read here: it may well be someone's whole progress, so it is never written over. */
    data object TooLarge : DecodedSave
}

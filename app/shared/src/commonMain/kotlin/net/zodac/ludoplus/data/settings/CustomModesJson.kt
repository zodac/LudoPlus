package net.zodac.ludoplus.data.settings

import net.zodac.ludoplus.data.JsonArray
import net.zodac.ludoplus.data.JsonObject
import net.zodac.ludoplus.data.JsonString
import net.zodac.ludoplus.data.buildJsonObject
import net.zodac.ludoplus.data.parseJson
import net.zodac.ludoplus.data.string
import net.zodac.ludoplus.data.stringList
import net.zodac.ludoplus.data.toJson
import net.zodac.ludoplus.model.CustomMode
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule
import net.zodac.ludoplus.model.storedIdOf

/**
 * The player's custom modes ([CustomMode]) as the settings keep them: a JSON array, each mode's id, name, base mode, and
 * house rules by id. A mode that can't be read (a base or every house rule this app doesn't know, say) is dropped, and
 * a house rule it doesn't know is left out; the rest are kept.
 */
internal object CustomModesJson {
    fun encode(modes: List<CustomMode>): String = JsonArray(
        modes.map { mode ->
            buildJsonObject {
                put("id", mode.id)
                put("name", mode.name)
                put("base", mode.base.id)
                put("houseRules", JsonArray(HouseRule.entries.filter { it in mode.houseRules }.map { JsonString(it.id) }))
            }
        },
    ).toJson()

    fun decode(json: String): List<CustomMode> {
        val items = (runCatching { parseJson(json) }.getOrNull() as? JsonArray)?.items ?: return emptyList()
        return items.mapNotNull { item ->
            runCatching {
                val obj = item as JsonObject
                CustomMode(
                    id = obj.string("id"),
                    name = obj.string("name"),
                    base = requireNotNull(storedIdOf<GameMode>(obj.string("base"))),
                    houseRules = obj.stringList("houseRules").mapNotNull { storedIdOf<HouseRule>(it) }.toSet(),
                )
            }.getOrNull()
        }.distinctBy { it.id }
    }
}

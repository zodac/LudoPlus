package net.zodac.ludo.data.settings

import net.zodac.ludo.data.JsonArray
import net.zodac.ludo.data.JsonObject
import net.zodac.ludo.data.JsonString
import net.zodac.ludo.data.buildJsonObject
import net.zodac.ludo.data.parseJson
import net.zodac.ludo.data.string
import net.zodac.ludo.data.stringList
import net.zodac.ludo.data.toJson
import net.zodac.ludo.model.CustomMode
import net.zodac.ludo.model.GameMode
import net.zodac.ludo.model.HouseRule
import net.zodac.ludo.model.storedIdOf

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

package net.zodac.ludoplus.data.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.CustomMode
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.HouseRule

class CustomModesJsonTest {

    private val modes = listOf(
        CustomMode("custom_1", "Cut-Throat", GameMode.CLASSIC, setOf(HouseRule.MUST_CAPTURE, HouseRule.BLOCKADES)),
        CustomMode("custom_2", "Quick \"Fire\" é", GameMode.TWO_DICE, setOf(HouseRule.FREE_START)),
    )

    @Test
    fun `custom modes come back as they were kept - in their order`() {
        assertEquals(modes, CustomModesJson.decode(CustomModesJson.encode(modes)))
        assertEquals(
            "[{\"id\":\"custom_1\",\"name\":\"Cut-Throat\",\"base\":\"classic\",\"houseRules\":[\"blockades\",\"must_capture\"]}," +
                "{\"id\":\"custom_2\",\"name\":\"Quick \\\"Fire\\\" é\",\"base\":\"two_dice\",\"houseRules\":[\"free_start\"]}]",
            CustomModesJson.encode(modes),
        )
    }

    @Test
    fun `one that can't be read is dropped - an unknown house rule left out - the rest kept - and nothing read from nonsense`() {
        val json = CustomModesJson.encode(modes)
        // An unknown base drops the mode; an unknown house rule beside a known one is left out.
        assertEquals(listOf(modes[1]), CustomModesJson.decode(json.replace("\"base\":\"classic\"", "\"base\":\"nope\"")))
        assertEquals(setOf(HouseRule.MUST_CAPTURE), CustomModesJson.decode(json.replace("\"blockades\"", "\"retired\"")).first().houseRules)
        // A mode left with no house rule it knows isn't one.
        assertEquals(listOf(modes[0]), CustomModesJson.decode(json.replace("\"free_start\"", "\"retired\"")))
        assertEquals(emptyList(), CustomModesJson.decode("{"))
        assertEquals(emptyList(), CustomModesJson.decode("{}"))
    }

    @Test
    fun `a custom mode has a name and a house rule - its rules its base's with them - names unique whatever their case - ids counting up`() {
        assertFailsWith<IllegalArgumentException> { CustomMode("custom_1", " ", GameMode.CLASSIC, setOf(HouseRule.BLOCKADES)) }
        assertFailsWith<IllegalArgumentException> { CustomMode("custom_1", "Plain", GameMode.CLASSIC, emptySet()) }
        assertFailsWith<IllegalArgumentException> { CustomMode("custom_1", "x".repeat(CustomMode.MAX_NAME_LENGTH + 1), GameMode.CLASSIC, setOf(HouseRule.BLOCKADES)) }
        val rules = modes[1].rules
        assertEquals(2, rules.dice)
        assertTrue(rules.freeStart)
        assertEquals(setOf(HouseRule.FREE_START), rules.houseRules)
        assertTrue(CustomMode.nameTaken("cut-throat ", modes))
        assertFalse(CustomMode.nameTaken("Cut-Throat", modes, except = "custom_1"))
    }
}

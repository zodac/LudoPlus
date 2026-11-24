package net.zodac.ludoplus

import kotlin.test.Test
import kotlin.test.assertEquals
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.ModePage

/** The pages of Statistics' drop-down: their order, and what each is stored as. */
class ModePageTest {

    @Test
    fun `every mode has a page in order - then All Modes`() {
        assertEquals(GameMode.entries + null, ModePage.all.map { it.mode })
    }

    @Test
    fun `a page is stored and read back - and one no longer known opens the default mode's`() {
        ModePage.all.forEach { assertEquals(it, ModePage.parse(it.stored)) }
        assertEquals("all_modes", ModePage.AllModes.stored)
        assertEquals(ModePage.Mode(GameMode.CLASSIC), ModePage.parse(null))
        assertEquals(ModePage.Mode(GameMode.CLASSIC), ModePage.parse("a mode since removed"))
    }
}

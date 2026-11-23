package net.zodac.ludoplus.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import net.zodac.ludoplus.data.achievements.AchievementsState
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCategory
import net.zodac.ludoplus.model.AchievementCounter
import net.zodac.ludoplus.model.AchievementVisibility
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.model.ProgressStyle
import net.zodac.ludoplus.model.RETIRED_ACHIEVEMENT_IDS

private const val NOW = 1_000L

private val PLAYERS = listOf(PlayerConfig(1, PlayerType.HUMAN, "Me")) + (2..4).map { PlayerConfig(it, PlayerType.AI, "P$it") }

class AchievementEngineTest {

    @Test
    fun `every id is its constant in lower case - the constant the title in snake case - and every id is unique`() {
        Achievement.entries.forEach { assertEquals(it.name.lowercase(), it.id) }
        assertEquals(Achievement.entries.size, Achievement.entries.map { it.id }.toSet().size)
        AchievementCounter.entries.forEach { assertEquals(it.name, it.id) }
    }

    @Test
    fun `each category's achievements sit together - in the categories' own order`() {
        val runs = Achievement.entries.map { it.category }.fold(listOf<AchievementCategory>()) { runs, category ->
            if (runs.lastOrNull() == category) runs else runs + category
        }
        assertEquals(AchievementCategory.entries, runs)
    }

    @Test
    fun `a ladder on one counter climbs - easiest first`() {
        AchievementCounter.entries.forEach { counter ->
            val targets = Achievement.entries.filter { it.counter == counter }.map { it.target }
            assertEquals(targets.sorted(), targets, counter.name)
        }
    }

    @Test
    fun `only Miscellaneous and Easter Eggs are hidden - and every one of them is`() {
        val hiddenCategories = setOf(AchievementCategory.MISCELLANEOUS, AchievementCategory.EASTER_EGGS)
        Achievement.entries.forEach {
            assertEquals(it.category in hiddenCategories, it.visibility == AchievementVisibility.HIDDEN, it.name)
        }
    }

    @Test
    fun `Completionist waits on everything but itself and the Easter Eggs`() {
        assertEquals(
            Achievement.entries.filterNot { it == Achievement.COMPLETIONIST || it.category == AchievementCategory.EASTER_EGGS },
            Achievement.COMPLETION_REQUIREMENTS,
        )
    }

    @Test
    fun `a new game earns The Journey Begins - after a lost one One More Time - against zodac Big Fan - from midnight to 6am Midnight Run`() {
        assertEquals(GameFeats(setOf(Achievement.THE_JOURNEY_BEGINS)), AchievementEngine.atGameStart(PLAYERS, previousGame = null, localHour = 14))

        val lost = GameState.new(GameMode.CLASSIC, 4).copy(phase = TurnPhase.Finished, finishingOrder = listOf(2, 0, 1, 3))
        val won = lost.copy(finishingOrder = listOf(0, 2, 1, 3))
        val unfinished = GameState.new(GameMode.CLASSIC, 4)
        assertTrue(Achievement.ONE_MORE_TIME in AchievementEngine.atGameStart(PLAYERS, lost, 14).achievements)
        assertTrue(Achievement.ONE_MORE_TIME !in AchievementEngine.atGameStart(PLAYERS, won, 14).achievements)
        assertTrue(Achievement.ONE_MORE_TIME !in AchievementEngine.atGameStart(PLAYERS, unfinished, 14).achievements)

        val creator = PLAYERS.toMutableList().apply { this[2] = PlayerConfig(3, PlayerType.HUMAN, "zodac") }
        val creatorCpu = PLAYERS.toMutableList().apply { this[2] = PlayerConfig(3, PlayerType.AI, "zodac") }
        val shouting = PLAYERS.toMutableList().apply { this[2] = PlayerConfig(3, PlayerType.HUMAN, "ZODAC") }
        assertTrue(Achievement.BIG_FAN in AchievementEngine.atGameStart(creator, null, 14).achievements)
        assertTrue(Achievement.BIG_FAN !in AchievementEngine.atGameStart(creatorCpu, null, 14).achievements)
        assertTrue(Achievement.BIG_FAN !in AchievementEngine.atGameStart(shouting, null, 14).achievements)

        assertTrue(Achievement.MIDNIGHT_RUN in AchievementEngine.atGameStart(PLAYERS, null, 0).achievements)
        assertTrue(Achievement.MIDNIGHT_RUN in AchievementEngine.atGameStart(PLAYERS, null, 5).achievements)
        assertTrue(Achievement.MIDNIGHT_RUN !in AchievementEngine.atGameStart(PLAYERS, null, 6).achievements)
        assertTrue(Achievement.MIDNIGHT_RUN !in AchievementEngine.atGameStart(PLAYERS, null, 23).achievements)

        assertEquals(GameFeats(setOf(Achievement.LETS_FINISH_THIS)), AchievementEngine.atGameResume())
        assertEquals(GameFeats(setOf(Achievement.SIDEWAYS)), AchievementEngine.atSideways())
    }

    @Test
    fun `every name with one first letter is Alliteration`() {
        val names = listOf("Mia", "max", "Moe", "Mel")
        val same = PLAYERS.mapIndexed { i, player -> player.copy(name = names[i]) }
        assertTrue(Achievement.ALLITERATION in AchievementEngine.atGameStart(same, null, 14).achievements)
        assertTrue(Achievement.ALLITERATION !in AchievementEngine.atGameStart(PLAYERS, null, 14).achievements, "Me, P2, P3, P4")
        val digits = PLAYERS.mapIndexed { i, player -> player.copy(name = "${i}x") }
        assertTrue(Achievement.ALLITERATION !in AchievementEngine.atGameStart(digits, null, 14).achievements)
    }

    @Test
    fun `a player named Ludo is Who's Ludo`() {
        val ludo = PLAYERS.toMutableList().apply { this[3] = PlayerConfig(4, PlayerType.AI, "ludo") }
        assertTrue(Achievement.WHOS_LUDO in AchievementEngine.atGameStart(ludo, null, 14).achievements)
        assertTrue(Achievement.WHOS_LUDO !in AchievementEngine.atGameStart(PLAYERS, null, 14).achievements)
    }

    @Test
    fun `no achievement takes a retired one's id`() {
        assertTrue(Achievement.entries.none { it.id in RETIRED_ACHIEVEMENT_IDS }, Achievement.entries.filter { it.id in RETIRED_ACHIEVEMENT_IDS }.toString())
    }

    @Test
    fun `counts are added to the stored counters - and a counter reaching a target unlocks it`() {
        val before = AchievementsState(counters = mapOf(AchievementCounter.GAMES_PLAYED to 9, AchievementCounter.CAPTURES to 3))
        val update = AchievementEngine.evaluate(GameFeats(counts = mapOf(AchievementCounter.GAMES_PLAYED to 1, AchievementCounter.CAPTURES to 2)), before, NOW)
        assertEquals(mapOf(AchievementCounter.GAMES_PLAYED to 10, AchievementCounter.CAPTURES to 5), update.counters)
        assertEquals(listOf(Achievement.GETTING_COMFORTABLE), update.newlyUnlocked)
        assertEquals(mapOf(Achievement.GETTING_COMFORTABLE to NOW), update.unlockedAt())
        // Regular (50) moved from 9 to 10, not across a quarter (12.5), nor did any longer ladder; Pest (10) from 3 to 5,
        // across its quarter (2.5) to its half: one banner.
        assertEquals(listOf(AchievementProgress(Achievement.PEST, 3, 5)), update.progressed)
    }

    @Test
    fun `a running total's progress banner shows at each quarter - never alongside its own unlock`() {
        val before = AchievementsState(counters = mapOf(AchievementCounter.PAWNS_HOME to 24))
        val update = AchievementEngine.evaluate(GameFeats(counts = mapOf(AchievementCounter.PAWNS_HOME to 1)), before, NOW)
        assertEquals(listOf(AchievementProgress(Achievement.FREQUENT_TRAVELLER, 24, 25)), update.progressed)

        val unlocking = AchievementEngine.evaluate(
            GameFeats(counts = mapOf(AchievementCounter.PAWNS_HOME to 1)),
            AchievementsState(unlockedAt = mapOf(Achievement.HOMECOMING to 5L), counters = mapOf(AchievementCounter.PAWNS_HOME to 99)),
            NOW,
        )
        assertEquals(listOf(Achievement.FREQUENT_TRAVELLER), unlocking.newlyUnlocked)
        assertEquals(emptyList(), unlocking.progressed.filter { it.achievement == Achievement.FREQUENT_TRAVELLER })
    }

    @Test
    fun `a win steps the win streak up with a banner each step - another's win breaks it back to 0 and steps the losing one up`() {
        val two = AchievementsState(counters = mapOf(AchievementCounter.WIN_STREAK to 2))
        val third = AchievementEngine.evaluate(GameFeats(streak = true), two, NOW)
        assertEquals(mapOf(AchievementCounter.WIN_STREAK to 3), third.counters)
        assertEquals(listOf(Achievement.ON_A_ROLL), third.newlyUnlocked)
        assertEquals(listOf(AchievementProgress(Achievement.HOT_STREAK, 2, 3), AchievementProgress(Achievement.UNSTOPPABLE, 2, 3)), third.progressed)

        assertEquals(mapOf(AchievementCounter.LOSS_STREAK to 1, AchievementCounter.WIN_STREAK to 0), AchievementEngine.evaluate(GameFeats(streak = false), two, NOW).counters)
        assertEquals(mapOf(AchievementCounter.LOSS_STREAK to 1), AchievementEngine.evaluate(GameFeats(streak = false), AchievementsState(), NOW).counters, "no win streak to break")

        val fourLosses = AchievementsState(counters = mapOf(AchievementCounter.LOSS_STREAK to 4))
        assertEquals(listOf(Achievement.LOSING_STREAK), AchievementEngine.evaluate(GameFeats(streak = false), fourLosses, NOW).newlyUnlocked)
        assertEquals(mapOf(AchievementCounter.WIN_STREAK to 1, AchievementCounter.LOSS_STREAK to 0), AchievementEngine.evaluate(GameFeats(streak = true), fourLosses, NOW).counters)
    }

    @Test
    fun `an achievement already earned isn't earned again - and the last of the rest brings Completionist`() {
        val earned = AchievementsState(unlockedAt = mapOf(Achievement.GOTCHA to 5L))
        assertTrue(AchievementEngine.unlockNow(setOf(Achievement.GOTCHA), earned, NOW).isEmpty)

        val allButOne = AchievementsState(unlockedAt = (Achievement.COMPLETION_REQUIREMENTS - Achievement.WOODEN_SPOON).associateWith { 5L })
        assertEquals(listOf(Achievement.WOODEN_SPOON, Achievement.COMPLETIONIST), AchievementEngine.unlockNow(setOf(Achievement.WOODEN_SPOON), allButOne, NOW).newlyUnlocked)
    }

    @Test
    fun `a target of 1 has no progress bar - a streak's bar can fall - every other counter's only climbs`() {
        Achievement.entries.forEach { achievement ->
            assertEquals(achievement.counter != null && achievement.target > 1, achievement.hasProgressBar, achievement.name)
        }
        assertEquals(ProgressStyle.STREAK, Achievement.UNSTOPPABLE.progressStyle)
        assertEquals(ProgressStyle.CUMULATIVE, Achievement.WELL_ROLLED.progressStyle)
    }
}

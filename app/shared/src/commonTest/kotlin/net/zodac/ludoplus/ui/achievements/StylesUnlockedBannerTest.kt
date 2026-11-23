package net.zodac.ludoplus.ui.achievements

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.zodac.ludoplus.app.AchievementEvent
import net.zodac.ludoplus.app.AchievementEvents
import net.zodac.ludoplus.app.StylesEarnedBy
import net.zodac.ludoplus.app.UnlockedStyle
import net.zodac.ludoplus.app.announce
import net.zodac.ludoplus.data.achievements.AchievementsState
import net.zodac.ludoplus.game.AchievementEngine
import net.zodac.ludoplus.game.style.StyleReward
import net.zodac.ludoplus.game.style.StyleUnlock
import net.zodac.ludoplus.game.style.styleRewards
import net.zodac.ludoplus.game.style.stylesUnlockedByCount
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCategory
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.style_family_garden
import net.zodac.ludoplus.resources.style_family_starry
import net.zodac.ludoplus.resources.style_family_maths
import net.zodac.ludoplus.resources.style_family_ocean
import net.zodac.ludoplus.resources.style_noun_background
import net.zodac.ludoplus.resources.style_noun_board
import net.zodac.ludoplus.resources.style_noun_dice
import net.zodac.ludoplus.ui.style.StyleCatalogs

@OptIn(ExperimentalCoroutinesApi::class)
class StylesUnlockedBannerTest {

    /** The achievements a count lock counts: every one, Easter Eggs included, those with styles of their own last. */
    private val countedAchievements = Achievement.entries.sortedBy { it.styleRewards.isNotEmpty() }

    /** Every count-locked style, with the count that unlocks it. */
    private val countLocks: List<Pair<Int, StyleReward>> = StyleCatalogs.flatMap { catalog ->
        catalog.families.mapNotNull { family ->
            (family.unlock as? StyleUnlock.AchievementCount)?.let { it.count to StyleReward(family.name, catalog.noun) }
        }
    }

    /**
     * The highest count a lock waits on that can be reached: the 95 and 100 locks can't until the maintainer's new
     * achievements come (2026-11-23; `StyleRegistryTest`).
     */
    private val highestCountInReach: Int get() = countLocks.map { it.first }.filter { it <= countedAchievements.size }.max()

    private fun stateWith(counted: Int, vararg extra: Achievement): AchievementsState =
        AchievementsState((countedAchievements.take(counted) + extra).associateWith { 0L })

    private fun events(block: () -> Unit): List<AchievementEvent> {
        val events = mutableListOf<AchievementEvent>()
        runTest(UnconfinedTestDispatcher()) {
            val collector = launch { AchievementEvents.events.collect { events += it } }
            block()
            collector.cancel()
        }
        return events
    }

    @Test
    fun `each count-locked style is unlocked by the step onto its count and no other - a jump over several unlocking each on the way`() {
        // One short or one past does not: Garden dice are unlocked at 95, but no styles are unlocked at 94 or 96.
        assertEquals(listOf(StyleReward(Res.string.style_family_garden, Res.string.style_noun_dice)), stylesUnlockedByCount(94, 95))
        assertEquals(emptyList(), stylesUnlockedByCount(93, 94))
        assertEquals(emptyList(), stylesUnlockedByCount(95, 96))
        // The water set opens together, at one count: the board, the die, and the background, in the Styles screen's order.
        assertEquals(
            listOf(
                StyleReward(Res.string.style_family_ocean, Res.string.style_noun_board),
                StyleReward(Res.string.style_family_ocean, Res.string.style_noun_dice),
                StyleReward(Res.string.style_family_ocean, Res.string.style_noun_background),
            ),
            stylesUnlockedByCount(14, 15),
        )

        for ((count, reward) in countLocks) {
            assertTrue(stylesUnlockedByCount(count - 1, count).contains(reward), "${reward.styleName} ${reward.categoryNoun} missing at $count")
        }
        val counts = countLocks.map { it.first }.toSet()
        for (count in 1..countedAchievements.size) {
            if (count !in counts) assertEquals(emptyList(), stylesUnlockedByCount(count - 1, count), "at $count")
        }

        // Across categories - and no specifically locked one, nor one waiting on a count beyond them all.
        val all = stylesUnlockedByCount(0, countedAchievements.size)
        assertEquals(countLocks.filter { it.first <= countedAchievements.size }.map { it.second }.toSet(), all.toSet())
        assertTrue(all.map { it.categoryNoun }.toSet().size > 1, "$all")
        assertTrue(all.none { it.styleName == Res.string.style_family_maths || it.styleName == Res.string.style_family_starry }, "$all")
    }

    @Test
    fun `an update crossing counts announces one styles banner after its unlocks - and none when no count is reached`() {
        // An update knows the achievements earned before and after it.
        val update = AchievementEngine.unlockNow(setOf(countedAchievements[22]), stateWith(22), 0L)
        assertEquals(22, update.unlockedCountBefore)
        assertEquals(23, update.unlockedCountAfter)
        // An Easter Egg counts like any other.
        val egg = Achievement.entries.first { it.category == AchievementCategory.EASTER_EGGS }
        val eggUpdate = AchievementEngine.unlockNow(setOf(egg), stateWith(22), 0L)
        assertEquals(23, eggUpdate.unlockedCountAfter)

        val crossing = AchievementEngine.unlockNow(countedAchievements.subList(20, 30).toSet(), stateWith(20), 0L)
        val expectedStyles = stylesUnlockedByCount(20, 30)
        assertTrue(expectedStyles.size > 1, "the test needs several styles between 20 and 30, got $expectedStyles")
        val events = events { announce(crossing, ownsAllStyles = false) }
        val styleBanners = events.filterIsInstance<AchievementEvent.StylesUnlocked>()
        assertEquals(1, styleBanners.size, "$events")
        assertEquals(expectedStyles.map { UnlockedStyle(it.styleName, it.categoryNoun) }, styleBanners.single().styles)
        assertEquals(StylesEarnedBy.Count(30), styleBanners.single().earnedBy)
        assertEquals(AchievementEvent.StylesUnlocked::class, events.last { it !is AchievementEvent.Progressed }::class)
        assertEquals(10, events.count { it is AchievementEvent.Unlocked })
    }

    @Test
    fun `an achievement with styles behind it is followed by their own banner - before the count's and the progress`() {
        val ownStyle = AchievementEngine.unlockNow(setOf(Achievement.COUNTING_UP), stateWith(0), 0L)
        assertEquals(
            listOf(
                AchievementEvent.Unlocked(Achievement.COUNTING_UP),
                AchievementEvent.StylesUnlocked(
                    listOf(UnlockedStyle(Res.string.style_family_maths, Res.string.style_noun_dice)),
                    StylesEarnedBy.OneAchievement(Achievement.COUNTING_UP),
                ),
            ),
            events { announce(ownStyle, ownsAllStyles = false) },
        )

        // With a count crossed too: the achievement's banner, its styles, then the count's styles.
        val count = highestCountInReach
        val crossing = AchievementEngine.unlockNow(setOf(countedAchievements[count - 1], Achievement.BIG_FAN), stateWith(count - 1), 0L)
        val banners = events { announce(crossing, ownsAllStyles = false) }.filter { it !is AchievementEvent.Progressed }
        val bigFan = banners.indexOf(AchievementEvent.Unlocked(Achievement.BIG_FAN))
        val ownBanner = banners[bigFan + 1] as AchievementEvent.StylesUnlocked
        assertEquals(StylesEarnedBy.OneAchievement(Achievement.BIG_FAN), ownBanner.earnedBy)
        assertEquals(2, ownBanner.styles.size)
        assertTrue((banners.last() as AchievementEvent.StylesUnlocked).earnedBy is StylesEarnedBy.Count, "$banners")

        // An achievement with no style behind it gets no styles banner of its own.
        val plainAchievement = countedAchievements.first { it.styleRewards.isEmpty() }
        val plain = AchievementEngine.unlockNow(setOf(plainAchievement), stateWith(0), 0L)
        assertTrue(events { announce(plain, ownsAllStyles = false) }.none { it is AchievementEvent.StylesUnlocked && it.earnedBy is StylesEarnedBy.OneAchievement })
    }

    @Test
    fun `an owner of every style gets the unlock and progress banners - in order - and no styles banner of either kind`() {
        // Big Fan has styles of its own, and the step onto a count lock's count crosses it.
        val count = highestCountInReach
        val crossing = AchievementEngine.unlockNow(setOf(countedAchievements[count - 1], Achievement.BIG_FAN), stateWith(count - 1), 0L)
        val owned = events { announce(crossing, ownsAllStyles = true) }
        assertTrue(owned.none { it is AchievementEvent.StylesUnlocked }, "$owned")
        assertEquals(events { announce(crossing, ownsAllStyles = false) }.filterNot { it is AchievementEvent.StylesUnlocked }, owned)
        assertTrue(owned.any { it is AchievementEvent.Unlocked })
    }
}

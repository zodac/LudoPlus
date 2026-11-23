package net.zodac.ludoplus.game

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.AchievementCounter
import net.zodac.ludoplus.model.Difficulty
import net.zodac.ludoplus.model.GameMode
import net.zodac.ludoplus.model.GameRules
import net.zodac.ludoplus.model.PawnPosition.Companion.YARD_PROGRESS
import net.zodac.ludoplus.model.PlayerConfig
import net.zodac.ludoplus.model.PlayerType

/** Red (player 1, this device), green, yellow, and blue: seats 0 to 3, their start squares 0, 13, 26, and 39. */
private const val RED = 0
private const val GREEN = 1
private const val YELLOW = 2
private const val BLUE = 3

private const val Y = YARD_PROGRESS
private const val HOME = 56

/** The last track square before a home column, as progress. */
private const val LAST_TRACK = 50

/** Player 1 a person; the rest CPUs of [difficulty], or people. */
private fun configs(cpus: Boolean = true, difficulty: Difficulty = Difficulty.MEDIUM) = listOf(
    PlayerConfig(1, PlayerType.HUMAN, "Me"),
) + (2..4).map { PlayerConfig(it, if (cpus) PlayerType.AI else PlayerType.HUMAN, "P$it", difficulty) }

/** A classic game with each player's pawns at the progress given (four each), [current] to roll. */
private fun game(vararg pawns: List<Int>, current: Int = RED, rules: GameRules = GameRules(), turns: Int = 0, timesCaptured: Int = 0, captures: Int = 0): GameState {
    val all = List(4) { pawns.getOrElse(it) { listOf(Y, Y, Y, Y) } }
    val new = GameState.new(GameMode.CLASSIC, playerCount = 4, first = current, rules = rules)
    return new.copy(
        players = new.players.mapIndexed { i, player ->
            if (i == RED) {
                player.copy(pawns = all[i], turns = turns, timesCaptured = timesCaptured, captures = captures)
            } else {
                player.copy(pawns = all[i])
            }
        },
    )
}

/** Plays a game step by step through [LudoEngine], each step shown to one [AchievementTracker]. */
private class Play(var state: GameState, players: List<PlayerConfig> = configs()) {
    val tracker = AchievementTracker(players)

    /** Everything earned so far. */
    val earned = mutableSetOf<Achievement>()
    val counts = mutableMapOf<AchievementCounter, Int>()
    val streaks = mutableListOf<Boolean>()

    /** What the last step earned. */
    var last = GameFeats()

    fun roll(value: Int) = step(LudoEngine.applyRoll(state, value))

    fun move(pawn: Int) = step(LudoEngine.move(state, pawn))

    /** Rolls [value] and moves [pawn] with it. */
    fun play(value: Int, pawn: Int) = roll(value).move(pawn)

    /** Sets the board to [next] without the tracker seeing it, as a part of the game a test skips over. */
    fun skipTo(next: GameState) = apply { state = next }

    private fun step(next: GameState) = apply {
        last = tracker.observe(state, next)
        earned += last.achievements
        for ((counter, count) in last.counts) counts[counter] = (counts[counter] ?: 0) + count
        last.streak?.let(streaks::add)
        state = next
    }
}

class AchievementTrackerTest {

    @Test
    fun `a capture - Gotcha - counts toward Bouncer - an opponent's capture earns player 1 nothing`() {
        // Red's pawn on square 3; green's pawn 0 on square 4 (13 + 43).
        val play = Play(game(listOf(3, Y, Y, Y), listOf(43, Y, Y, Y))).play(1, pawn = 0)
        assertEquals(setOf(Achievement.GOTCHA), play.last.achievements)
        assertEquals(1, play.counts[AchievementCounter.CAPTURES])

        // Green on square 3 captures red's pawn on square 4.
        val theirs = Play(game(listOf(4, Y, Y, Y), listOf(42, Y, Y, Y), current = GREEN)).play(1, pawn = 0)
        assertEquals(emptySet(), theirs.earned)
        assertNull(theirs.counts[AchievementCounter.CAPTURES])
    }

    @Test
    fun `Two For One takes two pawns with one move - Sitting Ducks is losing two that way`() {
        val two = Play(game(listOf(3, Y, Y, Y), listOf(43, 43, Y, Y))).play(1, pawn = 0)
        assertTrue(Achievement.TWO_FOR_ONE in two.earned)
        assertEquals(2, two.counts[AchievementCounter.CAPTURES])

        val ducks = Play(game(listOf(4, 4, Y, Y), listOf(42, Y, Y, Y), current = GREEN)).play(1, pawn = 0)
        assertEquals(setOf(Achievement.SITTING_DUCKS, Achievement.INSULT_TO_INJURY), ducks.earned)
        val one = Play(game(listOf(4, Y, Y, Y), listOf(42, Y, Y, Y), current = GREEN)).play(1, pawn = 0)
        assertEquals(emptySet(), one.earned)
    }

    @Test
    fun `Hat Trick is three captures in one turn - each capture's roll again keeps it going - and Nobody Is Safe a pawn of every opponent`() {
        // Green on square 4, yellow on 6 (26 + 32), blue on 7 (39 + 20): red's pawn on 3 takes them a square at a time.
        val play = Play(game(listOf(3, Y, Y, Y), listOf(43, 44, Y, Y), listOf(32, Y, Y, Y), listOf(20, Y, Y, Y)))
        play.play(1, pawn = 0).play(1, pawn = 0)
        assertFalse(Achievement.HAT_TRICK in play.earned)
        play.play(1, pawn = 0)
        assertTrue(Achievement.HAT_TRICK in play.earned)
        assertFalse(Achievement.NOBODY_IS_SAFE in play.earned, "Blue's still to be captured")
        play.play(1, pawn = 0)
        assertTrue(Achievement.NOBODY_IS_SAFE in play.earned)
        assertEquals(4, play.counts[AchievementCounter.CAPTURES])
    }

    @Test
    fun `captures in different turns are no Hat Trick`() {
        val play = Play(game(listOf(3, Y, Y, Y), listOf(43, 44, 45, Y)))
        play.play(1, pawn = 0).play(1, pawn = 0)
        // The turn passes, and comes back to red.
        play.skipTo(play.state.copy(current = RED, rollingAgain = false)).play(1, pawn = 0)
        assertEquals(3, play.counts[AchievementCounter.CAPTURES], "red's pawn from square 5 lands on 6, on green's third")
        assertFalse(Achievement.HAT_TRICK in play.earned)
    }

    @Test
    fun `Deja Vu is capturing the same pawn twice in a game`() {
        val play = Play(game(listOf(3, Y, Y, Y), listOf(43, Y, Y, Y))).play(1, pawn = 0)
        assertFalse(Achievement.DEJA_VU in play.earned)
        // Green's pawn 0 comes out again, and red catches it a second time.
        play.skipTo(game(listOf(10, Y, Y, Y), listOf(50, Y, Y, Y))).play(1, pawn = 0)
        assertTrue(Achievement.DEJA_VU in play.earned)
    }

    @Test
    fun `Not So Fast captures a pawn on the last square before its home column - Back To Square One is losing one there`() {
        // Green's pawn 0 at progress 50, square 11; red's pawn on 10.
        val fast = Play(game(listOf(10, Y, Y, Y), listOf(LAST_TRACK, Y, Y, Y))).play(1, pawn = 0)
        assertTrue(Achievement.NOT_SO_FAST in fast.earned)
        val sooner = Play(game(listOf(9, Y, Y, Y), listOf(LAST_TRACK - 1, Y, Y, Y))).play(1, pawn = 0)
        assertFalse(Achievement.NOT_SO_FAST in sooner.earned)

        // Red's pawn at progress 50, square 50; green's on 49 (13 + 36).
        val back = Play(game(listOf(LAST_TRACK, Y, Y, Y), listOf(36, Y, Y, Y), current = GREEN)).play(1, pawn = 0)
        assertEquals(setOf(Achievement.BACK_TO_SQUARE_ONE), back.earned)
    }

    @Test
    fun `Payback captures whoever last captured player 1 on player 1's very next turn - not a turn later`() {
        fun capturedByGreen() = Play(game(listOf(4, 20, Y, Y), listOf(42, Y, Y, Y), current = GREEN, turns = 5)).play(1, pawn = 0)

        // Back to red for their next turn: red's pawn on square 20 catches green's on 22 (13 + 9).
        val next = capturedByGreen()
        next.skipTo(next.state.copy(current = RED, rollingAgain = false, players = next.state.players.mapIndexed { i, p -> if (i == GREEN) p.copy(pawns = listOf(9, Y, Y, Y)) else p }))
        next.play(2, pawn = 1)
        assertTrue(Achievement.PAYBACK in next.earned)

        val later = capturedByGreen()
        later.skipTo(
            later.state.copy(
                current = RED,
                rollingAgain = false,
                players = later.state.players.mapIndexed { i, p ->
                    when (i) {
                        GREEN -> p.copy(pawns = listOf(9, Y, Y, Y))
                        RED -> p.copy(turns = p.turns + 1)
                        else -> p
                    }
                },
            ),
        )
        later.play(2, pawn = 1)
        assertTrue(Achievement.GOTCHA in later.earned)
        assertFalse(Achievement.PAYBACK in later.earned)
    }

    @Test
    fun `a pawn home - Home Sweet Home - counts toward Globetrotter - and with a 1 is Inch By Inch`() {
        val one = Play(game(listOf(HOME - 1, 10, Y, Y))).play(1, pawn = 0)
        assertEquals(setOf(Achievement.HOME_SWEET_HOME, Achievement.INCH_BY_INCH), one.earned)
        assertEquals(1, one.counts[AchievementCounter.PAWNS_HOME])

        val three = Play(game(listOf(HOME - 3, 10, Y, Y))).play(3, pawn = 0)
        assertEquals(setOf(Achievement.HOME_SWEET_HOME), three.earned)
    }

    @Test
    fun `Lap Of Honour takes one pawn from its yard all the way home with no other pawn moved meanwhile`() {
        val lap = Play(game(listOf(Y, 10, Y, Y))).play(6, pawn = 0)
        // Red's pawn 0 goes the long way round, unwatched, then home.
        lap.skipTo(lap.state.copy(players = lap.state.players.mapIndexed { i, p -> if (i == RED) p.copy(pawns = listOf(HOME - 2, 10, Y, Y)) else p }))
        lap.play(2, pawn = 0)
        assertTrue(Achievement.LAP_OF_HONOUR in lap.earned)

        val broken = Play(game(listOf(Y, 10, Y, Y))).play(6, pawn = 0).play(2, pawn = 1)
        broken.skipTo(broken.state.copy(current = RED, rollingAgain = false, players = broken.state.players.mapIndexed { i, p -> if (i == RED) p.copy(pawns = listOf(HOME - 2, 12, Y, Y)) else p }))
        broken.play(2, pawn = 0)
        assertTrue(Achievement.HOME_SWEET_HOME in broken.earned)
        assertFalse(Achievement.LAP_OF_HONOUR in broken.earned)
    }

    @Test
    fun `a lap ends with its pawn captured`() {
        val play = Play(game(listOf(Y, Y, Y, Y), listOf(42, Y, Y, Y))).play(6, pawn = 0)
        // Red's pawn walks on to square 4, green captures it, and it comes out and round again: a new lap, not the old.
        play.skipTo(play.state.copy(current = GREEN, rollingAgain = false, players = play.state.players.mapIndexed { i, p -> if (i == RED) p.copy(pawns = listOf(4, Y, Y, Y)) else p }))
        play.play(1, pawn = 0)
        play.skipTo(play.state.copy(current = RED, rollingAgain = false, players = play.state.players.mapIndexed { i, p -> if (i == RED) p.copy(pawns = listOf(HOME - 1, Y, Y, Y)) else p }))
        play.play(1, pawn = 0)
        assertFalse(Achievement.LAP_OF_HONOUR in play.earned)
    }

    @Test
    fun `All Hands On Deck is every pawn on the board - Buddy System two on one square`() {
        val deck = Play(game(listOf(1, 2, 3, Y))).play(6, pawn = 3)
        assertTrue(Achievement.ALL_HANDS_ON_DECK in deck.earned)
        val home = Play(game(listOf(HOME, 2, 3, Y))).play(6, pawn = 3)
        assertFalse(Achievement.ALL_HANDS_ON_DECK in home.earned, "a pawn home isn't on the board")

        assertEquals(setOf(Achievement.BUDDY_SYSTEM), Play(game(listOf(1, 3, Y, Y))).play(2, pawn = 0).earned)
        assertEquals(emptySet(), Play(game(listOf(1, 3, Y, Y))).play(1, pawn = 0).earned)
    }

    @Test
    fun `Too Much Of A Good Thing is the third 6 in a row ending the turn`() {
        val play = Play(game(listOf(0, Y, Y, Y))).play(6, pawn = 0).play(6, pawn = 0)
        assertFalse(Achievement.TOO_MUCH_OF_A_GOOD_THING in play.earned)
        play.roll(6)
        assertEquals(setOf(Achievement.TOO_MUCH_OF_A_GOOD_THING), play.last.achievements)
    }

    @Test
    fun `So Near, Yet So Far is the last pawn a square from home missed three turns running`() {
        val play = Play(game(listOf(HOME, HOME, HOME, HOME - 1), listOf(10, Y, Y, Y)))
        fun missTurn(roll: Int) = play.roll(roll).skipTo(play.state.copy(current = RED))
        missTurn(4)
        missTurn(6)
        assertFalse(Achievement.SO_NEAR_YET_SO_FAR in play.earned)
        missTurn(2)
        assertTrue(Achievement.SO_NEAR_YET_SO_FAR in play.earned)

        val two = Play(game(listOf(HOME, HOME, HOME - 1, HOME - 1)))
        repeat(3) { two.roll(4).skipTo(two.state.copy(current = RED)) }
        assertFalse(Achievement.SO_NEAR_YET_SO_FAR in two.earned, "two pawns left")
    }

    @Test
    fun `Locked Out is 5 turns with every pawn still in the yard - a pawn captured back there doesn't count`() {
        val four = Play(game(turns = 3)).roll(3)
        assertFalse(Achievement.LOCKED_OUT in four.earned)
        assertEquals(setOf(Achievement.LOCKED_OUT), Play(game(turns = 4)).roll(3).earned)
        assertEquals(emptySet(), Play(game(turns = 4, timesCaptured = 1)).roll(3).earned)
    }

    @Test
    fun `Punching Bag is the 15th of player 1's pawns captured in a game`() {
        val play = Play(game(listOf(4, Y, Y, Y), listOf(42, Y, Y, Y), current = GREEN, timesCaptured = 13)).play(1, pawn = 0)
        assertFalse(Achievement.PUNCHING_BAG in play.earned)
        val fifteenth = Play(game(listOf(4, Y, Y, Y), listOf(42, Y, Y, Y), current = GREEN, timesCaptured = 14)).play(1, pawn = 0)
        assertTrue(Achievement.PUNCHING_BAG in fifteenth.earned)
    }

    @Test
    fun `a win against three Hard CPUs - with none captured either way - before any opponent's pawn is home`() {
        val play = Play(game(listOf(HOME, HOME, HOME, HOME - 1), listOf(10, Y, Y, Y), turns = 59), configs(difficulty = Difficulty.HARD)).play(1, pawn = 3)
        assertEquals(
            setOf(
                Achievement.HOME_SWEET_HOME,
                Achievement.INCH_BY_INCH,
                Achievement.FIRST_VICTORY,
                Achievement.CLEAN_SWEEP,
                Achievement.PACIFIST,
                Achievement.UNTOUCHABLE,
                Achievement.HOW_DO_YOU_DO_FELLOW_CPUS,
            ),
            play.earned,
        )
        assertEquals(
            mapOf(
                AchievementCounter.PAWNS_HOME to 1,
                AchievementCounter.GAMES_WON to 1,
                AchievementCounter.GAMES_PLAYED to 1,
                AchievementCounter.DIE_ROLLS to 1,
                AchievementCounter.SQUARES_MOVED to 1,
            ),
            play.counts,
        )
        assertEquals(listOf(true), play.streaks)
    }

    @Test
    fun `a harder-won win earns only what it earned`() {
        // An opponent with a pawn home and another in their home column; red has captured and been captured; 61 turns; a
        // Medium CPU among people.
        val opponents = listOf(PlayerConfig(1, PlayerType.HUMAN, "Me"), PlayerConfig(2, PlayerType.AI, "P2"), PlayerConfig(3, PlayerType.HUMAN, "P3"), PlayerConfig(4, PlayerType.HUMAN, "P4"))
        val state = game(listOf(HOME, HOME, HOME, HOME - 1), listOf(HOME, HOME, HOME, 53), turns = 60, timesCaptured = 1, captures = 1)
        val play = Play(state, opponents).play(1, pawn = 3)
        assertEquals(setOf(Achievement.HOME_SWEET_HOME, Achievement.INCH_BY_INCH, Achievement.FIRST_VICTORY, Achievement.PHOTO_FINISH), play.earned)
    }

    @Test
    fun `Comeback Kid wins after trailing an opponent with all but one pawn home while player 1 had none`() {
        val play = Play(game(listOf(Y, Y, Y, Y), listOf(HOME, HOME, HOME, 10)))
        play.roll(3)
        play.skipTo(game(listOf(HOME, HOME, HOME, HOME - 1), listOf(HOME, HOME, HOME, 20))).play(1, pawn = 3)
        assertTrue(Achievement.COMEBACK_KID in play.earned)

        val never = Play(game(listOf(HOME, HOME, HOME, HOME - 1), listOf(HOME, HOME, HOME, 20))).play(1, pawn = 3)
        assertFalse(Achievement.COMEBACK_KID in never.earned)
    }

    @Test
    fun `a CPU's win - I, Robot - breaks the streak - and from all but one home in the home column is Pipped To The Post`() {
        val play = Play(game(listOf(HOME, HOME, HOME, 53), listOf(HOME, HOME, HOME, HOME - 1), current = GREEN)).play(1, pawn = 3)
        assertEquals(setOf(Achievement.I_ROBOT, Achievement.PIPPED_TO_THE_POST), play.earned)
        assertEquals(listOf(false), play.streaks)
        assertEquals(emptyMap(), play.counts, "player 1 isn't placed yet")

        val person = Play(game(listOf(HOME, HOME, HOME, 40), listOf(HOME, HOME, HOME, HOME - 1), current = GREEN), configs(cpus = false)).play(1, pawn = 3)
        assertEquals(emptySet(), person.earned, "a person's win, and red's last pawn still on the track")
    }

    @Test
    fun `Defeat From The Jaws Of Victory is losing after all but one pawn home while no opponent had any`() {
        val play = Play(game(listOf(HOME, HOME, HOME, 10), current = GREEN))
        play.roll(3)
        play.skipTo(game(listOf(HOME, HOME, HOME, 20), listOf(HOME, HOME, HOME, HOME - 1), current = GREEN)).play(1, pawn = 3)
        assertTrue(Achievement.DEFEAT_FROM_THE_JAWS_OF_VICTORY in play.earned)
    }

    @Test
    fun `player 1 placed counts a game played - last place is the Wooden Spoon`() {
        val rules = GameRules(stopAtFirstWinner = true)
        val play = Play(game(listOf(Y, Y, Y, Y), listOf(HOME, HOME, HOME, HOME - 1), listOf(10, Y, Y, Y), listOf(5, Y, Y, Y), current = GREEN, rules = rules)).play(1, pawn = 3)
        assertEquals(listOf(GREEN, YELLOW, BLUE, RED), play.state.finishingOrder)
        assertTrue(Achievement.WOODEN_SPOON in play.earned)
        assertEquals(1, play.counts[AchievementCounter.GAMES_PLAYED])
        assertNull(play.counts[AchievementCounter.GAMES_WON])

        val second = Play(game(listOf(20, Y, Y, Y), listOf(HOME, HOME, HOME, HOME - 1), listOf(10, Y, Y, Y), listOf(5, Y, Y, Y), current = GREEN, rules = rules)).play(1, pawn = 3)
        assertFalse(Achievement.WOODEN_SPOON in second.earned)
        assertEquals(1, second.counts[AchievementCounter.GAMES_PLAYED])
    }

    @Test
    fun `player 1's rolls and squares moved are counted with the next step that earns something - or when flushed - never an opponent's`() {
        val play = Play(game(listOf(10, Y, Y, Y)))
        play.play(2, pawn = 0)
        play.skipTo(play.state.copy(current = GREEN)).roll(3)
        assertEquals(emptyMap(), play.counts)
        assertEquals(GameFeats(counts = mapOf(AchievementCounter.DIE_ROLLS to 1, AchievementCounter.SQUARES_MOVED to 2)), play.tracker.flush())
        assertEquals(GameFeats(), play.tracker.flush())

        play.skipTo(game(listOf(HOME - 1, Y, Y, Y))).roll(4).skipTo(play.state.copy(current = RED)).play(1, pawn = 0)
        assertEquals(2, play.counts[AchievementCounter.DIE_ROLLS])
    }

    @Test
    fun `nothing is earned twice in a game`() {
        val play = Play(game(listOf(1, 3, 5, Y)))
        play.play(2, pawn = 0)
        assertTrue(Achievement.BUDDY_SYSTEM in play.last.achievements)
        play.skipTo(play.state.copy(current = RED)).play(2, pawn = 1)
        assertFalse(Achievement.BUDDY_SYSTEM in play.last.achievements)
    }
    /** Player 1 rolls each of [rolls] with the turn passing straight back to them after each: for rolls with no move. */
    private fun Play.rollEach(vararg rolls: Int) = apply { rolls.forEach { roll(it).skipTo(state.copy(current = RED, rollingAgain = false)) } }

    @Test
    fun `Off The Mark is a 6 with the game's first roll - Full Spread every number in the first six`() {
        assertTrue(Achievement.OFF_THE_MARK in Play(game()).roll(6).earned)
        assertFalse(Achievement.OFF_THE_MARK in Play(game(turns = 3)).roll(6).earned)

        // Every pawn home but one stuck two squares short, so no roll but a 1 or 2 moves: the turn passes back each time.
        val stuck = listOf(HOME, HOME, HOME, HOME - 2)
        val spread = Play(game(stuck)).rollEach(3, 4, 5, 6).play(1, pawn = 3)
        assertFalse(Achievement.FULL_SPREAD in spread.earned)
        spread.skipTo(spread.state.copy(current = RED)).roll(2)
        assertTrue(Achievement.FULL_SPREAD in spread.earned)
        val play = Play(game(stuck)).rollEach(3, 4, 5, 6, 3)
        assertFalse(Achievement.FULL_SPREAD in play.roll(2).earned, "a 3 twice, and no 1")
        assertFalse(Achievement.FULL_SPREAD in Play(game(stuck, turns = 1)).rollEach(3, 4, 5, 6).play(1, pawn = 3).roll(2).earned, "not the game's first rolls")
    }

    @Test
    fun `Dry Spell is 30 rolls without a 6 - Bottom Of The Barrel three 1s in a row - Six Pack 30 6s in a game`() {
        val dry = Play(game()).rollEach(*IntArray(29) { 2 })
        assertFalse(Achievement.DRY_SPELL in dry.earned)
        assertTrue(Achievement.DRY_SPELL in dry.rollEach(3).earned)

        val ones = Play(game()).rollEach(1, 1)
        assertFalse(Achievement.BOTTOM_OF_THE_BARREL in ones.earned)
        assertTrue(Achievement.BOTTOM_OF_THE_BARREL in ones.rollEach(1).earned)
        assertFalse(Achievement.BOTTOM_OF_THE_BARREL in Play(game()).rollEach(1, 1, 2, 1).earned)

        // A 6 that can't be moved: the last pawn four squares from home.
        val sixes = Play(game(listOf(HOME, HOME, HOME, HOME - 4))).rollEach(*IntArray(29) { 6 })
        assertFalse(Achievement.SIX_PACK in sixes.earned)
        sixes.rollEach(6)
        assertTrue(Achievement.SIX_PACK in sixes.earned)
        assertEquals(30, sixes.counts[AchievementCounter.SIXES_ROLLED], "handed over with the Six Pack")
    }

    @Test
    fun `Hot Hand starts three turns in a row with a 6`() {
        val play = Play(game(listOf(10, Y, Y, Y)))
        fun turn(first: Int) = play.play(first, pawn = 0).apply { if (state.rollingAgain) play(2, pawn = 0) }.skipTo(play.state.copy(current = RED, rollingAgain = false))
        turn(6)
        turn(6)
        assertFalse(Achievement.HOT_HAND in play.earned)
        turn(6)
        assertTrue(Achievement.HOT_HAND in play.earned)

        val broken = Play(game(listOf(10, Y, Y, Y)))
        listOf(6, 6, 3, 6).forEach { first ->
            broken.play(first, pawn = 0).apply { if (state.rollingAgain) play(2, pawn = 0) }.skipTo(broken.state.copy(current = RED, rollingAgain = false))
        }
        assertFalse(Achievement.HOT_HAND in broken.earned)
    }

    @Test
    fun `captures near a start - from one's own start - of a last pawn - with a last pawn - of one opponent 4 times - 10 in a game`() {
        // Green's pawn 3 squares out (square 16); red's on 15.
        assertTrue(Achievement.WELCOME_PARTY in Play(game(listOf(15, Y, Y, Y), listOf(3, Y, Y, Y))).play(1, pawn = 0).earned)
        assertFalse(Achievement.WELCOME_PARTY in Play(game(listOf(19, Y, Y, Y), listOf(7, Y, Y, Y))).play(1, pawn = 0).earned)
        // Red's pawn on its start square takes green's on square 4.
        assertTrue(Achievement.STRAIGHT_OUT_THE_GATE in Play(game(listOf(0, Y, Y, Y), listOf(43, Y, Y, Y))).play(4, pawn = 0).earned)
        assertTrue(Achievement.TALL_POPPY in Play(game(listOf(3, Y, Y, Y), listOf(HOME, HOME, HOME, 43))).play(1, pawn = 0).earned)
        assertTrue(Achievement.PARTING_SHOT in Play(game(listOf(HOME, HOME, HOME, 3), listOf(43, Y, Y, Y))).play(1, pawn = 3).earned)
        assertTrue(Achievement.ON_THE_WARPATH in Play(game(listOf(3, Y, Y, Y), listOf(43, Y, Y, Y), captures = 9)).play(1, pawn = 0).earned)
        assertFalse(Achievement.ON_THE_WARPATH in Play(game(listOf(3, Y, Y, Y), listOf(43, Y, Y, Y), captures = 8)).play(1, pawn = 0).earned)

        val picking = Play(game(listOf(3, Y, Y, Y), listOf(43, Y, Y, Y)))
        repeat(4) {
            assertFalse(Achievement.PICKING_ON_YOU in picking.earned)
            picking.skipTo(game(listOf(3, Y, Y, Y), listOf(43, Y, Y, Y))).play(1, pawn = 0)
        }
        assertTrue(Achievement.PICKING_ON_YOU in picking.earned)
    }

    @Test
    fun `Insult To Injury is two of player 1's pawns lost in one opponent's turn - Groundhog Day one pawn lost three times`() {
        // Green on square 3 takes red's pawn on 4, rolls again, and takes red's on 6.
        val insult = Play(game(listOf(4, 6, Y, Y), listOf(42, Y, Y, Y), current = GREEN)).play(1, pawn = 0)
        assertFalse(Achievement.INSULT_TO_INJURY in insult.earned)
        insult.play(2, pawn = 0)
        assertTrue(Achievement.INSULT_TO_INJURY in insult.earned)
        assertFalse(Achievement.SITTING_DUCKS in insult.earned)

        val groundhog = Play(game(listOf(4, Y, Y, Y), listOf(42, Y, Y, Y), current = GREEN))
        repeat(3) {
            assertFalse(Achievement.GROUNDHOG_DAY in groundhog.earned)
            groundhog.skipTo(game(listOf(4, Y, Y, Y), listOf(42, Y, Y, Y), current = GREEN)).play(1, pawn = 0)
        }
        assertTrue(Achievement.GROUNDHOG_DAY in groundhog.earned)
    }

    @Test
    fun `Early Bird is the first pawn out of any yard - Late Bloomer a win after being the last out`() {
        assertTrue(Achievement.EARLY_BIRD in Play(game()).play(6, pawn = 0).earned)
        assertFalse(Achievement.EARLY_BIRD in Play(game(listOf(Y, Y, Y, Y), listOf(10, Y, Y, Y))).play(6, pawn = 0).earned)

        val late = Play(game(listOf(Y, Y, Y, Y), listOf(10, Y, Y, Y), listOf(10, Y, Y, Y), listOf(10, Y, Y, Y))).play(6, pawn = 0)
        assertFalse(Achievement.LATE_BLOOMER in late.earned)
        late.skipTo(game(listOf(HOME, HOME, HOME, HOME - 1))).play(1, pawn = 3)
        assertTrue(Achievement.LATE_BLOOMER in late.earned)

        val notLast = Play(game(listOf(Y, Y, Y, Y), listOf(10, Y, Y, Y))).play(6, pawn = 0)
        notLast.skipTo(game(listOf(HOME, HOME, HOME, HOME - 1))).play(1, pawn = 3)
        assertFalse(Achievement.LATE_BLOOMER in notLast.earned)
    }

    @Test
    fun `Double Delivery is two pawns home in one turn`() {
        val play = Play(game(listOf(HOME - 1, HOME - 2, Y, Y))).play(1, pawn = 0)
        assertFalse(Achievement.DOUBLE_DELIVERY in play.earned)
        play.play(2, pawn = 1)
        assertTrue(Achievement.DOUBLE_DELIVERY in play.earned)
    }

    @Test
    fun `Express Delivery takes a pawn yard to home within 12 turns`() {
        fun lap(homeTurn: Int): Set<Achievement> {
            val play = Play(game()).play(6, pawn = 0)
            // Out on turn 1; home on [homeTurn].
            val far = play.state.copy(current = RED, rollingAgain = false, players = play.state.players.mapIndexed { i, p -> if (i == RED) p.copy(pawns = listOf(HOME - 6, Y, Y, Y), turns = homeTurn - 1) else p })
            return play.skipTo(far).play(6, pawn = 0).earned
        }
        assertTrue(Achievement.EXPRESS_DELIVERY in lap(12))
        assertFalse(Achievement.EXPRESS_DELIVERY in lap(13))
    }

    @Test
    fun `Full House is every pawn in the home column - Leapfrog three pawns passed in one move`() {
        assertTrue(Achievement.FULL_HOUSE in Play(game(listOf(51, 52, 53, 49))).play(2, pawn = 3).earned)
        assertFalse(Achievement.FULL_HOUSE in Play(game(listOf(51, 52, HOME, 49))).play(2, pawn = 3).earned)

        // Green's pawns on squares 1, 2, and 3 (13 + 40 to 42): red's pawn from its start square to 6 passes them all.
        assertTrue(Achievement.LEAPFROG in Play(game(listOf(0, Y, Y, Y), listOf(40, 41, 42, Y))).play(6, pawn = 0).earned)
        assertFalse(Achievement.LEAPFROG in Play(game(listOf(0, Y, Y, Y), listOf(40, 41, Y, Y))).play(6, pawn = 0).earned)
    }

    @Test
    fun `My Favourite Spot is every pawn on one square - but not the start square`() {
        val track = Play(game(listOf(10, 10, 10, 7))).play(3, pawn = 3)
        assertTrue(Achievement.MY_FAVOURITE_SPOT in track.earned)
        assertTrue(Achievement.MY_FAVOURITE_SPOT in Play(game(listOf(52, 52, 52, LAST_TRACK))).play(2, pawn = 3).earned, "the home column counts")

        assertFalse(Achievement.MY_FAVOURITE_SPOT in Play(game(listOf(0, 0, 0, Y))).play(6, pawn = 3).earned, "the start square doesn't count")
        assertFalse(Achievement.MY_FAVOURITE_SPOT in Play(game(listOf(10, 10, 10, 6))).play(3, pawn = 3).earned)
        assertFalse(Achievement.MY_FAVOURITE_SPOT in Play(game(listOf(HOME, HOME, HOME, 54))).play(2, pawn = 3).earned, "home isn't a square")
    }

    @Test
    fun `We're All Friends Here is a pawn of every player on one square - by anyone's move`() {
        // The star square 8: green's progress 47 (13 + 47), yellow's 34, and blue's 21.
        val mine = Play(game(listOf(5, Y, Y, Y), listOf(47, Y, Y, Y), listOf(34, Y, Y, Y), listOf(21, Y, Y, Y))).play(3, pawn = 0)
        assertTrue(Achievement.WERE_ALL_FRIENDS_HERE in mine.earned)

        val theirs = Play(game(listOf(8, Y, Y, Y), listOf(44, Y, Y, Y), listOf(34, Y, Y, Y), listOf(21, Y, Y, Y), current = GREEN)).play(3, pawn = 0)
        assertEquals(setOf(Achievement.WERE_ALL_FRIENDS_HERE), theirs.earned)

        val noBlue = Play(game(listOf(5, Y, Y, Y), listOf(47, Y, Y, Y), listOf(34, Y, Y, Y), listOf(20, Y, Y, Y))).play(3, pawn = 0)
        assertFalse(Achievement.WERE_ALL_FRIENDS_HERE in noBlue.earned, "Blue's pawn is a square short")
    }

    @Test
    fun `Front-Runner wins after the game's first pawn home - and the rest of the win's measures`() {
        val front = Play(game(listOf(HOME - 1, 10, 10, 10))).play(1, pawn = 0)
        front.skipTo(game(listOf(HOME, HOME, HOME, HOME - 1))).play(1, pawn = 3)
        assertTrue(Achievement.FRONT_RUNNER in front.earned)
        val notFirst = Play(game(listOf(HOME - 1, 10, 10, 10), listOf(HOME, Y, Y, Y))).play(1, pawn = 0)
        notFirst.skipTo(game(listOf(HOME, HOME, HOME, HOME - 1))).play(1, pawn = 3)
        assertFalse(Achievement.FRONT_RUNNER in notFirst.earned)

        fun win(players: List<PlayerConfig> = configs(), turns: Int = 70, captures: Int = 0, timesCaptured: Int = 0) =
            Play(game(listOf(HOME, HOME, HOME, HOME - 1), turns = turns, captures = captures, timesCaptured = timesCaptured), players).play(1, pawn = 3).earned
        assertTrue(Achievement.CONQUEROR in win(captures = 10))
        assertFalse(Achievement.CONQUEROR in win(captures = 9))
        assertTrue(Achievement.NEVER_SAY_DIE in win(timesCaptured = 10))
        assertFalse(Achievement.NEVER_SAY_DIE in win(timesCaptured = 9))
        assertTrue(Achievement.LIFE_OF_THE_PARTY in win(configs(cpus = false)))
        assertFalse(Achievement.LIFE_OF_THE_PARTY in win())
    }

    @Test
    fun `Never Left Home is every pawn still in the yard when another player wins`() {
        assertTrue(Achievement.NEVER_LEFT_HOME in Play(game(listOf(Y, Y, Y, Y), listOf(HOME, HOME, HOME, HOME - 1), current = GREEN)).play(1, pawn = 3).earned)
        assertFalse(Achievement.NEVER_LEFT_HOME in Play(game(listOf(Y, Y, Y, 3), listOf(HOME, HOME, HOME, HOME - 1), current = GREEN)).play(1, pawn = 3).earned)
    }

    @Test
    fun `finishing last counts toward Spoon Collector`() {
        val rules = GameRules(stopAtFirstWinner = true)
        val play = Play(game(listOf(Y, Y, Y, Y), listOf(HOME, HOME, HOME, HOME - 1), listOf(10, Y, Y, Y), listOf(5, Y, Y, Y), current = GREEN, rules = rules)).play(1, pawn = 3)
        assertEquals(1, play.counts[AchievementCounter.LAST_PLACES])
    }

    @Test
    fun `Counting Up is 1 to 6 in order - across turns`() {
        // Every pawn in the yard, so a 6 alone moves: the turn passes back after each other roll.
        val counting = Play(game()).rollEach(1, 2, 3, 4, 5)
        assertFalse(Achievement.COUNTING_UP in counting.earned)
        counting.roll(6)
        assertTrue(Achievement.COUNTING_UP in counting.earned)
        assertFalse(Achievement.COUNTING_UP in Play(game()).rollEach(1, 2, 4, 3, 5).roll(6).earned)
    }

    @Test
    fun `Wire To Wire is a win seen from the start with no opponent ever ahead on pawns home`() {
        val wire = Play(game(listOf(HOME - 1, 10, 10, 10))).roll(3)
        wire.skipTo(game(listOf(HOME, HOME, HOME, HOME - 1), listOf(HOME, HOME, Y, Y), turns = 40)).play(1, pawn = 3)
        assertTrue(Achievement.WIRE_TO_WIRE in wire.earned)

        val behind = Play(game(listOf(HOME - 1, 10, 10, 10))).roll(3)
        behind.skipTo(game(listOf(HOME, 10, 10, 10), listOf(HOME, HOME, Y, Y), turns = 20)).roll(3)
        behind.skipTo(game(listOf(HOME, HOME, HOME, HOME - 1), listOf(HOME, HOME, Y, Y), turns = 40)).play(1, pawn = 3)
        assertFalse(Achievement.WIRE_TO_WIRE in behind.earned, "green was ahead 2 to 1")

        val resumed = Play(game(listOf(HOME, HOME, HOME, HOME - 1), turns = 40)).play(1, pawn = 3)
        assertFalse(Achievement.WIRE_TO_WIRE in resumed.earned, "not seen from the start")
    }

    @Test
    fun `Time Flies is an hour of one game on screen - once`() {
        val tracker = AchievementTracker(configs())
        val playing = game(listOf(10, Y, Y, Y))
        assertEquals(GameFeats(), tracker.playTime(59 * 60 * 1_000L, playing))
        assertEquals(GameFeats(setOf(Achievement.TIME_FLIES)), tracker.playTime(60 * 60 * 1_000L, playing))
        assertEquals(GameFeats(), tracker.playTime(2 * 60 * 60 * 1_000L, playing))
    }

    @Test
    fun `Speed Demon is player 1's win within 5 minutes of the game on screen - once - and never another's win`() {
        val won = Play(game(listOf(HOME, HOME, HOME, HOME - 1))).play(1, pawn = 3).state
        val minutes = { m: Int -> m * 60 * 1_000L }
        assertEquals(GameFeats(), AchievementTracker(configs()).playTime(minutes(4), game(listOf(HOME, HOME, HOME, HOME - 1))), "not won yet")
        assertEquals(GameFeats(), AchievementTracker(configs()).playTime(minutes(5) + 1, won))
        val tracker = AchievementTracker(configs())
        assertEquals(GameFeats(setOf(Achievement.SPEED_DEMON)), tracker.playTime(minutes(5), won))
        assertEquals(GameFeats(), tracker.playTime(minutes(5), won), "once")

        val lost = Play(game(listOf(HOME, HOME, HOME, 53), listOf(HOME, HOME, HOME, HOME - 1), current = GREEN)).play(1, pawn = 3).state
        assertEquals(GameFeats(), AchievementTracker(configs()).playTime(minutes(1), lost), "green's win")
    }
}

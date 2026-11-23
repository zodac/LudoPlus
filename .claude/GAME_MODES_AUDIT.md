# Game modes: the achievement audit

Which achievements each mode, modifier, or rules setting blocks or makes easier, and how to audit a new one. Mode
mechanics are in `GAME_MODES.md`; the achievements themselves in `DESIGN.md`, "Achievements".

> DiceFive's audit (Quickfire, Stud, Third Wind, Hit List, D20, Tricolour, and its modifiers) is in commit `5423b6c`
> (`git show 5423b6c:.claude/GAME_MODES_AUDIT.md`). Its fields came back on 2026-11-24, with every mode opened to the
> ordinary achievements (the maintainer): `Achievement.mode`, `notEarnableIn`, `impossibleIn`, and
> `earnableWithHouseRules`, judged by `isEarnableIn` and `GameFeats.earnableIn`. The Achievements screen's tip names the
> modes left for one that is limited. `AchievementModeTest` pins each mode's sets.

## How to audit

For each achievement a new mode, modifier, or setting changes, decide:

- **Can't be earned with it.** Add the mode to its `impossibleIn`. An impossibility explains
    itself: no tip.
- **Earned for free with it.** Add a guard and a test for it in `AchievementTrackerTest`, and keep it out (`notEarnableIn`,
    or `mode` for one kept to a single mode). **Prove the test** by removing that one guard and
    watching it fail, one guard at a time.
- **Easier but not free.** Don't guard it. **Report the "easier" list to the maintainer** rather than deciding alone.
- **Count the thing, not its proxy.**

| Family | Achievements | Ask |
| --- | --- | --- |
| Pawn count | Wire To Wire, All Hands On Deck, Full House, Lap Of Honour, Comeback Kid, Defeat From The Jaws Of Victory, Photo Finish, Pipped To The Post, Tall Poppy, Parting Shot, So Near, Yet So Far, Buddy System, My Favourite Spot, Double Delivery | Do "all but one pawn", "every pawn", or "two pawns" mean the same with fewer or more pawns? |
| Track length | Speed Demon, Express Delivery, Locked Out, Not So Fast!, Back To Square One, Welcome Party, Leapfrog, Ultramarathon | Are the turn and square thresholds still fast or slow, and is there still a last square before the home column? |
| Rolls | Too Much Of A Good Thing, Inch By Inch, Locked Out, So Near, Yet So Far, Off The Mark, Full Spread, Six Pack, Dry Spell, Bottom Of The Barrel, Six Shooter, Counting Up | Is there a roll-again limit, a 1, a 6, and a roll that leaves the yard? Two dice change all of these |
| Captures | Gotcha!, Pest, Two For One, Straight Out The Gate, Hat Trick, Payback, Picking On You, Nobody Is Safe, On The Warpath, Déjà Vu, Bouncer, Menace, Public Enemy, Sitting Ducks, Insult To Injury, Groundhog Day, Punching Bag, Pacifist, Untouchable, Conqueror, Never Say Die | Can pawns still be captured, more than one at once, and does a capture still give a roll? |
| Table | How Do You Do, Fellow CPUs?, Life Of The Party, Nobody Is Safe, We're All Friends Here | Still three opponents? (Phase 8's 2-8 players) |
| Game end | Wooden Spoon, Spoon Collector, Never Left Home, Early Bird, Late Bloomer, Front-Runner, Homecoming, the games-played ladder, the win counts and both streaks | Is every player still placed, and does a game still take its usual time? |

Punching Bag's number came from simulated games (2,000 a difficulty, 2026-11-17): a player is captured about 6 times a
game (15 or more about 1-3%), and a win takes player 1 about 80 turns. Re-measure it for a mode that changes the track
or the die. Speed Demon is a win within 5 minutes on screen (the maintainer, 2026-11-23), so anything that shortens a
game makes it easier: fewer players and a lower Animations level, as well as a mode.

## Stop at first winner (Settings)

A rules setting, not a modifier: games with it count on the Leaderboard and earn everything.

- **Nothing is free.** The game ends at the first winner, and the others are placed as they stand.
- **Easier:** the games-played ladder (finished games come quicker, a game counted the moment player 1 is placed, as
    in a full game); Wooden Spoon and Spoon Collector (last place is given on the spot, rather than being the last left
    racing); Never Left Home (the game can end before a pawn gets out). Left unguarded, as reported to the maintainer
    (2026-11-17).
- **Harder:** nothing a full game gives is taken away, but a game it ends early has fewer turns in which to earn the
    in-play achievements.

## Teams (New Game)

A rules setting (Phase 8): two alternating teams, or pairs sitting opposite, with 4, 6, or 8 players; partners may
capture each other or not, and a player home may roll for a partner or sit out. Team games earn achievements, but stay
off the Leaderboard (`GameRecordRepository.recordGame`).

- **A win is the team's.** Player 1 wins when their team is placed first (`GameState.placeOf`), so a partner bringing
    the last pawn home wins it for them; Wooden Spoon is the last team's. Statistics counts the same.
- **A partner isn't an opponent.** The tracker's opponents, for Comeback Kid, Early Bird, and the like, leave partners
    out.
- **Easier:** a win shared with a partner, and with roll for partners, player 1's captures can come on a partner's
    pawn's move (credited to the player who rolled). Left unguarded, as reported to the maintainer (2026-11-18).
- **Not yet audited achievement by achievement**: each team-sensitive one's own wording is for the maintainer's
    review.

## House rules (New Game)

Modifiers (Phase 9, `DESIGN.md`, "Modifiers"). **A game with any house rule on earns only the house rules' own**, and
moves no counter or streak, whatever its mode (`Achievement.earnableWithHouseRules`; the maintainer, 2026-11-24, as
DiceFive): Everything Everywhere All At Once (a win of any mode but Classic with every one that changes something in it;
the winner's, so 26% of 200 simulated games of four Medium CPUs), and Where We're Going, We Don't Need Rules, for starting
another mode. So nothing else is free, and no house rule needs an audit of its own. Road Closed and Third Time's The Charm
(one house rule's each) were removed the day they were added: too many, and 75% and 64% of games with their rule on.

## How the modes were audited (2026-11-24)

Every achievement was measured, unguarded, over 600 games a mode of four Medium CPUs, player 1 in seat 1, and compared
with Classic. **Kept out** are those a mode makes routine, or that measure something else there; **easier** ones are left,
and reported to the maintainer. Counters (games, wins, streaks, captures, rolls) count in every mode.

| Mode | Own, and how often (a game) |
| --- | --- |
| Quick | Quick On The Draw (the win), Blink And You'll Miss It 1.8% (25 turns or fewer; a win's median is 34) |
| Two Dice | Double Or Nothing, Two Birds, Two Stones 12% |
| Portals | Through The Looking Glass, There And Back Again 41% (Easy 59%, Hard 27%) |
| Survival | Last Pawn Standing, Barely A Scratch 3.3% (a sixth of wins) |
| Chaos | Agent Of Chaos, One Of Everything 12% |
| Haunted House | Living To Tell The Tale, Not A Ghost Of A Chance 3.7% (harder with more players: never, in eight-player simulations) |

Grand Tour (a game finished of every mode, none with house rules) is worked out from the records each time a game ends, so
games from before it count (`AchievementCounter.MODES_FINISHED`, raised, never added to). A new mode raises its target;
anyone already holding it keeps it.

One By One (an Easter Egg) is 6% of Classic games for a player set on it, and 2-9% in the other modes; a 6 that can't move
the pawn that's out spoils it. The Great Escape is 5-8% of games for a Medium CPU, and 19% in Two Dice, left as easier.

## Quick (a mode)

Two pawns each, about half as long.

| Kept out | Quick, against Classic |
| --- | --- |
| Full House | 8.2%, against 0.0%: two pawns in the column, not four |
| Comeback Kid | 12.8%, against 0.5%: "all but one" is one pawn |
| Defeat From The Jaws Of Victory | 14.8%, against 0.2%: and three pawns home is its own words |
| Untouchable | 14.8%, against 0.8% |
| Pacifist | 5.5%, against 0.0% |
| My Favourite Spot | 9.5%, against 2.0%: two pawns on a square is Buddy System |
| Speed Demon | Half the game in the same five minutes |
| One By One, The Great Escape | Two pawns, not the four their words say |

**Easier:** Clean Sweep (2.3%, against 0.2%), Never Left Home (4.7%, against 0.7%), Pipped To The Post, All Hands On Deck,
and the games-played and win counters (games end sooner).

## Two Dice (a mode)

Both dice count as rolls, and either may be a first-roll 6.

| Kept out | Why |
| --- | --- |
| Bottom Of The Barrel, Counting Up, Dry Spell, Full Spread | A run of rolls is one die's: two at once measure something else |
| Express Delivery | 84%, against 9.3% |

**Can't be earned:** Too Much Of A Good Thing (doubles roll again, not 6s). **Easier:** Off The Mark (31%, against 15%), Hat
Trick (12%, against 2.3%), Double Delivery (13%, against 4.0%), Insult To Injury (16%, against 8.3%), Early Bird, and the
roll and 6 counters.

## Portals (a mode)

**Kept out:** Express Delivery (40%, against 9.3%). **Easier:** Double Delivery (11%, against 4.0%). Squares moved are those
walked, not those a portal skips.

## Survival (a mode)

Six pawns, no home, captured pawns out. A game counts as finished when player 1 is knocked out; a win or a loss, at its
end.

| Kept out | Survival, against Classic |
| --- | --- |
| All Hands On Deck, Buddy System | The opening stack: 100% |
| Clean Sweep, Wire To Wire | Nobody brings a pawn home: every win |
| Two For One, Hat Trick | Stacks are captured together: 19% and 13%, against 2.2% and 2.3% |
| Sitting Ducks, Insult To Injury | The same, lost: 26% and 43%, against 2.8% and 8.3% |
| Leapfrog | 64%, against 42%: a stack is three pawns |

**Can't be earned:** everything about home, a yard, a last square, or a pawn captured twice (`AchievementModeTest`
lists them). **Easier:** Payback (29%, against 16%), We're All Friends Here.

## Chaos (a mode)

Nothing is free: the closest, Express Delivery, is 14%, against 9.3%. A power-up's captures and pawns home count
(`GameEvent.Moved.allCaptures`, `endedAt`). Untouchable is easier (shields).

## Haunted House (a mode)

A ghost's catch is nobody's capture (`timesHaunted`), so Untouchable, Punching Bag, and the rest are of rivals' alone.
**Easier**, for the longer games: Six Pack (40%, against 7.3%), On The Warpath (21%, against 13%), and Time Flies.

## Custom modes (New Game)

A base mode with house rules: their games, as any with house rules, earn only the house rules' own.

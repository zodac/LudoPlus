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
| Pawn count | Wire To Wire, All Hands On Deck, Full House, Lap Of Honour, Comeback Kid, Defeat From The Jaws Of Victory, Photo Finish, Pipped To The Post, Tall Poppy, Parting Shot, So Near, Yet So Far, Visiting The Neighbours, My Favourite Spot, Double Delivery, Full Retreat, Hungry For 6s | Do "all but one pawn", "every pawn", or "two pawns" mean the same with fewer or more pawns? |
| Track length | Speed Demon, Express Delivery, Locked Out, Not So Fast!, Back To Square One, Wrong Neighbourhood, Leapfrog, Ultramarathon | Are the turn and square thresholds still fast or slow, and is there still a last square before the home column? |
| Rolls | Too Much Of A Good Thing, Inch By Inch, Locked Out, So Near, Yet So Far, Off The Mark, Loaded Dice, Six Pack, Dry Spell, Bottom Of The Barrel, Six Shooter, Counting Up | Is there a roll-again limit, a 1, a 6, and a roll that leaves the yard? Two dice change all of these |
| Captures | Gotcha!, Pest, Two For One, Straight Out The Gate, Hat Trick, Revenge Is Sweet, Picking On You, Nobody Is Safe, On The Warpath, Déjà Vu, Bouncer, Menace, Public Enemy, Sitting Ducks, Insult To Injury, Groundhog Day, Stop Hitting Yourself, Stop Picking On Me, Pacifist, Untouchable, Conqueror, Never Say Die, Full Retreat | Can pawns still be captured, more than one at once, and does a capture still give a roll? |
| Table | How Do You Do, Fellow CPUs?, Life Of The Party, Nobody Is Safe, We're All Friends Here, Visiting The Neighbours | Still three opponents (Fellow CPUs), eight players (Life Of The Party), or a pawn for each player (Visiting The Neighbours)? |
| Game end | Wooden Spoon, Cutlery Collector, Never Left Home, Early Bird, Late Bloomer, Front-Runner, Homecoming, the games-played ladder, the win counts and both streaks | Is every player still placed, and does a game still take its usual time? |

Stop Hitting Yourself's number came from simulated games (2,000 a difficulty, 2026-11-17): a player is captured about 6 times a
game (15 or more about 1-3%), and a win takes player 1 about 80 turns. Re-measure it for a mode that changes the track
or the die. Speed Demon is a win within 5 minutes on screen (the maintainer, 2026-11-23), so anything that shortens a
game makes it easier: fewer players and a lower Animations level, as well as a mode.

## Stop at first winner (Settings)

A rules setting, not a modifier: games with it count on the Leaderboard and earn everything.

- **Nothing is free.** The game ends at the first winner, and the others are placed as they stand.
- **Easier:** the games-played ladder (finished games come quicker, a game counted the moment player 1 is placed, as
    in a full game); Wooden Spoon and Cutlery Collector (last place is given on the spot, rather than being the last left
    racing). Left unguarded, as reported to the maintainer (2026-11-17). Never Left Home is judged at the first win either
    way, so is no easier (25% with it on or off).
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
| Colour Rush | Paint The Town Red (the win), Colour Coordinated 8% with four (26% with two, 12% with three, 8% with six, 7% with eight; measured again 2026-11-25 over 1,000 games, with stars claimed and counted, and a start square ending a run) |
| Two Dice | Double Or Nothing, Two Birds, Two Stones 12% |
| Portals | Through The Looking Glass, There And Back Again 45% |
| Survival | Last Pawn Standing, Barely A Scratch 3.3% (a sixth of wins) |
| Chaos | Agent Of Chaos, One Of Everything 67% (46% with two, 99% with eight; left easy at the maintainer's word, 2026-11-24, as power-ups came more often; measured again 2026-11-25, with one of each kind always on the track) |
| Haunted House | Living To Tell The Tale, Not A Ghost Of A Chance 3.7% (harder with more players: never, in eight-player simulations) |

Grand Tour (a game finished of every mode, none with house rules) is worked out from the records each time a game ends, so
games from before it count (`AchievementCounter.MODES_FINISHED`, raised, never added to). A new mode raises its target;
anyone already holding it keeps it.

One By One (an Easter Egg) is 6% of Classic games for a player set on it, and 2-9% in the other modes; a 6 that can't move
the pawn that's out spoils it. The Great Escape is 5-8% of games for a Medium CPU, and 19% in Two Dice, left as easier.

Stop Picking On Me is 11% of four-player Classic games (31% with two players, 5% with six), and 0.4-14% in the other modes,
so it's kept out of none. A ghost's catch is nobody's, and a partner isn't one of the players.

Express Delivery is Classic's alone (the maintainer, 2026-11-24): Chaos's Swap had made it 28% there, after Portals' 40% and
Two Dice's 84%. Its description says so. Those, and the mode sections' figures for it, were measured at 12 turns; it's 15
from 2026-11-25 (the maintainer), 23% of Classic games (12% with Stop at first winner).

Dry Spell is 15 rolls without a 6, not 30, and Never Left Home at least one pawn in the yard, captured ones included, not
all of them (the maintainer, 2026-11-25). Measured again over 600 games a mode of four Medium CPUs: Dry Spell is 75% in
Classic (47% in Colour Rush to 87% in Haunted House), and Never Left Home 25% (17% in Chaos to 46% in Colour Rush).

Loaded Dice is 8.0% of four-player Classic games (3.3% in Colour Rush to 9.5% in Haunted House; 5.8% with two players, 27%
with eight). Visiting The Neighbours is 1.0% (0.1-1.0% in the other modes), but 78% of Classic games with two players, 18%
with three, and none with five or more, which four pawns can't cover. It's kept out of nothing (the maintainer, 2026-11-25).

Full Retreat (an Easter Egg) is 7.2% of four-player Classic games (0.3% with two players, 42% with eight; 0.5% in Portals to
13% in Haunted House), and Hungry For 6s (another) 0.6% (8.5% with two; 0.3-0.9% in the other modes), the last over 2,000 games.

## Colour Rush (a mode)

Over as soon as the track is claimed, in about half of Classic's turns.

| Kept out | Colour Rush, against Classic |
| --- | --- |
| Clean Sweep | 5.0%, against 0.0%: few pawns are home by then |
| Speed Demon | Half the game in the same five minutes |

**Easier:** Never Left Home (46%, against 25%), and the games-played and win counters (games end sooner). **Harder:** what
comes late in a race (Photo Finish, Pipped To The Post, Parting Shot, So Near, Yet So Far, Lap Of Honour).

## Two Dice (a mode)

Both dice count as rolls, and either may be a first-roll 6.

| Kept out | Why |
| --- | --- |
| Bottom Of The Barrel, Counting Up, Dry Spell, Loaded Dice | A run of rolls is one die's: two at once measure something else |
| Express Delivery | 84%, against 9.3% |

**Can't be earned:** Too Much Of A Good Thing (doubles roll again, not 6s). **Easier:** Off The Mark (31%, against 15%), Hat
Trick (12%, against 2.3%), Double Delivery (13%, against 4.0%), Insult To Injury (16%, against 8.3%), Early Bird, and the
roll and 6 counters.

## Portals (a mode)

**Kept out:** Express Delivery (40%, against 9.3%). **Easier:** Double Delivery (11%, against 4.0%), and with every
portal open (2026-11-24), Untouchable (5.3%, against 1.3%). Squares moved are those
walked, not those a portal skips.

## Survival (a mode)

Six pawns, no home, captured pawns out. A game counts as finished when player 1 is knocked out; a win or a loss, at its
end.

| Kept out | Survival, against Classic |
| --- | --- |
| All Hands On Deck | The opening stack: 100% |
| Clean Sweep, Wire To Wire | Nobody brings a pawn home: every win |
| Two For One, Hat Trick | Stacks are captured together: 19% and 13%, against 2.2% and 2.3% |
| Sitting Ducks, Insult To Injury | The same, lost: 26% and 43%, against 2.8% and 8.3% |
| Leapfrog | 64%, against 42%: a stack is three pawns |

**Can't be earned:** everything about home, a yard, a last square, or a pawn captured twice (`AchievementModeTest`
lists them). **Easier:** Revenge Is Sweet (29%, against 16%), We're All Friends Here.

## Chaos (a mode)

**Kept out:** Express Delivery (28%, against 8.5%). A power-up's captures count (`GameEvent.Moved.allCaptures`, `endedAt`). Untouchable is easier (shields).

## Haunted House (a mode)

A ghost's catch is nobody's capture (`timesHaunted`), so Untouchable, Stop Hitting Yourself, and the rest are of rivals' alone.
**Easier**, for the longer games: Six Pack (40%, against 7.3%), On The Warpath (21%, against 13%), and Time Flies.

## Custom modes (New Game)

A base mode with house rules: their games, as any with house rules, earn only the house rules' own.

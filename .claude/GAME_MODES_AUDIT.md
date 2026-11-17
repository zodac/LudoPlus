# Game modes: the achievement audit

Which achievements each mode, modifier, or rules setting blocks or makes easier, and how to audit a new one. Mode
mechanics are in `GAME_MODES.md`; the achievements themselves in `DESIGN.md`, "Achievements".

> DiceFive's audit (Quickfire, Stud, Third Wind, Hit List, D20, Tricolour, and its modifiers) is in commit `5423b6c`
> (`git show 5423b6c:.claude/GAME_MODES_AUDIT.md`). Its fields for limiting an achievement to some modes
> (`Achievement.mode`, `notEarnableIn`, `impossibleIn`, `earnableWithModifiers`) and the Achievements screen's tip
> naming the modes left were not brought back with Phase 6: there is one mode. Restore them from there with the
> second mode or the first modifier (Phase 9).

## How to audit

For each achievement a new mode, modifier, or setting changes, decide:

- **Can't be earned with it.** Mark it impossible there (the restored `impossibleIn`). An impossibility explains
    itself: no tip.
- **Earned for free with it.** Add a guard and a test for it in `AchievementTrackerTest`, and keep it out (the restored
    `notEarnableIn`, or `mode` for one kept to a single mode). **Prove the test** by removing that one guard and
    watching it fail, one guard at a time.
- **Easier but not free.** Don't guard it. **Report the "easier" list to the maintainer** rather than deciding alone.
- **Count the thing, not its proxy.**

| Family | Achievements | Ask |
| --- | --- | --- |
| Pawn count | Wire To Wire, All Hands On Deck, Full House, Safety First, Lap Of Honour, Comeback Kid, Defeat From The Jaws Of Victory, Photo Finish, Pipped To The Post, Tall Poppy, Parting Shot, So Near, Yet So Far, Buddy System, Double Delivery | Do "all but one pawn", "every pawn", or "two pawns" mean the same with fewer or more pawns? |
| Track length | Speed Demon, Lightning Fast, Express Delivery, Scenic Route, Locked Out, Not So Fast!, Back To Square One, Welcome Party, Leapfrog, Marathon, Ultramarathon | Are the turn and square thresholds still fast or slow, and is there still a last square before the home column? |
| Rolls | Too Much Of A Good Thing, Inch By Inch, Locked Out, So Near, Yet So Far, Off The Mark, Full Spread, Hot Hand, Six Pack, Dry Spell, Bottom Of The Barrel, Six Shooter, Sixth Sense, Broken Record, Counting Up | Is there a roll-again limit, a 1, a 6, and a roll that leaves the yard? Two dice change all of these |
| Captures | Gotcha!, Pest, Two For One, Straight Out The Gate, Hat Trick, Payback, Picking On You, Nobody Is Safe, On The Warpath, Déjà Vu, Bouncer, Menace, Public Enemy, Sitting Ducks, Insult To Injury, Groundhog Day, Punching Bag, Pacifist, Untouchable, Conqueror, Never Say Die | Can pawns still be captured, more than one at once, and does a capture still give a roll? |
| Table | Last Human Standing, How Do You Do, Fellow CPUs?, Training Wheels, Life Of The Party, Nobody Is Safe, Musical Chairs | Still three opponents, and four seat colours? (Phase 8's 2-8 players) |
| Game end | Wooden Spoon, Spoon Collector, Never Left Home, Early Bird, Late Bloomer, Front-Runner, Homecoming, the games-played ladder, the win counts and both streaks | Is every player still placed, and does a game still take its usual time? |

The numbers behind Speed Demon and Punching Bag came from simulated games (2,000 a difficulty, 2026-11-17): a win
takes player 1 about 80 turns (the fastest tenth about 60), and a player is captured about 6 times a game (15 or more
about 1-3%). Re-measure them for a mode that changes the track or the die.

## Stop at first winner (Settings)

A rules setting, not a modifier: games with it count on the Leaderboard and earn everything.

- **Nothing is free.** The game ends at the first winner, and the others are placed as they stand.
- **Easier:** the games-played ladder (finished games come quicker, a game counted the moment player 1 is placed, as
    in a full game); Wooden Spoon and Spoon Collector (last place is given on the spot, rather than being the last left
    racing); Never Left Home (the game can end before a pawn gets out). Left unguarded, as reported to the maintainer
    (2026-11-17).
- **Harder:** nothing a full game gives is taken away, but a game it ends early has fewer turns in which to earn the
    in-play achievements.

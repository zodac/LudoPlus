package net.zodac.ludoplus.online

import net.zodac.ludoplus.model.Achievement
import net.zodac.ludoplus.model.BoardGeometry
import net.zodac.ludoplus.model.GameMode

/**
 * Each achievement's id on Play Games, from Play Console's Achievements > Get resources (matched by English name),
 * saved as `assets/play-console/achievements/achievement-ids.xml`. Empty until LudoPlus's Play Console project exists
 * (`.claude/PLAY_GAMES.md`, "Play Console"): an achievement with no id is never sent. Once the first is mapped,
 * `PlayGamesIdsTest` fails until every one is.
 */
internal val playAchievementIds: Map<Achievement, String> = emptyMap()

/**
 * Each leaderboard's id on Play Games, by its key ([GameMode.onlineLeaderboardKey]: a mode's board for one player count),
 * from Play Console's Leaderboards > Get resources. Empty until the boards are made there (`scripts/export-play-console.sh`
 * writes their sheet); once the first is mapped, `PlayGamesIdsTest` fails until every board in [playLeaderboardKeys] is.
 */
internal val playLeaderboardIds: Map<String, String> = emptyMap()

/** Every board Play Games has: each mode's, for each player count. */
internal val playLeaderboardKeys: List<String> =
    GameMode.entries.flatMap { mode -> (BoardGeometry.MIN_PLAYERS..BoardGeometry.MAX_PLAYERS).map(mode::onlineLeaderboardKey) }

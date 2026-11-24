package net.zodac.ludoplus.ui.game

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import net.zodac.ludoplus.data.settings.MotionGroup
import net.zodac.ludoplus.model.PlayerType
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.gameover_captures
import net.zodac.ludoplus.resources.gameover_main_menu
import net.zodac.ludoplus.resources.gameover_pawns_home
import net.zodac.ludoplus.resources.gameover_captured
import net.zodac.ludoplus.resources.gameover_duration_hours
import net.zodac.ludoplus.resources.gameover_duration_minutes
import net.zodac.ludoplus.resources.gameover_finished_in
import net.zodac.ludoplus.resources.gameover_host_plays_again
import net.zodac.ludoplus.resources.gameover_pawns_left
import net.zodac.ludoplus.resources.gameover_play_again
import net.zodac.ludoplus.resources.gameover_survived
import net.zodac.ludoplus.resources.gameover_review
import net.zodac.ludoplus.resources.gameover_title
import net.zodac.ludoplus.resources.gameover_tutorial_done
import net.zodac.ludoplus.resources.gameover_tutorial_finished
import net.zodac.ludoplus.resources.gameover_tutorial_new_game
import net.zodac.ludoplus.resources.gameover_turns
import net.zodac.ludoplus.resources.gameover_winner
import net.zodac.ludoplus.ui.common.BrandBackdrop
import net.zodac.ludoplus.ui.common.GroupMotion
import net.zodac.ludoplus.ui.common.LandscapePage
import net.zodac.ludoplus.ui.common.LocalLandscapePage
import net.zodac.ludoplus.ui.common.MAIN_ACTION_HEIGHT
import net.zodac.ludoplus.ui.common.PacedEffect
import net.zodac.ludoplus.ui.common.PageColumn
import net.zodac.ludoplus.ui.common.PageTopBar
import net.zodac.ludoplus.ui.common.TwoPanes
import net.zodac.ludoplus.ui.common.groupReduceMotion
import net.zodac.ludoplus.ui.common.joinClauses
import net.zodac.ludoplus.ui.common.ordinal
import net.zodac.ludoplus.ui.common.pluralStringResource
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.icons.Symbols
import net.zodac.ludoplus.ui.theme.CupRimGold
import net.zodac.ludoplus.ui.theme.GoldAccent
import net.zodac.ludoplus.ui.theme.GoldAccentDim
import net.zodac.ludoplus.ui.theme.color

/** The place column's width: "4th" and the like at the place's 16sp, with room to spare. */
private val PLACE_WIDTH = 40.sp

/** Matches the length of celebration.ogg, so the fireworks finish as the fanfare does. */
private const val CELEBRATION_MILLIS = 3300

/**
 * The results of a finished [game]: the winner in a raised card with the trophy (in a team game, a card for each of the
 * winning team's players), then everyone else in the order they finished (partners sharing their team's place), each with when they finished (the last, when the game ended), then their turns, captures, and times captured
 * (the last, who never brought every pawn home, with their pawns home in place of turns). With
 * [celebrate], a person's win gets the fanfare and gold fireworks (these only with the Celebrations motion on), once:
 * [onCelebrated] is called as it starts.
 *
 * The ways on are [onReviewBoard] (back to the finished board), [onBackToMenu], and [onPlayAgain] (a new game with the
 * same players). A guest's phone in a Nearby game has no [onPlayAgain]: with [hostPlaysAgain], a line says the host's
 * Play Again starts the next game there.
 *
 * The [tutorial]'s shows the winner alone, without the game's figures, then a line that it's done, and two ways on:
 * Done ([onBackToMenu], back where it was started) and New Game ([onPlayAgain]).
 */
@Composable
fun GameOverScreen(
    game: StartedGame,
    onBackToMenu: () -> Unit,
    onPlayAgain: (() -> Unit)?,
    onReviewBoard: () -> Unit,
    modifier: Modifier = Modifier,
    celebrate: Boolean = true,
    onCelebrated: () -> Unit = {},
    tutorial: Boolean = false,
    hostPlaysAgain: Boolean = false,
) {
    val state = game.state
    val order = state.finishingOrder
    // One winner, or in a team game the first team's players, each with their card.
    val winners = order.filter { state.placeOf(it) == 1 }
    val personWon = winners.any { game.players[it].type != PlayerType.AI }
    // Latched at first composition: [onCelebrated] clears the caller's flag at once, which mustn't cut these short.
    val celebrateNow = remember { celebrate && personWon }
    val sounds = rememberSoundEffects()
    LaunchedEffect(Unit) {
        if (celebrateNow) sounds.playCelebration()
        onCelebrated()
    }

    BrandBackdrop(modifier = modifier, driftingDice = true) {
        // The fanfare has its own sound setting; the sparks are what reduced motion drops.
        if (celebrateNow && !groupReduceMotion(MotionGroup.CELEBRATIONS)) {
            GroupMotion(MotionGroup.CELEBRATIONS) { GoldFireworks(CELEBRATION_MILLIS, Modifier.fillMaxSize()) }
        }
        LandscapePage {
            Column(modifier = Modifier.fillMaxSize()) {
                // No back arrow: the ways out are the buttons. The system's back goes to the menu.
                PageTopBar(title = stringResource(Res.string.gameover_title), onBack = null)
                PageColumn(
                    modifier = Modifier.weight(1f),
                    wide = true,
                    // The app bar has already taken the status bar's inset.
                    contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    TwoPanes(
                        spacing = 12.dp,
                        start = {
                            winners.forEach { WinnerCard(game, it, details = !tutorial) }
                            if (tutorial) {
                                Text(
                                    text = stringResource(Res.string.gameover_tutorial_finished),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                )
                            } else {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                        order.filter { it !in winners }.forEachIndexed { index, player ->
                                            if (index > 0) HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                            PlacingRow(game, player, place = requireNotNull(state.placeOf(player)))
                                        }
                                    }
                                }
                            }
                        },
                        end = {
                            // Beside the results in landscape, level with their top.
                            if (!LocalLandscapePage.current) Spacer(modifier = Modifier.height(8.dp))
                            if (tutorial) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = onBackToMenu, modifier = Modifier.weight(1f).heightIn(min = MAIN_ACTION_HEIGHT)) {
                                        Text(stringResource(Res.string.gameover_tutorial_done), style = MaterialTheme.typography.titleMedium)
                                    }
                                    Button(onClick = { onPlayAgain?.invoke() }, modifier = Modifier.weight(1f).heightIn(min = MAIN_ACTION_HEIGHT)) {
                                        Text(stringResource(Res.string.gameover_tutorial_new_game), style = MaterialTheme.typography.titleMedium)
                                    }
                                }
                            } else {
                                OutlinedButton(onClick = onReviewBoard, modifier = Modifier.fillMaxWidth().heightIn(min = MAIN_ACTION_HEIGHT)) {
                                    Text(stringResource(Res.string.gameover_review), style = MaterialTheme.typography.titleMedium)
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                if (hostPlaysAgain) {
                                    Text(
                                        text = stringResource(Res.string.gameover_host_plays_again),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedButton(onClick = onBackToMenu, modifier = Modifier.weight(1f).heightIn(min = MAIN_ACTION_HEIGHT)) {
                                        Text(stringResource(Res.string.gameover_main_menu), style = MaterialTheme.typography.titleMedium)
                                    }
                                    if (onPlayAgain != null) {
                                        Button(onClick = onPlayAgain, modifier = Modifier.weight(1f).heightIn(min = MAIN_ACTION_HEIGHT)) {
                                            Text(stringResource(Res.string.gameover_play_again), style = MaterialTheme.typography.titleMedium)
                                        }
                                    }
                                }
                            }
                        },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}

/** What [player] did: their turns and captures, or, if they never finished, how many pawns they brought home; where the last standing wins, how many they had left, and their captures alone. */
@Composable
private fun resultLine(game: StartedGame, player: Int): String {
    val side = game.state.players[player]
    val board = game.state.board
    val total = game.state.rules.pawnsPerPlayer
    val first = if (game.state.rules.lastStanding) {
        pluralStringResource(Res.plurals.gameover_pawns_left, total, side.pawnsLeft, total)
    } else if (side.isFinished(board)) {
        pluralStringResource(Res.plurals.gameover_turns, side.turns, side.turns)
    } else {
        pluralStringResource(Res.plurals.gameover_pawns_home, total, side.pawnsHome(board), total)
    }
    return joinClauses(
        listOfNotNull(
            first,
            pluralStringResource(Res.plurals.gameover_captures, side.captures, side.captures),
            // Where the last standing wins, the pawns left already say it.
            if (game.state.rules.lastStanding) null else pluralStringResource(Res.plurals.gameover_captured, side.timesCaptured, side.timesCaptured),
        ),
    )
}

/** "Finished in 18m 42s" for a player placed [millis] into the game ("Survived 18m 42s" where the last standing wins); hours and minutes from an hour. */
@Composable
private fun finishedText(millis: Long, survived: Boolean): String {
    val seconds = (millis / 1_000).toInt()
    val duration = if (seconds < 3_600) {
        stringResource(Res.string.gameover_duration_minutes, seconds / 60, seconds % 60)
    } else {
        stringResource(Res.string.gameover_duration_hours, seconds / 3_600, seconds % 3_600 / 60)
    }
    return stringResource(if (survived) Res.string.gameover_survived else Res.string.gameover_finished_in, duration)
}


/** The winner's card: their name, and with [details] their finish time and figures. */
@Composable
private fun WinnerCard(game: StartedGame, player: Int, details: Boolean = true) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {},
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(imageVector = Symbols.EmojiEvents, contentDescription = null, tint = GoldAccent, modifier = Modifier.size(44.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(stringResource(Res.string.gameover_winner), style = MaterialTheme.typography.labelLarge)
                PlayerName(game, player, MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold), dot = 16)
                if (details) {
                    game.finishMillis[player]?.let { Text(finishedText(it, game.state.rules.lastStanding), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                    Text(resultLine(game, player), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun PlacingRow(game: StartedGame, player: Int, place: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics(mergeDescendants = true) {}.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // At least as wide as the widest place, in the font's own size, so every name starts level; never wrapped.
        Text(
            ordinal(place),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            softWrap = false,
            modifier = Modifier.widthIn(min = with(LocalDensity.current) { PLACE_WIDTH.toDp() }),
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            PlayerName(game, player, MaterialTheme.typography.bodyLarge, dot = 12)
            game.finishMillis[player]?.let { Text(finishedText(it, game.state.rules.lastStanding), style = MaterialTheme.typography.bodyMedium) }
            Text(resultLine(game, player), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** [player]'s colour, name, and the CPU mark if they're the computer. */
@Composable
private fun PlayerName(game: StartedGame, player: Int, style: TextStyle, dot: Int) {
    val config = game.players[player]
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(color = config.colour.color, shape = CircleShape, modifier = Modifier.size(dot.dp)) {}
        Text(config.name, style = style, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        if (config.type == PlayerType.AI) CpuPlayerIcon(size = (dot + 4).dp)
    }
}

/** How much of the display a single burst's sparks take to spread and fade. */
private const val BURST_LIFE_FRACTION = 0.4f

/** How late into the display the last burst may start, leaving it time to play out. */
private const val LAST_BURST_START_FRACTION = 1f - BURST_LIFE_FRACTION

private const val BURST_COUNT = 5
private const val PARTICLES_PER_BURST = 18
private val FIREWORK_GOLDS = listOf(GoldAccent, GoldAccentDim, CupRimGold)

/** One burst of gold sparks: where and when it starts, and each spark's angle and speed, all fixed at the start. */
private class FireworkBurst(
    val startFraction: Float,
    val center: Offset,
    val angles: FloatArray,
    val speeds: FloatArray,
    val colors: List<Color>,
)

private fun randomBursts(random: Random): List<FireworkBurst> = List(BURST_COUNT) { index ->
    FireworkBurst(
        startFraction = (index / BURST_COUNT.toFloat()) * LAST_BURST_START_FRACTION + random.nextFloat() * 0.05f,
        // Off the edges, so a burst's outer sparks stay on screen.
        center = Offset(x = 0.15f + random.nextFloat() * 0.7f, y = 0.12f + random.nextFloat() * 0.45f),
        angles = FloatArray(PARTICLES_PER_BURST) { i -> (i / PARTICLES_PER_BURST.toFloat()) * (2 * PI).toFloat() + random.nextFloat() * 0.3f },
        speeds = FloatArray(PARTICLES_PER_BURST) { 0.55f + random.nextFloat() * 0.45f },
        colors = List(PARTICLES_PER_BURST) { FIREWORK_GOLDS[random.nextInt(FIREWORK_GOLDS.size)] },
    )
}

/** A one-shot display of gold fireworks over [durationMillis], every burst driven by one animated value. */
@Composable
private fun GoldFireworks(durationMillis: Int, modifier: Modifier = Modifier) {
    val progress = remember { Animatable(0f) }
    val bursts = remember { randomBursts(Random.Default) }
    PacedEffect(Unit) { progress.animateTo(1f, animationSpec = tween(durationMillis = durationMillis, easing = LinearEasing)) }
    Canvas(modifier = modifier) {
        val sparkRadius = size.minDimension * 0.007f
        val maxBurstRadius = size.minDimension * 0.3f
        for (burst in bursts) {
            val local = (progress.value - burst.startFraction) / BURST_LIFE_FRACTION
            if (local < 0f || local >= 1f) continue
            val center = Offset(burst.center.x * size.width, burst.center.y * size.height)
            // Sparks slow as they fly out, and fall a little.
            val eased = 1f - (1f - local) * (1f - local)
            val fall = local * local * size.minDimension * 0.08f
            for (i in 0 until PARTICLES_PER_BURST) {
                val radius = maxBurstRadius * burst.speeds[i] * eased
                val x = center.x + cos(burst.angles[i]) * radius
                val y = center.y + sin(burst.angles[i]) * radius + fall
                drawCircle(color = burst.colors[i].copy(alpha = 1f - local), radius = sparkRadius, center = Offset(x, y))
            }
        }
    }
}

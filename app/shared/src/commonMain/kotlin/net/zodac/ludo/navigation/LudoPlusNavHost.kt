package net.zodac.ludo.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import net.zodac.ludo.app.AppContainer
import net.zodac.ludo.app.LocalAppContainer
import net.zodac.ludo.data.orNullOnFailure
import net.zodac.ludo.game.style.StyleCategory
import net.zodac.ludo.model.Achievement
import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.common_default_player_name
import net.zodac.ludo.resources.game_tutorial_cpu_name
import net.zodac.ludo.ui.achievements.AchievementsScreen
import net.zodac.ludo.ui.achievements.AchievementsViewModel
import net.zodac.ludo.ui.common.LocalPageCover
import net.zodac.ludo.ui.common.LocalReduceMotion
import net.zodac.ludo.ui.common.PageCover
import net.zodac.ludo.ui.common.PageCoverOverlay
import net.zodac.ludo.ui.common.rememberPageCover
import net.zodac.ludo.ui.common.stringResource
import net.zodac.ludo.ui.game.GameScreen
import net.zodac.ludo.ui.game.GameViewModel
import net.zodac.ludo.ui.menu.MenuScreen
import net.zodac.ludo.ui.netplay.HostLobbyScreen
import net.zodac.ludo.ui.netplay.JoinScreen
import net.zodac.ludo.ui.records.LeaderboardScreen
import net.zodac.ludo.ui.records.LeaderboardViewModel
import net.zodac.ludo.ui.records.StatisticsScreen
import net.zodac.ludo.ui.records.StatisticsViewModel
import net.zodac.ludo.ui.rules.RulesScreen
import net.zodac.ludo.ui.settings.GraphicsScreen
import net.zodac.ludo.ui.settings.GraphicsViewModel
import net.zodac.ludo.ui.settings.SettingsScreen
import net.zodac.ludo.ui.settings.SettingsViewModel
import net.zodac.ludo.ui.setup.GameSetupScreen
import net.zodac.ludo.ui.styles.StyleCategoryScreen
import net.zodac.ludo.ui.styles.StylesScreen
import net.zodac.ludo.ui.styles.StylesViewModel
import net.zodac.ludo.ui.styles.StylesWarmUp

/**
 * The app's pages. Opening one is done under the [cover] (see [PageCover]), never with the NavHost's own transitions: one
 * page goes, then the next is built out of sight and comes. Going back - the system's back, a page's back arrow, leaving
 * a game - shows the page it returns to at once.
 */
@Composable
fun LudoPlusNavHost(navController: NavHostController = rememberNavController(), cover: PageCover = rememberPageCover()) {
    val container = LocalAppContainer.current
    val reduceMotion = LocalReduceMotion.current
    // Made once the page in front has faded out, unless a back press in the meantime has already left it.
    val change = { navigation: () -> Unit ->
        val from = navController.currentBackStackEntry
        cover.change(reduceMotion) { if (navController.currentBackStackEntry == from) navigation() }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalPageCover provides cover) {
            NavHost(
                navController = navController,
                startDestination = Screen.MENU,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
                popEnterTransition = { EnterTransition.None },
                popExitTransition = { ExitTransition.None },
            ) {
                pages(navController, container, change)
            }
        }
        PageCoverOverlay(cover)
    }
}

/** Earns [achievement] as the page it's in is opened ("Read The Manual", "Number Cruncher"). */
@Composable
private fun EarnedOnOpening(container: AppContainer, achievement: Achievement) {
    LaunchedEffect(achievement) { container.achievementUnlocker.unlock(setOf(achievement)) }
}

/** Each page, opened by a [change] of page under the cover. */
private fun NavGraphBuilder.pages(navController: NavHostController, container: AppContainer, change: (() -> Unit) -> Unit) {
    val navigate = { route: String -> change { navController.navigate(route) } }
    val navigateUp: () -> Unit = { navController.navigateUp() }
    val backToMenu: () -> Unit = { navController.popBackStack(Screen.MENU, inclusive = false) }

    composable(Screen.MENU) {
        // Null until the saved game's file has been read, so Play doesn't change shape under the player's finger.
        val hasInProgressGame by container.inProgressGameRepository.hasInProgressGame.collectAsStateWithLifecycle(initialValue = null)
        val savedStyles by container.savedStyles.collectAsStateWithLifecycle()
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            // Under the menu, which is opaque: runs the Styles page's drawing code once while the menu idles, so opening
            // Styles isn't the first time it runs - see StylesWarmUp. Never what the page cover waits on.
            CompositionLocalProvider(LocalPageCover provides null) {
                StylesWarmUp(picks = savedStyles, width = maxWidth)
            }
            MenuScreen(
                hasInProgressGame = hasInProgressGame,
                // Straight to the board, which brings the saved game back itself (see below).
                onContinue = { navigate(Screen.PLAY_GAME) },
                onNewGame = { navigate(Screen.PLAY_GRAPH) },
                onAchievements = { navigate(Screen.ACHIEVEMENTS) },
                onLeaderboard = { navigate(Screen.LEADERBOARD) },
                onStatistics = { navigate(Screen.STATISTICS) },
                onStyles = { navigate(Screen.STYLES) },
                onRules = { navigate(Screen.RULES) },
                onSettings = { navigate(Screen.SETTINGS) },
            )
        }
    }

    navigation(startDestination = Screen.PLAY_SETUP, route = Screen.PLAY_GRAPH) {
        composable(Screen.PLAY_SETUP) { backStackEntry ->
            val playGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.PLAY_GRAPH) }
            // Offered until a person has finished a game; not while that's still being read, so nothing appears late.
            val firstGame by produceState(initialValue = false) {
                value = orNullOnFailure { !container.gameRecordRepository.hasFinishedGame() } ?: false
            }
            GameSetupScreen(
                viewModel = viewModel<GameViewModel>(playGraphEntry, factory = GameViewModel.factory(container)),
                onStartGame = { navigate(Screen.PLAY_GAME) },
                onBack = navigateUp,
                showTutorialLink = firstGame,
                onTutorial = { navigate(Screen.TUTORIAL) },
                onHost = { navigate(Screen.PLAY_LOBBY) },
                onJoin = { navigate(Screen.JOIN_GRAPH) },
            )
        }
        composable(Screen.PLAY_LOBBY) { backStackEntry ->
            val playGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.PLAY_GRAPH) }
            HostLobbyScreen(
                viewModel = viewModel<GameViewModel>(playGraphEntry, factory = GameViewModel.factory(container)),
                // The board in the lobby's place, so back from the game doesn't land in a closed lobby.
                onStart = { change { navController.navigate(Screen.PLAY_GAME) { popUpTo(Screen.PLAY_LOBBY) { inclusive = true } } } },
                onBack = navigateUp,
            )
        }
        composable(Screen.PLAY_GAME) { backStackEntry ->
            val playGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.PLAY_GRAPH) }
            val gameViewModel = viewModel<GameViewModel>(playGraphEntry, factory = GameViewModel.factory(container))
            // With no game in hand (Continue, or Android ended the app while it was in the background), the saved one
            // comes back; with none to bring back, the menu.
            LaunchedEffect(gameViewModel) {
                if (gameViewModel.game.value == null && !gameViewModel.resumeGame()) backToMenu()
            }
            GameScreen(viewModel = gameViewModel, onBackToMenu = backToMenu)
        }
    }

    navigation(startDestination = Screen.JOIN_FIND, route = Screen.JOIN_GRAPH) {
        composable(Screen.JOIN_FIND) { backStackEntry ->
            val joinGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.JOIN_GRAPH) }
            JoinScreen(
                viewModel = viewModel<GameViewModel>(joinGraphEntry, factory = GameViewModel.factory(container)),
                onPlaying = { change { navController.navigate(Screen.JOIN_GAME) { popUpTo(Screen.JOIN_FIND) { inclusive = true } } } },
                onBack = navigateUp,
            )
        }
        composable(Screen.JOIN_GAME) { backStackEntry ->
            val joinGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.JOIN_GRAPH) }
            val gameViewModel = viewModel<GameViewModel>(joinGraphEntry, factory = GameViewModel.factory(container))
            GameScreen(viewModel = gameViewModel, onBackToMenu = {
                gameViewModel.leaveJoined()
                backToMenu()
            })
        }
    }

    composable(Screen.SETTINGS) {
        SettingsScreen(
            viewModel = viewModel(factory = SettingsViewModel.factory(container)),
            onBack = navigateUp,
            onGraphics = { navigate(Screen.GRAPHICS) },
        )
    }
    composable(Screen.GRAPHICS) {
        GraphicsScreen(
            viewModel = viewModel(factory = GraphicsViewModel.factory(container)),
            onBack = navigateUp,
        )
    }
    composable(Screen.RULES) {
        EarnedOnOpening(container, Achievement.READ_THE_MANUAL)
        RulesScreen(onBack = navigateUp, onStartTutorial = { navigate(Screen.TUTORIAL) })
    }
    composable(Screen.TUTORIAL) {
        // A view model of its own, never the play graph's: nothing of a real game is touched.
        val tutorialViewModel = viewModel<GameViewModel>(factory = GameViewModel.tutorialFactory(container))
        val defaultName = stringResource(Res.string.common_default_player_name, 1)
        val cpuName = stringResource(Res.string.game_tutorial_cpu_name)
        LaunchedEffect(tutorialViewModel) { tutorialViewModel.startTutorial(defaultName, cpuName) }
        GameScreen(
            viewModel = tutorialViewModel,
            // Done goes back where it was started: Rules, or New Game.
            onBackToMenu = navigateUp,
            // Back to New Game if that's where it was started, otherwise in place of the tutorial.
            onNewGame = {
                change {
                    if (!navController.popBackStack(Screen.PLAY_SETUP, inclusive = false)) {
                        navController.navigate(Screen.PLAY_GRAPH) { popUpTo(Screen.TUTORIAL) { inclusive = true } }
                    }
                }
            },
        )
    }
    composable(Screen.LEADERBOARD) {
        LeaderboardScreen(viewModel = viewModel(factory = LeaderboardViewModel.factory(container)), onBack = navigateUp)
    }
    composable(Screen.STATISTICS) {
        EarnedOnOpening(container, Achievement.NUMBER_CRUNCHER)
        StatisticsScreen(viewModel = viewModel(factory = StatisticsViewModel.factory(container)), onBack = navigateUp)
    }
    composable(Screen.ACHIEVEMENTS) {
        AchievementsScreen(viewModel = viewModel(factory = AchievementsViewModel.factory(container)), onBack = navigateUp)
    }
    composable(Screen.STYLES) {
        StylesScreen(
            viewModel = viewModel(factory = StylesViewModel.factory(container)),
            onBack = navigateUp,
            onOpenCategory = { category -> navigate(Screen.styleCategory(category)) },
        )
    }
    for (category in StyleCategory.entries) {
        composable(Screen.styleCategory(category)) {
            StyleCategoryScreen(
                viewModel = viewModel(factory = StylesViewModel.factory(container)),
                category = category,
                onBack = navigateUp,
            )
        }
    }
}

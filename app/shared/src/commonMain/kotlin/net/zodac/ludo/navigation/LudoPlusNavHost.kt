package net.zodac.ludo.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import net.zodac.ludo.ui.common.LocalPageCover
import net.zodac.ludo.ui.common.LocalReduceMotion
import net.zodac.ludo.ui.common.PageCover
import net.zodac.ludo.ui.common.PageCoverOverlay
import net.zodac.ludo.ui.common.rememberPageCover
import net.zodac.ludo.ui.game.GameScreen
import net.zodac.ludo.ui.game.GameViewModel
import net.zodac.ludo.ui.menu.MenuScreen
import net.zodac.ludo.ui.rules.RulesScreen
import net.zodac.ludo.ui.settings.GraphicsScreen
import net.zodac.ludo.ui.settings.GraphicsViewModel
import net.zodac.ludo.ui.settings.SettingsScreen
import net.zodac.ludo.ui.settings.SettingsViewModel
import net.zodac.ludo.ui.setup.GameSetupScreen

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

/** Each page, opened by a [change] of page under the cover. */
private fun NavGraphBuilder.pages(navController: NavHostController, container: AppContainer, change: (() -> Unit) -> Unit) {
    val navigate = { route: String -> change { navController.navigate(route) } }
    val navigateUp: () -> Unit = { navController.navigateUp() }
    val backToMenu: () -> Unit = { navController.popBackStack(Screen.MENU, inclusive = false) }

    composable(Screen.MENU) {
        // Null until the saved game's file has been read, so Play doesn't change shape under the player's finger.
        val hasInProgressGame by container.inProgressGameRepository.hasInProgressGame.collectAsStateWithLifecycle(initialValue = null)
        MenuScreen(
            hasInProgressGame = hasInProgressGame,
            // Straight to the board, which brings the saved game back itself (see below).
            onContinue = { navigate(Screen.PLAY_GAME) },
            onNewGame = { navigate(Screen.PLAY_GRAPH) },
            onRules = { navigate(Screen.RULES) },
            onSettings = { navigate(Screen.SETTINGS) },
        )
    }

    navigation(startDestination = Screen.PLAY_SETUP, route = Screen.PLAY_GRAPH) {
        composable(Screen.PLAY_SETUP) { backStackEntry ->
            val playGraphEntry = remember(backStackEntry) { navController.getBackStackEntry(Screen.PLAY_GRAPH) }
            GameSetupScreen(
                viewModel = viewModel<GameViewModel>(playGraphEntry, factory = GameViewModel.factory(container)),
                onStartGame = { navigate(Screen.PLAY_GAME) },
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
        RulesScreen(onBack = navigateUp)
    }
}

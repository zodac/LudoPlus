package net.zodac.ludoplus.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.common_cancel
import net.zodac.ludoplus.resources.game_leave_confirm
import net.zodac.ludoplus.resources.game_leave_message
import net.zodac.ludoplus.resources.game_leave_title
import net.zodac.ludoplus.resources.game_leave_tutorial_message
import net.zodac.ludoplus.resources.game_leave_tutorial_title
import net.zodac.ludoplus.ui.common.LudoPlusDialog
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.icons.Symbols

/**
 * The one "Leave game?" confirmation, whichever way the player is leaving (the back arrow or the system back gesture).
 * Whoever is leaving hands the destination to [request]; [LeaveGameConfirmationDialog] shows the dialog while
 * [isShowing].
 */
class LeaveGameConfirmation {

    private var pendingLeave by mutableStateOf<(() -> Unit)?>(null)

    /** Whether the game being left is the tutorial, which the dialog words as its own. */
    internal var tutorial by mutableStateOf(false)
        private set

    val isShowing: Boolean
        get() = pendingLeave != null

    /** Asks the player to confirm leaving the game (the [tutorial], if it is); [onLeave] runs only if they do. */
    fun request(onLeave: () -> Unit, tutorial: Boolean = false) {
        this.tutorial = tutorial
        pendingLeave = onLeave
    }

    internal fun confirm() {
        val leave = pendingLeave ?: return
        pendingLeave = null
        leave()
    }

    internal fun dismiss() {
        pendingLeave = null
    }
}

val LocalLeaveGameConfirmation = staticCompositionLocalOf { LeaveGameConfirmation() }

/** Shows [confirmation]'s dialog while it has a request pending. */
@Composable
fun LeaveGameConfirmationDialog(confirmation: LeaveGameConfirmation) {
    if (!confirmation.isShowing) return
    LudoPlusDialog(
        icon = Symbols.Logout,
        title = stringResource(if (confirmation.tutorial) Res.string.game_leave_tutorial_title else Res.string.game_leave_title),
        message = stringResource(if (confirmation.tutorial) Res.string.game_leave_tutorial_message else Res.string.game_leave_message),
        confirmLabel = stringResource(Res.string.game_leave_confirm),
        onConfirm = confirmation::confirm,
        dismissLabel = stringResource(Res.string.common_cancel),
        onDismiss = confirmation::dismiss,
        onDismissRequest = confirmation::dismiss,
    )
}

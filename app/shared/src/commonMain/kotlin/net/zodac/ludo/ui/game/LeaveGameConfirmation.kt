package net.zodac.ludo.ui.game

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import net.zodac.ludo.resources.Res
import net.zodac.ludo.resources.common_cancel
import net.zodac.ludo.resources.game_leave_confirm
import net.zodac.ludo.resources.game_leave_message
import net.zodac.ludo.resources.game_leave_title
import net.zodac.ludo.ui.common.LudoPlusDialog
import net.zodac.ludo.ui.common.stringResource
import net.zodac.ludo.ui.icons.Symbols

/**
 * The one "Leave game?" confirmation, whichever way the player is leaving (the back arrow or the system back gesture).
 * Whoever is leaving hands the destination to [request]; [LeaveGameConfirmationDialog] shows the dialog while
 * [isShowing].
 */
class LeaveGameConfirmation {

    private var pendingLeave by mutableStateOf<(() -> Unit)?>(null)

    val isShowing: Boolean
        get() = pendingLeave != null

    /** Asks the player to confirm leaving the game; [onLeave] runs only if they do. */
    fun request(onLeave: () -> Unit) {
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
        title = stringResource(Res.string.game_leave_title),
        message = stringResource(Res.string.game_leave_message),
        confirmLabel = stringResource(Res.string.game_leave_confirm),
        onConfirm = confirmation::confirm,
        dismissLabel = stringResource(Res.string.common_cancel),
        onDismiss = confirmation::dismiss,
        onDismissRequest = confirmation::dismiss,
    )
}

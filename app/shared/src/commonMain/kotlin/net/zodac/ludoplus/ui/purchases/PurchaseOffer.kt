package net.zodac.ludoplus.ui.purchases

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import net.zodac.ludoplus.platform.LocalPlatformServices
import net.zodac.ludoplus.purchases.Entitlement
import net.zodac.ludoplus.purchases.LocalStore
import net.zodac.ludoplus.purchases.Product
import net.zodac.ludoplus.purchases.ProductState
import net.zodac.ludoplus.purchases.StoreState
import net.zodac.ludoplus.purchases.has
import net.zodac.ludoplus.purchases.productFor
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.common_cancel
import net.zodac.ludoplus.resources.purchase_continue
import net.zodac.ludoplus.resources.purchase_failed
import net.zodac.ludoplus.ui.common.LudoPlusDialog
import net.zodac.ludoplus.ui.common.stringResource

/**
 * Whether [state] offers [entitlement] for sale: the store has answered, and no product granting it is owned or awaiting
 * payment. Loading counts, with the offer disabled until it answers ([offerReady]).
 */
internal fun StoreState.offers(entitlement: Entitlement): Boolean = when (this) {
    StoreState.Unavailable -> false
    StoreState.Loading -> true
    is StoreState.Ready -> products.none { (product, state) -> entitlement in product.grants && state == ProductState.Owned }
}

/** Whether [entitlement]'s own product is awaiting payment: its button says so, and is disabled. */
internal fun StoreState.pending(entitlement: Entitlement): Boolean =
    this is StoreState.Ready && products.any { (product, state) -> entitlement in product.grants && state == ProductState.Pending }

/** Whether the offer can be taken up: the store has answered with [entitlement]'s own product for sale. */
internal fun StoreState.offerReady(entitlement: Entitlement): Boolean =
    this is StoreState.Ready && of(Product.entries.single { it.grants == setOf(entitlement) }) is ProductState.ForSale && !pending(entitlement)

/**
 * The dialog an offer's button opens before the store's own sheet (which shows the price): [title], [message], and,
 * where the other purchase is for sale too ([state] says the account has neither), a switch labelled [alsoLabel] that
 * makes Continue buy both at once (the combined product). Continue buys [entitlement]'s product, or the combined one,
 * telling the player when the store's sheet couldn't open. TalkBack hears the switch as "[alsoLabel], switch, off".
 */
@Composable
internal fun PurchaseOfferDialog(
    entitlement: Entitlement,
    state: StoreState.Ready,
    icon: @Composable () -> Unit,
    title: String,
    message: String,
    alsoLabel: String,
    onDismiss: () -> Unit,
) {
    val store = LocalStore.current
    val platform = LocalPlatformServices.current
    val failed = stringResource(Res.string.purchase_failed)
    val other = Entitlement.entries.single { it != entitlement }
    // Both at once only while the account has neither, and isn't awaiting payment for the other.
    val offerBoth = state.of(Product.UNLOCK_ALL_STYLES_AND_REMOVE_ADS) is ProductState.ForSale && state.has(other) == false && !state.pending(other)
    var also by rememberSaveable { mutableStateOf(false) }
    LudoPlusDialog(
        icon = icon,
        title = title,
        message = AnnotatedString(message),
        confirmLabel = stringResource(Res.string.purchase_continue),
        onConfirm = {
            onDismiss()
            if (!store.buy(state.productFor(entitlement, alsoWanted = also && offerBoth))) platform.showTransientMessage(failed)
        },
        onDismissRequest = onDismiss,
        dismissLabel = stringResource(Res.string.common_cancel),
        onDismiss = onDismiss,
        extraContent = if (!offerBoth) null else {
            {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .padding(top = 8.dp)
                        .toggleable(
                            value = also,
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Switch,
                            onValueChange = { also = it },
                        ),
                ) {
                    Text(alsoLabel, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = also, onCheckedChange = null)
                }
            }
        },
    )
}

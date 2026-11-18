package net.zodac.ludoplus.netplay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby as GoogleNearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import kotlinx.coroutines.CompletableDeferred

/**
 * [Nearby] on Google's Nearby Connections: the only code that imports it. A star ([Strategy.P2P_STAR]), the host at
 * its centre and each guest linked to it alone. A guest's link is accepted on both sides at once: the session, not the
 * radio, decides who sits where. One for the process, attached to each new Activity, as asking for permissions needs one.
 * Failures are logged under [LOG_TAG]. See `.claude/NETPLAY.md`.
 */
internal class NearbyConnections private constructor(context: Context) : Nearby {

    override val available: Boolean = true

    private val client: ConnectionsClient = GoogleNearby.getConnectionsClient(context.applicationContext)
    private val appContext = context.applicationContext

    private var launcher: ActivityResultLauncher<Array<String>>? = null
    private var asking: CompletableDeferred<Boolean>? = null

    private fun attach(activity: ComponentActivity) {
        val launcher = activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            asking?.complete(results.values.all { it })
        }
        this.launcher = launcher
        activity.lifecycle.addObserver(
            LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_DESTROY && this.launcher === launcher) this.launcher = null },
        )
    }

    override suspend fun requestPermissions(): Boolean {
        val missing = PERMISSIONS.filter { ContextCompat.checkSelfPermission(appContext, it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isEmpty()) return true
        val launcher = launcher ?: return false
        val answer = CompletableDeferred<Boolean>()
        asking = answer
        launcher.launch(missing.toTypedArray())
        return answer.await().also { asking = null }
    }

    override fun host(name: String, events: (LinkEvent) -> Unit): Hosting {
        val links = lifecycle(events)
        val advertise = {
            client.startAdvertising(name, SERVICE_ID, links, AdvertisingOptions.Builder().setStrategy(STRATEGY).build())
                .addOnFailureListener { Log.w(LOG_TAG, "startAdvertising failed", it) }
        }
        advertise()
        return object : Hosting {
            private var findable = true

            override fun send(endpoint: String, bytes: ByteArray) {
                client.sendPayload(endpoint, Payload.fromBytes(bytes)).addOnFailureListener { Log.w(LOG_TAG, "sendPayload failed", it) }
            }

            override fun setFindable(findable: Boolean) {
                if (findable == this.findable) return
                this.findable = findable
                if (findable) advertise() else client.stopAdvertising()
            }

            override fun close() {
                client.stopAdvertising()
                client.stopAllEndpoints()
            }
        }
    }

    override fun discover(found: (List<FoundGame>) -> Unit): Discovery {
        val games = LinkedHashMap<String, FoundGame>()
        val callback = object : EndpointDiscoveryCallback() {
            override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                if (info.serviceId != SERVICE_ID) return
                games[endpointId] = FoundGame(endpointId, info.endpointName)
                found(games.values.toList())
            }

            override fun onEndpointLost(endpointId: String) {
                if (games.remove(endpointId) != null) found(games.values.toList())
            }
        }
        client.startDiscovery(SERVICE_ID, callback, DiscoveryOptions.Builder().setStrategy(STRATEGY).build())
            .addOnFailureListener { Log.w(LOG_TAG, "startDiscovery failed", it) }
        return object : Discovery {
            override fun stop() = client.stopDiscovery()
        }
    }

    override fun join(game: FoundGame, name: String, events: (LinkEvent) -> Unit): Joining {
        client.requestConnection(name, game.endpoint, lifecycle(events)).addOnFailureListener {
            Log.w(LOG_TAG, "requestConnection failed", it)
            events(LinkEvent.Disconnected(game.endpoint))
        }
        return object : Joining {
            override fun send(bytes: ByteArray) {
                client.sendPayload(game.endpoint, Payload.fromBytes(bytes)).addOnFailureListener { Log.w(LOG_TAG, "sendPayload failed", it) }
            }

            override fun close() = client.disconnectFromEndpoint(game.endpoint)
        }
    }

    /** Accepts every link as it's offered, and tells [events] of it, its payloads, and its end. */
    private fun lifecycle(events: (LinkEvent) -> Unit) = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(endpointId: String, info: ConnectionInfo) {
            client.acceptConnection(endpointId, payloads(events)).addOnFailureListener { Log.w(LOG_TAG, "acceptConnection failed", it) }
        }

        override fun onConnectionResult(endpointId: String, resolution: ConnectionResolution) {
            if (resolution.status.isSuccess) {
                events(LinkEvent.Connected(endpointId))
            } else {
                Log.w(LOG_TAG, "Connection to $endpointId failed: ${resolution.status}")
                events(LinkEvent.Disconnected(endpointId))
            }
        }

        override fun onDisconnected(endpointId: String) = events(LinkEvent.Disconnected(endpointId))
    }

    private fun payloads(events: (LinkEvent) -> Unit) = object : PayloadCallback() {
        override fun onPayloadReceived(endpointId: String, payload: Payload) {
            payload.asBytes()?.let { events(LinkEvent.Received(endpointId, it)) }
        }

        override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) = Unit
    }

    companion object {
        /** Why a call failed, in logcat: `adb logcat -s LudoPlusNearby`. */
        const val LOG_TAG = "LudoPlusNearby"

        /** What a LudoPlus game advertises as: only another LudoPlus phone finds it. Permanent once released. */
        internal const val SERVICE_ID = "net.zodac.ludoplus.netplay"

        private val STRATEGY = Strategy.P2P_STAR

        /** The runtime permissions Nearby Connections needs, by Android version. */
        internal val PERMISSIONS: List<String> = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
                Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            else -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        @Volatile
        private var instance: NearbyConnections? = null

        /** Nearby for [activity], attached to it: on a device with Google Play services; [NoNearby] elsewhere. */
        fun attach(activity: ComponentActivity): Nearby {
            if (GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(activity) != ConnectionResult.SUCCESS) return NoNearby
            val nearby = instance ?: NearbyConnections(activity).also { instance = it }
            nearby.attach(activity)
            return nearby
        }
    }
}

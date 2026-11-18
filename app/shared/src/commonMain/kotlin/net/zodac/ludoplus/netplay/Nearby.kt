package net.zodac.ludoplus.netplay

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * What the shared app needs from a platform's way of finding and talking to phones in the same room (Google's Nearby
 * Connections on Android), and nothing that names it. Each platform implements it once; [NoNearby] is the version for
 * previews, tests, and builds with none (the github app). It only moves bytes: what they say is [HostSession]'s and
 * [GuestSession]'s. Every callback comes on the main thread. See `.claude/NETPLAY.md`.
 */
interface Nearby {

    /** Whether this device and build can play over it at all: Netplay's buttons show only where it can. */
    val available: Boolean

    /**
     * Asks for the permissions it needs (Bluetooth and nearby Wi-Fi, or location on older Androids), where they aren't
     * granted yet: true once they are. The player is asked when they first host or join, never before.
     */
    suspend fun requestPermissions(): Boolean

    /** Makes this phone findable as [name]'s game, telling [events] of each guest's link: until [Hosting.close]. */
    fun host(name: String, events: (LinkEvent) -> Unit): Hosting

    /** Looks for games nearby, telling [found] the whole list each time it changes: until [Discovery.stop]. */
    fun discover(found: (List<FoundGame>) -> Unit): Discovery

    /** Joins [game] as [name], telling [events] of the link to its host: until [Joining.close]. */
    fun join(game: FoundGame, name: String, events: (LinkEvent) -> Unit): Joining
}

/** What happens on a link, by the other side's endpoint id. */
sealed interface LinkEvent {
    val endpoint: String

    data class Connected(override val endpoint: String) : LinkEvent

    data class Received(override val endpoint: String, val bytes: ByteArray) : LinkEvent {
        override fun equals(other: Any?): Boolean = other is Received && endpoint == other.endpoint && bytes.contentEquals(other.bytes)

        override fun hashCode(): Int = 31 * endpoint.hashCode() + bytes.contentHashCode()
    }

    data class Disconnected(override val endpoint: String) : LinkEvent
}

/** A game found nearby: its host's [name], as it advertises it. */
data class FoundGame(val endpoint: String, val name: String)

/** A phone hosting: findable while [setFindable] says so (from the start), linked to its guests until [close]. */
interface Hosting {
    fun send(endpoint: String, bytes: ByteArray)

    /**
     * Whether phones looking for games find this one: not once the game has started, but again while a guest's seat
     * waits for it to come back. Guests already linked stay linked.
     */
    fun setFindable(findable: Boolean)

    fun close()
}

interface Discovery {
    fun stop()
}

/** A phone's link to the game it joined. */
interface Joining {
    fun send(bytes: ByteArray)

    fun close()
}

/** No Nearby: previews, tests, the github app, and a device without Google Play services. */
object NoNearby : Nearby {
    override val available: Boolean = false

    override suspend fun requestPermissions(): Boolean = false

    override fun host(name: String, events: (LinkEvent) -> Unit): Hosting = object : Hosting {
        override fun send(endpoint: String, bytes: ByteArray) = Unit

        override fun setFindable(findable: Boolean) = Unit

        override fun close() = Unit
    }

    override fun discover(found: (List<FoundGame>) -> Unit): Discovery = object : Discovery {
        override fun stop() = Unit
    }

    override fun join(game: FoundGame, name: String, events: (LinkEvent) -> Unit): Joining = object : Joining {
        override fun send(bytes: ByteArray) = Unit

        override fun close() = Unit
    }
}

val LocalNearby = staticCompositionLocalOf<Nearby> { NoNearby }

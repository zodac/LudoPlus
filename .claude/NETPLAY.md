# Ludo Plus: netplay (Nearby)

**In progress (Phase 12, from 2026-11-18).** Phones in the same room play one game together over Google's Nearby
Connections: no server, no accounts. This file is the plan and the one source on the subject. Commits go under
`[Netplay]`.

## Decisions (the maintainer, 2026-11-18)

- **Transport: Nearby Connections first.** An internet relay may follow as a second transport, behind the same seam.
- **The play app only**: Nearby is part of Google Play services, which the github app has none of.
- **Seats: the host sets them**, on its New Game form as now, with one more seat type, **Nearby player**: an open
    seat a joining phone takes. Each joining phone takes one. The host's other seats are people passing its phone, or
    CPUs, which the host plays.
- **Rules: anything the host picks**: every mode, house rule, custom mode, team setting, and player count.
- **Counting: each phone, its own seat.** Every phone records its own player's result (Leaderboard, Statistics) and
    earns its own achievements, as the device's own seat does in a game on one phone; the host records its seats as
    now. The usual rules still apply: a game with house rules or teams stays off the Leaderboard, and so on.

## Design

- **The host is the authority.** It runs the game as now (its `GameViewModel`): it rolls, applies every action, and
    sends the whole game to every phone after each change. A joining phone (a **guest**) never runs the engine: it shows
    the game it was sent, and sends the host its player's roll or move, which the host checks as it checks a tap.
- **The protocol** (`netplay/NetMessage.kt`): small JSON messages, each with a type, over Nearby's byte payloads.
    The host's game is sent as `SavedGameJson`, the format that already round-trips every state.
    - Guest to host: `Hello` (protocol version, the player's name and colour), `Roll`, `Move` (pawn, owner, die),
        `Leave`.
    - Host to guest: `Welcome` (the seat it took) or `Refused` (full, started, another version), `Lobby` (the seats and
        who has taken them), `Game` (the whole game, numbered), `Ended`.
    - A version mismatch is refused at `Hello`, with "update the app".
- **Sessions** (`HostSession`, `GuestSession`): the lobby and the game's messages as pure state machines over a
    `NetLink` (send, receive, connected): tested without radios.
- **The transport seam** (`netplay/Nearby.kt`): advertise, discover, connect, accept, send, receive, disconnect;
    `NearbyConnectionsTransport` in the play flavour (`Strategy.P2P_STAR`: the host the star's centre), `NoNearby`
    elsewhere. Its permissions (Bluetooth and nearby Wi-Fi, and location up to Android 12) are asked for when the player
    first hosts or joins, never at launch.
- **The device's own seat** is no longer always seat 1 in a Nearby game: what records and achievements count as "this
    device's" becomes a seat the game names (`localSeat`), 0 everywhere else.

## Slices

1. The protocol and the sessions, pure and tested.
2. The transport (Nearby, permissions) and the lobby: host and join from New Game, the seat list filling in.
3. The game over the link: the guest's screen showing the host's game, its own turns sent; records and achievements
    for each phone's own seat.
4. Reconnect (a guest that drops can come back to its seat), turn timeouts, and the CPU stepping in for a guest gone
    for good.
5. The privacy policy, data-safety answers, `CLAUDE.md`'s aims, and a check on two phones.

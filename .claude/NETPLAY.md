# LudoPlus: netplay (Nearby)

**In progress (Phase 12, from 2026-11-18).** Phones in the same room play one game together over Google's Nearby
Connections: no server, no accounts. This file is the plan and the one source on the subject. Commits go under
`[Netplay]`.

## Decisions (the maintainer, 2026-11-18)

- **Transport: Nearby Connections first.** An internet relay follows as a second transport, behind the same seam, in
    Phase 13, after the launch ("Remote play (Phase 13)" below).
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
    - Guest to host: `Hello` (protocol version, the player's name and colour, and, coming back, its seat's token),
        `Roll`, `Move` (pawn, owner, die), `Leave`.
    - Host to guest: `Welcome` (the seat it took, and the token that takes it back) or `Refused` (full, started,
        another version), `Lobby` (the seats and who has taken them), `Game` (the whole game, numbered), `Ended`.
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

1. **Done** (2026-11-18): the protocol and the sessions, pure and tested (`NetplaySessionsTest`).
2. **Built** (2026-11-18; untested on phones): the transport (`NearbyConnections`, its permissions asked for as the
    lobby or join page opens) and the lobby: New Game's type chip cycles Player, CPU, Nearby (where Nearby is
    available); Start with a Nearby seat opens the lobby (`PLAY_LOBBY`), whose Start is enabled once every Nearby seat is
    taken; "Join a game nearby" under New Game's Start opens the join page (`JOIN_GRAPH`), listing the games found.
3. **Built** (2026-11-18; untested on phones): the game over the link (`NetplayGameTest`). The host's `GameViewModel`
    sends the game after every change and takes a guest's roll or move only for its own seat on its turn; its own taps
    don't play a Nearby seat (`StartedGame.currentIsCpu` covers them); a guest that leaves is played by the CPU, and a
    hosted game continued later has the CPU in every Nearby seat. The guest's `GameViewModel` shows the host's game at
    its own seat (`StartedGame.guestSeat`), sends its roll and move, saves nothing, and records and earns for its own
    seat alone (`AchievementTracker(mySeat)`, `AchievementEngine.atGameStart(mySeat)`, `GameRecordRepository.recordGame`).
    A finished game can now be sent (`SavedGameJson`'s `finished` flag), never continued.
4. **Built** (2026-11-18; untested on phones): reconnecting, turn timeouts, and the CPU stepping in.
    - A guest that sends `Leave` is gone for good: the CPU takes its seat (`PlayerType.AI`) and its token is forgotten.
    - A guest whose link drops in the game is **away** (`HostSession.away`): its seat stays Nearby, the CPU plays it,
        and the host is findable again (`Hosting.setFindable`) while any seat is away. The guest
        (`GuestState.Reconnecting`, the board still up) looks for a game under its host's name for `RECONNECT_MILLIS`
        (2 minutes on screen) and says `Hello` with its token over the new link; the host gives the seat back with
        `Welcome` and the latest `Game`. A token's seat is taken back even from a link the host hasn't yet seen drop.
        Not found in time, or refused, it's `Lost`. A link lost in the lobby, or after the game, is lost for good.
    - **Turn timeout**: on the host, a guest has `GUEST_TURN_MILLIS` (60 s, while the host's game is on screen) for each
        roll and move; one it doesn't make, the CPU makes, and the rest of that turn. A roll or move from the guest
        takes the turn back.
    - The board says so in a line over the players (a polite live region): the guest's "Reconnecting to the host...",
        "Lost the host's phone.", or "The host ended the game."; the host's "Jo dropped out: the CPU plays for now"
        (or how many did) (`NetplayNoticeTest`).
5. **Done but the phones** (2026-11-18): the privacy policy (section 4a, a dropped phone's rejoining too), the
    data-safety draft (`PUBLISHING.md`, "Data safety": nothing collected, as the phones talk directly), `CLAUDE.md`'s
    aims, and `README.md` and the store description (`assets/full-description.txt`). Left for the maintainer: the check
    on two phones, which needs a Play-signed build from an internal testing track (`PLAY_GAMES.md`, "Testing"), so a
    Play Console project first, and the data-safety form itself.

Known gaps: a guest's Game Over "Play Again" starts a game on its own form; a host that leaves ends the session for
everyone (each guest sees "The host ended the game"); a guest isn't warned before its turn runs out; and a guest that
rejoins a host of the same name that isn't its own is refused, and gives up rather than trying another.

## Remote play (Phase 13)

**Planned** (the maintainer, 2026-11-18), for after the launch (`IMPLEMENTATION.md`, Phase 13). Phones in different
places play one game through a relay, as a second transport behind `NetLink`. The protocol, the sessions, the host's
authority, and each phone counting its own seat stay as they are.

- **The relay, proposed: Firebase Realtime Database on the free Spark plan.** Spark never bills: at a limit it stops
    serving. Its limits are 100 connections at once (about 50 two-player games), 1 GB stored, and 10 GB downloaded a
    month. A game sends a few KB per change, so connections are the limit that binds. Check the current limits before
    building.
- **Ruled out.** Google Play Games' real-time and turn-based multiplayer were shut down in 2020. Phone-to-phone over
    the internet (WebRTC) needs a paid TURN relay for the networks that block direct links, and its library adds
    several MB to the APK. A server of our own (Cloudflare Workers, or a free VM) has to be written, run, and patched.
    Sending each move as a message is too slow for a game of quick moves.
- **Rooms.** The host makes a room under a short join code and shares it however it likes; there's no matchmaking, so
    players only meet people they gave the code to, and nothing needs moderating. The host writes `Lobby`, `Game`, and
    `Ended` to the room; each guest writes its own `Hello`, `Roll`, `Move`, and `Leave`. Each phone signs in
    anonymously, out of sight: no account.
- **Security rules**: only the room's host writes the host's messages, a guest writes only its own, a message's size is
    capped, and a room older than a day takes no writes. App Check keeps other apps off the free quota.
- **Presence.** The database's connection state and on-disconnect writes mark a guest away, and remove a room whose
    host has gone. Spark has no Cloud Functions, so nothing runs on the server.
- **Play app only**, as Nearby: the github app stays off the network.
- **Privacy.** Game data, player names included, passes through Google's servers under an anonymous id, and goes with
    the room. The privacy policy, the data-safety form, and `CLAUDE.md`'s aims say so before release.

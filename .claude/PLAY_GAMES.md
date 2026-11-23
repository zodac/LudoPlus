# LudoPlus: Google Play Games Services

**Built for LudoPlus (Phase 10, 2026-11-18); no Play Console project yet.** Sign-in, achievements mirrored both ways,
a leaderboard per mode and player count, cloud save across devices, and Play Games Sidekick, restored from DiceFive
(`5423b6c`), where each was checked by the maintainer on real phones. In LudoPlus none has run against Play yet: the
ids are empty until the Play Console project exists ("Play Console"). This file is the one source on the subject: where
another doc disagrees, follow this one.

## What Google provides

One SDK: `com.google.android.gms:play-services-games-v2` (v22.1.0; v1 is frozen).

| Feature      | API                               | Limits that shape the design                                                                                                                                                                              |
|--------------|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Sign-in      | `GamesSignInClient`               | Automatic whenever the SDK starts. **No sign-out API**. Needs `PlayGamesSdk.initialize()` and the `APP_ID` meta-data                                                                                      |
| Player       | `PlayersClient`                   | Gamer name, avatar, id                                                                                                                                                                                    |
| Achievements | `AchievementsClient`              | ≤400; **2,000 points** total, ≤200 each, multiples of 5; one icon each (Play draws the locked look); incremental 2-10,000 steps, forward only. **Published ones can't be reset** (drafts can, by testers) |
| Leaderboards | `LeaderboardsClient`              | ≤70; ordering fixed once published; daily/weekly/all-time automatic; ties by submission time. **Can't be reset**                                                                                          |
| Saved games  | `SnapshotsClient`                 | Named blobs ≤3 MB; conflicts settled by `resolveConflict`                                                                                                                                                 |
| Sidekick     | Added by Play to uploaded bundles | Android 13+, 3 GB+ RAM, **installed from Play**; enabled per release. Locked achievements show only after 100+ players in 30 days                                                                         |

- **Cost.** The release APK grew 5.49 MB to 5.83 MB (+346 KB; ~+143 KB compressed), with **no new permission**: v2
    talks to the Play services app, so `INTERNET` isn't merged in.
- **Level Up** (Google's 2026 programme) is not a goal, so its Game Stats are out of scope.

## Architecture

```text
app/shared/.../online/   GameServices (interface, GameServicesState, OnlineAccount, LocalGameServices), NoGameServices,
                         OnlineSync (reconciler), CloudSave + CloudSaveMerge (pure), CloudSaveCodec, CloudSaveStore,
                         OnlineSettingsRepository  - nothing here names Google
app/shared/.../ui/settings/OnlineAccountCard.kt   labelled by GameServices.serviceName
app/android/src/play/.../online/  PlayGamesServices, PlayGamesIds, PlayGamesAchievements, res/values/games-ids.xml
                         - the only gms.games imports, in the play flavour alone
```

- **Local storage is the source of truth.** The game never waits on or reads from the network; a signed-out, offline
    or sideloaded app behaves as if Play didn't exist. Every call has a timeout, and errors go into `state`.
- **Mirroring is idempotent, not a queue.** `OnlineSync.reconcile()` compares local state with the service and sends
    the difference, so a failed send is retried next time. Triggers: sign-in, app start, after a game, leaving a game
    part-way, 5s after a synced-setting change, `ON_STOP` with something new to save (`needsSyncOnLeaving`), and `ON_START`
    60 seconds or more after the last sync (`needsSyncOnReturning`). Either runs anyway after a sync that didn't get
    everything through. No polling.
- **The engine and `GameMode` never learn the network exists.** Per-mode data is a field (`onlineLeaderboardKey`).
- **The play app only.** Play Games is the `play` flavour's (`DESIGN.md`, "Distribution"): the SDK, the manifest's
    `APP_ID`, Sidekick and the disabled init provider are all in `src/play`. The github APK (`net.zodac.ludoplus.github`)
    has none of it, so a Play Games credential, which is per package, can't sign it in either; it has progress files
    instead, which the play app never has, signed in or not.
- **Play-only installs.** A play release build uses Play Games only when installed by Play (`com.android.vending`) on a
    phone with Play services; a debug build never does, as its public key has no credential ("Play Console")
    (`PlayGamesServices.usesPlayGames`). Otherwise: `NoGameServices`, no card, as in the github APK. The SDK is still
    compiled into a play debug build, for its tests.
- `PlayGamesServices` needs an `Activity`, so it's built in `MainActivity` and re-attached across config changes.
- **Superuser mode** (a debug build's set rolls) needs no block of its own: a debug build never uses Play Games.

## Account and sign-in

- **Only from Settings.** The SDK isn't started (`PlayGamesSdk.initialize`) until the player taps **Sign in**, and its
    auto-start `PlayGamesInitProvider` is disabled in the manifest (`PlayGamesServicesTest`). A sign-in sets `start_at_launch`
    (the `play_games` SharedPreferences, read in `Application.onCreate`, before DataStore could be); from then on the SDK
    starts with the app and signs in quietly.
- **That choice stays on its device.** Backups and device transfers leave `play_games` out (`res/xml/backup_rules.xml`
    up to Android 11, `data_extraction_rules.xml` from 12; `PlayFlavourTest`), so a restored phone asks before signing
    in. From Android 12 everything else is backed up, the `online` store included: its mirroring block must survive a
    restore. **Up to Android 11 only the settings are** (`backup_rules.xml`): there `adb backup` and `adb restore` need
    only USB debugging, so an edited backup would otherwise reach Play Games (a file import by another name). The cloud
    save restores the rest for a signed-in player; an excluded file isn't restored either. `PlayFlavourTest` pins both.
- **A failed sign-in says why** on the card, in red: "Couldn't sign in: " and the first failure's log message
    (`GameServicesState.SignedOut.failure`), untranslated, for a player to report. It goes on the next Sign in tap.
    Cancelling the account picker shows it too ("not authenticated"): the SDK can't tell the two apart.
- **Sign out is ours** (v2 has none): it clears `start_at_launch` and stops every call. It waits for the sign-in's sync to
    finish (`OnlineSync.signInSynced`).
- **The gamer name appears only on the Settings card** and never touches seat 1's name. Sanitised, ellipsised, up to
    two lines.
- **Seat 1 reports to the signed-in account**, whatever name it holds; seats 2-4 never do.
- **Choosing the account** is Play's: its default, or long-press the icon > Choose profile. No in-app picker.
- **Accounts are kept apart.** `synced_account` (salted hash) is the last account to sync. That account, or the first
    ever, **merges**. A different one **wipes** the synced data (achievements, counters, records, synced settings, the
    game in progress; no reset marker) and restores its own, once the service has answered. Signing out wipes nothing.

## Achievements

- **Local → Play** in every reconcile, including unlocks from before linking. Progress uses forward-only steps, each
    sent on a launch's first sync and after that only when it changes (`OnlineSync.sendProgress`, kept in memory per
    account).
- **Play → local** on sign-in: Play's unlocks unlock quietly here (with their styles), restoring a reinstall.
- **Our unlock banner shows alongside Play's pop-up** while signed in: Play's at the top, ours at the bottom, so they
    don't overlap. TalkBack announces both. Ours can be held and long-pressed to open the achievement. Progress nudges
    and styles banners are unchanged.
- **Play's achievements screen** opens from the Achievements page's count banner while signed in, which carries
    Google's game controller badge, which its branding guidelines require on an element or screen that opens Play's
    screens (`GameServices.serviceIcon`; the `play` flavour's `ic_play_games_controller`, from Google's branding zip,
    developer.android.com/games/pgs/downloads).
    There is no achievements reset: the option was removed from Settings.
- **Mapping**: `PlayGamesIds.kt`, empty until the Console has ids; an achievement with no id is never sent. From the
    first id, `PlayGamesIdsTest` fails until every achievement is mapped. `PlayGamesAchievements.kt` sets each one's Play
    type and visibility (`playType`, `playHidden`): a climbing total is incremental (steps ≤10,000, so Ultramarathon's
    100,000 squares are 10 a step), a streak or one-off standard.
- **Published types and steps never change.** Once published, a rule change here keeps the type and steps as
    published. Check a new incremental achievement's type and steps in Play Console before publishing: a mismatch fails
    every unlock silently.
- **Points** by tier (`PlayConsoleAchievements.kt`, test sources; proposed 2026-11-18, for the maintainer's review):
    trivial 5, easy 10, medium 15, hard 25, very hard 50, extreme 90 (2026-11-24), Completionist 100, every Easter egg 5. That spends
    all 2,000 (`PlayConsoleExportTest`), so a new achievement needs points freed first.
- **Icons**: each its own (Play rejects two the same; `PlayConsoleExportTest` checks).

## Leaderboards

**LudoPlus (decided 2026-11-18):** a board for each mode ranked by turns and player count, 2-8 (6 modes × 7 = 42 of the 70),
each the turns in seat 1's win under the mode's own rules, smaller better. Play ranks one number, ties by submission time,
so the local Leaderboard's captures tie-break doesn't carry.

- **42 boards**, one per mode and player count (`playLeaderboardKeys`: `<GameMode.onlineLeaderboardKey>_<n>p`, as
    `classic_4p`): numeric, smaller is better, from 1, tamper protection on. Ids in `playLeaderboardIds`, empty until the
    Console has them.
- **Submitted**: the turns of seat 1's win, only under the mode's own rules (`isPrimaryPlayer` and `playedUnmodified`:
    no teams, no house rules, so no custom mode). Each reconcile sends each board the fastest win since the newest game
    that account was sent from this device (`wins_posted:<account>:<key>`, moved only once Play confirms;
    `GameRecordRepository.unpostedWin`). So a first link sends the backlog, after the cloud merge. Game Over says nothing.
- **Viewing**: "Global leaderboard" under the Leaderboard's player counts, while signed in, opens Play's board for the
    mode and count on show (spoken "Global leaderboard for Classic, 4 players, on Google Play Games"). The local tables stay.
- **No reset**: Ludo has no Reset leaderboard, so no reset marker.
- **A new mode** needs seven console boards, their icons, and an `onlineLeaderboardKey`; one not ranked by turns
    (Survival) has a null key and no boards.

## Cloud save

One snapshot, `ludo-progress` (`CloudSaveCodec.SNAPSHOT_NAME`, permanent): versioned JSON, gzipped (okio), the same
bytes a progress file seals (`DESIGN.md`, "Persistence"). The table and the notes under it are DiceFive's; Ludo's
differences: records (by `GameResult.uuid`) in place of scores, no reset marker or dismissed cards, format version 1,
and the game in progress's copies compared by `StartedGame.steps`.
Always on while signed in, with no switch and no prompt. Every sync merges; nothing is picked or thrown away.

| Data                                                                              | Merge                                                                                                                                                                                                                                                                                       |
| --------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| Achievement unlocks                                                               | Union, earliest time                                                                                                                                                                                                                                                                        |
| Achievement counters                                                              | `CounterMerge`: `SUM` keeps one share per install (`counter_share_id`, new on each reset) and adds them; `FLAGS` union; win streak `LATEST`                                                                                                                                                 |
| Scores                                                                            | Union by `ScoreEntry.uuid`, less any at or before `scores_reset_at`                                                                                                                                                                                                                         |
| Dismissed stats cards                                                             | Union, later kept; dropped once a later game under the name is in the save                                                                                                                                                                                                                  |
| Style picks, New Game setup, Statistics sort, sound, vibration, confirm-leaving   | Last writer wins per key (`synced_at:<key>`); an unstamped one (a pick an older reset unstamped) beats one never set                                                                                                                                                                        |
| In-progress game                                                                  | Newest wins (`saved_at`, never earlier than the last); of two copies of one game (`GameState.id`) the one with more rolls wins (`furtherOn`), so neither a clock set back nor another device takes back a roll; one finished here (`finished_id`) or one this app can't read is never taken |
| Animations level, Orientation, Locked and Unlocked only switches                  | Not saved: they're about the device                                                                                                                                                                                                                                                         |

- **Ids, not enums**, so an older app carries a newer one's achievements, counters and settings through a merge.
- **Versions.** Fields are only added within a `version`. An app reads a newer version's known fields but never writes
    over it (`CloudSave.writableHere`). A corrupt save is replaced by this device's.
- **Format.** Version 1 packs the records: `names` and `modes` hold each value once, and `table` is one string of rows
    (`CloudSaveCodec.encodeRecords`), each about 70 bytes, so tens of thousands fit in the service's 3 MB.
- **Size.** A save inflating past `MAX_INFLATED_BYTES` (32 MiB), or too large for the device's memory, is
    `DecodedSave.TooLarge`: it is never written over or wiped for, and the sync counts as unreachable. A save over
    `MAX_SAVE_BYTES` isn't sent: it stays on the device, and the sync counts as unreachable.
- **After a merge**, `AchievementEngine.evaluateProgress` unlocks whatever the combined totals reach (quietly, then
    mirrored, so Play's pop-up announces it).
- **The write** reopens the snapshot and merges with anything committed since the read (`RESOLUTION_POLICY_MANUAL`,
    up to 10 retries). It writes only if this device added something, and before posting wins.
- **A sync with nothing to do costs no rows.** The save is still read from the service, but one matching the hash of
    what this device left there isn't decoded, and with `CloudSaveStore.changeStamp` unchanged too (the score writes
    `ScoreRepository` has made, and the save's other parts) no row is read and nothing is written. It is kept in memory, so
    each launch's first sync does it all.
- **When.** A new unlock syncs at once, as Play announces it. An update with only counters or progress waits while the
    game screen is up (`OnlineSyncRequests.requestOutsideGame`): a finished game, leaving the screen, and going to the
    background each ask for a sync anyway.
- **An app switch with nothing to do makes no calls**, as even an empty sync reads the save from the service. Coming back
    under 60 seconds after a sync doesn't read it, so another device's progress can take that long to arrive here, or until
    this device's next sync. The wait is on the wall clock: a monotonic one stops while the phone sleeps.

## Settings card

`OnlineAccountCard`, the top card in Settings, titled `serviceName` while signed out (shared UI never says "Google");
hidden when `Unavailable`.

- Signed in: avatar (decorative), the gamer name in gold as the title (the service's name is spoken, not shown), and
    **Sign out**. Signed out: "Not signed in" and **Sign in**. Sign in stays greyed while signing in and until that
    sync ends.
- "Saved `<time>`" (`cloud_saved:<account>`, via `formatTimestamp`) under the name, on one line at 360dp: the text
    runs up to the button rather than stopping at a `ListItem`'s end padding.
- While a sign-in's sync runs (`OnlineSync.progress`), a block names the step and count ("Merging game records (5/7)") with
    a spinner and a 400ms gliding bar (a still icon and snapping under reduced motion): `StepProgress`, which the
    github app's import shows too, with the same merge steps (`MergeStep`). Seven steps (`SyncStep`):
    downloading, settings, the game in progress, achievements, game records, uploading, leaderboard wins.
- "Couldn't reach Google Play Games - will retry" while the last sync failed (`OnlineSync.unreachable`).
- **TalkBack**: one group with its status as `stateDescription`, and the sync step in the heading ("…, Merging game records,
    step 5 of 7"); the progress block itself is hidden. Pinned in `OnlineAccountCardTest`.
- About's Privacy section adds the sign-in sentence wherever a service exists (`about_privacy_body_online`,
    `OnlineLinksTest`).

## Sidekick

Play's overlay: an edge tab that slides out achievements, streaks, offers and recording tools. We don't build it.

- `res/xml/sidekick_config.xml`, through the `com.google.android.finsky.deku.OVERLAY_CONFIG` meta-data
    (`SidekickConfigTest`): **`TOP_RIGHT` in portrait and landscape**, chosen from renders. The middle-right default
    covers the Σ totals button or the cup, and `TOP_LEFT` the back arrow. Positions are physical edges (the same in
    Arabic), and a player's own drag outranks them. **Snooze 300s** in a player's first session.
- No Sidekick SDK: that's for APKs. Play adds Sidekick to an uploaded bundle.

## Play Console

- **Project**: none yet (2026-11-18). Until it exists, `games-ids.xml`'s `game_services_project_id` is 0 and the id
    maps are empty; the play app's sign-in can't work, and only a Play install would try it. When it does:
    1. Create the Play Games Services project, turn on Saved Games, and put its project id in `games-ids.xml`.
    2. Add an Android credential for each signing key's SHA-1: Play's app signing key (every install from a Play track)
        and the upload key (the `RELEASE_KEY*` secrets).
    3. **Never the debug key** (`app/android/debug.keystore`, committed to the public repo): with a credential, anyone
        could build the app from changed source and post any score or unlock to the published boards.
  A build whose key has no credential fails sign-in with Google's `DEVELOPER_ERROR`, which only logcat shows (its
  `PlayGamesServices[SignInAuthenticator]` lines print the build's SHA-1); the app sees only "not authenticated".
- **Sheets**: `scripts/export-play-console.sh` writes the gitignored `assets/play-console/`:
    - `achievements/achievements-import-<timestamp>.zip` for Achievements > Import achievements (metadata, icon
        mappings, icons; no header rows, keyed by English name). English alone for now.
    - `achievements/achievements-<timestamp>.csv` and `achievements/achievement-icons/<id>.png` (512px) for review.
    - `achievements/achievement-ids.xml` isn't the script's: it's Achievements > Get resources (Android), saved there by
        hand after an import. `playAchievementIds` (`PlayGamesIds.kt`) is filled from it.
    - `leaderboards/leaderboards-<timestamp>.csv` and `leaderboards/leaderboard-icons/<key>.png` (the mode's mark over
        the player count; `PlayConsoleLeaderboardsExportTest`), entered by hand: Play has no leaderboard import. An icon
        can change any time; a board's ordering can't once published. `playLeaderboardIds` is filled from Get resources.
- Everything stays **draft** until publishing, because only drafts can be reset.

## Release

The code is done. The rest is the maintainer's, in Play Console:

0. **Set up the project and fill the ids** ("Play Console"), and update the privacy policy and data-safety form for
   sign-in and cloud save (`PUBLISHING.md`).
1. **Publish** the achievements and boards. From then on their ids, points and ordering are permanent.
2. **Upload the release's `.aab`** with the `play-release.yml` workflow (`DESIGN.md`, "Release pipeline") to an
   internal or closed track, and make sure **Sidekick is on by default** is ticked in Play Console.
3. **Check on a Play install** (Android 13+, 3 GB+): the tab sits top-right in both orientations over no control;
   TalkBack still reaches the back arrow and header; Play's sign-in pop-up doesn't hide the logo long (its wording, "Hey
   there" or "Welcome back", is Play's own, not ours); 60fps
   holds with the overlay open on a 3-4 GB phone.
4. Run a closed test (`PUBLISHING.md`), then a 5% rollout watching crashes, ANRs and engagement.

## Testing

- **Sign-in is tested on a Play-signed build**: the bundle on an internal testing track, installed from Play. The phone
    needs Play services and, until publishing, an account on the **PGS testers** list; anyone else fails sign-in in a
    way that looks like a bug.
- **Debug APKs have no Play Games**: no card, no sign-in (their key has no credential, above). The debug APK handed to
    testers is the github app's (`debug-apk` skill); the play flavour's debug variant only runs its unit tests and lint.
    The card, the sync and the cloud save are tested against fakes (`OnlineAccountCardTest`, `OnlineSyncTest`, ...).
- **Only the Play release flow** shows Sidekick, the Play-signed build's own sign-in, and Sidekick's locked
    achievements (100+ players).
- **The sandbox emulator** has no Play: use a real phone.
- Pure logic is tested against fakes: `OnlineSyncTest`, `CloudSaveMergeTest`, `CloudSaveCodecTest`, and the stores'.

## Risks and gotchas

- **Ids and points are permanent on Google's side.** Retiring an achievement means keeping its mapping and no longer
    reporting it. A rule change that makes old wins unfair needs a **new leaderboard key**; the old board stays frozen.
- **Banned words** apply to every console string; the export goes through the `StringChecks` guard.
- **Testers must be PGS testers** until publishing.
- **The data safety form** must declare Play Games and cloud save (draft answers: `PUBLISHING.md`, "Data safety").

Play work is committed as `[Play Games]`.

[Sign-in](https://developer.android.com/games/pgs/android/android-signin) ·
[Achievements](https://developer.android.com/games/pgs/achievements) ·
[Leaderboards](https://developer.android.com/games/pgs/leaderboards) ·
[Saved games](https://developer.android.com/games/pgs/android/saved-games) ·
[Sidekick](https://developer.android.com/games/pgs/play-games-sidekick) ·
[Data disclosure](https://developer.android.com/games/pgs/data-collection)

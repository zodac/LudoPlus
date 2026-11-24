#!/usr/bin/env python3
"""
Plays a game on the last release, upgrades the app over it, and confirms that game resumes where it was left.
android-release.yml's upgrade-check job runs it on an emulator; .claude/EMULATOR.md explains the job.

It drives the app with adb and UiAutomator's dump rather than the Baseline Profile journey (Journeys.kt): the journey
runs the play app built from this commit, while this has to drive a release already out, whose labels can differ from
this commit's. Every label it needs is in the constants below, and a game saved by the old app is compared to the new
app's board by what the board says of each place on it (the yards, squares, home columns, and home, which name every
pawn), the names shown, and the whole move log, keeping only the numbers of each place, and the time, players, and
numbers of each line of the log, so a rewording in a release isn't taken for a lost game. It's left only while it waits for the player's roll, so nothing moves between its snapshot and the upgrade.

Usage:        .github/scripts/ci_upgrade_check.py <old.apk> <new.apk> <directory for a failure's files>
              Both APKs must be the github app, signed with one key, with the new one's versionCode the higher.

Exit status:  0   the game resumed and could be played on, or the last release is a retired app no upgrade reaches
              1   the old app couldn't be driven: it says nothing about the upgrade, so the job tries once more
              2   the upgrade lost or changed the game, or the new app couldn't play it: never retried

Requirements: adb, with one device connected; aapt2, under $ANDROID_HOME/build-tools
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ElementTree
from pathlib import Path

PACKAGE = "net.zodac.ludoplus.github"
ACTIVITY = f"{PACKAGE}/net.zodac.ludoplus.MainActivity"
# Releases under another applicationId, which no later release can upgrade: Android takes the new one for another app.
# 1.0.0 went out as net.zodac.ludo.github before the package became net.zodac.ludoplus (the maintainer, 2026-11-19, chose
# to let it go rather than keep the old id). Any other package is a failure.
RETIRED_PACKAGES = ("net.zodac.ludo.github",)

# What the app shows. A release that relabels one of these needs the old label kept here beside the new one until a
# release with the new one is the last release.
PLAY_LABELS = ("Play",)
START_GAME_LABELS = ("Start Game",)
CONTINUE_LABELS = ("Continue",)
CRASH_TITLE_LABELS = ("Something went wrong",)
# The die (GameDie), whatever it shows; a button only while the player can roll it.
DIE_DESCRIPTION = re.compile(r"Die( showing \d+)?$")
# The board's places for a screen reader (BoardSemantics): each yard, track square, and home-column square, and home.
# A place is a button only while it holds a pawn the player can move.
PLACE_DESCRIPTION = re.compile(r"(\w+ yard|Square \d+|\w+ home column, square \d+|Home)(,|$)")
# A pawn named at a place on the track or in a home column: the board's pawns aren't all in their yards.
PAWN_OUT = re.compile(r"^(Square \d+|\w+ home column, square \d+),.* pawn \d")
BANNER_DESCRIPTION = re.compile(r"(Achievement earned|Styles? unlocked): ")
# The board's prompt ("Player 1, tap the die to roll."), told from a name by its full stop.
SENTENCE = re.compile(r"[.!?]$")
# The move log (MoveLogPanel): its title, and the time that starts each line, a text of its own before the line's.
LOG_TITLE_LABELS = ("Moves",)
LOG_TIME = re.compile(r"\[\d{2}:\d{2}:\d{2}\]\s*$")
# TEMPORARY: releases with no move log, whose game can't be compared by it. Once 1.1.0 is released no upgrade check starts
# from one of these: remove this, and what it's used for in play_on_old_app and differences.
LOGLESS_RELEASES = ("1.0.1",)

# The player's own rolls and moves before the game is left, at the least; more, up to MAX_PLAYER_ACTIONS, until a pawn
# is out of its yard, so the saved game holds more than a new one.
PLAYER_ACTIONS = 4
MAX_PLAYER_ACTIONS = 40
# The player's rolls and moves on the new app, to show the game plays on.
RESUMED_ACTIONS = 2

# Generous: on a hosted runner's emulator a single dump can take several seconds, and three CPUs play between the
# player's turns.
STEP_TIMEOUT_SECONDS = 90
TURN_TIMEOUT_SECONDS = 180
TAP_RETRY_SECONDS = 20
POLL_SECONDS = 1
SAVE_SETTLE_SECONDS = 5
# Swipes over the log that bring up no new line before it's taken to be at its end: a starved emulator drops some.
LOG_END_SWIPES = 3
# Slow, so the list stops with the finger rather than gliding on past lines not read yet.
LOG_SWIPE_MILLIS = 2000


class DriveFailure(Exception):
    """The app didn't do what a step needed."""


class Verdict(Exception):
    """The upgrade lost or changed the game, or the upgraded app couldn't play it."""


def adb(*args, check=True, timeout=300):
    """adb's stdout. Failing with check on, it raises with what adb said."""
    result = subprocess.run(["adb", *args], capture_output=True, text=True, timeout=timeout, check=False)
    if check and result.returncode != 0:
        raise DriveFailure(f"adb {' '.join(args)} failed: {result.stdout} {result.stderr}".strip())
    return result.stdout


def parse_screen(xml_text):
    """The nodes of a UiAutomator dump."""
    return list(ElementTree.fromstring(xml_text).iter("node"))


def read_screen():
    """The screen's nodes, or an empty list when it couldn't be read just now (UiAutomator refuses mid-change)."""
    adb("shell", "rm", "-f", "/sdcard/screen.xml", check=False)
    adb("shell", "uiautomator", "dump", "/sdcard/screen.xml", check=False, timeout=120)
    xml_text = adb("exec-out", "cat", "/sdcard/screen.xml", check=False)
    try:
        return parse_screen(xml_text)
    except ElementTree.ParseError:
        return []


def find_text(nodes, labels):
    return next((node for node in nodes if node.get("text") in labels), None)


def places(nodes):
    """The board's places, in the order a screen reader takes them, as (description, node)."""
    return [(node.get("content-desc"), node) for node in nodes if PLACE_DESCRIPTION.match(node.get("content-desc", ""))]


def find_die(nodes):
    return next((node for node in nodes if DIE_DESCRIPTION.match(node.get("content-desc", ""))), None)


def has_board(nodes):
    return find_die(nodes) is not None and bool(places(nodes))


def has_banner(nodes):
    return any(BANNER_DESCRIPTION.match(node.get("content-desc", "")) for node in nodes)


def player_action(nodes):
    """
    The player's next roll or move, if there is one now: the die while it can be rolled, else the first place with a pawn
    to move. None while a CPU plays, a move is still being played out, or a banner is over the board.
    """
    if has_banner(nodes):
        return None
    die = find_die(nodes)
    if die is not None and die.get("clickable") == "true":
        return die
    return next((node for _, node in places(nodes) if node.get("clickable") == "true"), None)


def awaiting_roll(nodes):
    """The game waits on the player's roll, with no banner over it: nothing on the board moves until it's taken."""
    die = find_die(nodes)
    return die is not None and die.get("clickable") == "true" and bool(places(nodes)) and not has_banner(nodes)


def pawn_out(nodes):
    return any(PAWN_OUT.match(description) for description, _ in places(nodes))


def ready_to_leave(nodes):
    """A pawn is out, so the saved game holds more than a new one, and the player's next action is a roll, not a move."""
    return awaiting_roll(nodes) and pawn_out(nodes)


def action_key(node):
    return node.get("content-desc"), node.get("bounds")


def numbers_in(text):
    return [int(number) for number in re.findall(r"\d+", text)]


def is_name(text):
    """True for a text on the board that names something in the game: not a sentence, nor the move log's title or times."""
    return bool(text) and not SENTENCE.search(text) and text not in LOG_TITLE_LABELS and not LOG_TIME.match(text)


def find_log(nodes):
    """The move log's list: what scrolls and holds the lines' times. None if the screen has none."""
    return next(
        (node for node in nodes if node.get("scrollable") == "true" and any(LOG_TIME.match(text.get("text", "")) for text in node.iter("node"))),
        None,
    )


def log_lines(log):
    """The lines of the move log's list that are on screen, newest first, as (time, what happened)."""
    texts = [node.get("text") for node in log.iter("node") if node.get("text")]
    return [(time_text.strip(), line) for time_text, line in zip(texts, texts[1:]) if LOG_TIME.match(time_text) and not LOG_TIME.match(line)]


def joined_lines(lines, page):
    """lines with the page under them, which starts with some of their last. None if it doesn't: lines between were missed."""
    if not lines:
        return page
    for overlap in range(min(len(lines), len(page)), 0, -1):
        if lines[-overlap:] == page[:overlap]:
            return lines + page[overlap:]
    return None


def log_entry(line, names):
    """A line of the log with its wording left out: its time, the players it names in order, and its numbers."""
    time_text, happened = line
    return [time_text, sorted((name for name in names if name in happened), key=happened.index), numbers_in(happened)]


def snapshot(nodes, log=None):
    """
    What a saved game has to bring back: the names the board shows, each place's numbers in order (a square's number, and
    each pawn's number and squares from home there; a yard's pawns), and every line of the move log (log, as read_log
    gives it; None where it wasn't read). Of a description only the numbers are kept, and of a line of the log what
    log_entry keeps, so rewording one in a release isn't taken for a lost game. Neither the board's prompt (a sentence:
    worded, not saved) nor the die (it shows the last roll until the next) is kept.
    """
    names = sorted(node.get("text") for node in nodes if is_name(node.get("text")))
    return {
        "names": names,
        "places": [numbers_in(description) for description, _ in places(nodes)],
        "log": None if log is None else [log_entry(line, names) for line in log],
    }


def differences(before, after):
    """Each part of a snapshot that isn't what it was, in words. Empty if the game is the same."""
    changed = []
    if before["names"] != after["names"]:
        changed.append(f"names: was {before['names']}, now {after['names']}")
    if len(before["places"]) != len(after["places"]):
        changed.append(f"places: was {len(before['places'])}, now {len(after['places'])}")
    else:
        changed += [
            f"place {index + 1}: was {was}, now {now}"
            for index, (was, now) in enumerate(zip(before["places"], after["places"]))
            if was != now
        ]
    # TEMPORARY (LOGLESS_RELEASES): no log was read before, so there's none to compare.
    if before["log"] is not None:
        changed += log_differences(before["log"], after["log"] or [])
    return changed


def log_differences(before, after):
    if len(before) != len(after):
        return [f"move log: was {len(before)} lines, now {len(after)}"]
    return [f"move log, line {index + 1}: was {was}, now {now}" for index, (was, now) in enumerate(zip(before, after)) if was != now]


def centre(node):
    left, top, right, bottom = (int(number) for number in re.findall(r"\d+", node.get("bounds")))
    return (left + right) // 2, (top + bottom) // 2


def tap(node):
    x, y = centre(node)
    adb("shell", "input", "tap", str(x), str(y))


def check_not_crashed(nodes):
    """The app's crash page, which is also where a saved game that can't be resumed goes, ends the check at once."""
    if find_text(nodes, CRASH_TITLE_LABELS) is not None:
        raise DriveFailure("the app's crash page is showing")


def wait_for(what, condition, timeout=STEP_TIMEOUT_SECONDS):
    """
    The first answer of condition(nodes) that isn't None, False, or empty, read from the screen until timeout, or a
    failure naming what. Not by truth: a node without children, as most are, is false to Python.
    """
    deadline = time.monotonic() + timeout
    nodes = []
    while time.monotonic() < deadline:
        nodes = read_screen()
        check_not_crashed(nodes)
        answer = condition(nodes)
        if answer is not None and answer is not False and answer != []:
            return answer
        time.sleep(POLL_SECONDS)
    on_screen = " | ".join(filter(None, (node.get("text") or node.get("content-desc") for node in nodes)))
    raise DriveFailure(f"{what} never happened (on screen: {on_screen[:600]})")


def tap_until(what, find, done):
    """
    Taps what find(nodes) returns until done(nodes) says it took: a starved emulator drops taps. Tapped again only after
    TAP_RETRY_SECONDS without done, and never while done holds, so a slow tap isn't doubled.
    """
    deadline = time.monotonic() + STEP_TIMEOUT_SECONDS
    while time.monotonic() < deadline:
        target = wait_for(what, lambda nodes: True if done(nodes) else find(nodes))
        if target is True:
            return
        tap(target)
        retry_at = time.monotonic() + TAP_RETRY_SECONDS
        while time.monotonic() < retry_at:
            nodes = read_screen()
            check_not_crashed(nodes)
            if done(nodes):
                return
            time.sleep(POLL_SECONDS)
    raise DriveFailure(f"tapping {what} never took")


def take_action():
    """The player's next roll or move, waited for through the CPUs' turns, and tapped until the board takes it."""
    before = action_key(wait_for("the player's turn", player_action, timeout=TURN_TIMEOUT_SECONDS))

    def taken(nodes):
        action = player_action(nodes)
        return action is None or action_key(action) != before

    tap_until("the player's roll or move", player_action, taken)


def read_log():
    """
    Every line of the move log, newest first, scrolled through from its top. The game has to be waiting on the player's
    roll: a move adds a line and brings the log back to its top.
    """
    lines = []
    unmoved = 0
    while unmoved < LOG_END_SWIPES:
        log = wait_for("the move log", find_log)
        joined = joined_lines(lines, log_lines(log))
        if joined is None:
            raise DriveFailure("the move log scrolled past lines that weren't read")
        unmoved = 0 if len(joined) > len(lines) else unmoved + 1
        lines = joined
        left, top, right, bottom = numbers_in(log.get("bounds"))
        x, quarter = (left + right) // 2, (bottom - top) // 4
        adb("shell", "input", "swipe", str(x), str(bottom - quarter), str(x), str(top + quarter), str(LOG_SWIPE_MILLIS))
    return lines


def apk_package(apk):
    """The applicationId an APK installs as, read by the newest aapt2 under $ANDROID_HOME."""
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or "")
    tools = sorted(sdk.glob("build-tools/*/aapt2"), key=lambda path: [int(part) for part in re.findall(r"\d+", path.parent.name)])
    if not tools:
        raise DriveFailure(f"no aapt2 under {sdk}/build-tools")
    result = subprocess.run([str(tools[-1]), "dump", "packagename", str(apk)], capture_output=True, text=True, timeout=120, check=False)
    if result.returncode != 0:
        raise DriveFailure(f"aapt2 couldn't read {apk}: {result.stderr.strip()}")
    return result.stdout.strip()


def upgrade_reaches(old_package, new_package):
    """
    True if the new app can upgrade the old one; False if the old one is a retired app (RETIRED_PACKAGES) no upgrade
    reaches. Any other mismatch is a verdict: an upgrade that would leave every player's game behind.
    """
    if new_package != PACKAGE:
        raise Verdict(f"the new APK is {new_package}, not {PACKAGE}")
    if old_package == PACKAGE:
        return True
    if old_package in RETIRED_PACKAGES:
        return False
    raise Verdict(f"the last release is {old_package}, which {new_package} can't upgrade")


def installed_times():
    dump = adb("shell", "dumpsys", "package", PACKAGE)
    first = re.search(r"firstInstallTime=(.+)", dump)
    last = re.search(r"lastUpdateTime=(.+)", dump)
    version = re.search(r"versionName=(\S+)", dump)
    return (first and first.group(1).strip(), last and last.group(1).strip(), version and version.group(1))


def launch():
    adb("shell", "am", "start", "-W", "-n", ACTIVITY)


def play_on_old_app(old_apk):
    """A fresh install of the old app with a game left waiting on the player's roll. Returns the board as it was left."""
    adb("uninstall", PACKAGE, check=False)
    adb("install", "--no-incremental", str(old_apk))
    version = installed_times()[2]
    print(f"Installed the old app, version {version}", flush=True)
    launch()
    tap_until("Play", lambda nodes: find_text(nodes, PLAY_LABELS), lambda nodes: find_text(nodes, START_GAME_LABELS) is not None)
    tap_until("Start Game", lambda nodes: find_text(nodes, START_GAME_LABELS), has_board)
    actions = 0
    # Read at the player's turn: the last action can be a roll that leaves a move to pick, which is taken too.
    while True:
        nodes = wait_for("the player's turn", lambda nodes: nodes if player_action(nodes) is not None else None, timeout=TURN_TIMEOUT_SECONDS)
        if actions >= PLAYER_ACTIONS and ready_to_leave(nodes):
            break
        if actions == MAX_PLAYER_ACTIONS:
            raise DriveFailure(f"no roll to wait on with a pawn out of its yard after {actions} of the player's rolls and moves")
        take_action()
        actions += 1
    wait_for("the player's roll", awaiting_roll, timeout=TURN_TIMEOUT_SECONDS)
    time.sleep(SAVE_SETTLE_SECONDS)
    nodes = wait_for("the player's roll", lambda nodes: nodes if awaiting_roll(nodes) else None)
    if version in LOGLESS_RELEASES:
        print(f"::notice::{version} has no move log: the game is compared without it", flush=True)
        left = snapshot(nodes)
    else:
        left = snapshot(nodes, read_log())
    print(f"The game as it was left, after {actions} of the player's rolls and moves: {left}", flush=True)
    return left


def upgrade(new_apk):
    """The new app over the old one, from the background, as a store's update would find it."""
    adb("shell", "input", "keyevent", "KEYCODE_HOME")
    time.sleep(SAVE_SETTLE_SECONDS)
    first_before, _, old_version = installed_times()
    adb("install", "-r", "--no-incremental", str(new_apk))
    first_after, _, new_version = installed_times()
    if first_after != first_before or new_version == old_version:
        raise Verdict(f"not an upgrade: {old_version} installed {first_before}, then {new_version} installed {first_after}")
    print(f"Upgraded {old_version} to {new_version}", flush=True)


def resume_on_new_app(left):
    """Continues the game on the new app: the same board, and one that still plays."""
    launch()
    tap_until("Continue", lambda nodes: find_text(nodes, CONTINUE_LABELS), has_board)
    wait_for("the player's roll", awaiting_roll)
    time.sleep(SAVE_SETTLE_SECONDS)
    nodes = wait_for("the player's roll", lambda nodes: nodes if awaiting_roll(nodes) else None)
    resumed = snapshot(nodes, None if left["log"] is None else read_log())
    print(f"The game as it resumed: {resumed}", flush=True)
    changed = differences(left, resumed)
    if changed:
        raise Verdict("the game changed in the upgrade: " + "; ".join(changed))
    try:
        for _ in range(RESUMED_ACTIONS):
            take_action()
    except DriveFailure as error:
        raise Verdict(f"the resumed game didn't play on: {error}") from error
    print("The game resumed as it was left, and plays on", flush=True)


def save_failure_files(directory):
    """Best effort: a capture that fails mustn't hide the failure itself."""
    directory.mkdir(parents=True, exist_ok=True)
    try:
        (directory / "upgrade-failure.xml").write_text(adb("exec-out", "cat", "/sdcard/screen.xml", check=False))
        (directory / "upgrade-failure-logcat.txt").write_text(adb("logcat", "-d", check=False))
        with (directory / "upgrade-failure.png").open("wb") as screen:
            subprocess.run(["adb", "exec-out", "screencap", "-p"], stdout=screen, timeout=60, check=False)
    except (OSError, subprocess.SubprocessError) as error:
        print(f"The failure's files weren't all saved: {error}", file=sys.stderr)


def run(old_apk, new_apk, failure_directory):
    """The whole check, returning the exit status in this file's header."""
    try:
        old_package = apk_package(old_apk)
        if not upgrade_reaches(old_package, apk_package(new_apk)):
            print(f"::notice::The last release is a retired app ({old_package}) that no upgrade reaches: nothing to check", flush=True)
            return 0
    except (DriveFailure, Verdict) as error:
        print(f"::error::The upgrade check failed: {error}", flush=True)
        return 2
    try:
        left = play_on_old_app(old_apk)
    except (DriveFailure, subprocess.SubprocessError) as error:
        print(f"::warning::The old app couldn't be driven: {error}", flush=True)
        save_failure_files(failure_directory)
        return 1
    try:
        upgrade(new_apk)
        resume_on_new_app(left)
    except (DriveFailure, Verdict, subprocess.SubprocessError) as error:
        print(f"::error::The upgrade check failed: {error}", flush=True)
        save_failure_files(failure_directory)
        return 2
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(f"usage: {sys.argv[0]} <old.apk> <new.apk> <directory for a failure's files>")
    sys.exit(run(Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])))

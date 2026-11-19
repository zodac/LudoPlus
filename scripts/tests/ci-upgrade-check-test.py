#!/usr/bin/env python3
"""
Tests the parts of .github/scripts/ci_upgrade_check.py that need no device: reading the board, what counts as the game
having changed, and which last release an upgrade reaches.

Usage: python3 -I scripts/tests/ci-upgrade-check-test.py   (from the repository root)
"""
import importlib.util
import sys
import unittest
from pathlib import Path

sys.dont_write_bytecode = True

SCRIPT = Path(__file__).resolve().parents[2] / ".github" / "scripts" / "ci_upgrade_check.py"
spec = importlib.util.spec_from_file_location("ci_upgrade_check", SCRIPT)
check = importlib.util.module_from_spec(spec)
spec.loader.exec_module(check)


def board(*nodes):
    """A UiAutomator dump of nodes given as (text, description, clickable)."""
    body = "".join(
        f'<node text="{text}" content-desc="{description}" clickable="{clickable}" bounds="[10,20][30,60]" />'
        for text, description, clickable in nodes
    )
    return check.parse_screen(f"<hierarchy>{body}</hierarchy>")


LEFT = (
    ("Player 1", "", "false"),
    ("Joker", "", "false"),
    ("Player 1, tap the die to roll.", "", "false"),
    ("", "Die showing 4", "true"),
    ("", "Red yard, 3 pawns", "false"),
    ("", "Green yard, 4 pawns", "false"),
    ("", "Square 1, Red start, safe for Red, empty", "false"),
    ("", "Square 16, Red pawn 3, 54 squares from home", "false"),
    ("", "Red home column, square 1, empty", "false"),
    ("", "Home, empty", "false"),
)


def replaced(nodes, old, new):
    """nodes with each one whose text or description starts with old replaced by new."""
    return [new if any(field.startswith(old) for field in node[:2]) else node for node in nodes]


class UpgradeCheckTest(unittest.TestCase):

    def test_a_snapshot_holds_the_names_and_each_places_numbers_in_order(self):
        self.assertEqual(
            {"names": ["Joker", "Player 1"], "places": [[3], [4], [1], [16, 3, 54], [1], []]},
            check.snapshot(board(*LEFT)),
        )

    def test_a_rewording_in_a_release_is_not_a_changed_game(self):
        reworded = replaced(LEFT, "Square 16, Red pawn 3, 54 squares from home", ("", "Square 16, Red pawn 3, 54 to go", "false"))
        reworded = replaced(reworded, "Player 1, tap the die to roll.", ("Your roll, Player 1.", "", "false"))
        reworded = replaced(reworded, "Die showing 4", ("", "Die", "true"))
        self.assertEqual([], check.differences(check.snapshot(board(*LEFT)), check.snapshot(board(*reworded))))

    def test_a_moved_pawn_changed_name_or_lost_game_is_named(self):
        before = check.snapshot(board(*LEFT))
        moved = replaced(LEFT, "Square 16, Red pawn 3, 54 squares from home", ("", "Square 16, empty", "false"))
        moved = replaced(moved, "Red home column, square 1, empty", ("", "Red home column, square 1, Red pawn 3, 5 squares from home", "false"))
        self.assertEqual(
            ["place 4: was [16, 3, 54], now [16]", "place 5: was [1], now [1, 3, 5]"],
            check.differences(before, check.snapshot(board(*moved))),
        )
        renamed = replaced(LEFT, "Joker", ("Classic", "", "false"))
        self.assertEqual(
            ["names: was ['Joker', 'Player 1'], now ['Classic', 'Player 1']"],
            check.differences(before, check.snapshot(board(*renamed))),
        )
        self.assertEqual(
            ["names: was ['Joker', 'Player 1'], now []", "places: was 6, now 0"],
            check.differences(before, check.snapshot(board())),
        )

    def test_the_players_action_is_the_rollable_die_else_a_movable_place(self):
        self.assertEqual("Die showing 4", check.player_action(board(*LEFT)).get("content-desc"))
        moving = replaced(LEFT, "Die showing 4", ("", "Die showing 6", "false"))
        moving = replaced(moving, "Red yard", ("", "Red yard, 3 pawns", "true"))
        self.assertEqual("Red yard, 3 pawns", check.player_action(board(*moving)).get("content-desc"))
        cpu = replaced(LEFT, "Die showing 4", ("", "Die showing 4", "false"))
        self.assertIsNone(check.player_action(board(*cpu)))
        self.assertIsNone(check.player_action(board(*LEFT, ("", "Achievement earned: First Steps. Bring a pawn out", "false"))))

    def test_the_game_is_left_only_while_it_awaits_the_players_roll_with_no_banner(self):
        self.assertTrue(check.awaiting_roll(board(*LEFT)))
        cpu = replaced(LEFT, "Die showing 4", ("", "Die showing 4", "false"))
        self.assertFalse(check.awaiting_roll(board(*cpu)))
        self.assertFalse(check.awaiting_roll(board(*LEFT, ("", "Achievement earned: First Steps. Bring a pawn out", "false"))))
        self.assertFalse(check.awaiting_roll(board(("", "Die", "true"))))

    def test_a_pawn_out_is_one_on_the_track_or_in_a_home_column(self):
        self.assertTrue(check.pawn_out(board(*LEFT)))
        in_column = replaced(LEFT, "Square 16, Red pawn 3", ("", "Square 16, empty", "false"))
        self.assertFalse(check.pawn_out(board(*in_column)))
        in_column = replaced(in_column, "Red home column, square 1", ("", "Red home column, square 1, Red pawn 3, 5 squares from home", "false"))
        self.assertTrue(check.pawn_out(board(*in_column)))

    def test_an_upgrade_reaches_the_same_package_skips_a_retired_one_and_fails_any_other(self):
        self.assertTrue(check.upgrade_reaches(check.PACKAGE, check.PACKAGE))
        self.assertFalse(check.upgrade_reaches("net.zodac.ludo.github", check.PACKAGE))
        with self.assertRaises(check.Verdict):
            check.upgrade_reaches("net.zodac.other.github", check.PACKAGE)
        with self.assertRaises(check.Verdict):
            check.upgrade_reaches(check.PACKAGE, "net.zodac.ludoplus")

    def test_the_crash_page_ends_a_step(self):
        with self.assertRaises(check.DriveFailure):
            check.check_not_crashed(board(("Something went wrong", "", "false")))
        check.check_not_crashed(board(*LEFT))


if __name__ == "__main__":
    unittest.main()

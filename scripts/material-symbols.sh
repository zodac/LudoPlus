#!/usr/bin/env bash
# Writes app/shared/.../ui/icons/Symbols.kt: every Material Symbols glyph the app draws, as an ImageVector built from
# Google's own path data (github.com/google/material-design-icons, symbols/web). Rounded, filled (fill 1, weight 400,
# grade 0, optical size 24) unless an entry says otherwise.
#
# To add a glyph: add a line to GLYPHS below - Kotlin name, the Symbol's name as fonts.google.com/icons lists it, then
# "fill0" for an unfilled one and "mirror" for one that flips in a right-to-left language - and run this. Needs the
# network.
#
# Usage: scripts/material-symbols.sh
set -euo pipefail

root_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output="${root_dir}/app/shared/src/commonMain/kotlin/net/zodac/ludoplus/ui/icons/Symbols.kt"

python3 -I - "${output}" <<'PYTHON'
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

GLYPHS = """
Abc abc
AcUnit ac_unit
Add add
Air air
Anchor anchor
ArrowBack arrow_back mirror
ArrowDownward arrow_downward
ArrowDropDown arrow_drop_down
ArrowForward arrow_forward mirror
ArrowOutward arrow_outward
ArrowUpward arrow_upward
AutoAwesome auto_awesome
AvgPace avg_pace
BackHand back_hand
Bed bed
Block block
Bolt bolt
Calculate calculate
Casino casino
Castle castle
Chair chair
Check check
CheckCircle check_circle
ChessPawn chess_pawn
CleaningServices cleaning_services
Close close
ContentCut content_cut
Cookie cookie
Crown crown
Cycle cycle
Cyclone cyclone
Dangerous dangerous
DeleteForever delete_forever
DeliveryTruckSpeed delivery_truck_speed
DeveloperMode developer_mode
DirectionsRun directions_run
Diversity3 diversity_3
DoorOpen door_open
Doorbell doorbell
Download download
E911Emergency e911_emergency
Elderly elderly
EmojiEvents emoji_events
EventRepeat event_repeat
Favorite favorite
FileOpen file_open
Filter4 filter_4
Filter6 filter_6
Filter7 filter_7
Fireplace fireplace
Flag flag
FormatListNumbered format_list_numbered
FormatPaint format_paint
Gavel gavel
GridViewOutlined grid_view fill0
GroupRemove group_remove
Handshake handshake
Healing healing
HeartBroken heart_broken
Help help mirror
HelpOutline help fill0 mirror
History history
HistoryEdu history_edu
HolidayVillage holiday_village
HomePin home_pin
HourglassEmpty hourglass_empty
House house
Info info
KeyboardArrowDown keyboard_arrow_down
KeyboardArrowLeft keyboard_arrow_left mirror
KeyboardArrowRight keyboard_arrow_right mirror
KeyboardArrowUp keyboard_arrow_up
KeyboardDoubleArrowDown keyboard_double_arrow_down
LocalCafe local_cafe
LocalFireDepartment local_fire_department
LocalFlorist local_florist
LocalShipping local_shipping
Lock lock
LockOpen lock_open
Logout logout mirror
Looks3 looks_3
LooksOne looks_one
Luggage luggage
LunchDining lunch_dining
Memory memory
MilitaryTech military_tech
ModeHeat mode_heat
MotionPhotosOff motion_photos_off
OpenInNew open_in_new mirror
OtherHouses other_houses
Palette palette
Pause pause
Person person
PersonRemove person_remove
PersonalInjury personal_injury
PestControl pest_control
PhotoCamera photo_camera
PlayArrow play_arrow
Public public
PushPin push_pin
QuestionMark question_mark
Quiz quiz
Radar radar
Raven raven
Redo redo mirror
Remove remove
Repeat repeat
Replay replay
Restaurant restaurant
RocketLaunch rocket_launch
Savings savings
Schedule schedule
ScreenRotation screen_rotation
SentimentNeutral sentiment_neutral
SentimentVerySatisfied sentiment_very_satisfied
Shield shield
SkipPrevious skip_previous
SmartToy smart_toy
SocialLeaderboard social_leaderboard
Sort sort mirror
Speed speed
SportsEsports sports_esports
SportsMma sports_mma
Sprint sprint
Stairs2 stairs_2
Star star
Stars stars
Start start mirror
StayCurrentLandscape stay_current_landscape
StayCurrentPortrait stay_current_portrait
Steps steps
Storefront storefront
Straighten straighten
Style style
Swords swords
Sync sync
SyncAlt sync_alt
Target target
Texture texture
Timeline timeline
Timer timer
TravelExplore travel_explore
TrendingDown trending_down mirror
TrendingUp trending_up mirror
UTurnLeft u_turn_left
Undo undo mirror
Upgrade upgrade
Vibration vibration
VolumeUp volume_up mirror
VolunteerActivism volunteer_activism
Wallpaper wallpaper
WavingHand waving_hand
WbSunny wb_sunny
WorkspacePremium workspace_premium
WrongLocation wrong_location
"""

BASE = "https://raw.githubusercontent.com/google/material-design-icons/master/symbols/web"


def fetch(line):
    kotlin, name, *flags = line.split()
    fill = "" if "fill0" in flags else "_fill1"
    with urllib.request.urlopen(f"{BASE}/{name}/materialsymbolsrounded/{name}{fill}_24px.svg", timeout=30) as response:
        svg = response.read().decode()
    paths = re.findall(r'<path[^>]* d="([^"]+)"', svg)
    if not paths or re.search(r"<(?!/?svg|path)[a-z]", svg):
        sys.exit(f"{name}: expected one or more plain paths")
    view_box = re.search(r'viewBox="([^"]+)"', svg)
    # Nearly every Symbol is on a 960 grid with y from -960 to 0; a few still carry the old 24 grid, with no viewBox.
    grid = 960 if view_box and view_box.group(1) == "0 -960 960 960" else 24
    if view_box and grid == 24:
        sys.exit(f"{name}: unexpected viewBox {view_box.group(1)}")
    mirror = ", autoMirror = true" if "mirror" in flags else ""
    grid_arg = "" if grid == 960 else ", grid = 24"
    note = f"{name}{', unfilled' if not fill else ''}"
    return f'    /** `{note}` */\n    val {kotlin}: ImageVector by lazy {{ symbol("{kotlin}", "{"".join(paths)}"{grid_arg}{mirror}) }}\n'


lines = [l for l in GLYPHS.strip().splitlines() if l.strip()]
if [l.split()[0] for l in lines] != sorted(l.split()[0] for l in lines):
    sys.exit("Keep GLYPHS in alphabetical order")
with ThreadPoolExecutor(16) as pool:
    entries = list(pool.map(fetch, lines))

source = f"""package net.zodac.ludoplus.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

// Generated by scripts/material-symbols.sh - add a glyph there and run it, rather than editing this by hand.

/**
 * Every Material Symbols glyph the app draws: Google's Material Symbols (Apache 2.0, github.com/google/material-design-icons),
 * Rounded and filled (fill 1, weight 400, grade 0, optical size 24) unless its note says unfilled. Each is built from
 * Google's own path data the first time it's used. A glyph that points a way (an arrow, a question mark) flips in a
 * right-to-left language, as Material's AutoMirrored icons did.
 */
object Symbols {{
{"".join(entries)}}}

/**
 * [pathData] as a 24dp [ImageVector], filled in black for the caller to tint. On a 960 [grid] the path's y runs from -960
 * at the top to 0, as Symbols draws; the older 24 grid runs from 0.
 */
private fun symbol(name: String, pathData: String, grid: Int = 960, autoMirror: Boolean = false): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = grid.toFloat(),
        viewportHeight = grid.toFloat(),
        autoMirror = autoMirror,
    )
        .addGroup(translationY = if (grid == 960) 960f else 0f)
        .addPath(addPathNodes(pathData), fill = SolidColor(Color.Black))
        .clearGroup()
        .build()
"""
with open(sys.argv[1], "w") as f:
    f.write(source)
print(f"{len(entries)} glyphs")
PYTHON

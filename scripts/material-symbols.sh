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
output="${root_dir}/app/shared/src/commonMain/kotlin/net/zodac/ludo/ui/icons/Symbols.kt"

python3 -I - "${output}" <<'PYTHON'
import re
import sys
import urllib.request
from concurrent.futures import ThreadPoolExecutor

GLYPHS = """
Abc abc
AcUnit ac_unit
Acute acute
Add add
Air air
Album album
AllInclusive all_inclusive
Anchor anchor
Android android
ArrowBack arrow_back mirror
ArrowDownward arrow_downward
ArrowDropDown arrow_drop_down
ArrowForward arrow_forward mirror
ArrowUpward arrow_upward
AssignmentTurnedIn assignment_turned_in
AutoAwesome auto_awesome
AvgPace avg_pace
BackHand back_hand
Badge badge
BarChart bar_chart
Block block
Bolt bolt
Cabin cabin
Calculate calculate
Cancel cancel
Casino casino
Castle castle
Celebration celebration
Chair chair
Check check
CheckCircle check_circle
Close close
ContentCut content_cut
Cottage cottage
Crown crown
Dangerous dangerous
DeleteForever delete_forever
DeleteSweep delete_sweep
DeveloperMode developer_mode
DirectionsRun directions_run
Diversity3 diversity_3
DoorOpen door_open
Download download
Elderly elderly
EmojiEvents emoji_events
EmojiPeople emoji_people
EventRepeat event_repeat
FastForward fast_forward
Favorite favorite
FileOpen file_open
Filter4 filter_4
Filter6 filter_6
Filter7 filter_7
FilterNone filter_none
FitnessCenter fitness_center
Flag flag
FlashOn flash_on
FlutterDash flutter_dash
FormatListNumbered format_list_numbered
FormatPaint format_paint
Functions functions
Gavel gavel
GridView grid_view
GridViewOutlined grid_view fill0
Groups groups
Healing healing
HealthAndSafety health_and_safety
HeartBroken heart_broken
Help help mirror
HelpOutline help fill0 mirror
Hiking hiking
History history
HistoryEdu history_edu
HolidayVillage holiday_village
HomeWork home_work
HourglassEmpty hourglass_empty
House house
Info info
KeyboardArrowDown keyboard_arrow_down
KeyboardArrowLeft keyboard_arrow_left mirror
KeyboardArrowRight keyboard_arrow_right mirror
KeyboardArrowUp keyboard_arrow_up
KeyboardDoubleArrowDown keyboard_double_arrow_down
KeyboardDoubleArrowUp keyboard_double_arrow_up
Landscape landscape
Landslide landslide
LocalCafe local_cafe
LocalFireDepartment local_fire_department
LocalFlorist local_florist
LocalShipping local_shipping
Lock lock
LockOpen lock_open
Logout logout mirror
Looks3 looks_3
Looks6 looks_6
LooksOne looks_one
Memory memory
MenuBook menu_book
MilitaryTech military_tech
ModeHeat mode_heat
MotionPhotosOff motion_photos_off
MoveUp move_up
MusicNote music_note
MyLocation my_location
NearMe near_me
Numbers numbers
OpenInNew open_in_new mirror
OtherHouses other_houses
Palette palette
Pause pause
PedalBike pedal_bike
Person person
PersonRemove person_remove
PersonalInjury personal_injury
PestControl pest_control
PhotoCamera photo_camera
PlayArrow play_arrow
Psychology psychology
Public public
QuestionMark question_mark
Quiz quiz
RecordVoiceOver record_voice_over
Redo redo mirror
Remove remove
Repeat repeat
Replay replay
RestartAlt restart_alt
Restaurant restaurant
Rocket rocket
RocketLaunch rocket_launch
Savings savings
Schedule schedule
School school
ScreenRotation screen_rotation
SentimentDissatisfied sentiment_dissatisfied
SentimentNeutral sentiment_neutral
Signature signature
Skull skull
SlowMotionVideo slow_motion_video
SmartToy smart_toy
SocialLeaderboard social_leaderboard
Sort sort mirror
Speed speed
SportsBar sports_bar
SportsEsports sports_esports
Sprint sprint
Stairs2 stairs_2
Star star
Stars stars
Start start mirror
StayCurrentLandscape stay_current_landscape
StayCurrentPortrait stay_current_portrait
Storefront storefront
Style style
Swords swords
Sync sync
SyncAlt sync_alt
Target target
Texture texture
ThumbDown thumb_down
ThumbUp thumb_up
Timeline timeline
Timer timer
TravelExplore travel_explore
TrendingDown trending_down mirror
TrendingUp trending_up mirror
Undo undo mirror
Update update
Upgrade upgrade
Vibration vibration
Visibility visibility
VolumeUp volume_up mirror
Wallpaper wallpaper
WavingHand waving_hand
WbSunny wb_sunny
Whatshot whatshot
WorkspacePremium workspace_premium
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

source = f"""package net.zodac.ludo.ui.icons

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

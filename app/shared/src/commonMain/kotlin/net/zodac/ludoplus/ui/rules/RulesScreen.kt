package net.zodac.ludoplus.ui.rules

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.SecondaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabPosition
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.MeasureScope
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.lerp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.zodac.ludoplus.resources.app_name
import net.zodac.ludoplus.resources.Res
import net.zodac.ludoplus.resources.rules_house_rule_line
import net.zodac.ludoplus.resources.rules_page_count
import net.zodac.ludoplus.resources.rules_page_in_group_spoken
import net.zodac.ludoplus.resources.rules_title
import net.zodac.ludoplus.resources.rules_tutorial_start
import net.zodac.ludoplus.ui.common.FooterPill
import net.zodac.ludoplus.ui.common.isLargeFont
import net.zodac.ludoplus.ui.common.LocalLandscapePage
import net.zodac.ludoplus.ui.common.LocalReduceMotion
import net.zodac.ludoplus.ui.common.MAIN_ACTION_HEIGHT
import net.zodac.ludoplus.ui.common.parseInlineMarkup
import net.zodac.ludoplus.ui.common.ScreenScaffold
import net.zodac.ludoplus.ui.common.SoraFontFamily
import net.zodac.ludoplus.ui.common.stringResource
import net.zodac.ludoplus.ui.common.VerticalScrollbar
import net.zodac.ludoplus.ui.icons.Symbols
import org.jetbrains.compose.resources.StringResource

/** How far in from each end of the tab row its tabs are hidden outright while there are more to
 * scroll to: the chevron's glyph (24dp, centred in its 48dp button, so ending 36dp in) plus a small
 * gap, so no label is ever drawn underneath it. */
private val TAB_EDGE_CLEAR = 40.dp

/** Past [TAB_EDGE_CLEAR], how far the tabs take to fade back in to full strength. */
private val TAB_EDGE_FADE = 24.dp

/** How much scrolling is left before an end's chevron and fade start easing away - so they fade out
 * as the last tab comes into view rather than switching off at the very end. */
private val TAB_EDGE_EASE = 48.dp

/** How much of the tab row's visible width a chevron tap scrolls by - less than all of it, so the
 * tab that was cut off at the edge is still in view afterwards, now whole. */
private const val TAB_CHEVRON_SCROLL_FRACTION = 0.6f

/** The page the rules were last on, kept while the app process lives (not across a restart). */
private object RulesPageMemory {
    var lastPage = 0
}

/** Forgets the remembered page, so the rules open on the first again - tests start from there. */
fun resetRulesPageMemory() {
    RulesPageMemory.lastPage = 0
}

/**
 * The Rules page: two rows of tabs over a [HorizontalPager] of every [RulesPage]. The top row picks
 * one of [RULES_GROUPS] (only the gameplay for now: the game modes and the modifiers join it in Phase 9, and until there
 * are two groups the row isn't shown), and the row under it has a tab per page of that group, so any group is a tap away from any page (Material's primary
 * and secondary tabs). The pager runs through every page, group after group: a swipe past a group's
 * last page carries on into the next group, and the top row follows. A tab snaps straight to its page
 * rather than animating there: an animated jump slides through, and so builds, every page between
 * (up to a whole group's), which a phone feels as lag - and keeps the rows scrolling the while, so a
 * second tap meanwhile only stops the scroll instead of reaching its tab. The page row scrolls, as a
 * group can have more pages than fit - a row of dots stopped saying where you were, or letting you
 * get to the last page, once there were more than a handful.
 *
 * A scrolling tab row gives no sign on its own that there's more of it: on a phone the first three
 * tabs can end right at the edge, so the row looks like all there is. A fade alone didn't fix that -
 * it only shows when a label happens to be under it, and with the selected tab centred the next one
 * can start just past the edge, leaving the fade over empty space. So each end with tabs beyond it
 * gets a [TabScrollChevron], which is there whatever the labels' widths, over a [fadeOffscreenEdges]
 * fade that keeps a cut-off label from running into it. Both rows and their tabs carry collection
 * semantics so TalkBack says "Tab, 1 of 3" and "Tab, 1 of 6", the spoken form of the same hint.
 */
@Composable
fun RulesScreen(onBack: () -> Unit, modifier: Modifier = Modifier, onStartTutorial: () -> Unit = {}) {
    val pagerState = rememberPagerState(initialPage = RulesPageMemory.lastPage.coerceIn(0, RULES_PAGES.size - 1), pageCount = { RULES_PAGES.size })
    // Remembered for the app's run, so the rules reopen where they were left.
    LaunchedEffect(pagerState) { snapshotFlow { pagerState.currentPage }.collect { RulesPageMemory.lastPage = it } }
    val coroutineScope = rememberCoroutineScope()
    // Only the group is read here, not the page: a page change then rebuilds only what shows the page (the two tabs
    // whose selection changes, the page row's indicator, the scrollbar and the footer), not the whole screen.
    val currentGroup by remember { derivedStateOf { groupOf(pagerState.currentPage) } }

    ScreenScaffold(title = stringResource(Res.string.rules_title), onBack = onBack, modifier = modifier, wide = true) {
        val groupTabs = @Composable {
            GroupTabRow(
                currentGroup = currentGroup,
                // To the group's first page - its overview, for the modes and the modifiers. A tap on the group already
                // showing leaves the page where it is.
                onSelect = { group -> if (group != currentGroup) coroutineScope.launch { pagerState.scrollToPage(GROUP_STARTS[group]) } },
            )
        }
        val pageTabs = @Composable {
            PageTabRow(pagerState = pagerState, group = currentGroup, onSelect = { page -> coroutineScope.launch { pagerState.scrollToPage(page) } })
        }
        val landscape = LocalLandscapePage.current
        if (RULES_GROUPS.size == 1) {
            pageTabs()
        } else if (landscape) {
            // One row in landscape, the groups then their pages, leaving the height to the page.
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.Bottom) {
                Box(modifier = Modifier.weight(LANDSCAPE_GROUP_TABS_WEIGHT)) { groupTabs() }
                Box(modifier = Modifier.weight(1f)) { pageTabs() }
            }
        } else {
            groupTabs()
            pageTabs()
        }

        // The footer floats over the pages rather than taking a row of its own: pinned to the bottom,
        // with the page text scrolling behind it, and each page padded by the footer's measured height
        // (so a large font still clears it) plus a gap, so the last line can always scroll above it.
        var footerHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        val footerRoom = with(density) { footerHeightPx.toDp() } + PAGE_FOOTER_GAP
        // At a large font the pill covers more of the words behind it, so the pages stop above it instead.
        val pagesAboveFooter = density.isLargeFont
        val pageBottomPadding = if (pagesAboveFooter) 0.dp else footerRoom
        // Held here rather than inside each page, so the scrollbar beside the pager can follow whichever
        // page is showing.
        val pageScrollStates = remember { List(RULES_PAGES.size) { ScrollState(initial = 0) } }
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // A gap between pages wider than the screen's side margin, so a page never shows a sliver
            // of its neighbour's text at its edge.
            HorizontalPager(
                state = pagerState,
                pageSpacing = PAGE_SPACING,
                modifier = Modifier.fillMaxSize().then(if (pagesAboveFooter) Modifier.padding(bottom = footerRoom) else Modifier),
            ) { page ->
                val rulesPage = RULES_PAGES[page]
                val shownBlocks = rememberShownBlocks(rulesPage.blocks.size)
                // A readable line however wide the page: the text keeps to READABLE_PAGE_WIDTH, centred, in landscape.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(pageScrollStates[page])
                        .wrapContentWidth()
                        .widthIn(max = if (landscape) READABLE_PAGE_WIDTH else Dp.Infinity)
                        .padding(bottom = pageBottomPadding),
                ) {
                    Text(
                        text = stringResource(rulesPage.title, stringResource(Res.string.app_name)),
                        style = MaterialTheme.typography.headlineSmall,
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.semantics { heading() },
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    for (block in rulesPage.blocks.take(shownBlocks)) {
                        RulesBlockView(block, onStartTutorial)
                    }
                }
            }

            PageScrollbar(pagerState, pageScrollStates)

            PageCountFooter(
                pagerState = pagerState,
                modifier = Modifier.align(Alignment.BottomCenter).onSizeChanged { footerHeightPx = it.height },
            )
        }
    }
}

/** In landscape, the group tabs' share of the row they share with the page tabs, against the page tabs' 1. */
private const val LANDSCAPE_GROUP_TABS_WEIGHT = 0.6f

/** Widest a page's text is laid out in landscape: a comfortable line, rather than the whole width. */
private val READABLE_PAGE_WIDTH = 640.dp

/** The top row: a tab per one of [RULES_GROUPS], [currentGroup] selected; [onSelect] is handed a tapped tab's group. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupTabRow(currentGroup: Int, onSelect: (Int) -> Unit) {
    val groupLabels = RULES_GROUPS.map { stringResource(it.label) }
    val scrollState = rememberScrollState()
    val placements = remember { TabPlacements() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    // A real Box, so the chevrons' align lands on their actual parent (see UI.md's gotchas).
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val tabWidths = groupTabWidths(groupLabels, constraints.maxWidth)
        KeepTabInView(scrollState, placements) { currentGroup.toFloat() }
        PrimaryScrollableTabRow(
            // Never changes, so the row never re-centres itself - see KeepTabInView. The indicator and each tab's
            // selected state follow currentGroup themselves.
            selectedTabIndex = 0,
            scrollState = scrollState,
            // Transparent over the backdrop, like the app bar above it, and flush with the page text.
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(currentGroup, matchContentSize = true),
                    width = Dp.Unspecified,
                    height = TAB_INDICATOR_HEIGHT,
                )
            },
            divider = { TabRowDivider() },
            // Each tab is sized by groupTabWidths instead.
            minTabWidth = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .fadeOffscreenEdges(scrollState, clearWidth = TAB_EDGE_CLEAR, fadeWidth = TAB_EDGE_FADE, easeDistance = TAB_EDGE_EASE)
                .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = RULES_GROUPS.size) },
        ) {
            groupLabels.forEachIndexed { index, label ->
                val width = tabWidths[index]
                Tab(
                    selected = index == currentGroup,
                    onClick = { onSelect(index) },
                    text = { Text(text = label, maxLines = 1, softWrap = false) },
                    modifier = remember(width, placements, index, rtl) { Modifier.width(width).rowTab(placements, index, rtl) },
                )
            }
        }
        TabScrollChevron(scrollState = scrollState, forward = false, modifier = Modifier.align(Alignment.CenterStart))
        TabScrollChevron(scrollState = scrollState, forward = true, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/**
 * The row under [GroupTabRow]: a tab per page of [group], the current one selected; [onSelect] is handed a tapped tab's
 * page (its index in [RULES_PAGES]). Reads the current page only where it's needed - each tab's selection (through
 * [derivedStateOf], so a page change recomposes only the two tabs it changes), the indicator's layout, and
 * [KeepTabInView] - so the row itself isn't rebuilt on every page change.
 */
@Composable
private fun PageTabRow(pagerState: PagerState, group: Int, onSelect: (Int) -> Unit) {
    val groupStart = GROUP_STARTS[group]
    val groupPages = RULES_GROUPS[group].pages
    // A group's page row starts scrolled to its start, not wherever the last group's was left.
    val scrollState = remember(group) { ScrollState(initial = 0) }
    val placements = remember(group) { TabPlacements() }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduceMotion = LocalReduceMotion.current
    val coroutineScope = rememberCoroutineScope()
    // A tap snaps the pager straight to its page (animating the pages would cost a frame of two whole pages), but the
    // indicator slides there through the tabs in between, as a swipe would carry it: this holds where it is while
    // it does, and the pager's own position is shown the rest of the time.
    val tapSlide = remember(group) { Animatable(0f) }
    var tapSliding by remember(group) { mutableStateOf(false) }
    // Where the indicator is, in tabs from the group's first: read in layout and by KeepTabInView, never in composition.
    val indicatorPosition = { if (tapSliding) tapSlide.value else pagerState.currentPage + pagerState.currentPageOffsetFraction - groupStart }
    // Runs in the tap itself, with the indicator pinned where it was *before* the pager moves: starting it from an effect
    // instead let the pager's jump reach the indicator first, which showed it at the new tab for a frame before sliding.
    val slideTo: (Int) -> Unit = { page ->
        val from = indicatorPosition()
        coroutineScope.launch {
            try {
                tapSlide.snapTo(from)
                tapSliding = true
                onSelect(page)
                if (!reduceMotion) {
                    val to = (page - groupStart).toFloat()
                    val millis = (TAP_SLIDE_BASE_MILLIS + TAP_SLIDE_PER_TAB_MILLIS * abs(to - from)).coerceAtMost(TAP_SLIDE_MAX_MILLIS)
                    tapSlide.animateTo(to, tween(millis.toInt(), easing = FastOutSlowInEasing))
                }
            } finally {
                // Back to following the pager - which is already on the page, so nothing moves.
                tapSliding = false
            }
        }
    }
    // A real Box, so the chevrons' align lands on their actual parent (see UI.md's gotchas).
    Box(modifier = Modifier.fillMaxWidth()) {
        // Follows the indicator, so the row scrolls along with it as it does through a swipe, rather than ahead of it.
        KeepTabInView(scrollState, placements) { indicatorPosition() }
        SecondaryScrollableTabRow(
            // Never changes, so the row never re-centres itself - see KeepTabInView. The indicator follows the pager,
            // and each tab's selected state the current page.
            selectedTabIndex = 0,
            scrollState = scrollState,
            // Transparent over the backdrop, like the app bar above it, and flush with the page text.
            containerColor = Color.Transparent,
            edgePadding = 0.dp,
            // Slides with the pages as they're swiped, rather than jumping once the next page is
            // the current one - see pagerIndicatorLayout.
            indicator = {
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorLayout { measurable, constraints, tabPositions ->
                        pagerIndicatorLayout(measurable, constraints, tabPositions, indicatorPosition())
                    }.testTag(TAB_INDICATOR_TAG),
                    height = SECONDARY_INDICATOR_HEIGHT,
                )
            },
            divider = { HorizontalDivider() },
            modifier = Modifier
                .fillMaxWidth()
                .fadeOffscreenEdges(scrollState, clearWidth = TAB_EDGE_CLEAR, fadeWidth = TAB_EDGE_FADE, easeDistance = TAB_EDGE_EASE)
                .semantics { collectionInfo = CollectionInfo(rowCount = 1, columnCount = groupPages.size) },
        ) {
            groupPages.forEachIndexed { index, rulesPage ->
                val page = groupStart + index
                val selected by remember(page) { derivedStateOf { pagerState.currentPage == page } }
                Tab(
                    selected = selected,
                    onClick = { slideTo(page) },
                    text = { Text(text = stringResource(rulesPage.tabLabel), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = remember(placements, index, rtl) { Modifier.rowTab(placements, index, rtl) },
                )
            }
        }
        TabScrollChevron(scrollState = scrollState, forward = false, modifier = Modifier.align(Alignment.CenterStart))
        TabScrollChevron(scrollState = scrollState, forward = true, modifier = Modifier.align(Alignment.CenterEnd))
    }
}

/** How long the indicator takes to slide to a tapped tab: this much, plus [TAP_SLIDE_PER_TAB_MILLIS] for each tab it passes, up to [TAP_SLIDE_MAX_MILLIS]. */
private const val TAP_SLIDE_BASE_MILLIS = 160f
private const val TAP_SLIDE_PER_TAB_MILLIS = 50f
private const val TAP_SLIDE_MAX_MILLIS = 400f

/** A tab's place in its row: recorded for [KeepTabInView], and announced as its position in the row ("Tab, 2 of 6"). */
private fun Modifier.rowTab(placements: TabPlacements, index: Int, rtl: Boolean): Modifier = this
    .recordPlacement(placements, index, rtl)
    .semantics { collectionItemInfo = CollectionItemInfo(rowIndex = 0, rowSpan = 1, columnIndex = index, columnSpan = 1) }

/** The scrollbar for whichever page is showing: in the screen's right-hand margin, beside the text rather than over it
 * (as on Styles), and only while that page is too long to fit. */
@Composable
private fun BoxScope.PageScrollbar(pagerState: PagerState, pageScrollStates: List<ScrollState>) {
    VerticalScrollbar(
        scrollState = pageScrollStates[pagerState.currentPage],
        width = SCREEN_MARGIN,
        modifier = Modifier.align(Alignment.TopEnd).offset(x = SCREEN_MARGIN),
    )
}

/** Body text of every block, styled by its [parseInlineMarkup] markers: *italic* for the name of a setting, **bold** for a
 * number. TalkBack hears the plain words. */
@Composable
private fun RulesBodyText(resource: StringResource, modifier: Modifier = Modifier) {
    val text = stringResource(resource, stringResource(Res.string.app_name))
    val styled = remember(text) { parseInlineMarkup(text) }
    Text(
        text = styled,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Space under every block, so paragraphs and diagrams are spaced alike. */
private val BLOCK_GAP = 10.dp

/** Draws one [RulesBlock] of a page. */
@Composable
private fun RulesBlockView(block: RulesBlock, onStartTutorial: () -> Unit) {
    when (block) {
        is RulesText -> RulesBodyText(block.text, modifier = Modifier.padding(bottom = BLOCK_GAP))
        RulesTutorialStart -> FilledTonalButton(
            onClick = onStartTutorial,
            // The gap outside the button's height: inside it, the gap would come out of the button.
            modifier = Modifier.padding(bottom = BLOCK_GAP).fillMaxWidth().heightIn(min = MAIN_ACTION_HEIGHT),
        ) {
            Text(text = stringResource(Res.string.rules_tutorial_start), style = MaterialTheme.typography.titleMedium)
        }
        is RulesHouseRule -> {
            val line = stringResource(Res.string.rules_house_rule_line, "*${stringResource(block.rule.title)}*", stringResource(block.rule.description))
            Text(
                text = remember(line) { parseInlineMarkup(line) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = BLOCK_GAP),
            )
        }
        is RulesDiagramBlock -> Box(modifier = Modifier.fillMaxWidth().padding(top = 6.dp, bottom = BLOCK_GAP), contentAlignment = Alignment.Center) {
            RulesDiagram(block.kind)
        }
    }
}

/** How many of a page's blocks are built in the frame it opens on - about a screenful. */
private const val FIRST_BLOCKS = 4

/** How many more of a page's blocks are built in each frame after that, until it's whole. */
private const val BLOCKS_PER_FRAME = 2

/**
 * How many of a page's [count] blocks to build so far: [FIRST_BLOCKS] at once, then [BLOCKS_PER_FRAME] more each
 * frame. Building a long page all in the frame it opens on held that frame up long enough to feel on a phone, when only
 * its first screenful can be seen; the rest is below the fold, and is built over the next few frames instead, before
 * anyone can scroll to it.
 */
@Composable
private fun rememberShownBlocks(count: Int): Int {
    var shown by remember { mutableIntStateOf(minOf(count, FIRST_BLOCKS)) }
    LaunchedEffect(count) {
        while (shown < count) {
            withFrameNanos { }
            shown = minOf(count, shown + BLOCKS_PER_FRAME)
        }
    }
    return shown
}

/** ScreenScaffold's side margin, which the pages' scrollbar sits in. */
private val SCREEN_MARGIN = 20.dp

/** The gap between two pages of the pager - twice [SCREEN_MARGIN], so neither page's text shows in
 * the other's margin when they're not quite lined up. */
private val PAGE_SPACING = SCREEN_MARGIN * 2

/**
 * Places the tab row's indicator under [pagePosition] - the pager's current page plus how far it's
 * been swiped towards the next (`currentPage + currentPageOffsetFraction`) - so it slides between
 * two tabs with the finger, its width easing from one label's to the other's. Read during layout,
 * so following a swipe re-places the indicator without recomposing the tab row.
 */
fun MeasureScope.pagerIndicatorLayout(
    measurable: Measurable,
    constraints: Constraints,
    tabPositions: List<TabPosition>,
    pagePosition: Float,
): MeasureResult {
    val position = pagePosition.coerceIn(0f, (tabPositions.size - 1).toFloat())
    val from = tabPositions[position.toInt()]
    val to = tabPositions[(position.toInt() + 1).coerceAtMost(tabPositions.size - 1)]
    val fraction = position - position.toInt()
    // Under the label, as the stock indicator sits (matchContentSize): centred on its tab, as wide as its label.
    // Each position's `left` is its tab's start (not its label's), and `width` its whole tab - a tab can be wider
    // than its label (a minimum width, or the tab's padding), so the tab's centre is `left + width / 2`.
    val width = lerp(from.contentWidth, to.contentWidth, fraction)
    val centre = lerp(from.left + from.width / 2, to.left + to.width / 2, fraction)
    val widthPx = width.roundToPx()
    val placeable = measurable.measure(constraints.copy(minWidth = widthPx, maxWidth = widthPx))
    // Material then places the indicator itself, centred under the row's *selected* tab: in by half of that tab's
    // width less the indicator's own (SecondaryScrollableTabRow, for the tab `selectedTabIndex` names - always 0 here,
    // see PageTabRow; not clamped at 0 in material3 1.4.0, as it later is - RulesTabIndicatorTest catches an upgrade changing
    // it). Whatever that comes to is already added, so it's taken off - leaving it in puts the indicator out by half the
    // difference between tab 0's width and the shown tab's, which only shows once the tabs differ in width.
    val materialInset = (tabPositions[0].width.roundToPx() - placeable.width) / 2
    return layout(placeable.width, placeable.height) {
        // Relative, so a right-to-left row (whose tab positions count from the right) puts it under its tab, not mirrored away.
        placeable.placeRelative(x = (centre - width / 2).roundToPx() - materialInset, y = 0)
    }
}

/** Test tag of the page row's indicator, so a test can check it sits under the selected tab. */
internal const val TAB_INDICATOR_TAG = "rulesTabIndicator"

/** How far a [Tab]'s label is padded in from each side of it - Material's own padding, which the group row has to count in when it sizes its tabs. */
private val TAB_LABEL_PADDING = 16.dp

/**
 * How wide each group tab is, across [rowWidthPx]: all alike, as Material's fixed tabs are, while every label fits a
 * third - otherwise each as wide as its label needs, plus an equal share of what's left, so a long label
 * is never cut off and the row still spans the width. Only when the labels don't fit the row at all (a large font) does
 * it scroll, with the same chevrons as the page row.
 *
 * Worked in whole pixels, adding up to exactly the row: widths even a pixel over make it scrollable, and a
 * tap on a scrollable row while it scrolls is taken as "stop", not passed to the tab - see [KeepTabInView].
 */
@Composable
private fun groupTabWidths(labels: List<String>, rowWidthPx: Int): List<Dp> {
    val measurer = rememberTextMeasurer()
    val style = MaterialTheme.typography.titleSmall
    val density = LocalDensity.current
    return remember(labels, style, rowWidthPx, density) {
        val padding = with(density) { (TAB_LABEL_PADDING * 2).roundToPx() }
        // A pixel over, so a label measured at exactly its width isn't cut off by rounding.
        val natural = labels.map { label -> measurer.measure(label, style, maxLines = 1, softWrap = false).size.width + 1 + padding }
        val equal = rowWidthPx / labels.size
        val base = if (natural.all { it <= equal }) List(labels.size) { 0 } else natural
        val left = (rowWidthPx - base.sum()).coerceAtLeast(0)
        // What's left shared out, and the pixels that don't divide evenly given one each to the first tabs, so the
        // row spans its width exactly.
        val widths = base.mapIndexed { index, width -> width + left / labels.size + if (index < left % labels.size) 1 else 0 }
        with(density) { widths.map { it.toDp() } }
    }
}

/** Where each tab of a scrolling row was last placed along it, by its index: its start and end in pixels, from the
 * row's start. Written as the tabs are placed, read by [KeepTabInView]; not state, as nothing redraws from it. */
private class TabPlacements {
    val starts = mutableMapOf<Int, Int>()
    val ends = mutableMapOf<Int, Int>()
}

/** Records where this tab is placed in its row into [placements], counted from the row's start - its right edge when
 * [rtl], as the row's scroll is counted. */
private fun Modifier.recordPlacement(placements: TabPlacements, index: Int, rtl: Boolean): Modifier = onPlaced { coordinates ->
    val parentWidth = coordinates.parentLayoutCoordinates?.size?.width ?: return@onPlaced
    val left = coordinates.positionInParent().x.roundToInt()
    val right = left + coordinates.size.width
    placements.starts[index] = if (rtl) parentWidth - right else left
    placements.ends[index] = if (rtl) parentWidth - left else right
}

/**
 * Scrolls a tab row just enough to bring its [selected] tab clear of the edge fades, and only if it isn't already -
 * after a swipe to a page whose tab is off screen, say. Material's scrollable rows instead re-centre the selected tab,
 * with a scroll animation, every time it changes; and while a scrollable is scrolling, Compose takes a tap on it as
 * "stop scrolling" and never passes it to the tab. So a second tap soon after the first (within ~300ms in tests, longer
 * on a slow phone) was lost, which on a device felt like tabs that only worked some of the time. Both rows are given a
 * `selectedTabIndex` that never changes, so they never re-centre, and this keeps the selected tab in view instead: a
 * tap on a tab that's already in view doesn't scroll anything, and one partly under an edge fade moves the row at once
 * (as the page itself snaps), so there's never a scroll under the next tap.
 */
@Composable
private fun KeepTabInView(scrollState: ScrollState, placements: TabPlacements, selected: () -> Float) {
    val edge = with(LocalDensity.current) { (TAB_EDGE_CLEAR + TAB_EDGE_FADE).roundToPx() }
    LaunchedEffect(scrollState, placements) {
        snapshotFlow(selected).collectLatest { position ->
            // Between two tabs while the indicator is on its way: the tab it is passing, as far along as it is, so a row that
            // has to scroll goes smoothly with the indicator instead of jumping a tab's width as each tab is passed.
            val first = floor(position).toInt()
            val fraction = position - first
            var bounds = placements.boundsAt(first, fraction)
            if (bounds == null) {
                // Placed by the next frame, if the row (or this tab) is new.
                withFrameNanos { }
                bounds = placements.boundsAt(first, fraction) ?: return@collectLatest
            }
            val (start, end) = bounds
            val viewport = scrollState.viewportSize
            // Each end's fade (and chevron) only covers the row while there's more beyond it.
            val visibleStart = scrollState.value + if (scrollState.value > 0) edge else 0
            val visibleEnd = scrollState.value + viewport - if (scrollState.value < scrollState.maxValue) edge else 0
            val target = when {
                start < visibleStart -> start - edge
                end > visibleEnd -> end - viewport + edge
                else -> return@collectLatest
            }
            // At once, not animated: a scroll in progress would swallow the next tap, as below.
            scrollState.scrollTo(target.coerceIn(0, scrollState.maxValue))
        }
    }
}

/** The start and end of the place [fraction] of the way from tab [index] to the next, or null if either isn't placed yet. */
private fun TabPlacements.boundsAt(index: Int, fraction: Float): Pair<Int, Int>? {
    val start = starts[index] ?: return null
    val end = ends[index] ?: return null
    if (fraction <= 0f) return start to end
    val nextStart = starts[index + 1] ?: return start to end
    val nextEnd = ends[index + 1] ?: return start to end
    return (start + (nextStart - start) * fraction).roundToInt() to (end + (nextEnd - end) * fraction).roundToInt()
}

/** The group row's indicator, as thick as the stock one - named so the divider can centre itself on it. */
private val TAB_INDICATOR_HEIGHT = 3.dp

/** The page row's indicator: thinner and flat, as a secondary row's is, so it reads as under the group row's. */
private val SECONDARY_INDICATOR_HEIGHT = 2.dp

/**
 * The group row's divider, raised off the row's bottom edge so it runs through the middle of the
 * indicator, which sits on that edge - left at the bottom, the indicator rests on top of the line
 * instead. (The indicator can't be lowered onto the line: the row clips anything below its bottom.)
 */
@Composable
private fun TabRowDivider() {
    HorizontalDivider(modifier = Modifier.padding(bottom = (TAB_INDICATOR_HEIGHT - DividerDefaults.Thickness) / 2))
}

/** Space between the end of a page's text and the top of the footer, once scrolled to the bottom. */
private val PAGE_FOOTER_GAP = 8.dp

/**
 * "2 of 5" pinned to the bottom of the pages - where you are in the group showing and how many pages
 * it has, in one glance, alongside the page row's chevrons (which say only that there's more). A small
 * gold pill drawn over the pages, not a row of its own, so it costs the pages no height: longer text
 * scrolls behind it, the pill's own background keeping it readable on top. Drawn by the shared [FooterPill].
 *
 * TalkBack hears "Gameplay, page 2 of 5", and as a polite live region it's announced again whenever the
 * page changes - a swipe through the pager otherwise lands on a new page without a word, and one past
 * a group's last page lands in another group without saying so.
 */
@Composable
private fun PageCountFooter(pagerState: PagerState, modifier: Modifier = Modifier) {
    val group = groupOf(pagerState.currentPage)
    val page = pagerState.currentPage - GROUP_STARTS[group]
    val pageCount = RULES_GROUPS[group].pages.size
    val pageSpoken = stringResource(Res.string.rules_page_in_group_spoken, stringResource(RULES_GROUPS[group].label), page + 1, pageCount)
    FooterPill(
        text = stringResource(Res.string.rules_page_count, page + 1, pageCount),
        modifier = modifier.semantics {
            contentDescription = pageSpoken
            liveRegion = LiveRegionMode.Polite
        },
    )
}

/**
 * Hides a horizontally scrolling row's content at whichever end has more beyond it: fully across
 * [clearWidth], where that end's [TabScrollChevron] sits, so its glyph is never drawn over a label,
 * then fading back in over [fadeWidth]. Both deepen with how far there is left to scroll (up to
 * [easeDistance]), so they ease away as the last tab comes fully into view instead of vanishing at
 * the end. Drawn only: nothing is hidden from touch or from TalkBack.
 */
private fun Modifier.fadeOffscreenEdges(scrollState: ScrollState, clearWidth: Dp, fadeWidth: Dp, easeDistance: Dp): Modifier = this
    // Offscreen so the DstIn masks below cut into the row's own pixels, not the backdrop behind it.
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val width = (clearWidth + fadeWidth).toPx().coerceAtMost(size.width / 2)
        if (width <= 0f) return@drawWithContent
        // Where along the mask the clear zone gives way to the fade, as a fraction of it.
        val clearFraction = (clearWidth.toPx() / width).coerceIn(0f, 1f)
        val ease = easeDistance.toPx()
        // The scroll state counts from the row's start, which is its right edge in a right-to-left layout; the masks are
        // drawn in screen coordinates, so the strengths swap sides there.
        val rtl = layoutDirection == LayoutDirection.Rtl
        val leftStrength = scrollState.edgeStrength(forward = rtl, over = ease)
        val rightStrength = scrollState.edgeStrength(forward = !rtl, over = ease)
        if (leftStrength > 0f) {
            val edge = Color.Black.copy(alpha = 1f - leftStrength)
            drawRect(
                brush = Brush.horizontalGradient(0f to edge, clearFraction to edge, 1f to Color.Black, startX = 0f, endX = width),
                size = Size(width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
        if (rightStrength > 0f) {
            val edge = Color.Black.copy(alpha = 1f - rightStrength)
            drawRect(
                brush = Brush.horizontalGradient(
                    0f to Color.Black,
                    (1f - clearFraction) to edge,
                    1f to edge,
                    startX = size.width - width,
                    endX = size.width,
                ),
                topLeft = Offset(size.width - width, 0f),
                size = Size(width, size.height),
                blendMode = BlendMode.DstIn,
            )
        }
    }

/**
 * How much more there is to scroll towards one end, from 0 (none) to 1 ([over] or more) - so an edge
 * hint deepens with the distance left rather than switching on and off, and eases away as the last
 * tab comes fully into view instead of vanishing at the end.
 */
private fun ScrollState.edgeStrength(forward: Boolean, over: Float): Float {
    val remaining = if (forward) maxValue - value else value
    return (remaining / over).coerceIn(0f, 1f)
}

/**
 * A chevron at one end of the tab row while there are more tabs that way - the hint that there's
 * more, which the fade can't give by itself when no label happens to be under it. A tap scrolls the
 * row that way rather than changing page: it points at more tabs, not at the next page.
 *
 * Kept out of TalkBack (`clearAndSetSemantics`): the tabs it scrolls to are TalkBack stops already,
 * each announced with its position and the count ("Tab, 5 of 7"), so it would only add a stop that
 * says nothing new.
 */
@Composable
private fun TabScrollChevron(scrollState: ScrollState, forward: Boolean, modifier: Modifier = Modifier) {
    val coroutineScope = rememberCoroutineScope()
    val reduceMotion = LocalReduceMotion.current
    val shown by remember(scrollState, forward) {
        derivedStateOf { if (forward) scrollState.canScrollForward else scrollState.canScrollBackward }
    }
    // Not composed at all when there's nothing that way, so it never sits over a tab taking its taps.
    if (!shown) return

    IconButton(
        onClick = {
            val distance = scrollState.viewportSize * TAB_CHEVRON_SCROLL_FRACTION
            val by = if (forward) distance else -distance
            coroutineScope.launch { if (reduceMotion) scrollState.scrollBy(by) else scrollState.animateScrollBy(by) }
        },
        modifier = modifier
            .graphicsLayer { alpha = scrollState.edgeStrength(forward, over = TAB_EDGE_EASE.toPx()) }
            .clearAndSetSemantics {},
    ) {
        Icon(
            imageVector = if (forward) Symbols.KeyboardArrowRight else Symbols.KeyboardArrowLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

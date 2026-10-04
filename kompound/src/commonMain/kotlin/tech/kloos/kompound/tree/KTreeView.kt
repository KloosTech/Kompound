package tech.kloos.kompound.tree

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.focusable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/**
 * Which nodes of a [KTreeView] are open and which one is selected. Keys are strings (ids), so the state can be saved; create it with
 * [rememberKTreeState] to survive configuration changes.
 */
@Stable
public class KTreeState(expanded: Set<String> = emptySet(), selected: String? = null) {
    /** Keys of the open nodes. */
    public var expanded: Set<String> by mutableStateOf(expanded)

    /** Key of the selected node, or `null`. */
    public var selected: String? by mutableStateOf(selected)

    /** Opens or closes the node [key]. */
    public fun toggle(key: String) {
        expanded = if (key in expanded) expanded - key else expanded + key
    }

    /** Opens the node [key] and all of [ancestors] so it is visible. */
    public fun reveal(key: String, ancestors: List<String>) {
        expanded = expanded + ancestors
        selected = key
    }

    public companion object {
        /** Saves and restores the open nodes and the selection. */
        public val Saver: Saver<KTreeState, Any> = Saver(
            save = { listOf(it.expanded.toList(), it.selected ?: "") },
            restore = { v -> @Suppress("UNCHECKED_CAST") (v as List<Any>).let { KTreeState((it[0] as List<String>).toSet(), (it[1] as String).ifEmpty { null }) } },
        )
    }
}

/** A [KTreeState] kept across configuration changes. */
@Composable
public fun rememberKTreeState(expanded: Set<String> = emptySet(), selected: String? = null): KTreeState =
    rememberSaveable(saver = KTreeState.Saver) { KTreeState(expanded, selected) }

/**
 * A tree of expandable nodes (a file browser, an outline, a settings hierarchy) that stays fast with thousands of nodes: only the visible
 * rows are composed.
 *
 * Keyboard (as in the WAI-ARIA tree pattern): Up and Down move between visible nodes, Right opens a closed node or moves to its first child,
 * Left closes an open node or moves to its parent, Home and End jump to the first and last, Enter or Space selects. Screen readers
 * get each node's level, whether it is expanded or collapsed (with expand and collapse actions) and whether it is selected.
 *
 * @param roots The top-level nodes.
 * @param children The child nodes of a node (empty for a leaf); asked only for nodes that are open or whose arrow is shown.
 * @param key A stable string identity of a node.
 * @param modifier Modifier applied to the list. Give it a bounded height.
 * @param state Open nodes and the selection.
 * @param onSelect Called when the user selects a node (the state is updated too).
 * @param indent Indentation per level.
 * @param content The label of a node: text, an icon and text, a checkbox ... Receives the node and its depth.
 */
@Composable
public fun <T> KTreeView(
    roots: List<T>,
    children: (T) -> List<T>,
    key: (T) -> String,
    modifier: Modifier = Modifier,
    state: KTreeState = rememberKTreeState(),
    onSelect: ((T) -> Unit)? = null,
    indent: androidx.compose.ui.unit.Dp = 20.dp,
    content: @Composable (node: T, depth: Int) -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val strings = KompoundTheme.strings
    val expanded = state.expanded
    // The visible rows, flattened (depth first): the list is lazy, so thousands of closed nodes cost nothing.
    val rows = remember(roots, expanded) {
        val out = ArrayList<TreeRow<T>>()
        fun walk(nodes: List<T>, depth: Int, parent: Int) {
            for (node in nodes) {
                val kids = children(node)
                val index = out.size
                out += TreeRow(node, key(node), depth, kids.isNotEmpty(), parent)
                if (kids.isNotEmpty() && key(node) in expanded) walk(kids, depth + 1, index)
            }
        }
        walk(roots, 0, -1)
        out
    }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val density = KompoundTheme.tokens.density
    val requesters = remember { HashMap<String, FocusRequester>() }
    var pending by remember { mutableStateOf<String?>(null) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // Focus the row at [index]; a row that is not composed yet is scrolled into view first and gets the focus when it appears.
    fun moveFocusTo(index: Int) {
        val target = rows.getOrNull(index) ?: return
        pending = target.key
        val visible = listState.layoutInfo.visibleItemsInfo.any { it.index == index }
        if (!visible) scope.launch { listState.scrollToItem(index) } else requesters[target.key]?.requestFocus()
    }
    LazyColumn(modifier.semantics { collectionInfo = CollectionInfo(rows.size, 1) }, state = listState) {
        itemsIndexed(rows, key = { _, r -> r.key }) { index, row ->
            val requester = requesters.getOrPut(row.key) { FocusRequester() }
            TreeRowItem(
                row, index, rows, state, indent, density.height(40.dp), requester, requesters,
                onSelect = { state.selected = row.key; onSelect?.invoke(row.node) },
                onToggle = { state.toggle(row.key) },
                expandLabel = strings.expand, collapseLabel = strings.collapse, expandedText = strings.expanded, collapsedText = strings.collapsed,
                pendingFocus = pending, focusRow = ::moveFocusTo,
                content = content,
            )
        }
    }
}

private class TreeRow<T>(val node: T, val key: String, val depth: Int, val hasChildren: Boolean, val parentIndex: Int)

@Composable
private fun <T> TreeRowItem(
    row: TreeRow<T>,
    index: Int,
    rows: List<TreeRow<T>>,
    state: KTreeState,
    indent: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    requester: FocusRequester,
    requesters: Map<String, FocusRequester>,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    expandLabel: String,
    collapseLabel: String,
    expandedText: String,
    collapsedText: String,
    pendingFocus: String?,
    focusRow: (Int) -> Unit,
    content: @Composable (T, Int) -> Unit,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val focused by source.collectIsFocusedAsState()
    val isOpen = row.key in state.expanded
    val isSelected = state.selected == row.key
    val scheme = MaterialTheme.colorScheme
    val l = KompoundTheme.tokens.stateLayer
    val background = when {
        isSelected -> scheme.secondaryContainer
        focused -> scheme.onSurface.copy(alpha = l.focused)
        hovered -> scheme.onSurface.copy(alpha = l.hovered)
        else -> Color.Transparent
    }
    val tint = if (isSelected) scheme.onSecondaryContainer else scheme.onSurface
    val focus = focusRow
    androidx.compose.runtime.LaunchedEffect(pendingFocus) { if (pendingFocus == row.key) requester.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(background)
            .hoverable(source)
            .focusRequester(requester)
            .onKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (e.key) {
                    Key.DirectionDown -> { focus(index + 1); true }
                    Key.DirectionUp -> { focus(index - 1); true }
                    Key.DirectionRight -> { if (row.hasChildren && !isOpen) onToggle() else if (row.hasChildren) focus(index + 1); true }
                    Key.DirectionLeft -> { if (row.hasChildren && isOpen) onToggle() else if (row.parentIndex >= 0) focus(row.parentIndex); true }
                    Key.MoveHome -> { focus(0); true }
                    Key.MoveEnd -> { focus(rows.lastIndex); true }
                    Key.Enter, Key.Spacebar -> { onSelect(); true }
                    else -> false
                }
            }
            .clickable(interactionSource = source, indication = null, role = Role.Button) { onSelect() }
            .semantics(mergeDescendants = true) {
                selected = isSelected
                collectionItemInfo = CollectionItemInfo(index, 1, 0, 1)
                if (row.hasChildren) {
                    stateDescription = if (isOpen) expandedText else collapsedText
                    if (isOpen) collapse { onToggle(); true } else expand { onToggle(); true }
                }
            }
            .padding(start = 8.dp + indent * row.depth, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(24.dp).then(if (row.hasChildren) Modifier.clickable(role = Role.Button, onClickLabel = if (isOpen) collapseLabel else expandLabel) { onToggle() } else Modifier), contentAlignment = Alignment.Center) {
            if (row.hasChildren) Chevron(isOpen, scheme.onSurfaceVariant)
        }
        CompositionLocalProvider(LocalKContentColor provides tint) { content(row.node, row.depth) }
    }
}

@Composable
private fun Chevron(open: Boolean, color: Color) {
    Box(
        Modifier.size(12.dp).rotate(if (open) 90f else 0f).drawBehind {
            drawPath(Path().apply { moveTo(size.width * 0.32f, size.height * 0.15f); lineTo(size.width * 0.7f, size.height / 2f); lineTo(size.width * 0.32f, size.height * 0.85f) }, color, style = Stroke(1.8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        },
    )
}

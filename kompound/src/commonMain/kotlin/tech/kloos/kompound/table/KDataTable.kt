package tech.kloos.kompound.table

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.selection.KCheckbox
import tech.kloos.kompound.skeleton.KSkeleton
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.theme.KompoundTheme
import tech.kloos.kompound.theme.LocalKContentColor

/** How wide a column is: a fixed [Fixed] size, or a [Weight] share of what the fixed columns leave. */
public sealed interface KColumnWidth {
    /** Exactly [width]. */
    public class Fixed(public val width: Dp) : KColumnWidth

    /** A share of the remaining width, proportional to [weight]. */
    public class Weight(public val weight: Float = 1f) : KColumnWidth
}

/**
 * One column of a [KDataTable].
 *
 * @property key Stable identity, used for sorting.
 * @property header Text of the header cell (announced as the column name).
 * @property width Fixed or weighted width.
 * @property sortable Whether a click on the header sorts by this column; needs a [comparator] for [KDataTable]'s built-in sorting.
 * @property comparator Orders two rows by this column (ascending). Without one, sort in your own code from `onSortChange`.
 * @property alignment Horizontal alignment of the header and the cells (use [Alignment.End] for numbers).
 * @property cell Content of this column for one row.
 */
@Immutable
public class KColumn<T>(
    public val key: String,
    public val header: String,
    public val width: KColumnWidth = KColumnWidth.Weight(1f),
    public val sortable: Boolean = false,
    public val comparator: Comparator<T>? = null,
    public val alignment: Alignment.Horizontal = Alignment.Start,
    public val cell: @Composable (T) -> Unit,
) {
    public companion object {
        /** A text column: the cell shows [text] of the row, ellipsised; sortable by that text (or [comparator]). */
        public fun <T> text(
            key: String,
            header: String,
            width: KColumnWidth = KColumnWidth.Weight(1f),
            sortable: Boolean = true,
            alignment: Alignment.Horizontal = Alignment.Start,
            comparator: Comparator<T>? = null,
            text: (T) -> String,
        ): KColumn<T> = KColumn(
            key, header, width, sortable, comparator ?: Comparator { a, b -> text(a).compareTo(text(b), ignoreCase = true) }, alignment,
        ) { row -> KText(text(row), maxLines = 1, overflow = TextOverflow.Ellipsis) }
    }
}

/** The column a table is sorted by and the direction. */
@Immutable
public data class KSortState(val columnKey: String, val ascending: Boolean = true) {
    /** The next state when the user clicks the header of [key]: ascending, then descending, then unsorted (`null`). */
    public companion object {
        public fun next(current: KSortState?, key: String): KSortState? = when {
            current == null || current.columnKey != key -> KSortState(key, true)
            current.ascending -> KSortState(key, false)
            else -> null
        }
    }
}

/**
 * A table that scales to thousands of rows: only the rows on screen are composed. Columns have fixed or weighted widths; the table
 * scrolls sideways when the columns need more than the available width ([minTableWidth]).
 *
 * - **Sorting.** Pass [sort] and [onSortChange] (a click on a sortable header cycles ascending, descending, none). With [sortLocally]
 *   (default) the table orders [rows] itself using the column's `comparator`; turn it off when you sort on a server.
 * - **Selection.** Pass [selection] and [onSelectionChange] to get a checkbox column with a select-all header; rows are announced as selected.
 * - **Rows.** [onRowClick] makes rows clickable; either way rows are focusable, the up and down arrow keys move between them, Space toggles
 *   the selection and Enter clicks.
 * - **Loading and empty.** [loading] shows placeholder rows; with no rows [emptyContent] is shown (default: "No data").
 *
 * Screen readers get a grid with its row and column counts, header cells that say how the column is sorted, and rows that read as one item.
 *
 * @param rows The data. Not modified; keep it stable (`remember`) so sorting is not redone needlessly.
 * @param columns The columns, in order.
 * @param rowKey A stable identity of a row (an id), used for lazy list keys and the selection.
 * @param modifier Modifier applied to the table. Give it a bounded height (the rows scroll inside).
 * @param sort The current sort, or `null` for the order of [rows].
 * @param onSortChange Called with the new sort when a sortable header is clicked; `null` makes headers inert.
 * @param sortLocally Order the rows here with the column comparators.
 * @param selection Keys of the selected rows, or `null` for no selection column.
 * @param onSelectionChange Called with the new selection.
 * @param onRowClick Called when a row is clicked or Enter is pressed on it.
 * @param striped Tint every other row.
 * @param loading Show placeholder rows instead of [rows].
 * @param minTableWidth Width below which the table scrolls sideways instead of squeezing columns.
 * @param emptyContent Shown when there are no rows.
 */
@Composable
public fun <T> KDataTable(
    rows: List<T>,
    columns: List<KColumn<T>>,
    rowKey: (T) -> Any,
    modifier: Modifier = Modifier,
    sort: KSortState? = null,
    onSortChange: ((KSortState?) -> Unit)? = null,
    sortLocally: Boolean = true,
    selection: Set<Any>? = null,
    onSelectionChange: ((Set<Any>) -> Unit)? = null,
    onRowClick: ((T) -> Unit)? = null,
    striped: Boolean = false,
    loading: Boolean = false,
    minTableWidth: Dp = 0.dp,
    emptyContent: @Composable () -> Unit = { KText(KompoundTheme.strings.noData, Modifier.padding(24.dp)) },
) {
    remember { KompoundStyles.ensureEnabled() }
    val strings = KompoundTheme.strings
    val shown = remember(rows, sort, columns, sortLocally) {
        val comparator = sort?.takeIf { sortLocally }?.let { s -> columns.firstOrNull { it.key == s.columnKey }?.comparator?.let { c -> if (s.ascending) c else c.reversed() } }
        if (comparator == null) rows else rows.sortedWith(comparator)
    }
    val density = KompoundTheme.tokens.density
    val rowHeight = density.height(48.dp)
    val headerHeight = density.height(44.dp)
    val selectable = selection != null
    val checkWidth = 52.dp
    val scheme = MaterialTheme.colorScheme
    BoxWithConstraints(modifier) {
        val available = maxWidth
        val contentWidth = maxOf(available, minTableWidth)
        val fixed = columns.sumOf { (it.width as? KColumnWidth.Fixed)?.width?.value?.toDouble() ?: 0.0 }.toFloat() + if (selectable) checkWidth.value else 0f
        val totalWeight = columns.sumOf { ((it.width as? KColumnWidth.Weight)?.weight ?: 0f).toDouble() }.toFloat().coerceAtLeast(0.0001f)
        val flexible = (contentWidth.value - fixed).coerceAtLeast(0f)
        val widths = columns.map { c ->
            when (val w = c.width) {
                is KColumnWidth.Fixed -> w.width
                is KColumnWidth.Weight -> (flexible * w.weight / totalWeight).dp.coerceAtLeast(48.dp)
            }
        }
        val scroll = rememberScrollState()
        val total = widths.fold(0.dp) { a, b -> a + b } + if (selectable) checkWidth else 0.dp
        val grid = Modifier.semantics { collectionInfo = CollectionInfo(rowCount = shown.size + 1, columnCount = columns.size + if (selectable) 1 else 0) }
        Column(Modifier.then(if (total > available) Modifier.horizontalScroll(scroll) else Modifier).width(maxOf(total, available)).then(grid)) {
            // Header
            Row(
                Modifier.fillMaxWidth().height(headerHeight).background(scheme.surfaceContainerHigh).drawBehind {
                    drawRect(scheme.outlineVariant, Offset(0f, size.height - 1.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 1.dp.toPx()))
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (selectable) {
                    val all = shown.isNotEmpty() && shown.all { rowKey(it) in selection!! }
                    val some = shown.any { rowKey(it) in selection!! }
                    Box(Modifier.width(checkWidth), contentAlignment = Alignment.Center) {
                        KCheckbox(
                            state = if (all) ToggleableState.On else if (some) ToggleableState.Indeterminate else ToggleableState.Off,
                            onClick = { onSelectionChange?.invoke(if (all) selection!! - shown.map(rowKey).toSet() else selection!! + shown.map(rowKey)) },
                            modifier = Modifier.semantics { contentDescription = strings.selectAll },
                        )
                    }
                }
                columns.forEachIndexed { i, column ->
                    HeaderCell(column, widths[i], sort, if (onSortChange != null && column.sortable) ({ onSortChange(KSortState.next(sort, column.key)) }) else null, i + if (selectable) 1 else 0)
                }
            }
            when {
                loading -> repeat(5) {
                    Row(Modifier.fillMaxWidth().height(rowHeight), verticalAlignment = Alignment.CenterVertically) {
                        if (selectable) Spacer(checkWidth)
                        columns.forEachIndexed { i, _ -> Box(Modifier.width(widths[i]).padding(horizontal = 12.dp)) { KSkeleton(Modifier.fillMaxWidth().height(14.dp), animated = true) } }
                    }
                }
                shown.isEmpty() -> Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { emptyContent() }
                else -> {
                    val listState = rememberLazyListState()
                    // rows keep their identity across a sort, so the list would follow the old first row to its new place: start at the top
                    LaunchedEffect(sort) { listState.scrollToItem(0) }
                    val focusManager = LocalFocusManager.current
                    LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState) {
                        itemsIndexed(shown, key = { _, row -> rowKey(row) }) { index, row ->
                            val key = rowKey(row)
                            val isSelected = selection?.contains(key) == true
                            TableRow(
                                row, columns, widths, rowHeight, striped && index % 2 == 1, isSelected, selectable, checkWidth, index + 1, columns.size + if (selectable) 1 else 0,
                                onToggle = { if (selectable) onSelectionChange?.invoke(if (isSelected) selection!! - key else selection!! + key) },
                                onClick = onRowClick?.let { click -> { click(row) } },
                                onMove = { down -> focusManager.moveFocus(if (down) FocusDirection.Down else FocusDirection.Up) },
                                selectLabel = strings.selectRow,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Spacer(width: Dp) = Box(Modifier.width(width))

@Composable
private fun HeaderCell(column: KColumn<*>, width: Dp, sort: KSortState?, onClick: (() -> Unit)?, columnIndex: Int) {
    val strings = KompoundTheme.strings
    val active = sort?.takeIf { it.columnKey == column.key }
    val description = when {
        active == null -> null
        active.ascending -> strings.sortedAscending
        else -> strings.sortedDescending
    }
    val color = if (active != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .width(width)
            .fillMaxHeight()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = if (active?.ascending == true) strings.sortDescending else strings.sortAscending, onClick = onClick) else Modifier)
            .semantics(mergeDescendants = true) {
                collectionItemInfo = CollectionItemInfo(0, 1, columnIndex, 1)
                if (description != null) stateDescription = description
            }
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, column.alignment),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalKContentColor provides color) {
            KText(column.header, maxLines = 1, overflow = TextOverflow.Ellipsis, style = headerStyle(color))
            if (column.sortable && onClick != null) SortArrow(active?.ascending, color)
        }
    }
}

@Composable
private fun headerStyle(color: Color): androidx.compose.foundation.style.Style {
    val type = MaterialTheme.typography
    return remember(type, color) { androidx.compose.foundation.style.Style { contentColor(color); textStyle(type.labelLarge.copy(color = color)) } }
}

@Composable
private fun SortArrow(ascending: Boolean?, color: Color) {
    Box(
        Modifier.size(12.dp).drawBehind {
            val w = 1.6.dp.toPx()
            val c = size.width / 2f
            if (ascending == null) {
                // both directions, faint: the column can be sorted
                val faint = color.copy(alpha = 0.45f)
                drawPath(Path().apply { moveTo(c - 3.dp.toPx(), size.height * 0.4f); lineTo(c, size.height * 0.12f); lineTo(c + 3.dp.toPx(), size.height * 0.4f) }, faint, style = Stroke(w))
                drawPath(Path().apply { moveTo(c - 3.dp.toPx(), size.height * 0.6f); lineTo(c, size.height * 0.88f); lineTo(c + 3.dp.toPx(), size.height * 0.6f) }, faint, style = Stroke(w))
            } else {
                val up = ascending
                val y1 = if (up) size.height * 0.7f else size.height * 0.3f
                val y2 = if (up) size.height * 0.25f else size.height * 0.75f
                drawLine(color, Offset(c, size.height * 0.2f), Offset(c, size.height * 0.8f), w)
                drawPath(Path().apply { moveTo(c - 3.5.dp.toPx(), y1); lineTo(c, y2); lineTo(c + 3.5.dp.toPx(), y1) }, color, style = Stroke(w))
            }
        },
    )
}

@Composable
private fun <T> TableRow(
    row: T,
    columns: List<KColumn<T>>,
    widths: List<Dp>,
    height: Dp,
    stripe: Boolean,
    selected: Boolean,
    selectable: Boolean,
    checkWidth: Dp,
    rowIndex: Int,
    columnCount: Int,
    onToggle: () -> Unit,
    onClick: (() -> Unit)?,
    onMove: (down: Boolean) -> Unit,
    selectLabel: String,
) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val scheme = MaterialTheme.colorScheme
    val l = KompoundTheme.tokens.stateLayer
    val background = when {
        selected -> scheme.secondaryContainer.copy(alpha = 0.6f)
        hovered -> scheme.onSurface.copy(alpha = l.hovered)
        stripe -> scheme.onSurface.copy(alpha = 0.03f)
        else -> Color.Transparent
    }
    val interactive = onClick != null || selectable
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(background)
            .hoverable(source)
            .then(
                if (interactive) Modifier
                    .clickable(interactionSource = source, indication = null, role = Role.Button) { if (onClick != null) onClick() else onToggle() }
                    .onKeyEvent { e ->
                        if (e.type != KeyEventType.KeyDown) return@onKeyEvent false
                        when (e.key) {
                            Key.DirectionDown -> { onMove(true); true }
                            Key.DirectionUp -> { onMove(false); true }
                            Key.Spacebar -> if (selectable) { onToggle(); true } else false
                            else -> false
                        }
                    }
                else Modifier,
            )
            .semantics(mergeDescendants = true) {
                collectionItemInfo = CollectionItemInfo(rowIndex, 1, 0, columnCount)
                if (selectable) this.selected = selected
            }
            .drawBehind { drawRect(scheme.outlineVariant.copy(alpha = 0.5f), Offset(0f, size.height - 0.5.dp.toPx()), androidx.compose.ui.geometry.Size(size.width, 0.5.dp.toPx())) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (selectable) {
            Box(Modifier.width(checkWidth), contentAlignment = Alignment.Center) {
                KCheckbox(selected, onCheckedChange = { onToggle() }, Modifier.semantics { contentDescription = selectLabel })
            }
        }
        columns.forEachIndexed { i, column ->
            Box(Modifier.width(widths[i]).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart.let { if (column.alignment == Alignment.End) Alignment.CenterEnd else if (column.alignment == Alignment.CenterHorizontally) Alignment.Center else it }) {
                column.cell(row)
            }
        }
    }
}

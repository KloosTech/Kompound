package tech.kloos.kompound.accordion

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.style.MutableStyleState
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.styleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.divider.KDivider

/**
 * Which sections of a [KAccordion] are open. With [exclusive] set, opening a section closes the others.
 * Survives configuration changes when created with [rememberKAccordionState].
 */
@Stable
public class KAccordionState(public val exclusive: Boolean = false, initiallyExpanded: Set<String> = emptySet()) {
    private val open = mutableStateMapOf<String, Boolean>().apply { initiallyExpanded.forEach { put(it, true) } }

    /** Keys of the sections that are currently open. */
    public val expandedKeys: Set<String> get() = open.filterValues { it }.keys

    /** Whether the section [key] is open. */
    public fun isExpanded(key: String): Boolean = open[key] == true

    /** Opens or closes [key]; opening closes all other sections when [exclusive]. */
    public fun setExpanded(key: String, expanded: Boolean) {
        if (expanded && exclusive) open.keys.toList().forEach { if (it != key) open[it] = false }
        open[key] = expanded
    }

    /** Flips [key]. */
    public fun toggle(key: String): Unit = setExpanded(key, !isExpanded(key))

    /** Closes every section. */
    public fun collapseAll() { open.keys.toList().forEach { open[it] = false } }

    internal companion object {
        fun saver(exclusive: Boolean): Saver<KAccordionState, Any> =
            listSaver(save = { it.expandedKeys.toList() }, restore = { KAccordionState(exclusive, it.toSet()) })
    }
}

/** Remembers a [KAccordionState] across configuration changes and process death. */
@Composable
public fun rememberKAccordionState(exclusive: Boolean = false, initiallyExpanded: Set<String> = emptySet()): KAccordionState =
    rememberSaveable(exclusive, saver = KAccordionState.saver(exclusive)) { KAccordionState(exclusive, initiallyExpanded) }

/** Scope of [KAccordion]; its [Item] ties a [KExpandable] to the accordion's state. */
@Stable
public class KAccordionScope internal constructor(private val state: KAccordionState) {
    /**
     * One section. [key] identifies it in the [KAccordionState] and must be unique within the accordion.
     * A divider is drawn below the section.
     */
    @Composable
    public fun Item(
        key: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        headerStyle: Style = Style,
        contentStyle: Style = Style,
        header: @Composable RowScope.() -> Unit,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        KExpandable(
            expanded = state.isExpanded(key),
            onExpandedChange = { state.setExpanded(key, it) },
            modifier = modifier,
            enabled = enabled,
            headerStyle = headerStyle,
            contentStyle = contentStyle,
            header = header,
            content = content,
        )
        KDivider()
    }

    /** [Item] with a one-line [title] as header. */
    @Composable
    public fun Item(
        key: String,
        title: String,
        modifier: Modifier = Modifier,
        enabled: Boolean = true,
        headerStyle: Style = Style,
        contentStyle: Style = Style,
        content: @Composable ColumnScope.() -> Unit,
    ) {
        KExpandable(
            title = title,
            expanded = state.isExpanded(key),
            onExpandedChange = { state.setExpanded(key, it) },
            modifier = modifier,
            enabled = enabled,
            headerStyle = headerStyle,
            contentStyle = contentStyle,
            content = content,
        )
        KDivider()
    }
}

/**
 * A stack of [KExpandable] sections managed by one [KAccordionState]. For long or dynamic lists build the same
 * from `LazyColumn` items with [KExpandable] and your own state: this one composes every section.
 *
 * @param state Open sections; [rememberKAccordionState] by default.
 * @param modifier Modifier applied to the outermost node.
 * @param style Overrides merged over a transparent container.
 * @param content The sections: `Item(key = "shipping", title = "Shipping") { ... }`.
 */
@Composable
public fun KAccordion(
    modifier: Modifier = Modifier,
    state: KAccordionState = rememberKAccordionState(),
    style: Style = Style,
    content: @Composable KAccordionScope.() -> Unit,
) {
    remember { KompoundStyles.ensureEnabled() }
    val styleState = remember { MutableStyleState(null) }
    val scope = remember(state) { KAccordionScope(state) }
    Column(modifier.fillMaxWidth().styleable(styleState, style)) { scope.content() }
}

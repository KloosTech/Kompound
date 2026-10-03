package tech.kloos.kompound.graph

import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.contentPadding
import androidx.compose.foundation.style.rememberUpdatedStyleState
import androidx.compose.foundation.style.selected
import androidx.compose.foundation.style.styleable
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.KompoundStyles
import tech.kloos.kompound.graph.model.GraphCommand
import tech.kloos.kompound.graph.model.GraphNode
import tech.kloos.kompound.graph.model.NodeId
import tech.kloos.kompound.textfield.KTextArea

/** The node kind of comment (note) nodes. `KNodeGraph` draws them itself; [nodeContent] never sees them. */
public const val KCommentKind: String = "comment"

/** Creates a comment node: a sticky note with editable text kept in [GraphNode.data] as a `String`. It has no ports. */
public fun commentNode(id: String, position: Offset, text: String = ""): GraphNode = GraphNode(NodeId(id), KCommentKind, position, emptyList(), text)

/**
 * A comment node: a sticky note you can write in. Drag its top strip to move it; the text is committed (one undo step) when the field
 * loses focus.
 *
 * @param node The comment node (kind [KCommentKind]).
 * @param modifier Modifier applied to the note.
 * @param width Width in world units.
 * @param style Overrides merged over [KCommentDefaults.style].
 */
@Composable
public fun KComment(node: GraphNode, modifier: Modifier = Modifier, width: Dp = 200.dp, style: Style = Style) {
    remember { KompoundStyles.ensureEnabled() }
    val state = LocalKGraphState.current ?: error("KComment must be used inside KNodeGraph")
    val source = remember { MutableInteractionSource() }
    val selected = node.id in state.selection
    val styleState = rememberUpdatedStyleState(source) { it.isSelected = selected }
    val stored = node.data as? String ?: ""
    var draft by remember(stored) { mutableStateOf(stored) }
    Column(
        modifier
            .width(width)
            .onSizeChanged { state.sizes[node.id] = Size(it.width.toFloat(), it.height.toFloat()) }
            .semantics { contentDescription = "Comment" }
            .focusable(true, source)
            .nodeSelectOnClick(state, node.id)
            .styleable(styleState, KCommentDefaults.style(), style),
    ) {
        // the drag strip
        Column(Modifier.fillMaxWidth().height(14.dp).nodeDragHandle(state, node.id)) {}
        KTextArea(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp).onFocusChanged { focus ->
                if (!focus.isFocused && draft != (node.data as? String ?: "")) state.execute(GraphCommand.UpdateNodeData(node.id, draft))
            },
            placeholder = "Note",
            minLines = 2,
            maxLines = 8,
        )
    }
}

/** Defaults for [KComment]. */
public object KCommentDefaults {
    /** A note: `tertiaryContainer`, medium shape, a primary outline when selected. */
    @Composable
    public fun style(): Style {
        val c = MaterialTheme.colorScheme
        val shapes = MaterialTheme.shapes
        return remember(c, shapes) {
            Style {
                background(c.tertiaryContainer)
                contentColor(c.onTertiaryContainer)
                shape(shapes.medium)
                borderWidth(1.dp)
                borderColor(c.outlineVariant)
                contentPadding(0.dp)
                selected { borderWidth(2.dp); borderColor(c.primary) }
            }
        }
    }
}

package tech.kloos.kompound.showcase.tree

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.tree.KTreeView
import tech.kloos.kompound.tree.rememberKTreeState

private const val Usage_tree = """import tech.kloos.kompound.tree.KTreeView
import tech.kloos.kompound.tree.rememberKTreeState

val state = rememberKTreeState(expanded = setOf("src"))

KTreeView(
    roots = files,
    children = { it.children },
    key = { it.path },
    modifier = Modifier.height(300.dp), // bounded height
    state = state,
    onSelect = { open(it) },
) { node, depth -> KText(node.name) }"""

private class Node(val path: String, val name: String, val children: List<Node> = emptyList())

private fun folder(path: String, name: String, depth: Int, width: Int): Node =
    Node(path, name, if (depth == 0) List(width) { Node("$path/file$it.kt", "File$it.kt") } else List(width) { folder("$path/dir$it", "dir$it", depth - 1, width) })

@KompoundDemo(
    id = "tree.view",
    title = "KTreeView",
    description = "An expandable tree with the WAI-ARIA keyboard model and virtualized rows, for file browsers and outlines.",
    category = KompoundCategory.Data,
    tags = ["tree", "outline", "hierarchy", "file browser", "expand"],
    since = "0.2.0",
    status = "Beta",
    usage = Usage_tree,
)
@Composable
fun DemoScope.KTreeViewDemo() {
    val depth = floatControl("Depth", 1f..6f, 4f).toInt()
    val width = floatControl("Children per node", 1f..6f, 4f).toInt()
    val roots = remember(depth, width) { listOf(folder("src", "src", depth, width), folder("docs", "docs", 1, width)) }
    val state = rememberKTreeState(expanded = setOf("src"))
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        KTreeView(roots, { it.children }, { it.path }, Modifier.fillMaxWidth().height(320.dp), state = state) { node, _ -> KText(node.name) }
        KText("Selected: ${state.selected ?: "none"}")
    }
}

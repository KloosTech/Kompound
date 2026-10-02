package tech.kloos.kompound.showcase.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.annotations.KompoundCategory
import tech.kloos.kompound.annotations.KompoundDemo
import tech.kloos.kompound.buttons.KFab
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.demo.DemoScope
import tech.kloos.kompound.divider.KDivider
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.list.KListItem
import tech.kloos.kompound.scaffold.KScaffold
import tech.kloos.kompound.scaffold.KTopBar
import tech.kloos.kompound.showcase.DemoIcons
import tech.kloos.kompound.text.KText

private const val Usage_scaffold_screen = """import tech.kloos.kompound.buttons.KFab
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.scaffold.KScaffold
import tech.kloos.kompound.scaffold.KTopBar
import tech.kloos.kompound.snackbar.KSnackbarHost
import tech.kloos.kompound.snackbar.KSnackbarHostState

val snackbars = remember { KSnackbarHostState() }

KScaffold(
    topBar = {
        KTopBar(
            title = "Inbox",
            navigation = { KIconButton(onClick = { back() }, contentDescription = "Back") { KIcon(Icons.Rounded.Star, null) } },
        )
    },
    floatingActionButton = { KFab(onClick = { compose() }, contentDescription = "Write") { KIcon(Icons.Rounded.Add, null) } },
    snackbarHost = { KSnackbarHost(snackbars) },
) { padding ->
    Column(Modifier.padding(padding)) {
        KText("Content")
    }
}"""

@KompoundDemo(
    id = "scaffold.screen",
    title = "KTopBar and KScaffold",
    description = "Screen layout with a top bar, bottom bar, floating action button and snackbar slot; insets are explicit.",
    category = KompoundCategory.Layout,
    tags = ["scaffold", "top bar", "app bar", "screen", "layout", "toolbar"],
    since = "0.1.0",
    usage = Usage_scaffold_screen,
)
@Composable
fun DemoScope.KScaffoldDemo() {
    val title = textControl("Title", "Inbox")
    val navigation = boolControl("Navigation icon", true)
    val actions = boolControl("Action icons", true)
    val fab = boolControl("Floating action button", true)
    val bottomBar = boolControl("Bottom bar", true)
    Box(Modifier.fillMaxWidth().height(420.dp).padding(8.dp)) {
        KScaffold(
            topBar = {
                KTopBar(
                    title = title,
                    windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),   // the demo sits inside the catalog, which handles the insets
                    navigation = if (navigation) ({ KIconButton(onClick = {}, contentDescription = "Back") { KIcon(DemoIcons.Check, null) } }) else null,
                    actions = if (actions) ({
                        KIconButton(onClick = {}, contentDescription = "Favourite") { KIcon(DemoIcons.Star, null) }
                        KIconButton(onClick = {}, contentDescription = "Done") { KIcon(DemoIcons.Check, null) }
                    }) else null,
                )
            },
            bottomBar = if (bottomBar) ({
                Box(Modifier.fillMaxWidth()) {
                    KDivider()
                    KText("Bottom bar slot", Modifier.padding(16.dp))
                }
            }) else null,
            floatingActionButton = if (fab) ({ KFab(onClick = {}, contentDescription = "Create") { KIcon(DemoIcons.Star, null) } }) else null,
        ) { padding ->
            Box(Modifier.padding(padding)) {
                androidx.compose.foundation.layout.Column {
                    repeat(5) { KListItem("Message ${it + 1}", supporting = "Preview of the message text") }
                }
            }
        }
    }
}

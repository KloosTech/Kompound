package tech.kloos.kompound.scaffold

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.SquareIcon
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.buttons.KIconButton
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.near
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KScaffoldTest {
    private val s = ButtonTestScheme.copy(onSurfaceVariant = Color(0xFFFF0080), surface = Color(0xFF123456), background = Color(0xFF654321))
    private val none = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp)

    @Test
    fun topBarShowsTitleAndIsAtLeast64dpHigh() = runComposeUiTest {
        setContent { MaterialTheme(s) { KTopBar("Inbox", Modifier.testTag("bar"), windowInsets = none) } }
        onNodeWithText("Inbox", useUnmergedTree = true).assertExists()
        assertEquals(64, onNodeWithTag("bar").fetchSemanticsNode().size.height)
    }

    @Test
    fun topBarContentIsPushedDownByTheInsetsButTheBackgroundCoversThem() = runComposeUiTest {
        setContent {
            MaterialTheme(s) { KTopBar("Inbox", Modifier.testTag("bar"), windowInsets = WindowInsets(0.dp, 24.dp, 0.dp, 0.dp)) }
        }
        val node = onNodeWithTag("bar")
        assertEquals(64 + 24, node.fetchSemanticsNode().size.height)
        assertTrue(node.captureToImage().toPixelMap()[4, 4].near(s.surface), "background reaches the very top")
    }

    @Test
    fun navigationAndActionsAreShownWithQuietIcons() = runComposeUiTest {
        var actions = 0
        setContent {
            MaterialTheme(s) {
                KTopBar(
                    "T", Modifier.testTag("bar"), windowInsets = none,
                    navigation = { KIconButton(onClick = {}, "Back") { KIcon(SquareIcon, null) } },
                    actions = { KIconButton(onClick = { actions++ }, "Search") { KIcon(SquareIcon, null) } },
                )
            }
        }
        onNodeWithContentDescription("Search").performClick()
        assertEquals(1, actions)
        onNodeWithContentDescription("Back").assertExists()
        assertTrue(onNodeWithTag("bar").captureToImage().containsColor(s.onSurfaceVariant), "icons use onSurfaceVariant")
    }

    @Test
    fun longTitleStopsBeforeTheActions() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Box(Modifier.width(240.dp)) {
                    KTopBar(
                        "W".repeat(80), Modifier.testTag("bar"), windowInsets = none,
                        actions = { KIconButton(onClick = {}, "Act") { KIcon(SquareIcon, null) } },
                    )
                }
            }
        }
        assertEquals(240, onNodeWithTag("bar").fetchSemanticsNode().size.width)
        val action = onNodeWithContentDescription("Act").fetchSemanticsNode().boundsInRoot
        assertTrue(action.right <= 240f, "the action stays inside the bar")
    }

    @Test
    fun scaffoldPlacesBarsContentFabAndSnackbar() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Box(Modifier.width(300.dp).height(500.dp)) {
                    KScaffold(
                        topBar = { Box(Modifier.testTag("top").width(300.dp).height(50.dp)) },
                        bottomBar = { Box(Modifier.testTag("bottom").height(40.dp).width(300.dp)) },
                        floatingActionButton = { Box(Modifier.testTag("fab").width(56.dp).height(56.dp)) },
                        snackbarHost = { Box(Modifier.testTag("snack").width(100.dp).height(40.dp)) },
                    ) { Box(Modifier.testTag("content").fillMaxSize()) }
                }
            }
        }
        val root = onNodeWithTag("content").fetchSemanticsNode().boundsInRoot
        val bottom = onNodeWithTag("bottom").fetchSemanticsNode().boundsInRoot
        val fab = onNodeWithTag("fab").fetchSemanticsNode().boundsInRoot
        val snack = onNodeWithTag("snack").fetchSemanticsNode().boundsInRoot
        assertEquals(500f, bottom.bottom, "bottom bar sits at the bottom")
        assertEquals(bottom.top, root.bottom, "content ends where the bottom bar starts")
        assertEquals(root.bottom - 16f, fab.bottom, "fab is 16 above the content's bottom edge")
        assertEquals(300f - 16f, fab.right, "fab is 16 from the end edge")
        assertEquals(root.bottom - 16f, snack.bottom, "snackbar host is 16 above the bottom")
        assertTrue(kotlin.math.abs((snack.left + snack.right) / 2f - 150f) < 1f, "snackbar host is centred")
    }

    @Test
    fun scaffoldPaintsTheBackgroundAndPassesInsetsAsPadding() = runComposeUiTest {
        var received: androidx.compose.foundation.layout.PaddingValues? = null
        setContent {
            MaterialTheme(s) {
                Box(Modifier.testTag("screen").width(100.dp).height(100.dp)) {
                    KScaffold(contentWindowInsets = WindowInsets(4.dp, 8.dp, 12.dp, 16.dp)) { padding -> received = padding }
                }
            }
        }
        assertTrue(onNodeWithTag("screen").captureToImage().toPixelMap()[50, 50].near(s.background), "screen background")
        val p = received!!
        assertEquals(8.dp, p.calculateTopPadding())
        assertEquals(16.dp, p.calculateBottomPadding())
        assertEquals(4.dp, p.calculateLeftPadding(androidx.compose.ui.unit.LayoutDirection.Ltr))
        assertEquals(12.dp, p.calculateRightPadding(androidx.compose.ui.unit.LayoutDirection.Ltr))
    }
}

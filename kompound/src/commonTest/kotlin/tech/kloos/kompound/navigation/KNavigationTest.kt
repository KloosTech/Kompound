package tech.kloos.kompound.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.countText
import tech.kloos.kompound.text.KText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KNavigationTest {
    private val scheme = lightColorScheme()
    private val items = listOf(
        KNavItem("home", "Home", { Box(Modifier.size(24.dp)) }),
        KNavItem("inbox", "Inbox", { Box(Modifier.size(24.dp)) }, badge = "4"),
        KNavItem("archive", "Archive", { Box(Modifier.size(24.dp)) }, enabled = false),
        KNavItem("settings", "Settings", { Box(Modifier.size(24.dp)) }, contentDescription = "App settings"),
    )

    private fun androidx.compose.ui.test.SemanticsNodeInteraction.role() = fetchSemanticsNode().config.getOrNull(SemanticsProperties.Role)
    private fun androidx.compose.ui.test.SemanticsNodeInteraction.selected() = fetchSemanticsNode().config.getOrNull(SemanticsProperties.Selected)

    @Test
    fun theBarShowsEveryDestinationAsATabAndMarksTheSelectedOne() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KNavigationBar(items, "inbox", {}, Modifier.testTag("bar")) } }
        waitForIdle()
        assertEquals(Role.Tab, onNodeWithText("Home").role())
        assertEquals(true, onNodeWithText("Inbox").selected())
        assertEquals(false, onNodeWithText("Home").selected())
        onNodeWithText("4").assertExists()
        onNodeWithText("Archive").assertIsNotEnabled()
        onNodeWithContentDescription("App settings").assertExists()
    }

    @Test
    fun clickingReportsTheKeyAndADisabledItemDoesNothing() = runComposeUiTest {
        val picks = mutableListOf<String>()
        setContent { MaterialTheme(scheme) { KNavigationBar(items, "home", { picks += it }) } }
        onNodeWithText("Inbox").performClick()
        onNodeWithText("Archive").performClick()
        waitForIdle()
        assertEquals(listOf("inbox"), picks)
    }

    @Test
    fun onlyTheSelectedLabelShowsWhenAlwaysShowLabelIsOff() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KNavigationBar(items, "home", {}, alwaysShowLabel = false) } }
        waitForIdle()
        onNodeWithText("Home").assertExists()
        assertEquals(0, countText("Inbox"))
    }

    @Test
    fun arrowKeysMoveAcrossTheBarSkippingDisabledItems() = runComposeUiTest {
        var selected by mutableStateOf("inbox")
        setContent { MaterialTheme(scheme) { KNavigationBar(items, selected, { selected = it }) } }
        onNodeWithText("Inbox").requestFocus()
        onNodeWithText("Inbox").performKeyInput { pressKey(Key.DirectionRight) }
        waitForIdle()
        assertEquals("settings", selected)
        onNodeWithText("Settings").performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()
        assertEquals("inbox", selected)
    }

    @Test
    fun theRailStacksDestinationsAndHasHeaderAndFooterSlots() = runComposeUiTest {
        val picks = mutableListOf<String>()
        setContent {
            MaterialTheme(scheme) {
                Box(Modifier.size(300.dp, 500.dp)) {
                    KNavigationRail(items, "home", { picks += it }, header = { KText("HeaderSlot") }, footer = { KText("FooterSlot") })
                }
            }
        }
        waitForIdle()
        onNodeWithText("HeaderSlot").assertExists()
        onNodeWithText("FooterSlot").assertExists()
        assertEquals(true, onNodeWithText("Home").selected())
        onNodeWithText("Inbox").performClick()
        assertEquals(listOf("inbox"), picks)
        onNodeWithText("Inbox").requestFocus()
        onNodeWithText("Inbox").performKeyInput { pressKey(Key.DirectionDown) }
        waitForIdle()
        assertEquals("settings", picks.last(), "down moves to the next enabled destination")
    }

    @Test
    fun theDrawerListsItemsSectionsAndDividers() = runComposeUiTest {
        val picks = mutableListOf<String>()
        val entries = listOf(
            KNavEntry.Item(items[0]), KNavEntry.Item(items[1]), KNavEntry.Divider, KNavEntry.Section("Admin"), KNavEntry.Item(items[3]),
        )
        setContent { MaterialTheme(scheme) { Box(Modifier.size(400.dp, 600.dp)) { KNavigationDrawer(entries, "settings", { picks += it }, header = { KText("My app") }) } } }
        waitForIdle()
        onNodeWithText("My app").assertExists()
        onNodeWithText("Admin").assertExists()
        assertEquals(true, onNodeWithText("Settings").selected())
        assertEquals(Role.Tab, onNodeWithText("Home").role())
        onNodeWithText("Home").performClick()
        assertEquals(listOf("home"), picks)
    }

    @Test
    fun theModalDrawerOpensOverTheContentAndClosesFromTheScrimAndEscape() = runComposeUiTest {
        var open by mutableStateOf(false)
        var closes = 0
        setContent {
            MaterialTheme(scheme) {
                KModalNavigationDrawer(open, { closes++; open = false }, drawer = { KText("DrawerContent") }, Modifier.size(500.dp, 400.dp)) { KText("ScreenBehind") }
            }
        }
        waitForIdle()
        onNodeWithText("ScreenBehind").assertExists()
        assertEquals(0, countText("DrawerContent"), "closed: no drawer in the tree")
        open = true
        waitForIdle()
        onNodeWithText("DrawerContent").assertExists()
        onNodeWithContentDescription("Close navigation menu").performClick()
        waitForIdle()
        assertEquals(1, closes)
        assertEquals(0, countText("DrawerContent"))
        open = true
        waitForIdle()
        onNodeWithText("DrawerContent").performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertTrue(closes >= 2, "escape closes it: $closes")
    }
}

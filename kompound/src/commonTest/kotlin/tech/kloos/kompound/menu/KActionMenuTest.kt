package tech.kloos.kompound.menu

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.SquareIcon
import tech.kloos.kompound.buttons.ButtonTestScheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KActionMenuTest {
    private val s = ButtonTestScheme

    @Test
    fun opensOnTheTriggerAndClosesAfterAPick() = runComposeUiTest {
        var picked = 0
        setContent {
            MaterialTheme(s) {
                KActionMenu(listOf(KMenuActionItem("Rename", { picked++ })), contentDescription = "More actions")
            }
        }
        onNodeWithText("Rename").assertDoesNotExist()
        onNodeWithContentDescription("More actions").performClick()
        waitForIdle()
        onNodeWithText("Rename").performClick()
        waitForIdle()
        assertEquals(1, picked)
        onNodeWithText("Rename").assertDoesNotExist()
    }

    @Test
    fun stayOpenEntriesKeepTheMenuOpen() = runComposeUiTest {
        var count = 0
        setContent { MaterialTheme(s) { KActionMenu(listOf(KMenuActionItem("Toggle", { count++ }, closeOnClick = false)), "More") } }
        onNodeWithContentDescription("More").performClick()
        waitForIdle()
        onNodeWithText("Toggle").performClick()
        onNodeWithText("Toggle").performClick()
        assertEquals(2, count)
        onNodeWithText("Toggle").assertExists()
    }

    @Test
    fun nullEntriesAreSkippedAndSupportingTextIsShown() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KActionMenu(
                    listOf(null, KMenuActionItem("Sync", {}, supportingText = "Last sync 5 min ago"), null, KMenuActionDivider, KMenuActionItem("Delete", {})),
                    "More",
                )
            }
        }
        onNodeWithContentDescription("More").performClick()
        waitForIdle()
        onNodeWithText("Sync").assertExists()
        onNodeWithText("Last sync 5 min ago").assertExists()
        onNodeWithText("Delete").assertExists()
    }

    @Test
    fun groupsUnfoldInPlace() = runComposeUiTest {
        var picked = ""
        setContent {
            MaterialTheme(s) {
                KActionMenu(listOf(KMenuActionGroup("Sort by", listOf(KMenuActionItem("Name", { picked = "name" }), KMenuActionItem("Date", { picked = "date" })))), "More")
            }
        }
        onNodeWithContentDescription("More").performClick()
        waitForIdle()
        onNodeWithText("Name").assertDoesNotExist()
        onNodeWithText("Sort by").performClick()
        waitForIdle()
        onNodeWithText("Date").performClick()
        assertEquals("date", picked)
    }

    @Test
    fun selectedEntriesAreMarkedAndDisabledOnesIgnoreClicks() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(s) {
                KActionMenu(listOf(KMenuActionItem("Locked", { clicks++ }, enabled = false), KMenuActionItem("Current", {}, selected = true, icon = SquareIcon)), "More")
            }
        }
        onNodeWithContentDescription("More").performClick()
        waitForIdle()
        onNodeWithText("Locked").assertIsNotEnabled()
        runCatching { onNodeWithText("Locked").performClick() }
        assertEquals(0, clicks)
    }

    @Test
    fun badgeShowsOnTheTriggerWithItsDescription() = runComposeUiTest {
        setContent { MaterialTheme(s) { KActionMenu(emptyList(), "More", Modifier.testTag("m"), showBadge = true, badgeContentDescription = "Needs attention") } }
        onNodeWithContentDescription("Needs attention").assertExists()
    }
}

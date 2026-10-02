package tech.kloos.kompound.steps

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.buttons.ButtonTestScheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class KStepListTest {
    private val s = ButtonTestScheme

    @Test
    fun showsEveryStepTitleAndTrailingNote() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KStepList(listOf(KStep("Download", KStepState.Done, "0:12"), KStep("Install", KStepState.InProgress(0.4f), "0:30"), KStep("Restart")))
            }
        }
        listOf("Download", "Install", "Restart", "0:12", "0:30").forEach { onNodeWithText(it, useUnmergedTree = true).assertExists() }
    }

    @Test
    fun stepsAnnounceTheirStateAndPercentage() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KStepList(listOf(KStep("A", KStepState.Done), KStep("B", KStepState.InProgress(0.4f)), KStep("C", KStepState.InProgress(null)), KStep("D")))
            }
        }
        val states = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).fetchSemanticsNodes()
            .map { it.config.getOrNull(SemanticsProperties.StateDescription) }
        assertEquals(listOf("Done", "In progress, 40%", "In progress", "Waiting"), states)
    }

    @Test
    fun labelsCanBeLocalised() = runComposeUiTest {
        setContent { MaterialTheme(s) { KStepList(listOf(KStep("A", KStepState.Done)), labels = KStepLabels(done = "Fertig")) } }
        val states = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription)).fetchSemanticsNodes().map { it.config.getOrNull(SemanticsProperties.StateDescription) }
        assertEquals(listOf("Fertig"), states)
    }

    @Test
    fun changingAStateUpdatesTheList() = runComposeUiTest {
        var steps = androidx.compose.runtime.mutableStateOf(listOf(KStep("A", KStepState.Waiting)))
        setContent { MaterialTheme(s) { KStepList(steps.value, Modifier.testTag("l")) } }
        steps.value = listOf(KStep("A", KStepState.Done, "done in 3 s"))
        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        onNodeWithText("done in 3 s", useUnmergedTree = true).assertExists()
    }

    @Test
    fun emptyListRenders() = runComposeUiTest {
        setContent { MaterialTheme(s) { KStepList(emptyList(), Modifier.testTag("l")) } }
        onNodeWithTag("l").assertExists()
    }
}

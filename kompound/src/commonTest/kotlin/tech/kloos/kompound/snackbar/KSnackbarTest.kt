package tech.kloos.kompound.snackbar

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.buttons.topCentre
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.near
import tech.kloos.kompound.theme.KompoundColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KSnackbarTest {
    private val s = ButtonTestScheme.copy(error = Color(0xFFFF00FF), inverseSurface = Color(0xFF202020), inverseOnSurface = Color(0xFFFFFF80))

    private class Harness(val host: KSnackbarHostState, val scope: CoroutineScope)

    private fun androidx.compose.ui.test.ComposeUiTest.mount(): Harness {
        val host = KSnackbarHostState()
        lateinit var scope: CoroutineScope
        mainClock.autoAdvance = false
        setContent {
            scope = rememberCoroutineScope()
            MaterialTheme(s) { KSnackbarHost(host, Modifier.testTag("host")) }
        }
        mainClock.advanceTimeByFrame()
        return Harness(host, scope)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.launchShow(h: Harness, block: suspend () -> Unit): Job {
        var job: Job? = null
        runOnUiThread { job = h.scope.launch { block() } }
        mainClock.advanceTimeBy(300)
        return job!!
    }

    @Test
    fun showsTheMessageThenDismissesItAfterTheShortDuration() = runComposeUiTest {
        val h = mount()
        var result: KSnackbarResult? = null
        launchShow(h) { result = h.host.showSnackbar("Saved") }
        onNodeWithText("Saved", useUnmergedTree = true).assertExists()
        mainClock.advanceTimeBy(3_500)
        onNodeWithText("Saved", useUnmergedTree = true).assertExists()
        assertNull(result)
        mainClock.advanceTimeBy(1_000)
        assertEquals(KSnackbarResult.Dismissed, result)
        onNodeWithText("Saved", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun actionEndsTheSnackbarWithActionPerformed() = runComposeUiTest {
        val h = mount()
        var result: KSnackbarResult? = null
        launchShow(h) { result = h.host.showSnackbar("Deleted", actionLabel = "Undo") }
        onNodeWithText("Undo", useUnmergedTree = true).performClick()
        mainClock.advanceTimeBy(300)
        assertEquals(KSnackbarResult.ActionPerformed, result)
        onNodeWithText("Deleted", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun snackbarsWithAnActionStayLongerByDefault() = runComposeUiTest {
        val h = mount()
        var result: KSnackbarResult? = null
        launchShow(h) { result = h.host.showSnackbar("Deleted", actionLabel = "Undo") }
        mainClock.advanceTimeBy(6_000)
        assertNull(result, "still shown after 6s (long duration)")
        mainClock.advanceTimeBy(5_000)
        assertEquals(KSnackbarResult.Dismissed, result)
    }

    @Test
    fun messagesAreQueuedOneAfterAnother() = runComposeUiTest {
        val h = mount()
        var first: KSnackbarResult? = null
        var second: KSnackbarResult? = null
        launchShow(h) { first = h.host.showSnackbar("First") }
        launchShow(h) { second = h.host.showSnackbar("Second") }
        onNodeWithText("First", useUnmergedTree = true).assertExists()
        onNodeWithText("Second", useUnmergedTree = true).assertDoesNotExist()
        runOnUiThread { h.host.currentSnackbar!!.dismiss() }
        mainClock.advanceTimeBy(500)
        assertEquals(KSnackbarResult.Dismissed, first)
        onNodeWithText("Second", useUnmergedTree = true).assertExists()
        assertNull(second)
    }

    @Test
    fun indefiniteSnackbarsStayUntilDismissed() = runComposeUiTest {
        val h = mount()
        var result: KSnackbarResult? = null
        launchShow(h) { result = h.host.showSnackbar("Stay", duration = KSnackbarDuration.Indefinite) }
        mainClock.advanceTimeBy(60_000)
        onNodeWithText("Stay", useUnmergedTree = true).assertExists()
        assertNull(result)
    }

    @Test
    fun cancellingTheCallerRemovesTheSnackbar() = runComposeUiTest {
        val h = mount()
        val job = launchShow(h) { h.host.showSnackbar("Bye", duration = KSnackbarDuration.Indefinite) }
        onNodeWithText("Bye", useUnmergedTree = true).assertExists()
        runOnUiThread { job.cancel() }
        mainClock.advanceTimeBy(500)
        onNodeWithText("Bye", useUnmergedTree = true).assertDoesNotExist()
        assertNull(h.host.currentSnackbar)
    }

    @Test
    fun tonesUseTheirColours() = runComposeUiTest {
        val h = mount()
        launchShow(h) { h.host.showSnackbar("MMMM", tone = KSnackbarTone.Error, duration = KSnackbarDuration.Indefinite) }
        assertTrue(onNodeWithTag("host").captureToImage().topCentre(3).near(s.error), "error container")
    }

    @Test
    fun neutralUsesInverseColoursAndActionIsTinted() = runComposeUiTest {
        val h = mount()
        launchShow(h) { h.host.showSnackbar("MMMM", actionLabel = "MMMM", duration = KSnackbarDuration.Indefinite) }
        val img = onNodeWithTag("host").captureToImage()
        assertTrue(img.topCentre(3).near(s.inverseSurface), "inverse surface")
        assertTrue(img.containsColor(s.inverseOnSurface), "message colour")
        assertTrue(img.containsColor(s.inversePrimary), "action colour")
    }

    @Test
    fun snackbarIsAnnouncedPolitely() = runComposeUiTest {
        val h = mount()
        launchShow(h) { h.host.showSnackbar("Hello", duration = KSnackbarDuration.Indefinite) }
        val live = onNode(androidx.compose.ui.test.SemanticsMatcher.keyIsDefined(SemanticsProperties.LiveRegion), useUnmergedTree = true)
        assertEquals(LiveRegionMode.Polite, live.fetchSemanticsNode().config.getOrNull(SemanticsProperties.LiveRegion))
    }

    @Test
    fun customTonesAreSolidColoursFromTheKompoundTokens() = runComposeUiTest {
        val h = mount()
        launchShow(h) { h.host.showSnackbar("MMMM", tone = KSnackbarTone.Success, duration = KSnackbarDuration.Indefinite) }
        assertTrue(onNodeWithTag("host").captureToImage().topCentre(3).near(KompoundColors.Light.success), "success container")
    }
}

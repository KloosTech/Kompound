package tech.kloos.kompound.catalog.harness

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.catalog.KompoundAllDemos
import tech.kloos.kompound.catalog.ui.KompoundCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class HarnessTest {
    private val slider get() = KompoundAllDemos.entries.first { it.meta.id.startsWith("slider") }

    @Test
    fun aLinkWithoutOptionsGetsTheDefaults() {
        val l = HarnessLaunch.parse("kompound://demo/button.basic")!!
        assertEquals(HarnessLaunch("button.basic"), l)
        assertEquals(false, l.dark)
        assertEquals("en", l.lang)
        assertEquals(true, l.bare)
    }

    @Test
    fun everyOptionIsRead() {
        val l = HarnessLaunch.parse("kompound://demo/showcase/slider.basic?theme=dark&font=2.0&rtl=true&density=compact&lang=de&bare=false&control=Enabled::false&control=Steps%20(0%20%3D%20continuous)::4")!!
        assertEquals("showcase/slider.basic", l.demo)
        assertEquals(true, l.dark)
        assertEquals(2f, l.fontScale)
        assertEquals(true, l.rtl)
        assertEquals("compact", l.density)
        assertEquals("de", l.lang)
        assertEquals(false, l.bare)
        assertEquals(mapOf("Enabled" to "false", "Steps (0 = continuous)" to "4"), l.controls)
    }

    @Test
    fun valuesAreCheckedAndPercentDecodedAsUtf8() {
        assertEquals(3f, HarnessLaunch.parse("kompound://demo/x?font=9")!!.fontScale, "clamped")
        assertEquals(0.5f, HarnessLaunch.parse("kompound://demo/x?font=0")!!.fontScale)
        assertEquals(1f, HarnessLaunch.parse("kompound://demo/x?font=abc")!!.fontScale)
        assertEquals("comfortable", HarnessLaunch.parse("kompound://demo/x?density=huge")!!.density)
        assertEquals(mapOf("Name" to "Zoë ä+b"), HarnessLaunch.parse("kompound://demo/x?control=Name::Zo%C3%AB%20%C3%A4%2Bb")!!.controls)
        assertEquals(mapOf("A" to "1::2"), HarnessLaunch.parse("kompound://demo/x?control=A::1::2")!!.controls, "only the first :: splits")
        assertEquals("100%", HarnessLaunch.percentDecode("100%"), "a lone percent sign stays")
        assertTrue(HarnessLaunch.parse("kompound://demo/x?control=novalue")!!.controls.isEmpty())
        assertEquals(mapOf("a=b" to "c"), HarnessLaunch.parse("kompound://demo/x?control=a%3Db::c")!!.controls, "a name may contain =")
    }

    @Test
    fun otherLinksAreNotHarnessLinks() {
        assertNull(HarnessLaunch.parse("https://example.com/demo/x"))
        assertNull(HarnessLaunch.parse("kompound://other/x"))
        assertNull(HarnessLaunch.parse("kompound://demo/"))
        assertNull(HarnessLaunch.parse("kompound://demo/?theme=dark"))
    }

    @Test
    fun anIdOrAQualifiedIdFindsTheEntry() {
        val e = slider
        assertEquals(e, HarnessLaunch(e.meta.id).resolve(KompoundAllDemos.entries))
        assertEquals(e, HarnessLaunch(e.qualifiedId).resolve(KompoundAllDemos.entries))
        assertNull(HarnessLaunch("nope").resolve(KompoundAllDemos.entries))
    }

    @Test
    fun theBareScreenShowsTheDemoItsControlsPresetsAndTheReadyMarker() = runComposeUiTest {
        mainClock.autoAdvance = false
        val launch = HarnessLaunch(slider.qualifiedId, controls = mapOf("Steps (0 = continuous)" to "4"))
        setContent { MaterialTheme { KompoundCatalog(launch = launch) } }
        onAllNodesWithTag("harness:ready").assertCountEquals0()
        repeat(4) { mainClock.advanceTimeByFrame() }
        val ready = onNodeWithTag("harness:ready").fetchSemanticsNode()
        assertEquals(listOf(slider.qualifiedId), ready.config.getOrNull(SemanticsProperties.ContentDescription))
        onNodeWithTag("harness:preview").assertIsDisplayed()
        onNodeWithTag("control:Steps (0 = continuous):value").assertIsDisplayed()
        onNodeWithText("4").assertExists()
        onNodeWithTag("control:Enabled").assertExists()
    }

    @Test
    fun anUnknownDemoShowsAnError() = runComposeUiTest {
        setContent { KompoundCatalog(launch = HarnessLaunch("does.not.exist")) }
        onNodeWithTag("harness:error").assertIsDisplayed()
    }

    @Test
    fun everyDemoReachesTheReadyMarkerThroughTheHarness() {
        val failures = ArrayList<String>()
        for (entry in KompoundAllDemos.entries) {
            try {
                runComposeUiTest {
                    mainClock.autoAdvance = false
                    setContent { MaterialTheme { KompoundCatalog(launch = HarnessLaunch(entry.qualifiedId)) } }
                    repeat(4) { mainClock.advanceTimeByFrame() }
                    assertNotNull(onNodeWithTag("harness:ready").fetchSemanticsNode())
                }
            } catch (e: Throwable) {
                failures += "${entry.qualifiedId}: ${e.message?.take(120)}"
            }
        }
        assertTrue(failures.isEmpty(), "Demos that do not become ready in the harness:\n" + failures.joinToString("\n"))
    }

    @Test
    fun theEnvironmentFollowsTheLink() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { MaterialTheme { KompoundCatalog(launch = HarnessLaunch(slider.qualifiedId, dark = true, rtl = true, fontScale = 2f, lang = "de", density = "compact")) } }
        repeat(4) { mainClock.advanceTimeByFrame() }
        onNodeWithTag("harness:ready").assertExists()
    }

    @Test
    fun aSecondLinkInTheSameSessionReplacesTheDemoAndItsControls() = runComposeUiTest {
        mainClock.autoAdvance = false
        var launch by androidx.compose.runtime.mutableStateOf(HarnessLaunch(slider.qualifiedId, controls = mapOf("Steps (0 = continuous)" to "4")))
        setContent { MaterialTheme { KompoundCatalog(launch = launch) } }
        repeat(4) { mainClock.advanceTimeByFrame() }
        onNodeWithTag("control:Steps (0 = continuous):value").assertIsDisplayed()
        onNodeWithText("4").assertExists()
        // the same demo again with other presets (what a flow does with openLink while the app runs)
        launch = HarnessLaunch(slider.qualifiedId, controls = mapOf("Steps (0 = continuous)" to "8", "Enabled" to "false"))
        repeat(4) { mainClock.advanceTimeByFrame() }
        onNodeWithTag("control:Steps (0 = continuous):value").assertIsDisplayed()
        onNodeWithText("8").assertExists()
        onNodeWithTag("control:Enabled").assertExists()
        // and another demo
        val other = KompoundAllDemos.entries.first { it.qualifiedId != slider.qualifiedId }
        launch = HarnessLaunch(other.qualifiedId)
        repeat(4) { mainClock.advanceTimeByFrame() }
        assertEquals(listOf(other.qualifiedId), onNodeWithTag("harness:ready").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun theSameLinkReceivedAgainStartsTheDemoAfresh() = runComposeUiTest {
        mainClock.autoAdvance = false
        val fields = KompoundAllDemos.entries.first { it.meta.id == "textfield.basic" }
        var launch by androidx.compose.runtime.mutableStateOf(HarnessLaunch(fields.qualifiedId, nonce = 1))
        setContent { MaterialTheme { KompoundCatalog(launch = launch) } }
        repeat(4) { mainClock.advanceTimeByFrame() }
        // the preview's field comes first (the controls card has a "Placeholder" text control with the same words)
        onAllNodesWithText("name@example.com").onFirst().performTextInput("typed")
        repeat(2) { mainClock.advanceTimeByFrame() }
        onNodeWithText("typed").assertExists()
        launch = HarnessLaunch(fields.qualifiedId, nonce = 2)   // the identical link, received once more
        repeat(4) { mainClock.advanceTimeByFrame() }
        assertEquals(0, onAllNodesWithText("typed").fetchSemanticsNodes().size)
    }
}

private fun androidx.compose.ui.test.SemanticsNodeInteractionCollection.assertCountEquals0() {
    assertEquals(0, fetchSemanticsNodes().size)
}

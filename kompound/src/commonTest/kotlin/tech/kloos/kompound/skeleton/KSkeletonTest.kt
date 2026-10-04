package tech.kloos.kompound.skeleton

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KSkeletonTest {
    private val scheme = lightColorScheme()

    private fun androidx.compose.ui.test.ComposeUiTest.pixels(tag: String) =
        onNodeWithTag(tag).captureToImage().toPixelMap().let { m -> (0 until m.width step 4).map { x -> m[x, m.height / 2] } }

    @Test
    fun theHighlightMovesWhileAnimatedAndStaysStillWhenNot() = runComposeUiTest {
        mainClock.autoAdvance = false
        var animated by mutableStateOf(true)
        setContent { MaterialTheme(scheme) { KSkeleton(Modifier.size(200.dp, 40.dp).testTag("sk"), animated = animated) } }
        mainClock.advanceTimeByFrame(); mainClock.advanceTimeBy(100)
        val a = pixels("sk")
        mainClock.advanceTimeBy(500)
        val b = pixels("sk")
        assertNotEquals(a, b, "the highlight band moved between frames")
        animated = false
        mainClock.advanceTimeByFrame(); mainClock.advanceTimeBy(100)
        val still1 = pixels("sk")
        mainClock.advanceTimeBy(500)
        val still2 = pixels("sk")
        assertEquals(still1, still2, "no movement when animated = false")
    }

    @Test
    fun theBlockHasTheSkeletonColourAndIsSilentForScreenReadersUnlessDescribed() = runComposeUiTest {
        setContent {
            MaterialTheme(scheme) {
                Column {
                    KSkeleton(Modifier.size(100.dp, 20.dp).testTag("silent"), animated = false)
                    KSkeleton(Modifier.size(100.dp, 20.dp).testTag("described"), animated = false, contentDescription = "Avatar loading")
                }
            }
        }
        waitForIdle()
        val pixel = onNodeWithTag("silent").captureToImage().toPixelMap()[50, 10]
        val expected = scheme.surfaceContainerHighest
        assertTrue(kotlin.math.abs(pixel.red - expected.red) < 0.03f && kotlin.math.abs(pixel.blue - expected.blue) < 0.03f, "block colour $pixel vs $expected")
        assertEquals(null, onNodeWithTag("silent", useUnmergedTree = true).fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
        onNodeWithContentDescription("Avatar loading").assertExists()
    }

    @Test
    fun aGroupAnnouncesLoadingOnceForAllItsBlocks() = runComposeUiTest {
        setContent {
            MaterialTheme(scheme) {
                KSkeletonGroup(Modifier.testTag("group")) {
                    KSkeletonCircle(40.dp, animated = false)
                    KSkeletonText(Modifier.fillMaxWidth(), lines = 3, animated = false)
                }
            }
        }
        waitForIdle()
        onNodeWithContentDescription("Loading").assertExists()
        assertEquals(listOf("Loading"), onNodeWithTag("group").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun skeletonTextMakesTheRequestedLinesWithAShorterLastOne() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KSkeletonText(Modifier.size(200.dp, 100.dp).testTag("text"), lines = 3, lastLineFraction = 0.5f, lineHeight = 10.dp, spacing = 6.dp, animated = false) } }
        waitForIdle()
        val m = onNodeWithTag("text").captureToImage().toPixelMap()
        val bg = m[199, 99]
        fun filled(y: Int, x: Int) = m[x, y] != bg
        assertTrue(filled(5, 190), "first line spans the width")
        assertTrue(filled(21, 190), "second line spans the width")
        assertTrue(filled(37, 50) && !filled(37, 190), "last line is half as wide")
    }

    @Test
    fun kShimmerCanBeUsedOnAnyContent() = runComposeUiTest {
        mainClock.autoAdvance = false
        setContent { MaterialTheme(scheme) { androidx.compose.foundation.layout.Box(Modifier.size(100.dp, 20.dp).testTag("any").kShimmer(true)) } }
        mainClock.advanceTimeByFrame(); mainClock.advanceTimeBy(50)
        onNodeWithTag("any").assertExists()
    }
}

package tech.kloos.kompound.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.foundation.layout.Box
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.near
import tech.kloos.kompound.theme.KompoundColors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KAvatarTest {
    private val s = ButtonTestScheme

    @Test
    fun initialsUseFirstAndLastWord() {
        assertEquals("AL", initials("Ada Lovelace"))
        assertEquals("G", initials("  grace  "))
        assertEquals("JD", initials("jean claude van damme"))
        assertEquals("?", initials("   "))
        assertEquals("?", initials(""))
    }

    @Test
    fun showsInitialsWhenThereIsNoImage() = runComposeUiTest {
        setContent { MaterialTheme(s) { KAvatar("Ada Lovelace") } }
        onNodeWithText("AL", useUnmergedTree = true).assertExists()
    }

    @Test
    fun sameNameAlwaysGetsTheSameColourAndNamesSpreadAcrossColours() = runComposeUiTest {
        val names = listOf("Ada Lovelace", "Ada Lovelace", "Grace Hopper", "Alan Turing", "Linus Torvalds", "Margaret Hamilton", "Dennis Ritchie")
        setContent {
            MaterialTheme(s) {
                Column { names.forEachIndexed { i, n -> KAvatar(n, Modifier.testTag("a$i")) } }
            }
        }
        val colours = names.indices.map { i -> onNodeWithTag("a$i").captureToImage().toPixelMap().let { it[it.width / 2, 1] } }
        assertTrue(colours[0].near(colours[1], 0.001f), "same name, same colour")
        assertTrue(colours.map { it.toString() }.toSet().size >= 2, "different names should not all share one colour")
    }

    @Test
    fun sizesAreSmall32Medium40Large56() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Row {
                    KAvatar("A", Modifier.testTag("s"), size = KAvatarSize.Small)
                    KAvatar("A", Modifier.testTag("m"))
                    KAvatar("A", Modifier.testTag("l"), size = KAvatarSize.Large)
                }
            }
        }
        assertEquals(32, onNodeWithTag("s").fetchSemanticsNode().size.width)
        assertEquals(40, onNodeWithTag("m").fetchSemanticsNode().size.width)
        assertEquals(56, onNodeWithTag("l").fetchSemanticsNode().size.width)
    }

    @Test
    fun imageSlotReplacesInitials() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                KAvatar("Ada Lovelace", Modifier.testTag("a"), image = { Box(Modifier.fillMaxSize().background(Color.Magenta)) })
            }
        }
        onNodeWithText("AL", useUnmergedTree = true).assertDoesNotExist()
        val img = onNodeWithTag("a").captureToImage()
        assertTrue(img.toPixelMap()[img.width / 2, img.height / 2].near(Color.Magenta))
    }

    @Test
    fun statusDotShowsPresenceColourAndIsDescribed() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KAvatar("Ada", Modifier.testTag("online"), status = KAvatarStatus.Online)
                    KAvatar("Ada", Modifier.testTag("none"))
                }
            }
        }
        val img = onNodeWithTag("online").captureToImage()
        // Medium avatar is 40px, dot is 12px in the bottom-end corner: its centre is at (34, 34).
        assertTrue(img.toPixelMap()[34, 34].near(KompoundColors.Light.success), "online dot colour")
        val d = onNodeWithTag("online").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("Ada, online"), d)
        assertEquals(listOf("Ada"), onNodeWithTag("none").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
    }
}

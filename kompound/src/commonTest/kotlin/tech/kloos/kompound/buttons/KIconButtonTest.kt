package tech.kloos.kompound.buttons

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.graphics.toPixelMap
import tech.kloos.kompound.SquareIcon
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.icon.KIcon
import tech.kloos.kompound.near
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KIconButtonTest {
    private val s = ButtonTestScheme

    @Test
    fun exposesContentDescriptionAndButtonRole() = runComposeUiTest {
        setContent { MaterialTheme(s) { KIconButton(onClick = {}, contentDescription = "Add", Modifier.testTag("b")) { KIcon(SquareIcon, null) } } }
        val config = onNodeWithTag("b").fetchSemanticsNode().config
        assertEquals(listOf("Add"), config.getOrNull(SemanticsProperties.ContentDescription))
        assertEquals(Role.Button, config.getOrNull(SemanticsProperties.Role))
    }

    @Test
    fun clickInvokesCallbackAndDisabledDoesNot() = runComposeUiTest {
        var clicks = 0
        setContent {
            MaterialTheme(s) {
                Column {
                    KIconButton(onClick = { clicks++ }, "On", Modifier.testTag("on")) { KIcon(SquareIcon, null) }
                    KIconButton(onClick = { clicks += 100 }, "Off", Modifier.testTag("off"), enabled = false) { KIcon(SquareIcon, null) }
                }
            }
        }
        onNodeWithTag("on").performClick()
        onNodeWithTag("off").assertIsNotEnabled().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun touchTargetIs48dp() = runComposeUiTest {
        setContent { MaterialTheme(s) { KIconButton(onClick = {}, "Add", Modifier.testTag("b")) { KIcon(SquareIcon, null) } } }
        val size = onNodeWithTag("b").fetchSemanticsNode().size
        assertEquals(48, size.width)
        assertEquals(48, size.height)
    }

    @Test
    fun filledVariantHasContainerAndIconGetsOnPrimary() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                Column {
                    KIconButton(onClick = {}, "Filled", Modifier.testTag("filled"), variant = KButtonVariant.Filled) { KIcon(SquareIcon, null) }
                    KIconButton(onClick = {}, "Standard", Modifier.testTag("standard")) { KIcon(SquareIcon, null) }
                }
            }
        }
        val filled = onNodeWithTag("filled").captureToImage()
        assertTrue(filled.toPixelMap()[filled.width / 2, 6].near(s.primary), "container")
        assertTrue(filled.containsColor(s.onPrimary), "icon colour")
        val standard = onNodeWithTag("standard").captureToImage()
        assertEquals(0f, standard.toPixelMap()[standard.width / 2, 6].alpha, 0.01f)
        assertTrue(standard.containsColor(s.primary), "standard icon uses primary")
    }
}

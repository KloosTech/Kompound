package tech.kloos.kompound.icon

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.style.Style
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.style.size
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import tech.kloos.kompound.SquareIcon
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.theme.LocalKContentColor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KIconTest {
    private val scheme = lightColorScheme(onSurface = Color(0xFF00FF00))

    @Test
    fun exposesContentDescription() = runComposeUiTest {
        setContent { KIcon(SquareIcon, "Close", Modifier.testTag("i")) }
        val d = onNodeWithTag("i").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription)
        assertEquals(listOf("Close"), d)
    }

    @Test
    fun decorativeIconHasNoContentDescription() = runComposeUiTest {
        setContent { KIcon(SquareIcon, null, Modifier.testTag("i")) }
        assertNull(onNodeWithTag("i").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ContentDescription))
    }

    @Test
    fun defaultSizeIs24dp() = runComposeUiTest {
        setContent { KIcon(SquareIcon, null, Modifier.testTag("i")) }
        val size = onNodeWithTag("i").fetchSemanticsNode().size
        assertEquals(24, size.width)
        assertEquals(24, size.height)
    }

    @Test
    fun styleOverridesSize() = runComposeUiTest {
        setContent { KIcon(SquareIcon, null, Modifier.testTag("i"), style = Style { size(40.dp) }) }
        assertEquals(40, onNodeWithTag("i").fetchSemanticsNode().size.width)
    }

    @Test
    fun explicitTintWins() = runComposeUiTest {
        val red = Color(0xFFFF0000)
        setContent {
            MaterialTheme(scheme) {
                CompositionLocalProvider(LocalKContentColor provides Color.Blue) { KIcon(SquareIcon, null, Modifier.testTag("i"), tint = red) }
            }
        }
        assertTrue(onNodeWithTag("i").captureToImage().containsColor(red))
    }

    @Test
    fun fallsBackToProvidedContentColourThenOnSurface() = runComposeUiTest {
        setContent {
            MaterialTheme(scheme) {
                Column {
                    KIcon(SquareIcon, null, Modifier.testTag("plain"))
                    CompositionLocalProvider(LocalKContentColor provides Color.Blue) { KIcon(SquareIcon, null, Modifier.testTag("provided")) }
                }
            }
        }
        assertTrue(onNodeWithTag("plain").captureToImage().containsColor(scheme.onSurface), "expected onSurface")
        assertTrue(onNodeWithTag("provided").captureToImage().containsColor(Color.Blue), "expected provided colour")
    }
}

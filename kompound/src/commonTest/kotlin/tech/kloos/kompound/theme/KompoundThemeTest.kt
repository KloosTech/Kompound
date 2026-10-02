package tech.kloos.kompound.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp
import kotlin.test.assertFalse
import tech.kloos.kompound.containsColor
import tech.kloos.kompound.contrast
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KompoundThemeTest {
    private fun <T> read(content: @Composable (capture: (T) -> Unit) -> Unit): T? {
        var out: T? = null
        runComposeUiTest { setContent { content { out = it } } }
        return out
    }

    @Test
    fun tokensDefaultToLightWithoutTheme() {
        val t = read<KompoundTokens> { capture -> capture(KompoundTheme.tokens) }
        assertSame(KompoundTokens.Light, t)
    }

    @Test
    fun lightSchemeSelectsLightTokensAndDarkSelectsDark() {
        val light = read<KompoundTokens> { c -> KompoundTheme(colorScheme = lightColorScheme()) { c(KompoundTheme.tokens) } }
        val dark = read<KompoundTokens> { c -> KompoundTheme(colorScheme = darkColorScheme()) { c(KompoundTheme.tokens) } }
        assertSame(KompoundTokens.Light, light)
        assertSame(KompoundTokens.Dark, dark)
    }

    @Test
    fun customTokensAreProvided() {
        val custom = KompoundTokens.Light.copy(colors = KompoundColors.Light.copy(success = Color.Magenta))
        val t = read<KompoundTokens> { c -> KompoundTheme(tokens = custom) { c(KompoundTheme.tokens) } }
        assertEquals(Color.Magenta, t!!.colors.success)
    }

    @Test
    fun m3ThemeIsProvidedToContent() {
        val scheme = lightColorScheme(primary = Color(0xFF123456))
        val primary = read<Color> { c -> KompoundTheme(colorScheme = scheme) { c(MaterialTheme.colorScheme.primary) } }
        assertEquals(Color(0xFF123456), primary)
    }

    @Test
    fun extensionColourPairsMeetWcagAaForText() {
        for ((name, colors) in listOf("light" to KompoundColors.Light, "dark" to KompoundColors.Dark)) {
            val pairs = listOf(
                "success" to (colors.success to colors.onSuccess),
                "successContainer" to (colors.successContainer to colors.onSuccessContainer),
                "warning" to (colors.warning to colors.onWarning),
                "warningContainer" to (colors.warningContainer to colors.onWarningContainer),
                "info" to (colors.info to colors.onInfo),
                "infoContainer" to (colors.infoContainer to colors.onInfoContainer),
            )
            for ((pairName, pair) in pairs) {
                val ratio = contrast(pair.first, pair.second)
                assertTrue(ratio >= 4.5f, "$name $pairName contrast $ratio < 4.5")
            }
        }
    }
}

@OptIn(ExperimentalTestApi::class)
class KompoundThemeRootTextTest {
    private val dark = darkColorScheme(onBackground = Color(0xFFFFFF00), background = Color(0xFF101010))

    @Test
    fun plainTextOnThePageUsesTheThemesOnBackgroundColour() {
        runComposeUiTest {
            setContent {
                KompoundTheme(colorScheme = dark) {
                    androidx.compose.foundation.layout.Box(Modifier.testTag("page")) { tech.kloos.kompound.text.KText("MMMM") }
                }
            }
            val img = onNodeWithTag("page").captureToImage()
            assertTrue(img.containsColor(Color(0xFFFFFF00)), "plain KText should be onBackground, not black")
        }
    }

    @Test
    fun textInsideAStyledComponentStillUsesTheComponentsOwnColour() {
        runComposeUiTest {
            setContent {
                KompoundTheme(colorScheme = lightColorScheme(onBackground = Color(0xFFFFFF00), primary = Color(0xFF0000FF), onPrimary = Color(0xFF00FF00))) {
                    tech.kloos.kompound.buttons.KButton(onClick = {}, Modifier.testTag("btn")) { tech.kloos.kompound.text.KText("MMMM") }
                }
            }
            val img = onNodeWithTag("btn").captureToImage()
            assertTrue(img.containsColor(Color(0xFF00FF00)), "button label keeps onPrimary")
            assertFalse(img.containsColor(Color(0xFFFFFF00)), "the root colour must not leak into the button label")
        }
    }

    @Test
    fun rootWrapperDoesNotChangeTheSizeOfItsContent() {
        runComposeUiTest {
            setContent {
                KompoundTheme(colorScheme = dark) {
                    androidx.compose.foundation.layout.Box(Modifier.testTag("child").then(Modifier.width(123.dp)).height(45.dp))
                }
            }
            val size = onNodeWithTag("child").fetchSemanticsNode().size
            assertEquals(123, size.width)
            assertEquals(45, size.height)
        }
    }
}


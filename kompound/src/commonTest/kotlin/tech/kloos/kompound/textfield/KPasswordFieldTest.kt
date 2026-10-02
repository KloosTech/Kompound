package tech.kloos.kompound.textfield

import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.theme.KompoundTheme
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KPasswordFieldTest {
    private val scheme = lightColorScheme(error = Color(0xFFFF0000))
    private val isPassword = SemanticsMatcher.keyIsDefined(SemanticsProperties.Password)

    @Test
    fun isMaskedUntilTheEyeButtonIsPressed() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KPasswordField("secret", {}, Modifier.testTag("p"), label = "Password") } }
        fun pixels(): IntArray = onNodeWithTag("p").captureToImage().toPixelMap().let { map -> IntArray(map.width * map.height) { map.buffer[it] } }
        assertEquals(1, onAllNodes(isPassword).fetchSemanticsNodes().size)
        val masked = pixels()
        onNodeWithContentDescription("Show password").performClick()
        waitForIdle()
        val shown = pixels()
        onNodeWithContentDescription("Hide password").assertExists()
        assertTrue(!masked.contentEquals(shown), "showing the password did not change what is drawn")
        onNodeWithContentDescription("Hide password").performClick()
        waitForIdle()
        assertTrue(!shown.contentEquals(pixels()), "hiding did not mask the text again")
    }

    @Test
    fun typingCallsBackWithTheNewPassword() = runComposeUiTest {
        var password by mutableStateOf("")
        setContent { MaterialTheme(scheme) { KPasswordField(password, { password = it }) } }
        onNode(hasSetTextAction()).performTextInput("hunter2")
        waitForIdle()
        assertEquals("hunter2", password)
    }

    @Test
    fun customEyeDescriptionsAreUsed() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KPasswordField("x", {}, showDescription = "Passwort anzeigen", hideDescription = "Passwort verbergen") } }
        onNodeWithContentDescription("Passwort anzeigen").performClick()
        waitForIdle()
        onNodeWithContentDescription("Passwort verbergen").assertExists()
    }

    @Test
    fun strengthMeterAndSupportingTextAppearUnderTheField() = runComposeUiTest {
        setContent {
            MaterialTheme(scheme) {
                KPasswordField("abc", {}, supportingText = "Use 12 or more characters", strength = { KPasswordStrength(1, 4, "Weak") })
            }
        }
        onNodeWithContentDescription("Weak").assertExists()
        onNodeWithText("Use 12 or more characters").assertExists()
    }

    @Test
    fun noMeterWithoutAStrengthFunction() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KPasswordField("abc", {}) } }
        assertEquals(0, onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)).fetchSemanticsNodes().size)
    }

    @Test
    fun strengthIsRecomputedWhenThePasswordChanges() = runComposeUiTest {
        var password by mutableStateOf("a")
        setContent { MaterialTheme(scheme) { KPasswordField(password, { password = it }, strength = { KPasswordStrength(it.length.coerceAtMost(4)) }) } }
        fun level() = onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo))[0].fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!.current
        assertEquals(1f, level())
        password = "abcd"
        waitForIdle()
        assertEquals(4f, level())
    }
}

@OptIn(ExperimentalTestApi::class)
class KStrengthMeterTest {
    private val scheme = lightColorScheme(error = Color(0xFFFF0000), outlineVariant = Color(0xFFCCCCCC))
    private fun Color.near(o: Color, tol: Float = 0.08f) = abs(red - o.red) < tol && abs(green - o.green) < tol && abs(blue - o.blue) < tol
    private fun ImageBitmap.at(x: Int) = toPixelMap()[x, height / 2]

    @Test
    fun litSegmentsUseTheLevelColourAndTheRestTheTrack() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KompoundTheme(scheme) { KStrengthMeter(1, 4, Modifier.testTag("m").width(200.dp)) } } }
        val image = onNodeWithTag("m").captureToImage()
        assertTrue(image.at(10).near(scheme.error), "first bar is not the error colour")
        assertTrue(image.at(image.width - 10).near(scheme.outlineVariant), "last bar is not the track colour")
    }

    @Test
    fun fullLevelIsSuccessColouredAndExposedAsProgress() = runComposeUiTest {
        var success = Color.Unspecified
        setContent {
            MaterialTheme(scheme) {
                KompoundTheme(scheme) {
                    success = KompoundTheme.tokens.colors.success
                    KStrengthMeter(4, 4, Modifier.testTag("m").width(200.dp), description = "Strong")
                }
            }
        }
        val image = onNodeWithTag("m").captureToImage()
        assertTrue(image.at(10).near(success) && image.at(image.width - 10).near(success))
        val info = onNodeWithTag("m").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)
        assertNotNull(info)
        assertEquals(4f, info.current)
    }

    @Test
    fun levelsOutsideTheRangeAreClamped() = runComposeUiTest {
        setContent { MaterialTheme(scheme) { KompoundTheme(scheme) { KStrengthMeter(99, 4, Modifier.testTag("a")); KStrengthMeter(-3, 4, Modifier.testTag("b")) } } }
        assertEquals(4f, onNodeWithTag("a").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!.current)
        assertEquals(0f, onNodeWithTag("b").fetchSemanticsNode().config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)!!.current)
    }
}

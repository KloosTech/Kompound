package tech.kloos.kompound.theme

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.chip.KChip
import tech.kloos.kompound.list.KListItem
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class KDensityMathTest {
    @Test
    fun heightsAndSpacesScaleToWholeDpAndComfortableChangesNothing() {
        assertEquals(40.dp, KDensity.Comfortable.height(40.dp))
        assertEquals(32.dp, KDensity.Compact.height(40.dp))
        assertEquals(46.dp, KDensity.Spacious.height(40.dp))
        assertEquals(8.dp, KDensity.Comfortable.space(8.dp))
        assertEquals(6.dp, KDensity.Compact.space(8.dp))
        assertEquals(10.dp, KDensity.Spacious.space(8.dp))
        assertEquals(0.dp, KDensity.Compact.space(0.dp))
    }
}

@OptIn(ExperimentalTestApi::class)
class KDensityUiTest {
    private fun heights(density: KDensity?): Map<String, Float> {
        val result = LinkedHashMap<String, Float>()
        runComposeUiTest {
            setContent {
                KompoundTheme(lightColorScheme(), density = density) {
                    Column {
                        KButton({}, Modifier.testTag("button")) { KText("Go") }
                        KChip("Chip", {}, Modifier.testTag("chip"))
                        KTextField("", {}, Modifier.testTag("field"), label = "Name")
                        KListItem("Headline", Modifier.testTag("item"))
                    }
                }
            }
            for (tag in listOf("button", "chip", "field", "item")) result[tag] = onNodeWithTag(tag).fetchSemanticsNode().size.height.toFloat()
        }
        return result
    }

    @Test
    fun compactComfortableAndSpaciousControlsGetProgressivelyTaller() {
        val compact = heights(KDensity.Compact)
        val comfortable = heights(KDensity.Comfortable)
        val spacious = heights(KDensity.Spacious)
        val none = heights(null)
        assertEquals(comfortable, none, "no density given means comfortable")
        for (tag in comfortable.keys) {
            assertTrue(compact.getValue(tag) < comfortable.getValue(tag), "$tag: compact ${compact[tag]} < comfortable ${comfortable[tag]}")
            assertTrue(spacious.getValue(tag) > comfortable.getValue(tag), "$tag: spacious ${spacious[tag]} > comfortable ${comfortable[tag]}")
        }
    }
}

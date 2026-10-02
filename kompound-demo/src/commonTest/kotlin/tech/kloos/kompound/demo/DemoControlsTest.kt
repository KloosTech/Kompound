package tech.kloos.kompound.demo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class DemoControlsTest {
    private enum class Size { Small, Large }

    @Test
    fun controlsAreRegisteredInDeclarationOrderWithInitialValues() = runComposeUiTest {
        val scope = DemoControls()
        var seen = emptyList<Any>()
        setContent {
            seen = listOf(
                scope.textControl("Label", "Hi"),
                scope.boolControl("Enabled", true),
                scope.choiceControl("Size", Size.entries, Size.Large),
                scope.floatControl("Width", 0f..100f, 250f),
            )
        }
        waitForIdle()
        assertEquals(listOf("Label", "Enabled", "Size", "Width"), scope.controls.map { it.name })
        assertEquals(listOf("Hi", true, Size.Large, 100f), seen)   // slider initial clamped into range
    }

    @Test
    fun editingAControlRecomposesTheDemoWithTheNewValue() = runComposeUiTest {
        val scope = DemoControls()
        var label = ""
        setContent { label = scope.textControl("Label", "a") }
        waitForIdle()
        (scope.controls.single() as TextControl).value = "b"
        waitForIdle()
        assertEquals("b", label)
    }

    @Test
    fun choiceUsesLabelAndSelectionIndex() = runComposeUiTest {
        val scope = DemoControls()
        var chosen: Size? = null
        setContent { chosen = scope.choiceControl("Size", Size.entries, Size.Small, label = { it.name.uppercase() }) }
        waitForIdle()
        val control = scope.controls.single() as ChoiceControl
        assertEquals(listOf("SMALL", "LARGE"), control.options)
        control.selectedIndex = 1
        waitForIdle()
        assertEquals(Size.Large, chosen)
    }

    @Test
    fun controlsAreRemovedWhenTheDemoLeavesComposition() = runComposeUiTest {
        val scope = DemoControls()
        var show by mutableStateOf(true)
        setContent { if (show) scope.boolControl("On") }
        waitForIdle()
        assertEquals(1, scope.controls.size)
        show = false
        waitForIdle()
        assertTrue(scope.controls.isEmpty())
    }

    @Test
    fun valueSurvivesRecompositionOfTheSameDemo() = runComposeUiTest {
        val scope = DemoControls()
        var tick by mutableStateOf(0)
        var value = ""
        setContent { tick.let { value = scope.textControl("T", "initial") } }
        waitForIdle()
        (scope.controls.single() as TextControl).value = "edited"
        waitForIdle()
        tick++
        waitForIdle()
        assertEquals("edited", value)
        assertEquals(1, scope.controls.size)
    }
}

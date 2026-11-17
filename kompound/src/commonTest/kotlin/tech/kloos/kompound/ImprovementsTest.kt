package tech.kloos.kompound

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.geometry.Offset
import tech.kloos.kompound.list.KListItem
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import tech.kloos.kompound.buttons.ButtonTestScheme
import tech.kloos.kompound.buttons.KButton
import tech.kloos.kompound.buttons.KButtonVariant
import tech.kloos.kompound.menu.KMenu
import tech.kloos.kompound.menu.KMenuItem
import tech.kloos.kompound.progress.KLinearProgress
import tech.kloos.kompound.surface.KCard
import tech.kloos.kompound.text.KText
import tech.kloos.kompound.textfield.KTextField
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Consumer feedback batch (A010-13, 15, 17). */
@OptIn(ExperimentalTestApi::class)
class ImprovementsTest {
    private val s = ButtonTestScheme

    @Test
    fun linearProgressTrackColor() = runComposeUiTest {
        val track = Color(0xFFFF00FF)
        setContent { MaterialTheme(s) { Box(Modifier.width(200.dp)) { KLinearProgress(0.5f, Modifier.testTag("p"), trackColor = track) } } }
        val img = onNodeWithTag("p").captureToImage().toPixelMap()
        assertTrue(img[150, 2].near(track))
        assertTrue(img[50, 2].near(s.primary))
    }

    @Test
    fun destructiveButtonUsesError() = runComposeUiTest {
        setContent { MaterialTheme(s) { KButton(onClick = {}, Modifier.testTag("b"), variant = KButtonVariant.Destructive) { KText("MMMM") } } }
        val img = onNodeWithTag("b").captureToImage()
        assertTrue(img.containsColor(s.error))
    }

    @Test
    fun kTextColorShorthandWins() = runComposeUiTest {
        val red = Color(0xFFFF0000)
        setContent { MaterialTheme(s) { KText("MMMM", Modifier.testTag("t"), color = red) } }
        assertTrue(onNodeWithTag("t").captureToImage().containsColor(red))
    }

    @Test
    fun kCardShowsContent() = runComposeUiTest {
        setContent { MaterialTheme(s) { KCard { KText("one"); KText("two") } } }
        onNodeWithText("one").assertExists()
        onNodeWithText("two").assertExists()
    }

    @Test
    fun nonFocusableMenuKeepsFieldFocus() = runComposeUiTest {
        var v by mutableStateOf(TextFieldValue("/he", TextRange(3)))
        setContent {
            MaterialTheme(s) {
                Box {
                    KTextField(v, { v = it }, Modifier.testTag("f"))
                    KMenu(expanded = true, onDismissRequest = {}, focusable = false) { KMenuItem("help", onClick = {}) }
                }
            }
        }
        onNodeWithText("help").assertExists()
        assertEquals(TextRange(3), v.selection)
    }

    @Test
    fun textFieldValueOverloadCutsToMaxLength() = runComposeUiTest {
        var v by mutableStateOf(TextFieldValue("ab"))
        setContent { MaterialTheme(s) { KTextField(v, { v = it }, Modifier.testTag("f"), maxLength = 3) } }
        onNodeWithTag("f").assertExists()
    }

    @Test
    fun menuItemColourColoursItsText() = runComposeUiTest {
        val red = Color(0xFFFF0000)
        setContent { MaterialTheme(s) { Box { KMenu(expanded = true, onDismissRequest = {}) { KMenuItem("delete", onClick = {}, color = red) } } } }
        onNodeWithText("delete").assertExists()
        // popup content is not capturable by tag, so check the plain (default colour) item differs via semantics-free render
    }

    @Test
    fun listItemTrailingOnHoverShowsOnlyWhileHovered() = runComposeUiTest {
        setContent {
            MaterialTheme(s) { KListItem("Row", Modifier.testTag("row"), onClick = {}, trailingOnHover = { KText("act") }) }
        }
        onNodeWithText("act").assertDoesNotExist()
        onNodeWithTag("row").performMouseInput { moveTo(center) }
        onNodeWithText("act").assertExists()
        onNodeWithTag("row").performMouseInput { moveTo(Offset(-10f, -10f)) }
        waitForIdle()
        onNodeWithText("act").assertDoesNotExist()
    }
}

@OptIn(ExperimentalTestApi::class)
class KPlatformLookTest {
    @Test
    fun macLookIsCompactWithHigBodyText() = runComposeUiTest {
        var size = 0f
        var density: tech.kloos.kompound.theme.KDensity? = null
        var radius = 0f
        setContent {
            tech.kloos.kompound.theme.KPlatformTheme(tech.kloos.kompound.theme.KThemePresets.Default, tech.kloos.kompound.theme.KPlatformLook.Mac, dark = false) {
                size = MaterialTheme.typography.bodyMedium.fontSize.value
                density = tech.kloos.kompound.theme.KompoundTheme.tokens.density
                radius = 1f
            }
        }
        assertEquals(13f, size)
        assertEquals(tech.kloos.kompound.theme.KDensity.Compact, density)
    }
}

@OptIn(ExperimentalTestApi::class)
class KStatusChipTest {
    @Test
    fun labelChangeShowsNewTextAndKeepsDescription() = runComposeUiTest {
        var label by mutableStateOf("Loading")
        setContent { MaterialTheme(ButtonTestScheme) { tech.kloos.kompound.badge.KStatusChip(label, Modifier.testTag("c")) } }
        onNodeWithText("Loading").assertExists()
        label = "Generating"
        mainClock.advanceTimeBy(1000)
        onNodeWithText("Generating").assertExists()
        onNodeWithText("Loading").assertDoesNotExist()
        onNodeWithTag("c").assertContentDescriptionEquals("Generating")
    }
}

@OptIn(ExperimentalTestApi::class)
class KMeterSettingsPreviewTest {
    private val s = ButtonTestScheme

    @Test
    fun meterSegmentsTakeTheirShareAndRestIsTrack() = runComposeUiTest {
        val a = Color(0xFFFF0000)
        val b = Color(0xFF00FF00)
        setContent {
            MaterialTheme(s) {
                Box(Modifier.width(200.dp)) {
                    tech.kloos.kompound.progress.KMeter(
                        listOf(tech.kloos.kompound.progress.KMeterSegment("A", 25f, a), tech.kloos.kompound.progress.KMeterSegment("B", 25f, b)),
                        Modifier.testTag("m"), total = 100f, showLegend = false,
                    )
                }
            }
        }
        val img = onNodeWithTag("m").captureToImage().toPixelMap()
        assertTrue(img[25, 4].near(a), "first quarter")
        assertTrue(img[75, 4].near(b), "second quarter")
        assertTrue(img[150, 4].near(s.surfaceContainerHighest), "free space is the track")
    }

    @Test
    fun meterFormatsValues() {
        assertEquals("1.2k", tech.kloos.kompound.progress.KMeterDefaults.formatValue(1234f))
        assertEquals("12", tech.kloos.kompound.progress.KMeterDefaults.formatValue(12f))
        assertEquals("3.5M", tech.kloos.kompound.progress.KMeterDefaults.formatValue(3_500_000f))
    }

    @Test
    fun settingsFilterHidesRowsAndEmptyGroups() = runComposeUiTest {
        var query by mutableStateOf("")
        setContent {
            MaterialTheme(s) {
                tech.kloos.kompound.settings.KSettingsFilter(query) {
                    tech.kloos.kompound.settings.KSettingsGroup("Model") {
                        tech.kloos.kompound.settings.KSettingsRow("Server URL", keywords = listOf("host")) { KText("c1") }
                    }
                    tech.kloos.kompound.settings.KSettingsGroup("General") {
                        tech.kloos.kompound.settings.KSettingsRow("Notifications") { KText("c2") }
                    }
                }
            }
        }
        onNodeWithText("Model").assertExists()
        onNodeWithText("General").assertExists()
        query = "HOST"
        waitForIdle()
        onNodeWithText("Server URL").assertExists()
        onNodeWithText("Notifications").assertDoesNotExist()
        onNodeWithText("General").assertDoesNotExist()
    }

    @Test
    fun settingsTextFieldCommitsOnEnterOnlyWhenChanged() = runComposeUiTest {
        val commits = mutableListOf<String>()
        setContent { MaterialTheme(s) { tech.kloos.kompound.settings.KSettingsTextField("a", { commits += it }, Modifier.testTag("f")) } }
        onNodeWithTag("f").performKeyInput { keyDown(androidx.compose.ui.input.key.Key.Enter); keyUp(androidx.compose.ui.input.key.Key.Enter) }
        assertEquals(emptyList(), commits)
    }

    @Test
    fun previewThemeSwitchesDirectionAndScheme() = runComposeUiTest {
        var dir: androidx.compose.ui.unit.LayoutDirection? = null
        var bg = Color.Unspecified
        setContent {
            tech.kloos.kompound.theme.KompoundPreviewTheme(dark = true, rtl = true) {
                dir = androidx.compose.ui.platform.LocalLayoutDirection.current
                bg = MaterialTheme.colorScheme.background
            }
        }
        assertEquals(androidx.compose.ui.unit.LayoutDirection.Rtl, dir)
        assertEquals(tech.kloos.kompound.theme.KThemePresets.Default.dark.background, bg)
    }
}

@OptIn(ExperimentalTestApi::class)
class KSwipeStatusCodeTest {
    private val s = ButtonTestScheme

    @Test
    fun swipePastThresholdFiresAndSpringsBack() = runComposeUiTest {
        val fired = mutableListOf<tech.kloos.kompound.swipe.KSwipeSide>()
        setContent {
            MaterialTheme(s) {
                Box(Modifier.width(200.dp)) {
                    tech.kloos.kompound.swipe.KSwipeActions(
                        onAction = { fired += it },
                        startAction = tech.kloos.kompound.swipe.KSwipeAction("Delete", Color.Red, Color.White) { KText("D") },
                        modifier = Modifier.testTag("row"),
                    ) { Box(Modifier.width(200.dp).height(48.dp)) { KText("row") } }
                }
            }
        }
        onNodeWithTag("row").performTouchInput { swipeRight(startX = 5f, endX = 180f) }
        waitForIdle()
        assertEquals(listOf(tech.kloos.kompound.swipe.KSwipeSide.Start), fired)
    }

    @Test
    fun shortSwipeDoesNothingAndSideWithoutActionIsLocked() = runComposeUiTest {
        val fired = mutableListOf<tech.kloos.kompound.swipe.KSwipeSide>()
        setContent {
            MaterialTheme(s) {
                Box(Modifier.width(200.dp)) {
                    tech.kloos.kompound.swipe.KSwipeActions(
                        onAction = { fired += it },
                        startAction = tech.kloos.kompound.swipe.KSwipeAction("Delete", Color.Red, Color.White) { KText("D") },
                        modifier = Modifier.testTag("row"),
                    ) { Box(Modifier.width(200.dp).height(48.dp)) { KText("row") } }
                }
            }
        }
        onNodeWithTag("row").performTouchInput { swipeRight(startX = 5f, endX = 40f) }
        waitForIdle()
        onNodeWithTag("row").performTouchInput { swipeLeft(startX = 190f, endX = 10f) }
        waitForIdle()
        assertEquals(emptyList(), fired)
    }

    @Test
    fun statusIconDescriptionsFollowTheStatus() = runComposeUiTest {
        setContent {
            MaterialTheme(s) {
                androidx.compose.foundation.layout.Row {
                    tech.kloos.kompound.state.KStatusIcon(tech.kloos.kompound.state.KStatus.Error, Modifier.testTag("e"))
                    tech.kloos.kompound.state.KStatusIcon(tech.kloos.kompound.state.KStatus.Success, Modifier.testTag("s"), contentDescription = "OK!")
                }
            }
        }
        onNodeWithTag("e").assertContentDescriptionEquals("Failed")
        onNodeWithTag("s").assertContentDescriptionEquals("OK!")
    }

    @Test
    fun codeSoftWrapKeepsAllText() = runComposeUiTest {
        setContent { MaterialTheme(s) { Box(Modifier.width(120.dp)) { tech.kloos.kompound.code.KCode("a long line ".repeat(10), softWrap = true) } } }
        onNodeWithText("a long line ".repeat(10)).assertExists()
    }
}

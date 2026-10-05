package tech.kloos.kompound.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import tech.kloos.kompound.icon.KIcons
import tech.kloos.kompound.inline.KInlineEdit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class KTextStyleTest {
    @Test
    fun aMaterialTextStyleChangesTheSizeOfTheText() = runComposeUiTest {
        setContent {
            MaterialTheme(lightColorScheme()) {
                KText("small", TextStyle(fontSize = 12.sp))
                KText("big", TextStyle(fontSize = 40.sp, fontWeight = FontWeight.Bold))
                KText("heading", KTextDefaults.heading())
            }
        }
        val small = onNodeWithText("small").fetchSemanticsNode().size.height
        val big = onNodeWithText("big").fetchSemanticsNode().size.height
        assertTrue(big > small * 2, "40sp text is much taller than 12sp: $big vs $small")
        onNodeWithText("heading").assertIsDisplayed()
    }

    @Test
    fun publicIconsAreTheSameVectorsTheComponentsDraw() {
        assertEquals("edit", KIcons.Edit.name)
        assertEquals("add", KIcons.Add.name)
        assertEquals("delete", KIcons.Delete.name)
    }

    @Test
    fun inlineEditCanStartInEditModeAndBeHoisted() = runComposeUiTest {
        var editing by mutableStateOf<Boolean?>(null)
        var changes = 0
        setContent {
            MaterialTheme(lightColorScheme()) {
                KInlineEdit("name", {}, startEditing = false, editing = editing, onEditingChange = { editing = it; changes++ }, fillWidth = true)
            }
        }
        onNodeWithText("name").assertIsDisplayed()
        editing = true
        waitForIdle()
        onNodeWithContentDescription("Cancel").assertExists()
        editing = false
        waitForIdle()
        onNodeWithText("name").performClick()
        assertEquals(true, editing, "clicking the text asks to edit")
        assertTrue(changes >= 1)
    }
}

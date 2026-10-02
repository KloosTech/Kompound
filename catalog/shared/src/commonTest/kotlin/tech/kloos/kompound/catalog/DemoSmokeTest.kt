package tech.kloos.kompound.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import tech.kloos.kompound.demo.DemoControls
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders every discovered demo under light, dark, RTL and 200% font scale (COMPONENT_SPEC C-095).
 * Adding a demo adds it to this test automatically.
 */
@OptIn(ExperimentalTestApi::class)
class DemoSmokeTest {
    private class Config(val name: String, val dark: Boolean, val rtl: Boolean, val fontScale: Float)

    private val configs = listOf(
        Config("light", dark = false, rtl = false, fontScale = 1f),
        Config("dark", dark = true, rtl = false, fontScale = 1f),
        Config("rtl", dark = false, rtl = true, fontScale = 1f),
        Config("fontScale2", dark = false, rtl = false, fontScale = 2f),
    )

    @Test
    fun registryIsNotEmpty() {
        assertTrue(KompoundAllDemos.entries.isNotEmpty())
    }

    @Test
    fun everyDemoRendersInEveryConfiguration() {
        for (entry in KompoundAllDemos.entries) {
            for (config in configs) {
                runComposeUiTest {
                    setContent {
                        val density = LocalDensity.current
                        CompositionLocalProvider(
                            LocalLayoutDirection provides if (config.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr,
                            LocalDensity provides Density(density.density, config.fontScale),
                        ) {
                            MaterialTheme(colorScheme = if (config.dark) darkColorScheme() else lightColorScheme()) {
                                val controls = remember { DemoControls() }
                                val content = entry.content
                                Box(Modifier.testTag("demo")) { controls.content() }
                            }
                        }
                    }
                    waitForIdle()
                    try {
                        onNodeWithTag("demo").assertExists()
                    } catch (e: AssertionError) {
                        throw AssertionError("${entry.qualifiedId} failed to render in ${config.name}", e)
                    }
                }
            }
        }
    }
}

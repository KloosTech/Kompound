package tech.kloos.kompound.catalog

import androidx.compose.ui.graphics.toAwtImage
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteractionsProvider
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runDesktopComposeUiTest
import org.junit.Test
import tech.kloos.kompound.catalog.theme.ThemeMode
import tech.kloos.kompound.catalog.theme.ThemeSettings
import tech.kloos.kompound.catalog.ui.KompoundCatalog
import java.io.File
import javax.imageio.ImageIO

/**
 * Renders the real catalog at desktop and phone sizes and writes PNGs to build/screenshots so the design can be
 * reviewed without launching an app. Also a smoke test: it must lay out and respond to clicks without crashing.
 */
@OptIn(ExperimentalTestApi::class)
class CatalogScreenshots {
    private val outDir = File("build/screenshots").also { it.mkdirs() }

    /** Saves the page, and each overlay window (dialog, sheet) as `<name>-overlay<N>.png`. */
    private fun SemanticsNodeInteractionsProvider.save(name: String) {
        val roots = onAllNodes(isRoot()).fetchSemanticsNodes().size
        for (i in 0 until roots) {
            val image = onAllNodes(isRoot())[i].captureToImage().toAwtImage()
            ImageIO.write(image, "png", File(outDir, if (i == 0) "$name.png" else "$name-overlay$i.png"))
        }
    }

    @Test
    fun desktopLight() = runDesktopComposeUiTest(1440, 900) {
        setContent { KompoundCatalog(initialSettings = ThemeSettings(mode = ThemeMode.Light)) }
        waitForIdle()
        save("desktop-light")
    }

    @Test
    fun desktopDarkWithThemeDesigner() = runDesktopComposeUiTest(1440, 900) {
        setContent { KompoundCatalog(initialSettings = ThemeSettings(mode = ThemeMode.Dark)) }
        waitForIdle()
        onNodeWithContentDescription("Theme designer").performClick()
        waitForIdle()
        save("desktop-dark-theme-designer")
    }

    @Test
    fun desktopHowToUse() = runDesktopComposeUiTest(1440, 900) {
        setContent { KompoundCatalog(initialSettings = ThemeSettings(mode = ThemeMode.Dark)) }
        waitForIdle()
        onNodeWithText("How to use").performClick()
        waitForIdle()
        save("desktop-how-to-use")
    }

    @Test
    fun desktopTealRound() = runDesktopComposeUiTest(1440, 900) {
        setContent { KompoundCatalog(initialSettings = ThemeSettings(mode = ThemeMode.Light)) }
        waitForIdle()
        onNodeWithContentDescription("Theme designer").performClick()
        onNodeWithContentDescription("Teal").performClick()
        waitForIdle()
        save("desktop-teal")
    }

    @Test
    fun desktopBadgeOnTintedStage() = runDesktopComposeUiTest(1440, 900) {
        setContent { KompoundCatalog(initialSettings = ThemeSettings(hue = 330f, saturation = 0.65f, mode = ThemeMode.Light)) }
        waitForIdle()
        onNodeWithText("KBadge", useUnmergedTree = true).performClick()
        waitForIdle()
        onNodeWithText("Tinted", useUnmergedTree = true).performClick()
        waitForIdle()
        save("desktop-badge-tinted")
    }

    @Test
    fun desktopTagSheet() = runDesktopComposeUiTest(1440, 900) {
        setContent { KompoundCatalog(initialSettings = ThemeSettings(mode = ThemeMode.Light)) }
        waitForIdle()
        onNodeWithContentDescription("Filter by tag").performClick()
        waitForIdle()
        save("desktop-tag-sheet")
    }

    @Test
    fun phoneList() = runDesktopComposeUiTest(390, 844) {
        setContent { KompoundCatalog(initialSettings = ThemeSettings(mode = ThemeMode.Light)) }
        waitForIdle()
        save("phone-list")
    }

    @Test
    fun phoneDetail() = runDesktopComposeUiTest(390, 844) {
        setContent { KompoundCatalog(initialSettings = ThemeSettings(mode = ThemeMode.Light)) }
        waitForIdle()
        onNodeWithText("KButton", useUnmergedTree = true).performClick()
        waitForIdle()
        save("phone-detail")
    }
}

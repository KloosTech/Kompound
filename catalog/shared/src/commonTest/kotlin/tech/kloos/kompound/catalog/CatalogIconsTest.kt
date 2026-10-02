package tech.kloos.kompound.catalog

import tech.kloos.kompound.catalog.ui.CatalogIcons
import kotlin.test.Test
import kotlin.test.assertTrue

class CatalogIconsTest {
    @Test
    fun everyIconPathParsesAndHasDrawingCommands() {
        // A malformed path would throw when the icon is first used, so build every one here.
        CatalogIcons.all.forEach { icon ->
            assertTrue(icon.root.size > 0, "${icon.name} has no path")
            assertTrue(icon.viewportWidth == 24f && icon.viewportHeight == 24f)
        }
    }
}

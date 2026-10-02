import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import tech.kloos.kompound.catalog.ui.KompoundCatalog

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport { KompoundCatalog() }
}

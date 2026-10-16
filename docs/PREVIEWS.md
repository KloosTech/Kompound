# Previews and screenshot tests

`KompoundPreviewTheme` (package `tech.kloos.kompound.theme`) wraps content in a theme, a page background and fixed switches, so a preview does not
depend on the machine's dark mode:

```kotlin
@Preview @Composable
fun ChatPreviewDark() = KompoundPreviewTheme(spec = KThemePresets.GitHubDark, dark = true) { ChatContent(sampleState) }

@Preview @Composable
fun ChatPreviewRtlCompact() = KompoundPreviewTheme(rtl = true, density = KDensity.Compact) { ChatContent(sampleState) }
```

Make screens previewable by splitting them into a stateless `Content(state, onEvent)` and a thin stateful wrapper.

## Screenshot test recipe (Compose Desktop)

Compose Multiplatform's UI test API renders without a window and can capture an image. This is how Kompound's own tests check colours:

```kotlin
@OptIn(ExperimentalTestApi::class)
class ChatScreenshotTest {
    @Test fun dark() = runComposeUiTest {
        setContent { KompoundPreviewTheme(dark = true) { ChatContent(sampleState) } }
        val image = onRoot().captureToImage()
        // compare against a stored PNG, e.g. with the pixel diff of your choice
        assertTrue(image.width > 0)
    }
}
```

Keep the golden images in `src/desktopTest/resources/golden/`, write them on the first run and fail on a pixel difference above a small tolerance
(fonts differ a little between machines, so run the check on one OS in CI). Roborazzi also supports Compose Desktop if you prefer a ready-made diff tool.

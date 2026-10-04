# Kompound

[![CI](https://github.com/KloosTech/Kompound/actions/workflows/ci.yml/badge.svg)](https://github.com/KloosTech/Kompound/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/tech.kloos.kompound/kompound?include_prereleases&label=Maven%20Central)](https://central.sonatype.com/namespace/tech.kloos.kompound)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](LICENSE)

**Kompound** is a Kotlin Multiplatform UI component library for **Android, iOS, Desktop (JVM) and Web (Wasm)**, built on
Compose Multiplatform and the new Compose **Styles API**. Components take a single `style` parameter instead of dozens
of colour and shape parameters, keep their interaction states (hovered, pressed, focused, disabled, selected, error)
inside the style, and read their colours from your Material 3 theme.

> **Status: alpha.** The API can still change before 1.0, and the Styles API itself is experimental in Compose.

## Try it

**[Live catalog in your browser](https://kompound.kloos.tech/)** (Kotlin/Wasm; needs a recent Chrome, Edge, Firefox or Safari).
It lists every component with search, category and tag filters, and interactive controls for each component's states.
The same catalog runs as an Android app, an iOS app and a desktop app; build them yourself (see [Build from source](#build-from-source))
or grab the Android APK and desktop installers from the [Releases](https://github.com/KloosTech/Kompound/releases) page.

## What you get

| Area | Components |
|------|-----------|
| **Foundation** | `KompoundTheme` (+ tokens), `KText`, `KIcon`, `KSurface`, `KDivider` |
| **Buttons** | `KButton` (filled, tonal, outlined, text; loading state), `KIconButton`, `KFab`, `KToggleButton`, `KSegmentedControl` |
| **Selection** | `KCheckbox` (tri-state), `KRadioButton`, `KSwitch`, `KChip` (assist and filter) |
| **Text input** | `KTextField`, `KTextArea`, `KNumberField`, `KSearchBar`, `KInlineEdit` |
| **Pickers** | `KDropdown`, `KMultiDropdown`, `KMenu` / `KMenuItem`, `KDateField`, `KDateRangeField` |
| **Display** | `KBadge`, `KAvatar`, `KListItem`, `KLinearProgress`, `KCircularProgress`, `KEmptyState`, `KErrorState` |
| **Layout** | `KScaffold`, `KTopBar` |
| **Node graph** (`kompound-graph`) | `KNodeGraph`, `KNode`, groups and subgraphs, auto layout, JSON, `GraphEngine` (suspending nodes, traces, pins), `KNodeInspector` |
| **Overlays and feedback** | `KDialog`, `KAlertDialog`, `KBottomSheet` (adaptive), `KSnackbar`, `KTooltip` |

Every component is tested on desktop and the iOS simulator (pixel checks, semantics, hit areas, state layers), has a demo,
and follows the [component contract](docs/COMPONENT_SPEC.md).

## Install

Kompound is published to Maven Central under the namespace `tech.kloos.kompound`.

```kotlin
// build.gradle.kts of a Kotlin Multiplatform module (commonMain)
plugins {
    // Opts the module in to the Compose Styles API (see below). Available from 0.1.0-alpha03.
    id("tech.kloos.kompound") version "0.1.0-alpha03"
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("tech.kloos.kompound:kompound:0.1.0-alpha03")
        }
    }
}
```

An Android-only or desktop-only project adds the same `implementation(...)` line; Gradle picks the right variant.

**Why the plugin?** Every Kompound composable has a `style: Style` parameter and Compose's `Style` is still experimental, so the Kotlin compiler
reports "This foundation style API is experimental" for *every* Kompound call, even one that passes no style, unless the calling module opts in.
A library cannot opt its callers in, so apply the `tech.kloos.kompound` plugin (above), or add the opt-in yourself, which is what the plugin does:

```kotlin
kotlin { compilerOptions { optIn.add("androidx.compose.foundation.style.ExperimentalFoundationStyleApi") } }
```

The node graph framework (editor canvas, execution engine, inspector; experimental) is a separate artifact: `implementation("tech.kloos.kompound:kompound-graph:0.1.0-alpha03")`. Start with [`docs/AGENT_GUIDE.md`](docs/AGENT_GUIDE.md) section 7.

**Requirements**

| | |
|---|---|
| Kotlin | 2.4.20 or newer (the library is compiled with 2.4.20) |
| Compose Multiplatform | 1.12.1 or newer (`foundation` 1.12 contains the Styles API) |
| Android | minSdk 24, compileSdk 37 |
| iOS | 15+ (`iosArm64`, `iosSimulatorArm64`) |
| Desktop | JVM 11+ |
| Web | `wasmJs` and `js` (IR, browser) |

Kompound depends only on Compose Multiplatform (runtime, foundation, ui, animation, Material 3 for theme tokens and the
date picker calendar). No other third-party libraries.

## Use it

```kotlin
@Composable
fun App() {
    KompoundTheme {                         // wraps MaterialTheme; follows the system light/dark mode
        var name by remember { mutableStateOf("") }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KTextField(name, { name = it }, label = "Name", placeholder = "Ada Lovelace")
            KButton(onClick = { save(name) }, enabled = name.isNotBlank()) { KText("Save") }
        }
    }
}
```

Two things that differ from plain Material 3:

- **Use `KText` and `KIcon`** inside Kompound components, not Material's `Text`/`Icon`. Text inside a styled component inherits
  its colour and typography from the component's style; Material's `Text` does not read that. Icons take their tint from the
  surrounding component automatically.
- **`KompoundTheme` is optional.** Without it, components use the ambient `MaterialTheme` and light extension colours.
  Wrap your app in it to get dark-aware semantic colours (success, warning, info) and the spacing, motion and state-layer tokens
  via `KompoundTheme.tokens`.

### Styling

Every visual component has a `style: Style` parameter. Your style is merged over the component's default, so you only
set what you want to change, and state blocks work the same way:

```kotlin
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.pressed

KButton(
    onClick = {},
    style = Style {
        background(Color(0xFF006D3B))
        pressed { background(Color(0xFF004D29)) }
    },
) { KText("Custom green") }
```

Defaults are exposed too (`KButtonDefaults.style(variant)`, `KTextFieldDefaults.style()`, ...), so you can build on them. Colours of the
default styles come from your `MaterialTheme.colorScheme`, so an app that is already themed needs no extra work.

### Dialogs, sheets and overlays

Overlays are shown by calling them while they should be visible; you own the state:

```kotlin
var open by remember { mutableStateOf(false) }
KButton(onClick = { open = true }) { KText("Share") }
if (open) {
    KBottomSheet(onDismissRequest = { open = false }, title = "Share with") {   // sheet on phones, dialog on wide windows
        KListItem("Ada Lovelace", onClick = { open = false })
    }
}
```

Snackbars use a host state: `val host = remember { KSnackbarHostState() }`, place `KSnackbarHost(host)` in a `KScaffold`'s
`snackbarHost` slot, and call `host.showSnackbar("Saved", actionLabel = "Undo")` from a coroutine; it returns whether the user pressed the action.

## Make your own catalog entries

The annotation, runtime model, KSP processor and Gradle plugins are published, so any project can mark composables as demos and get a
generated registry (the mechanism the Kompound catalog itself uses):

```kotlin
// build.gradle.kts of the module that contains the demos
plugins { id("tech.kloos.kompound.demos") version "0.1.0-alpha03" }   // applies KSP, the processor and the dependencies
```

```kotlin
@KompoundDemo(id = "my.button", title = "My button", category = KompoundCategory.Buttons, tags = ["button"])
@Composable
fun DemoScope.MyButtonDemo() {
    val label = textControl("Label", "Click me")      // shows up as an interactive control next to the demo
    val enabled = boolControl("Enabled", true)
    KButton(onClick = {}, enabled = enabled) { KText(label) }
}
```

The plugin generates `KompoundRegistry_<module>` with every demo, metadata (title, description, tags, status) and a composable to render it.
See [`samples/consumer`](samples/consumer) for a complete example that builds against the published artifacts.
The catalog app itself (`catalog/shared`) is not published as a library yet.

## Build from source

Prerequisites: JDK 17 or newer, the Android SDK (platform 37 and build-tools 37), and for the iOS app a Mac with Xcode
and [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`). Create `local.properties` with
`sdk.dir=/path/to/Android/sdk` if `ANDROID_HOME` is not set.

```bash
./gradlew :catalog:desktopApp:run                              # catalog on the desktop (fastest feedback loop)
./gradlew :catalog:webApp:wasmJsBrowserDevelopmentRun          # catalog in the browser with live reload
./gradlew :catalog:androidApp:installDebug                     # catalog on a connected device or emulator
cd catalog/iosApp && xcodegen generate && open KompoundCatalogApp.xcodeproj    # catalog on an iOS simulator

./gradlew :kompound:desktopTest :kompound:iosSimulatorArm64Test               # library tests
./gradlew :catalog:shared:desktopTest                                         # demo smoke test: renders every demo in light/dark/RTL/large font
./gradlew publishToMavenLocalAll                                              # install all artifacts into ~/.m2
```

### Project layout

| Module | What it is |
|--------|-----------|
| `kompound` | The component library (published) |
| `kompound-annotations` | `@KompoundDemo` and friends (published) |
| `kompound-demo` | Runtime model for demos: `DemoEntry`, `DemoScope` controls (published) |
| `kompound-processor` | KSP processor that generates a demo registry (published) |
| `kompound-gradle-plugin` | Gradle plugins `tech.kloos.kompound.demos` and `.catalog` (published, included build) |
| `showcase` | The demos for every component |
| `catalog/*` | The catalog app: `shared` (UI), `androidApp`, `iosApp`, `desktopApp`, `webApp` |
| `samples/consumer` | A standalone project that depends on the published artifacts |
| `build-logic` | Convention plugins shared by the modules |

## Contributing

Components follow a strict contract (naming, styling through `Style`, accessibility, tests, demos): read
[`docs/COMPONENT_SPEC.md`](docs/COMPONENT_SPEC.md) first. In short, a new component is a composable in `kompound`, a `@KompoundDemo` function in `showcase`
(it appears in the catalog on its own), and tests that check its pixels, semantics and states. The PR template has the checklist.

More documentation:

- [`docs/AGENT_GUIDE.md`](docs/AGENT_GUIDE.md): compact guide for AI coding agents (rules, component reference, graph framework, testing)
- [`docs/SPEC.md`](docs/SPEC.md): architecture, decisions and the project log
- [`docs/COMPONENT_SPEC.md`](docs/COMPONENT_SPEC.md): the component contract
- [`docs/adr`](docs/adr): architecture decision records (styling model, demo discovery, catalog aggregation)
- [`docs/RELEASING.md`](docs/RELEASING.md): how library and catalog releases work
- [`CHANGELOG.md`](CHANGELOG.md)

## License

Apache License 2.0, see [LICENSE](LICENSE). Third-party notices are in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

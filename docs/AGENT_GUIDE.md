# Kompound: guide for AI coding agents

Read this first when you write code that uses Kompound. It is a compact, checked reference: every signature below was taken from the
source, so prefer it over guessing from Material 3 habits. Human docs: `README.md`, `docs/COMPONENT_SPEC.md`. Source of truth for any
signature: the file named in the "Where things are" table at the end.

## 1. What this is

Kompound is a Kotlin Multiplatform (Android, iOS, Desktop JVM, Web wasmJs) UI library on **Compose Multiplatform 1.12** and the
**Compose Styles API**. Package root `tech.kloos.kompound`, all public composables are prefixed **`K`** (`KButton`, `KTextField`).
Two artifacts matter to app code:

| Artifact | Contents |
|---|---|
| `tech.kloos.kompound:kompound` | the component library (published, `0.1.0-alpha02`) |
| `tech.kloos.kompound:kompound-graph` | node graph framework (editor, execution engine, inspector). Published since `0.1.0-alpha02`; `implementation("tech.kloos.kompound:kompound-graph:0.1.0-alpha02")` |

Status: alpha. The Styles API is experimental, so APIs can still change.

## 2. Ten rules (the mistakes agents make most)

1. **Use `KText` and `KIcon`, never Material `Text`/`Icon`, inside Kompound components.** Material's `Text` ignores the colour and
   typography the component's `Style` provides, so text comes out in the wrong colour (dark text on dark backgrounds).
2. **Style through the `style: Style` parameter only.** There are no `colors =`, `shape =`, `elevation =` parameters. Your `Style` is merged *over* the component default, so set only what you change.
3. **State-dependent looks go inside the `Style`** (`pressed { }`, `hovered { }`, `focused { }`, `disabled { }`, `selected { }`), not in `if (isPressed)` branches.
4. **The Styles API is experimental: opt in once per module.** Apply the Gradle plugin `id("tech.kloos.kompound")` (it adds the opt-in to every Kotlin compilation) or add `optIn.add("androidx.compose.foundation.style.ExperimentalFoundationStyleApi")` to `compilerOptions`. Without it, **every Kompound call fails to compile** ("This foundation style API is experimental"), even with default arguments, because each composable has a `style: Style` parameter.
5. **Wrap the app in `KompoundTheme { }`.** It wraps `MaterialTheme`, follows the system dark mode and adds `success`/`warning`/`info` colours and spacing/motion tokens. Colours come from your `MaterialTheme.colorScheme`; do not hard-code them.
6. **Parameters named `contentDescription` are required** on icon-only components (`KIconButton`, `KFab`, `KActionMenu`): give a real description.
7. **You own the state.** Fields, switches, dropdowns, dialogs are controlled: pass the value and a change callback and keep the state in `remember { mutableStateOf(...) }` or a ViewModel. Dialogs and sheets are shown by *calling* them while a boolean is true.
8. **`modifier` is the first optional parameter; `style` comes right after it.** Pass arguments by name after the required ones.
9. **Common code only.** Everything lives in `commonMain`; do not use `java.*`/`android.*` APIs in shared UI code.
10. **Write a test or a `@KompoundDemo` for what you add** (see section 9). A new component must have a demo; the catalog shows it automatically.

## 3. Project setup

```kotlin
// build.gradle.kts of a Kotlin Multiplatform module
plugins {
    kotlin("multiplatform") version "2.4.20"
    id("org.jetbrains.compose") version "1.12.1"
    id("org.jetbrains.kotlin.plugin.compose") version "2.4.20"
    id("tech.kloos.kompound") version "0.1.0-alpha03"      // adds the Styles API opt-in (from alpha03; before that add optIn.add(...) by hand)
}
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("tech.kloos.kompound:kompound:0.1.0-alpha02")
            implementation(compose.foundation)
        }
    }
}
```

Requirements: Kotlin 2.4.20+, Compose Multiplatform 1.12.1+, Android minSdk 24, iOS 15+, JVM 11+.

Minimal app:

```kotlin
@Composable
fun App() {
    KompoundTheme {
        var name by remember { mutableStateOf("") }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KTextField(name, { name = it }, label = "Name", placeholder = "Ada Lovelace")
            KButton(onClick = { save(name) }, enabled = name.isNotBlank()) { KText("Save") }
        }
    }
}
```

Imports follow the package of the file: `tech.kloos.kompound.buttons.KButton`, `tech.kloos.kompound.text.KText`,
`tech.kloos.kompound.textfield.KTextField`, `tech.kloos.kompound.theme.KompoundTheme`, and so on (package = the directory under
`kompound/src/commonMain/kotlin/tech/kloos/kompound/`).

## 4. Styling

```kotlin
import androidx.compose.foundation.style.Style
import androidx.compose.foundation.style.pressed
import androidx.compose.foundation.style.hovered

KButton(
    onClick = {},
    style = Style {
        background(Color(0xFF006D3B))                    // merged over the default look
        pressed { background(Color(0xFF004D29)) }        // state blocks live in the style
    },
) { KText("Custom green") }
```

- Base styles are exposed so you can build on them: `KButtonDefaults.style(variant)`, `KTextFieldDefaults.style()`.
- Tokens: `KompoundTheme.tokens.colors.success / warning / info` (each with `on…`, `…Container`, `on…Container`), plus spacing, motion and state-layer opacity tokens. Everything else comes from `MaterialTheme.colorScheme`, `.typography`, `.shapes`.
- Rich text (annotated strings) needs an explicit `textStyle` on `KText(AnnotatedString, textStyle = …)`: inherited text style does not apply to annotated text.

## 5. Component reference

Exact parameters in the source file; this lists what each is for and the parameters you use most. Content lambdas are
`@Composable`; nullable lambdas are optional slots.

### Foundations
| Composable | Use | Notes |
|---|---|---|
| `KText(text: String, modifier, style, maxLines, overflow)` / `KText(AnnotatedString, …, textStyle)` | all text | |
| `KIcon(imageVector \| bitmap \| painter, contentDescription: String?, modifier, style, tint)` | icons | `contentDescription = null` for decorative |
| `KSurface(modifier, style, contentColor, content)` / `KSurface(onClick, …)` | container; the clickable overload is a button-like surface | |
| `KDivider(modifier, style, orientation)` | separator | |
| `KompoundTheme(colorScheme, typography, shapes, tokens, content)` | app theme | |

### Buttons
| Composable | Use |
|---|---|
| `KButton(onClick, modifier, variant, style, enabled, loading, interactionSource, effects, content: RowScope)` | `variant`: `KButtonVariant.Filled / Tonal / Outlined / Text`. `loading = true` shows a spinner. `effects = KButtonEffects(clickShadow, bounce, fade, colorMorph, shapeMorph, sparkles)` for press feedback |
| `KIconButton(onClick, contentDescription, modifier, variant = Text, …, icon)` | icon-only button |
| `KFab(onClick, contentDescription, …, content)` | floating action button |
| `KToggleButton(checked, onCheckedChange, …, content)` | on/off button |
| `KProgressButton(text, progress: Float?, onClick, …)` | button that shows download/upload progress (`null` = indeterminate) |
| `KSegmentedControl(options: List<String>, selectedIndex, onSelectedIndexChange, …)` | 2 to 5 mutually exclusive choices |
| `KSlideToConfirm(label, onConfirm, …)` | slide to confirm a risky action |

### Selection and input
| Composable | Use |
|---|---|
| `KCheckbox(checked, onCheckedChange: ((Boolean)->Unit)?, …, label)` and a tri-state overload `KCheckbox(state: ToggleableState, onClick, …)` | pass `null` callback for read-only |
| `KRadioButton(selected, onClick, …, label)` | |
| `KSwitch(checked, onCheckedChange: ((Boolean)->Unit)?, …, label)` | |
| `KChip(label, onClick, modifier, selected: Boolean? = null, …, leading, trailing)` | `selected = null` is an assist chip, `true/false` a filter chip |
| `KSlider(value, onValueChange, valueRange, steps, …)` | |
| `KTextField(value, onValueChange, label, placeholder, supportingText, isError, enabled, readOnly, singleLine, …)` | |
| `KTextArea(value, onValueChange, label, minLines, maxLines, maxLength, …)` | multi-line |
| `KNumberField(value: String, onValueChange, allowDecimal, allowNegative, …)` | value is a `String` |
| `KPasswordField(value, onValueChange, strength: ((String)->KPasswordStrength)?, …)` and `KStrengthMeter(level, segments)` | |
| `KSearchBar(query, onQueryChange, onSearch, placeholder, trailingActions, …)` | |
| `KInlineEdit(value, onValueChange, validate: (String)->String?, …)` | click-to-edit text |
| `KMarkdownField(value, onValueChange, …)` | editor that previews markdown |
| `KDropdown(options: List<T>, selected: T?, onSelect, optionLabel, label, …)` | single choice |
| `KMultiDropdown(options, selected: Set<T>, onSelectionChange, …)` | multiple choice |
| `KDateField(value: Long?, onValueChange, formatDate, …)` / `KDateRangeField(start, end, onRangeChange, …)` | epoch millis |

### Display
| Composable | Use |
|---|---|
| `KBadge(text, tone, emphasis, shape, leading)` and `KBadgeDot(tone, emphasis, contentDescription)` | `KBadgeTone`: Neutral, Primary, Error, Success, Warning, Info. `KBadgeEmphasis`: Strong, Subtle. `KBadgeShape`: Pill, Square |
| `KAvatar(name, size: KAvatarSize, status: KAvatarStatus?, image)` | sizes Small, Medium, Large; status Online, Away, Busy, Offline |
| `KListItem(headline: String \| @Composable, supporting, overline, leading, trailing, bottom, onClick, selected, …)` | list rows |
| `KKeyValue(label, value, orientation, flipped, …)` and `KMetric(value, unit)` | label/value pairs and big numbers |
| `KLinearProgress(progress: Float?, …)` / `KCircularProgress(progress: Float?, size, strokeWidth, …)` | `null` = indeterminate |
| `KStepList(steps: List<KStep>, labels)` with `KStep(title, state: KStepState, trailing)` | `KStepState.Waiting`, `InProgress(progress)`, `Done` |
| `KEmptyState(title, description, illustration, action)` / `KErrorState(title, description, onRetry, …)` | empty and error screens |
| `KMarkdown(markdown, onLinkClick, selectable, …)` | renders markdown |
| `KCode(code, onCodeChange?, language: KCodeLanguage, showLineNumbers, …)` | code view; editable when `onCodeChange` is set |
| `KTooltip(text, placement: KTooltipPlacement.Above/Below, …, content)` | wraps the thing it describes |
| `KExpandable(title \| header slot, expanded, onExpandedChange, …, content)` | one collapsible section |
| `KAccordion(state = rememberKAccordionState(exclusive, initiallyExpanded), …) { Item(key, title = …) { content } }` (or `Item(key, header = { … }) { content }`) | a group of sections; `key` must be unique |

### Layout and navigation
| Composable | Use |
|---|---|
| `KScaffold(topBar, bottomBar, snackbarHost, floatingActionButton, contentWindowInsets, style, content: (PaddingValues))` | screen frame; apply the `PaddingValues` to your content |
| `KTopBar(title: String \| @Composable, navigation, actions: RowScope, windowInsets)` | |
| `KMenu(expanded, onDismissRequest, …) { KMenuItem(text, onClick, enabled, selected, showCheck, leading, trailing, supportingText) }` | anchored dropdown menu |
| `KActionMenu(actions: List<KMenuAction?>, contentDescription, …)` | overflow menu from data: `KMenuActionItem(text, onClick, supportingText, icon, badge, selected, closeOnClick, enabled)` and `KMenuActionGroup(text, items)`; `null` entries are ignored (build the list with conditionals); groups render with dividers |

### Overlays and feedback
| Composable | Use |
|---|---|
| `KDialog(onDismissRequest, title, actions: RowScope, fullScreen, showCloseButton, …) { content }` | general dialog |
| `KAlertDialog(onDismissRequest, title, message, confirmText, onConfirm, dismissText)` | confirm/cancel |
| `KBottomSheet(onDismissRequest, title, actions, showCloseButton, …) { content }` | sheet on phones, dialog on wide windows |
| `KSnackbarHost(hostState)` + `KSnackbarHostState().showSnackbar(message, actionLabel, tone, duration)` | `suspend`; returns `KSnackbarResult.ActionPerformed` or `Dismissed`. Put the host in `KScaffold(snackbarHost = …)` |

Show overlays by calling them under a condition:

```kotlin
var open by remember { mutableStateOf(false) }
KButton(onClick = { open = true }) { KText("Delete") }
if (open) {
    KAlertDialog(onDismissRequest = { open = false }, title = "Delete?", message = "This cannot be undone.",
        confirmText = "Delete", onConfirm = { delete(); open = false }, dismissText = "Cancel")
}
```

Snackbar:

```kotlin
val host = remember { KSnackbarHostState() }
val scope = rememberCoroutineScope()
KScaffold(snackbarHost = { KSnackbarHost(host) }) { padding ->
    KButton(onClick = { scope.launch { if (host.showSnackbar("Saved", actionLabel = "Undo") == KSnackbarResult.ActionPerformed) undo() } },
        modifier = Modifier.padding(padding)) { KText("Save") }
}
```

## 6. Patterns

- **Form**: one `mutableStateOf` per field (or a state class), `isError` + `supportingText` on invalid fields, submit `KButton(enabled = valid, loading = saving)`.
- **List screen**: `KScaffold(topBar = { KTopBar("Title") })` + `LazyColumn { items(x) { KListItem(headline = it.name, onClick = …) } }`; show `KEmptyState` / `KErrorState(onRetry)` for the other states.
- **Choice**: 2 to 5 options `KSegmentedControl`; more options `KDropdown`; several answers `KMultiDropdown`.
- **Destructive action**: `KAlertDialog`, or `KSlideToConfirm` when it must be deliberate.
- **Long task**: `KStepList` plus `KLinearProgress(progress)` (or `KProgressButton`).

## 7. Node graph framework (`kompound-graph`)

Everything below is under `tech.kloos.kompound.graph`. Design rationale: `docs/adr/0004-node-graph.md`. A working, compiled example:
`showcase/.../graph/KNodeGraphDemo.kt`; logic circuits: `KLogicGatesDemo.kt`; async execution with inspector: `KGraphEngineDemo.kt`.

### 7.1 Mental model

- The graph is **one flat immutable `Graph`** of `GraphNode(id, kind, position, ports, data, group, scope, pin)`, `Edge(id, from: PortRef, to: PortRef)` and `NodeGroup`.
- Every change is an invertible **`GraphCommand`** (undo/redo for free). Never mutate a `Graph`; call `state.execute(command)`.
- `kind` is a string; **your `nodeContent` lambda maps `kind` to a composable**. Composables are never serialized, only the data.
- Ports: `PortSpec.input(id, label, type, capacity, signal)` / `PortSpec.output(...)`. `PortType.of("number")`: two ports connect when types match (or one is `PortType.Any`). Inputs hold one wire by default; outputs many.

### 7.2 Minimal editor

```kotlin
val number = PortType.of("number")
val graph = Graph.of(
    nodes = listOf(
        GraphNode(NodeId("a"), "number", Offset(40f, 40f), listOf(PortSpec.output("value", type = number)), data = 3f),
        GraphNode(NodeId("sum"), "add", Offset(340f, 60f),
            listOf(PortSpec.input("x", type = number), PortSpec.input("y", type = number), PortSpec.output("out", type = number))),
    ),
    edges = listOf(Edge(EdgeId("a->sum"), PortRef(NodeId("a"), PortId("value")), PortRef(NodeId("sum"), PortId("x")))),
)
val state = remember { KGraphState(graph, gridStep = 24f) }

KNodeGraph(state, Modifier.fillMaxWidth().height(480.dp), fitOnFirstLayout = true) { node ->
    when (node.kind) {
        "number" -> KNode(node, "Number") {
            Content { KSlider(node.data as Float, { state.execute(GraphCommand.UpdateNodeData(node.id, it)) }, valueRange = 0f..10f) }
            Output("value", "Value")
        }
        else -> KNode(node, "Add") { Input("x", "X"); Input("y", "Y"); Output("out", "Sum") }
    }
}
```

`KNode`'s scope offers `Input(port, label, editor)`, `Output(port, label)`, `Content { }`, `PortHandle(port)` and `Collapsible(title) { }`.
`KNode(collapsible = true)` adds a chevron that folds the whole body. Node height follows its content, wires follow resizes.

Edit from code: `state.connect(a, b)`, `state.execute(cmd)`, `state.undo()`, `state.redo()`, `state.removeSelection()`, `state.fitView()`,
`state.autoLayout()`, `state.groupSelection()`, `state.createSubgraph("name")`, `state.load(graph)`, `state.clearHistory()`.
React to edits with `state.onGraphChange = { old, new -> … }`.

Useful `KNodeGraph` parameters: `nodeTypes` (menu to add nodes), `edgeStyle = { edge -> KEdgeStyle(color, animated, dashed) }`,
`portColor`, `nodeStatus`, `edgeLabel`, `overlay = { KGraphControls(state, …); KMiniMap(state, …) }`, `virtualizeAbove` (default 150 nodes).
Mouse: drag background selects; hand tool (key H, button in `KGraphControls`), middle/right button or Space+drag pans; wheel zooms. Keys: Delete, Ctrl/Cmd+Z/Shift+Z/C/V/D/G/A, F fit, L arrange.

### 7.3 Saving and loading

```kotlin
val json = GraphJson(
    nodeData = mapOf("script" to nodeDataCodec<ScriptConfig>(encode = { … JsonObject … }, decode = { … })),  // per node kind
    portTypes = listOf(number),                                                                          // your PortType objects
)
val text = state.toJson(json)          // nodes, ports, data, pins, groups, scopes, edges, viewport
state.loadJson(text, json)             // clears history and selection
```

Unknown node kinds survive load and save unchanged (data is kept as raw `JsonValue`). Data types without a codec throw `GraphJsonException` naming the type. Typed values (pins, test inputs) use `ValueJson` (`Int`, `Long`, `Float`, lists, maps, your own types via `valueCodec`).

### 7.4 Running a graph (execution engine, `graph.runtime`)

The engine runs nodes in dependency order; **you supply the behaviour per node kind**. It knows nothing about CLIs, HTTP or scripts.

```kotlin
val runners = mapOf(
    "number" to singleOutputRunner { node, _ -> node.data as Float },
    "slow" to NodeRunner { ctx ->                       // may suspend as long as it likes
        ctx.log("starting")
        ctx.progress(0.2f, "fetching")
        delay(1000)
        ctx.emit("out", 1)                              // a signal downstream sees right away
        mapOf("out" to (ctx.inputs.require<Float>("in") * 2))   // final value(s)
    },
)
val engine = rememberGraphEngine(state, runners)         // follows the editor: every edit is handed to engine.update
```

- Result per node: `engine.runOf(id)`: `Idle | Waiting | Running | Done(outputs) | Failed(error) | Blocked(by)`. Read it in composables; it is observable.
- Edits cancel stale runs and re-run what is downstream. A failure blocks only downstream nodes. Cycles fail with `CycleException`.
- `rememberGraphEngine(state, runners, autoRun = false)` plus `engine.start()`, `stop()`, `rerun(id)`, `rerunAll()`.
- **Streaming**: a node may `emit` many times. Each input port has `PortSpec.input(..., signal = SignalMode.X)`: `Latest` (default, re-run on each value, cancelling the one in progress), `Each` (one run per value, in order), `Collect` (wait for the end, get a list), `Final` (wait for the end, get the last).
- **Subgraphs** are routed through at any depth. **Pins**: `state.pin(id, outputs)` fixes a node's outputs (the node and nodes only it would feed are not run); `state.unpin(id)`.
- **Test one node alone**: `engine.testNode(id, mapOf("a" to 41))` returns a `NodeTestRun` (status, attempt, real outputs, `cancel()`), recorded as an execution with trigger `Test`.
- **Traces**: `engine.executions` (observable), each with `attempts`: inputs, outputs, emissions, logs, progress, error, timing. `TraceOptions(maxExecutions = 20, captureValues = true, redact = …)` on the engine: values are captured by default, so redact or disable it for secrets.
- Thread safety: the engine guards its bookkeeping with a short lock; runners execute on `runDispatcher` (default `Dispatchers.Default`).

### 7.5 Inspecting runs (`graph.inspector`)

```kotlin
KNodeGraph(state, …,
    nodeStatus = { engine.nodeStatus(it, selectedExecution) },       // spinner, check, cross on each node
    edgeLabel = { engine.edgeLabel(it, selectedExecution) },         // "3 items" on the wires
) { node -> KNode(node, …, onDoubleClick = { inspecting = node.id }) { … } }

KExecutionList(engine, selectedExecution, { selectedExecution = it })     // "Live" row plus recorded runs
KNodeInspector(engine, node, state = state, execution = selectedExecution, parameters = { /* node settings UI */ })
```

`KNodeInspector` shows input (left), your `parameters` (middle) and output (right) with Schema, Table and JSON views, logs and errors, "Test step" with editable input, Pin/Unpin and "Run from here". `KValueView(value, mode)` shows any value on its own. Its input editor reads numbers as `Double`; pass `parseInput` for typed ports.

### 7.6 Graph gotchas

- The `Graph` is immutable: `state.graph.node(id)` after an edit returns the new node; re-read it, do not keep old copies.
- Put view state (slider position, text field) in `node.data` via `UpdateNodeData`, not in `remember`, or it is lost when the node scrolls out of view (virtualization composes only nearby nodes) and cannot be undone.
- Fold state of `Collapsible`/`collapsible` lives in `KGraphState` (`isExpanded`, `setExpanded`, `isCollapsed`, `setCollapsed`); it is not undoable and not saved.
- Node body text must be `KText` (see rule 1).
- Use `ConnectionPolicy(allowCycles = true)` on `KGraphState` for feedback circuits (latches). The engine rejects cycles; use your own settle loop for those (see `LogicSimulation` in the showcase).
- Do not run the engine on a scope without a dispatcher and call it from several threads unless needed; call `update/start/stop` from your UI thread.

## 8. Catalog demos (any project, not only this repo)

```kotlin
// build.gradle.kts of the module that contains demos
plugins { id("tech.kloos.kompound.demos") version "0.1.0-alpha02" }   // applies KSP, the processor and dependencies

@KompoundDemo(id = "my.button", title = "My button", category = KompoundCategory.Buttons, tags = ["button"])
@Composable
fun DemoScope.MyButtonDemo() {
    val label = textControl("Label", "Click me")          // an interactive control shown next to the demo
    val enabled = boolControl("Enabled", true)
    KButton(onClick = {}, enabled = enabled) { KText(label) }
}
```

Control helpers: `textControl`, `boolControl`, `choiceControl(name, options, initial, label)`, `floatControl(name, range, initial)`.
`KompoundCategory`: Foundations, Inputs, Buttons, Display, Feedback, Navigation, Layout, Overlays, Data (the demo description must be 160 characters or fewer, or KSP fails). The optional `usage = """…"""` string is shown in the "How to use" tab and is compile-checked in this repo.

## 9. Testing recipes

```kotlin
@OptIn(ExperimentalTestApi::class)
class MyTest {
    @Test fun clicks() = runComposeUiTest {
        var clicked = false
        setContent { MaterialTheme(lightColorScheme()) { KButton({ clicked = true }) { KText("Go") } } }
        onNodeWithText("Go").performClick()
        assertTrue(clicked)
    }
}
```

- Dependencies: `kotlin("test")` and `compose.uiTest`; desktop tests also need `compose.desktop.currentOs`.
- Run desktop tests: `./gradlew :kompound:desktopTest`; iOS: `:kompound:iosSimulatorArm64Test` (needs a Mac).
- When two nodes share the same text, `onNodeWithText` fails with "found 2 nodes": use `onAllNodesWithText(...).onFirst()`.
- Use `waitUntil(timeoutMillis = 5_000) { … }` (named argument) for async state; for coroutines in pure logic use `kotlinx-coroutines-test` `runTest` with a `StandardTestDispatcher(testScheduler)` and pass the scope itself (not `backgroundScope`) to anything you `advanceUntilIdle()` over.
- Pixel checks: `captureToImage().toPixelMap()`.

## 10. Verifying your change (this repo)

```bash
./gradlew :kompound:desktopTest :kompound-graph:desktopTest        # library tests (fast)
./gradlew :catalog:shared:desktopTest                              # renders every demo in light, dark, RTL and 200% font
./gradlew :kompound-graph:compileCommonMainKotlinMetadata          # catches JVM-only APIs in common code
./gradlew :showcase:compileKotlinWasmJs :kompound-graph:iosSimulatorArm64Test
./gradlew :catalog:desktopApp:run                                  # look at it
```

Pitfalls that only show up on other targets: `HashMap.merge`/`synchronized`/`Thread` and other JVM-only APIs in `commonMain` (the metadata compile catches them); text colour in dark themes (rule 1); hover states in pixel tests (use a colour tolerance).

## 11. Where things are

| Need | Look at |
|---|---|
| Any component's exact signature | `kompound/src/commonMain/kotlin/tech/kloos/kompound/<package>/K<Name>.kt` |
| Theme tokens | `kompound/.../theme/KompoundTokens.kt`, `KompoundTheme.kt` |
| Component contract (naming, styling, a11y, tests) | `docs/COMPONENT_SPEC.md` |
| Usage of every component with controls | `showcase/src/commonMain/kotlin/tech/kloos/kompound/showcase/<package>/` |
| Graph editor, state, nodes | `kompound-graph/src/commonMain/kotlin/tech/kloos/kompound/graph/` (`KNodeGraph.kt`, `KGraphState.kt`, `KNode.kt`, `model/`) |
| Graph engine and traces | `.../graph/runtime/` (`GraphEngine.kt`, `NodeRun.kt`, `Trace.kt`, `Pins.kt`) |
| Graph JSON | `.../graph/serialization/` (`GraphJson.kt`, `ValueJson.kt`, `Json.kt`) |
| Graph inspector | `.../graph/inspector/` |
| Architecture decisions | `docs/adr/` (0001 styling, 0004 node graph) |
| Release and publishing | `docs/RELEASING.md` |
| Sample consumer project | `samples/consumer` |

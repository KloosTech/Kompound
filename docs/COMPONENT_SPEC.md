# Kompound Component Specification (v0.1 draft)

The contract every component must satisfy to be accepted into `:kompound`.
Companion to `SPEC.md`. Based on the AndroidX Compose API Guidelines, Material 3 conventions and KMP library practice.

Keywords: **MUST**, **SHOULD**, **MAY** as in RFC 2119.
Each rule has an ID (`C-xxx`) and an **enforcement** tag so the checklist is mostly automated, not honour-system:

- `[build]` compiler / explicit API / KSP processor error
- `[lint]` detekt / ktlint / compose-rules / custom lint fails CI
- `[test]` generated or shared test fails CI
- `[review]` human PR review (checked via PR template)

Open items marked **(verify S4)** depend on spike S4 (Compose Styles API / M3 availability in Compose Multiplatform).

---

## 1. Scope and definition

A **component** is a public `@Composable` (or small family of them, e.g. `KTabRow` + `KTab`) in `:kompound` that is part of the supported API, has a demo in `:showcase`, and appears in the catalog.

Tiers:
| Tier | Meaning | Examples |
|------|---------|----------|
| Primitive | Thin, stylable building block, no opinion on layout | `KSurface`, `KIcon` |
| Component | Complete widget with states | `KButton`, `KTextField`, `KChip` |
| Pattern | Composition of components with behaviour | `KSearchBar`, `KFormField` |

Do not wrap an M3 component 1:1 with no added value. A component MUST justify existence: added design, behaviour, accessibility, stylability, or cross-platform adaptation. `[review]`

## 2. Naming and package

- **C-001** Public composables use prefix **`K`** (`KButton`) to avoid clashes with `androidx`/M3 names in consumer imports. `[lint]` (Decided.)
- **C-002** PascalCase noun for components that emit UI; camelCase verb/noun phrase for non-emitting `@Composable` functions that return values (`rememberKButtonState`). `[lint]` (compose-rules)
- **C-003** Package: `tech.kloos.kompound.<category>.<component>`; one component family per package; category = value from `Category` enum. `[lint]`
- **C-004** File name = primary declaration name. One component family per file group. `[lint]`
- **C-005** Defaults object named `<Component>Defaults`; state holder `<Component>State`; style type `<Component>Style` (see §3). `[lint]`
- **C-006** Everything not meant for consumers is `internal`. Anything shared across components but not public uses `@KompoundInternalApi` opt-in. `[build]`

## 3. Styling model (core of Kompound): Styles API first

Kompound is built **on the Compose Styles API** (`androidx.compose.foundation.style`: `Style`, `StyleScope`, `StyleState`, `Modifier.styleable(...)`), not on wrapped M3 components. Names below follow Google's published API; exact package/signatures and experimental status are **verified in spike S4, which gates everything else** (SPEC §8).

### 3.1 Concepts
- **`Style`**: declarative bundle of visual properties (background, shape, border, padding, content colour, text style, size, alpha/scale/translation) for any foundation composable.
- **`StyleScope`**: lambda receiver where properties are declared, including nested state blocks (`pressed { }`, `hovered { }`, `focused { }`, `disabled { }`, `selected { }`, `checked { }`).
- **`StyleState`**: carries interaction + app state (enabled, selected, toggle state, error, loading) into the style layer.
- Style changes are applied in the **draw/layout phases**, avoiding recomposition for state-driven visual change (press scale, colour transitions). Claim comes from Google's design; S4 measures it (recomposition counter test) on Android, iOS, desktop.

### 3.2 Architecture
Layers, highest priority wins:

1. **Per-call `style` parameter**: every visual component has `style: Style = KButtonDefaults.style()`. Consumer-provided style is merged **over** the default (`default.then(consumer)` semantics; consumer wins on conflict, unspecified properties inherit).
2. **Subtree style provider**: `CompositionLocal` per component family (`LocalKButtonStyle`) so an app restyles all buttons in a subtree.
3. **`<Component>Defaults.style()`**: reads tokens, returns the base `Style`. Variants are separate functions (`KButtonDefaults.filledStyle()`, `.outlinedStyle()`, `.textStyle()`) that **compose** from a shared base via `then`.
4. **Tokens**: `KompoundTheme` supplies tokens (decided: M3 roles plus a small extension set, no parallel colour system: `KompoundColors` success/warning/info with containers, `KompoundSpacing`, `KompoundMotion`, `KompoundStateLayer`). **M3's `ColorScheme`, `Typography`, `Shapes` are the token source** (so apps already themed with M3 work unchanged); Kompound adds only tokens M3 lacks (spacing scale, motion, extra semantic colours, state-layer opacities). No parallel colour system.

Component bodies are built from **foundation** primitives + `Modifier.styleable`, not from M3 `Button`/`Card`. M3 is a **token and theming dependency**, not a widget dependency. (This refines the earlier "M3 as foundation" decision: M3 stays the design-token foundation; widgets use the Styles API. Reason: wrapped M3 widgets cannot be restyled to the same depth and bring their own indication/animation machinery.) Where Styles API lacks a needed property, fall back to a documented modifier inside the component, never to a public parameter that leaks it.

### 3.3 Rules
- **C-010** No hard-coded colours, text styles, shapes, `.dp` spacing in component bodies; styles are built in `<Component>Defaults` from tokens. `[lint]` (custom detekt rule)
- **C-011** Every visual property a designer would plausibly change is expressible through the `style` parameter without copying the component. `[review]`
- **C-012** Public visual component MUST have `style: Style` parameter (placed after `modifier`) defaulting to `<Component>Defaults.style()`. `[lint]`
- **C-013** State-dependent visuals (pressed, hovered, focused, disabled, selected, checked, error, loading) are declared **inside the Style** via state blocks, not through `if (isPressed)` branches or `animate*AsState` in the component body. `[lint]` + `[review]`
- **C-014** Consumer style merges over defaults; documented and tested (test: override one property, others keep default; override a state block, others keep default). `[test]`
- **C-015** `Style` params and `Defaults` functions are the **only** public styling surface. Do not also add per-property `colors`/`shape`/`elevation` params (avoids two competing systems). Exception: `contentPadding`-like params that affect layout contract and are required by M3-familiarity may exist if the Styles API cannot express them. `[review]`
- **C-016** Interaction wiring standard: component takes `interactionSource: MutableInteractionSource? = null`, creates one with `remember` if null, and feeds it into `StyleState`. No legacy ripple unless the style asks for it (`LocalIndication` set to none inside styled components; press feedback comes from the Style). `[test]`
- **C-017** Defaults render correctly under default M3 theme, dark theme, custom `ColorScheme`, no dynamic colour (desktop/iOS). `[test]` (catalog theme matrix)
- **C-018** Text via style's text properties / `LocalTextStyle`; no direct font family in bodies. `[lint]`
- **C-019** Content colour via the style's `contentColor`; text inside components MUST be `KText` (or another `styleable` text), never M3 `Text`, because M3 `Text` ignores inherited Style text properties (ADR 0001 addendum). Icons tint via `KIcon` (to be added) for the same reason. `[lint]` (forbid `androidx.compose.material3.Text` import in `:kompound`)
- **C-016a** Modifier order inside a component: behaviour modifiers (`clickable`, `toggleable`, `selectable`, focus) wrap `styleable`, i.e. `modifier.clickable(...).styleable(...)`. Otherwise the style's padding and min size fall outside the hit area. Covered by a mandatory hit-area test (`size >= style minimum`). `[test]`
- **C-019b** Components call `KompoundStyles.ensureEnabled()` (turns on `ComposeFoundationFlags.isInheritedTextStyleEnabled`). `[review]`
- **C-019a** Experimental Styles API types are not leaked in a way that breaks consumers when Google changes them: Kompound re-exports through `typealias`/thin wrappers only where API is Stable; while Experimental, public API using `Style` is itself `@KompoundExperimentalApi`-gated at **library level** by an opt-in propagated once (module-level opt-in), documented in README. `[build]`

### 3.4 If S4 fails (fallbacks, in order)
1. Styles API present in AndroidX but not yet in the CMP-published artifacts → pin the CMP/AndroidX version that has it, or consume the artifact directly if it is multiplatform.
2. API only available on Android → Kompound defines a **thin `KStyle` abstraction** with the same shape (state blocks, merge via `then`) implemented on top of `Modifier.Node` + `InteractionSource`; swapped for Google's `Style` via typealias later. Public signatures use `KStyle` so migration is source-compatible.
3. API unstable/removed → keep `KStyle`; Styles API remains an internal implementation detail.
Decision recorded in `docs/adr/0001-styling.md` after S4.

## 4. API design

- **C-020** Signature order: required params → `modifier: Modifier = Modifier` (first optional) → optional params with defaults → trailing content lambda. `[lint]`
- **C-021** Exactly one `modifier` param, applied to the **outermost** layout node, once. `[lint]` (compose-rules)
- **C-022** `modifier` is the first optional parameter. `[lint]`
- **C-023** Content slots are `@Composable` lambdas with explicit receiver scopes where layout matters (`RowScope`). Prefer slots over `String`/`Boolean` flag explosion; offer `String` convenience overload only if it is a trivial wrapper. `[review]`
- **C-024** Event callbacks: `onXxx: (T) -> Unit`, named in present tense (`onClick`, `onValueChange`), invoked on main thread, never called during composition. `[lint]`
- **C-025** State hoisting: controlled by default (`value` + `onValueChange`); optionally provide `rememberXxxState` for uncontrolled convenience. State holder is `@Stable`, creatable outside composition, has `Saver` where it survives config/process change. `[review]` + `[test]`
- **C-026** Parameters are stable types or `@Immutable`/`@Stable`; no `MutableState`, `List<T>` of unstable T without `ImmutableList`/wrapper policy. Compose compiler metrics must show component **skippable**. `[test]` (compiler-report check task)
- **C-027** No default argument that allocates per recomposition without `remember`. `[lint]`
- **C-028** Boolean params permitted only for independent flags (`enabled`); mutually exclusive modes use enum/sealed class. Max 12 parameters excluding modifier+content; beyond that split or group in style/config object. `[lint]`
- **C-029** `enabled: Boolean = true` for interactive components; disabled component not focusable/clickable and announces disabled state. `[test]`
- **C-030** Nullable parameter means "feature absent", never "use default". `[review]`
- **C-031** Public API explicit: visibility + return types (`explicitApi()` strict). `[build]`
- **C-032** Binary compatibility: public signature changes tracked by ABI validator; use overloads + `@Deprecated(ReplaceWith)` not changed signatures; Compose-generated `$default` synthetic overload break risk acknowledged → add params only via new overload with `@Deprecated` old one hidden. `[build]`
- **C-033** Prefer `Modifier` extension for behaviour-only additions (`Modifier.kShimmer()`), implemented with `Modifier.Node` API, not `composed {}`. `[lint]`

## 5. Behaviour and state

- **C-040** All interactive states handled (declared via Style state blocks, C-013) and visually distinguishable: enabled, disabled, pressed, hovered (pointer platforms), focused (keyboard), selected/checked, error, loading (where applicable). `[test]` + demo shows each
- **C-041** Uses `InteractionSource`; hover/focus work on Desktop and iPad/Android with pointer/keyboard; pressed ripple/indication uses `LocalIndication`. `[test]`
- **C-042** Minimum touch target 48dp (Android/iOS) via `minimumInteractiveComponentSize()`; desktop may use compact density but only via an explicit density token. `[test]`
- **C-043** Survives configuration change / process death: `rememberSaveable` where user-entered state exists. `[test]` (Android + desktop state restoration test)
- **C-044** No side effects in composition body; use `LaunchedEffect`/`DisposableEffect`/`rememberUpdatedState` correctly; keys correct. `[lint]`
- **C-045** State animations are Style-driven (draw/layout phase); motion tokens shared; respect system "reduce motion"/animation scale where platform exposes it; infinite animations MUST pause when off-screen/disabled. `[review]`
- **C-046** Component handles empty, very long, and RTL content without crash or clipping (ellipsis/wrapping rules documented). `[test]` (demo stress variants rendered in smoke test)
- **C-047** Never block main thread; no platform I/O inside component. Image loading is consumer concern (slot) unless component explicitly documented. `[review]`

## 5a. Platform behaviour

- **C-050** Code lives in `commonMain`. `expect/actual` only for genuine platform difference; each actual exists for **all** targets (android, ios arm64+sim, desktop, wasm if enabled). `[build]`
- **C-051** No `java.*`, `android.*`, `platform.UIKit.*` in `commonMain`. `[build]`
- **C-052** Platform adaptation (e.g. scrollbar on desktop, haptics on mobile, cursor icon on desktop) isolated behind `internal expect` helpers with sensible no-op fallback. `[review]`
- **C-053** Keyboard: all interactive components fully operable by keyboard (Tab, Shift+Tab, Space/Enter, arrows where role demands) on Desktop. `[test]`
- **C-054** Text input components: IME actions, autofill hints, `KeyboardOptions`, paste/selection work on all targets; no assumptions about soft keyboard presence. `[test]` (manual checklist for iOS)
- **C-056** Overlay components adapt to window size: dialogs and sheets use a bottom sheet on compact widths and a centred dialog (or side sheet) on medium and larger widths, desktop and web included; one component, one API, adaptive implementation. `[review]`
- **C-055** Safe-area / window insets: components never consume insets implicitly; edge-to-edge handled by consumer or explicit `windowInsets` parameter. `[review]`

## 6. Accessibility

- **C-060** Correct semantic `Role` (`Button`, `Checkbox`, `Switch`, `Tab`, `RadioButton`, …) via `Modifier.semantics`/standard modifiers (`clickable(role=…)`, `toggleable`, `selectable`). `[test]`
- **C-061** Every non-decorative visual has `contentDescription` param (required for icon-only components; no default empty string for meaningful icons); decorative uses `null`. `[lint]`
- **C-062** State exposed to semantics: `stateDescription`, `selected`, `disabled`, `error`, `progress`, `liveRegion` for status changes. `[test]`
- **C-063** Contrast: text ≥ 4.5:1, non-text UI ≥ 3:1 for **default** styles in light + dark. `[test]` (token contrast test over default theme)
- **C-064** Respect font scale up to 200% without clipping or lost functionality; layouts use `sp` for text and flexible containers. `[test]` (smoke test at 2.0 fontScale)
- **C-065** Focus order logical; visible focus indicator; no keyboard trap; custom focus uses `Modifier.focusable`/`focusProperties`. `[test]`
- **C-066** Merge semantics for composite items (`mergeDescendants = true`) so screen readers read one item. `[review]`
- **C-067** Touch target and spacing per C-042. Colour never sole means of conveying state. `[review]`
- **C-068** Test with TalkBack (Android) and VoiceOver (iOS) once per new component *Pattern/Component* tier; record in PR. `[review]`

## 7. Performance

- **C-070** Skippable per compiler report (C-026). `[test]`
- **C-071** Lazy lists used for unbounded content; components inside `LazyColumn` items keep keys stable. `[review]`
- **C-072** No allocation in hot paths (draw / layout / pointer input): use `drawBehind`/`Modifier.Node` + lambda-based `graphicsLayer {}` / `offset {}` for animated values to avoid recomposition. `[review]`
- **C-073** State reads deferred to the latest phase possible. `[review]`
- **C-074** Benchmarks (optional) for Pattern tier with list usage: macrobenchmark on Android; not blocking. `[review]`

## 8. Dependencies and resources

- **C-080** Allowed dependencies in `:kompound`: Kotlin stdlib, Compose Multiplatform (runtime, foundation, ui, material3, animation, resources), `kompound-annotations`. Anything else requires a spec change. `[build]` (dependency allowlist task)
- **C-081** Resources via Compose Resources only (`composeResources/`), package `tech.kloos.kompound.resources`; resource names prefixed `kompound_`. `[build]`
- **C-082** Strings user-visible by default come from resources and are localisable; consumer can override via param. At minimum `values/` (en); RTL tested. `[lint]`
- **C-083** Icons: components accept icons as slots (`ImageVector`, `Painter` or a composable); Kompound ships no icon set beyond a few internal vectors it needs itself (chevron, check, close), written as code, never `material-icons-extended`. Icons use `KIcon`; its tint falls back to `LocalKContentColor`, which components provide (icons do not inherit Style `contentColor`, see ADR 0001 addendum). `[build]`
- **C-084** Third-party assets (fonts, icons, images) carry Apache-2.0-compatible licence, recorded in `THIRD_PARTY_NOTICES.md` with source and licence. `[review]`

## 9. Demo (required for catalog listing)

- **C-090** Exactly one `@KompoundDemo` per component family in `:showcase`, mirror package path. `[build]` (KSP: error if public component lacks demo — via component marker `@KompoundComponent` on the composable; processor cross-checks)
- **C-091** Annotation metadata complete: `id` (stable, unique), `title`, `description` (≤ 160 chars, one sentence), `category`, ≥ 2 `tags` (normalised), `since`, `status`. `[build]`
- **C-092a** Demo includes a **style playground**: toggles for forced states (pressed/hovered/focused/disabled/selected/error), a live custom `Style` override, and theme switch (light/dark/custom scheme). `[review]`
- **C-092** Demo shows: default, every state (C-040), every variant, with content slot examples, long text, RTL, disabled, error. Use `DemoScope` controls for interactive properties. `[review]`
- **C-093** Demo uses only public API of `:kompound` (no `internal` / `@KompoundInternalApi`). `[build]`
- **C-094** Demo is deterministic (no random/time-based output) so screenshot tests are stable. `[test]`
- **C-095** Registry smoke test renders every demo on desktop under: light, dark, RTL, fontScale 2.0 without exception. `[test]` (generated)
- **C-096** `@Preview` annotated function for IDE preview may be added alongside; not required. `[review]`

## 10. Documentation

- **C-100** KDoc on every public declaration: summary sentence, `@param` for each parameter, `@sample` linking to demo/snippet, notes on state/accessibility. `[lint]`
- **C-101** KDoc documents default behaviour, thread/threading expectations, and what is NOT supported. `[review]`
- **C-102** CHANGELOG entry (user-facing) under `Unreleased`. `[lint]` (CI check on PR)
- **C-103** Experimental API annotated `@KompoundExperimentalApi` (RequiresOptIn) and `status = Experimental` in demo. `[build]`

## 11. Testing

Minimum for **every** component:
- **C-110** Unit/UI test (`runComposeUiTest`, in `commonTest` where possible) covering: renders, click/interaction, disabled, state change, semantics (role/state/description). `[test]`
- **C-111** Test for each public state holder + `Saver` round trip. `[test]`
- **C-112** Registry smoke test (C-095) passes. `[test]`
- **C-113** Screenshot tests (Roborazzi desktop) for default + each state, light/dark: phase 2, required once enabled. `[test]`
- **C-114** `apiCheck` passes (ABI dump updated intentionally). `[build]`
- **C-115** Line coverage target ≥ 80 % for component logic (not enforced as gate in v0.1, reported). `[review]`

## 12. Versioning, lifecycle and deprecation

- **C-120** Lifecycle: `Experimental` → `Beta` → `Stable` → `Deprecated` → removed. Status stored in demo annotation and KDoc.
- **C-121** `Stable` components follow SemVer: breaking change only in major release; new optional params via overload pattern (C-032).
- **C-122** `Experimental`/`Beta` may break in minor releases, MUST be opt-in annotated.
- **C-123** Deprecation: `@Deprecated(level = WARNING, replaceWith)` for ≥ 1 minor release, then `ERROR`, then removal in next major.
- **C-124** Renaming `id` requires `aliases` in `@KompoundDemo` (keeps deep links working).
- **C-125** Raising min SDK/iOS/JDK or bumping Compose/Kotlin minimum = documented in release notes, minor (pre-1.0) / major (≥1.0).

## 13. Security, licensing, privacy

- **C-130** No network, file system, analytics, or permissions use in components. `[review]`
- **C-131** No reflection-based code on common targets (breaks iOS/wasm, R8). `[build]`
- **C-132** Consumer R8/proguard: library ships `consumer-rules.pro` only if needed (none expected). `[review]`
- **C-133** Contributions licensed Apache-2.0 (DCO sign-off `Signed-off-by`). `[review]`

## 14. PR checklist (to be copied into `.github/pull_request_template.md`)

```
## New / changed component checklist
- [ ] Component justified (C §1) and tier chosen
- [ ] Naming/package per §2
- [ ] Styling via Defaults + theme tokens, no hard-coded values (§3)
- [ ] API: modifier, slots, state hoisting, stability (§4)
- [ ] All states + keyboard + RTL + font scale (§5, §6)
- [ ] Platform code only via expect/actual with all actuals (§5a)
- [ ] Accessibility semantics + screen-reader test noted (§6)
- [ ] No new dependencies; resources via Compose Resources (§8)
- [ ] @KompoundDemo complete, shows all states (§9)
- [ ] KDoc + CHANGELOG (§10)
- [ ] Tests added; apiCheck updated (§11)
- [ ] Lifecycle status set (§12)
```

## 15. Enforcement implementation plan

| Mechanism | Covers | Phase |
|-----------|--------|-------|
| `explicitApi()`, ABI validator, dependency allowlist task | C-006, C-031, C-032, C-080 | 1 |
| `kompound-processor` (KSP) | C-090, C-091, C-093, C-124, id uniqueness, `@KompoundComponent` ↔ demo pairing | 1 |
| detekt + compose-rules + custom detekt rules (no hard-coded colour/dp, naming prefix, `style` param present, no `if(isPressed)`) | C-001–C-005, C-010, C-012, C-013, C-020–C-028 | 1 |
| Generated registry smoke test (light/dark/RTL/fontScale) | C-014, C-046, C-064, C-095 | 1 |
| Per-component UI tests + semantics assertions | C-029, C-040–C-043, C-060–C-065 | 1 (template) |
| Compose compiler metrics check task | C-026, C-070 | 2 |
| Roborazzi screenshots | C-113 | 2 |
| Contrast test over default tokens | C-063 | 2 |
| PR template + CODEOWNERS | all `[review]` | 1 |
| Scaffold task `./gradlew newComponent --name=KButton --category=Buttons` generating component, defaults, demo, test, KDoc skeleton | lowers barrier | 1 |

## 16. Open items

1. Styles API adoption (S4) — determines §3 layer 4.
2. Screenshot test tool choice (Roborazzi vs Paparazzi vs Compose desktop golden) — decide in phase 2.
3. Localisation scope beyond English.

Resolved: prefix `K` (C-001); `kompound-annotations` is published (consumers can annotate own demos).

## 17. Foundation components (Wave 0)
`KompoundTheme` + tokens, `KIcon`, `KSurface`, `KDivider` are the base for later components. `DemoScope` controls (`textControl`, `boolControl`, `choiceControl`, `floatControl`) let every demo expose its states; the catalog renders them and the generated smoke test renders every demo under light, dark, RTL and 200% font scale (C-095).

# ADR 0001: Styling model – Compose Styles API

Status: accepted (spike S4, 2026-10-02)

## Context
COMPONENT_SPEC §3 builds Kompound on the Compose Styles API (`androidx.compose.foundation.style`). Spike S4 verified it in a throwaway multi-target project.

## Spike setup
Kotlin 2.4.20, Compose Multiplatform 1.12.1 (foundation 1.12.1), AGP 9.4.1 (`com.android.kotlin.multiplatform.library`), Gradle 9.8.0, JDK 23 / toolchain.
Targets: `jvm("desktop")`, `iosSimulatorArm64`, `wasmJs`, `android` (compileSdk 36, minSdk 24).

## Findings
| Check | Result |
|-------|--------|
| `Style`, `styleable`, `rememberUpdatedStyleState` available from `commonMain` | Yes |
| Compiles for desktop, iosSimulatorArm64, wasmJs, android | Yes (compileSdk 36 is enough; docs' "compileSdk 37" not required here) |
| State blocks (`pressed {}`, `disabled {}`) | Yes, but they are **extension functions needing explicit imports** (`androidx.compose.foundation.style.pressed`) |
| Consumer `style` merged over component default via `Modifier.styleable(state, default, custom)` | Yes (API shape); override semantics test still to write in Phase 1 |
| Press state change does not recompose component content | Confirmed on desktop (content `SideEffect` count unchanged after emitting `PressInteraction.Press`) |
| Experimental | Yes: needs opt-in `androidx.compose.foundation.style.ExperimentalFoundationStyleApi` |
| Material 3 component styling | Not supported by Styles API (matches decision: M3 = tokens only) |

Runtime follow-up: the same recomposition test **also passes on the iOS simulator (iosSimulatorArm64Test)**. Android emulator (API 37) run failed on test tooling, not the API (`NoSuchMethodException: android.hardware.input.InputManager.getInstance` from Espresso, still present with espresso-core 3.7.0) – retry with a newer Espresso/AndroidX Test or an API 36 image. Wasm runtime not run (needs browser).

Not yet verified: press visuals actually changing colour (only recomposition was asserted), `animate` blocks, Android + wasm runtime.

## Decision
Adopt Styles API as primary styling primitive (path 1 of COMPONENT_SPEC §3.4). No `KStyle` fallback needed now.
Mitigation for experimental status: module-wide opt-in set in the convention plugin; pin Compose/Kotlin; public API using `Style` marked per C-019a; keep `style: Style = Style` defaults.

## Consequences
- Consumers need Compose Multiplatform / foundation >= 1.12 and must opt in to `ExperimentalFoundationStyleApi`.
- Library must track Google API changes (the API was already reworked once between alphas); upgrade deliberately, never via auto-merge.
- Add a CI test per component for override/merge semantics and zero-recomposition on state change.

## Addendum: findings from Phase 1 (runtime on Android API 37 emulator)
- Styles API renders correctly on Android: background, shape, content padding, `pressed {}` (colour change confirmed by pixel diff while holding a touch) and `disabled {}`.
- **Inherited text style is behind a feature flag**: `ComposeFoundationFlags.isInheritedTextStyleEnabled` (default false). Without it `contentColor`/`textStyle` in a Style do not reach children. Kompound wraps this in `KompoundStyles.ensureEnabled()`, called by components.
- **Material `Text` does not read inherited style**; `BasicText` with a plain modifier did not either. Text must use `Kompound` `KText` (BasicText + `Modifier.styleable`) to inherit. Consumers using M3 `Text` inside Kompound components must use `KText` or set colours explicitly.
- `contentPadding(h, v)` and state blocks (`pressed`, `disabled`) are extension functions that need explicit imports.

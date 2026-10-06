# ADR 0008: Android UI testing with Maestro (plan)

Status: **accepted as a plan** (decisions of 2026-10-06 are in section 11); nothing here is built yet; this is the plan for a Maestro framework that tests every Kompound component on a real Android
device, one component at a time, improving the framework as we go. Sources: the Maestro documentation (what-is-maestro, Jetpack Compose,
selectors, CLI commands and options, workspace and tags, reports and artifacts, hooks, devices, wait commands, known issues; page index at
`https://docs.maestro.dev/llms.txt`) and the current state of this repository (checked on 2026-10-06).

## 1. Why, and what Maestro adds to what we have

Today the library is tested by Compose UI tests on desktop and the iOS simulator (`runComposeUiTest`: semantics, keys, colours), a catalog smoke
test (every demo renders in light, dark, RTL, 200 % font) and an automated accessibility audit of every demo. None of that runs on an Android
device. What only a device shows: the real soft keyboard and IME, touch slop and fling, system gestures and edge-to-edge insets, popups and
dialogs as real windows, font and display scaling, process death, performance on real hardware, and what Android's accessibility service actually
exposes. Maestro drives the device "at arm's length" through that accessibility layer (`piloting the device, not the app`), so a Maestro flow
sees what a user (and a screen reader) sees, with no test code inside the app.

Maestro **complements** the existing tests and does not replace them: component logic stays in Compose tests (fast, deterministic, no device),
Maestro owns "does it work on a real Android device as a user would use it".

## 2. What the Maestro docs tell us that shapes the design

| Fact (from the docs) | Consequence for us |
|---|---|
| Flows are YAML; elements are found through the accessibility tree by `text` (regex, also matches accessibility labels), `id` (Android resource id), `index`, `point`, plus relative (`above`, `below`, ...), state (`enabled`, `checked`, `focused`, `selected`) and dimension matchers. | Our components expose `contentDescription`, text and state; tests assert on those. Dimension matchers let us assert touch-target size (WCAG 24 dp, our 48 dp rule). |
| For Compose, `Modifier.semantics { testTagsAsResourceId = true }` makes `Modifier.testTag` visible as `id:`. | The catalog sets it once at the root (debug and test builds) and tags the catalog's own chrome and controls with stable ids. Components themselves are not tagged (a library must not impose ids); the harness (section 4) addresses them by text and description. |
| Default discovery runs only flows at the top level of a directory; subfolders need `flows:` globs in `config.yaml` (`"**"`, `"!_lib/**"`). | Shared subflows live in `_lib/` and are never run as tests. |
| Tags in the flow header; `--include-tags` / `--exclude-tags` (OR within a flag, include then exclude), also in `config.yaml` (`includeTags`, `excludeTags`); `executionOrder`. | One tag vocabulary (section 5) drives smoke, per-component, environment and quarantine runs. |
| `maestro test` flags: `--device`/`--udid`, `--format JUNIT|HTML|HTML-DETAILED`, `--output`, `--test-output-dir`, `--debug-output`, `--env`, `--config`, `--shard-split N`, `--shard-all N`, `--continuous`. | A thin wrapper script (section 6) passes these; JUnit goes to CI, HTML for humans, shards for the fast lane. |
| Artifacts per flow: `commands.json`, `logs/` (device logs, crashes, ANR), `screenshots/` (failing steps), `screen-hierarchy/`, user screenshots and recordings. | CI uploads them on failure; the hierarchy dump is the first thing to read when a selector fails. |
| `runFlow` (with `env:` and `when:`), `onFlowStart` / `onFlowComplete` hooks, `runScript` (JavaScript), `repeat`, `retry`. | Page-object style subflows with parameters; hooks only for cheap setup (they run for every flow). |
| Waiting is built in: assertions poll; `extendedWaitUntil`, `waitForAnimationToEnd`; `retryTapIfNoChange`. | The rule: **no sleeps**. Assertions first, explicit realistic timeouts second. |
| `assertScreenshot` compares against a reference image (`path`, `cropOn`, `thresholdPercentage`, default 95 %); baselines come from `takeScreenshot`. | Visual regression is possible, but only on a pinned device image; see phase 5 and the risks. |
| `inputText` is ASCII only on Android; WebView content may be invisible; `pressKey` covers a fixed key set; "Reset to default values" and permission monitoring quirks on some physical devices (Redmi, Oppo, Realme); Java 17 or 21 required. | Text tests use ASCII; keyboard-navigation matrix stays in Compose tests; the device lane documents tested phones; our local default JDK is 23, so Maestro runs under a pinned JDK (sdkman or `JAVA_HOME` in the wrapper). |

Not yet verified (the spike in phase 0 settles them): whether `launchApp` accepts intent `arguments` on Android (we plan on a deep link via
`openLink` anyway, which is documented), how `assertScreenshot` stores and updates baselines, and which gestures (`swipe` with percent points,
`longPressOn`, `doubleTapOn`) are precise enough for sliders, split panes and the colour square.

## 3. State of the repository

- Catalog Android app: `catalog/androidApp` (`tech.kloos.kompound.catalog`, minSdk 24, targetSdk 37), one `MainActivity` that shows `KompoundCatalog()`; no deep link, no test tags, no `testTagsAsResourceId`.
- The catalog lists **68 demos** (23 inputs, 7 display, 7 overlays, 6 feedback, 5 each buttons, layout and graph, 3 data, 2 each foundations and navigation, 1 animation and 1 utilities). A demo is a composable with live controls (`boolControl`, `choiceControl`, `floatControl`, `textControl`), shown in a preview card with a controls card beside it; `CatalogState.selectedId` picks it. The registry (`KompoundAllDemos`) is generated by KSP.
- Local machine: `adb` and `emulator` present, AVDs `Kompound_API37` and `Pixel_9_Pro`, JDK 23, **Maestro not installed**. CI (`ci.yml`) builds the Android catalog (`assembleDebug`) but runs no instrumented or Maestro tests; `catalog-release.yml` builds the release APK on every merge.

## 4. Decisions

### 4.1 Test the catalog app through a test harness mode, not through its chrome
Flows should not click through a sidebar to reach a component (slow, brittle, tests the catalog instead of the library). The catalog app gets:

1. **A deep link** `kompound://demo/<qualifiedId>?theme=light|dark&font=1.0..2.0&rtl=true|false&density=compact|comfortable|spacious&bare=true&controls=Name%3Dvalue,...`. `openLink` (documented, works on every Android) launches straight into one demo in one environment. Handled in `MainActivity` (Android only), mapped to a plain `CatalogLaunch` state in common code so desktop and iOS can reuse it later.
2. **Bare mode**: only the demo's preview and its controls, no sidebar, no top bar, no animation-heavy chrome; the preview is on a fixed surface at a fixed size. This is the unit a flow tests and, later, screenshots.
3. **Controls are operable by Maestro**: each control row has a stable `testTag` (`control:<name>`) and an accessible label equal to its name, so a flow can toggle `Enabled` or set `Count` by id. The `controls=` query parameter pre-sets them so most flows need no taps to reach a state.
4. **`testTagsAsResourceId = true` at the root of the Android catalog** (always on in debug and the `maestro` build variant; off in release).
5. **A harness readiness signal**: a node with id `harness:ready` and the demo id as its description appears once the demo composed and the first frame settled, so every flow starts with one `assertVisible: id: harness:ready` instead of waits.
6. **A `maestro` build type** (`debuggable = false`, debug-signed, harness enabled, minify off) so tests run against an optimised build, like users; plain `debug` stays for development.

The library components stay free of test ids. The flows address them by visible text and content description, which is also the accessibility contract we want to guard.

### 4.2 Repository layout

```
maestro/
  config.yaml                  workspace: flows globs, tags policy, output dir
  README.md                    how to run (emulator, physical device, CI), conventions
  flows/
    _lib/                      subflows, never run as tests (excluded by globs)
      open-demo.yaml           openLink + wait for harness:ready   env: DEMO, THEME, FONT, RTL, DENSITY, CONTROLS
      set-control.yaml         set a control by name and value      env: NAME, VALUE
      assert-no-crash.yaml     app still in foreground, no ANR dialog
      shot.yaml                takeScreenshot with the naming scheme
      env/                     dark.yaml, rtl.yaml, font2.yaml, compact.yaml (environment wrappers)
    components/
      <category>/<demo-id>/
        00-smoke.yaml          renders, no crash, key text present   (tag: smoke)
        10-interact.yaml       the main behaviour
        20-states.yaml         disabled, error, empty, loading via controls
        30-env.yaml            dark, RTL, font 200 %, density (runs the interact core through wrappers)
        40-visual.yaml         screenshots (tag: visual)
    suites/                    cross-cutting: a11y-targets.yaml, orientation.yaml, process-death.yaml
  baselines/                   reference screenshots (phase 6; storage decided then)
  scripts/
    maestro.sh                 the one entry point (section 6)
    device-setup.sh            normalise a device (section 7)
    scaffold.sh                create the skeleton flows for a demo id from the registry
    coverage.sh                fail when a demo has no smoke flow (ratchet allowlist)
```

### 4.3 Conventions (enforced by review and, where possible, by the coverage script)
- **Selectors, in this order**: `id` (harness and catalog chrome only), then visible `text`, then `description` (accessibility label). Never coordinates, except gestures (`swipe`, `point` percentages) where the flow says why.
- **No `sleep`-style waits**. Assertions poll; `extendedWaitUntil` with an explicit timeout for known slow work; `waitForAnimationToEnd` only before a screenshot or a gesture on something that animates in.
- **Every flow starts through `_lib/open-demo.yaml`** and ends with `assert-no-crash`; the flow header declares `appId`, `name`, `tags`.
- **Tag vocabulary**: `smoke` (one fast flow per demo, the PR gate), `component:<demo-id>`, `category:<name>`, `env` (environment matrix), `visual`, `a11y`, `gesture`, `slow` (over 60 s), `quarantine` (known flaky, excluded from gates, tracked), `wip`.
- **Names are stable ids**: `components/inputs/color.picker/10-interact.yaml`; the JUnit class name is the demo id (`junitClassname` property).
- **Determinism**: ASCII text only, fixed locale `en-US`, fixed time zone, animations off on the device, fixed font scale per flow via the deep link, no network.
- **One behaviour per flow**; shared steps become subflows with `env:` parameters (page-object style), never copy-paste.

### 4.4 Coverage as a rule, with a ratchet
`scaffold.sh <demo-id>` reads the demo's metadata and controls from the registry (the KSP processor also writes `demos.json`: id, title, category, controls with types and ranges) and creates the five flow files with the right deep links and TODO markers. `coverage.sh` compares `demos.json` with `flows/components/**`: a demo without `00-smoke.yaml` fails CI, except demos in a checked-in allowlist that may only shrink. This is the same discipline as `DemoAccessibilityTest.knownGaps`.

### 4.5 What each layer of a component test covers (the definition of done for a component)
1. **Smoke**: opens by deep link, harness ready, headline text present, no crash. Under 15 s.
2. **Interaction**: every user-visible behaviour in the demo: tap, long press, type, clear, select, toggle, open and dismiss popups and dialogs (including Back), scroll lists, drag sliders and dividers with `swipe`. Asserts on text, description and state (`checked`, `enabled`, `selected`, `focused`).
3. **States**: through `controls=` and control taps: disabled (assert `enabled: false`, taps do nothing), error, empty, loading, long text.
4. **Environment matrix**: the interaction core again in dark, RTL, font scale 2.0, `compact` density, and landscape (one wrapper flow per environment, so the matrix costs no copies).
5. **Accessibility**: names exist (`assertVisible` by description), state is exposed (`checked`, `selected`), and **touch targets** via dimension matchers (at least 48 dp for standard density, 40 dp compact, never below 24 dp).
6. **Visual** (phase 6): `takeScreenshot` baselines of the preview in light and dark, compared with `assertScreenshot` on the reference phone only.
7. **Keyboard**: arrow keys are outside `pressKey`'s set, so the keyboard matrix (`docs/KEYBOARD.md`) stays in the Compose tests; Maestro covers IME actions (Done, Next, Search) and Back, Enter and Backspace.

## 5. The component-by-component method, and how the framework improves

Work is one **component per pull request**, in an order that exercises new framework needs early, and **every PR may change the framework** with a line in `maestro/FRAMEWORK_LOG.md` (what was missing, what changed, which earlier flows were updated). A component is done when its layers in 4.5 exist (the gestures it does not need are marked not applicable), they pass 20 times in a row on the emulator, and the coverage script is green.

Pilot order (each row names the framework capability it forces):

| # | Component | Forces |
|---|---|---|
| 1 | `KButton` (smoke, tap, loading, disabled) | the harness itself, `open-demo`, `set-control`, JUnit output |
| 2 | `KTextField` / `KPasswordField` | IME, `inputText`, `eraseText`, focus state, error text, `hideKeyboard` |
| 3 | `KCheckbox`, `KSwitch`, `KRadioButton` | state selectors (`checked`), toggle controls |
| 4 | `KTabRow` | `selected` state, relative selectors, RTL wrapper |
| 5 | `KDialog`, `KBottomSheet`, `KMenu` | popup windows, `back`, dismiss on outside tap |
| 6 | `KSlider`, `KSplitPane` | `swipe` precision, percent points, drag helpers in `_lib` |
| 7 | `KDataTable`, `KTreeView`, lists | `scrollUntilVisible`, virtualization asserts, large data |
| 8 | `KColorPicker`, `KTimePicker`, charts | custom canvas widgets: gestures only, what semantics can and cannot say |
| 9 | the rest of Inputs, then Display, Feedback, Layout, Navigation, Overlays, Animation, Utilities | environment matrix at scale |
| 10 | Graph (`KNodeGraph` and friends) | pan and zoom gestures, pointer-first editor limits (see risks) |

Cross-cutting suites (`flows/suites/`) are added when the pilots have settled the framework: touch targets everywhere, orientation change, process death and restore (`killApp`, relaunch), font scale sweep, and a "monkey" smoke that opens every demo in a row to catch crashes.

## 6. Running tests: one entry point

`maestro/scripts/maestro.sh` wraps the CLI so local, CI and physical-device runs are the same command:

```
maestro/scripts/maestro.sh --tags smoke                       # the gate
maestro/scripts/maestro.sh --component color.picker           # one component, all layers
maestro/scripts/maestro.sh --tags env --device <serial>       # environment matrix on a given device
maestro/scripts/maestro.sh --shard 3 --tags smoke             # split across three connected devices
maestro/scripts/maestro.sh --visual --update-baselines        # refresh screenshots (reference phone only)
```

It checks the prerequisites (JDK 17 or 21, `maestro` version pinned in `.maestro-version`, `adb`, the reference phone attached), builds and installs the `maestro` build type only when the APK changed (hash of the build output), runs `device-setup.sh`, then `maestro test` with `--format junit --output build/maestro/report.xml --test-output-dir build/maestro/artifacts --debug-output build/maestro/debug` and the tag filters, and prints the failing flows with their screenshot and hierarchy paths. Exit codes are the CLI's.

## 7. Devices

**Reference device (decided): OnePlus 9 Pro, Snapdragon 888, Android 14 (API 34).** All development and all gates run on this phone first. Its
exact profile (OxygenOS build, `adb shell wm size`, `wm density`, refresh rate, font scale) is read in the phase 0 spike and recorded in
`maestro/devices.md`; flows never depend on pixel positions, so the profile only matters for screenshots and speed. Consequences:
- **The minSdk edge (API 24) and API 37 (our `targetSdk`) are not covered for now.** A later CI emulator mirrors the phone (API 34) rather than the newest image.
- **Visual baselines, if we adopt them, are recorded on this phone** (the one reference device), with display size, font scale, theme and refresh rate pinned by `device-setup.sh --check`. A different phone never updates them.
- **OxygenOS belongs to the Oppo family**, which the Maestro known-issues page lists for "unable to clear state" and driver activation problems: expect to need "Disable permission monitoring" and "Verify apps over USB" off in developer options (both are in `device-setup.sh`'s checklist), avoid `clearState` (use `stopApp` plus a harness reset), and allow USB installs ("Install via USB") for the driver and the test build.
- **Nothing needs an emulator to start**; `Kompound_API37` stays as a fallback for people without the phone.

**Normalisation (`device-setup.sh`, idempotent, run before every session).** `adb shell settings put global window_animation_scale 0`, `transition_animation_scale 0`, `animator_duration_scale 0`; stay awake while charging and screen timeout max; rotation locked to portrait unless a flow rotates; navigation mode and font scale reset; `adb shell cmd locale` to `en-US`; disable "Verify apps over USB" and enable "Disable permission monitoring" where the device offers them (the known-issue workarounds); install the APK with `adb install -r -g`; dismiss the keyboard and notification shade. It prints what it changed, and a `--check` mode fails when the device deviates (used by CI).

**Speed on the phone.** The goal is speed (a fast Snapdragon 888 on USB 3 is the main lane, not a later one): a fast phone on USB 3, animations off, screen on, pre-installed APK, then
- **deep links skip navigation** (the largest saving: no sidebar, no scrolling, no search),
- **no per-flow reinstall**: `launchApp` with `stopApp` instead of `clearState` unless the flow tests persistence,
- **`--shard-split N` across several attached devices** (a phone plus emulators) for the full suite,
- **`--continuous` while developing** a component, so a saved flow re-runs at once,
- a **device registry** in `maestro/devices.md` (model, Android version, quirks, the workaround flags above, measured full-suite time), starting with the OnePlus 9 Pro; more phones are added only with their quirks and a measured time.
The same `maestro.sh` runs everywhere; `--device <serial>` selects the phone when several are attached.

**CI (later, and not on the phone).** GitHub-hosted runners cannot use the phone, so until a self-hosted runner exists the gate is **local**: a pull request that changes `kompound/`, `catalog/` or `maestro/` runs `maestro/scripts/maestro.sh --tags smoke` on the phone before merge and pastes the one-line summary the script prints (flows, passed, duration, device, build hash) into the PR. When that habit is stable, `maestro.yml` is added: a Linux runner with KVM and an **API 34 emulator** (matching the phone's OS) running the smoke tag on pull requests (path filtered) and the full suite nightly, JUnit in the job summary, artifacts (`commands.json`, logs, screenshots, hierarchy, recordings) uploaded on failure. A self-hosted runner with the phone attached is an option after that.

## 8. Reliability and quality gates

- **Flake budget**: a flow that fails and then passes on a rerun is a bug in the flow or the app, logged in `maestro/FLAKES.md`. One automatic retry in CI (`retry` is for steps, not for hiding failures); a flow with more than one flake in 20 runs is tagged `quarantine` within a day (excluded from gates, still run nightly) and fixed or deleted within a week.
- **Stability check for new flows**: `maestro.sh --repeat 20 --component <id>` must be green before merge.
- **Time budgets**: smoke suite under 3 minutes on the OnePlus 9 Pro (about 6 on an emulator); the full suite reported, not gated.
- **Version pinning**: Maestro CLI version in `.maestro-version`, installed in CI by a cached step; bumped in its own PR with a full nightly run.
- **Failure triage**: the first artifact to read is `screen-hierarchy/`, then `logs/` (crashes and ANRs are in there), then the recording.
- **Review checklist** for a flow PR: header, tags, selectors per 4.3, no sleeps, subflows reused, states and environment covered or marked not applicable, 20x green, `FRAMEWORK_LOG.md` updated when the framework changed.

## 9. Phases and exit criteria

| Phase | Work | Exit criterion |
|---|---|---|
| 0 Spike (1 to 2 days) | Install Maestro under JDK 21, connect the OnePlus 9 Pro, run against the existing catalog: tap through to one demo, read the hierarchy dump, try `swipe` on a slider, try `assertScreenshot`, check `launchApp` arguments, record the device profile and the OxygenOS quirks that bite. | A written list of what works, what does not, gesture precision numbers and the device profile, appended to this ADR and to `devices.md`. |
| 1 Harness | Deep link, bare mode, control tags, `harness:ready`, `testTagsAsResourceId`, `maestro` build type, `demos.json` export. | `openLink` to any of the 68 demos reaches `harness:ready` on the phone; a Compose test checks the deep-link parser. |
| 2 Framework and pilots | `config.yaml`, `_lib`, `maestro.sh`, `device-setup.sh` (with `--check`), `scaffold.sh`, `coverage.sh`, pilots 1 to 5 of the table, `FRAMEWORK_LOG.md`. | Pilot flows 20x green on the phone; scaffold generates a runnable skeleton for any demo. |
| 3 Breadth | Remaining components in the order of section 5, cross-cutting suites, environment matrix, coverage ratchet turned on (hard fail). | Every demo has a smoke flow; allowlist empty or justified; full smoke under 3 minutes. |
| 4 Local gate | The pre-merge smoke habit in section 7, the PR summary line, `devices.md` measured times. | Two weeks of PRs with the gate run and no unexplained flake. |
| 5 CI | `maestro.yml` on an API 34 emulator, artifacts, JUnit summary, path filters, pinned Maestro version. | A pull request that breaks a pilot component fails the emulator gate with usable artifacts. |
| 6 Visual regression (decision deferred, see question 1) | Baselines of every component's preview in light and dark on the phone, update workflow, threshold policy. | A deliberate style change shows up as a failing screenshot flow and is accepted with `--update-baselines` in a reviewed PR. |

## 10. Risks and open questions

| Risk | Mitigation |
|---|---|
| Custom Canvas widgets (colour square, charts, node graph canvas) expose little through accessibility, so Maestro can only drive them by coordinates and read what we describe. | Test gestures by their visible effect (a text readout in the demo), add semantics where they are missing anyway (the accessibility audit wants them), keep geometry-heavy checks in Compose tests. Graph editing stays mostly out of scope for Maestro. |
| `swipe` may be too coarse for slider and divider values. | Phase 0 measures it; fall back to coarse assertions (moved right, value increased) and keep exact values in Compose tests. |
| `inputText` is ASCII only; no arrow keys. | Documented; Unicode input and keyboard navigation stay in Compose tests. |
| Screenshot baselines are device-specific and fragile; baseline files grow the repository. | One reference device (the OnePlus 9 Pro) with pinned display settings, per-flow thresholds, storage decided at phase 6 (no baselines in git before). |
| Popups and dialogs are separate windows; the hierarchy may need a moment. | `assertVisible` polling, `extendedWaitUntil` where known; helper subflows. |
| Fully managed or "hardened" phones reject Maestro's driver app. | Use plain consumer phones for the device lane. |
| Maestro upgrades change behaviour. | Version pinned, upgrade in its own PR. |
| Test time grows with 68 components times environments. | Environment wrappers reuse cores; shard across devices; smoke gate stays small; full matrix nightly. |

## 11. Decisions (2026-10-06)

1. **Baselines**: no baselines in git until phase 6; then decide between Git LFS and CI artifacts with the churn we have measured by then.
2. **Real-device coverage**: our own devices. Maestro Cloud is revisited only when we need many models.
3. **Devices and API levels**: **start with the OnePlus 9 Pro (Snapdragon 888, Android 14) only.** No API 37 or API 24 coverage for now; a later emulator mirrors API 34.
4. **Harness in the release APK**: no. The harness (deep link, bare mode, tags) exists only in a separate `maestro` build type, so the published catalog APK stays as it is.
5. **The fast phone**: the OnePlus 9 Pro.

# Kompound – Specification (v0.1 draft)

Status: draft for review. Nothing implemented yet.
Repo: `github.com/KloosTech/Kompound` · License: Apache-2.0

## 1. Goals

1. **Library** – Compose Multiplatform UI components, consumable from other KMP projects. Targets: Android, iOS, Desktop (JVM).
2. **Catalog** – app that lists every component, lets visitors search / filter / tag, and try components live on device.
3. **Low barrier to add a component** – write one composable + one annotation. No registration, no navigation edits, no catalog edits.
4. **Future-proof framework** – structure, build, publishing, CI stable first; components second.

Non-goals (v0.1): web/wasm *library* target, theming marketplace, paid licensing/gating, component code generation.

## 2. Decisions on open questions

| # | Question | Decision | Why |
|---|----------|----------|-----|
| Q1 | UI toolkit / styling | Compose Multiplatform. **Styles API is the styling primitive, front and centre** (COMPONENT_SPEC §3). M3 supplies design tokens (ColorScheme/Typography/Shapes) and theming interop only; widgets are built on foundation + `Modifier.styleable`, not wrapped M3 widgets. Only third-party deps: JetBrains/AndroidX Compose (+ M3 for tokens). | Deepest restylability, state-driven visuals without recomposition, no duplicate widget layer. Availability in CMP is gate S4. |
| Q2 | Module layout | `:kompound` (library), `:catalog:shared` (KMP), `:catalog:androidApp`, `:catalog:desktopApp`, `:catalog:iosApp` (Xcode), `:kompound-annotations`, `:kompound-processor`, `:kompound-theme` optional | AGP 9+ forbids `com.android.application` in same module as KMP plugin. Research layout (single `:catalog` with android/desktop/ios source sets) is outdated for new AGP. |
| Q3 | Android library plugin | `com.android.kotlin.multiplatform.library` | Official replacement for `com.android.library` in KMP. Caveat: single variant, no build types/flavors, no Android resources (use Compose Resources instead), no BuildConfig. |
| Q4 | Desktop source set name | `jvm("desktop")` → `desktopMain` | Matches research tree; consumers resolve `-desktop` artifact. |
| Q5 | Auto-discovery mechanism | **KSP processor** scanning `@KompoundComponent` in `:kompound`, generating `KompoundRegistry` consumed by catalog | Works on all targets (no reflection on iOS/native). Alternatives rejected: classpath scan (JVM only), hand-written registry (barrier), Gradle source-parse (fragile). |
| Q6 | Where do demos live | Separate KMP module `:showcase` depending on `:kompound`; demos mirror component package paths | Library artifact must not ship demo code or preview deps. `:catalog:shared` depends on `:showcase`. Trade-off: component and demo in two modules. |
| Q7 | Metadata model | Annotation on **demo function** in `:showcase` (see §4) | Keeps library API clean; metadata can change without library release. |
| Q8 | Search/filter/tags | Client-side, in-memory over generated registry; fuzzy match on name, description, tags, category | Registry small (hundreds). No backend. |
| Q9 | Customers try on device | Catalog distributed via: Wasm web build (GitHub Pages, zero install), Android APK (GitHub Releases) → Play internal/open testing later, iOS TestFlight, Desktop dmg/msi/deb | Web catalog is the cheapest "try now" – see Q10. |
| Q10 | Add Wasm *catalog* target? | **Yes.** Adds no dependencies (Compose + M3 both ship `wasmJs`); only cost is that every `expect` needs a wasm actual and Compose Web is Beta. Honours "dependency free" rule. Library `wasmJs` artifacts published only when flag on. | Lowest friction for prospects; library stays 3 targets. Compose Wasm is Beta; acceptable for a showcase. Requires `:showcase` and `:kompound` to compile for `wasmJs` → **forces `:kompound` to add `wasmJs` target** (a catalog can't depend on a module lacking the target). Decision: add `wasmJs` to `:kompound` as *unpublished-by-default* target, flip on once stable. Needs your sign-off (§9). |
| Q11 | Maven coordinates | Central namespace `tech.kloos` (verified via DNS TXT record on `kloos.tech`); group `tech.kloos.kompound`; artifact `kompound` (+ `kompound-annotations`); base package `tech.kloos.kompound` | Owned domain, user-decided. Reverse-DNS of `kloos.tech`. |
| Q12 | Publishing target | Maven Central (primary) via `com.vanniktech.maven.publish`; `mavenLocal` for dev; GitHub Packages **not** used for public | Central needs no consumer credentials. GitHub Packages requires auth even for public reads – bad for customers. Keep as optional private channel only. |
| Q13 | iOS distribution of library | KMP publishes klibs through Maven; Kotlin consumers need nothing more. **XCFramework/SPM only if a Swift-only consumer exists** | Compose iOS UI is normally embedded in a Kotlin-built framework by the *consumer's* KMP module; shipping a prebuilt framework of a UI library is rarely useful. Deferred. |
| Q14 | Versioning | SemVer, `0.x` until API stable, tags `vX.Y.Z`, version in `gradle.properties` | Release workflow keyed on tag. |
| Q15 | API stability | `kotlinx.binary-compatibility-validator` (or Kotlin's built-in ABI validation) + `explicitApi()` | Prevents accidental breaks; mandatory for a published library. |
| Q16 | Docs | Dokka multi-module HTML → GitHub Pages under `/api`, catalog Wasm under `/` | Single site. |
| Q17 | Min versions | "Reasonable defaults": Android minSdk 24, compileSdk/targetSdk latest stable, JVM toolchain 17 (library bytecode 11), iOS 15+, Desktop JDK 17+. | Re-check vs Compose MP floor in spike S2; raise only, never silently lower. Raising minimums = minor/major bump per COMPONENT_SPEC versioning. |
| Q18 | Tests | commonTest unit tests, Compose UI tests (`runComposeUiTest`) on desktop, screenshot tests via Roborazzi (desktop/Android) optional phase 2 | Desktop tests are fastest CI. |
| Q19 | Registry validation | Processor emits compile **errors** for: missing id, duplicate id, non-`@Composable` target, unknown tag-category | Catches bad contributions at build time. |
| Q20 | Catalog state | Deep link / URL per component (`#/component/button`), persisted theme/dark mode | Shareable links to customers (esp. web). |

## 3. Module layout

```
Kompound/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties                # VERSION_NAME, GROUP, POM_* metadata
├── gradle/libs.versions.toml
├── build-logic/                     # convention plugins (included build)
│   └── src/main/kotlin/
│       ├── kompound.kmp-library.gradle.kts   # targets, explicitApi, toolchain
│       ├── kompound.publishing.gradle.kts    # vanniktech config
│       └── kompound.compose.gradle.kts
├── kompound/                        # PUBLISHED library
│   └── src/{commonMain,androidMain,iosMain,desktopMain,commonTest}
├── kompound-annotations/            # PUBLISHED (tiny, KMP, no deps): @KompoundDemo etc.
├── kompound-processor/              # JVM, KSP processor, NOT published initially
├── showcase/                        # KMP: demos for every component (not published)
│   └── src/commonMain/kotlin/...    # @KompoundDemo functions
├── catalog/
│   ├── shared/                      # KMP: nav, search UI, registry consumer (not published)
│   ├── androidApp/                  # com.android.application
│   ├── desktopApp/                  # compose desktop application, packaging
│   ├── iosApp/                      # Xcode project embedding catalog:shared framework
│   └── webApp/                      # wasmJs (if Q10 approved)
├── docs/SPEC.md
└── .github/workflows/
```

Rules:
- `:kompound` depends on nothing from showcase/catalog/processor.
- Dependency direction: `catalog:* → showcase → kompound`; `showcase` + KSP → `kompound-processor`.
- Convention plugins in `build-logic` – every module's `build.gradle.kts` stays < 30 lines.
- Use type-safe project accessors (`projects.kompound`) and a single version catalog.

## 4. Component authoring contract (the "low barrier")

Adding a component = 2 steps:

1. Write composable in `kompound/src/commonMain/kotlin/tech/kloos/kompound/<category>/KButton.kt`.
2. Write demo in `showcase/src/commonMain/kotlin/.../<category>/KButtonDemo.kt`:

```kotlin
@KompoundDemo(
    id = "button.primary",                 // unique, stable, kebab/dot; used in deep links
    title = "Button",
    description = "Primary action button with loading state.",
    category = Category.Inputs,            // enum from annotations module
    tags = ["button", "action", "cta"],
    since = "0.1.0",
    platforms = [Platform.Android, Platform.Ios, Platform.Desktop], // default all
    status = Status.Stable,                // Experimental | Beta | Stable | Deprecated
)
@Composable
fun KButtonDemo() { /* may declare controls, see below */ }
```

KSP generates (in `:showcase`):

```kotlin
public object KompoundRegistry { val entries: List<DemoEntry> }
public class DemoEntry(val meta: DemoMeta, val content: @Composable () -> Unit)
```

Catalog reads `KompoundRegistry.entries`. Nothing else to touch.

### 4.1 Interactive controls (phase 2 but design now)
A `DemoScope` receiver gives state-backed controls so visitors can "try" a component:

```kotlin
@Composable fun DemoScope.KButtonDemo() {
    val label by textControl("Label", "Click me")
    val enabled by boolControl("Enabled", true)
    KButton(onClick = {}, enabled = enabled) { Text(label) }
}
```
Catalog renders a control panel from registered controls. Also supports multiple **variants** per demo via `@KompoundVariant` or `variants { }` DSL. Define `DemoScope` API in v0.1 even if panel UI is minimal, to avoid later breaking change in the annotation contract.

### 4.2 Search / filter / tags
- Search: tokenised, case-insensitive, fuzzy over `title`, `description`, `tags`, `category`.
- Filters: category (multi), tags (multi, AND/OR toggle), platform, status; sort by name / since.
- Tags free-form but **normalised** (lowercase, trimmed); processor warns on near-duplicates (`btn` vs `button`) via a `tags.allowlist` file option (strict mode off by default).
- Tag chips clickable → apply filter.
- Metadata for filter UI (all categories/tags + counts) derived at startup.

### 4.3 Catalog UX (v0.1 scope)
Adaptive layout: list/detail on desktop/tablet/web (two-pane), single-pane navigation on phone. Detail screen: live demo, controls, description, tags, status, "since", code snippet (see pitfall P9), light/dark toggle, density/font-scale toggle, RTL toggle. Deep link per component.

## 5. Build and tooling details

- Kotlin Gradle plugin + Compose Multiplatform plugin + KSP2 versions pinned together in catalog; renovate/dependabot config for upgrades.
- KSP in KMP: apply per-target configurations (`kspCommonMainMetadata` for common; plus `kspAndroid`, `kspDesktop`, `kspIosArm64`, `kspIosSimulatorArm64`, `kspIosX64`, `kspWasmJs`). Simplest robust approach: run processor **only on metadata/common** and wire generated sources into commonMain (known pattern: `kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")` + `dependsOn` for compile tasks). Must be validated in spike S1 before committing – this is the riskiest piece.
- `explicitApi()` strict on `:kompound`.
- Gradle: configuration cache + build cache on, Gradle wrapper pinned, JDK toolchain.
- Lint: `ktlint` or `detekt` + Compose rules (`io.nlopez.compose.rules`) – enforces Compose conventions (modifier param, naming, stability).
- Compose compiler stability: enable strong skipping (default); generate compiler metrics report task for review; consider stability config file for kotlinx collections.

## 6. Publishing

### 6.1 Automated by repo (I implement)
- Convention plugin `kompound.publishing` with vanniktech plugin: POM metadata (name, description, URL, license Apache-2.0, developer, SCM), sources jar, Dokka javadoc jar, signing from in-memory key env vars.
- Tasks: `publishToMavenLocal`, `publishAndReleaseToMavenCentral`.
- GitHub Actions:
  - `ci.yml` – on PR: build on `ubuntu` (jvm/android/desktop tests, lint, apiCheck) and `macos` (iOS link + tests).
  - `release.yml` – on tag `v*`: **macos runner** (required to build and publish iOS targets; Kotlin/Native Apple targets can only be built on macOS) → publish to Central; build catalog artifacts (APK/AAB, desktop installers per OS matrix, wasm site); create GitHub Release with artifacts; deploy Pages (catalog + Dokka).
  - `snapshot.yml` optional – publish `-SNAPSHOT` to Central snapshots on main.
- Consumer README snippet, `CHANGELOG.md`, `RELEASING.md`.

### 6.2 Manual tasks for you (cannot be done from repo) – checklist
1. **Sonatype Central Portal** account (central.sonatype.com) → register namespace `tech.kloos`, verify by adding the DNS TXT record the portal shows to `kloos.tech`.
2. Generate **user token** (username/password pair) in portal.
3. Create **GPG key** (`gpg --full-generate-key`, RSA 4096), publish public key to `keyserver.ubuntu.com` / `keys.openpgp.org` (Central checks), export private key armored (`gpg --export-secret-keys --armor`).
4. Add GitHub repo **Actions secrets**: `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_IN_MEMORY_KEY`, `SIGNING_IN_MEMORY_KEY_ID`, `SIGNING_IN_MEMORY_KEY_PASSWORD`.
5. Enable **GitHub Pages** (source: GitHub Actions) for the repo.
6. **Android signing**: create upload keystore for catalog app; store as base64 secret + passwords. Decide applicationId (e.g. `tech.kloos.kompound.catalog`). Play Console account ($25 one-time) only if you want Play distribution.
7. **iOS TestFlight** (optional, **blocked: Apple Developer enrolment still in review**; iOS publishing steps are skipped automatically while the related secrets are absent): App Store Connect app record, bundle id, signing certificate/profile, App Store Connect API key as secrets; fastlane lane in CI. Without it iOS catalog is simulator/Xcode-install only.
8. **Desktop**: macOS notarisation needs Apple Developer ID cert (same program); Windows signing needs a code-signing cert (optional, SmartScreen warnings otherwise).
9. Branch protection on `main`, require CI.
10. First release to Central cannot be undone (versions are immutable) → do a `0.1.0-alpha01` dry run via portal's "publish manually/validate" mode first.

## 7. Pitfalls and mitigations

| ID | Pitfall | Mitigation |
|----|---------|-----------|
| P1 | AGP 9 vs KMP plugin in same module as app | Separate `androidApp` module (already in layout). |
| P2 | KSP + KMP codegen wiring is flaky (task ordering, iOS targets, "generated sources not visible") | Spike S1; fallback: Gradle task generating registry from annotation-free convention (e.g. scanning `@KompoundDemo` with a source-level parser) or JVM-only processor emitting `commonMain` source. |
| P3 | Library adds `wasmJs` → published artifacts for it must stay consistent; Compose Web beta | Keep wasmJs behind Gradle property `kompound.enableWasm`; do not publish until flagged on. |
| P4 | Resources: Android `res/` unsupported in new KMP lib plugin | Use Compose Resources (`composeResources/`); set unique `packageOfResClass` (`tech.kloos.kompound.resources`) to avoid clashes in consumer apps; fonts/icons shipped this way. |
| P5 | Fonts differ per platform; text metrics shift | Bundle fonts via Compose Resources; avoid `FontFamily.Default` reliance in components. |
| P6 | Consumers' Compose MP / Kotlin versions older than library's → Gradle resolution error or klib ABI mismatch | Document supported matrix in README; publish compat table per release; expose Compose deps as `api` only where types leak; state "consumer must use ≥ X" in README. |
| P7 | Accidental public API leakage (internal helpers, Material types) | `explicitApi()`, binary-compat validator, `@KompoundInternalApi` opt-in annotation. |
| P8 | iOS: CI cost/time (macOS runners 10× minutes) | Run iOS link/tests only on PR touching code, cache Konan (`~/.konan`) and Gradle; skip iOS in draft PRs. |
| P9 | "Show code snippet" in catalog needs source text; no reflection | KSP can capture the demo's source text into the registry (read `KSFunctionDeclaration` source via file location) or require explicit `snippet = """..."""`. Start with explicit/optional; auto-capture phase 2. |
| P10 | Compose `@Preview` duplicates demos | Optionally KSP-generate `@Preview` wrappers? Skip v0.1; IDE previews for commonMain composables work via `@Preview` from `org.jetbrains.compose.ui:ui-tooling-preview`, add `debugImplementation` only in showcase. |
| P11 | Catalog crashes on one bad demo take down app | Wrap each demo in error boundary (`try` not possible around composables → use `CompositionLocalProvider` + `runCatching` at event level, render isolated; accept limitation; CI smoke test renders every registry entry). |
| P12 | Registry test coverage: new component may render fine in isolation but fail on iOS | Generated parametrised UI test iterates `KompoundRegistry.entries` on desktop CI (render + no exception), and snapshot test later. |
| P13 | Component API churn breaks customers | `status` field + `@RequiresOptIn` for Experimental components; `Deprecated` with `ReplaceWith`; ABI validator in CI. |
| P14 | Duplicate ids / renamed ids break deep links | Processor error on duplicates; id rename policy documented (`aliases = []` in annotation). |
| P15 | Desktop `run` + hot reload friction | Add Compose Hot Reload plugin to `desktopApp` (huge dev-loop gain). |
| P16 | iOS Xcode project hand-maintained & stale | Keep minimal `iosApp` using standard KMP wizard template; framework embedded via `embedAndSignAppleFrameworkForXcode`; build in CI via `xcodebuild`. |
| P17 | Licensing | **Resolved:** open source, Apache-2.0, free for all. Every source file needs no header but NOTICE/LICENSE stay in root; third-party assets (fonts, icons) must have compatible licences – checked by component checklist. |
| P18 | Secrets leaking in logs/forks | Release workflow only on tags in main repo, `environment: release` with required reviewer. |
| P19 | Gradle publish partial failure (some targets uploaded) | Central Portal deployment is atomic per deployment (upload bundle then release); vanniktech uses it. Never hand-upload per-target. |
| P20 | Kotlin/Native on Apple Silicon vs Intel iOS simulator target | Include `iosArm64` + `iosSimulatorArm64`; add `iosX64` only if Intel CI/consumers matter (being dropped by ecosystem). |
| P21 | Package naming churn | Base package fixed: `tech.kloos.kompound`. Resource package `tech.kloos.kompound.resources`. |

## 8. Phases

- **Phase 0 – spikes (time-boxed, order S4 → S2 → S1 → S3):** S1 KSP registry across all targets incl. iOS; S2 AGP KMP library plugin + Compose resources + min-version floor; S3 wasmJs compile of `:kompound` + `:showcase`; **S4 (FIRST, gates everything): Styles API availability/stability in Compose Multiplatform on Android, iOS, desktop, wasm; verify state blocks, merge/`then`, interaction wiring, no-recomposition claim (recomposition counter), and which CMP/AndroidX Compose + Kotlin versions ship it.** Outcome recorded in `docs/adr/0001-styling.md`; fallback tree in COMPONENT_SPEC §3.4.
- **Phase 0.5 – contract:** finalise `COMPONENT_SPEC.md` (checklist + lint/ test enforcement) before any component is written.
- **Phase 1 – skeleton:** build-logic, version catalog, modules, one sample component (`KButton`) + demo, catalog (list, search, filter, tags, detail) on desktop + android + iOS, CI green, `publishToMavenLocal` works, consumer sample verifying import via `mavenLocal`.
- **Phase 2 – publish:** Central dry run, release workflow, Dokka, Pages, APK on Releases.
- **Phase 3 – catalog polish:** controls panel, variants, code snippets, deep links, wasm site, TestFlight.
- **Phase 4 – components** at will.

Acceptance for Phase 1:
- Adding a new `@KompoundDemo` function is the only change needed for it to appear in catalog on all targets, searchable by title/tag, filterable by category.
- `./gradlew :catalog:desktopApp:run` launches; Android debug APK installs; iOS app runs in simulator.
- `./gradlew publishToMavenLocal` then sample consumer project resolves `tech.kloos.kompound:kompound:<ver>` for Android, desktop, iOS.
- `apiCheck`, lint, tests pass in CI.

## 9. Decisions (resolved from Decisions.md)

- Open source, Apache-2.0 → Maven Central. Resolved.
- wasmJs: allowed provided no extra dependencies. Resolved (Q10).
- Domain `kloos.tech` → `tech.kloos.*`. Resolved (Q11).
- M3 foundation + stylable + leverage Compose styling APIs. Resolved (Q1); verify styling API availability in spike S4.
- Min versions: defaults (Q17).
- Apple Developer account in review → iOS signing/TestFlight/notarisation jobs are written but gated on secret presence. Until then catalog iOS = simulator build only.
- Existing components NOT ported first. First deliverable is the **component checklist / contract**: see `docs/COMPONENT_SPEC.md`. Category enum below is provisional.
- Secrets are added to GitHub later. Workflows must therefore **degrade gracefully** (see §11).

## 10. Initial category enum (editable)
`Inputs, Buttons, Display, Feedback, Navigation, Layout, Overlays, Data, Animation, Utilities`

## 11. GitHub Actions plan (no secrets needed to start)

All publishing runs in GitHub Actions. Secrets get added later, so each job checks for them and **skips (not fails)** when absent: `if: ${{ env.HAS_SECRET == 'true' }}` pattern using a prior step that sets the flag from `secrets.X != ''`.

| Workflow | Trigger | Runs on | Secrets needed | Does |
|----------|---------|---------|----------------|------|
| `ci.yml` | PR, push main | ubuntu + macos | none | build, lint (detekt/ktlint + compose-rules), unit + registry smoke tests, `apiCheck`, desktop UI tests, iOS link/simulator tests (macos), wasm compile |
| `catalog-web.yml` | push main | ubuntu | none (Pages enabled) | build wasm catalog + Dokka → deploy GitHub Pages |
| `snapshot.yml` | push main (optional) | macos | Central + signing | publish `-SNAPSHOT` |
| `release.yml` | tag `v*` | macos (+ matrix for desktop installers) | Central + signing (library); keystore (APK/AAB); Apple (iOS, notarisation) | publish to Central, GitHub Release with APK + desktop installers, Pages |
| `dependency-review` / Renovate | schedule | ubuntu | none | dependency updates |

Secret names (to add later): `MAVEN_CENTRAL_USERNAME`, `MAVEN_CENTRAL_PASSWORD`, `SIGNING_IN_MEMORY_KEY`, `SIGNING_IN_MEMORY_KEY_ID`, `SIGNING_IN_MEMORY_KEY_PASSWORD`, `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, and later `APPLE_*` / `ASC_API_KEY_*`.
Unsigned fallback: with no Android keystore the release job attaches a debug-signed APK, clearly named `-unsigned`.
Release job runs in GitHub `environment: release` (optional required reviewer).

## 12. Decided in review
- Component prefix `K`.
- `kompound-annotations` is published. To make it usable by third parties, the annotations module (and the processor + a catalog-shell library, `kompound-catalog`) should be consumable so any KMP project can generate its own catalog from its own `@KompoundDemo` functions. Treat `kompound-processor` and the catalog shell as publishable candidates (stabilise API before 1.0; ship as `kompound-catalog` + KSP artifact).

## 13. Styles API impact summary
- **Component contract:** every visual component has a `style: Style` param; state visuals live in Style state blocks.
- **Catalog:** each demo gets a style playground (force states, live style override, theme switch) – see COMPONENT_SPEC C-092a. `DemoScope` (§4.1) gains `styleControl(...)` helpers.
- **Risk:** Styles API is new/experimental; hence spike S4 first and the `KStyle` fallback (COMPONENT_SPEC §3.4). Pin Compose/Kotlin versions to ones that ship it and document the consumer minimum (P6).
- **Unverified claims** from external research (e.g. recomposition-free animation, exact API names) are treated as hypotheses until S4 confirms.

## 14. Phase 0 progress
- **S4 done: PASS.** See `docs/adr/0001-styling.md`. Styles API works from `commonMain` on desktop/iOS/wasm/android with CMP 1.12.1; experimental opt-in required; recomposition claim confirmed on desktop.
- **S3 (wasmJs) partial pass:** Styles API + Compose foundation/material3 compile for `wasmJs`.
- **S2 partial pass:** `com.android.kotlin.multiplatform.library` 9.4.1 + compileSdk 36 compiles. Compose Resources and min-version floor still to check.
- **Pinned toolchain (spike-proven):** Kotlin 2.4.20, CMP 1.12.1, AGP 9.4.1, Gradle 9.8.0.
- **S1 done: PASS.** See `docs/adr/0002-demo-discovery.md`. KSP on commonMain metadata works on all targets (tests ran on desktop + iOS simulator). Needs explicit task wiring, to live in a convention plugin; third-party use wants a Gradle plugin.
- **S2 done: PASS (with caveats).** Compose Resources work on desktop/iOS/wasm/android with `packageOfResClass = "tech.kloos.kompound.resources"` (accessors are extension properties; must `import tech.kloos.kompound.resources.<name>`). `minSdk = 21` compiles (spec default stays 24). Caveat: with the AGP KMP library plugin the Compose task `copyAndroidDeviceTestComposeResourcesToAndroidAssets` fails ("outputDirectory not set"); workaround: disable that task when using `androidDeviceTest`.
- **Runtime checks:** Styles recomposition test passes on desktop and iOS simulator. Android emulator (API 37) blocked by Espresso/`InputManager.getInstance` incompatibility; wasm runtime untested. Both carried into Phase 1 CI setup.
- **Environment notes:** a physical Android device is paired over adb; always set `ANDROID_SERIAL` for connected tests. Emulator `Kompound_API37` exists. Xcode 26.6 + iOS 18.2/18.5/26.5 simulators present.
- **Remaining design item (Phase 1):** multi-module registry aggregation.
- **Phase 0 verdict:** architecture confirmed. Ready for Phase 1 (skeleton).

## 15. Catalog aggregation locked (ADR 0003)
Option D: per-module generated registry + Gradle plugin that aggregates from project dependencies. Added module `kompound-demo` (runtime model) and included build `kompound-gradle-plugin`. Categories are free strings. See `docs/adr/0003-catalog-aggregation.md`. Supersedes the single-registry description in §4 where they differ.

## 16. Phase 1 progress
Done: Gradle skeleton (`build-logic` convention plugins, `kompound-gradle-plugin` included build, version catalog, wrapper 9.8.0); modules `kompound-annotations`, `kompound-demo`, `kompound-processor`, `kompound` (KButton, KText, KompoundStyles), `showcase`, `catalog:shared` (search, category/tag filter, adaptive list/detail, tests), `catalog:desktopApp`, `catalog:androidApp`, `catalog:webApp`.
Verified: all targets compile (android, desktop, iosArm64, iosSimulatorArm64, wasmJs); catalog tests pass on desktop incl. generated-registry discovery; Android API 37 emulator shows auto-discovered KButton with working pressed/disabled styles; wasm distribution builds.
Open for Phase 1: `catalog:iosApp` (Xcode project), GitHub Actions workflows, publishing config (vanniktech, Dokka, `publishToMavenLocal` + consumer sample), ABI validator, detekt/compose-rules, PR template, scaffold task `newComponent`, KButton/KText tests (override-merge, zero-recomposition), web runtime check in a browser.

## 17. iOS catalog
`catalog/iosApp` added (XcodeGen `project.yml`, SwiftUI host, `MainViewController()` from `:catalog:shared` iosMain). Built with `xcodebuild` and run on the iPhone 16 simulator (iOS 18.2): auto-discovered KButton listed. Notes: Swift app module must not share the name of the Kotlin framework (`KompoundCatalog`), so the app target is `KompoundCatalogApp`; first simulator boot is slow. Open for CI: `xcodegen generate` + `xcodebuild` on a macOS runner.

## 18. Component test baseline (KButton, KText)
12 tests run on desktop and the iOS simulator (common tests with `runComposeUiTest`): click, disabled (no click + semantics), button role, default background from theme (pixel check), consumer style overrides only what it sets (background override keeps default content colour), consumer `pressed` block overrides default pressed, default pressed changes background, label inherits `contentColor`, press does not recompose content, hit area includes style padding, `KText` inherits parent colour and own style overrides it. These are the template for the per-component test requirements (C-110, C-014, C-040).
Bug found by tests: `clickable` was inside `styleable`, shrinking the hit area to the label. Fixed; rule C-016a added.

## 19. Publishing, CI, processor tests (done)
- Processor: 17 tests (kotlin-compile-testing + KSP2) cover generation, sorting, tag normalisation, escaping, `DemoScope` receiver, no-demo case, and build errors (duplicate id/alias, non-composable, parameters, bad id, blank title, long description, private, foreign receiver).
- Publishing: convention plugin `kompound.publishing` (vanniktech 0.37.0, POM metadata, signing only when `signingInMemoryKey` exists). `publishToMavenLocalAll`, `publishToMavenCentralAll`, `publishAndReleaseToMavenCentralAll` cover all modules and the plugin build. Version override via `-PVERSION_NAME` verified for every artifact and the plugin jar manifest.
- Consumer sample (`samples/consumer`) builds against published artifacts and discovers its own demo via the Gradle plugin; tests pass on desktop and iOS simulator.
- Workflows (actionlint clean): `ci.yml` (ubuntu + macOS + consumer sample), `catalog-web.yml` (Pages), `release.yml` (Central, APK, desktop dmg/msi/deb, GitHub Release; jobs skip without secrets). PR template added. See `docs/RELEASING.md`.
- Not done yet: Dokka/javadoc, ABI validator, detekt + compose-rules, `newComponent` scaffold task, running the workflows on GitHub (needs the repo push).

## 20. Wave 0 (foundation) done
- Decisions: colour tokens = M3 roles + small extension set; icons passed as slots (no icon set); overlays adaptive by window size.
- Added: `KompoundTheme`/`KompoundTokens` (success/warning/info colours with WCAG-AA-checked pairs, spacing, motion, state layers), `LocalKContentColor`, `KIcon`, `KSurface` (plain and clickable), `KDivider`; `DemoScope` controls with catalog `ControlPanel`; generated `DemoSmokeTest` (every demo x light/dark/RTL/fontScale2).
- Tests: kompound 33 + demo controls 5 + processor 17 + catalog 7 (desktop; kompound and catalog also on the iOS simulator).
- Visual check on Android API 37: five demos auto-discovered, controls live-update demos.

## 21. Wave 1 (primitives) done
Components: `KButton` (filled, tonal, outlined, text), `KIconButton`, `KFab` (regular, extended), `KToggleButton`, `KSegmentedControl`, `KChip` (assist + filter), `KBadge` + `KBadgeDot`, `KAvatar` (initials, image slot, presence), `KCheckbox` (incl. tri-state), `KRadioButton`, `KSwitch`. All have demos with controls and pass the generated smoke test.
Findings:
- `animate { }` inside a Style (also within a state block) interpolates its properties in the layout/draw phase: `KSwitch` thumb, track and halo animate with no recomposition (tested).
- Shared state: several `styleable` nodes can share one `StyleState` (halo + box + row), so one interaction source drives all layers.
- Button family shares `ButtonBase` (hover + click/toggle outside `styleable`, C-016a); selection controls share `SelectionRow`/`HaloSlot`.
- Test technique: distinct-hue test scheme + pixel sampling at density 1; sample solid bands, not antialiased edges; stack siblings in a `Column` so captures do not overlap.
- Tests: 96 desktop + 96 iOS simulator (kompound + catalog), 17 processor, 5 demo controls.
Next: Wave 2 inputs (text fields, search bar, dropdowns, date pickers, inline edit).

## 22. Wave 2 (inputs) done
Components: `KTextField`, `KTextArea`, `KNumberField`, `KSearchBar`, `KMenu` + `KMenuItem`, `KDropdown`, `KMultiDropdown`, `KDateField`, `KDateRangeField`, `KInlineEdit`.
Decisions and findings:
- Text input uses foundation `BasicTextField` (value API). The label sits above the field (no floating label animation). Typed text cannot inherit Style text properties, so `KTextFieldDefaults.textStyle(enabled)` passes it explicitly.
- Custom Style state works: `StyleStateKey<Boolean>` set via `MutableStyleState.set` gives an `error { }` block; used by text fields, dropdowns and date fields. Several `styleable` nodes can share one `StyleState`.
- `PickerField` (text-field-looking trigger that opens a menu or dialog) is shared by dropdowns and date fields; they stay "selected" (primary outline) while open.
- `KMenu` uses `Popup` with a custom position provider (below the anchor, flips above when it does not fit, clamped to the window; pure function with unit tests). Up/Down move focus, Escape/back/outside click dismiss.
- Date fields wrap Material 3's `DatePicker`/`DateRangePicker` for calendar logic (a calendar from scratch would need `kotlinx-datetime`, which the dependency allowlist forbids). Dates are UTC-midnight epoch millis; formatting is a pure-Kotlin ISO formatter (`KDateFormat.iso`, tested incl. leap day and pre-1970) that callers can replace. Replace the M3 dialog with a Kompound dialog when `KDialog` exists (Wave 3).
- `KInlineEdit` deliberately has no save-on-blur: focus moving to the save/cancel buttons would save before a cancel click registers.
- Internal icons grew to six (search, close, chevron, check, calendar, edit), all in THIRD_PARTY_NOTICES.md.
- Tests: 148 desktop + 148 iOS simulator (kompound + catalog), 17 processor, 5 demo controls. Visual check on Android API 37: dropdown popup (width, flip of chevron, scrolling) and the date dialog.
Next: Wave 3 (dialog, bottom sheet, snackbar, tooltip, list item, progress, top bar/scaffolds, empty/error states).

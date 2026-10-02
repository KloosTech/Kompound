# ADR 0002: Demo discovery via KSP on commonMain metadata

Status: accepted (spike S1, 2026-10-02)

## Decision
`@KompoundDemo` (SOURCE retention, in published `kompound-annotations`) is processed by a JVM KSP processor run **once on `kspCommonMainMetadata`**. It generates `KompoundRegistry` into `build/generated/ksp/metadata/commonMain/kotlin`, added as a `commonMain` source dir.

## Spike result (KSP 2.3.12, Kotlin 2.4.20, CMP 1.12.1, AGP 9.4.1)
- Clean build + registry test pass on **desktop** and **iosSimulatorArm64** (tests executed); **iosArm64, wasmJs, android** compile.
- Adding a demo regenerates the registry incrementally (13 s rebuild); no other file touched.
- Duplicate ids fail the build with file:line error.
- Special characters in descriptions (`"`, `$`) are escaped correctly.

## Required wiring (gotchas, move into convention plugin)
1. `kotlin.srcDir("build/generated/ksp/metadata/commonMain/kotlin")` on commonMain.
2. `add("kspCommonMainMetadata", project(":processor"))` only; no per-target ksp configs.
3. Every `compile*`/`ksp<Target>` task must `dependsOn("kspCommonMainKotlinMetadata")`, except the metadata compile itself. Without it Gradle fails with "implicit dependency" validation error (KSP creates no-op per-target ksp tasks that read the srcDir).
4. Processor registered with `META-INF/services/com.google.devtools.ksp.processing.SymbolProcessorProvider`.
5. Enum annotation args arrive as `KSType`; parse by `toString().substringAfterLast('.')` (fine) or resolve declarations.

## Open / next
- Third-party consumers (published processor): needs same task wiring; ship a Gradle plugin (`tech.kloos.kompound.catalog`) that applies it, rather than documenting 4 manual steps.
- Registry across modules (demos in several modules): spike only covered one module; design: each module generates `KompoundRegistry_<module>` + catalog aggregates via a `ServiceLoader`-free list generated in the catalog module (to decide in Phase 1).
- `DemoScope` controls, code-snippet capture not spiked.

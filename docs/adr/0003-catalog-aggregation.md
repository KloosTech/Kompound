# ADR 0003: Catalog aggregation (option D)

Status: accepted

## Decision
- Every module that contains demos applies Gradle plugin **`tech.kloos.kompound.demos`**. KSP (ADR 0002) generates one registry per module: `tech.kloos.kompound.registry.KompoundRegistry_<moduleId>` implementing `DemoRegistry`. `moduleId` defaults to the sanitised Gradle project path (`:showcase` → `showcase`), overridable via `kompoundDemos { moduleId.set("...") }`.
- The catalog module applies **`tech.kloos.kompound.catalog`**. It inspects the catalog's `project(...)` dependencies (commonMain `api`/`implementation`), keeps those that applied the demos plugin, and generates `KompoundAllDemos` listing their registries. Zero config for in-repo modules.
- External (Maven) modules: `kompoundCatalog { externalRegistry("fully.qualified.KompoundRegistry_x") }` (one line in Gradle).
- Entry ids are namespaced `<moduleId>/<demoId>`; duplicate `moduleId` among aggregated registries fails the build; duplicate demo ids inside a module fail KSP.
- Order: by moduleId, then declaration order sorted by id.
- Demo function shapes accepted: `@Composable fun X()` or `@Composable fun DemoScope.X()` (scope for controls, phase 2).
- Categories are **free strings** (normalised) with constants in `KompoundCategory`, not a fixed enum, so third-party modules can add categories.

## Module roles
- `kompound-annotations` (published): `@KompoundDemo`, `KompoundCategory`, `KompoundStatus`, `KompoundPlatform`. No dependencies.
- `kompound-demo` (published): runtime model `DemoEntry`, `DemoMeta`, `DemoRegistry`, `DemoScope` (needs Compose runtime).
- `kompound-processor` (published): KSP processor.
- `kompound-gradle-plugin` (published, included build): plugins above.

## Consequences
Plugin-defined wiring (S1 gotchas) lives in one place. Cross-module duplicate checks and ordering are deterministic. Third parties use the same mechanism as in-repo modules.

# Instructions for AI coding agents

Kompound is a Kotlin Multiplatform UI library on Compose Multiplatform and the Compose Styles API (`tech.kloos.kompound`, components prefixed `K`).

- **Using the library or the graph framework:** read [`docs/AGENT_GUIDE.md`](docs/AGENT_GUIDE.md) first. It has the rules, a checked component reference, setup, patterns, the node graph framework and testing recipes.
- **Adding or changing a component:** follow [`docs/COMPONENT_SPEC.md`](docs/COMPONENT_SPEC.md); every component needs a `@KompoundDemo` in `showcase/` and tests.
- **Hard rules:** use `KText`/`KIcon` (never Material `Text`/`Icon`) inside components; style only through the `style: Style` parameter; opt in to `ExperimentalFoundationStyleApi`; common code only in `commonMain`.
- **Before you finish:** `./gradlew :kompound:desktopTest :kompound-graph:desktopTest :catalog:shared:desktopTest :kompound-graph:compileCommonMainKotlinMetadata :showcase:compileKotlinWasmJs`

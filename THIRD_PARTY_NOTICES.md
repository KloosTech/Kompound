# Third-party notices

## Material Symbols path data
`kompound/src/commonMain/kotlin/tech/kloos/kompound/internal/KompoundIcons.kt` contains vector path data for a handful of
icons (search, close, chevron, check, calendar, drag handle) taken from Google's Material Symbols / Material Icons,
licensed under the Apache License 2.0 (https://www.apache.org/licenses/LICENSE-2.0).
Copyright Google LLC.

## compose-dnd
`kompound/src/commonMain/kotlin/tech/kloos/kompound/dnd/` (except `KReorderable.kt` and `KDropZone.kt`) and the tests under
`kompound/src/*Test/kotlin/tech/kloos/kompound/dnd/engine/` are derived from compose-dnd by Mohamed Ben Rejeb and the Compose Dnd
project contributors (https://github.com/MohamedRejeb/compose-dnd), licensed under the Apache License 2.0. Changes: moved to the
package `tech.kloos.kompound.dnd`, public API renamed with the `K` prefix, deprecated API removed, explicit API mode applied.
Each derived file keeps its original copyright header.

# UI: Modern inline-code chips in KMarkdown (dark theme)

## Goal (user-facing)
Inline code spans in rendered markdown must look like modern, professional chips: rounded corners, uniform generous padding (text no longer hugs the edges), clean contrast in the dark theme — instead of a flat squared box padded only by inserted spaces.

## Domain facts
- Local Compose Multiplatform library `tech.kloos.kompound` 0.1.0-SNAPSHOT, published to the local ~/.m2 repo via `scripts/publish.sh local` (this is a local dev library, no remote repo concerns).
- Inline code styling currently in `markdown/KMarkdown.kt` (~line 408): plain `SpanStyle` — `FontFamily.Monospace`, `background = surfaceContainerHighest`, `color = onSurface`, no shape, padding achieved by a single space before/after the span. `SpanStyle` backgrounds are always flat rectangles, so rounded corners require a different technique (drawBehind + TextLayoutResult, or InlineTextContent chips).
- `InlineLook` / `KMarkdownDefaults.look()` are `internal`.
- The consumer app (Agent010) builds against this local SNAPSHOT (`kompound.local=true`); after any library change it must be republished locally and the consumer rebuilt.
- `KMarkdown.kt` already has uncommitted changes in the working tree — keep/layer on top of them, don't revert them.
- Editor-side markdown styling lives in `KMarkdownField.kt` (`styleMarkdownSource`) — keep visual consistency between rendered output and editor.
- Fenced code blocks are a separate element — do not break their styling.

## Hard requirements
1. Rendered inline code gets rounded corners (tasteful radius, consistent with the design language) and uniform padding (~6dp horizontal, ~2dp vertical as starting point — your call), replacing the space-padding hack.
2. Implementation in this library (consumer must not parse markdown itself).
3. Colors: keep design-system-consistent values; verify/fix dark-theme contrast (complaint: dark chip on dark background, poor separation). Optional: configurable `codeBackground`/`codeColor` parameters on KMarkdown, analogous to the existing `linkColor`.
4. Fenced code block styling stays intact.
5. No new dependencies. Library compiles clean.
6. Republish the local SNAPSHOT (`scripts/publish.sh local`), then verify the consumer: `cd /Users/jack/Workspace/04_Programming/01_Kotlin/Agent010 && ./gradlew :composeApp:compileAndroidMain` must be green.

## Design delegation
Your design decisions — codebase = source of truth. Technique (drawBehind vs InlineTextContent), exact radius/padding/colors, parameter API: your call.

## Definition of Done
- Library compile green + SNAPSHOT republished locally + Agent010 `:composeApp:compileAndroidMain` green.
- Report: changed files (repo paths), chosen technique/radius/padding/colors, publish status, consumer build status.

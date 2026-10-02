# Changelog

## Unreleased
- Added `KAccordion` / `KExpandable` (foldable sections, exclusive mode, saveable state, expand/collapse accessibility actions). Ported from the Snettbox Accordion.
- Added `KActionMenu` (icon button with a menu of actions: supporting text, attention dots, check marks, unfolding groups, dividers) and `KMenuItem(supportingText = ...)`. Ported from the Snettbox ActionMenu.
- Added `KMarkdown` (renders headings, lists, task lists, quotes, tables, links, highlighted code blocks; selectable) and `KMarkdownField` (edit the source with live styling), plus `KText(AnnotatedString, textStyle = ...)`. The Styles API does not apply a `textStyle` to annotated text yet, hence the explicit parameter.
- Catalog: every component has a "How to use" tab with a copyable Kotlin sample, rendered with `KCode`. New `@KompoundDemo(usage = ...)` field carried through the processor into `DemoMeta.usage`.
- `KCode` no longer crashes inside a scrolling parent: it only scrolls vertically when its height is bounded.
- Added `KCode`: syntax-highlighted code in a selectable text field (read-only or editable, line numbers, Kotlin/JSON/plain built in, custom `KCodeLanguage`, theme-following colours and a One Dark palette).
- Catalog release: only the newest `catalog-v*` GitHub Release is kept, the APK gets an increasing version code, and `scripts/create-android-keystore.sh` sets up the signing key so Obtainium can update the app.
- `KButton` effects (`KButtonEffects`): click shadow (on by default for filled and tonal), bounce, fade, colour morph, shape morph and sparkles; toggles in the KButton demo.
- Catalog redesign: adaptive shell with sidebar, category menu and tag filter behind icon buttons, preview stages, live theme designer (hue, saturation, roundness, text size, presets, "Get code"), built with Kompound's own components.
- Added `KSlider`.
- Fixed: `KompoundTheme` sets a root text style so plain `KText` follows the theme (dark mode).
- Fixed: `KText` kept the inherited style only for the first text; now survives text changes.
- Wave 3: `KLinearProgress`, `KCircularProgress`, `KButton` loading state, `KListItem`, `KEmptyState`, `KErrorState`, `KTopBar`, `KScaffold`, `KSnackbar`, `KTooltip`, `KDialog`, `KAlertDialog`, `KBottomSheet`.
- Changed: the standard icon button now uses `onSurfaceVariant`.
- CI: library (`v*` tags) and catalog apps (`catalog-v*` tags) are released by separate workflows.
- Wave 2: `KTextField`, `KTextArea`, `KNumberField`, `KSearchBar`, `KMenu`/`KMenuItem`, `KDropdown`, `KMultiDropdown`, `KDateField`, `KDateRangeField`, `KInlineEdit`.
- Wave 1: `KButton` variants, `KIconButton`, `KFab`, `KToggleButton`, `KSegmentedControl`, `KChip`, `KBadge`, `KAvatar`, `KCheckbox`, `KRadioButton`, `KSwitch`.
- Wave 0: `KompoundTheme` with tokens, `KIcon`, `KSurface`, `KDivider`; `DemoScope` controls and catalog control panel.
- Initial skeleton: `kompound` (KButton, KText), annotations, demo model, KSP processor, Gradle plugins, showcase, multi-target catalog (Android, iOS, desktop, web).

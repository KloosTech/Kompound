# ADR 0006: Release 0.2 plan (components, theming, forms, accessibility)

Status: accepted; wave 1 implemented

The goal of 0.2 is that an app can be built from Kompound alone: navigation, data, input and feedback components that the 0.1 line lacked,
plus the foundations they need (density, theme presets and import, localisation) and a form layer on top of the fields. Every new component
follows `COMPONENT_SPEC.md` (style parameter, states, accessibility, demo, tests); this plan only fixes the order and the decisions.

## Waves

| Wave | Content | State |
|------|---------|-------|
| 1 Foundation | density (`KDensity`), theme presets and import (`KThemeSpec`, `KThemePresets`, `KThemeImport`), localisation (`KompoundStrings`), JSON tree moved to `:kompound` | done |
| 2 Navigation and layout | `KTabRow`, `KNavigationBar`, `KNavigationRail`, `KNavigationDrawer`, `KContextMenuArea`, `KSplitPane`, `KSkeleton` / shimmer | done |
| 3 Data | `KDataTable` (sortable, virtualized), `KTreeView`, `KSparkline`, `KBarChart`, `KLineChart` | done |
| 4 Input | `KCommandPalette`, `KCombobox`, `KTagInput`, `KTimePicker`, `KColorPicker` | planned |
| 5 Forms and accessibility | form state and validation (`KFormState`, `KForm`, `KFormField`), accessibility audit (keyboard matrix document, automated semantics checks over every demo) | planned |

## Decisions

- **Density** is a token (`KompoundTheme.tokens.density`) that default styles read; Compact is about 80% control height and 70% padding,
  Spacious about 115% and 125%. Text never changes size. Compact is below the 48dp touch target and says so in its KDoc (C-042 asks for
  an explicit token: this is it).
- **Localisation uses code bundles, not Compose Resources** (amends C-082). `KompoundStrings` is an immutable class with every built-in text;
  English, German, French, Spanish and Italian ship, `KompoundTheme` picks one from the device locale, and any field can be overridden with
  `copy`. Reasons: Compose Resources load asynchronously on the web targets, cannot be replaced by a composition local, and a plain object is
  testable (a unit test checks that every bundle translates every text). Component parameters still win over the bundle.
- **Theme import is JSON only and lenient**: Material Theme Builder exports and design tokens (Tokens Studio, W3C) are read by role name, unknown
  keys become warnings and missing roles keep the Material defaults. No network, no Figma API.
- **Theme presets are generated from seed colours** with a small HSL tone ramp (no HCT dependency) and `on` colours picked for 4.5:1 contrast; a test
  checks every pair of every preset in light and dark. `KThemePresets.fromSeeds` lets apps make their own.
- **The JSON tree is public API of `:kompound`** (`tech.kloos.kompound.json`); `kompound-graph` keeps typealiases so its code and callers are unchanged.

## Acceptance for every component of the release

Demo with controls and a compile-checked usage sample; tests for semantics (role, state, description), keyboard operation, disabled state and
the density and RTL variants where they apply; entry in README, AGENT_GUIDE and CHANGELOG; included in the registry smoke test (light, dark, RTL,
200% font).

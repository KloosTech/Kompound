# Maestro UI tests (Android)

End-to-end tests of every Kompound component on a real Android device. Plan and decisions: [ADR 0008](../docs/adr/0008-android-ui-testing-with-maestro.md);
devices and their quirks: [devices.md](devices.md). This is phase 1 (the harness); the `maestro.sh` wrapper, device setup script and the
component flows come in the next phases, so for now the commands are the plain Maestro CLI.

## How a flow reaches a component

The catalog has a **harness** in its `maestro` build type (it is not in the published APK): a deep link opens one demo, alone on screen,
in a chosen environment.

```
kompound://demo/<demo-id>?theme=dark&font=2.0&rtl=true&density=compact&lang=en&bare=true&control=Enabled::false&control=Steps::4
```

| Query | Meaning | Default |
|---|---|---|
| `<demo-id>` | the demo's id (`slider.basic`) or qualified id (`showcase/slider.basic`) | required |
| `theme` | `light` or `dark` | `light` |
| `font` | font scale 0.5 to 3.0, absolute (the device's own scale is ignored) | `1.0` |
| `rtl` | `true` mirrors the layout | `false` |
| `density` | `compact`, `comfortable`, `spacious` | `comfortable` |
| `lang` | language of Kompound's own labels: `en`, `de`, `fr`, `es`, `it` | `en` |
| `bare` | `false` shows the demo inside the normal catalog instead | `true` |
| `control` (repeat) | `Name::value` presets a control: `true`/`false`, a choice's label, a number, text. `::` because names may contain `=`; url-encode the rest | demo defaults |

Test ids the harness exposes (they are Android resource ids, so `id:` works in a flow):

| id | what |
|---|---|
| `harness:ready` | appears when the demo is composed and two frames have passed; its description is the qualified demo id. Wait for it instead of for time |
| `harness:preview` | the demo itself |
| `harness:controls` | the controls card |
| `control:<name>` | the control (a switch, a text field, a slider, the chip group of a choice) |
| `control:<name>:value` | a slider's readout |
| `control:<name>=<option>` | one chip of a choice |

Components themselves carry no test ids. Flows find them by visible text and content description, which is also what the accessibility audit guards.

## Layout

```
maestro/
  config.yaml                 workspace: which folders hold flows
  .maestro-version            the CLI version tests were written against
  devices.md                  device registry and one-time device setup
  flows/
    _lib/open-demo.yaml       open a demo (env: DEMO, THEME, FONT, RTL, DENSITY, LANG, CONTROLS) and wait for harness:ready
    components/<category>/<demo-id>/00-smoke.yaml ...
    suites/all-demos-open.yaml   every demo opens (tags: suite, slow)
  spike/                      phase 0 experiments (not tests)
```

## Run (phase 1, by hand)

Prerequisites: Maestro CLI 2.11.0 (`~/.maestro/bin`), JDK 17 or 21 as `JAVA_HOME`, `adb`, the phone set up as in `devices.md`.

```
./gradlew :catalog:androidApp:assembleMaestro
adb -s <serial> install -r -g catalog/androidApp/build/outputs/apk/maestro/androidApp-maestro.apk
maestro --device <serial> test maestro/flows --config maestro/config.yaml --include-tags smoke \
  --test-output-dir build/maestro/artifacts --format junit --output build/maestro/report.xml
```

`--include-tags suite` runs the "every demo opens" suite (about 75 s for 67 demos on the OnePlus 9 Pro). The app id of the harness build is
`tech.kloos.kompound.catalog.maestro` (it installs next to the published catalog).

## Conventions (short; the full list is ADR 0008, section 4.3)
Selectors: `id` for harness and controls, then visible text, then description. No sleeps: assert, or `extendedWaitUntil`. Every flow opens its demo through
`_lib/open-demo.yaml`. Tags: `smoke`, `component:<id>`, `category:<name>`, `suite`, `slow`, `quarantine`. The JUnit class name is the demo id.

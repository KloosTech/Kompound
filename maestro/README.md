# Maestro UI tests (Android)

End-to-end tests of every Kompound component on a real Android device. Plan and decisions: [ADR 0008](../docs/adr/0008-android-ui-testing-with-maestro.md);
devices and their quirks: [devices.md](devices.md). Phases 1 and 2 are in place (harness, scripts, first pilot components); the other components follow one per pull request.

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
    _lib/                     shared subflows, never run as tests: open-demo, preview-see / -not-see / -tap, assert-enabled / -disabled,
                              assert-checked / -unchecked, assert-selected / -unselected, assert-row-checked / -unchecked,
                              assert-no-crash, strings.js (every Kompound label in five languages, generated)
    components/<category>/<demo-id>/
      00-smoke.yaml  10-interact.yaml  20-states.yaml  30-env.yaml   (tags: smoke, interact, states, env)
      _core.yaml              assertions in the caller's environment (run by 30 for each environment, never alone)
      _interact.yaml          taps and typing (run by 10 once, and by 30 once in a stress environment)
    suites/all-demos-open.yaml   every demo opens (tags: suite, slow)
  scripts/                    maestro.sh, device-setup.sh, scaffold.sh, coverage.sh, prune-allowlist.sh, demo-ids.sh, timings.sh, gen-strings.py
  coverage-allowlist.txt      demos without a smoke flow (empty: all demos are covered; a new demo must come with its flows)
  FRAMEWORK_LOG.md            what changed in the framework and why
  spike/                      phase 0 experiments (not tests)
```

## Run

Prerequisites: Maestro CLI at the version in `.maestro-version` (`~/.maestro/bin`, or `MAESTRO_HOME`), a JDK 17 or 21 (found automatically, or `JAVA_HOME`), `adb`, and the phone set up as in `devices.md`.

```
maestro/scripts/maestro.sh --tags smoke                    # one flow per demo: the gate
maestro/scripts/maestro.sh --component button.primary      # every flow of one component
maestro/scripts/maestro.sh --tags env --device <serial>    # the environment matrix on a given device
maestro/scripts/maestro.sh --component slider.basic --repeat 20    # stability check; reports flaky flows
maestro/scripts/maestro.sh --flow maestro/flows/components/buttons/button.primary/20-states.yaml --continuous   # while writing
maestro/scripts/device-setup.sh --check                    # what is wrong with the phone, changing nothing
maestro/scripts/scaffold.sh <demo-id> --text "Headline"    # skeleton flows for a demo
maestro/scripts/coverage.sh                                # every demo has a smoke flow (no device needed; CI runs it)
maestro/scripts/timings.sh                                 # time per command type and the ten slowest steps of the last run
python3 maestro/scripts/gen-strings.py                     # regenerate _lib/strings.js after KompoundStrings changed
```

`maestro.sh` builds the `maestro` build type, installs it when the APK changed, checks the device, runs the selection (default excludes `quarantine`, `wip`, `todo`),
writes `build/maestro/report-<n>.xml` (JUnit), `build/maestro/artifacts/` (screenshots, hierarchy, logs, per run) and `build/maestro/summary.txt`, and exits with the
CLI's code. `--help` lists every option. `--tags suite` runs the "every demo opens" suite (about 75 s for 67 demos on the OnePlus 9 Pro).

## Conventions (short; the full list is ADR 0008, section 4.3)
Selectors: `id` for harness and controls, then visible text, then description. Assert and tap **in the preview** through `_lib/preview-*` (the controls card repeats many
words). No sleeps: assert, or `extendedWaitUntil`. Every flow opens its demo through `_lib/open-demo.yaml`. Tags: `smoke`, `interact`, `states`, `env`, `component:<id>`,
`category:<name>`, `suite`, `slow`, `quarantine`, `wip`, `todo` (skeleton, not run). The JUnit class name is the demo id. A new component = `scaffold.sh`, then fill in `_core`, `10`, `20`, `30`.

## Traps (each cost a run; details in FRAMEWORK_LOG.md)
- **Never reinstall the Maestro driver** (`maestro hierarchy`, or `maestro test` without `--no-reinstall-driver`): the phone's battery exemption for it is lost and steps stall for 50 s (see `devices.md`). `maestro.sh` handles it; probe a screen with a throwaway flow (`takeScreenshot`, or `copyTextFrom` plus `evalScript console.log`).
- `hideKeyboard` presses Back when no keyboard is up and then leaves the app. Close overlays by tapping the scrim.
- The preview helpers are scoped above the controls card: on a page longer than the screen, or with the keyboard up, the card is off screen and every scoped assertion fails. Hide the keyboard first, or use plain selectors on long pages.
- `enabled: true` or `checked: false` together with `containsChild` never match in Maestro 2.11: use the negative helpers (`assert-enabled`, `assert-unchecked`, ...).
- `eraseText` deletes backwards from the caret, which lands mid-text in RTL: reset between scenarios instead of editing a field twice. Flows that tap by position (sliders, split pane, tag input, colour hex field, slide to confirm) run their interaction in the default environment or without RTL.

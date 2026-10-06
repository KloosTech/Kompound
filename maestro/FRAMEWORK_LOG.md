# Framework log

Every change to the Maestro framework (scripts, `_lib`, conventions, the harness) is one entry: what was missing, what changed, which earlier flows were touched.
Newest first. Component pull requests add entries here when they need something new (ADR 0008, section 5).

## 2026-10-06, pilots 2 and 3: KTextField and KPasswordField
- **Missing**: Maestro parses every flow it discovers, also those tagged `todo`, and rejects a file without a command (`Commands Section Required`). **Changed**: `scaffold.sh` puts an `open-demo` step into every skeleton.
- **Missing**: Kompound's own labels change with the language, so a flow that looks for "Show password" fails under `LANG: de`. **Added**: `_lib/strings.js` (the words flows need, in all five languages; `runScript` with `LANG`, then `${output.s.showPassword}`). Keep it in sync with `KompoundStrings`.
- **Learned**: the field's own node has no text until something is typed (placeholder and label are separate nodes), so tap the placeholder text or, for a field without one, the content description inside the field (`text: "Password"`, `index: 1`, scoped above the controls). Typing costs 2.5 s plus 2 s to close the keyboard, so typing flows stay short. The `env` flows are tagged `slow` (over a minute each).
- Real behaviour checked on the phone: typing and erasing, a character limit with its counter, disabled and read-only fields ignore typing, an error message, password text hidden and shown with the eye (also in German), strength meter label changes.

## 2026-10-06, pilot 1: KButton
- **Missing**: text selectors also match the controls card (`Filled` is a variant chip as well as a button), so assertions passed or failed for the wrong element. **Changed**: `_lib/preview-see`, `preview-not-see`, `preview-tap` scope every selector to the preview with `above: id harness:controls`. The convention is now "assert and tap in the preview through these helpers".
- **Missing**: no way to assert a disabled button; the text node reports `enabled=true`, only its container is disabled. **Changed**: `_lib/assert-disabled` and `assert-enabled` use `enabled` plus `containsChild` (relational selectors), scoped to the preview.
- **Missing**: a place for behaviour shared by the default and the environment flows. **Changed**: per component a `_core.yaml` (never run alone: `config.yaml` excludes `_*.yaml`) holds the behaviour; `10-interact` runs it once, `30-env` runs it under dark, RTL, font 2.0, compact and German.
- **Missing**: scripts. **Added**: `maestro.sh` (build, install by apk hash, device check, run, JUnit, summary, `--repeat` flake report), `device-setup.sh` (`--check`, `--strict`), `scaffold.sh`, `coverage.sh` with `coverage-allowlist.txt` (ratchet) and `demo-ids.sh`.
- **Learned**: a screen that animates forever (a loading spinner) makes Maestro wait for it to settle, which costs up to 20 s per step on that screen; open the state with `control=Loading::true` and assert, do not tap around on it. A tap costs 2 to 5 s on this phone even when nothing changes; the "environments" flow of KButton takes about two minutes for that reason, so core flows keep to one or two taps. Animations are still at 1.0 on the phone (adb may not change them): measure again after setting them off by hand.

## 2026-10-06, phase 1: harness
- Deep link, bare screen, `harness:ready`, tagged controls, `maestro` build type, `open-demo`, the first smoke flow and the "every demo opens" suite. See ADR 0008, appendix B.

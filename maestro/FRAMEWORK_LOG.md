# Framework log

Every change to the Maestro framework (scripts, `_lib`, conventions, the harness) is one entry: what was missing, what changed, which earlier flows were touched.
Newest first. Component pull requests add entries here when they need something new (ADR 0008, section 5).

## 2026-10-06, pilot 1: KButton
- **Missing**: text selectors also match the controls card (`Filled` is a variant chip as well as a button), so assertions passed or failed for the wrong element. **Changed**: `_lib/preview-see`, `preview-not-see`, `preview-tap` scope every selector to the preview with `above: id harness:controls`. The convention is now "assert and tap in the preview through these helpers".
- **Missing**: no way to assert a disabled button; the text node reports `enabled=true`, only its container is disabled. **Changed**: `_lib/assert-disabled` and `assert-enabled` use `enabled` plus `containsChild` (relational selectors), scoped to the preview.
- **Missing**: a place for behaviour shared by the default and the environment flows. **Changed**: per component a `_core.yaml` (never run alone: `config.yaml` excludes `_*.yaml`) holds the behaviour; `10-interact` runs it once, `30-env` runs it under dark, RTL, font 2.0, compact and German.
- **Missing**: scripts. **Added**: `maestro.sh` (build, install by apk hash, device check, run, JUnit, summary, `--repeat` flake report), `device-setup.sh` (`--check`, `--strict`), `scaffold.sh`, `coverage.sh` with `coverage-allowlist.txt` (ratchet) and `demo-ids.sh`.
- **Learned**: a screen that animates forever (a loading spinner) makes Maestro wait for it to settle, which costs up to 20 s per step on that screen; open the state with `control=Loading::true` and assert, do not tap around on it. A tap costs 2 to 5 s on this phone even when nothing changes; the "environments" flow of KButton takes about two minutes for that reason, so core flows keep to one or two taps. Animations are still at 1.0 on the phone (adb may not change them): measure again after setting them off by hand.

## 2026-10-06, phase 1: harness
- Deep link, bare screen, `harness:ready`, tagged controls, `maestro` build type, `open-demo`, the first smoke flow and the "every demo opens" suite. See ADR 0008, appendix B.

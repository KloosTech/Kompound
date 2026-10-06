# Phase 0 spike flows (ADR 0008)

Throwaway flows used on 2026-10-06 to measure Maestro 2.11.0 against the existing catalog APK on the OnePlus 9 Pro. They are kept as
examples of what was tried, not as tests. They navigate through the catalog's own list and search (slow and fragile on purpose: that is
the finding); the real flows will use the harness deep link (ADR 0008, section 4.1).

| Flow | What it measured |
|---|---|
| `01-open-kbutton.yaml` | launch, tap a list item, assert, `takeScreenshot` (path must be relative to the output folder) |
| `03-slider.yaml` | `scrollUntilVisible` over the 67-item list (54 s) |
| `04-slider-precision.yaml` | `swipe` and `tapOn point` precision on `KSlider`, reading the value with `copyTextFrom` + `evalScript` console output |
| `05-ime.yaml` | `inputText`, `hideKeyboard`, Back, `eraseText` timings and behaviour with Gboard |
| `06-speed.yaml` | `tapOn` with and without `retryTapIfNoChange` / `waitToSettleTimeoutMs` |
| `07-visual.yaml` | `assertScreenshot` whole screen and with `cropOn` (needs a baseline `base-kbutton.png` taken with `takeScreenshot`) |

Run one: `maestro --device <serial> test maestro/spike/05-ime.yaml --test-output-dir build/maestro/spike` under JDK 17 or 21.
Results are in ADR 0008, appendix A.

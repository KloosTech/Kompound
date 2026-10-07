# Visual checks

Screenshots of every demo in a fixed matrix, checked by pixel analysis (layer 1, here) and later by a local vision model (layer 2). Local only, on the reference phone (1080x2412, 480 dpi).

```
maestro/scripts/maestro.sh --visual [--component <demo-id>]      # takes the screenshots (flows 40-visual.yaml, generated)
python3 maestro/visual/analyze.py build/maestro/artifacts       # findings.json, report.html, annotated/*.png
python3 -m unittest maestro/visual/test_analyze.py              # self-test with planted defects, no device
```

Matrix per demo (edit `scripts/gen-visual-flows.sh`, run it again): light, dark, RTL, font 2.0, compact; all at `lang=en`. Names: `visual__<demo>__<theme>_<dir>_f<font>_<density>_<lang>.png`.

| Check | Finds | Severity |
|---|---|---|
| `clip` | ink on the left or right screen edge: cut-off or overflowing content (font 2.0 stress) | error |
| `mirror` | the RTL shot is not the mirror image of the LTR shot: unmirrored offsets, icons, paddings | error |
| `contrast` | an element under 3:1 against its surroundings (disabled elements show up here by design) | warn |
| `align` | leading edges of stacked elements 1.3 to 4 dp apart (only with `--align`, noisy) | info |
| `regress` | pixel diff against `--baseline <dir>` (`--update-baseline` stores the current shots) | error |

## Layer 2: vision model (Ollama on the 3090, `192.168.1.8:11434`)

```
python3 maestro/visual/calibrate.py build/maestro/artifacts            # real screenshots with one planted defect each, plus clean controls
python3 maestro/visual/vlm.py score build/visual-cal --model qwen3.8:27b # how many it flags, locates, and invents
python3 maestro/visual/vlm.py review build/maestro/artifacts --model qwen3.8:27b   # vlm-findings.json for real shots
```

Calibration on 56 images (42 planted defects, 14 clean), temperature 0, thinking off:

| Model | flagged | located | clean images with a false alarm | per image |
|---|---|---|---|---|
| `qwen3.8:27b` | 33/42 | 16/42 | 6/14 | 3.6 s |
| `gemma4:31b` | 32/42 | 6/42 | 6/14 | 27.8 s |
| `qwen3.5:9b` | 28/42 | 6/42 | 7/14 (19 invented issues) | 2.0 s |
| `gemma4:26b` | 19/42 | 6/42 | 0/14 | 14.1 s |

With `--verify` (each issue is cropped and sent back for a yes/no) `qwen3.8:27b` invents half as many alarms (3 of 14 clean images) but flags 22 instead of 33 of the planted defects: it throws out the subtle ones (shifted, missing, recoloured), so it is off by default.

On the 335 real screenshots `qwen3.8:27b` reported 347 issues (207 "missing", 123 "clipped"). Sampling them, almost all are wrong: content that continues below the screen edge read as "clipped", a sibling's feature read as "missing", deliberate ellipsis and horizontally scrolling code read as truncation. The one plausible hit was a truncated table cell at font 2.0 (by design). **Precision on real screens is far too low to be useful as it is.** Layer 1 is the check; ideas that may work better are a light-versus-dark and LTR-versus-RTL comparison asked as "what differs besides colour or direction", and close-ups of single components with the design intent in the prompt.

"located" means the centre of the model's box lies near the planted defect. No model is good enough to gate on: use it to rank shots for a human, and re-run `score` after any prompt or model change.

Not done yet: token palette check (needs the theme colours as data), sibling spacing consistency, a verification pass over each vlm finding, baselines in a storage decided later.

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

Not done yet: token palette check (needs the theme colours as data), sibling spacing consistency, layer 2 (vision model on the 3090, see the ADR), baselines in a storage decided later.

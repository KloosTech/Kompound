#!/usr/bin/env python3
"""Builds the calibration set for the vision model (layer 2): real screenshots with one planted defect each, plus untouched copies.

    calibrate.py <screenshots folder> [--out build/visual-cal] [--seed 7] [--demos 14]

Writes <out>/<id>.png and <out>/manifest.json: [{file, demo, defect, box, expected}] where defect is "none" for the controls.
Defects (drawn on the screenshot, so the true answer is known):
  contrast   the label colour is changed to a light grey
  clipped    the right half of an element is cut off with the page colour (a truncated label)
  shifted    one element is moved 36 px (12 dp) sideways, out of line with its neighbours
  overlap    a copy of an element is pasted over its neighbour
  missing    a filled element (button, pill, switch, chip) is painted over with the page colour
  recolor    a filled element gets a hue that is not in the theme
"""
import argparse
import json
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).parent))
import analyze as A

DEFECTS = ["contrast", "clipped", "shifted", "overlap", "missing", "recolor"]


def pick(boxes, rng, filled, min_w=120, min_h=24):
    """An element big enough to see; filled=True prefers solid shapes (large and tall), False prefers text lines."""
    ok = [b for b in boxes if b[2] - b[0] >= min_w and b[3] - b[1] >= min_h and b[3] - b[1] <= (200 if filled else 80)]
    return rng.choice(ok) if ok else None


def plant(img, a, bg, boxes, defect, rng):
    """Returns (new image, box in full-image coordinates, expected text) or None when the page has no suitable element."""
    top = A.TOP
    im = img.copy()
    px = im.load()
    if defect in ("contrast", "clipped", "shifted", "overlap"):
        b = pick(boxes, rng, filled=False)
    else:
        b = pick(boxes, rng, filled=True, min_w=80, min_h=40)
    if not b:
        return None
    x0, y0, x1, y1 = b[0], b[1] + top, b[2], b[3] + top
    bgc = tuple(int(v) for v in bg)
    region = im.crop((x0, y0, x1, y1))
    if defect == "contrast":
        arr = np.asarray(region, dtype=np.int16)
        ink = np.abs(arr - np.array(bgc)).max(axis=2) > A.INK
        light = np.array([min(255, c + 22) if sum(bgc) > 380 else max(0, c - 22) for c in bgc])
        arr[ink] = light
        im.paste(Image.fromarray(arr.astype(np.uint8)), (x0, y0))
        what = "text that is almost the colour of the background"
    elif defect == "clipped":
        mid = x0 + (x1 - x0) // 2
        im.paste(Image.new("RGB", (x1 - mid, y1 - y0), bgc), (mid, y0))
        what = "an element that is cut off halfway"
    elif defect == "shifted":
        im.paste(Image.new("RGB", (x1 - x0, y1 - y0), bgc), (x0, y0))
        dx = 36 if x1 + 36 < im.width else -36
        im.paste(region, (x0 + dx, y0))
        what = "an element out of line with its neighbours"
    elif defect == "overlap":
        others = [o for o in boxes if o != b and abs((o[1] + top) - y0) < 220 and o[0] < x1 and o[2] > x0 - 200]
        t = rng.choice(others) if others else None
        if not t:
            return None
        im.paste(region, (t[0] + 10, t[1] + top + 8))
        what = "two elements drawn on top of each other"
    elif defect == "missing":
        im.paste(Image.new("RGB", (x1 - x0, y1 - y0), bgc), (x0, y0))
        what = "an element that is missing"
    elif defect == "recolor":
        arr = np.asarray(region, dtype=np.int16)
        solid = np.abs(arr - np.array(bgc)).max(axis=2) > 60
        arr[solid] = (arr[solid][:, [1, 2, 0]] * 0.5 + np.array([140, 220, 40]) * 0.5).clip(0, 255)
        im.paste(Image.fromarray(arr.astype(np.uint8)), (x0, y0))
        what = "an element with an off-theme colour"
    return im, [x0, y0, x1, y1], what


def main():
    p = argparse.ArgumentParser()
    p.add_argument("folder")
    p.add_argument("--out", default="build/visual-cal")
    p.add_argument("--seed", type=int, default=7)
    p.add_argument("--demos", type=int, default=14)
    args = p.parse_args()
    rng = random.Random(args.seed)
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    shots = {}
    for f in sorted(Path(args.folder).rglob("visual__*_light_ltr_f1.0_comfortable_en.png")):
        m = A.NAME.search(f.name)
        shots[m["demo"]] = f
    # demos with enough going on to carry a defect
    usable = [d for d in sorted(shots) if d not in ("theme.presets", "skeleton.loading", "chart.simple") and not d.startswith("graph.")]
    demos = rng.sample(usable, min(args.demos, len(usable)))
    manifest, n = [], 0
    for demo in demos:
        img, arr = A.load(shots[demo])
        cont = arr[A.TOP:arr.shape[0] - A.BOTTOM]
        bg = A.page_color(cont)
        boxes = A.elements(A.ink_mask(cont, bg))
        # controls
        name = f"{n:03d}.png"
        img.save(out / name)
        manifest.append(dict(file=name, demo=demo, defect="none", box=None, expected="nothing wrong"))
        n += 1
        for defect in rng.sample(DEFECTS, 3):
            res = plant(img, arr, bg, boxes, defect, rng)
            if not res:
                continue
            im, box, what = res
            name = f"{n:03d}.png"
            im.save(out / name)
            manifest.append(dict(file=name, demo=demo, defect=defect, box=box, expected=what))
            n += 1
    (out / "manifest.json").write_text(json.dumps(manifest, indent=1))
    kinds = {k: sum(1 for m in manifest if m["defect"] == k) for k in ["none"] + DEFECTS}
    print(f"{len(manifest)} images in {out}: {kinds}")


if __name__ == "__main__":
    main()

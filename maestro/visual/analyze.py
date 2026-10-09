#!/usr/bin/env python3
"""Layer 1 of the visual checks (maestro/visual/README.md): deterministic pixel analysis of the screenshots that
`maestro.sh --visual` takes. No model, only numpy and Pillow.

    analyze.py <screenshots folder> [--out DIR] [--density 480] [--baseline DIR] [--update-baseline] [--align]

Reads every visual__<demo>__<theme>_<dir>_f<font>_<density>_<lang>.png below the folder and writes
<out>/findings.json, <out>/report.html and <out>/annotated/*.png. Exit code 1 when a finding has severity "error".

maestro/visual/expectations.json lists demos that are exempt from a check on purpose, with the reason.

Checks (dp = px * 160 / density):
  clip      ink touches the left or right screen edge (content cut off or overflowing)           error
  mirror    the RTL shot is not the mirror image of the LTR shot (block layout, not glyphs)      error
  contrast  an element (text or filled shape) has less than 3:1 against its surroundings         warn
  align     leading edges of stacked elements that miss each other by a few dp                   info
  regress   differs from the stored baseline (only with --baseline)                              error
"""
import argparse
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

NAME = re.compile(r"visual__(?P<demo>.+?)__(?P<theme>[a-z]+)_(?P<dir>ltr|rtl)_f(?P<font>[0-9.]+)_(?P<density>[a-z]+)_(?P<lang>[a-z]+)(?:_fuzz(?P<fuzz>[0-9]+))?\.png$")
TOP, BOTTOM = 130, 90          # px cut off for the status bar and the gesture bar (reference phone, 1080x2412)
INK = 24                       # channel difference from the page colour that counts as drawn
GAP_PX = 24                    # a horizontal gap this wide ends an element


def load(path):
    img = Image.open(path).convert("RGB")
    return img, np.asarray(img, dtype=np.int16)


def page_color(a):
    q = (a // 4).reshape(-1, 3).astype(np.int32)
    key = np.bincount((q[:, 0] << 12) | (q[:, 1] << 6) | q[:, 2], minlength=1 << 18).argmax()
    return np.array([key >> 12, (key >> 6) & 63, key & 63]) * 4 + 2


def ink_mask(a, bg):
    return np.abs(a - bg).max(axis=2) > INK


def runs(flags, min_gap=1):
    """[(start, end)) of True runs in a 1-d bool array; runs closer than min_gap are merged."""
    out, start, last = [], None, None
    for i, f in enumerate(flags):
        if f:
            if start is None:
                start = i
            elif i - last > min_gap:
                out.append((start, last + 1))
                start = i
            last = i
    if start is not None:
        out.append((start, last + 1))
    return out


def elements(ink):
    """Bounding boxes (x0, y0, x1, y1) of ink groups: horizontal bands split at wide column gaps."""
    boxes = []
    for y0, y1 in runs(ink.any(axis=1)):
        band = ink[y0:y1]
        for x0, x1 in runs(band.any(axis=0), GAP_PX):
            rows = np.flatnonzero(band[:, x0:x1].any(axis=1))
            boxes.append((x0, y0 + int(rows[0]), x1, y0 + int(rows[-1]) + 1))
    return boxes


def lum(c):
    c = np.asarray(c, dtype=float) / 255
    c = np.where(c <= 0.03928, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)
    return 0.2126 * c[..., 0] + 0.7152 * c[..., 1] + 0.0722 * c[..., 2]


def ratio(a, b):
    la, lb = float(lum(a)), float(lum(b))
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def check_clip(a, bg, ink, dp):
    out = []
    for name, cols in (("left", ink[:, :2]), ("right", ink[:, -2:])):
        for y0, y1 in runs(cols.any(axis=1), 6):
            if y1 - y0 >= 4:
                out.append(dict(check="clip", severity="error", box=[0 if name == "left" else a.shape[1] - 2, y0 + TOP, 2 if name == "left" else a.shape[1], y1 + TOP],
                                msg=f"content touches the {name} screen edge for {(y1 - y0) / dp:.0f} dp"))
    return out


def check_contrast(a, bg, boxes, dp):
    out = []
    for x0, y0, x1, y1 in boxes:
        if x1 - x0 < 6 or y1 - y0 < 6:
            continue
        pad = 4
        crop = a[max(0, y0 - pad):y1 + pad, max(0, x0 - pad):x1 + pad]
        local = page_color(crop)
        d = np.abs(crop - local).max(axis=2)
        if (d > INK).sum() < 12:
            continue
        iy, ix = np.unravel_index(d.argmax(), d.shape)
        r = ratio(crop[iy, ix], local)
        if r < 3.0:
            out.append(dict(check="contrast", severity="warn", box=[x0, y0 + TOP, x1, y1 + TOP], value=round(r, 2),
                            msg=f"{r:.1f}:1 against its surroundings (needs 3:1); {int((x1 - x0) / dp)}x{int((y1 - y0) / dp)} dp element"))
    return out


def check_align(boxes, rtl, dp, width):
    """Leading edges (left in LTR, right in RTL) of elements that nearly line up but do not."""
    edges = [(width - b[2] if rtl else b[0], b) for b in boxes if b[3] - b[1] >= 8]
    out = []
    for i, (e1, b1) in enumerate(edges):
        for e2, b2 in edges[i + 1:]:
            diff = abs(e1 - e2)
            if 4 <= diff <= 12 and abs(b1[1] - b2[1]) < 600:
                out.append(dict(check="align", severity="info", box=[min(b1[0], b2[0]), b1[1] + TOP, max(b1[2], b2[2]), b2[3] + TOP], value=round(diff / dp, 1),
                                msg=f"leading edges differ by {diff / dp:.1f} dp"))
    return out[:6]


def close_x(ink, width=30):
    """Fill horizontal gaps narrower than width px, so a line of text becomes one bar (glyph shapes do not mirror, the block layout must)."""
    c = np.cumsum(np.pad(ink, ((0, 0), (width, width))).astype(np.int32), axis=1)
    dil = (c[:, 2 * width:] - c[:, :-2 * width]) > 0                 # dilated by width px each side
    c = np.cumsum(np.pad(dil, ((0, 0), (width, width)), constant_values=True).astype(np.int32), axis=1)
    return (c[:, 2 * width:] - c[:, :-2 * width]) == 2 * width       # eroded again


def blocks(ink, size=16):
    ink = close_x(ink)
    h, w = ink.shape
    h, w = h // size * size, w // size * size
    return ink[:h, :w].reshape(h // size, size, w // size, size).mean(axis=(1, 3)) > 0.35


def dilate(m):
    p = np.pad(m, 1)
    return p[:-2, 1:-1] | p[2:, 1:-1] | p[1:-1, :-2] | p[1:-1, 2:] | m | p[:-2, :-2] | p[2:, 2:] | p[:-2, 2:] | p[2:, :-2]


def check_mirror(ltr, rtl, out_dir, demo):
    """Layout of the RTL shot against the flipped LTR shot, one block (16 px) of tolerance so anti-aliasing and glyph shapes do not count."""
    a, b = blocks(ltr[:, ::-1]), blocks(rtl)       # flip at pixel level: the block grid must stay aligned
    n = min(a.shape[0], b.shape[0])
    a, b = a[:n], b[:n]
    union = (a | b).sum()
    if union == 0:
        return []
    only_a, only_b = a & ~dilate(b), b & ~dilate(a)
    diff = (only_a.sum() + only_b.sum()) / union
    if diff <= 0.05:
        return []
    vis = np.zeros((*a.shape, 3), dtype=np.uint8) + 255
    vis[a & b] = (200, 200, 200)
    vis[a & ~b] = (235, 190, 190)
    vis[b & ~a] = (190, 190, 235)
    vis[only_a] = (230, 60, 60)     # only in the mirrored LTR shot
    vis[only_b] = (60, 60, 230)     # only in the RTL shot
    path = out_dir / "annotated" / f"{demo}__mirror.png"
    Image.fromarray(vis).resize((vis.shape[1] * 6, vis.shape[0] * 6), Image.NEAREST).save(path)
    return [dict(check="mirror", severity="error", box=[0, TOP, ltr.shape[1], TOP + ltr.shape[0]], value=round(float(diff), 2), image=str(path.relative_to(out_dir)),
                 msg=f"RTL layout differs from the mirrored LTR layout by {diff:.0%} of the drawn area (red: LTR only, blue: RTL only)")]


def check_baseline(a, base_path, out_dir, name):
    if not base_path.exists():
        return [dict(check="regress", severity="info", box=[0, 0, 0, 0], msg="no baseline yet")]
    b = np.asarray(Image.open(base_path).convert("RGB"), dtype=np.int16)
    if b.shape != a.shape:
        return [dict(check="regress", severity="error", box=[0, 0, 0, 0], msg=f"size changed {b.shape[:2]} -> {a.shape[:2]}")]
    d = np.abs(a - b).max(axis=2) > 32
    d[:TOP] = False
    d[-BOTTOM:] = False
    frac = d.mean()
    if frac < 0.001:
        return []
    ys, xs = np.nonzero(d)
    return [dict(check="regress", severity="error", box=[int(xs.min()), int(ys.min()), int(xs.max()), int(ys.max())], value=round(float(frac), 4),
                 msg=f"{frac:.2%} of the pixels differ from the baseline")]


def annotate(img, findings, path):
    d = ImageDraw.Draw(img)
    colors = dict(error=(220, 30, 30), warn=(240, 150, 0), info=(40, 120, 220))
    for f in findings:
        if f["box"][2] > 0:
            d.rectangle(f["box"], outline=colors[f["severity"]], width=4)
    img.save(path)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("folder")
    p.add_argument("--out", default="build/visual")
    p.add_argument("--density", type=int, default=480)
    p.add_argument("--baseline")
    p.add_argument("--update-baseline", action="store_true")
    p.add_argument("--align", action="store_true", help="also report near-miss alignment (noisy, needs a human look)")
    args = p.parse_args()
    dp = args.density / 160
    out_dir = Path(args.out)
    (out_dir / "annotated").mkdir(parents=True, exist_ok=True)

    expected = json.loads((Path(__file__).parent / "expectations.json").read_text())
    shots = {}
    for f in sorted(Path(args.folder).rglob("visual__*.png")):
        m = NAME.search(f.name)
        if m:
            shots[f.name] = (m.groupdict(), f)       # a later run of the same name wins
    if not shots:
        sys.exit(f"no visual__*.png below {args.folder}")

    findings, by_demo, content = [], defaultdict(dict), {}
    for name, (meta, path) in shots.items():
        img, a = load(path)
        cont = a[TOP:a.shape[0] - BOTTOM]
        bg = page_color(cont)
        ink = ink_mask(cont, bg)
        boxes = elements(ink)
        fuzz = meta["fuzz"] is not None      # random themes: contrast and mirror do not apply, clipping does
        fs = check_clip(cont, bg, ink, dp) + ([] if fuzz else check_contrast(cont, bg, boxes, dp)) + (check_align(boxes, meta["dir"] == "rtl", dp, a.shape[1]) if args.align else [])
        if args.baseline:
            base = Path(args.baseline) / meta["demo"] / name
            if args.update_baseline:
                base.parent.mkdir(parents=True, exist_ok=True)
                img.save(base)
            else:
                fs += check_baseline(a, base, out_dir, name)
        fs = [f for f in fs if meta["demo"] not in expected.get(f["check"], {})]
        for f in fs:
            f.update(demo=meta["demo"], shot=name)
        if any(f["severity"] != "info" for f in fs):
            annotate(img.copy(), fs, out_dir / "annotated" / name)
        findings += fs
        content[name] = ink
        by_demo[meta["demo"]][(meta["theme"], meta["dir"], meta["font"], meta["density"], meta["fuzz"])] = name

    for demo, d in by_demo.items():
        if demo in expected.get("mirror", {}):
            continue
        l, r = d.get(("light", "ltr", "1.0", "comfortable", None)), d.get(("light", "rtl", "1.0", "comfortable", None))
        if l and r:
            for f in check_mirror(content[l], content[r], out_dir, demo):
                f.update(demo=demo, shot=r)
                findings.append(f)

    (out_dir / "findings.json").write_text(json.dumps(findings, indent=1))
    write_report(out_dir, findings, len(shots), len(by_demo))
    counts = defaultdict(int)
    for f in findings:
        counts[f["severity"]] += 1
    print(f"{len(shots)} screenshots, {len(by_demo)} demos: {counts['error']} errors, {counts['warn']} warnings, {counts['info']} info -> {out_dir}/report.html")
    sys.exit(1 if counts["error"] else 0)


def write_report(out_dir, findings, n_shots, n_demos):
    order = dict(error=0, warn=1, info=2)
    rows = []
    for f in sorted(findings, key=lambda f: (order[f["severity"]], f["demo"], f["check"])):
        img = f.get("image") or f"annotated/{f['shot']}"
        shown = f'<a href="{img}"><img src="{img}" height="220"></a>' if (out_dir / img).exists() else ""
        rows.append(f'<tr class="{f["severity"]}"><td>{f["severity"]}</td><td>{f["demo"]}</td><td>{f["check"]}</td><td>{f["msg"]}<br><small>{f["shot"]}</small></td><td>{shown}</td></tr>')
    html = f"""<!doctype html><meta charset="utf-8"><title>Kompound visual report</title>
<style>body{{font:14px system-ui;margin:24px}}table{{border-collapse:collapse}}td{{border:1px solid #ccc;padding:6px;vertical-align:top}}
.error td:first-child{{background:#fbb}}.warn td:first-child{{background:#fd9}}.info td:first-child{{background:#bdf}}</style>
<h1>Visual report</h1><p>{n_shots} screenshots, {n_demos} demos, {len(findings)} findings.</p>
<table><tr><th>severity</th><th>demo</th><th>check</th><th>finding</th><th>shot</th></tr>{''.join(rows)}</table>"""
    (out_dir / "report.html").write_text(html)


if __name__ == "__main__":
    main()

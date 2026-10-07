#!/usr/bin/env python3
"""Layer 2 of the visual checks: a vision model on a local Ollama server looks at screenshots and lists layout and colour defects.

    vlm.py score <calibration folder> --model gemma4:26b [--host http://192.168.1.8:11434] [--limit N] [--verify]
    vlm.py review <screenshots folder> --model gemma4:26b --out build/visual

`score` runs the model on the planted-defect set from calibrate.py and prints, per defect kind, how often it flagged the image and how often it pointed at the
right place, and how many issues it invents on the untouched controls. `review` runs it on real screenshots and writes <out>/vlm-findings.json.
Only the standard library is used.
"""
import argparse
import base64
import io
import json
import sys
import time
import urllib.request
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import analyze as A
from PIL import Image

KINDS = ["contrast", "clipped", "misaligned", "overlap", "missing", "color", "spacing", "other"]
SCHEMA = {
    "type": "object",
    "properties": {"issues": {"type": "array", "items": {"type": "object", "properties": {
        "kind": {"type": "string", "enum": KINDS},
        "box": {"type": "array", "items": {"type": "integer"}, "minItems": 4, "maxItems": 4},
        "description": {"type": "string"},
    }, "required": ["kind", "box", "description"]}}},
    "required": ["issues"],
}
PROMPT = """You review a screenshot of a UI component library demo on an Android phone. The page shows the component under test at the top, and below it a card titled "Controls" with the demo's settings; that card is test tooling, still check it for visual defects but it has the same rules.

Find visual defects only: text or controls clipped or cut off, elements overlapping, an element that is missing where its siblings have one, text hard to read against its background, a colour that does not fit the rest of the page, an element out of line with its neighbours (left edges, baselines, equal gaps), uneven or cramped spacing.
Do not comment on content, wording or taste. If the page looks right, return an empty list. Report only defects you can see clearly.

For each defect give its kind, a box [x0, y0, x1, y1] around it in a 0..1000 coordinate system (0,0 is the top left of the image, 1000,1000 the bottom right), and one short sentence.
Answer with JSON only: {"issues": [...]}"""


def prepare(path, max_side=1344):
    """The part of the screenshot below the status bar and above the empty bottom, as PNG bytes, and the pixel box it covers in the original."""
    img, arr = A.load(path)
    cont = arr[A.TOP:arr.shape[0] - A.BOTTOM]
    ink = A.ink_mask(cont, A.page_color(cont))
    rows = ink.any(axis=1).nonzero()[0]
    y1 = min(cont.shape[0], int(rows[-1]) + 80) if len(rows) else cont.shape[0]
    crop = img.crop((0, A.TOP, img.width, A.TOP + y1))
    scale = min(1.0, max_side / max(crop.size))
    sent = crop.resize((int(crop.width * scale), int(crop.height * scale)), Image.LANCZOS)
    buf = io.BytesIO()
    sent.save(buf, "PNG")
    return buf.getvalue(), (0, A.TOP, img.width, A.TOP + y1)


def ask(host, model, png, timeout=600):
    body = {"model": model, "stream": False, "think": False, "format": SCHEMA, "options": {"temperature": 0, "num_ctx": 8192},
            "messages": [{"role": "user", "content": PROMPT, "images": [base64.b64encode(png).decode()]}]}
    req = urllib.request.Request(f"{host}/api/chat", json.dumps(body).encode(), {"Content-Type": "application/json"})
    t = time.time()
    with urllib.request.urlopen(req, timeout=timeout) as r:
        data = json.load(r)
    text = data["message"]["content"]
    try:
        issues = json.loads(text).get("issues", [])
    except json.JSONDecodeError:
        issues = [dict(kind="other", box=[0, 0, 1000, 1000], description="unparsable answer: " + text[:80])]
    return issues, time.time() - t


VERIFY_SCHEMA = {"type": "object", "properties": {"real": {"type": "boolean"}, "reason": {"type": "string"}}, "required": ["real", "reason"]}


def verify(host, model, png, area, issue, timeout=600):
    """Second look: the part of the screenshot around one reported issue, with a yes/no question. Returns (real, reason)."""
    img = Image.open(io.BytesIO(png))
    b = [max(0, min(1000, v)) for v in issue["box"]]
    w, h = img.size
    x0, y0, x1, y1 = b[0] * w / 1000, b[1] * h / 1000, b[2] * w / 1000, b[3] * h / 1000
    padx, pady = max(60, (x1 - x0) * 0.5), max(60, (y1 - y0) * 0.5)
    crop = img.crop((int(max(0, x0 - padx)), int(max(0, y0 - pady)), int(min(w, x1 + padx)), int(min(h, y1 + pady))))
    if crop.width < 2 or crop.height < 2:
        return False, "empty region"
    if max(crop.size) < 400:
        f = 400 / max(crop.size)
        crop = crop.resize((int(crop.width * f), int(crop.height * f)), Image.LANCZOS)
    buf = io.BytesIO()
    crop.save(buf, "PNG")
    question = (f"This is a close-up of part of a UI screenshot. Someone reported a possible visual defect here ({issue['kind']}: {issue['description']}). "
                "Look at the close-up and decide whether a real visual defect is visible: something cut off, overlapping, missing, unreadable against its background, "
                "off-colour or out of line with its neighbours. Normal design (rounded corners, outlines, disabled grey, spacing that looks deliberate) is not a defect. "
                'Answer JSON only: {"real": true or false, "reason": "one short sentence"}')
    body = {"model": model, "stream": False, "think": False, "format": VERIFY_SCHEMA, "options": {"temperature": 0, "num_ctx": 8192},
            "messages": [{"role": "user", "content": question, "images": [base64.b64encode(buf.getvalue()).decode()]}]}
    req = urllib.request.Request(f"{host}/api/chat", json.dumps(body).encode(), {"Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=timeout) as r:
        try:
            d = json.loads(json.load(r)["message"]["content"])
            return bool(d.get("real")), str(d.get("reason", ""))
        except json.JSONDecodeError:
            return True, "unparsable verdict"


def to_pixels(box, area):
    x0, y0, x1, y1 = area
    w, h = x1 - x0, y1 - y0
    b = [max(0, min(1000, v)) for v in box]
    return [x0 + b[0] * w / 1000, y0 + b[1] * h / 1000, x0 + b[2] * w / 1000, y0 + b[3] * h / 1000]


def near(issue_box, truth, area, slack=0.6):
    """The centre of the model's box lies inside the planted box grown by slack of its size (models are loose with boxes)."""
    b = to_pixels(issue_box, area)
    cx, cy = (b[0] + b[2]) / 2, (b[1] + b[3]) / 2
    tw, th = truth[2] - truth[0], truth[3] - truth[1]
    return truth[0] - slack * tw <= cx <= truth[2] + slack * tw and truth[1] - slack * th <= cy <= truth[3] + slack * th


def score(args):
    folder = Path(args.folder)
    manifest = json.loads((folder / "manifest.json").read_text())[: args.limit or None]
    stats = defaultdict(lambda: dict(n=0, flagged=0, located=0, issues=0))
    times, rows = [], []
    for i, m in enumerate(manifest):
        png, area = prepare(folder / m["file"])
        issues, secs = ask(args.host, args.model, png)
        if args.verify:
            t = time.time()
            issues = [x for x in issues if len(x.get("box", [])) == 4 and verify(args.host, args.model, png, area, x)[0]]
            secs += time.time() - t
        times.append(secs)
        s = stats[m["defect"]]
        s["n"] += 1
        s["issues"] += len(issues)
        if m["defect"] != "none":
            s["flagged"] += bool(issues)
            s["located"] += any(near(x["box"], m["box"], area) for x in issues if len(x.get("box", [])) == 4)
        rows.append(dict(file=m["file"], defect=m["defect"], seconds=round(secs, 1), issues=issues))
        print(f"[{i + 1}/{len(manifest)}] {m['file']} {m['defect']:9s} {secs:5.1f}s  {len(issues)} issues", flush=True)
    planted = [k for k in stats if k != "none"]
    print(f"\n{args.model}: {sum(times) / len(times):.1f} s per image")
    for k in sorted(planted):
        s = stats[k]
        print(f"  {k:9s} flagged {s['flagged']}/{s['n']}  located {s['located']}/{s['n']}")
    n = sum(stats[k]["n"] for k in planted)
    print(f"  all       flagged {sum(stats[k]['flagged'] for k in planted)}/{n}  located {sum(stats[k]['located'] for k in planted)}/{n}")
    c = stats["none"]
    print(f"  controls  {c['issues']} invented issues on {c['n']} clean images, {sum(1 for r in rows if r['defect'] == 'none' and r['issues'])} images with at least one")
    Path(args.out).mkdir(parents=True, exist_ok=True)
    (Path(args.out) / f"score-{args.model.replace(':', '_').replace('/', '_')}{'-verify' if args.verify else ''}.json").write_text(json.dumps(rows, indent=1))


def review(args):
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    findings = []
    shots = sorted(Path(args.folder).rglob("visual__*.png"))
    for i, f in enumerate(shots):
        png, area = prepare(f)
        issues, secs = ask(args.host, args.model, png)
        if args.verify:
            kept = []
            for x in issues:
                real, why = verify(args.host, args.model, png, area, x) if len(x.get("box", [])) == 4 else (False, "no box")
                if real:
                    kept.append(x)
            issues = kept
        for x in issues:
            findings.append(dict(demo=A.NAME.search(f.name)["demo"], shot=f.name, check="vlm", severity="warn", kind=x["kind"],
                                 box=[round(v) for v in to_pixels(x["box"], area)], msg=x["description"]))
        print(f"[{i + 1}/{len(shots)}] {f.name} {secs:.1f}s {len(issues)} issues", flush=True)
    (out / "vlm-findings.json").write_text(json.dumps(findings, indent=1))
    print(f"{len(findings)} findings -> {out}/vlm-findings.json")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("command", choices=["score", "review"])
    p.add_argument("folder")
    p.add_argument("--model", default="gemma4:26b")
    p.add_argument("--host", default="http://192.168.1.8:11434")
    p.add_argument("--limit", type=int)
    p.add_argument("--verify", action="store_true", help="send the region of every issue back to the model for a yes/no second look")
    p.add_argument("--out", default="build/visual")
    args = p.parse_args()
    (score if args.command == "score" else review)(args)


if __name__ == "__main__":
    main()

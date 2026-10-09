"""Self-test of analyze.py with synthetic pages: every check must catch a planted defect and stay quiet on a clean page.
Run: python3 -m unittest maestro/visual/test_analyze.py   (no device, no model)"""
import sys
import tempfile
import unittest
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).parent))
import analyze as A

W, H, DP = 1080, 1200, 3.0


def page(draw_fn, bg=(250, 250, 250)):
    img = Image.new("RGB", (W, H), bg)
    draw_fn(ImageDraw.Draw(img))
    a = np.asarray(img, dtype=np.int16)
    b = A.page_color(a)
    ink = A.ink_mask(a, b)
    return a, b, ink, A.elements(ink)


def clean(d):
    d.rectangle((48, 100, 400, 160), fill=(30, 30, 30))
    d.rectangle((48, 220, 500, 280), fill=(30, 30, 30))
    d.rounded_rectangle((800, 100, 1000, 180), 40, fill=(90, 50, 160))


class Checks(unittest.TestCase):
    def test_clean_page_has_no_findings(self):
        a, bg, ink, boxes = page(clean)
        self.assertEqual(A.check_clip(a, bg, ink, DP) + A.check_contrast(a, bg, boxes, DP) + A.check_align(boxes, False, DP, W), [])

    def test_low_contrast_is_found(self):
        a, bg, ink, boxes = page(lambda d: d.rectangle((48, 100, 400, 160), fill=(205, 205, 205)))
        self.assertEqual([f["check"] for f in A.check_contrast(a, bg, boxes, DP)], ["contrast"])

    def test_dark_theme_contrast_ok_and_bad(self):
        ok = page(lambda d: d.rectangle((48, 100, 400, 160), fill=(220, 220, 220)), bg=(20, 20, 24))
        bad = page(lambda d: d.rectangle((48, 100, 400, 160), fill=(50, 50, 56)), bg=(20, 20, 24))
        self.assertEqual(A.check_contrast(ok[0], ok[1], ok[3], DP), [])
        self.assertEqual(len(A.check_contrast(bad[0], bad[1], bad[3], DP)), 1)

    def test_clip_is_found(self):
        a, bg, ink, _ = page(lambda d: d.rectangle((0, 100, 300, 160), fill=(30, 30, 30)))
        self.assertEqual([f["check"] for f in A.check_clip(a, bg, ink, DP)], ["clip"])
        a, bg, ink, _ = page(lambda d: d.rectangle((900, 100, W, 160), fill=(30, 30, 30)))
        self.assertEqual(len(A.check_clip(a, bg, ink, DP)), 1)

    def test_near_miss_alignment_is_found_exact_alignment_is_not(self):
        def near(d):
            d.rectangle((48, 100, 400, 160), fill=(30, 30, 30))
            d.rectangle((57, 220, 500, 280), fill=(30, 30, 30))      # 9 px = 3 dp off
        self.assertEqual(len(A.check_align(page(near)[3], False, DP, W)), 1)
        self.assertEqual(A.check_align(page(clean)[3], False, DP, W), [])

    def test_rtl_mirror(self):
        ltr = page(clean)[2]
        mirrored = page(lambda d: (d.rectangle((W - 400, 100, W - 48, 160), fill=(30, 30, 30)), d.rectangle((W - 500, 220, W - 48, 280), fill=(30, 30, 30)),
                                   d.rounded_rectangle((80, 100, 280, 180), 40, fill=(90, 50, 160))))[2]
        unmirrored = page(clean)[2]
        with tempfile.TemporaryDirectory() as t:
            out = Path(t)
            (out / "annotated").mkdir()
            self.assertEqual(A.check_mirror(ltr, mirrored, out, "x"), [])
            self.assertEqual([f["check"] for f in A.check_mirror(ltr, unmirrored, out, "x")], ["mirror"])

    def test_baseline_regression(self):
        a = page(clean)[0]
        b = page(lambda d: (clean(d), d.rectangle((48, 600, 400, 700), fill=(200, 30, 30))))[0]
        with tempfile.TemporaryDirectory() as t:
            base = Path(t) / "b.png"
            Image.fromarray(a.astype(np.uint8)).save(base)
            self.assertEqual(A.check_baseline(a, base, Path(t), "x"), [])
            self.assertEqual(A.check_baseline(b, base, Path(t), "x")[0]["severity"], "error")


if __name__ == "__main__":
    unittest.main()

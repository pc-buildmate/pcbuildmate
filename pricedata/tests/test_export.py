from __future__ import annotations

import csv
import json
import sys
import tempfile
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from pricepipe.export import export  # noqa: E402


class ExportTest(unittest.TestCase):
    def setUp(self) -> None:
        self.seed = ROOT / "seeds" / "2026-11.csv"
        self.tmp = tempfile.TemporaryDirectory()
        self.out = Path(self.tmp.name)

    def tearDown(self) -> None:
        self.tmp.cleanup()

    def test_exports_catalogue_and_manifest(self) -> None:
        manifest = export(
            self.seed,
            self.out,
            version="2026.11",
            generated_at="2026-11-04T00:00:00+00:00",
        )

        self.assertEqual(26, manifest["productCount"])
        self.assertEqual(26, manifest["priceCount"])
        self.assertEqual("2026-11-04", manifest["priceDate"])
        self.assertEqual(["CPU", "GPU", "MEMORY", "MOTHERBOARD", "PSU", "STORAGE"], manifest["categories"])

        catalogue = json.loads((self.out / "catalogue-2026.11.json").read_text(encoding="utf-8"))
        self.assertEqual(26, len(catalogue["components"]))
        self.assertEqual("2026.11", catalogue["catalogueVersion"])
        self.assertTrue(catalogue["isDemo"])
        for c in catalogue["components"]:
            self.assertIn(c["category"], {"CPU", "MOTHERBOARD", "MEMORY", "GPU", "PSU", "STORAGE"})
            self.assertGreater(c["priceCny"], 0)
            self.assertEqual("2026-11-04", c["priceDate"])
            self.assertTrue(c["attributes"])

    def test_igpu_flag_is_preserved_in_products(self) -> None:
        export(self.seed, self.out, version="2026.11")
        with open(self.out / "products.csv", newline="", encoding="utf-8") as f:
            rows = {r["product_id"]: r for r in csv.DictReader(f)}

        self.assertEqual("NO", rows["cpu-demo-7500f"]["integrated_graphics"])
        self.assertEqual("YES", rows["cpu-demo-9600x"]["integrated_graphics"])
        self.assertEqual("1", rows["cpu-demo-7500f"]["recommendable"])

    def test_no_case_or_cooler_in_price_dataset(self) -> None:
        export(self.seed, self.out, version="2026.11")
        catalogue = json.loads((self.out / "catalogue-2026.11.json").read_text(encoding="utf-8"))
        categories = {c["category"] for c in catalogue["components"]}

        self.assertNotIn("CASE", categories)
        self.assertNotIn("COOLER", categories)


if __name__ == "__main__":
    unittest.main()


class MergeTest(unittest.TestCase):
    def test_active_plus_overlay_keeps_anchors(self) -> None:
        import json
        import tempfile
        from pathlib import Path
        from pricepipe.merge import merge

        with tempfile.TemporaryDirectory() as tmp:
            out = Path(tmp) / "catalogue-app.json"
            info = merge(
                ROOT / "data" / "derived" / "catalogue-2026.11.json",
                ROOT / "overlay" / "demo-anchors.json",
                out,
            )
            self.assertEqual(30, info["componentCount"])
            data = json.loads(out.read_text(encoding="utf-8"))
            ids = {c["componentId"] for c in data["components"]}
            for cid in ("case-demo-320", "case-demo-itx", "cooler-demo-160", "cooler-demo-175"):
                self.assertIn(cid, ids)
            self.assertEqual("2026.11", data["catalogueVersion"])

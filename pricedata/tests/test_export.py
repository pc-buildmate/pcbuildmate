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

    # ---------------------------------------------------------- state 覆盖

    def _seed_with_override(self, product_id: str, path: str, state: object, keep_value: bool = True) -> Path:
        """复制 seed，把某记录的某个 spec 加上 state 键（可选去掉 value）。"""
        out = Path(self.tmp.name) / "seed.csv"
        with open(self.seed, newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            fields = reader.fieldnames
            rows = list(reader)
        for row in rows:
            if row["product_id"] != product_id:
                continue
            specs = json.loads(row["specs_json"])
            spec = dict(specs[path])
            spec["state"] = state
            if not keep_value:
                spec.pop("value", None)
            specs[path] = spec
            row["specs_json"] = json.dumps(specs, ensure_ascii=False)
        with open(out, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fields)
            writer.writeheader()
            writer.writerows(rows)
        return out

    def _seed_with_spec(self, product_id: str, path: str, spec: dict) -> Path:
        """复制 seed，把某记录的某个 spec 整体替换成给定对象。"""
        out = Path(self.tmp.name) / "seed.csv"
        with open(self.seed, newline="", encoding="utf-8") as f:
            reader = csv.DictReader(f)
            fields = reader.fieldnames
            rows = list(reader)
        for row in rows:
            if row["product_id"] != product_id:
                continue
            specs = json.loads(row["specs_json"])
            specs[path] = spec
            row["specs_json"] = json.dumps(specs, ensure_ascii=False)
        with open(out, "w", newline="", encoding="utf-8") as f:
            writer = csv.DictWriter(f, fieldnames=fields)
            writer.writeheader()
            writer.writerows(rows)
        return out

    def test_numeric_must_match_the_value(self) -> None:
        seed = self._seed_with_spec("mb-demo-b650", "memorySlots", {"value": "4", "numeric": 5})
        with self.assertRaises(ValueError) as ctx:
            export(seed, self.out, version="2026.11")
        self.assertIn("不一致", str(ctx.exception))

    def test_numeric_without_value_is_rejected(self) -> None:
        seed = self._seed_with_spec("mb-demo-b650", "memorySlots", {"numeric": 4})
        with self.assertRaises(ValueError) as ctx:
            export(seed, self.out, version="2026.11")
        self.assertIn("没有 value 却带 numeric", str(ctx.exception))

    def test_blank_value_with_reported_state_is_rejected(self) -> None:
        seed = self._seed_with_spec("mb-demo-b650", "socket", {"value": "", "state": "REPORTED"})
        with self.assertRaises(ValueError) as ctx:
            export(seed, self.out, version="2026.11")
        self.assertIn("state=REPORTED 但没有 value", str(ctx.exception))

    def test_unknown_spec_key_is_rejected(self) -> None:
        seed = self._seed_with_spec(
            "mb-demo-b650", "socket", {"value": "AM5", "numeric": None, "statee": "REPORTED"}
        )
        with self.assertRaises(ValueError) as ctx:
            export(seed, self.out, version="2026.11")
        self.assertIn("未知的 spec 键", str(ctx.exception))

    def test_no_override_reproduces_committed_derived_outputs(self) -> None:
        """不带覆盖时，四个产物与仓库里已提交的 derived 文件逐字节一致（换行符按平台归一）。"""
        committed = ROOT / "data" / "derived"
        export(
            self.seed,
            self.out,
            version="2026.11",
            generated_at="2026-11-04T00:00:00+00:00",
        )
        for name in ("products.csv", "prices.csv", "catalogue-2026.11.json", "manifest.json"):
            got = (self.out / name).read_bytes().replace(b"\r\n", b"\n")
            want = (committed / name).read_bytes().replace(b"\r\n", b"\n")
            self.assertEqual(want, got, f"{name} 与已提交产物不一致")

    def test_field_state_override_changes_only_that_field(self) -> None:
        export(self.seed, self.out, version="2026.11")
        baseline = json.loads((self.out / "catalogue-2026.11.json").read_text(encoding="utf-8"))

        seed = self._seed_with_override("mb-demo-b650", "socket", "REPORTED")
        out2 = Path(self.tmp.name) / "overridden"
        export(seed, out2, version="2026.11")
        changed = json.loads((out2 / "catalogue-2026.11.json").read_text(encoding="utf-8"))

        base_by_id = {c["componentId"]: c for c in baseline["components"]}
        over_by_id = {c["componentId"]: c for c in changed["components"]}
        self.assertEqual(set(base_by_id), set(over_by_id))

        for cid, base in base_by_id.items():
            over = over_by_id[cid]
            if cid != "mb-demo-b650":
                self.assertEqual(base, over, f"{cid} 不该受影响")
                continue
            self.assertEqual("REPORTED", over["attributes"]["socket"]["state"])
            self.assertEqual(base["attributes"]["socket"]["value"], over["attributes"]["socket"]["value"])
            for path, attr in base["attributes"].items():
                if path == "socket":
                    continue
                self.assertEqual(attr["state"], over["attributes"][path]["state"], path)

    def test_validate_stage_also_rejects_bad_state(self) -> None:
        """`pricepipe.cli validate` 走的是同一个 validate()，不能只在 export 里拦。"""
        from pricepipe.export import validate
        from pricepipe.models import load_seed

        seed = self._seed_with_override("mb-demo-b650", "socket", "MAYBE")
        errors = validate(load_seed(seed))
        self.assertTrue(any("state 只能是" in e for e in errors), errors)

    def test_invalid_state_is_rejected(self) -> None:
        seed = self._seed_with_override("mb-demo-b650", "socket", "MAYBE")
        with self.assertRaises(ValueError) as ctx:
            export(seed, self.out, version="2026.11")
        self.assertIn("state 只能是 VERIFIED/REPORTED/EMPTY", str(ctx.exception))

    def test_empty_state_with_value_is_rejected(self) -> None:
        seed = self._seed_with_override("mb-demo-b650", "socket", "EMPTY")
        with self.assertRaises(ValueError) as ctx:
            export(seed, self.out, version="2026.11")
        self.assertIn("state=EMPTY 但带 value", str(ctx.exception))

    def test_empty_state_with_numeric_is_rejected(self) -> None:
        """EMPTY 只挡 value 不够：3D 的 num() 是 numeric ?: value，不查 state。"""
        seed = self._seed_with_override("mb-demo-b650", "memorySlots", "EMPTY", keep_value=False)
        with self.assertRaises(ValueError) as ctx:
            export(seed, self.out, version="2026.11")
        self.assertIn("state=EMPTY 但带 numeric", str(ctx.exception))

    def test_reported_state_without_value_is_rejected(self) -> None:
        seed = self._seed_with_override("mb-demo-b650", "socket", "REPORTED", keep_value=False)
        with self.assertRaises(ValueError) as ctx:
            export(seed, self.out, version="2026.11")
        self.assertIn("state=REPORTED 但没有 value", str(ctx.exception))


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

from __future__ import annotations

import csv
import json
from dataclasses import dataclass
from pathlib import Path

CATEGORIES = {"CPU", "MOTHERBOARD", "MEMORY", "GPU", "PSU", "STORAGE"}
REQUIRED = {
    "product_id",
    "category",
    "brand",
    "model",
    "release_year",
    "status",
    "price_cny",
    "observed_at",
    "source_url",
    "source_type",
    "confidence",
    "specs_json",
}


@dataclass(frozen=True)
class Row:
    product_id: str
    category: str
    brand: str
    model: str
    part_number: str
    release_year: int
    platform: str
    integrated_graphics: str
    status: str
    price_cny: int
    list_price_cny: int | None
    promo_price_cny: int | None
    channel: str
    observed_at: str
    source_url: str
    source_type: str
    confidence: str
    captured_by: str
    notes: str
    specs: dict[str, dict]


def load_seed(path: str | Path) -> list[Row]:
    rows: list[Row] = []
    with open(path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        missing = REQUIRED - set(reader.fieldnames or [])
        if missing:
            raise ValueError(f"seed 缺少列: {sorted(missing)}")
        for line_no, raw in enumerate(reader, start=2):
            try:
                rows.append(_row(raw, line_no))
            except Exception as e:
                raise ValueError(f"{path}:{line_no}: {e}") from e
    return rows


def _row(raw: dict, line_no: int) -> Row:
    if raw["category"] not in CATEGORIES:
        raise ValueError(f"未知类目 {raw['category']}")
    specs = json.loads(raw["specs_json"])
    if not isinstance(specs, dict) or not specs:
        raise ValueError("specs_json 必须是非空对象")
    return Row(
        product_id=raw["product_id"].strip(),
        category=raw["category"].strip(),
        brand=raw["brand"].strip(),
        model=raw["model"].strip(),
        part_number=(raw.get("part_number") or "").strip(),
        release_year=int(raw["release_year"]),
        platform=(raw.get("platform") or "").strip(),
        integrated_graphics=(raw.get("integrated_graphics") or "").strip().upper(),
        status=raw["status"].strip(),
        price_cny=int(raw["price_cny"]),
        list_price_cny=_opt_int(raw.get("list_price_cny")),
        promo_price_cny=_opt_int(raw.get("promo_price_cny")),
        channel=raw["channel"].strip(),
        observed_at=raw["observed_at"].strip(),
        source_url=raw["source_url"].strip(),
        source_type=raw["source_type"].strip(),
        confidence=raw["confidence"].strip(),
        captured_by=(raw.get("captured_by") or "").strip(),
        notes=(raw.get("notes") or "").strip(),
        specs=specs,
    )


def _opt_int(value: str | None) -> int | None:
    if value is None or value.strip() == "":
        return None
    return int(value)

from __future__ import annotations

import csv
import json
from collections import defaultdict
from dataclasses import asdict
from datetime import datetime, timezone
from pathlib import Path

from .models import CATEGORIES, Row, load_seed

DATASET_START = "2020-01-01"
MIN_RECOMMEND_YEAR = 2020
SCHEMA_VERSION = 1


def validate(rows: list[Row]) -> list[str]:
    errors: list[str] = []
    seen: set[tuple] = set()
    for r in rows:
        if r.release_year < 2020:
            errors.append(f"{r.product_id}: release_year<2020，需 historical 层处理")
        if r.price_cny <= 0:
            errors.append(f"{r.product_id}: price_cny<=0")
        if r.list_price_cny is not None and r.promo_price_cny is not None and r.promo_price_cny > r.list_price_cny:
            errors.append(f"{r.product_id}: promo > list")
        if r.category == "CPU" and r.integrated_graphics not in {"YES", "NO", ""}:
            errors.append(f"{r.product_id}: integrated_graphics 必须是 YES/NO")
        key = (r.product_id, r.observed_at, r.channel, "new", r.source_type)
        if key in seen:
            errors.append(f"{r.product_id}: 重复观测 {key}")
        seen.add(key)
        if not r.source_url.startswith("http"):
            errors.append(f"{r.product_id}: source_url 不是 URL")
        errors.extend(_spec_state_errors(r))
    return errors


def _spec_state_errors(r: Row) -> list[str]:
    """字段级 state 覆盖的取值校验（ADR-0011 决策 5 是**每字段**的证据等级）。

    升级为 `VERIFIED`（低 confidence 记录里把某字段提成 VERIFIED）是允许的，但这个开关
    既能降级也能提级：PR 描述里必须给出处，否则审查无法判断证据够不够。
    """
    errors: list[str] = []
    for path, spec in r.specs.items():
        if not isinstance(spec, dict):
            errors.append(f"{r.product_id}.{path}: spec 必须是对象")
            continue
        state = spec.get("state")
        if state is None:
            continue
        value = spec.get("value")
        numeric = spec.get("numeric")
        if state not in {"VERIFIED", "REPORTED", "EMPTY"}:
            errors.append(f"{r.product_id}.{path}: state 只能是 VERIFIED/REPORTED/EMPTY，实际 {state!r}")
        elif state == "EMPTY":
            # 与 tools/audit-catalogue.mjs 的「EMPTY 必须无值」一致，避免 validate 过、audit 报错。
            # numeric 也要挡：3D 预览的 num() 是 numeric ?: value，不查 state，会拿 EMPTY 的数去画尺寸。
            if value is not None:
                errors.append(f"{r.product_id}.{path}: state=EMPTY 但带 value={value!r}")
            if numeric is not None:
                errors.append(f"{r.product_id}.{path}: state=EMPTY 但带 numeric={numeric!r}")
        elif value is None:
            errors.append(f"{r.product_id}.{path}: state={state} 但没有 value")
    return errors


def export(
    seed: str | Path,
    out_dir: str | Path,
    version: str,
    generated_at: str | None = None,
) -> dict:
    rows = load_seed(seed)
    errs = validate(rows)
    if errs:
        raise ValueError("validation failed:\n" + "\n".join(errs))

    out = Path(out_dir)
    out.mkdir(parents=True, exist_ok=True)

    # 最新观测价（同产品多条时按 observed_at 取最大）
    latest: dict[str, Row] = {}
    for r in rows:
        cur = latest.get(r.product_id)
        if cur is None or (r.observed_at, r.channel) > (cur.observed_at, cur.channel):
            latest[r.product_id] = r

    products = sorted(latest.values(), key=lambda r: r.product_id)
    prices = sorted(rows, key=lambda r: (r.product_id, r.observed_at, r.channel))

    _write_products(out / "products.csv", products)
    _write_prices(out / "prices.csv", prices)
    catalogue = _catalogue(products, version)
    (out / f"catalogue-{version}.json").write_text(
        json.dumps(catalogue, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    price_date = max(r.observed_at for r in rows)
    manifest = {
        "datasetVersion": version,
        "schemaVersion": SCHEMA_VERSION,
        "datasetStart": DATASET_START,
        "minRecommendYear": MIN_RECOMMEND_YEAR,
        "generatedAt": generated_at or datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "priceDate": price_date,
        "productCount": len(products),
        "priceCount": len(prices),
        "categories": sorted({r.category for r in products}),
        "sourceTypes": sorted({r.source_type for r in prices}),
        "currency": "CNY",
        "isDemo": True,
    }
    (out / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )
    return manifest


def _write_products(path: Path, rows: list[Row]) -> None:
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow([
            "product_id", "category", "brand", "model", "part_number",
            "release_year", "platform", "integrated_graphics", "status", "recommendable",
        ])
        for r in rows:
            recommendable = r.status == "active" and r.release_year >= MIN_RECOMMEND_YEAR
            w.writerow([
                r.product_id, r.category, r.brand, r.model, r.part_number,
                r.release_year, r.platform, r.integrated_graphics, r.status,
                1 if recommendable else 0,
            ])


def _write_prices(path: Path, rows: list[Row]) -> None:
    with open(path, "w", newline="", encoding="utf-8") as f:
        w = csv.writer(f)
        w.writerow([
            "observation_id", "product_id", "observed_at", "price_cny",
            "list_price_cny", "promo_price_cny", "channel", "condition",
            "source_url", "source_type", "confidence", "captured_by", "notes",
        ])
        for r in rows:
            oid = f"{r.product_id}:{r.observed_at}:{r.channel}:{r.source_type}"
            w.writerow([
                oid, r.product_id, r.observed_at, r.price_cny,
                "" if r.list_price_cny is None else r.list_price_cny,
                "" if r.promo_price_cny is None else r.promo_price_cny,
                r.channel, "new", r.source_url, r.source_type,
                r.confidence, r.captured_by, r.notes,
            ])


def _catalogue(rows: list[Row], version: str) -> dict:
    components = []
    for r in rows:
        if r.status != "active" or r.release_year < MIN_RECOMMEND_YEAR:
            continue
        attrs = {}
        for path, spec in r.specs.items():
            value = spec.get("value")
            # 字段级 state 优先（ADR-0011 决策 5：证据等级是每字段的）；
            # 没写才退回记录级 confidence 推导，行为与旧版完全一致。
            override = spec.get("state")
            attrs[path] = {
                "value": value,
                "state": override if override is not None else (
                    "EMPTY" if value is None else (
                        "VERIFIED" if r.confidence == "high" else "REPORTED"
                    )
                ),
                "sources": [],
                "conflict": False,
                "numeric": spec.get("numeric"),
            }
        components.append({
            "componentId": r.product_id,
            "category": r.category,
            "manufacturer": r.brand,
            "model": r.model,
            "partNumber": r.part_number,
            "priceCny": r.price_cny,
            "attributes": attrs,
            "catalogueVersion": version,
            "priceDate": r.observed_at,
            "isDemo": True,
        })
    return {
        "catalogueVersion": version,
        "priceDate": max(r.observed_at for r in rows),
        "isDemo": True,
        "note": "2020+ 价格快照；参考价，不承诺实时/库存；生成器见 pricedata/",
        "components": components,
    }

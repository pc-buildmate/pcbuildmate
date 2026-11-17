from __future__ import annotations

import argparse
import csv
import sys
import time
from pathlib import Path

from .adapters import colorful_store
from .export import export, validate
from .merge import merge
from .models import load_seed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser("pricepipe")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_export = sub.add_parser("export", help="seed CSV -> products/prices/catalogue/manifest")
    p_export.add_argument("--seed", required=True, type=Path)
    p_export.add_argument("--out", required=True, type=Path)
    p_export.add_argument("--version", required=True)
    p_export.add_argument("--generated-at", default=None)

    p_merge = sub.add_parser("merge", help="active snapshot + demo overlay -> App catalogue")
    p_merge.add_argument("--active", required=True, type=Path)
    p_merge.add_argument("--overlay", required=True, type=Path)
    p_merge.add_argument("--out", required=True, type=Path)

    p_validate = sub.add_parser("validate", help="只校验 seed CSV")
    p_validate.add_argument("--seed", required=True, type=Path)

    p_fetch = sub.add_parser("fetch-colorful", help="抓七彩虹实时价并与 seeds 对照（默认只读）")
    p_fetch.add_argument("--seed", required=True, type=Path)
    p_fetch.add_argument("--pages", type=int, default=0, help="0=用 index list（覆盖全）；>0=翻分页")
    p_fetch.add_argument("--page-size", type=int, default=50)
    p_fetch.add_argument("--delay", type=float, default=3.0, help="翻页间隔秒（防 429 限频）")
    p_fetch.add_argument("--observed-at", default=None)
    p_fetch.add_argument("--apply", action="store_true", help="写回 diff 记录（只改价格/来源列，observed_at 不变）")

    args = parser.parse_args(argv)

    if args.cmd == "merge":
        info = merge(args.active, args.overlay, args.out)
        print(f"OK {info['componentCount']} components -> {args.out}")
        return 0

    if args.cmd == "validate":
        errors = validate(load_seed(args.seed))
        if errors:
            print("\n".join(errors), file=sys.stderr)
            return 1
        print("OK")
        return 0

    if args.cmd == "export":
        manifest = export(args.seed, args.out, args.version, args.generated_at)
        print(
            f"OK {manifest['productCount']} products / "
            f"{manifest['priceCount']} prices -> {args.out}"
        )
        return 0

    if args.cmd == "fetch-colorful":
        return _fetch_colorful(args)

    return 1


def _fetch_colorful(args: argparse.Namespace) -> int:
    rows = load_seed(args.seed)
    if args.pages <= 0:
        observations = colorful_store.fetch_index_list(observed_at=args.observed_at)
    else:
        observations = []
        for page in range(1, args.pages + 1):
            observations.extend(
                colorful_store.fetch_page(
                    page_index=page, page_size=args.page_size, observed_at=args.observed_at
                )
            )
            if page < args.pages:
                time.sleep(max(0.0, args.delay))

    print(f"抓取 {len(observations)} 条商品；seeds colorful_store 记录：")
    matches = colorful_store.diff_seed(rows, observations)
    for m in matches:
        if m.status == "consistent":
            print(f"  [=] {m.product_id:22} {m.seed_price_cny:>6} = {m.observation.price_cny:>6}  {m.seed_model}")
        elif m.status == "diff":
            print(f"  [!] {m.product_id:22} {m.seed_price_cny:>6} -> {m.observation.price_cny:>6}  {m.seed_model}")
        elif m.status == "ambiguous":
            print(f"  [?] {m.product_id:22} 多匹配，请人工挑  {m.seed_model}")
        else:
            print(f"  [ ] {m.product_id:22} 未抓到  {m.seed_model}")

    if not args.apply:
        print("\n（只读模式；确认无误后加 --apply 写回 diff 记录）")
        return 0

    updated = _apply_matches(args.seed, matches)
    print(f"\n已更新 {updated} 条 seeds 记录（只改 price/list_price/source_url/confidence，observed_at 不变）")
    return 0


def _apply_matches(seed_path: Path, matches) -> int:
    updates = {m.product_id: m.observation for m in matches if m.status == "diff" and m.observation}
    if not updates:
        return 0
    with open(seed_path, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        fieldnames = reader.fieldnames
        rows = list(reader)
    updated = 0
    for row in rows:
        obs = updates.get(row["product_id"])
        if obs is None:
            continue
        row["price_cny"] = str(obs.price_cny)
        row["list_price_cny"] = str(obs.price_cny)
        row["source_url"] = obs.source_url
        row["confidence"] = obs.confidence
        updated += 1
    with open(seed_path, "w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)
    return updated


if __name__ == "__main__":
    raise SystemExit(main())

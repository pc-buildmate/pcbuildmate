from __future__ import annotations

import argparse
import sys
from pathlib import Path

from .export import export, validate
from .models import load_seed


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser("pricepipe")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_export = sub.add_parser("export", help="seed CSV -> products/prices/catalogue/manifest")
    p_export.add_argument("--seed", required=True, type=Path)
    p_export.add_argument("--out", required=True, type=Path)
    p_export.add_argument("--version", required=True)
    p_export.add_argument("--generated-at", default=None)

    p_validate = sub.add_parser("validate", help="只校验 seed CSV")
    p_validate.add_argument("--seed", required=True, type=Path)

    args = parser.parse_args(argv)

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

    return 1


if __name__ == "__main__":
    raise SystemExit(main())

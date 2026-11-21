from __future__ import annotations

import json
from pathlib import Path


def merge(active_path: str | Path, overlay_path: str | Path, out_path: str | Path) -> dict:
    """active（2020+ 快照）叠加 overlay（demo 机箱/散热等锚点），**整条记录**以 overlay 为准。

    注意：overlay 是记录级覆盖，会把该记录在 active 里的全部属性（含 export 写入的
    字段级 `state` 覆盖）一起替换掉。若以后某个 componentId 同时出现在 overlay 里，
    又需要保留字段级 state，得在 overlay 文件里把该属性一并写全。
    """
    active = json.loads(Path(active_path).read_text(encoding="utf-8"))
    overlay = json.loads(Path(overlay_path).read_text(encoding="utf-8"))

    by_id = {c["componentId"]: c for c in active["components"]}
    for c in overlay["components"]:
        c = dict(c)
        c["catalogueVersion"] = active["catalogueVersion"]
        c["priceDate"] = active["priceDate"]
        c["isDemo"] = True
        by_id[c["componentId"]] = c  # overlay wins

    merged = dict(active)
    merged["components"] = sorted(by_id.values(), key=lambda c: c["componentId"])
    merged["note"] = "active 2020+ snapshot + demo case/cooler anchors"
    out = Path(out_path)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(merged, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return {
        "componentCount": len(merged["components"]),
        "catalogueVersion": merged["catalogueVersion"],
        "priceDate": merged["priceDate"],
    }

"""七彩虹商城（skycolorful.com）价格 adapter —— 已接通真实 API。

**实测结论（2026-11-17）**：

- 站点是 Vite SPA，但商品数据走独立接口域名 **``https://interface.skycolorful.com``**
  （不是 ``www``）；
- 公开商品列表/详情**无需登录**，但请求必须带固定 header：
  ``User-from: Pc``、``source: Pc``、``UcSource: 30``、``version: 2.0.0``；
- 三个可用端点：
  - ``GET  /api/Index/GetGoodsIndexList`` -> ``Data.DataList[].Data[]``（分类嵌套）
  - ``GET  /api/Good/GetDetail?GoodId=<uuid>`` -> ``Data``
  - ``POST /api/Good/GetPage`` body ``{"goodPageQuery":{"PageIndex":N,"PageSize":M}}`` -> ``Data.DataList[]``
- 价格字段：``Price``（number）／``PriceStr``（string），商品名 ``Name``，ID 是 UUID。

**边界**：不做全站翻页、不绕登录/验证码、不写死 token；低频（``MIN_INTERVAL_S``）。
测试全部用 fixture，不联网；``fetch_*`` 是薄封装，便于人工跑一次真实验证。
"""
from __future__ import annotations

import json
import re
import urllib.request
from dataclasses import dataclass
from datetime import date
from typing import Any

ADAPTER_ID = "colorful_store"
CHANNEL = "colorful_store"
SOURCE_TYPE = "official"
DEFAULT_UA = "pcbuildmate-pricedata/0.1 (+https://github.com/pc-buildmate/pcbuildmate)"
MIN_INTERVAL_S = 3600  # 与 sources.example.yml 一致：最低 1 小时/次

BASE_URL = "https://interface.skycolorful.com"
FIXED_HEADERS = {
    "User-from": "Pc",
    "source": "Pc",
    "UcSource": "30",
    "version": "2.0.0",
    "Accept": "application/json",
}


@dataclass(frozen=True)
class PriceObservation:
    """一次页面/接口价观测；字段与 ``models.Row`` 的价格部分对齐。"""

    model: str | None
    price_cny: int
    source_url: str
    observed_at: str
    confidence: str
    notes: str


# ---- 抓取（真实可用；测试不调用）----

def fetch(
    url: str,
    *,
    method: str = "GET",
    data: dict[str, Any] | None = None,
    timeout: float = 15.0,
    headers: dict[str, str] | None = None,
) -> str:
    """低频抓取；自动带固定 header，POST 自动序列化 JSON。失败抛 ``URLError``。"""
    req_headers = {**FIXED_HEADERS, "User-Agent": DEFAULT_UA}
    if headers:
        req_headers.update(headers)
    body: bytes | None = None
    if data is not None:
        body = json.dumps(data, ensure_ascii=False).encode("utf-8")
        req_headers.setdefault("Content-Type", "application/json")
    req = urllib.request.Request(url, data=body, headers=req_headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read().decode("utf-8", errors="replace")


def fetch_index_list(*, observed_at: str | None = None) -> list[PriceObservation]:
    """首页商品列表（分类嵌套）。"""
    url = f"{BASE_URL}/api/Index/GetGoodsIndexList"
    return parse_goods_list(fetch(url), source_url=url, observed_at=observed_at)


def fetch_page(
    *, page_index: int = 1, page_size: int = 20, observed_at: str | None = None
) -> list[PriceObservation]:
    """分页商品列表；``goodPageQuery`` 是必须的包裹层（实测 400 若无此字段）。"""
    url = f"{BASE_URL}/api/Good/GetPage"
    payload = {"goodPageQuery": {"PageIndex": page_index, "PageSize": page_size}}
    return parse_goods_list(
        fetch(url, method="POST", data=payload), source_url=url, observed_at=observed_at
    )


def fetch_detail(good_id: str, *, observed_at: str | None = None) -> PriceObservation | None:
    """单个商品详情；``GoodId`` 是 UUID。"""
    url = f"{BASE_URL}/api/Good/GetDetail?GoodId={good_id}"
    items = parse_goods_list(fetch(url), source_url=url, observed_at=observed_at)
    return items[0] if items else None


# ---- 解析（纯函数，测试覆盖）----

def parse_goods_list(
    payload: str | dict[str, Any],
    *,
    source_url: str,
    observed_at: str | None = None,
) -> list[PriceObservation]:
    """解析 GetGoodsIndexList / GetPage / GetDetail 的响应。

    递归遍历 ``Data``，取所有同时带 ``Name`` 与 ``Price`` 的节点——
    这样同时兼容「平铺列表」「分类嵌套」「单对象」三种结构，且跳过只有名字没有价格的分类节点。
    """
    try:
        data = json.loads(payload) if isinstance(payload, str) else payload
    except json.JSONDecodeError:
        return []
    if not isinstance(data, dict):
        return []

    out: list[PriceObservation] = []

    def walk(node: Any) -> None:
        if isinstance(node, dict):
            name = node.get("Name")
            price = _to_cny(node.get("Price")) or _to_cny(node.get("PriceStr"))
            if isinstance(name, str) and name.strip() and price is not None:
                out.append(
                    _observation(
                        name.strip(), price, source_url, observed_at,
                        "七彩虹商城 API 价；低频采集",
                    )
                )
            for value in node.values():
                if isinstance(value, (dict, list)):
                    walk(value)
        elif isinstance(node, list):
            for value in node:
                walk(value)

    walk(data.get("Data"))
    return out


def parse(url: str, html: str, *, observed_at: str | None = None) -> PriceObservation | None:
    """HTML 兜底解析（SSR 页/JSON-LD）；取不到价格返回 ``None``（记 EMPTY，不猜价）。"""
    price = extract_price(html)
    if price is None:
        return None
    return _observation(extract_model(html), price, url, observed_at, "七彩虹商城页面价；低频采集")


# ---- seeds 对照（纯函数，供 CLI/人工核对）----

@dataclass(frozen=True)
class SeedMatch:
    """一条 seeds 记录与抓取结果的配对结论。"""

    product_id: str
    seed_model: str
    seed_price_cny: int
    observation: PriceObservation | None
    status: str  # consistent / diff / missing / ambiguous


def diff_seed(seed_rows: list[Any], observations: list[PriceObservation]) -> list[SeedMatch]:
    """把 ``channel=colorful_store`` 的 seeds 记录与抓取结果按型号 token 配对。

    只做匹配，**不写文件**；匹配不到/多匹配的交给人工判断。
    """
    out: list[SeedMatch] = []
    for row in seed_rows:
        if getattr(row, "channel", None) != CHANNEL:
            continue
        hits = [o for o in observations if _sig_matches(row.model, o.model)]
        if len(hits) > 1:
            out.append(SeedMatch(row.product_id, row.model, row.price_cny, None, "ambiguous"))
            continue
        if not hits:
            out.append(SeedMatch(row.product_id, row.model, row.price_cny, None, "missing"))
            continue
        obs = hits[0]
        status = "consistent" if obs.price_cny == row.price_cny else "diff"
        out.append(SeedMatch(row.product_id, row.model, row.price_cny, obs, status))
    return out


_MODEL_TOKEN = re.compile(r"(?:RTX|GTX)\s*\d{4}(?:\s*TI)?|[A-Z]\d{3,4}[A-Z]?|\d{4}(?:\s*TI)?")

_EXCLUDE_KEYWORDS = ("笔记本", "游戏本", "主机", "整机", "套装", "套餐", "配置", "定制")
_FEATURE_WORDS = ("ULTRA-S", "ULTRA-OC", "VULCAN", "CVN", "WIFI7", "WIFI6E", "SFF")


def _model_tokens(model: str) -> list[str]:
    """提取型号 token（B850M / Z890M / RTX5070 / RTX5070TI 等）。"""
    return ["".join(t.split()) for t in _MODEL_TOKEN.findall(model.upper())]


def _signature(model: str) -> str:
    """型号签名（token 串联），用于精确区分 B850 / B850M / 5070 / 5070TI。"""
    return "".join(_model_tokens(model))


def _sig_matches(seed_model: str, product_name: str | None) -> bool:
    """型号签名完全相等 + 特征词全部命中，且排除整机/笔记本。

    - ``B850`` ≠ ``B850M``；``RTX5070`` ≠ ``RTX5070TI``（防 5070 匹配 5070 Ti）；
    - ``ULTRA-S`` ≠ ``ULTRA-OC``（同型号不同 SKU）。
    """
    if not product_name:
        return False
    upper = product_name.upper()
    if any(keyword in upper for keyword in _EXCLUDE_KEYWORDS):
        return False
    seed_sig = _signature(seed_model)
    if not seed_sig or seed_sig != _signature(product_name):
        return False
    seed_features = [w for w in _FEATURE_WORDS if w in seed_model.upper()]
    return all(w in upper for w in seed_features)


def parse_json(
    payload: str | dict[str, Any],
    *,
    source_url: str,
    observed_at: str | None = None,
) -> PriceObservation | None:
    """解析通用单对象 JSON（非七彩虹结构）；七彩虹请用 ``parse_goods_list``。"""
    try:
        data = json.loads(payload) if isinstance(payload, str) else payload
    except json.JSONDecodeError:
        return None
    if not isinstance(data, dict):
        return None
    node = data.get("data", data)
    if isinstance(node, list):
        node = node[0] if node else None
    if not isinstance(node, dict):
        return None
    price = _first_number(node, ("salePrice", "price", "currentPrice", "linePrice"))
    if price is None:
        return None
    model = _first_str(node, ("goodsName", "name", "title", "productName"))
    return _observation(model, price, source_url, observed_at, "通用 JSON 价；低频采集")


# ---- HTML 解析 ----

def extract_price(html: str) -> int | None:
    """JSON-LD -> meta -> 文本兜底。"""
    for value in _jsonld_prices(html):
        price = _to_cny(value)
        if price is not None:
            return price
    for value in _meta_prices(html):
        price = _to_cny(value)
        if price is not None:
            return price
    return _text_price(html)


def extract_model(html: str) -> str | None:
    for name in _jsonld_names(html):
        if name.strip():
            return name.strip()
    for key in ("og:title", "product:title"):
        value = _meta_content(html, key)
        if value and value.strip():
            return value.strip()
    title = _tag_text(html, "title")
    return title.strip() if title else None


# ---- 内部 ----

def _observation(
    model: str | None,
    price: int,
    source_url: str,
    observed_at: str | None,
    notes: str,
) -> PriceObservation:
    return PriceObservation(
        model=model,
        price_cny=price,
        source_url=source_url,
        observed_at=observed_at or date.today().isoformat(),
        confidence="high",
        notes=notes,
    )


def _first_number(node: dict[str, Any], keys: tuple[str, ...]) -> int | None:
    for key in keys:
        if key in node:
            price = _to_cny(node[key])
            if price is not None:
                return price
    return None


def _first_str(node: dict[str, Any], keys: tuple[str, ...]) -> str | None:
    for key in keys:
        value = node.get(key)
        if isinstance(value, str) and value.strip():
            return value.strip()
    return None


def _jsonld_blocks(html: str) -> list[Any]:
    blocks: list[Any] = []
    for m in re.finditer(
        r'<script[^>]*type=["\']application/ld\+json["\'][^>]*>(.*?)</script>',
        html,
        re.S | re.I,
    ):
        try:
            blocks.append(json.loads(m.group(1).strip()))
        except json.JSONDecodeError:
            continue
    return blocks


def _iter_nodes(value: Any):
    if isinstance(value, dict):
        yield value
        for v in value.values():
            yield from _iter_nodes(v)
    elif isinstance(value, list):
        for v in value:
            yield from _iter_nodes(v)


def _as_list(value: Any) -> list[Any]:
    if value is None:
        return []
    return value if isinstance(value, list) else [value]


def _jsonld_prices(html: str) -> list[str]:
    out: list[str] = []
    for block in _jsonld_blocks(html):
        for node in _iter_nodes(block):
            if node.get("@type") == "Product":
                for offer in _as_list(node.get("offers")):
                    if isinstance(offer, dict) and "price" in offer:
                        out.append(str(offer["price"]))
    return out


def _jsonld_names(html: str) -> list[str]:
    out: list[str] = []
    for block in _jsonld_blocks(html):
        for node in _iter_nodes(block):
            if node.get("@type") == "Product" and node.get("name"):
                out.append(str(node["name"]))
    return out


def _meta_prices(html: str) -> list[str]:
    keys = ("product:price:amount", "og:price:amount")
    return [value for key in keys if (value := _meta_content(html, key))]


def _meta_content(html: str, key: str) -> str | None:
    pattern = re.compile(
        rf'<meta[^>]+(?:property|name)=["\']{re.escape(key)}["\'][^>]*content=["\']([^"\']+)["\']',
        re.I,
    )
    m = pattern.search(html)
    return m.group(1) if m else None


def _text_price(html: str) -> int | None:
    # 必须带货币符号，避免把规格数字（如 337mm）当价格
    for m in re.finditer(r"(?:¥|￥|RMB|CNY)\s*([0-9]{3,6})(?:\.\d{1,2})?", html):
        price = _to_cny(m.group(1))
        if price is not None:
            return price
    return None


def _tag_text(html: str, tag: str) -> str | None:
    m = re.search(rf"<{re.escape(tag)}[^>]*>(.*?)</{re.escape(tag)}>", html, re.S | re.I)
    if not m:
        return None
    text = re.sub(r"<[^>]+>", "", m.group(1)).strip()
    return text or None


def _to_cny(value: object) -> int | None:
    if value is None:
        return None
    try:
        amount = float(str(value).replace(",", "").strip())
    except (TypeError, ValueError):
        return None
    if amount <= 0:
        return None
    return int(round(amount))

from __future__ import annotations

import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "src"))

from pricepipe.adapters import ADAPTERS, colorful_store  # noqa: E402


# ---- HTML 兜底路径的 fixture ----

JSONLD_HTML = """<html><head>
<script type="application/ld+json">
{"@context":"https://schema.org","@type":"Product",
 "name":"iGame RTX 5070 Vulcan X OC 12GB",
 "offers":{"@type":"Offer","price":"9799","priceCurrency":"CNY"}}
</script></head><body></body></html>"""

META_HTML = """<html><head>
<meta property="product:price:amount" content="4099.00">
<meta property="og:title" content="iGame RTX 5060 Ultra W DUO OC 8GB">
</head><body></body></html>"""

TEXT_HTML = """<html><head><title>COLORFUL 战斧 RTX 5070 Ti 豪华版 SFF</title></head>
<body><div class="price">¥ 13399.00</div></body></html>"""

NO_PRICE_HTML = """<html><body><p>暂无报价</p><span>长 337mm</span></body></html>"""


# ---- 真实七彩虹 API 响应 fixture（结构取自 2026-11-17 实测）----

PAGE_JSON = """{"Data":{"DataList":[
  {"Id":"760843d0-46f6-4dd5-abf5-a8a327d1ef19","Name":"iGame X870E VULCAN OC V14","Price":3799.00,"PriceStr":"3799.00"},
  {"Id":"fa498a1e-341c-4229-89cd-7fed9bcab5ef","Name":"SIGAPES 灯牌音响","Price":238,"PriceStr":"238"}
]}}"""

INDEX_JSON = """{"Data":{"DataList":[
  {"Id":"8a254368-f055-4433-b34a-3ee28ad73225","Name":"笔记本","Sort":1,"Data":[
    {"Id":"8d436237-5708-4f43-84ae-a94f87ba26a6","Name":"七彩虹 将星 X16 Pro","Price":10999.00,"PriceStr":"10999.00"}
  ]}
]}}"""

DETAIL_JSON = """{"Data":{"Id":"8d436237-5708-4f43-84ae-a94f87ba26a6","Name":"七彩虹 将星 X16 Pro","Price":10999.00}}"""


class ColorfulStoreTest(unittest.TestCase):
    def test_registered(self) -> None:
        self.assertIs(ADAPTERS["colorful_store"], colorful_store)
        self.assertEqual("colorful_store", colorful_store.ADAPTER_ID)
        self.assertGreaterEqual(colorful_store.MIN_INTERVAL_S, 60)

    def test_real_api_constants(self) -> None:
        self.assertEqual("https://interface.skycolorful.com", colorful_store.BASE_URL)
        for key in ("User-from", "source", "UcSource", "version"):
            self.assertIn(key, colorful_store.FIXED_HEADERS)

    # ---- HTML 兜底 ----

    def test_parses_jsonld_price_and_model(self) -> None:
        obs = colorful_store.parse("https://www.skycolorful.com/p/1", JSONLD_HTML, observed_at="2026-11-17")
        self.assertIsNotNone(obs)
        assert obs is not None
        self.assertEqual(9799, obs.price_cny)
        self.assertEqual("iGame RTX 5070 Vulcan X OC 12GB", obs.model)
        self.assertEqual("2026-11-17", obs.observed_at)

    def test_parses_meta_price(self) -> None:
        self.assertEqual(4099, colorful_store.extract_price(META_HTML))
        self.assertEqual("iGame RTX 5060 Ultra W DUO OC 8GB", colorful_store.extract_model(META_HTML))

    def test_parses_text_price(self) -> None:
        self.assertEqual(13399, colorful_store.extract_price(TEXT_HTML))
        self.assertEqual("COLORFUL 战斧 RTX 5070 Ti 豪华版 SFF", colorful_store.extract_model(TEXT_HTML))

    def test_missing_price_returns_none(self) -> None:
        self.assertIsNone(colorful_store.extract_price(NO_PRICE_HTML))
        self.assertIsNone(colorful_store.parse("https://www.skycolorful.com/p/x", NO_PRICE_HTML))

    def test_jsonld_wins_over_text(self) -> None:
        html = JSONLD_HTML + '<div class="price">¥ 9999.00</div>'
        self.assertEqual(9799, colorful_store.extract_price(html))

    def test_spec_numbers_are_not_treated_as_price(self) -> None:
        self.assertIsNone(colorful_store.extract_price("<p>长 337mm，厚 61mm</p>"))

    def test_meta_price_with_spaces_and_commas(self) -> None:
        html = '<meta property="og:price:amount" content="13,399">'
        self.assertEqual(13399, colorful_store.extract_price(html))

    def test_invalid_jsonld_falls_through(self) -> None:
        html = '<script type="application/ld+json">{bad json}</script><div class="price">¥ 4099</div>'
        self.assertEqual(4099, colorful_store.extract_price(html))

    # ---- 通用 parse_json ----

    def test_parses_json_api_detail(self) -> None:
        payload = '{"code":0,"data":{"goodsName":"iGame RTX 5070","salePrice":9799}}'
        obs = colorful_store.parse_json(payload, source_url="u", observed_at="2026-11-17")
        self.assertIsNotNone(obs)
        assert obs is not None
        self.assertEqual(9799, obs.price_cny)
        self.assertEqual("iGame RTX 5070", obs.model)

    def test_json_price_string_form(self) -> None:
        obs = colorful_store.parse_json('{"data":{"price":"4099"}}', source_url="u")
        self.assertIsNotNone(obs)
        assert obs is not None
        self.assertEqual(4099, obs.price_cny)

    def test_json_missing_price_returns_none(self) -> None:
        self.assertIsNone(colorful_store.parse_json('{"code":0,"data":{"goodsName":"x"}}', source_url="u"))

    def test_json_invalid_returns_none(self) -> None:
        self.assertIsNone(colorful_store.parse_json("{not json}", source_url="u"))

    # ---- 真实七彩虹 parse_goods_list（GetPage / GetIndexList / GetDetail）----

    def test_goods_list_from_page(self) -> None:
        items = colorful_store.parse_goods_list(PAGE_JSON, source_url="u", observed_at="2026-11-17")
        self.assertEqual(2, len(items))
        self.assertEqual(3799, items[0].price_cny)
        self.assertEqual("iGame X870E VULCAN OC V14", items[0].model)
        self.assertEqual(238, items[1].price_cny)

    def test_goods_list_from_nested_index(self) -> None:
        # 分类节点「笔记本」无 Price，必须跳过；只取内层商品
        items = colorful_store.parse_goods_list(INDEX_JSON, source_url="u")
        self.assertEqual(1, len(items))
        self.assertEqual(10999, items[0].price_cny)
        self.assertEqual("七彩虹 将星 X16 Pro", items[0].model)

    def test_goods_list_from_detail(self) -> None:
        items = colorful_store.parse_goods_list(DETAIL_JSON, source_url="u")
        self.assertEqual(1, len(items))
        self.assertEqual(10999, items[0].price_cny)

    def test_goods_list_skips_nodes_without_price(self) -> None:
        self.assertEqual([], colorful_store.parse_goods_list('{"Data":{"DataList":[{"Name":"仅名字"}]}}', source_url="u"))

    def test_goods_list_null_data(self) -> None:
        self.assertEqual([], colorful_store.parse_goods_list('{"Data":null,"Code":10004}', source_url="u"))

    def test_goods_list_invalid_json(self) -> None:
        self.assertEqual([], colorful_store.parse_goods_list("{bad", source_url="u"))

    def test_goods_list_price_str_fallback(self) -> None:
        payload = '{"Data":{"DataList":[{"Name":"X","PriceStr":"4099.00"}]}}'
        items = colorful_store.parse_goods_list(payload, source_url="u")
        self.assertEqual(1, len(items))
        self.assertEqual(4099, items[0].price_cny)


class _FakeRow:
    def __init__(self, product_id: str, model: str, price_cny: int, channel: str = "colorful_store") -> None:
        self.product_id = product_id
        self.model = model
        self.price_cny = price_cny
        self.channel = channel


def _obs(name: str, price: int) -> "colorful_store.PriceObservation":
    return colorful_store.PriceObservation(name, price, "u", "2026-11-17", "high", "x")


class DiffSeedTest(unittest.TestCase):
    def test_consistent(self) -> None:
        rows = [_FakeRow("mb-demo-z890m", "iGame Z890M ULTRA W V20", 1599)]
        m = colorful_store.diff_seed(rows, [_obs("iGame Z890M ULTRA W V20", 1599)])
        self.assertEqual(1, len(m))
        self.assertEqual("consistent", m[0].status)

    def test_diff(self) -> None:
        rows = [_FakeRow("mb-demo-z890m", "iGame Z890M ULTRA W V20", 1499)]
        m = colorful_store.diff_seed(rows, [_obs("iGame Z890M ULTRA W V20", 1599)])
        self.assertEqual("diff", m[0].status)
        assert m[0].observation is not None
        self.assertEqual(1599, m[0].observation.price_cny)

    def test_missing(self) -> None:
        rows = [_FakeRow("gpu-demo-5060", "iGame RTX 5060 Ultra W DUO OC 8GB", 4099)]
        m = colorful_store.diff_seed(rows, [_obs("iGame Z890M ULTRA W V20", 1599)])
        self.assertEqual("missing", m[0].status)
        self.assertIsNone(m[0].observation)

    def test_ambiguous(self) -> None:
        # 同型号多 SKU（如白色/黑色），应判多匹配交人工
        rows = [_FakeRow("mb-demo-b850m", "iGame B850M ULTRA-S V14", 1399)]
        obs = [
            _obs("iGame B850M ULTRA-S V14", 1399),
            _obs("iGame B850M ULTRA-S V14 白色", 1299),
        ]
        m = colorful_store.diff_seed(rows, obs)
        self.assertEqual("ambiguous", m[0].status)

    def test_skips_non_colorful_channel(self) -> None:
        rows = [_FakeRow("cpu-demo-7600x", "Ryzen 5 7600X", 1500, channel="manual")]
        self.assertEqual([], colorful_store.diff_seed(rows, []))

    def test_model_tokens(self) -> None:
        self.assertIn("B850M", colorful_store._model_tokens("iGame B850M ULTRA-S V14"))
        self.assertIn("RTX5070", colorful_store._model_tokens("iGame RTX 5070 Vulcan X OC 12GB"))

    def test_notebook_is_not_a_gpu_match(self) -> None:
        # 笔记本名里也含 RTX5060，但它是整机，不能当显卡价
        rows = [_FakeRow("gpu-demo-5060", "iGame RTX 5060 Ultra W DUO OC 8GB", 4099)]
        ob = _obs("七彩虹 隐星 P16 Pro 游戏本（Ultra 7 251HX RTX5060 8GB）星风版", 11999)
        m = colorful_store.diff_seed(rows, [ob])
        self.assertEqual("missing", m[0].status)

    def test_sig_distinguishes_ti(self) -> None:
        # 5060 不能被 5060 Ti 抢走
        rows = [_FakeRow("gpu-demo-5060", "iGame RTX 5060 Ultra W DUO OC 8GB", 4099)]
        obs = [
            _obs("iGame GeForce RTX 5060 Ti Ultra OC 8GB", 4799),
            _obs("iGame GeForce RTX 5060 Ultra W DUO OC 8GB", 4099),
        ]
        m = colorful_store.diff_seed(rows, obs)
        self.assertEqual("consistent", m[0].status)
        assert m[0].observation is not None
        self.assertEqual(4099, m[0].observation.price_cny)

    def test_sig_distinguishes_b850_from_b850m(self) -> None:
        rows = [_FakeRow("mb-demo-b850-cvn", "CVN B850 GAMING PRO WIFI7 V14", 1499)]
        m = colorful_store.diff_seed(rows, [_obs("iGame B850M ULTRA-S V14", 1399)])
        self.assertEqual("missing", m[0].status)

    def test_sig_matches_exact_variant(self) -> None:
        rows = [_FakeRow("mb-demo-b850-cvn", "CVN B850 GAMING PRO WIFI7 V14", 1499)]
        m = colorful_store.diff_seed(rows, [_obs("CVN B850 GAMING  PRO WIFI7 V14", 1499)])
        self.assertEqual("consistent", m[0].status)

    def test_sig_distinguishes_ultra_s_from_ultra_oc(self) -> None:
        rows = [_FakeRow("mb-demo-b850m", "iGame B850M ULTRA-S V14", 1399)]
        m = colorful_store.diff_seed(rows, [_obs("iGame B850M ULTRA-OC V14", 1299)])
        self.assertEqual("missing", m[0].status)


if __name__ == "__main__":
    unittest.main()

# 目录价格来源

> 价格是 **2026-11-04 的参考快照**，不是实时价，也不代表库存。
> 全部记录 `isDemo=true`；厂商规格优先，价格来自官方商城/公开报价页。
> 采集口径：同一日期、同一类目、页面显示价；不把促销券后价当稳定价。

## 新增记录（2026-11-04）

| componentId | 型号 | 价格 CNY | 来源 |
|---|---|---:|---|
| `cpu-demo-9600x` | AMD Ryzen 5 9600X | 1349 | [it168 行情](https://www.it168.com/hotwords/2262_1.shtml) |
| `cpu-demo-245k` | Intel Core Ultra 5 245K | 1229 | [什么值得买 京东价](https://www.smzdm.com/p/183586915/) |
| `cpu-demo-7500f` | AMD Ryzen 5 7500F（无核显） | 999 | [ZOL CPU 报价](https://detail.zol.com.cn/cpu/) |
| `mb-demo-b850-cvn` | COLORFUL CVN B850 GAMING PRO WIFI7 V14 | 1499 | [七彩虹商城](https://www.skycolorful.com/) · [规格新闻](https://news.ifeng.com/c/8oM90MLvXtb) |
| `mb-demo-b850m` | COLORFUL iGame B850M ULTRA-S V14 | 1399 | [七彩虹商城](https://www.skycolorful.com/) · [规格新闻](https://news.17173.com/content/05202026/170650520.shtml) |
| `mb-demo-z890m` | COLORFUL iGame Z890M ULTRA W V20 | 1599 | [七彩虹商城](https://www.skycolorful.com/) · [规格新闻](https://news.17173.com/content/08262026/120310845.shtml) |
| `mb-demo-b550m` | COLORFUL BATTLE-AX B550M GAMING WIFI6E V14 | 549 | [七彩虹商城](https://www.skycolorful.com/) · [规格页](https://product.pconline.com.cn/mb/colorful/2995802_detail.html) |
| `mem-demo-ddr5-16` | Kingston FURY Beast DDR5 6000 16GB | 1349 | [天极报价](https://wap.yesky.com/product/product/1142/1142908/price.shtml) |
| `gpu-demo-5060` | COLORFUL iGame RTX 5060 Ultra W DUO OC 8GB | 4099 | [七彩虹商城](https://www.skycolorful.com/) · [ZOL 参数](https://m.zol.com/shop_2209817/44642088.html) |
| `gpu-demo-5070` | COLORFUL iGame RTX 5070 Vulcan X OC 12GB | 9799 | [七彩虹商城](https://www.skycolorful.com/) · [ZOL 参数](https://detail.zol.com.cn/2163/2162852/param.shtml) |
| `gpu-demo-5070ti` | COLORFUL 战斧 RTX 5070 Ti 豪华版 SFF 16GB | 13399 | [七彩虹商城](https://www.skycolorful.com/) · [尺寸/功耗](https://www.compuzone.co.kr/product/product_detail.htm?ProductNo=1377341) |
| `gpu-demo-5080` | COLORFUL iGame RTX 5080 Ultra W OC 16GB | 21099 | [七彩虹商城](https://www.skycolorful.com/) · [ZOL 参数](http://m.zol.com/shop_62445/42518431.html) |
| `psu-demo-rm750e` | Corsair RM750e 750W | 849 | [什么值得买 电源页](https://www.smzdm.com/ju/sp61v12/) |
| `case-demo-l216` | LIAN LI Lancool 216 | 579 | [机箱横评报价](https://m.toutiao.com/article/7662236752735715867/) |
| `cooler-demo-pa120max` | Thermalright PA120 VISION MAX ARGB | 439 | [上市价新闻](https://news.17173.com/content/10112026/200325023.shtml) |
| `storage-demo-sn580` | WD Blue SN580 1TB | 969 | [ZOL 报价](https://detail.zol.com.cn/solid_state_drive/s8137/pic_2.html) |

## 原有 demo seed（2026-10-31，早期演示数据）

以下 16 条是本项目早期演示目录，价格不是本轮采集结果；保留它们是为了答辩锚点
（337mm GPU / 320mm 机箱 / BIOS 缺字段），不代表当前市场价：

`cpu-demo-7600x`、`cpu-demo-13600k`、`mb-demo-b650`、`mb-demo-lga1700`、
`mem-demo-ddr5-32`、`mem-demo-ddr4-32`、`gpu-demo-337`、`gpu-demo-300`、
`psu-demo-750`、`psu-demo-550`、`case-demo-320`、`case-demo-itx`、
`cooler-demo-160`、`cooler-demo-175`、`storage-demo-nvme`、`storage-demo-sata`。

## 采集规则

1. 所有记录 `priceDate=2026-11-04`，与 header 一致；`isDemo=true`。
2. 价格只作“参考快照”，不承诺实时、库存、券后价。
3. 规格字段：厂商页/官方商城可标 `VERIFIED`；零售页/新闻标 `REPORTED`；不确定就 `EMPTY`。
4. 不爬虫、不接电商 API；本文件只记录人工可复核的公开页面。
5. 新增/修改记录时，同步更新本表；答辩被问“数据哪来的”以本表为准。

# DEMO-01 · 目录扩充

> 状态：**done**（31 条，2026-11-04）｜认领：@MoeField｜预估：2–3 天｜依赖：D1

## 目标

把 demo 目录从“8 类 × 2 件”扩到“8 类 × 3–8 件”，并且每条记录都有来源/日期/证据状态，足以支撑 D4 推荐与替换。

## 现状

`assets/catalogue/demo-v1.json` 已从 16 条扩到 **31 条**，8 类每类 ≥3；来源见 [SOURCES.md](../../assets/catalogue/SOURCES.md)。D5 的 337→300 锚点仍靠 `gpu-demo-300` 支撑。

## 需要写

- 每类补 1–6 条真实型号（价格用带日期的参考价，标注 `isDemo=true`）；
- 关键字段：socket / chipset / DDR 代际 / GPU 长度厚度 / PSU 功率 / 机箱限长 / 散热器高度；
- 所有字段路径必须在 `docs/03 §3.3` 冻结表内；
- 新增记录不能破坏现有 fixture/锚点。

## 验收

```bash
./gradlew :data:testDebugUnitTest
node ci/check-boundaries.mjs
node ci/check-keywords.mjs
```

- 31 条，8 类每类 ≥3；
- `CatalogueParser` seed 校验全过；
- `:domain:rules:test` 60/60 不变；
- D5 仍能给出 300mm 候选。

## 不要做

- 不接实时价格/库存/爬虫；
- 不伪造“已验证”的厂商数据；
- 不把 demo 记录混进生产 seed。

## 第一步

先补 GPU / CASE / PSU 三类，让 D4 有可选空间；再补 CPU/主板组合。

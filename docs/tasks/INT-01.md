# INT-01 · App 目录快照合并

> 状态：**done**（2026-11-05）｜认领：@MoeField｜预估：1 天｜依赖：pricedata MVP

## 目标

`pricedata` 的 26 条 active 快照 + demo 锚点（case/cooler/337/320）合成
`assets/catalogue/catalogue-app.json`，App 只读这一份，不联网。

## 允许修改

```
pricedata/**
assets/catalogue/**
data/src/test/**
```

## 合并规则

- active 快照按 componentId 提供 6 类；
- overlay 提供 `case-demo-320`、`case-demo-itx`、`cooler-demo-160`、`cooler-demo-175`；
- 同 ID 冲突时 overlay 优先（保留 BIOS EMPTY / 337 锚点）；
- `isDemo=true`；`priceDate` 统一；
- 输出 `catalogue-app.json`，不覆盖 `demo-v1.json`。

## 验收

- `:data:testDebugUnitTest` 绿；
- demo 8 件总价仍 7800；337 vs 320 仍 BLOCKER；
- D4 8000/GAMING 仍可行；
- `node ci/check-boundaries.mjs` 绿。

## 结果

- `assets/catalogue/catalogue-app.json`：26 active + 4 demo anchors = 30 条；
- App 默认路径改为 `catalogue/catalogue-app.json`；
- 真机 demo 仍 7800、8 件；`catalogueVersion=2026.11`；
- `pricedata/tests` 4/4；merge CLI 可复现。

## 不做

- 不把 case/cooler 加进价格历史；
- 不改 `contracts/`。

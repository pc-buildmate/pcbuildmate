#!/usr/bin/env node
/**
 * 把 assets/ 里的演示目录与规则生成为浏览器可直接用的 site/data.generated.js。
 * 单一真相源仍是 assets/；不要手改生成物。
 */
import { readFileSync, writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const cat = JSON.parse(readFileSync(join(ROOT, "assets/catalogue/demo-v1.json"), "utf8"));
const rules = JSON.parse(readFileSync(join(ROOT, "assets/rules/rules-v1.json"), "utf8"));

const payload = {
  catalogueVersion: cat.catalogueVersion,
  priceDate: cat.priceDate,
  isDemo: true,
  note: "演示数据，非实时价格",
  rulesVersion: rules.rulesVersion,
  catalogue: cat.components,
  rules: rules.rules.map((r) => ({
    ruleId: r.ruleId, kind: r.kind, left: r.left, right: r.right,
    severity: r.severity, aggregation: r.aggregation,
    formula: r.formula || null, formulaTarget: r.formulaTarget || null
  }))
};

const out = "/* 自动生成，勿手改：node tools/gen-site-data.mjs */\n" +
  "(function (root) { root.PB_DATA = " + JSON.stringify(payload, null, 1) + "; })\n" +
  "(typeof globalThis !== \"undefined\" ? globalThis : this);\n";

writeFileSync(join(ROOT, "site/data.generated.js"), out);
console.log("[site-data] 目录 " + payload.catalogue.length + " 条，规则 " + payload.rules.length + " 条");

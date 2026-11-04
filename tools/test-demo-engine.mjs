#!/usr/bin/env node
/**
 * 用设计仓库里的 60 条单规则 fixture + 8 条答辩锚点，验证 site/engine.js。
 * 这是「演示站点不是另写一套逻辑」的证明：同一套语义，同一批期望值。
 *
 * 用法：node tools/test-demo-engine.mjs
 */
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(import.meta.url);
const engine = require(join(ROOT, "site/engine.js"));

const rules = JSON.parse(readFileSync(join(ROOT, "assets/rules/rules-v1.json"), "utf8")).rules;
const fx = JSON.parse(readFileSync(join(ROOT, "assets/fixtures/fixtures-rules-v1.json"), "utf8"));
const anchors = JSON.parse(readFileSync(join(ROOT, "assets/fixtures/fixtures-anchor-v1.json"), "utf8"));

const opts = { rules: rules, evidenceOverrides: {} };
const demoCatalogue = JSON.parse(readFileSync(join(ROOT, "assets/catalogue/demo-v1.json"), "utf8")).components;
const fullCatalogue = demoCatalogue.slice();
const total = (items) => items.reduce((s, it) => s + it.quantity * it.unitPriceCny, 0);

function outcomeFor(outcomes, ruleId) {
  return outcomes.find((o) => o.ruleId === ruleId);
}

const NON_RULE_KEYS = new Set(["totalCny", "budgetCny", "withinBudget", "note"]);

function checkExpected(label, outcomes, expected, failures) {
  for (const ruleId of Object.keys(expected || {})) {
    if (NON_RULE_KEYS.has(ruleId)) continue;
    const exp = expected[ruleId];
    const got = outcomeFor(outcomes, ruleId);
    if (!got) { failures.push(label + " " + ruleId + ": 缺结果"); continue; }
    if (exp.outcome && got.status !== exp.outcome) {
      failures.push(label + " " + ruleId + ": 期望 " + exp.outcome + "，实际 " + got.status);
    }
    if (exp.severity && got.severity !== exp.severity) {
      failures.push(label + " " + ruleId + ": severity 期望 " + exp.severity + "，实际 " + got.severity);
    }
    if (exp.weakest && got.weakest !== exp.weakest) {
      failures.push(label + " " + ruleId + ": weakest 期望 " + exp.weakest + "，实际 " + got.weakest);
    }
    if (exp.reason && got.reason !== exp.reason) {
      failures.push(label + " " + ruleId + ": reason 期望 " + exp.reason + "，实际 " + got.reason);
    }
    if (exp.missingFieldIsNull === true && got.missingField !== null && got.missingField !== undefined) {
      failures.push(label + " " + ruleId + ": missingField 应为 null，实际 " + got.missingField);
    }
    if (exp.missingField && typeof exp.missingField === "object") {
      // 演示版用路径字符串，契约版是 FieldRef；只比较 fieldPath
      const path = typeof got.missingField === "string"
        ? got.missingField
        : (got.missingField && (got.missingField.fieldPath || got.missingField.path));
      if (path !== exp.missingField.fieldPath) {
        failures.push(label + " " + ruleId + ": missingField 期望 " + exp.missingField.fieldPath + "，实际 " + JSON.stringify(got.missingField));
      }
    }
    if (exp.conflictId && got.conflictId !== exp.conflictId) {
      failures.push(label + " " + ruleId + ": conflictId 期望 " + exp.conflictId + "，实际 " + got.conflictId);
    }
    if (exp.leftValue && String(got.left && got.left.value) !== String(exp.leftValue)) {
      failures.push(label + " " + ruleId + ": leftValue 期望 " + exp.leftValue + "，实际 " + (got.left && got.left.value));
    }
    if (exp.rightValue && String(got.right && got.right.value) !== String(exp.rightValue)) {
      failures.push(label + " " + ruleId + ": rightValue 期望 " + exp.rightValue + "，实际 " + (got.right && got.right.value));
    }
  }
}

const failures = [];
let n = 0;

// ---- 60 条单规则 fixture
for (const f of fx.fixtures) {
  const rev = { id: f.revisionId, items: f.items };
  const outcomes = engine.evaluate(rev, f.catalogue, opts);
  checkExpected(f.fixtureId, outcomes, f.expected, failures);
  n++;
}

// ---- 8 条锚点
for (const f of anchors.fixtures) {
  const rev = { id: f.revisionId, items: f.items };
  const cat = (f.catalogue && f.catalogue.length ? f.catalogue : []).concat(demoCatalogue);
  const outcomes = engine.evaluate(rev, cat, opts);
  if (f.expectedBefore) {
    if (total(f.items) !== f.expectedBefore.totalCny) {
      failures.push(f.fixtureId + ": before 总价期望 " + f.expectedBefore.totalCny + "，实际 " + total(f.items));
    }
    checkExpected(f.fixtureId + " before", outcomes, f.expectedBefore, failures);
    // 应用候选：替换 GPU
    const cand = f.suggestion.candidateComponentId;
    const rec = cat.find((r) => r.componentId === cand);
    if (!rec) { failures.push(f.fixtureId + ": 目录里没有候选 " + cand); }
    else {
      const swapped = f.items.map((it) =>
        it.category === rec.category
          ? { componentId: rec.componentId, category: rec.category, quantity: it.quantity, unitPriceCny: rec.priceCny }
          : it);
      const rev2 = { id: f.revisionId + 1, items: swapped };
      const out2 = engine.evaluate(rev2, cat, opts);
      if (total(swapped) !== f.expectedAfter.totalCny) {
        failures.push(f.fixtureId + ": after 总价期望 " + f.expectedAfter.totalCny + "，实际 " + total(swapped));
      }
      checkExpected(f.fixtureId + " after", out2, f.expectedAfter, failures);
      const gpu = outcomeFor(out2, "GPU_CASE_LENGTH");
      if (gpu.status !== "PASSED") failures.push(f.fixtureId + ": 替换后 GPU_CASE_LENGTH 应 PASSED，实际 " + gpu.status);
    }
  } else {
    checkExpected(f.fixtureId, outcomes, f.expected, failures);
  }
  n++;
}

if (failures.length) {
  console.error("[demo-engine] " + failures.length + " 处不一致：");
  for (const f of failures) console.error("  ✗ " + f);
  process.exit(1);
}
console.log("[demo-engine] OK — " + fx.fixtures.length + " 条规则 fixture + " + anchors.fixtures.length + " 条锚点全部与演示引擎一致。");

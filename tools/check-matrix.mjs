#!/usr/bin/env node
/**
 * 验证矩阵完整性检查（docs/07-unit-verification.md 7.5）。
 *
 * 输入：build/reports/matrix.json，由 Gradle TestListener 产出，格式：
 *   { "tests": [ { "id": "...", "status": "passed|failed|skipped", "tags": ["rule:GPU_CASE_LENGTH","case:PASSED"] } ] }
 *
 * 标签约定（冻结）：
 *   rule:<RULE_ID>            单规则语义用例（必须同时带 case:）
 *   case:PASSED|CONFLICT|UNVERIFIED|MISSING
 *   fsm:<diagnosis|replacement>:<State>:<Event>
 *   inv:INV-xx
 *
 * 用法：
 *   node tools/check-matrix.mjs                      # 读默认路径
 *   node tools/check-matrix.mjs --file path.json     # 指定文件（自测用）
 *   node tools/check-matrix.mjs --mutations          # 检查 12 条关键变异
 *
 * 退出码：0 通过（或仅规格模式）；1 有空格子。
 */
import { readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const CASES = ["PASSED", "CONFLICT", "UNVERIFIED", "MISSING"];
const argFile = (() => {
  const i = process.argv.indexOf("--file");
  return i > -1 ? process.argv[i + 1] : null;
})();
const MUTATION_MODE = process.argv.includes("--mutations");

const readJson = (rel) => JSON.parse(readFileSync(join(ROOT, rel), "utf8"));

function expectedRules() {
  const rules = readJson("assets/rules/rules-v1.json").rules;
  const out = new Set();
  for (const r of rules) for (const c of CASES) out.add("rule:" + r.ruleId + "|case:" + c);
  return out;
}

function expectedFsm() {
  const diagnosisStates = ["Idle", "Running", "Done", "Failed"];
  const diagnosisEvents = ["StartCheck", "CheckCompleted", "CheckFailed", "BuildEdited"];
  const replStates = ["Idle", "Candidates", "Previewing", "Rechecking", "Applied", "NoCandidate"];
  const replEvents = ["ConflictSelected", "CandidatesLoaded", "PreviewCandidate", "PreviewBuilt", "Apply", "Cancel", "RecheckCompleted", "RecheckRejected", "BuildEdited", "Dismiss"];
  const out = new Set();
  for (const s of diagnosisStates) for (const e of diagnosisEvents) out.add("fsm:diagnosis:" + s + ":" + e);
  for (const s of replStates) for (const e of replEvents) out.add("fsm:replacement:" + s + ":" + e);
  return out;
}

function expectedInvariants() {
  const out = new Set();
  for (let i = 1; i <= 18; i++) out.add("inv:INV-" + String(i).padStart(2, "0"));
  return out;
}

function diff(expected, actual, label) {
  const missing = [...expected].filter((x) => !actual.has(x)).sort();
  const extra = [...actual].filter((x) => !expected.has(x)).sort();
  if (missing.length) {
    console.error("✗ " + label + " 缺 " + missing.length + " 格：");
    for (const m of missing) console.error("    " + m);
  }
  if (extra.length) {
    console.warn("! " + label + " 有 " + extra.length + " 个未知标签（拼写错？）：" + extra.slice(0, 5).join(", "));
  }
  return missing.length === 0;
}

if (MUTATION_MODE) {
  const path = argFile || "build/reports/key-mutations.json";
  if (!existsSync(join(ROOT, path))) {
    console.log("[matrix] 规格模式：未找到 " + path + "，跳过（需要 12 条关键变异记录）");
    process.exit(0);
  }
  const data = readJson(path);
  const byId = new Map((data.mutations || []).map((m) => [m.id, m]));
  let bad = 0;
  for (let i = 1; i <= 12; i++) {
    const id = "MUT-" + String(i).padStart(2, "0");
    const m = byId.get(id);
    if (!m) { console.error("✗ 缺变异记录 " + id); bad++; continue; }
    if (m.status !== "KILLED") { console.error("✗ " + id + " 状态 = " + m.status + "（应 KILLED）" + (m.killer ? " 杀手：" + m.killer : "")); bad++; }
  }
  if (bad) { console.error("[matrix] " + bad + " 条关键变异未杀死"); process.exit(1); }
  console.log("[matrix] OK — 12/12 关键变异被杀死");
  process.exit(0);
}

const matrixPath = argFile || "build/reports/matrix.json";
if (!existsSync(join(ROOT, matrixPath))) {
  console.log("[matrix] 规格模式（无测试产物）——期望覆盖：");
  console.log("    rules            " + expectedRules().size + " 格");
  console.log("    state machine    " + expectedFsm().size + " 格");
  console.log("    invariants       " + expectedInvariants().size + " 格");
  console.log("[matrix] 生成 " + matrixPath + " 后重跑本命令进行实际校验。");
  process.exit(0);
}

const data = readJson(matrixPath);
const passed = (data.tests || []).filter((t) => t.status === "passed");
const skipped = (data.tests || []).filter((t) => t.status === "skipped");
if (skipped.length) console.warn("! 有 " + skipped.length + " 条 skipped 用例——跳过即视为空格");

const ruleActual = new Set();
const fsmActual = new Set();
const invActual = new Set();
for (const t of passed) {
  const tags = t.tags || [];
  const ruleTag = tags.find((x) => x.startsWith("rule:"));
  const caseTag = tags.find((x) => x.startsWith("case:"));
  if (ruleTag && caseTag) ruleActual.add(ruleTag + "|" + caseTag);
  for (const tag of tags) {
    if (tag.startsWith("fsm:")) fsmActual.add(tag);
    if (tag.startsWith("inv:")) invActual.add(tag);
  }
}

const okRules = diff(expectedRules(), ruleActual, "规则矩阵");
const okFsm = diff(expectedFsm(), fsmActual, "状态机矩阵");
const okInv = diff(expectedInvariants(), invActual, "不变量矩阵");

console.log("[matrix] rules " + ruleActual.size + "/" + expectedRules().size +
  ", fsm " + fsmActual.size + "/" + expectedFsm().size +
  ", inv " + invActual.size + "/" + expectedInvariants().size);

if (!okRules || !okFsm || !okInv) {
  console.error("[matrix] 矩阵不完整：空格子即视为未完成（docs/07-unit-verification.md 7.5）");
  process.exit(1);
}
console.log("[matrix] OK — 三块矩阵无空格。");

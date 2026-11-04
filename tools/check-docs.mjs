#!/usr/bin/env node
/**
 * 文档与数据自检（零依赖）。
 *
 * 检查：
 *   1. 所有本地 markdown 链接可解析
 *   2. rules-v1.json：15 条规则，路径都在 docs/03-rules.md 3.3 节表内
 *   3. 每条规则 4 类 fixture 齐全（PASSED / CONFLICT / UNVERIFIED / MISSING）
 *   4. 答辩锚点 8 条存在，且 7800 -> 7900 与 BIOS 未验证的一致性
 *   5. INV-01..18 在 02 章有定义；MUT-01..12 在 07 章有定义
 *   6. ADR-0001..0006 文件存在且在索引里
 *   7. 04 章状态机包含全部状态与事件名
 *   8. ci/modules.json 的每个模块在 05 章出现
 *
 * 用法：node tools/check-docs.mjs
 * 退出码：0 通过，1 有问题。
 */
import { readFileSync, readdirSync, existsSync, statSync } from "node:fs";
import { join, dirname, relative, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const errors = [];
const warnings = [];
const ok = (msg) => console.log("✓ " + msg);
const fail = (msg) => { errors.push(msg); console.error("✗ " + msg); };
const warn = (msg) => { warnings.push(msg); console.warn("! " + msg); };

const read = (rel) => readFileSync(join(ROOT, rel), "utf8");
const exists = (rel) => existsSync(join(ROOT, rel));

function walkMd(dir, out = []) {
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    if (statSync(full).isDirectory()) walkMd(full, out);
    else if (name.endsWith(".md")) out.push(full);
  }
  return out;
}

// ---------------------------------------------------------------- 1. 链接
const mdFiles = [join(ROOT, "README.md"), ...walkMd(join(ROOT, "docs"))];
let linkCount = 0;
for (const file of mdFiles) {
  const text = readFileSync(file, "utf8");
  for (const m of text.matchAll(/\[[^\]]*\]\(([^)\s]+)\)/g)) {
    let target = m[1];
    if (/^(https?:|mailto:|#)/.test(target)) continue;
    target = target.split("#")[0];
    if (!target) continue;
    linkCount++;
    const abs = resolve(dirname(file), target);
    if (!existsSync(abs)) fail("死链 " + relative(ROOT, file) + " -> " + m[1]);
  }
}
ok("链接检查：" + linkCount + " 条本地链接");

// ---------------------------------------------------------------- 2/3. 规则与 fixture
const rulesDoc = read("docs/03-rules.md");
const sec33 = rulesDoc.split("## 3.3")[1]?.split("## 3.4")[0] ?? "";
const pathTable = new Set();
for (const line of sec33.split("\n")) {
  const m = line.match(/^\|\s*([A-Z_]+)\s*\|\s*\x60([A-Za-z0-9_]+)\x60\s*\|/);
  if (m) pathTable.add(m[1] + "." + m[2]);
}
if (pathTable.size < 30) fail("3.3 节路径表只解析到 " + pathTable.size + " 条（应 ≥ 30）");
else ok("字段路径表：" + pathTable.size + " 条");

const rules = JSON.parse(read("assets/rules/rules-v1.json"));
const fx = JSON.parse(read("assets/fixtures/fixtures-rules-v1.json"));
if (rules.rules.length !== 15) fail("规则数应为 15，实际 " + rules.rules.length);
else ok("规则数 15");

const REQUIRED_CASES = ["PASSED", "CONFLICT", "UNVERIFIED", "MISSING"];
const byRule = new Map();
for (const f of fx.fixtures) {
  if (!byRule.has(f.ruleId)) byRule.set(f.ruleId, []);
  byRule.get(f.ruleId).push(f);
}
let fixtureOk = true;
for (const r of rules.rules) {
  for (const side of ["left", "right"]) {
    const spec = r[side];
    if (!spec) continue;
    const key = spec.category + "." + spec.path;
    if (!pathTable.has(key)) { fail("规则 " + r.ruleId + " 的 " + side + " 路径不在 3.3 节：" + key); fixtureOk = false; }
  }
  const list = byRule.get(r.ruleId) || [];
  if (list.length !== 4) { fail("规则 " + r.ruleId + " fixture 数 = " + list.length + "（应 4）"); fixtureOk = false; continue; }
  const cases = list.map((f) => f.case).sort();
  for (const c of REQUIRED_CASES) if (!cases.includes(c)) { fail("规则 " + r.ruleId + " 缺 case " + c); fixtureOk = false; }
  for (const id of r.fixtures || []) {
    if (!list.some((f) => f.fixtureId === id)) { fail("规则 " + r.ruleId + " 声明的 fixture 不存在：" + id); fixtureOk = false; }
  }
  for (const f of list) {
    if (!f.expected || !f.expected[r.ruleId]) { fail("fixture " + f.fixtureId + " 缺 expected[" + r.ruleId + "]"); fixtureOk = false; }
    if (f.isDemo !== true) { fail("fixture " + f.fixtureId + " 必须 isDemo=true"); fixtureOk = false; }
  }
}
if (fixtureOk) ok("60 条单规则 fixture：四类齐全、路径合法、isDemo 正确");

// ---------------------------------------------------------------- 4. 锚点
const anchor = JSON.parse(read("assets/fixtures/fixtures-anchor-v1.json"));
const anchorIds = new Set(anchor.fixtures.map((f) => f.fixtureId));
const requiredAnchors = [
  "fx_gpu_len_conflict_337_320", "fx_gpu_len_pass_300_320", "fx_gpu_len_equal_320_320",
  "fx_bios_missing", "fx_source_conflict_gpu_len", "fx_psu_power_conflict",
  "fx_memory_gen_conflict", "fx_suggestion_300_plus_100"
];
for (const id of requiredAnchors) if (!anchorIds.has(id)) fail("缺答辩锚点 fixture：" + id);
const e2e = anchor.fixtures.find((f) => f.fixtureId === "fx_suggestion_300_plus_100");
if (e2e) {
  if (e2e.expectedBefore?.totalCny !== 7800) fail("锚点 e2e 的 before 总价应为 7800，实际 " + e2e.expectedBefore?.totalCny);
  if (e2e.expectedAfter?.totalCny !== 7900) fail("锚点 e2e 的 after 总价应为 7900，实际 " + e2e.expectedAfter?.totalCny);
  if (e2e.suggestion?.priceDeltaCny !== 100) fail("锚点 e2e 的差价应为 100");
}
if (requiredAnchors.every((id) => anchorIds.has(id))) ok("答辩锚点 8 条齐全，7800 -> 7900 一致");

// ---------------------------------------------------------------- 5. INV / MUT
const contractsDoc = read("docs/02-contracts.md");
const definedInv = new Set();
for (const line of contractsDoc.split("\n")) {
  const m = line.match(/^\|\s*(INV-\d{2})\s*\|/);
  if (m) definedInv.add(m[1]);
}
for (let i = 1; i <= 18; i++) {
  const id = "INV-" + String(i).padStart(2, "0");
  if (!definedInv.has(id)) fail("02 章缺不变量定义：" + id);
}
if (definedInv.size === 18) ok("不变量 INV-01..18 全部定义");

const verifyDoc = read("docs/07-unit-verification.md");
const definedMut = new Set();
for (const line of verifyDoc.split("\n")) {
  const m = line.match(/^\|\s*(MUT-\d{2})\s*\|/);
  if (m) definedMut.add(m[1]);
}
for (let i = 1; i <= 12; i++) {
  const id = "MUT-" + String(i).padStart(2, "0");
  if (!definedMut.has(id)) fail("07 章缺 mutation 定义：" + id);
}
if (definedMut.size === 12) ok("关键 mutation MUT-01..12 全部定义");

// ---------------------------------------------------------------- 6. ADR
const adrDir = join(ROOT, "docs/11-adr");
const adrIndex = read("docs/11-adr/README.md");
for (let i = 1; i <= 6; i++) {
  const n = String(i).padStart(4, "0");
  const files = readdirSync(adrDir).filter((f) => f.startsWith(n));
  if (files.length !== 1) { fail("ADR-" + n + " 文件数 = " + files.length); continue; }
  if (!adrIndex.includes("(" + files[0] + ")")) fail("ADR 索引未链接 " + files[0]);
}
ok("6 条 ADR 检查完成");

// ---------------------------------------------------------------- 7. 状态机文档
const smDoc = read("docs/04-state-machines.md");
const states = ["Idle", "Running", "Done", "Failed", "Candidates", "Previewing", "Rechecking", "Applied", "NoCandidate"];
const events = ["StartCheck", "CheckCompleted", "CheckFailed", "BuildEdited", "ConflictSelected", "CandidatesLoaded",
  "PreviewCandidate", "PreviewBuilt", "Apply", "Cancel", "RecheckCompleted", "RecheckRejected", "Dismiss"];
for (const s of states) if (!smDoc.includes(s)) fail("04 章缺状态：" + s);
for (const e of events) if (!smDoc.includes(e)) fail("04 章缺事件：" + e);
ok("04 章包含全部状态（" + states.length + "）与事件（" + events.length + "）");

// ---------------------------------------------------------------- 8. 模块矩阵覆盖
const modules = JSON.parse(read("ci/modules.json")).modules;
const indep = read("docs/05-module-independence.md");
for (const m of modules) {
  if (!indep.includes(m.projectPath) && !indep.includes(m.name)) warn("05 章未出现模块 " + m.name);
}
ok("ci/modules.json：" + modules.length + " 个模块");

// ---------------------------------------------------------------- 9. 版本文件
if (!exists("docs/VERSION")) fail("缺 docs/VERSION");
else ok("docs/VERSION 存在");

// ---------------------------------------------------------------- 汇总
console.log("");
if (warnings.length) console.log("[check-docs] " + warnings.length + " 条警告");
if (errors.length) {
  console.error("[check-docs] " + errors.length + " 个问题");
  process.exit(1);
}
console.log("[check-docs] OK — 文档、规则、fixture、ADR、状态机全部自洽。");

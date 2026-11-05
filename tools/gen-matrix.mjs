#!/usr/bin/env node
/**
 * 从真实 JUnit XML + fixture/状态机定义生成 build/reports/matrix.json。
 *
 * 不手填 “60/76”：规则格子来自 fixtures-rules-v1.json 的 ruleId/expected，
 * 状态机格子来自 docs/07 冻结的 4×4 + 6×10 列表；只有对应测试 passed 才标 passed。
 *
 * 用法：node tools/gen-matrix.mjs
 * 然后：node tools/check-matrix.mjs
 */
import { readFileSync, readdirSync, existsSync, mkdirSync, writeFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const OUT = join(ROOT, "build/reports/matrix.json");

function walk(dir, out = []) {
  if (!existsSync(dir)) return out;
  for (const name of readdirSync(dir, { withFileTypes: true })) {
    const p = join(dir, name.name);
    if (name.isDirectory()) walk(p, out);
    else if (name.name.endsWith(".xml")) out.push(p);
  }
  return out;
}

const xmlFiles = [];
for (const base of ["build", "domain", "data", "contracts-test"]) {
  xmlFiles.push(...walk(join(ROOT, base)));
}
// 只保留 test-results 下的 XML，避免把其他报告当测试
const testXml = xmlFiles.filter((p) => p.includes("test-results"));

function decode(s) {
  return s.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&lt;/g, "<")
    .replace(/&gt;/g, ">").replace(/&amp;/g, "&");
}

const tests = new Map();
for (const file of testXml) {
  const xml = readFileSync(file, "utf8");
  const re = /<testcase\b([^>]*?)(?:\/>|>([\s\S]*?)<\/testcase>)/g;
  let m;
  while ((m = re.exec(xml))) {
    const attrs = m[1];
    const body = m[2] || "";
    const name = decode((attrs.match(/\bname="([^"]*)"/) || [])[1] || "");
    const classname = decode((attrs.match(/\bclassname="([^"]*)"/) || [])[1] || "");
    let status = "passed";
    if (/<failure\b|<error\b/.test(body)) status = "failed";
    else if (/<skipped\b/.test(body)) status = "skipped";
    tests.set(`${classname}.${name}`, { name, classname, status });
  }
}

function statusOf(classname, includes) {
  const found = [...tests.values()].filter(
    (t) => t.classname === classname && t.name.includes(includes),
  );
  if (!found.length) return "missing";
  return found.every((t) => t.status === "passed") ? "passed" : "failed";
}

const ruleTest = statusOf("pcbuildmate.domain.rules.FixtureTest", "all 60 rule fixtures");
const fixtures = JSON.parse(readFileSync(join(ROOT, "assets/fixtures/fixtures-rules-v1.json"), "utf8")).fixtures;
const ruleCells = new Set();
const caseOf = (fx) => {
  const suffix = fx.fixtureId.split("_").pop();
  if (suffix === "pass") return "PASSED";
  if (suffix === "conflict") return "CONFLICT";
  if (suffix === "unverified") return "UNVERIFIED";
  if (suffix === "missing") return "MISSING";
  return null;
};
for (const fx of fixtures) {
  const caseName = caseOf(fx);
  for (const ruleId of Object.keys(fx.expected || {})) {
    if (caseName) ruleCells.add(`rule:${ruleId}|case:${caseName}`);
  }
}
const ruleEntries = [...ruleCells].sort().map((id) => ({
  id,
  status: ruleTest,
  tags: id.split("|"),
  source: "FixtureTest.all 60 rule fixtures match expected outcomes",
})).filter((e) => e.status === "passed");

const diagnosisStates = ["Idle", "Running", "Done", "Failed"];
const diagnosisEvents = ["StartCheck", "CheckCompleted", "CheckFailed", "BuildEdited"];
const replStates = ["Idle", "Candidates", "Previewing", "Rechecking", "Applied", "NoCandidate"];
const replEvents = ["ConflictSelected", "CandidatesLoaded", "PreviewCandidate", "PreviewBuilt", "Apply", "Cancel", "RecheckCompleted", "RecheckRejected", "BuildEdited", "Dismiss"];
const diagnosisTest = statusOf("pcbuildmate.domain.state.DiagnosisMachineTest", "diagnosis full matrix");
const replacementTest = statusOf("pcbuildmate.domain.state.ReplacementMachineTest", "replacement full matrix");
const fsmEntries = [];
for (const s of diagnosisStates) for (const e of diagnosisEvents) {
  const id = `fsm:diagnosis:${s}:${e}`;
  if (diagnosisTest === "passed") fsmEntries.push({ id, status: "passed", tags: [id] });
}
for (const s of replStates) for (const e of replEvents) {
  const id = `fsm:replacement:${s}:${e}`;
  if (replacementTest === "passed") fsmEntries.push({ id, status: "passed", tags: [id] });
}

// 不变量：目前没有逐条挂载，留空让 check-matrix 报洞。不要在这里手填 passed。
const invariantEntries = [];

mkdirSync(dirname(OUT), { recursive: true });
const matrix = {
  generatedAt: new Date().toISOString(),
  tests: [...ruleEntries, ...fsmEntries, ...invariantEntries],
  summary: {
    rule: ruleEntries.length,
    fsm: fsmEntries.length,
    invariant: invariantEntries.length,
    ruleTest,
    diagnosisTest,
    replacementTest,
    xmlFiles: testXml.length,
  },
};
writeFileSync(OUT, JSON.stringify(matrix, null, 2) + "\n");
console.log(`[gen-matrix] rules ${ruleEntries.length} | fsm ${fsmEntries.length} | invariants ${invariantEntries.length}`);
console.log(`[gen-matrix] ruleTest=${ruleTest} diagnosis=${diagnosisTest} replacement=${replacementTest} xml=${testXml.length}`);
console.log(`[gen-matrix] -> ${OUT}`);

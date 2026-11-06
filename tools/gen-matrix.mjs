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
import { execFileSync } from "node:child_process";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { FSM_GRID } from "./matrix-spec.mjs";

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
for (const base of ["build", "domain", "data", "contracts-test", "feature"]) {
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

const diagnosisTest = statusOf("pcbuildmate.domain.state.DiagnosisMachineTest", "diagnosis full matrix");
const replacementTest = statusOf("pcbuildmate.domain.state.ReplacementMachineTest", "replacement full matrix");
const fsmEntries = [];
for (const [machine, grid] of Object.entries(FSM_GRID)) {
  const status = machine === "diagnosis" ? diagnosisTest : replacementTest;
  if (status !== "passed") continue;
  for (const state of grid.states) for (const event of grid.events) {
    const id = `fsm:${machine}:${state}:${event}`;
    fsmEntries.push({ id, status: "passed", tags: [id] });
  }
}

// 不变量：每条必须指向真实 JUnit 测试或真实 CI 检查。
// 未挂载的（INV-02/10）保持缺失，让 check-matrix 报洞，不伪造。
function testInvariant(id, classname, includes) {
  const status = statusOf(classname, includes);
  return { id, status, tags: [id], source: `${classname}.${includes}` };
}
function externalInvariant(id, args) {
  let status = "passed";
  try {
    execFileSync("node", args, { cwd: ROOT, stdio: "pipe" });
  } catch {
    status = "failed";
  }
  return { id, status, tags: [id], source: `node ${args.join(" ")}` };
}

const invariantEntries = [
  testInvariant("inv:INV-01", "pcbuildmate.domain.revision.RevisionInvariantsTest", "inconsistent total"),
  testInvariant("inv:INV-02", "pcbuildmate.feature.workspace.WorkspaceRevisionTest", "revision ids increase"),
  testInvariant("inv:INV-10", "pcbuildmate.feature.workspace.WorkspaceRevisionTest", "revision ids increase"),
  testInvariant("inv:INV-03", "pcbuildmate.domain.compare.DefaultBuildComparatorTest", "stale diagnosis"),
  testInvariant("inv:INV-04", "pcbuildmate.domain.rules.FixtureTest", "all 60 rule fixtures"),
  testInvariant("inv:INV-05", "pcbuildmate.domain.rules.FixtureTest", "all 7 rule anchor fixtures"),
  testInvariant("inv:INV-06", "pcbuildmate.domain.rules.FixtureTest", "all 60 rule fixtures"),
  testInvariant("inv:INV-07", "pcbuildmate.domain.state.ReplacementMachineTest", "replacement full matrix"),
  testInvariant("inv:INV-08", "pcbuildmate.domain.replacement.DefaultReplacementAdvisorTest", "structured no-candidate"),
  testInvariant("inv:INV-09", "pcbuildmate.domain.state.DiagnosisMachineTest", "diagnosis full matrix"),
  testInvariant("inv:INV-11", "pcbuildmate.data.catalogue.CatalogueParserTest", "parses the demo catalogue"),
  testInvariant("inv:INV-12", "pcbuildmate.domain.compare.DefaultBuildComparatorTest", "stale diagnosis"),
  testInvariant("inv:INV-13", "pcbuildmate.domain.recommend.DefaultBudgetRecommenderTest", "8000 gaming is feasible"),
  testInvariant("inv:INV-14", "pcbuildmate.domain.replacement.DefaultReplacementAdvisorTest", "300mm gpu"),
  testInvariant("inv:INV-15", "pcbuildmate.domain.rules.FixtureTest", "evaluation is deterministic"),
  testInvariant("inv:INV-17", "pcbuildmate.data.store.InMemoryBuildStoreTest", "save then load"),
  externalInvariant("inv:INV-16", ["ci/check-boundaries.mjs"]),
  externalInvariant("inv:INV-18", ["ci/check-boundaries.mjs"]),
];

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

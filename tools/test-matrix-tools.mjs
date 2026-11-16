#!/usr/bin/env node
/**
 * 矩阵工具自测：matrix-spec / check-matrix 自己也要有测试。
 *
 * 为什么需要它：整个"覆盖证据"链都压在这两个脚本上——如果 check-matrix 少判一块矩阵、
 * 或者把 SURVIVED 当成通过，所有文档里的 60/76/18 与 12/12 就都成了装饰。
 *
 * 用法：node tools/test-matrix-tools.mjs
 * 依据：docs/07-unit-verification.md §7.5
 */
import { spawnSync } from "node:child_process";
import { mkdtempSync, writeFileSync, rmSync } from "node:fs";
import { join, dirname } from "node:path";
import { tmpdir } from "node:os";
import { fileURLToPath } from "node:url";
import { expectedRules, expectedFsm, expectedInvariants, CASES } from "./matrix-spec.mjs";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const CHECKER = join(ROOT, "tools/check-matrix.mjs");
const dir = mkdtempSync(join(tmpdir(), "matrix-tools-"));
let failures = 0;
let checks = 0;

function check(name, cond, detail = "") {
  checks++;
  if (cond) return;
  failures++;
  console.error(`✗ ${name}${detail ? "\n    " + detail : ""}`);
}

function run(args) {
  // spawnSync 而不是 execFileSync：成功时也要拿到 stderr（未知标签告警走 stderr）。
  const r = spawnSync("node", [CHECKER, ...args], { encoding: "utf8" });
  return { code: r.status ?? 1, out: r.stdout || "", err: r.stderr || "" };
}

function write(name, data) {
  const p = join(dir, name);
  writeFileSync(p, JSON.stringify(data, null, 2));
  return p;
}

function completeMatrix() {
  const tests = [];
  for (const id of expectedRules()) {
    const [ruleTag, caseTag] = id.split("|");
    tests.push({ id, status: "passed", tags: [ruleTag, caseTag] });
  }
  for (const tag of expectedFsm()) tests.push({ id: tag, status: "passed", tags: [tag] });
  for (const tag of expectedInvariants()) tests.push({ id: tag, status: "passed", tags: [tag] });
  return { generatedAt: "test", tests, summary: {} };
}

function killedMutations() {
  const mutations = [];
  for (let i = 1; i <= 12; i++) mutations.push({ id: "MUT-" + String(i).padStart(2, "0"), status: "KILLED" });
  return { mutations };
}

// ---------- 规格本身 ----------
check("规则矩阵应为 15 条 × 4 类 = 60 格", expectedRules().size === 60, `实际 ${expectedRules().size}`);
check("状态机矩阵应为 4×4 + 6×10 = 76 格", expectedFsm().size === 76, `实际 ${expectedFsm().size}`);
check("不变量矩阵应为 INV-01..18", expectedInvariants().size === 18, `实际 ${expectedInvariants().size}`);
check(
  "每条规则都必须有四类 case",
  [...expectedRules()].every((id) => CASES.some((c) => id.endsWith("|case:" + c))),
);

// ---------- 矩阵检查器 ----------
const full = write("matrix-full.json", completeMatrix());
let r = run(["--file", full]);
check("完整矩阵应通过", r.code === 0, `exit=${r.code} ${r.err.slice(0, 200)}`);
check("完整矩阵的报告应为 60/76/18", /rules 60\/60, fsm 76\/76, inv 18\/18/.test(r.out), r.out.slice(0, 200));

const oneMissing = completeMatrix();
const victim = [...expectedRules()][0];
oneMissing.tests = oneMissing.tests.filter((t) => t.id !== victim);
r = run(["--file", write("matrix-missing-rule.json", oneMissing)]);
check("缺一个规则格应失败", r.code === 1, `exit=${r.code}`);
check("失败信息应点名缺失的格", r.err.includes(victim), r.err.slice(0, 300));

const fsmMissing = completeMatrix();
const fsmVictim = [...expectedFsm()][0];
fsmMissing.tests = fsmMissing.tests.filter((t) => t.id !== fsmVictim);
r = run(["--file", write("matrix-missing-fsm.json", fsmMissing)]);
check("缺一个状态机格应失败", r.code === 1, `exit=${r.code}`);
check("失败信息应点名缺失的格", r.err.includes(fsmVictim), r.err.slice(0, 300));

const skipped = completeMatrix();
skipped.tests[0] = { ...skipped.tests[0], status: "skipped" };
r = run(["--file", write("matrix-skipped.json", skipped)]);
check("被 skip 的用例不算通过", r.code === 1, `exit=${r.code}`);

const extra = completeMatrix();
extra.tests.push({ id: "rule:NOT_A_RULE|case:PASSED", status: "passed", tags: ["rule:NOT_A_RULE", "case:PASSED"] });
r = run(["--file", write("matrix-extra.json", extra)]);
check("未知标签应至少给出告警", r.code === 0 && /未知标签/.test(r.err + r.out), r.out.slice(0, 200) + r.err.slice(0, 200));

// ---------- 变异检查器 ----------
r = run(["--mutations", "--file", write("mut-all-killed.json", killedMutations())]);
check("12/12 KILLED 应通过", r.code === 0 && /12\/12/.test(r.out), `exit=${r.code} ${r.out.slice(0, 200)}`);

const survived = killedMutations();
survived.mutations[4].status = "SURVIVED";
r = run(["--mutations", "--file", write("mut-survived.json", survived)]);
check("有存活变异应失败", r.code === 1, `exit=${r.code}`);
check("失败信息应点名 MUT-05", r.err.includes("MUT-05"), r.err.slice(0, 200));

const broken = killedMutations();
broken.mutations[0].status = "BROKEN";
r = run(["--mutations", "--file", write("mut-broken.json", broken)]);
check("BROKEN 也算未杀死", r.code === 1, `exit=${r.code}`);

const missingMutation = killedMutations();
missingMutation.mutations.pop();
r = run(["--mutations", "--file", write("mut-missing.json", missingMutation)]);
check("缺一条变异记录应失败", r.code === 1 && r.err.includes("MUT-12"), r.err.slice(0, 200));

rmSync(dir, { recursive: true, force: true });

if (failures) {
  console.error(`\n[test-matrix-tools] ${failures}/${checks} 项失败`);
  process.exit(1);
}
console.log(`[test-matrix-tools] OK — ${checks} 项断言全部通过。`);

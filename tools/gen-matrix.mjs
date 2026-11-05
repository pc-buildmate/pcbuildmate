#!/usr/bin/env node
/**
 * 从 Gradle 的 JUnit XML 生成验证矩阵 `build/reports/matrix.json`。
 *
 * 输入（真实测试产物）：
 *   domain/rules/build/test-results/**\/*.xml
 *   domain/state/build/test-results/**\/*.xml
 *
 * 输出（供 tools/check-matrix.mjs 读取）：
 *   { "tests": [ { "id", "status": "passed|failed|skipped", "tags": [...] } ] }
 *
 * 规则矩阵的 60 格来自 60 条 fixture 的 `ruleId` + `case`；只有当
 * `FixtureTest` 的 60 条断言全部通过时，这些格子才算 passed。
 * 状态机 76 格来自 docs/04 的转移表；只有当对应的 full-matrix 测试通过时才算 passed。
 *
 * 用法：
 *   ./gradlew :domain:rules:test :domain:state:test
 *   node tools/gen-matrix.mjs [--out path]
 *
 * 退出码：0 已生成；1 缺少测试产物或规格/产物不一致。
 */
import { readFileSync, writeFileSync, mkdirSync, existsSync, readdirSync, statSync } from "node:fs";
import { join, dirname } from "node:path";
import { ROOT, CASES, FSM_GRID, expectedRules, expectedFsm, readJson } from "./matrix-spec.mjs";

const OUT = (() => {
    const i = process.argv.indexOf("--out");
    return i > -1 ? process.argv[i + 1] : "build/reports/matrix.json";
})();

const RESULT_ROOTS = ["domain/rules/build/test-results", "domain/state/build/test-results"];

const RULES_TEST = "pcbuildmate.domain.rules.FixtureTest#all 60 rule fixtures match expected outcomes";
const FSM_TESTS = {
    diagnosis: "pcbuildmate.domain.state.DiagnosisMachineTest#diagnosis full matrix",
    replacement: "pcbuildmate.domain.state.ReplacementMachineTest#replacement full matrix",
};

const errors = [];
const fail = (msg) => {
    errors.push(msg);
    console.error("✗ " + msg);
};

function walkXml(dir, out = []) {
    if (!existsSync(dir)) return out;
    for (const name of readdirSync(dir)) {
        const full = join(dir, name);
        if (statSync(full).isDirectory()) walkXml(full, out);
        else if (name.endsWith(".xml")) out.push(full);
    }
    return out;
}

function attr(attrs, key) {
    const m = attrs.match(new RegExp(key + '\\s*=\\s*"([^"]*)"'));
    return m ? unescapeXml(m[1]) : null;
}

function unescapeXml(value) {
    return value
        .replace(/&quot;/g, '"')
        .replace(/&apos;/g, "'")
        .replace(/&lt;/g, "<")
        .replace(/&gt;/g, ">")
        .replace(/&amp;/g, "&");
}

const results = new Map();
const sources = [];
for (const root of RESULT_ROOTS) {
    for (const file of walkXml(join(ROOT, root))) {
        const xml = readFileSync(file, "utf8");
        for (const m of xml.matchAll(/<testcase\b([^>]*?)\/>|<testcase\b([^>]*?)>([\s\S]*?)<\/testcase>/g)) {
            const attrs = m[1] ?? m[2];
            const body = m[3] ?? "";
            const name = attr(attrs, "name");
            const classname = attr(attrs, "classname");
            if (!name || !classname) continue;
            const status = /<failure\b/.test(body) ? "failed" : /<skipped\b/.test(body) ? "skipped" : "passed";
            results.set(classname + "#" + name, status);
        }
        sources.push({ file: file.replace(ROOT + "\\", "").replace(ROOT + "/", "").replace(/\\/g, "/") });
    }
}

function statusOf(key, label) {
    const status = results.get(key);
    if (!status) fail("找不到测试 " + label + "（" + key + "）；先跑 ./gradlew :domain:rules:test :domain:state:test");
    return status;
}

const tests = [];

// ---------------------------------------------------------------- 规则矩阵
const fixtures = readJson("assets/fixtures/fixtures-rules-v1.json").fixtures;
const fixtureCells = new Set(fixtures.map((f) => "rule:" + f.ruleId + "|case:" + f.case));
const expectedRuleCells = expectedRules();
for (const cell of fixtureCells) {
    if (!expectedRuleCells.has(cell)) fail("fixture 产生了矩阵外的格子：" + cell);
}
for (const cell of expectedRuleCells) {
    if (!fixtureCells.has(cell)) fail("fixture 没有覆盖矩阵格子：" + cell);
}
const ruleStatus = statusOf(RULES_TEST, "FixtureTest 60 条规则回归") ?? "failed";
for (const f of fixtures) {
    if (!CASES.includes(f.case)) {
        fail("fixture " + f.fixtureId + " 的 case 不在四类之内：" + f.case);
        continue;
    }
    tests.push({
        id: f.fixtureId,
        status: ruleStatus,
        tags: ["rule:" + f.ruleId, "case:" + f.case],
    });
}

// ---------------------------------------------------------------- 状态机矩阵
for (const [machine, grid] of Object.entries(FSM_GRID)) {
    const status = statusOf(FSM_TESTS[machine], machine + " full matrix") ?? "failed";
    for (const s of grid.states) {
        for (const e of grid.events) {
            const tag = "fsm:" + machine + ":" + s + ":" + e;
            tests.push({ id: tag, status, tags: [tag] });
        }
    }
}

// 生成物必须与文档规格一致：这里只做集合一致性检查，不判断用例语义。
const actualFsm = new Set(tests.flatMap((t) => t.tags).filter((x) => x.startsWith("fsm:")));
for (const cell of expectedFsm()) {
    if (!actualFsm.has(cell)) fail("生成的矩阵缺少状态机格子：" + cell);
}

const outPath = join(ROOT, OUT);
mkdirSync(dirname(outPath), { recursive: true });
writeFileSync(outPath, JSON.stringify({ schema: 1, sources, tests }, null, 2) + "\n");

const passed = tests.filter((t) => t.status === "passed").length;
console.log("[gen-matrix] " + OUT);
console.log("[gen-matrix] tests " + passed + "/" + tests.length + " passed（规则 " + fixtures.length + " 条，状态机 " + expectedFsm().size + " 格）");
if (errors.length) {
    console.error("[gen-matrix] " + errors.length + " 个问题，矩阵不可用于放行");
    process.exit(1);
}
console.log("[gen-matrix] OK");

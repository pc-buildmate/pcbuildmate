#!/usr/bin/env node
/**
 * 关键变异 MUT-01..12：直接改一处源码 -> 跑目标测试 -> 恢复源码。
 * 不用 PIT：课程项目只有 12 条关键变异，手工补丁更可解释。
 *
 * 用法：GRADLE_BIN=/path/to/gradle node tools/run-mutations.mjs
 * 产物：build/reports/key-mutations.json（供 check-matrix --mutations）
 */
import { readFileSync, writeFileSync, mkdirSync } from "node:fs";
import { execFileSync } from "node:child_process";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const GRADLE = process.env.GRADLE_BIN || join(ROOT, "gradlew");
const OUT = join(ROOT, "build/reports/key-mutations.json");

const mutations = [
  {
    id: "MUT-01", desc: "EMPTY/missing -> PASSED",
    file: "domain/rules/src/main/kotlin/pcbuildmate/domain/rules/DefaultRuleEngine.kt",
    find: "private fun missingPart(rule: RuleDef): RuleOutcome.Unverified =\n        RuleOutcome.Unverified(\n            ruleId = rule.ruleId,\n            weakest = VerificationState.VERIFIED,\n            missingField = null,\n            reason = UnverifiedReason.MISSING_FIELD,\n        )",
    replace: "private fun missingPart(rule: RuleDef): RuleOutcome =\n        RuleOutcome.Passed(\n            ruleId = rule.ruleId,\n            weakest = VerificationState.VERIFIED,\n        )",
    test: ":domain:rules:test", killer: "FixtureTest",
  },
  {
    id: "MUT-02", desc: "source conflict ignored",
    file: "domain/rules/src/main/kotlin/pcbuildmate/domain/rules/DefaultRuleEngine.kt",
    find: "private fun firstConflict(left: Side, right: Side): ConflictHit? {\n        val field = when {",
    replace: "private fun firstConflict(left: Side, right: Side): ConflictHit? {\n        if (true) return null\n        val field = when {",
    test: ":domain:rules:test", killer: "FixtureTest anchor",
  },
  {
    id: "MUT-03", desc: "<= becomes <",
    file: "domain/rules/src/main/kotlin/pcbuildmate/domain/rules/DefaultRuleEngine.kt",
    find: "RuleKind.NUMERIC_LE -> number(left) <= number(right)",
    replace: "RuleKind.NUMERIC_LE -> number(left) < number(right)",
    test: ":domain:rules:test", killer: "FixtureTest",
  },
  {
    id: "MUT-04", desc: "weakest picks strongest",
    file: "domain/rules/src/main/kotlin/pcbuildmate/domain/rules/DefaultRuleEngine.kt",
    find: "if (a.ordinal >= b.ordinal) a else b",
    replace: "if (a.ordinal <= b.ordinal) a else b",
    test: ":domain:rules:test", killer: "FixtureTest",
  },
  {
    id: "MUT-05", desc: "stale diagnosis shown",
    file: "domain/compare/src/main/kotlin/pcbuildmate/domain/compare/DefaultBuildComparator.kt",
    find: "if (d != null && d.revisionId != rev.id) {",
    replace: "if (false) {",
    test: ":domain:compare:test", killer: "DefaultBuildComparatorTest.stale",
  },
  {
    id: "MUT-06", desc: "Apply skips Rechecking",
    file: "domain/state/src/main/kotlin/pcbuildmate/domain/state/ReplacementMachine.kt",
    find: "state = ReplacementState.Rechecking(\n                        ctx = state.ctx,\n                        previewRevisionId = previewRevisionId,\n                    ),",
    replace: "state = ReplacementState.Applied(previewRevisionId),",
    test: ":domain:state:test", killer: "ReplacementMachineTest",
  },
  {
    id: "MUT-07", desc: "replacement keeps new blockers",
    file: "domain/replacement/src/main/kotlin/pcbuildmate/domain/replacement/DefaultReplacementAdvisor.kt",
    find: "if (newBlockers.isNotEmpty()) return null",
    replace: "if (false) return null",
    test: ":domain:replacement:test", killer: "filters a candidate",
  },
  {
    id: "MUT-08", desc: "no candidate is hidden",
    file: "domain/replacement/src/main/kotlin/pcbuildmate/domain/replacement/DefaultReplacementAdvisor.kt",
    find: "noCandidate = if (candidates.isEmpty()) {\n                NoCandidateReason(",
    replace: "noCandidate = if (false) {\n                NoCandidateReason(",
    test: ":domain:replacement:test", killer: "structured no-candidate",
  },
  {
    id: "MUT-09", desc: "Undo reuses revision id",
    file: "feature/src/main/kotlin/pcbuildmate/feature/workspace/WorkspaceViewModel.kt",
    find: "id = ++revisionSeq,\n            buildId = current.buildId,",
    replace: "id = current.id,\n            buildId = current.buildId,",
    test: ":feature:testDebugUnitTest", killer: "WorkspaceRevisionTest",
  },
  {
    id: "MUT-10", desc: "over-budget build reported feasible",
    file: "domain/recommend/src/main/kotlin/pcbuildmate/domain/recommend/DefaultBudgetRecommender.kt",
    find: "if (total <= request.budgetCny) {",
    replace: "if (true) {",
    test: ":app:testDebugUnitTest", killer: "RecommendationIntegrationTest",
  },
  {
    id: "MUT-11", desc: "demo catalogue becomes production",
    file: "data/src/main/kotlin/pcbuildmate/data/catalogue/CatalogueParser.kt",
    find: "isDemo = dto.isDemo,",
    replace: "isDemo = false,",
    test: ":data:testDebugUnitTest", killer: "CatalogueParserTest",
  },
  {
    id: "MUT-12", desc: "stale diagnosis accessor leaks",
    file: "contracts/src/main/kotlin/pcbuildmate/contracts/Workspace.kt",
    find: "get() = diagnosis?.takeIf { it.revisionId == revision?.id }",
    replace: "get() = diagnosis",
    test: ":feature:testDebugUnitTest", killer: "WorkspaceUiStateTest",
  },
];

const results = [];
for (const m of mutations) {
  const path = join(ROOT, m.file);
  const original = readFileSync(path, "utf8");
  if (!original.includes(m.find)) {
    results.push({ ...m, status: "BROKEN", note: "patch not found" });
    continue;
  }
  writeFileSync(path, original.replace(m.find, m.replace));
  let status = "SURVIVED";
  let note = "";
  try {
    execFileSync(GRADLE, [m.test, "--no-daemon", "--console=plain", "-Pkotlin.compiler.execution.strategy=in-process"], {
      cwd: ROOT, stdio: "pipe", encoding: "utf8",
      env: { ...process.env, GRADLE_USER_HOME: join(ROOT, ".gradle-home") },
    });
  } catch (e) {
    const out = `${e.stdout || ""}\n${e.stderr || ""}`;
    note = out.split("\n").filter((l) => l.includes("FAILED") || l.includes("e: ")).slice(0, 3).join(" | ");
    status = /e: file:|Compilation error/.test(out) ? "BROKEN" : "KILLED";
  } finally {
    writeFileSync(path, original);
  }
  results.push({ ...m, status, note });
  console.log(`${m.id} ${status}  ${m.desc}`);
}

// 恢复源码后重跑一遍干净测试，避免失败 XML 留在 test-results 里污染矩阵。
try {
  execFileSync(GRADLE, [
    ":data:testDebugUnitTest", ":feature:testDebugUnitTest", ":app:testDebugUnitTest",
    ":domain:rules:test", ":domain:revision:test", ":domain:recommend:test",
    ":domain:replacement:test", ":domain:state:test", ":domain:compare:test",
    "--rerun-tasks", "--no-daemon", "--console=plain",
    "-Pkotlin.compiler.execution.strategy=in-process",
  ], { cwd: ROOT, stdio: "pipe", encoding: "utf8", env: { ...process.env, GRADLE_USER_HOME: join(ROOT, ".gradle-home") } });
  console.log("[mutations] clean test suite passed");
} catch (e) {
  console.error("[mutations] clean test suite failed — matrix will contain holes");
  process.exitCode = 1;
}

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify({ mutations: results }, null, 2) + "\n");
console.log(`[mutations] -> ${OUT}`);

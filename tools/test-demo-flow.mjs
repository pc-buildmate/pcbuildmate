#!/usr/bin/env node
/**
 * 无头跑一遍完整用户旅程（与 UI 走同一个 reducer），验证 UI 逻辑而不是截图。
 * 用法：node tools/test-demo-flow.mjs
 */
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(import.meta.url);
globalThis.PBEngine = require(join(ROOT, "site/engine.js"));
const engine = globalThis.PBEngine;
const S = require(join(ROOT, "site/state.js"));

const data = JSON.parse(readFileSync(join(ROOT, "assets/catalogue/demo-v1.json"), "utf8"));
const rules = JSON.parse(readFileSync(join(ROOT, "assets/rules/rules-v1.json"), "utf8")).rules;

const BUILD_A = {
  CPU: "cpu-demo-7600x", MOTHERBOARD: "mb-demo-b650", MEMORY: "mem-demo-ddr5-32",
  GPU: "gpu-demo-337", PSU: "psu-demo-750", CASE: "case-demo-320",
  COOLER: "cooler-demo-160", STORAGE: "storage-demo-nvme"
};
const BUILD_B = {
  CPU: "cpu-demo-7600x", MOTHERBOARD: "mb-demo-b650", MEMORY: "mem-demo-ddr5-32",
  PSU: "psu-demo-550", CASE: "case-demo-320", COOLER: "cooler-demo-160", STORAGE: "storage-demo-nvme"
};

const fails = [];
const eq = (label, got, want) => {
  if (JSON.stringify(got) !== JSON.stringify(want)) fails.push(label + "：期望 " + JSON.stringify(want) + "，实际 " + JSON.stringify(got));
};
const truthy = (label, v) => { if (!v) fails.push(label + "：应为真"); };

function createApp(picks) {
  let app = S.createApp({
    catalogue: data.components, catalogueVersion: data.catalogueVersion, priceDate: data.priceDate,
    rules: rules,
    evidenceOverrides: { "mb-demo-b650.biosGeneration": { state: "EMPTY", value: null } }
  });
  if (picks) {
    const r = S.reduce(app, { type: "LoadDemo", picks: picks });
    app = r.state;
  }
  return app;
}

/** 同步驱动器：把 Effect 立刻执行掉，再喂回完成事件。 */
function fire(app, ev) {
  let queue = [ev];
  let guard = 0;
  while (queue.length) {
    if (++guard > 50) throw new Error("effect 循环");
    const e = queue.shift();
    const r = S.reduce(app, e);
    app = r.state;
    for (const ef of r.effects) {
      if (ef.type === "RunDiagnosis") {
        const rev = app.revision;
        const diag = engine.diagnose(rev, app.catalogue, {
          rules: app.rules, evidenceOverrides: app.evidenceOverrides,
          catalogueVersion: app.catalogueVersion, now: "t0"
        });
        queue.push({ type: "CheckCompleted", diagnosis: diag });
      } else if (ef.type === "LoadCandidates") {
        const conflict = app.replacement.conflict;
        queue.push({ type: "CandidatesLoaded", result: S.suggest(app, conflict) });
      } else if (ef.type === "Recheck") {
        const diag = engine.diagnose(ef.revision, app.catalogue, {
          rules: app.rules, evidenceOverrides: app.evidenceOverrides,
          catalogueVersion: app.catalogueVersion, now: "t1"
        });
        queue.push({ type: "RecheckCompleted", diagnosis: diag });
      }
    }
  }
  return app;
}

// ---------------------------------------------------------------- 1. 主流程
let app = createApp(BUILD_A);
eq("A 总价", app.revision.totalCny, 7800);
app = fire(app, { type: "StartCheck" });
// 有 BLOCKER 时 overall 直接 CONFLICT（02 章 §2.3.2 第 1 行），不是 MIXED
eq("overall", app.diagnosis.overall, "CONFLICT");
eq("冲突数", app.diagnosis.counts.conflict, 2);
eq("未验证数", app.diagnosis.counts.unverified, 1);
eq("分数", app.diagnosis.score, 52); // 100 -30*BLOCKER -15*MAJOR -3*UNVERIFIED
const gpuConflict = app.diagnosis.outcomes.find((o) => o.ruleId === "GPU_CASE_LENGTH");
eq("GPU conflictId", gpuConflict.conflictId, "GPU_CASE_LENGTH:gpu-demo-337.lengthMm:case-demo-320.maxGpuLengthMm");
const bios = app.diagnosis.outcomes.find((o) => o.ruleId === "CPU_MB_BIOS_SUPPORT");
eq("BIOS 状态", bios.status, "UNVERIFIED");
eq("BIOS missingField", bios.missingField, "biosGeneration");

// 选冲突 -> 候选
app = fire(app, { type: "SelectConflict", conflictId: gpuConflict.conflictId });
eq("候选状态", app.replacement.kind, "Candidates");
eq("首选候选", app.replacement.candidates[0].componentId, "gpu-demo-300");
eq("候选差价", app.replacement.candidates[0].priceDeltaCny, 100);

// 预览 -> 应用 -> 重检
app = fire(app, { type: "PreviewCandidate", componentId: "gpu-demo-300" });
eq("预览总价", app.replacement.previewRevision.totalCny, 7900);
eq("预览不改当前", app.revision.totalCny, 7800);
app = fire(app, { type: "Apply" });
eq("替换状态", app.replacement.kind, "Applied");
eq("应用后总价", app.revision.totalCny, 7900);
eq("应用后 overall", app.diagnosis.overall, "UNVERIFIED");
eq("应用后 GPU", app.diagnosis.outcomes.find((o) => o.ruleId === "GPU_CASE_LENGTH").status, "PASSED");
eq("应用后 BIOS", app.diagnosis.outcomes.find((o) => o.ruleId === "CPU_MB_BIOS_SUPPORT").status, "UNVERIFIED");
truthy("revision id 递增", app.revision.id > 2);

// ---------------------------------------------------------------- 2. 编辑清诊断
const before = app.revision.id;
app = fire(app, { type: "SetQuantity", componentId: "mem-demo-ddr5-32", quantity: 2 });
eq("编辑后诊断清空", app.diagnosis, null);
eq("编辑后状态 Idle", app.diagnosisState.kind, "Idle");
truthy("编辑产生新 revision", app.revision.id > before);

// ---------------------------------------------------------------- 3. 迟到结果被丢弃
app = fire(app, { type: "StartCheck" });            // Running(rev N)
const staleRevId = app.revision.id;
app = fire(app, { type: "SetQuantity", componentId: "mem-demo-ddr5-32", quantity: 1 }); // 编辑 -> Idle
const lateDiag = { revisionId: staleRevId, outcomes: [], overall: "PASSED", counts: { passed: 0, conflict: 0, unverified: 0 } };
app = fire(app, { type: "CheckCompleted", diagnosis: lateDiag });
eq("迟到结果不生效", app.diagnosis, null);
truthy("日志记录 DROPPED", app.log.some((l) => l.kind === "DROPPED"));

// ---------------------------------------------------------------- 4. 无候选
let app2 = createApp(BUILD_A);
app2 = fire(app2, { type: "ReplaceComponent", componentId: "case-demo-itx" });
app2 = fire(app2, { type: "StartCheck" });
const formFactor = app2.diagnosis.outcomes.find((o) => o.ruleId === "MB_CASE_FORM_FACTOR" && o.status === "CONFLICT");
truthy("ITX 机箱产生板型冲突", !!formFactor);
app2 = fire(app2, { type: "SelectConflict", conflictId: formFactor.conflictId });
eq("无候选状态", app2.replacement.kind, "NoCandidate");
truthy("无候选有原因", !!app2.replacement.reason.blockingConstraint);

// ---------------------------------------------------------------- 5. 撤销
let app3 = createApp(BUILD_A);
const revBefore = app3.revision.id;
app3 = fire(app3, { type: "SetQuantity", componentId: "gpu-demo-337", quantity: 2 });
const afterEdit = app3.revision.totalCny;
app3 = fire(app3, { type: "Undo" });
eq("撤销后总价恢复", app3.revision.totalCny, 7800);
truthy("撤销产生更大的 id", app3.revision.id > revBefore);
truthy("撤销前总价确实变过", afterEdit !== 7800);

// ---------------------------------------------------------------- 6. 不变量
const inv = S.invariants(app);
for (const c of inv) if (!c.ok) fails.push("不变量 " + c.id + " 失败：" + c.detail);

// ---------------------------------------------------------------- 7. 推荐
const rec = S.recommend(createApp(null), 8000, "GAMING");
truthy("推荐在预算内", rec.totalCny <= 8000);
truthy("推荐可行", rec.feasible);
truthy("权重视图非空", rec.weightsVisible.length > 0);
const rec2 = S.recommend(createApp(null), 3000, "GAMING");
eq("低预算不可行", rec2.feasible, false);
truthy("低预算有缺口", rec2.shortfallCny > 0);

if (fails.length) {
  console.error("[demo-flow] " + fails.length + " 处失败：");
  for (const f of fails) console.error("  ✗ " + f);
  process.exit(1);
}
console.log("[demo-flow] OK — 主流程 / 编辑清诊断 / 迟到丢弃 / 无候选 / 撤销 / 不变量 / 推荐 全部通过。");

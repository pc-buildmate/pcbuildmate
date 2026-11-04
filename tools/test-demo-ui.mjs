#!/usr/bin/env node
/**
 * UI 冒烟测试：用一个极小的 DOM 替身跑通所有渲染路径，确认没有运行时错误。
 * 不做像素断言（那需要浏览器），只保证「每种 state 都能渲染出来」。
 * 用法：node tools/test-demo-ui.mjs
 */
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { createRequire } from "node:module";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const require = createRequire(import.meta.url);

function makeNode(tag) {
  const n = {
    tagName: tag, children: [], dataset: {}, attributes: {},
    className: "", _text: "", hidden: false, disabled: false, value: "", type: "",
    style: { setProperty() {} },
    classList: { _s: new Set(), add(c) { this._s.add(c); }, remove(c) { this._s.delete(c); }, toggle(c, v) { if (v) this._s.add(c); else this._s.delete(c); }, contains(c) { return this._s.has(c); } },
    appendChild(c) { n.children.push(c); return c; },
    removeChild(c) { n.children = n.children.filter((x) => x !== c); return c; },
    addEventListener() {},
    setAttribute(k, v) { n.attributes[k] = v; },
    closest() { return null; },
    querySelector() { return null; },
    get firstChild() { return n.children[0] || null; },
    set textContent(v) { n._text = String(v); n.children = []; },
    get textContent() { return n._text; }
  };
  return n;
}

const doc = {
  createElement: makeNode,
  querySelector: () => null
};

const containers = {
  screen: makeNode("main"),
  sheet: makeNode("div"),
  inspector: makeNode("div"),
  tabs: makeNode("div"),
  chipCatalogue: makeNode("span"),
  chipPrice: makeNode("span"),
  slow: makeNode("input")
};

// 组装脚本环境
globalThis.window = globalThis;
globalThis.document = doc;
const load = (f) => require(join(ROOT, "site", f));
load("data.generated.js");
globalThis.PBEngine = load("engine.js");
globalThis.PBState = load("state.js");
const UI = load("ui.js");

const fails = [];
function step(label, fn) {
  try { fn(); } catch (e) { fails.push(label + " 抛出异常：" + e.message + "\n" + (e.stack || "").split("\n").slice(0, 3).join("\n")); }
}
const count = (node) => (node.children.length ? 1 + node.children.reduce((s, c) => s + count(c), 0) : 0);
const textOf = (node) => (node.children || []).reduce((s, c) => s + textOf(c), "") + (node._text || "");

step("mount", () => {
  UI.mount(doc, containers);
  if (!containers.screen.children.length) fails.push("首页没有任何节点");
});

step("载入演示配置", () => {
  UI.dispatch({ type: "LoadDemo", picks: { CPU: "cpu-demo-7600x", MOTHERBOARD: "mb-demo-b650", MEMORY: "mem-demo-ddr5-32", GPU: "gpu-demo-337", PSU: "psu-demo-750", CASE: "case-demo-320", COOLER: "cooler-demo-160", STORAGE: "storage-demo-nvme" } });
  const st = UI.getState();
  if (st.revision.totalCny !== 7800) fails.push("载入后总价应为 7800，实际 " + st.revision.totalCny);
  if (!containers.screen.children.length) fails.push("工作区没有渲染");
});

step("诊断完成", () => {
  const st = UI.getState();
  const Engine = globalThis.PBEngine;
  const diag = Engine.diagnose(st.revision, st.catalogue, { rules: st.rules, evidenceOverrides: st.evidenceOverrides, catalogueVersion: "static-v1" });
  UI.dispatch({ type: "CheckCompleted", diagnosis: diag });
  if (!UI.getState().diagnosis) fails.push("诊断没有写入 state");
});

step("诊断页渲染", () => {
  UI.dispatch({ type: "Goto", screen: "diagnostics" });
  if (!containers.screen.children.length) fails.push("诊断页没有渲染");
});

step("冲突 -> 候选", () => {
  const st = UI.getState();
  const c = st.diagnosis.outcomes.find((o) => o.ruleId === "GPU_CASE_LENGTH");
  UI.dispatch({ type: "SelectConflict", conflictId: c.conflictId });
  const result = globalThis.PBState.suggest(UI.getState(), c);
  UI.dispatch({ type: "CandidatesLoaded", result });
  if (UI.getState().replacement.kind !== "Candidates") fails.push("候选状态不对：" + UI.getState().replacement.kind);
});

step("预览与应用", () => {
  UI.dispatch({ type: "PreviewCandidate", componentId: "gpu-demo-300" });
  if (UI.getState().replacement.kind !== "Previewing") fails.push("预览状态不对");
  UI.dispatch({ type: "Apply" });
  if (UI.getState().replacement.kind !== "Rechecking") fails.push("应用应先进入重检，实际 " + UI.getState().replacement.kind);
  const st = UI.getState();
  const Engine = globalThis.PBEngine;
  const diag = Engine.diagnose(st.replacement.previewRevision, st.catalogue, { rules: st.rules, evidenceOverrides: st.evidenceOverrides, catalogueVersion: "static-v1" });
  UI.dispatch({ type: "RecheckCompleted", diagnosis: diag });
  if (UI.getState().replacement.kind !== "Applied") fails.push("重检通过后应为 Applied，实际 " + UI.getState().replacement.kind);
  if (UI.getState().revision.totalCny !== 7900) fails.push("应用后总价应为 7900");
});

step("部件弹层", () => {
  UI.setUi({ mode: "part", partCat: "GPU" });
  if (containers.sheet.hidden) fails.push("部件弹层没有打开");
  UI.setUi({ mode: "picker", pickerCat: "CASE" });
  if (!containers.sheet.children.length) fails.push("选择器没有渲染");
  UI.setUi({ mode: null });
});

step("保存与比较", () => {
  UI.dispatch({ type: "SaveBuild" });
  if (!UI.getState().savedBuilds.length) fails.push("保存失败");
  UI.dispatch({ type: "Goto", screen: "compare" });
  if (!containers.screen.children.length) fails.push("比较页没有渲染");
});

step("比较页的过期诊断分支", () => {
  const st = UI.getState();
  const fake = {
    id: "stale-1", name: "过期样本",
    revision: { id: 77, items: st.revision.items.slice(0, 3), totalCny: 2900, isDemo: true },
    diagnosis: { revisionId: 75, overall: "PASSED", score: 100, counts: { passed: 10, conflict: 0, unverified: 0 }, outcomes: [] }
  };
  st.savedBuilds.push(fake);
  UI.setUi({ compareIds: ["stale-1", "current"] });
  UI.dispatch({ type: "Goto", screen: "compare" });
  const text = textOf(containers.screen);
  if (!text.includes("诊断过期")) fails.push("比较页没有显示「诊断过期」");
  st.savedBuilds.pop();
});

step("四个面板", () => {
  ["state", "invariants", "log", "evidence", "about"].forEach((tab) => {
    UI.setUi({ tab });
    if (!containers.inspector.children.length) fails.push(tab + " 面板没有渲染");
  });
});

step("编辑清诊断", () => {
  UI.dispatch({ type: "Goto", screen: "workspace" });
  UI.dispatch({ type: "SetQuantity", componentId: "mem-demo-ddr5-32", quantity: 2 });
  if (UI.getState().diagnosis) fails.push("编辑后诊断应清空");
});

const rendered = textOf(containers.screen) + textOf(containers.inspector) + textOf(containers.sheet);
for (const bad of ["undefined", "NaN", "[object Object]", "null"]) {
  if (rendered.includes(bad)) fails.push("渲染文本里出现了 " + bad);
}

const nodes = count(containers.screen) + count(containers.inspector);
if (fails.length) {
  console.error("[demo-ui] " + fails.length + " 处失败：");
  for (const f of fails) console.error("  ✗ " + f);
  process.exit(1);
}
console.log("[demo-ui] OK — 入口/工作区/诊断/替换/比较 + 5 个检查器面板均可渲染（" + nodes + " 个节点）。");

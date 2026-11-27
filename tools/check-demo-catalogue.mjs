#!/usr/bin/env node
/**
 * demo-v1 原型目录的守护检查（issue #197）。
 *
 * 背景：`assets/catalogue/demo-v1.json` 是 32 条原型资产（DEMO-01），被 site 原型、
 * `tools/test-demo-*.mjs` 和 `:data` 的单测当测试资源读。它曾被 `tools/gen-fixtures.mjs`
 * 顺手一起生成，但生成器手里的 DEMO 只有 16 条 fixture 锚点——谁跑一次生成，这份 32 条
 * 的原型目录就被静默打回 16 条。现在生成器不再写它，改由本脚本守住它的形状。
 *
 * 检查内容：
 *   1. 32 条、8 类每类 ≥3、componentId 唯一
 *   2. 记录元数据：isDemo / catalogueVersion / priceDate 与文件头一致
 *   3. 证据三态自洽：EMPTY 不带 value/numeric；VERIFIED/REPORTED 必须有 value；
 *      numeric 若存在必须等于 value
 *   4. 字段路径都在 docs/03 §3.3 冻结表内
 *   5. 答辩锚点：8 件套合计 7800，换 300mm 卡后 7900
 *
 * 用法：
 *   node tools/check-demo-catalogue.mjs             # 检查
 *   node tools/check-demo-catalogue.mjs --self-test # 自测：把数据改坏必须红
 */
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const DEMO_FILE = "assets/catalogue/demo-v1.json";
const DOCS_FILE = "docs/03-rules.md";

const CATEGORIES = ["CPU", "MOTHERBOARD", "MEMORY", "GPU", "PSU", "CASE", "COOLER", "STORAGE"];
const MIN_PER_CATEGORY = 3;
const EXPECTED_RECORDS = 32;

/** 演示配置 A（¥7,800）与换 300mm 显卡后的 B（¥7,900）；与 README / 答辩脚本同一组锚点。 */
const BUILD_A = [
  "cpu-demo-7600x", "mb-demo-b650", "mem-demo-ddr5-32", "gpu-demo-337",
  "psu-demo-750", "case-demo-320", "cooler-demo-160", "storage-demo-nvme"
];
const BUILD_B = BUILD_A.map((id) => (id === "gpu-demo-337" ? "gpu-demo-300" : id));
const TOTALS = [
  { label: "7800 演示配置", ids: BUILD_A, total: 7800 },
  { label: "7900 替换后配置", ids: BUILD_B, total: 7900 }
];

const SPEC_KEYS = new Set(["value", "state", "sources", "conflict", "numeric"]);
const STATES = new Set(["VERIFIED", "REPORTED", "EMPTY"]);

/** docs/03 §3.3 冻结表 → Set<"CATEGORY.path">（与 ci/check-field-paths.mjs 的 parseDocs 同构）。 */
export function parseFieldPaths(text) {
  const start = text.indexOf("字段路径冻结表");
  if (start < 0) throw new Error("docs/03 里找不到 §3.3 字段路径冻结表");
  const rest = text.slice(start);
  const end = rest.indexOf("\n## ", 1);
  const section = end > 0 ? rest.slice(0, end) : rest;
  const out = new Set();
  for (const line of section.split("\n")) {
    if (!line.startsWith("|")) continue;
    const cells = line.split("|").map((c) => c.trim());
    const category = cells[1];
    if (!CATEGORIES.includes(category)) continue;
    const path = (cells[2] || "").replace(/`/g, "");
    if (!path || path === "路径") continue;
    out.add(category + "." + path);
  }
  if (!out.size) throw new Error("docs/03 §3.3 冻结表解析为空");
  return out;
}

const isDate = (v) => typeof v === "string" && /^\d{4}-\d{2}-\d{2}$/.test(v);
const nonEmptyString = (v) => typeof v === "string" && v.trim() !== "";

/** 单个字段的证据三态校验；`at` 用于定位（第几条 + 字段路径）。 */
function checkSpec(at, spec, add) {
  if (!spec || typeof spec !== "object" || Array.isArray(spec)) {
    add(at + " 不是证据对象");
    return;
  }
  for (const key of Object.keys(spec)) {
    if (!SPEC_KEYS.has(key)) add(`${at} 有未知键 ${key}（只允许 value/state/sources/conflict/numeric）`);
  }
  const { value, state, numeric, sources, conflict } = spec;
  if (!STATES.has(state)) add(`${at} state 非法：${state}`);
  if (sources !== undefined && !Array.isArray(sources)) add(at + " sources 不是数组");
  if (conflict !== undefined && typeof conflict !== "boolean") add(at + " conflict 不是布尔");

  if (state === "EMPTY") {
    if (value !== null) add(`${at} state=EMPTY 但 value 不是 null`);
    if (numeric !== null) add(`${at} state=EMPTY 但 numeric 不是 null`);
    return;
  }
  if (!nonEmptyString(value)) {
    add(`${at} state=${state} 但没有 value`);
    return;
  }
  if (numeric !== null) {
    if (typeof numeric !== "number" || !Number.isFinite(numeric)) {
      add(at + " numeric 不是有限数字");
      return;
    }
    const parsed = Number(value);
    if (!Number.isFinite(parsed)) add(`${at} numeric=${numeric} 但 value 不是数字："${value}"`);
    else if (Math.abs(parsed - numeric) > 1e-9) add(`${at} numeric=${numeric} 与 value="${value}" 不一致`);
  }
}

/**
 * 校验一份 demo-v1 文档；返回问题列表（空数组 = 通过）。
 * @param {object} doc 解析后的 demo-v1.json
 * @param {Set<string>} fieldPaths docs/03 §3.3 的字段路径集合
 * @returns {string[]}
 */
export function validateDemoCatalogue(doc, fieldPaths) {
  const problems = [];
  const add = (msg) => problems.push(msg);

  if (!doc || typeof doc !== "object" || Array.isArray(doc)) {
    add("文件不是 JSON 对象");
    return problems;
  }
  if (!Array.isArray(doc.components)) {
    add("缺 components 数组");
    return problems;
  }
  if (doc.isDemo !== true) add("文件头 isDemo 必须为 true");
  if (!nonEmptyString(doc.catalogueVersion)) add("文件头 catalogueVersion 为空");
  if (!isDate(doc.priceDate)) add("文件头 priceDate 不是 YYYY-MM-DD：" + doc.priceDate);
  if (doc.components.length !== EXPECTED_RECORDS) {
    add(`记录数应为 ${EXPECTED_RECORDS}，实际 ${doc.components.length}（DEMO-01 的口径）`);
  }

  const seen = new Set();
  const perCategory = Object.fromEntries(CATEGORIES.map((c) => [c, 0]));
  doc.components.forEach((r, i) => {
    const at = `第 ${i + 1} 条${r && r.componentId ? "（" + r.componentId + "）" : ""}`;
    if (!r || typeof r !== "object" || Array.isArray(r)) {
      add(at + " 不是对象");
      return;
    }
    if (!nonEmptyString(r.componentId)) add(at + " 缺 componentId");
    else if (seen.has(r.componentId)) add(at + " componentId 重复");
    else seen.add(r.componentId);

    if (!CATEGORIES.includes(r.category)) {
      add(at + " category 非法：" + r.category);
      return;
    }
    perCategory[r.category]++;

    for (const field of ["manufacturer", "model", "partNumber"]) {
      if (!nonEmptyString(r[field])) add(`${at} 的 ${field} 为空`);
    }
    if (!Number.isInteger(r.priceCny) || r.priceCny < 0) add(at + " 的 priceCny 不是非负整数");
    if (r.isDemo !== true) add(at + " 必须 isDemo=true（原型数据不得混入上架目录）");
    if (r.catalogueVersion !== doc.catalogueVersion) add(at + " 的 catalogueVersion 与文件头不一致");
    if (r.priceDate !== doc.priceDate) add(at + " 的 priceDate 与文件头不一致");

    if (!r.attributes || typeof r.attributes !== "object" || Array.isArray(r.attributes) || !Object.keys(r.attributes).length) {
      add(at + " 的 attributes 为空或不是对象");
      return;
    }
    for (const [path, spec] of Object.entries(r.attributes)) {
      const key = r.category + "." + path;
      if (!fieldPaths.has(key)) add(`${at} 的字段路径不在 docs/03 §3.3 冻结表：${key}`);
      checkSpec(`${at} ${path}`, spec, add);
    }
  });

  for (const category of CATEGORIES) {
    if (perCategory[category] < MIN_PER_CATEGORY) {
      add(`${category} 类只有 ${perCategory[category]} 条（应 ≥${MIN_PER_CATEGORY}）`);
    }
  }

  const byId = new Map(
    doc.components.filter((r) => r && typeof r.componentId === "string").map((r) => [r.componentId, r])
  );
  for (const id of new Set([...BUILD_A, ...BUILD_B])) {
    if (!byId.has(id)) add("答辩锚点组件缺失：" + id);
  }
  for (const { label, ids, total } of TOTALS) {
    if (!ids.every((id) => byId.has(id))) continue; // 缺件已在上面点名，别重复报总价
    const sum = ids.reduce((s, id) => s + (byId.get(id).priceCny || 0), 0);
    if (sum !== total) add(`${label} 合计应为 ¥${total}，实际 ¥${sum}`);
  }
  return problems;
}

function summarize(doc) {
  const components = Array.isArray(doc?.components) ? doc.components : [];
  const perCategory = Object.fromEntries(CATEGORIES.map((c) => [c, 0]));
  const states = { VERIFIED: 0, REPORTED: 0, EMPTY: 0 };
  for (const r of components) {
    if (!r || typeof r !== "object") continue;
    if (perCategory[r.category] !== undefined) perCategory[r.category]++;
    for (const spec of Object.values(r.attributes || {})) {
      if (spec && states[spec.state] !== undefined) states[spec.state]++;
    }
  }
  return {
    records: components.length,
    categories: perCategory,
    states,
    summary:
      `${components.length} 条 / ${CATEGORIES.length} 类（每类 ≥${MIN_PER_CATEGORY}）/ ` +
      `证据 VERIFIED ${states.VERIFIED} · REPORTED ${states.REPORTED} · EMPTY ${states.EMPTY} / ` +
      "锚点 7800 → 7900"
  };
}

/** 从磁盘读 demo-v1 与 docs/03，返回 { problems, stats, summary }。JSON 坏了也返回问题而不是抛栈。 */
export function checkDemoCatalogueFile(root = ROOT) {
  let doc;
  try {
    doc = JSON.parse(readFileSync(join(root, DEMO_FILE), "utf8"));
  } catch (e) {
    return { problems: ["无法解析 " + DEMO_FILE + "：" + e.message], stats: null, summary: "无法解析" };
  }
  const fieldPaths = parseFieldPaths(readFileSync(join(root, DOCS_FILE), "utf8"));
  const problems = validateDemoCatalogue(doc, fieldPaths);
  const stats = summarize(doc);
  return { problems, stats, summary: stats.summary };
}

function clone(o) {
  return JSON.parse(JSON.stringify(o));
}

/** 自测：先确认真实数据是绿的，再逐项改坏，确认每条规则真的会红。 */
function selfTest() {
  const doc = JSON.parse(readFileSync(join(ROOT, DEMO_FILE), "utf8"));
  const fieldPaths = parseFieldPaths(readFileSync(join(ROOT, DOCS_FILE), "utf8"));
  const base = validateDemoCatalogue(doc, fieldPaths);
  if (base.length) {
    console.error("[demo-catalogue] 自测失败：真实数据本身就有问题 —— " + base.slice(0, 3).join("；"));
    process.exit(1);
  }

  const mutate = (label, needle, fn) => {
    const copy = clone(doc);
    fn(copy);
    const problems = validateDemoCatalogue(copy, fieldPaths);
    return { label, needle, problems };
  };
  const cases = [
    mutate("删掉一条记录", "记录数应为", (d) => d.components.pop()),
    mutate("isDemo 改成 false", "isDemo=true", (d) => { d.components[0].isDemo = false; }),
    mutate("EMPTY 带 numeric", "state=EMPTY 但 numeric", (d) => {
      d.components.find((r) => r.componentId === "mb-demo-b650").attributes.biosGeneration =
        { value: null, state: "EMPTY", sources: [], conflict: false, numeric: 5 };
    }),
    mutate("numeric 与 value 不一致", "与 value", (d) => {
      d.components.find((r) => r.componentId === "cpu-demo-7600x").attributes.tdpW.numeric = 106;
    }),
    mutate("componentId 重复", "componentId 重复", (d) => { d.components[1].componentId = d.components[0].componentId; }),
    mutate("未知字段路径", "不在 docs/03", (d) => {
      d.components[0].attributes.totallyNewField =
        { value: "1", state: "VERIFIED", sources: [], conflict: false, numeric: 1 };
    }),
    mutate("锚点价格被改", "7800 演示配置 合计", (d) => {
      d.components.find((r) => r.componentId === "gpu-demo-337").priceCny = 3401;
    })
  ];

  const failed = cases.filter((c) => !c.problems.some((p) => p.includes(c.needle)));
  if (failed.length) {
    for (const c of failed) {
      console.error(`✗ 自测「${c.label}」没有被抓到（期望命中「${c.needle}」）：${c.problems.slice(0, 3).join("；") || "无问题"}`);
    }
    process.exit(1);
  }
  console.log(`[demo-catalogue] 自测通过：真实数据 0 问题，${cases.length} 种改坏全部被抓到。`);
}

function main() {
  if (process.argv.includes("--self-test")) {
    selfTest();
    return;
  }
  const { problems, summary } = checkDemoCatalogueFile(ROOT);
  if (problems.length) {
    console.error(`✗ [demo-catalogue] ${problems.length} 个问题：`);
    for (const p of problems) console.error("  " + p);
    console.error("  修法：demo-v1 是 DEMO-01 的 32 条原型资产，改数据/形状；不要改本检查的期望值。");
    process.exit(1);
  }
  console.log("[demo-catalogue] OK — " + summary);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main();

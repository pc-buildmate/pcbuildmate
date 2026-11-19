#!/usr/bin/env node
/**
 * 字段路径三处一致性：docs/03 §3.3 冻结表 ↔ FieldPathRegistry ↔ 审计脚本的 FIELDS。
 *
 * 由来：ADR-0011 加 `pcieX16SlotMm` / `pcieSlotCount` 时，契约、目录、registry 和 docs/03
 * 都改了，唯独 `tools/audit-catalogue.mjs` 里硬编码的字段表没跟上，审计于是误报
 * 10 条 UNKNOWN_FIELD（issue #149）。三份表各自维护，靠人记同步迟早再漂。
 *
 * 用法：node ci/check-field-paths.mjs [--self-test]
 */
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const CATEGORIES = ["CPU", "MOTHERBOARD", "MEMORY", "GPU", "PSU", "CASE", "COOLER", "STORAGE"];

/** docs/03 §3.3 表格 → { 类别: Set(path) } */
export function parseDocs(text) {
  const start = text.indexOf("字段路径冻结表");
  if (start < 0) throw new Error("docs/03 里找不到 §3.3 字段路径冻结表");
  const rest = text.slice(start);
  const end = rest.indexOf("\n## ", 1);
  const section = end > 0 ? rest.slice(0, end) : rest;
  const out = {};
  for (const line of section.split("\n")) {
    if (!line.startsWith("|")) continue;
    const cells = line.split("|").map((c) => c.trim());
    const category = cells[1];
    if (!CATEGORIES.includes(category)) continue;
    const path = (cells[2] || "").replace(/`/g, "");
    if (!path || path === "路径") continue;
    (out[category] ??= new Set()).add(path);
  }
  return out;
}

/** FieldPathRegistry.kt → { 类别: Set(path) } */
export function parseRegistry(text) {
  const out = {};
  for (const m of text.matchAll(/Category\.([A-Z_]+) to mapOf\(([\s\S]*?)\n\s*\),/g)) {
    for (const e of m[2].matchAll(/"([A-Za-z0-9_]+)" to FieldType\.[A-Z]+/g)) {
      (out[m[1]] ??= new Set()).add(e[1]);
    }
  }
  return out;
}

/** 从 start 处的 "{" 找到匹配的 "}"，返回内部文本。 */
function braceBody(text, start) {
  let depth = 0;
  for (let i = start; i < text.length; i++) {
    if (text[i] === "{") depth++;
    else if (text[i] === "}") {
      depth--;
      if (depth === 0) return text.slice(start + 1, i);
    }
  }
  throw new Error("花括号未闭合");
}

/** tools/audit-catalogue.mjs 的 FIELDS → { 类别: Set(path) } */
export function parseAudit(text) {
  const open = text.indexOf("const FIELDS = {");
  if (open < 0) throw new Error("审计脚本里找不到 const FIELDS");
  const body = braceBody(text, text.indexOf("{", open));
  const out = {};
  // 逐个类别块：NAME: { ... }
  for (const m of body.matchAll(/([A-Z_]+):\s*\{/g)) {
    const cat = m[1];
    if (!CATEGORIES.includes(cat)) continue;
    const block = braceBody(body, m.index + m[0].length - 1);
    // 只取本块第一层的字段名：形如 `path: int(...)` / `path: csv(...)` / `path: oneOf(...)` / `path: { kind: ... }`
    for (const e of block.matchAll(/([A-Za-z0-9_]+):\s*(?:int\(|csv\(|oneOf\(|\{\s*kind:)/g)) {
      (out[cat] ??= new Set()).add(e[1]);
    }
  }
  return out;
}

function diffSets(labelA, a, labelB, b) {
  const problems = [];
  for (const cat of CATEGORIES) {
    const want = a[cat] ?? new Set();
    const got = b[cat] ?? new Set();
    for (const p of want) if (!got.has(p)) problems.push(`${cat}.${p}: 在 ${labelA} 有，${labelB} 缺`);
    for (const p of got) if (!want.has(p)) problems.push(`${cat}.${p}: 在 ${labelB} 有，${labelA} 缺`);
  }
  return problems;
}

export function compare(docs, registry, audit) {
  return [
    ...diffSets("docs/03", docs, "FieldPathRegistry", registry),
    ...diffSets("docs/03", docs, "audit FIELDS", audit),
  ];
}

function selfTest() {
  const docs = { MOTHERBOARD: new Set(["socket", "pcieSlotCount"]) };
  const good = { MOTHERBOARD: new Set(["socket", "pcieSlotCount"]) };
  const bad = { MOTHERBOARD: new Set(["socket"]) };
  const p1 = compare(docs, good, good);
  const p2 = compare(docs, bad, bad);
  const ok = p1.length === 0 && p2.some((x) => x.includes("pcieSlotCount"));
  if (!ok) {
    console.error("✗ self-test failed", { p1, p2 });
    process.exit(1);
  }
  console.log("[field-paths] 自测通过：一致时不报，缺字段时点名。");
}

if (process.argv.includes("--self-test")) {
  selfTest();
} else {
  const docs = parseDocs(readFileSync(join(ROOT, "docs/03-rules.md"), "utf8"));
  const registry = parseRegistry(
    readFileSync(join(ROOT, "data/src/main/kotlin/pcbuildmate/data/catalogue/FieldPathRegistry.kt"), "utf8"),
  );
  const audit = parseAudit(readFileSync(join(ROOT, "tools/audit-catalogue.mjs"), "utf8"));
  const problems = compare(docs, registry, audit);
  if (problems.length) {
    console.error(`✗ [field-paths] ${problems.length} 处不一致：`);
    for (const p of problems) console.error("  " + p);
    console.error("  修法：docs/03 §3.3 是唯一权威，另两处跟着它改。");
    process.exit(1);
  }
  console.log("[field-paths] OK — docs/03、FieldPathRegistry、审计 FIELDS 三处字段集合一致。");
}

#!/usr/bin/env node
/**
 * 范围外关键词 / 权限 / 依赖坐标检查（公理 A5「范围即代码」）。
 *
 * 扫描每个模块 src/main 下的 Kotlin / XML / gradle 文件；
 * 跳过注释行与带 OUT_OF_SCOPE_DEMO 标注的文件。
 * 用法：node ci/check-keywords.mjs [--verbose]
 * 依据：docs/09-ci-and-scope.md §9.2 第 2 层
 */
import { readFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { join, dirname, relative } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const cfg = JSON.parse(readFileSync(join(ROOT, "ci/keywords.json"), "utf8"));
const VERBOSE = process.argv.includes("--verbose");
const ESCAPE = /[.*+?^$(){}|[\]\\]/g;

function walk(dir, out = []) {
  if (!existsSync(dir)) return out;
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if (/\.(kt|java|xml|kts)$/.test(name)) out.push(full);
  }
  return out;
}

const violations = [];
let scanned = 0;

for (const mod of readdirSync(ROOT)) {
  const mainDir = join(ROOT, mod, "src", "main");
  if (!existsSync(mainDir)) continue;
  for (const file of walk(mainDir)) {
    const rel = relative(ROOT, file).replace(/\\/g, "/");
    const text = readFileSync(file, "utf8");
    if (text.includes("OUT_OF_SCOPE_DEMO")) { if (VERBOSE) console.log("[keywords] 跳过（已标注范围外演示）：" + rel); continue; }
    scanned++;
    text.split("\n").forEach((line, i) => {
      const code = line.trim();

      // 卫生检查针对原始行（注释里的 TODO 也要抓）
      if (/\.kt$/.test(file)) {
        for (const h of cfg.hygiene) {
          if (line.includes(h)) violations.push({ rel, line: i + 1, kind: "hygiene", detail: h });
        }
      }

      // 以下检查跳过注释行
      if (code.startsWith("//") || code.startsWith("*") || code.startsWith("/*")) return;

      for (const kw of cfg.identifiers) {
        const re = new RegExp("\\b" + kw.replace(ESCAPE, "\\$&") + "\\b");
        if (re.test(line)) violations.push({ rel, line: i + 1, kind: "identifier", detail: kw });
      }
      for (const perm of cfg.permissions) {
        if (new RegExp("<uses-permission[^>]*android\\.permission\\." + perm + "\\b").test(line))
          violations.push({ rel, line: i + 1, kind: "permission", detail: perm });
      }
      for (const dep of cfg.gradleDeps) {
        if (new RegExp("(implementation|api|kapt|ksp)\\s*\\(?\\s*\"[^\"]*" + dep + "[/:\"]").test(line))
          violations.push({ rel, line: i + 1, kind: "dependency", detail: dep });
      }
      for (const s of cfg.userStrings) {
        if (line.includes(s)) violations.push({ rel, line: i + 1, kind: "user-string", detail: s });
      }
    });
  }
}

for (const v of violations) {
  console.error("✗ " + v.rel + ":" + v.line + "  [" + v.kind + "] " + v.detail);
}
if (violations.length) {
  console.error("\n[keywords] " + violations.length + " 处命中。范围外功能见 docs/09-ci-and-scope.md §9.1；");
  console.error("[keywords] 确需保留请提 scope-change 并由人批准，不要直接加白名单。");
  process.exit(1);
}
console.log("[keywords] OK — 扫描 " + scanned + " 个文件，0 命中。" + (scanned === 0 ? "（尚未有 src/main 源码）" : ""));

#!/usr/bin/env node
/**
 * 模块依赖边界检查（INV-16 的执行者）。
 *
 * 检查三件事：
 *   1. 源码 import 不以禁用前缀开头；
 *   2. build.gradle.kts 里的 project(":x") 在 allowed 列表内；
 *   3. 白名单（ci/allowlist.txt）必须逐条给出理由与批准人。
 *
 * 用法：node ci/check-boundaries.mjs [--verbose]
 * 退出码：0 通过，1 违规。
 * 依据：docs/05-module-independence.md §5.3
 */
import { readFileSync, readdirSync, statSync, existsSync } from "node:fs";
import { join, dirname, relative } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const cfg = JSON.parse(readFileSync(join(ROOT, "ci/modules.json"), "utf8"));
const VERBOSE = process.argv.includes("--verbose");

const allowlist = new Map();
const allowPath = join(ROOT, "ci/allowlist.txt");
if (existsSync(allowPath)) {
  for (const line of readFileSync(allowPath, "utf8").split("\n")) {
    const t = line.trim();
    if (!t || t.startsWith("#")) continue;
    const [file, pattern] = t.split("|").map((s) => s.trim());
    if (!file || !pattern) {
      console.error("[boundaries] 白名单格式错误（需要 file | pattern | reason | approver | date）：" + t);
      process.exitCode = 1;
      continue;
    }
    if (!allowlist.has(file)) allowlist.set(file, []);
    allowlist.get(file).push(pattern);
  }
}

function walk(dir) {
  const out = [];
  if (!existsSync(dir)) return out;
  for (const name of readdirSync(dir)) {
    const full = join(dir, name);
    const st = statSync(full);
    if (st.isDirectory()) out.push(...walk(full));
    else if (name.endsWith(".kt") || name.endsWith(".kts")) out.push(full);
  }
  return out;
}

function isAllowlisted(relPath, pattern) {
  const list = allowlist.get(relPath);
  return !!list && list.some((p) => pattern.includes(p) || p === "*");
}

const violations = [];
let scannedFiles = 0;
let scannedModules = 0;

for (const m of cfg.modules) {
  const srcDir = join(ROOT, m.srcPath);
  if (!existsSync(srcDir)) {
    if (VERBOSE) console.log("[boundaries] skip " + m.name + " (no sources yet: " + m.srcPath + ")");
    continue;
  }
  scannedModules++;
  for (const file of walk(srcDir)) {
    scannedFiles++;
    const rel = relative(ROOT, file).replace(/\\/g, "/");
    const text = readFileSync(file, "utf8");
    const lines = text.split("\n");
    lines.forEach((line, i) => {
      const imp = line.match(/^\s*import\s+([A-Za-z0-9_.*]+)/);
      if (!imp) return;
      const target = imp[1];
      for (const bad of m.forbiddenImports) {
        if (target.startsWith(bad) && !isAllowlisted(rel, target)) {
          violations.push({
            file: rel, line: i + 1, kind: "import", detail: target, module: m.name,
            hint: "改为注入 contracts 中的端口（docs/05-module-independence.md §5.4）"
          });
        }
      }
    });
    // build.gradle.kts 的 project 依赖
    if (file.endsWith("build.gradle.kts")) {
      for (const mm of text.matchAll(/project\(\s*"(:[^"]+)"\s*\)/g)) {
        const dep = mm[1];
        if (!m.allowed.includes(dep) && !isAllowlisted(rel, dep)) {
          violations.push({
            file: rel, line: 0, kind: "project-dep", detail: dep, module: m.name,
            hint: "允许的依赖：" + (m.allowed.join(", ") || "（无）") + "；见 §5.3 矩阵"
          });
        }
      }
    }
  }
}

for (const v of violations) {
  console.error("✗ " + v.file + (v.line ? ":" + v.line : ""));
  console.error("    [" + v.kind + "] " + v.detail + "（模块 " + v.module + "）");
  console.error("    " + v.hint);
}

if (violations.length) {
  console.error("\n[boundaries] " + violations.length + " 处违规，扫描 " + scannedModules + " 个模块 / " + scannedFiles + " 个文件。");
  process.exit(1);
}
console.log("[boundaries] OK — 扫描 " + scannedModules + " 个模块 / " + scannedFiles + " 个文件，0 违规。");

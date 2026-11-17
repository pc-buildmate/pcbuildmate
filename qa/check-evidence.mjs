#!/usr/bin/env node
/**
 * 发布前的证据完整性门：docs/21 里不能留空格子，引用的截图必须存在。
 *
 * 用法：node qa/check-evidence.mjs [--file docs/21-device-evidence.md]
 * 退出码：0 = 完整；1 = 有未填项或缺图。
 *
 * 背景：qa 目录下的脚本和 docs/21 的表格需要人填；12/01 发布会前，
 * "还有一格没填"必须能被机器查出来，而不是靠肉眼扫表格。
 */
import { readFileSync, existsSync } from "node:fs";
import { join, dirname, isAbsolute } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const argIdx = process.argv.indexOf("--file");
const file = argIdx > -1 ? process.argv[argIdx + 1] : "docs/21-device-evidence.md";
const path = isAbsolute(file) ? file : join(ROOT, file);

if (!existsSync(path)) {
  console.error(`✗ 找不到证据文档：${file}`);
  process.exit(1);
}

const lines = readFileSync(path, "utf8").split("\n");
const problems = [];

lines.forEach((line, i) => {
  const n = i + 1;
  if (line.includes("⬜")) problems.push(`${n}: 未填写的格子 → ${line.trim().slice(0, 80)}`);
  for (const m of line.matchAll(/(\.\.\/)?(qa\/)?screenshots\/([A-Za-z0-9._-]+\.(?:png|jpg))/g)) {
    const rel = m[1] ? m[2] ? `qa/screenshots/${m[3]}` : `screenshots/${m[3]}` : `qa/screenshots/${m[3]}`;
    const candidates = [
      join(ROOT, "qa/screenshots", m[3]),
      join(dirname(path), "..", "qa", "screenshots", m[3]),
      join(dirname(path), "screenshots", m[3]),
    ];
    if (!candidates.some((p) => existsSync(p))) {
      problems.push(`${n}: 引用了不存在的截图 ${rel}`);
    }
  }
});

if (problems.length) {
  console.error(`✗ [evidence] ${problems.length} 个问题：`);
  for (const p of problems) console.error("  " + p);
  process.exit(1);
}
console.log("[evidence] OK — 没有未填写的格子，引用的截图都存在。");

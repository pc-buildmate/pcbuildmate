#!/usr/bin/env node
/**
 * 发布前的证据完整性门：docs/21 里不能留空格子，引用的截图必须存在。
 *
 * 用法：node qa/check-evidence.mjs [--file <path>] [--self-test]
 * 退出码：0 = 完整；1 = 有未填项或缺图。
 *
 * 背景：docs/21 的表格需要人填；发布会前，"还有一格没填"必须能被机器查出来。
 * 故意不接进 PR 流水线：工作过程中的占位符是合理的，这个门检查"发布前状态"。
 */
import { readFileSync, existsSync } from "node:fs";
import { join, dirname, isAbsolute } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const args = process.argv.slice(2);
const fileArgIdx = args.indexOf("--file");
const file = fileArgIdx > -1 ? args[fileArgIdx + 1] : "docs/21-device-evidence.md";
const path = isAbsolute(file) ? file : join(ROOT, file);

/** 纯函数：给定文档文本与判断文件是否存在的方法，返回问题列表。 */
export function check(text, baseDir, exists) {
  const problems = [];
  text.split("\n").forEach((line, i) => {
    const n = i + 1;
    if (line.includes("⬜")) problems.push(`${n}: 未填写的格子 → ${line.trim().slice(0, 80)}`);
    for (const m of line.matchAll(/(?:\.\.\/)?(?:qa\/)?screenshots\/([A-Za-z0-9._-]+\.(?:png|jpg))/g)) {
      const name = m[1];
      const candidates = [
        join(ROOT, "qa/screenshots", name),
        join(baseDir, "..", "qa", "screenshots", name),
        join(baseDir, "screenshots", name),
      ];
      if (!candidates.some(exists)) problems.push(`${n}: 引用了不存在的截图 ${name}`);
    }
  });
  return problems;
}

function selfTest() {
  const exists = (p) => p.endsWith("run1-02-diagnostics.jpg");
  const good = "# t\n| a | b |\n|---|---|\n| ok | ![](screenshots/run1-02-diagnostics.jpg) |\n";
  const bad = "# t\n| a | b |\n|---|---|\n| ⬜ | x |\n\n![](screenshots/missing.jpg)\n";
  const p1 = check(good, join(ROOT, "docs"), exists);
  const p2 = check(bad, join(ROOT, "docs"), exists);
  const ok =
    p1.length === 0 &&
    p2.some((x) => x.includes("未填写")) &&
    p2.some((x) => x.includes("不存在的截图"));
  if (!ok) {
    console.error("✗ self-test failed", { good: p1, bad: p2 });
    process.exit(1);
  }
  console.log("[evidence] 自测通过：正常文档通过，含 ⬜ 与缺图的文档被拦下。");
}

if (args.includes("--self-test")) {
  selfTest();
} else if (!existsSync(path)) {
  console.error(`✗ 找不到证据文档：${file}`);
  process.exit(1);
} else {
  const problems = check(readFileSync(path, "utf8"), dirname(path), existsSync);
  if (problems.length) {
    console.error(`✗ [evidence] ${problems.length} 个问题：`);
    for (const p of problems) console.error("  " + p);
    process.exit(1);
  }
  console.log("[evidence] OK — 没有未填写的格子，引用的截图都存在。");
}

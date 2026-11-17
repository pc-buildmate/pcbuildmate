#!/usr/bin/env node
/**
 * 变异表静态自检：不跑 Gradle，几秒钟确认 12 条变异仍然"打得进去"。
 *
 * 为什么需要：`tools/run-mutations.mjs` 要跑 12 次 Gradle（约 5 分钟）才能发现
 * "patch not found"——而这类问题几乎总是改了源码却忘了同步变异表（或反过来）。
 * 这里只做三件不需要编译的事：文件在不在、find 串还能不能在（按 LF 规范化后）找到、
 * replace 是否确实不同。真正的"变异会不会被杀"仍然只有跑 Gradle 才知道。
 *
 * 用法：node ci/check-mutations.mjs [--self-test]
 */
import { readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";
import { mutations } from "../tools/run-mutations.mjs";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

/** 纯函数：返回问题列表，便于自测。 */
export function validate(list, readFile) {
  const problems = [];
  const ids = new Set();

  for (const m of list) {
    if (ids.has(m.id)) problems.push(`${m.id}: id 重复`);
    ids.add(m.id);

    if (!m.find) problems.push(`${m.id}: find 为空`);
    if (!m.replace) problems.push(`${m.id}: replace 为空`);
    if (m.find === m.replace) problems.push(`${m.id}: find 与 replace 相同，不会产生变异`);
    if (!m.test || !m.test.startsWith(":")) problems.push(`${m.id}: test 任务名异常：${m.test}`);

    const full = join(ROOT, m.file);
    let text;
    try {
      text = readFile(full);
    } catch {
      problems.push(`${m.id}: 源文件不存在 ${m.file}`);
      continue;
    }
    // 与 run-mutations 一致：Windows 检出的 CRLF 不影响匹配。
    const normalized = text.replace(/\r\n/g, "\n");
    if (!normalized.includes(m.find)) {
      problems.push(`${m.id}: 在 ${m.file} 里找不到 find 串（源码改过？）`);
    }
  }

  for (let i = 1; i <= 12; i++) {
    const id = "MUT-" + String(i).padStart(2, "0");
    if (!ids.has(id)) problems.push(`缺变异 ${id}`);
  }
  return problems;
}

function selfTest() {
  const fake = [
    { id: "MUT-01", file: "nope.kt", find: "a", replace: "b", test: ":x:test" },
    { id: "MUT-02", file: "README.md", find: "definitely-not-present", replace: "b", test: ":x:test" },
    { id: "MUT-03", file: "README.md", find: "a", replace: "a", test: ":x:test" },
  ];
  const problems = validate(fake, (p) => (p.endsWith("README.md") ? "file contents" : readFileSync(p, "utf8")));
  const expectations = ["源文件不存在", "找不到 find 串", "find 与 replace 相同", "缺变异 MUT-04"];
  const missing = expectations.filter((e) => !problems.some((p) => p.includes(e)));
  if (missing.length) {
    console.error("✗ 自测未命中：" + missing.join(" / "));
    process.exit(1);
  }
  console.log(`[mutations-check] 自测通过（命中 ${expectations.length} 类问题，共 ${problems.length} 条）`);
}

if (process.argv.includes("--self-test")) {
  selfTest();
} else {
  const problems = validate(mutations, (p) => readFileSync(p, "utf8"));
  if (problems.length) {
    console.error(`✗ [mutations-check] ${problems.length} 个问题：`);
    for (const p of problems) console.error("  " + p);
    console.error("  修法：同步 tools/run-mutations.mjs 的补丁串与源码。");
    process.exit(1);
  }
  console.log(`[mutations-check] OK — ${mutations.length} 条变异的文件、find/replace 与任务名都可解析。`);
}

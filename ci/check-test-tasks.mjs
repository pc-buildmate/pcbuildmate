#!/usr/bin/env node
/**
 * CI 测试任务覆盖检查（防止"有测试但流水线不跑"）。
 *
 * 背景：#52 之前，`jvm` job 的 Gradle 任务是手写的，`:ui` 和 `:app` 的
 * 单元测试从来不在里面——`Scene3dTest`、`RecommendationIntegrationTest`
 * 一直没在 CI 上执行过，而 CI 依旧全绿。手写列表会随模块增减而漂移，
 * 所以这里把"模块有 src/test 就必须在 CI 任务列表里"变成可执行的检查。
 *
 * 用法：node ci/check-test-tasks.mjs [--ci <workflow.yml>] [--verbose]
 * 默认读取 .github/workflows/ci.yml。
 */
import { readFileSync, readdirSync, existsSync, statSync } from "node:fs";
import { join, dirname, isAbsolute } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const ciArgIndex = process.argv.indexOf("--ci");
const ciArg = ciArgIndex > -1 ? process.argv[ciArgIndex + 1] : ".github/workflows/ci.yml";
const CI_PATH = isAbsolute(ciArg) ? ciArg : join(ROOT, ciArg);
const VERBOSE = process.argv.includes("--verbose");

const settings = readFileSync(join(ROOT, "settings.gradle.kts"), "utf8");
const modules = [...settings.matchAll(/"(:[A-Za-z0-9:_-]+)"/g)].map((m) => m[1]);

/** 模块目录：`:domain:rules` -> `domain/rules` */
const dirOf = (mod) => mod.slice(1).replace(/:/g, "/");

/** 是否有测试源码（存在 src/test 且有 .kt/.java 文件） */
function hasTests(mod) {
  const dir = join(ROOT, dirOf(mod), "src", "test");
  if (!existsSync(dir) || !statSync(dir).isDirectory()) return false;
  const walk = (d) =>
    readdirSync(d, { withFileTypes: true }).some((e) =>
      e.isDirectory() ? walk(join(d, e.name)) : /\.(kt|java)$/.test(e.name),
    );
  return walk(dir);
}

/** Android 模块用 testDebugUnitTest，纯 JVM 模块用 test */
function expectedTask(mod) {
  const gradlePath = join(ROOT, dirOf(mod), "build.gradle.kts");
  if (!existsSync(gradlePath)) return null;
  const text = readFileSync(gradlePath, "utf8");
  if (/android\.(library|application)/.test(text)) return "testDebugUnitTest";
  return "test";
}

const ciText = readFileSync(CI_PATH, "utf8");
const ciTasks = new Set(
  [...ciText.matchAll(/(:[A-Za-z0-9:_-]+:(?:test|testDebugUnitTest))\b/g)].map((m) => m[1]),
);

const errors = [];
let checked = 0;

for (const mod of modules) {
  const task = expectedTask(mod);
  if (!task) continue;
  const tests = hasTests(mod);
  const wanted = `${mod}:${task}`;
  const listed = ciTasks.has(wanted);

  if (tests) {
    checked++;
    if (!listed) errors.push(`缺：${wanted}（${dirOf(mod)}/src/test 有测试，但 CI 不执行）`);
    else if (VERBOSE) console.log(`[test-tasks] ok ${wanted}`);
  } else if (listed) {
    errors.push(`多：${wanted}（模块没有测试源码，任务会被跳过，说明列表已漂移）`);
  } else if (VERBOSE) {
    console.log(`[test-tasks] 跳过 ${mod}（无测试源码）`);
  }
}

for (const task of ciTasks) {
  const mod = task.slice(0, task.lastIndexOf(":"));
  if (!modules.includes(mod)) errors.push(`未知模块：${task}（settings.gradle.kts 里没有 ${mod}）`);
}

// ---------- instrumented 测试也要有人跑 ----------
// `:module:test` 守的是 JVM 单测；`src/androidTest` 的契约（Room 等）只有
// `connected*AndroidTest` 才会执行，漏掉它 CI 依旧全绿。
const instrumented = modules.filter((mod) => {
  const dir = join(ROOT, dirOf(mod), "src", "androidTest");
  return existsSync(dir) && statSync(dir).isDirectory();
});

if (instrumented.length) {
  if (!/:\w[\w:-]*:connectedDebugAndroidTest/.test(ciText)) {
    errors.push(
      `缺：*:connectedDebugAndroidTest（${instrumented.join(", ")} 有 src/androidTest，但 CI 不跑 instrumented 测试）`,
    );
  }
  if (!/^\s{2}room:/m.test(ciText)) {
    errors.push("缺：CI 里没有 `room` job（instrumented 测试需要模拟器，单独成 job）");
  }
  if (VERBOSE) console.log(`[test-tasks] instrumented 模块：${instrumented.join(", ")}`);
}

if (errors.length) {
  console.error(`✗ [test-tasks] ${errors.length} 个问题：`);
  for (const e of errors) console.error("  " + e);
  console.error("  修法：把测试任务补进 .github/workflows/ci.yml 的 jvm job，或删掉已无测试的任务。");
  process.exit(1);
}

console.log(`[test-tasks] OK — ${checked} 个含测试的模块全部在 CI 任务列表里。`);

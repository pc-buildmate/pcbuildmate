#!/usr/bin/env node
/**
 * 审计报告与实测一致：docs/20 的头行数字必须等于 `tools/audit-catalogue.mjs --no-write` 的实际输出。
 *
 * 由来：ADR-0011 与价格核对之后，docs/20 仍写着 158 个字段；报告是生成文件，
 * 但没人跑脚本就会一直过期，而答辩会引用它的数字。这个检查把它变成几秒钟的事。
 *
 * 用法：node qa/check-audit-report.mjs [--self-test]
 * 退出码：0 = 一致；1 = 不一致或读不到。
 */
import { spawnSync } from "node:child_process";
import { readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const REPORT = join(ROOT, "docs/20-catalogue-audit.md");

/** 从审计 stdout 提取关键数字。 */
export function parseAuditOutput(text, exitCode) {
  const head = text.match(/(\d+) 条记录 \/ (\d+) 个字段/);
  const anomalies = text.match(/异常 (\d+) 条（待修 (\d+)）/);
  const urls = text.match(/记录级有出处 (\d+)\/(\d+)（本轮带 URL (\d+)）/);
  if (!head || !anomalies) return null;
  return {
    records: +head[1],
    fields: +head[2],
    anomalies: +anomalies[1],
    pending: +anomalies[2],
    sources: urls ? +urls[1] : null,
    total: urls ? +urls[2] : null,
    urls: urls ? +urls[3] : null,
    exitCode,
  };
}

/** 从 docs/20 正文提取同一组数字。 */
export function parseReport(text) {
  const head = text.match(/(\d+) 条记录、(\d+) 个字段.*?异常 (\d+) 条（待修 (\d+) 条）；退出码 (\d+)/s);
  const fieldsRow = text.match(/\| 字段总数 \| (\d+) \|/);
  const urlRow = text.match(/其中本轮采集带 URL \| (\d+) \/ (\d+)/);
  if (!head) return null;
  return {
    records: +head[1],
    fields: +head[2],
    anomalies: +head[3],
    pending: +head[4],
    exitCode: +head[5],
    fieldsRow: fieldsRow ? +fieldsRow[1] : null,
    urls: urlRow ? +urlRow[1] : null,
    total: urlRow ? +urlRow[2] : null,
  };
}

export function compare(actual, reported) {
  const problems = [];
  if (!actual) return ["无法解析审计脚本输出"];
  if (!reported) return ["无法解析 docs/20 的头行"];
  const keys = [
    ["records", "记录数"],
    ["fields", "字段数"],
    ["anomalies", "异常数"],
    ["pending", "待修数"],
    ["exitCode", "退出码"],
  ];
  for (const [k, label] of keys) {
    if (actual[k] !== reported[k]) problems.push(`${label}：实测 ${actual[k]}，报告 ${reported[k]}`);
  }
  if (reported.fieldsRow !== null && reported.fieldsRow !== actual.fields) {
    problems.push(`§20.5 字段总数：实测 ${actual.fields}，报告 ${reported.fieldsRow}`);
  }
  if (actual.urls !== null && reported.urls !== null && reported.urls !== actual.urls) {
    problems.push(`带 URL 的记录数：实测 ${actual.urls}，报告 ${reported.urls}`);
  }
  return problems;
}

function selfTest() {
  const actual = { records: 30, fields: 168, anomalies: 4, pending: 2, sources: 30, total: 30, urls: 27, exitCode: 1 };
  const good = parseReport(
    "> 运行结果：**30 条记录、168 个字段逐项检查完毕；异常 4 条（待修 2 条）；退出码 1**\n\n| 字段总数 | 168 |\n\n| 　其中本轮采集带 URL | 27 / 30 |",
  );
  const bad = parseReport(
    "> 运行结果：**30 条记录、158 个字段逐项检查完毕；异常 2 条（待修 0 条）；退出码 0**\n\n| 字段总数 | 158 |\n\n| 　其中本轮采集带 URL | 14 / 30 |",
  );
  const p1 = compare(actual, good);
  const p2 = compare(actual, bad);
  const ok = p1.length === 0 && p2.some((x) => x.includes("字段数")) && p2.some((x) => x.includes("退出码"));
  if (!ok) {
    console.error("✗ self-test failed", { p1, p2 });
    process.exit(1);
  }
  console.log("[audit-report] 自测通过：一致时不报，字段数/退出码不一致会点名。");
}

if (process.argv.includes("--self-test")) {
  selfTest();
} else if (!existsSync(REPORT)) {
  console.error(`✗ 找不到报告：${REPORT}`);
  process.exit(1);
} else {
  const run = spawnSync("node", [join(ROOT, "tools/audit-catalogue.mjs"), "--no-write"], { encoding: "utf8" });
  const actual = parseAuditOutput(`${run.stdout || ""}\n${run.stderr || ""}`, run.status ?? 1);
  const reported = parseReport(readFileSync(REPORT, "utf8"));
  const problems = compare(actual, reported);
  if (problems.length) {
    console.error(`✗ [audit-report] ${problems.length} 处不一致：`);
    for (const p of problems) console.error("  " + p);
    console.error("  修法：node tools/audit-catalogue.mjs（重新生成 docs/20），不要手改。");
    process.exit(1);
  }
  console.log(`[audit-report] OK — ${actual.records} 条 / ${actual.fields} 字段 / 异常 ${actual.anomalies}（待修 ${actual.pending}）/ 退出码 ${actual.exitCode} 与报告一致。`);
}

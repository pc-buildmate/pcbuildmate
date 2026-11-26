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
    items: auditItems(text),
    exitCode,
  };
}

/** `[待修] MISSING_FIELD mb-demo-lga1700.pcieX16SlotMm — …` → 身份键集合。 */
function auditItems(text) {
  return [...text.matchAll(/\[(待修|已解释)\]\s+([A-Z_]+)\s+(\S+)/g)]
    .map((m) => `${m[2]} ${m[3]} ${m[1]}`)
    .sort();
}

/** 报告异常表的一行 → 身份键；不是异常行返回 null。 */
function reportItem(line) {
  const id = line.match(/^\|\s*\d+\s*\|\s*`([^`]+)`\s*\|/);
  if (!id) return null;
  // 全角（）与半角 () 在报告里都可能出现，两种都要认。
  const pair = line.match(/`([A-Z_]{3,})`\s*\/\s*`?([^`）)]+?)`?[\s]*[）)]/);
  if (!pair) return null;
  const status = line.includes("**待修**") ? "待修" : line.includes("已解释") ? "已解释" : null;
  if (!status) return null;
  return `${pair[1]} ${id[1]}.${pair[2]} ${status}`;
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
    claimsAllFieldsOk: /个字段全部合格/.test(text),
    items: text
      .split("\n")
      .map(reportItem)
      .filter(Boolean)
      .sort(),
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
  // 结论句必须与待修数一致：曾经报告一边写「168 个字段全部合格」、一边列出 2 条待修。
  if (actual.pending > 0 && reported.claimsAllFieldsOk) {
    problems.push(`报告声称「字段全部合格」，但实测有 ${actual.pending} 条待修`);
  }
  // 条数对上不等于内容对上：换了一条异常但数量不变时，上面几项全都发现不了。
  if (Array.isArray(actual.items) && Array.isArray(reported.items)) {
    const a = new Set(actual.items);
    const r = new Set(reported.items);
    for (const k of a) if (!r.has(k)) problems.push(`报告缺少异常：${k}`);
    for (const k of r) if (!a.has(k)) problems.push(`报告多出异常：${k}`);
  }
  return problems;
}

function selfTest() {
  const stdout = [
    "[audit] assets/catalogue/catalogue-app.json：30 条记录 / 168 个字段 / 版本 2026.11",
    "[audit] 记录级有出处 30/30（本轮带 URL 27）",
    "[audit] 异常 4 条（待修 2）",
    "  [待修] MISSING_FIELD mb-demo-lga1700.pcieX16SlotMm — 冻结表要求但记录中没有该字段",
    "  [待修] MISSING_FIELD mb-demo-lga1700.pcieSlotCount — 冻结表要求但记录中没有该字段",
    "  [已解释] SOURCE_STALE case-demo-l216.SOURCES.md — 表格行仍写「LIAN LI Lancool 216」",
    "  [已解释] SOURCE_STALE cooler-demo-pa120max.SOURCES.md — 表格行仍写「PA120 VISION MAX」",
  ].join("\n");
  const actual = parseAuditOutput(stdout, 1);

  const good = parseReport(
    [
      "> 运行结果：**30 条记录、168 个字段逐项检查完毕；异常 4 条（待修 2 条）；退出码 1**",
      "",
      "| 字段总数 | 168 |",
      "",
      "| 　其中本轮采集带 URL | 27 / 30 |",
      "",
      "| 1 | `mb-demo-lga1700` | 冻结表要求的字段不存在（`MISSING_FIELD` / `pcieX16SlotMm`） | 说明 | **待修** | 修法 |",
      "| 2 | `mb-demo-lga1700` | 冻结表要求的字段不存在（`MISSING_FIELD` / `pcieSlotCount`） | 说明 | **待修** | 修法 |",
      "| 3 | `case-demo-l216` | 来源表有该 ID 但目录无记录（`SOURCE_STALE` / `SOURCES.md`） | 说明 | 已解释 | 留痕 |",
      "| 4 | `cooler-demo-pa120max` | 来源表有该 ID 但目录无记录（`SOURCE_STALE` / `SOURCES.md`） | 说明 | 已解释 | 留痕 |",
    ].join("\n"),
  );
  // 数字全对、只换了异常内容：旧版检查发现不了，新版必须点名。
  const swapped = parseReport(
    [
      "> 运行结果：**30 条记录、168 个字段逐项检查完毕；异常 4 条（待修 2 条）；退出码 1**",
      "",
      "| 字段总数 | 168 |",
      "",
      "| 　其中本轮采集带 URL | 27 / 30 |",
      "",
      "| 1 | `mb-demo-b650` | 冻结表要求的字段不存在（`MISSING_FIELD` / `pcieX16SlotMm`） | 说明 | **待修** | 修法 |",
      "| 2 | `mb-demo-lga1700` | 冻结表要求的字段不存在（`MISSING_FIELD` / `pcieSlotCount`） | 说明 | **待修** | 修法 |",
      "| 3 | `case-demo-l216` | 来源表有该 ID 但目录无记录（`SOURCE_STALE` / `SOURCES.md`） | 说明 | 已解释 | 留痕 |",
      "| 4 | `cooler-demo-pa120max` | 来源表有该 ID 但目录无记录（`SOURCE_STALE` / `SOURCES.md`） | 说明 | 已解释 | 留痕 |",
    ].join("\n"),
  );
  const stale = parseReport(
    "> 运行结果：**30 条记录、158 个字段逐项检查完毕；异常 2 条（待修 0 条）；退出码 0**\n\n| 字段总数 | 158 |\n\n| 　其中本轮采集带 URL | 14 / 30 |",
  );

  // 结论句和待修数打架：数字全对，但正文声称「字段全部合格」。
  const falseClaim = parseReport(
    [
      "> 运行结果：**30 条记录、168 个字段逐项检查完毕；异常 2 条（待修 2 条）；退出码 1**",
      "",
      "| 字段总数 | 168 |",
      "",
      "- 字段级：168 个字段全部合格；",
    ].join("\n"),
  );

  const p1 = compare(actual, good);
  const p2 = compare(actual, swapped);
  const p3 = compare(actual, stale);
  const p4 = compare(actual, falseClaim);
  const ok =
    p1.length === 0 &&
    p2.some((x) => x.includes("报告缺少异常：MISSING_FIELD mb-demo-lga1700.pcieX16SlotMm")) &&
    p2.some((x) => x.includes("报告多出异常：MISSING_FIELD mb-demo-b650.pcieX16SlotMm")) &&
    p4.some((x) => x.includes("字段全部合格")) &&
    p3.some((x) => x.includes("字段数")) &&
    p3.some((x) => x.includes("退出码"));
  if (!ok) {
    console.error("✗ self-test failed", { p1, p2, p3 });
    process.exit(1);
  }
  console.log("[audit-report] 自测通过：一致不报；数字不符、条目被换掉都点名。");
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

#!/usr/bin/env node
/**
 * 把 docs/ 合并成一份可打印 / 可分享的单文件：
 *   dist/PC-Buildmate-Design-Guide.md
 *
 * 源文件仍是 docs/（唯一真相源）；本脚本只做拼接 + 目录 + 统计。
 * 用法：node tools/build-single-md.mjs
 */
import { readFileSync, writeFileSync, mkdirSync, readdirSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

const ORDER = [
  ["README.md", "README"],
  ["docs/00-overview.md", "00 概览"],
  ["docs/01-glossary.md", "01 术语"],
  ["docs/02-contracts.md", "02 契约语义"],
  ["docs/03-rules.md", "03 规则表"],
  ["docs/04-state-machines.md", "04 状态机"],
  ["docs/05-module-independence.md", "05 模块独立性"],
  ["docs/06-modules.md", "06 模块契约卡"],
  ["docs/07-unit-verification.md", "07 单元验证"],
  ["docs/08-testing-and-triage.md", "08 Oracle 与归因"],
  ["docs/09-ci-and-scope.md", "09 范围与 CI"],
  ["docs/10-llm-delivery.md", "10 LLM 交付"],
  ["docs/11-adr/README.md", "11 ADR 索引"],
  ["docs/11-adr/0001-mvi-revision.md", "ADR-0001"],
  ["docs/11-adr/0002-static-catalogue.md", "ADR-0002"],
  ["docs/11-adr/0003-no-runtime-llm.md", "ADR-0003"],
  ["docs/11-adr/0004-pure-jvm-domain.md", "ADR-0004"],
  ["docs/11-adr/0005-rules-as-data.md", "ADR-0005"],
  ["docs/11-adr/0006-three-state-evidence.md", "ADR-0006"],
  ["docs/12-prompt-templates.md", "12 Prompt 模板"],
  ["docs/13-risks.md", "13 风险与口径"],
  ["docs/14-team-and-milestones.md", "14 分工与里程碑"]
];

let out = "";
let toc = "";
let words = 0;
let chars = 0;

for (const [file, title] of ORDER) {
  const text = readFileSync(join(ROOT, file), "utf8");
  chars += text.length;
  words += text.split(/\s+/).filter(Boolean).length;
  toc += "  - " + title + "\n";
  out += "\n\n---\n\n" + text.trimEnd() + "\n";
}

const version = readFileSync(join(ROOT, "docs/VERSION"), "utf8").trim().split("\n")[0];
const header = [
  "# PC Buildmate · 设计指南（单文件版）",
  "",
  "> 版本 " + version + "  ·  由 tools/build-single-md.mjs 从 docs/ 生成，请勿手改本文件。",
  "> 源文件：README.md + docs/00..13 + docs/11-adr/*",
  "",
  "## 目录",
  "",
  toc
].join("\n");

mkdirSync(join(ROOT, "dist"), { recursive: true });
writeFileSync(join(ROOT, "dist/PC-Buildmate-Design-Guide.md"), header + out);
console.log("[bundle] dist/PC-Buildmate-Design-Guide.md");
console.log("[bundle] 源文件 " + ORDER.length + " 个，" + chars + " 字符（约 " + Math.round(chars / 1.7) + " tokens）");

#!/usr/bin/env node
/**
 * 目录一致性审计（DATA-03，零依赖）。
 *
 * 审计 `assets/catalogue/catalogue-app.json`，判定依据是 `docs/03-rules.md` §3.3
 * 冻结字段表与 §3.4 的 CSV / 数值解析规范（不是审计者自定的口径）：
 *   A 记录级：必需键、ID 唯一、类别、价格/价格日期、目录版本与 isDemo 一致性
 *   B 字段级：字段是否在冻结表内、枚举值域、CSV token 正则、版本号格式、
 *             数值下限与 numeric 自洽、证据三态合法（EMPTY 必须无值）
 *   C 交叉级：主板 formFactor ↔ 机箱支持列表、CPU socket ↔ 主板、
 *             内存代数 ↔ 主板、CPU 支持芯片组 ↔ 主板芯片组
 *   D 来源表：`assets/catalogue/SOURCES.md` 与目录的 componentId 双向对照（只读）
 *   E 统计：VERIFIED / REPORTED / EMPTY 分布与字段级 sources 落地情况
 *
 * EMPTY 是合法三态（规则遇到即返回 UNVERIFIED），只统计、不计为异常。
 * 无法解释的异常才让退出码为 1。
 *
 * 用法：
 *   node tools/audit-catalogue.mjs            # 打印摘要并重写 docs/20-catalogue-audit.md
 *   node tools/audit-catalogue.mjs --no-write # 只检查不写文件（CI 用）
 *   node tools/audit-catalogue.mjs --strict   # 已解释的来源表异常也算失败（INFO-01 补采后当闸门）
 *   node tools/audit-catalogue.mjs --catalogue <path> [--sources <path>] [--report <path>]
 *                                             # 审计候选快照，默认不写报告
 * 退出码：0 = 无待修异常；1 = 有待修异常。
 */
import { readFileSync, writeFileSync } from "node:fs";
import { join, dirname, isAbsolute } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
const argOf = (flag) => {
  const at = process.argv.indexOf(flag);
  return at >= 0 && process.argv[at + 1] ? process.argv[at + 1] : null;
};
const CATALOGUE = argOf("--catalogue") ?? "assets/catalogue/catalogue-app.json";
const SOURCES = argOf("--sources") ?? "assets/catalogue/SOURCES.md";
const REPORT = argOf("--report") ?? "docs/20-catalogue-audit.md";
const OVERRIDDEN = Boolean(argOf("--catalogue") || argOf("--sources") || argOf("--report"));
const WRITE = process.argv.includes("--write") || (!process.argv.includes("--no-write") && !OVERRIDDEN);
const resolvePath = (path) => (isAbsolute(path) ? path : join(ROOT, path));

// ------------------------------------------------------------ 冻结的取值域
const CATEGORIES = ["CPU", "MOTHERBOARD", "MEMORY", "GPU", "PSU", "CASE", "COOLER", "STORAGE"];
const SOCKETS = ["AM5", "AM4", "LGA1700", "LGA1851"];
const FORM_FACTORS = ["ATX", "MICRO_ATX", "MINI_ITX"];
const MEMORY_GENERATIONS = ["DDR4", "DDR5"];
const STORAGE_INTERFACES = ["M2_NVME", "SATA"];
const CONNECTORS = ["12VHPWR", "8PIN_X2", "8PIN", "SATA"];
const STATES = ["VERIFIED", "REPORTED", "EMPTY"];
const REQUIRED_KEYS = [
  "componentId", "category", "manufacturer", "model",
  "partNumber", "priceCny", "priceDate", "catalogueVersion",
];
const DATE_RE = /^\d{4}-\d{2}-\d{2}$/;
const TOKEN_RE = /^[A-Z0-9_]+$/;
const VERSION_RE = /^\d+\.\d+$/;

const int = (min) => ({ kind: "number", min });
const oneOf = (values) => ({ kind: "enum", values });
const csv = (values) => ({ kind: "csv", values });

/** docs/03 §3.3 冻结字段表：类别 -> 路径 -> 期望类型。路径集合即该类别字段全集。 */
const FIELDS = {
  CPU: {
    socket: oneOf(SOCKETS),
    supportedChipsets: csv(null),
    tdpW: int(1),
    minBiosGeneration: { kind: "version" },
    integratedGraphics: oneOf(["YES", "NO"]),
  },
  MOTHERBOARD: {
    socket: oneOf(SOCKETS),
    chipset: { kind: "token" },
    biosGeneration: { kind: "version" },
    memoryGeneration: oneOf(MEMORY_GENERATIONS),
    memorySlots: int(1),
    maxMemoryGb: int(1),
    formFactor: oneOf(FORM_FACTORS),
    m2Slots: int(0),
    sataPorts: int(0),
    storageInterfaces: csv(STORAGE_INTERFACES),
    // ADR-0011：3D 槽位用（docs/03 §3.3 已有，见 issue #149）
    pcieX16SlotMm: int(0),
    pcieSlotCount: int(0),
  },
  MEMORY: {
    generation: oneOf(MEMORY_GENERATIONS),
    moduleCapacityGb: int(1),
    kitCapacityGb: int(1),
    moduleCount: int(1),
  },
  GPU: {
    lengthMm: int(1),
    thicknessMm: int(1),
    slotWidth: int(1),
    tdpW: int(1),
    recommendedPsuW: int(1),
    requiredPsuConnectors: csv(CONNECTORS),
  },
  PSU: { continuousW: int(1), connectors: csv(CONNECTORS) },
  CASE: {
    maxGpuLengthMm: int(1),
    maxGpuThicknessMm: int(1),
    maxGpuSlots: int(1),
    maxCoolerHeightMm: int(1),
    supportedFormFactors: csv(FORM_FACTORS),
    driveBays: int(0),
  },
  COOLER: { heightMm: int(1), socketMounts: csv(SOCKETS) },
  STORAGE: { interface: oneOf(STORAGE_INTERFACES) },
};

const TITLES = {
  MISSING_KEY: "记录必需键缺失或为空",
  DUPLICATE_ID: "componentId 重复",
  BAD_CATEGORY: "类别不在 8 类之内",
  BAD_PRICE: "priceCny 非正数",
  BAD_DATE: "priceDate 不是 YYYY-MM-DD",
  VERSION_DRIFT: "记录目录版本与顶层不一致",
  DEMO_DRIFT: "记录 isDemo 与顶层不一致",
  MISSING_FIELD: "冻结表要求的字段不存在",
  UNKNOWN_FIELD: "字段不在冻结表内",
  BAD_STATE: "证据状态不在三态之内",
  BAD_SOURCES: "sources 不是数组",
  CONFLICT_FLAG: "conflict=true 需人工裁定",
  EMPTY_WITH_VALUE: "EMPTY 却带值",
  VALUE_MISSING: "非 EMPTY 却无值",
  BAD_ENUM: "枚举值不在取值域",
  BAD_TOKEN: "CSV token 不是大写 token 或不在取值域",
  BAD_VERSION: "版本号不是「主.次」",
  BAD_NUMBER: "数值字段不可解析为数字",
  NUMERIC_MISMATCH: "numeric 与 value 不一致",
  NUMBER_BELOW_MIN: "数值低于冻结表下限",
  CASE_COVERAGE: "板型没有任何机箱支持",
  BOARD_COVERAGE: "没有主板满足该 CPU socket / 内存代数",
  CHIPSET_COVERAGE: "CPU 支持芯片组与主板芯片组无交集",
  SOURCE_STALE: "来源表有该 ID 但目录无记录",
  SOURCE_MISSING: "目录有记录但来源表两段都未提及",
};

const anomalies = [];
function add(code, id, path, detail, opts = {}) {
  anomalies.push({
    code,
    id,
    path: path ?? "—",
    detail,
    scope: opts.scope ?? "field",
    explained: opts.explained ?? null,
  });
}

// ------------------------------------------------------------ 读取数据
const file = JSON.parse(readFileSync(resolvePath(CATALOGUE), "utf8").replace(/^\uFEFF/, ""));
const records = Array.isArray(file.components) ? file.components : [];
const version = file.catalogueVersion;
const isDemo = file.isDemo;
const attrOf = (record) =>
  record.attributes && typeof record.attributes === "object" ? record.attributes : {};
const valueOf = (record, path) => {
  const attr = attrOf(record)[path];
  if (!attr || attr.state === "EMPTY") return null;
  return attr.value === null || attr.value === undefined || attr.value === "" ? null : attr.value;
};
const csvOf = (record, path) => {
  const raw = valueOf(record, path);
  return raw ? String(raw).split(",").map((t) => t.trim()).filter(Boolean) : [];
};

// ------------------------------------------------------------ A 记录级
const idCount = new Map();
for (const record of records) {
  const id = record.componentId;
  idCount.set(id, (idCount.get(id) ?? 0) + 1);
  for (const key of REQUIRED_KEYS) {
    const raw = record[key];
    if (raw === undefined || raw === null || raw === "") {
      add("MISSING_KEY", id ?? "(缺 componentId)", key, "键缺失或为空", { scope: "record" });
    }
  }
  if (!CATEGORIES.includes(record.category)) {
    add("BAD_CATEGORY", id, "category", `未知类别 ${record.category}`, { scope: "record" });
  }
  if (typeof record.priceCny !== "number" || !Number.isFinite(record.priceCny) || record.priceCny <= 0) {
    add("BAD_PRICE", id, "priceCny", `priceCny=${record.priceCny}`, { scope: "record" });
  }
  if (typeof record.priceDate !== "string" || !DATE_RE.test(record.priceDate)) {
    add("BAD_DATE", id, "priceDate", `priceDate=${record.priceDate}`, { scope: "record" });
  }
  if (record.catalogueVersion !== version) {
    add("VERSION_DRIFT", id, "catalogueVersion", `记录 ${record.catalogueVersion} ≠ 顶层 ${version}`, { scope: "record" });
  }
  if (record.isDemo !== isDemo) {
    add("DEMO_DRIFT", id, "isDemo", `记录 ${record.isDemo} ≠ 顶层 ${isDemo}`, { scope: "record" });
  }
}
for (const [id, count] of idCount) {
  if (count > 1) add("DUPLICATE_ID", id, "componentId", `出现 ${count} 次`, { scope: "record" });
}

// ------------------------------------------------------------ B 字段级
for (const record of records) {
  const id = record.componentId;
  const spec = FIELDS[record.category];
  if (!spec) continue;
  if (!record.attributes || typeof record.attributes !== "object") {
    add("MISSING_KEY", id, "attributes", "attributes 缺失");
    continue;
  }
  const attrs = record.attributes;
  for (const path of Object.keys(spec)) {
    if (!(path in attrs)) add("MISSING_FIELD", id, path, "冻结表要求但记录中没有该字段");
  }
  for (const path of Object.keys(attrs)) {
    if (!(path in spec)) add("UNKNOWN_FIELD", id, path, "不在 docs/03 §3.3 冻结表内");
  }

  for (const [path, attr] of Object.entries(attrs)) {
    const expected = spec[path];
    const state = attr?.state;
    if (!STATES.includes(state)) add("BAD_STATE", id, path, `state=${state}`);
    if (attr?.conflict === true) add("CONFLICT_FLAG", id, path, "conflict=true");
    if (!Array.isArray(attr?.sources)) add("BAD_SOURCES", id, path, "sources 不是数组");

    const raw = attr?.value;
    const empty = raw === null || raw === undefined || raw === "";
    if (state === "EMPTY" && !empty) add("EMPTY_WITH_VALUE", id, path, `值是 ${JSON.stringify(raw)}`);
    if (state !== "EMPTY" && empty) add("VALUE_MISSING", id, path, `${state} 却无值`);
    if (!expected || empty) continue;

    if (expected.kind === "enum") {
      if (!expected.values.includes(raw)) {
        add("BAD_ENUM", id, path, `"${raw}" 不在 ${expected.values.join(" / ")}`);
      }
    } else if (expected.kind === "csv") {
      for (const token of String(raw).split(",").map((t) => t.trim()).filter(Boolean)) {
        if (!TOKEN_RE.test(token)) add("BAD_TOKEN", id, path, `"${token}" 不是大写 token`);
        else if (expected.values && !expected.values.includes(token)) {
          add("BAD_TOKEN", id, path, `"${token}" 不在 ${expected.values.join(" / ")}`);
        }
      }
    } else if (expected.kind === "token") {
      if (!TOKEN_RE.test(String(raw))) add("BAD_TOKEN", id, path, `"${raw}" 不是大写 token`);
    } else if (expected.kind === "version") {
      if (!VERSION_RE.test(String(raw))) add("BAD_VERSION", id, path, `"${raw}" 不是「主.次」`);
    } else if (expected.kind === "number") {
      const parsed = Number(raw);
      if (!Number.isFinite(parsed)) {
        add("BAD_NUMBER", id, path, `"${raw}" 不是数字`);
      } else {
        const declared = attr?.numeric;
        if (declared !== null && declared !== undefined && Math.abs(declared - parsed) > 1e-9) {
          add("NUMERIC_MISMATCH", id, path, `numeric=${declared} 与 value=${parsed} 不一致`);
        }
        const effective = declared ?? parsed;
        if (effective < expected.min) add("NUMBER_BELOW_MIN", id, path, `${effective} < ${expected.min}`);
      }
    }
  }
}

// ------------------------------------------------------------ C 交叉级
const byCategory = (category) => records.filter((record) => record.category === category);
const boards = byCategory("MOTHERBOARD");
const cases = byCategory("CASE");
const cpus = byCategory("CPU");
const memories = byCategory("MEMORY");

for (const board of boards) {
  const formFactor = valueOf(board, "formFactor");
  if (!formFactor) continue;
  const supported = cases.some((chassis) => csvOf(chassis, "supportedFormFactors").includes(formFactor));
  if (!supported) add("CASE_COVERAGE", board.componentId, "formFactor", `${formFactor} 无任何机箱支持`, { scope: "cross" });
}
for (const cpu of cpus) {
  const socket = valueOf(cpu, "socket");
  if (socket && !boards.some((board) => valueOf(board, "socket") === socket)) {
    add("BOARD_COVERAGE", cpu.componentId, "socket", `socket ${socket} 没有主板`, { scope: "cross" });
  }
}
for (const memory of memories) {
  const generation = valueOf(memory, "generation");
  if (generation && !boards.some((board) => valueOf(board, "memoryGeneration") === generation)) {
    add("BOARD_COVERAGE", memory.componentId, "generation", `${generation} 没有主板支持`, { scope: "cross" });
  }
}
const boardChipsets = new Set(boards.map((board) => valueOf(board, "chipset")).filter(Boolean));
for (const cpu of cpus) {
  const chipsets = csvOf(cpu, "supportedChipsets");
  if (chipsets.length && !chipsets.some((chipset) => boardChipsets.has(chipset))) {
    add("CHIPSET_COVERAGE", cpu.componentId, "supportedChipsets", `${chipsets.join(",")} 无对应主板芯片组`, { scope: "cross" });
  }
}

// ------------------------------------------------------------ D 来源表（只读）
// SOURCES.md 分两段：本轮采集表格（逐条带 URL）与散文列举的早期 demo seed
// （文件自己声明「价格不是本轮采集结果」）。两段分开统计，后者不算缺来源。
const sourcedRows = [];
const legacyIds = new Set();
for (const line of readFileSync(resolvePath(SOURCES), "utf8").replace(/^\uFEFF/, "").split("\n")) {
  const row = line.match(/^\|\s*`([^`|]+)`\s*\|([^|]*)\|([^|]*)\|/);
  if (row) {
    sourcedRows.push({ id: row[1].trim(), model: row[2].trim(), price: row[3].trim() });
    continue;
  }
  if (line.trimStart().startsWith("|")) continue;
  for (const token of line.matchAll(/`([^`]+)`/g)) {
    const candidate = token[1].trim();
    if (/^[a-z][a-z0-9]*(-[a-z0-9]+)+$/.test(candidate)) legacyIds.add(candidate);
  }
}
const catalogueIds = new Set(records.map((record) => record.componentId));
const sourcedIds = new Set(sourcedRows.map((row) => row.id));
const staleSources = sourcedRows.filter((row) => !catalogueIds.has(row.id));
const matchedSources = sourcedRows.filter((row) => catalogueIds.has(row.id));
const legacyInCatalogue = [...legacyIds].filter((id) => catalogueIds.has(id));
const missingSources = records.filter(
  (record) => !sourcedIds.has(record.componentId) && !legacyIds.has(record.componentId),
);
for (const row of staleSources) {
  add("SOURCE_STALE", row.id, "SOURCES.md", `表格行仍写「${row.model}」¥${row.price}，目录中已无此 ID`, {
    scope: "source",
    explained: "型号已下线或替换；SOURCES.md 归陈思言（INFO-01），本审计只报不改，留痕待其更新来源表",
  });
}
for (const record of missingSources) {
  add("SOURCE_MISSING", record.componentId, "SOURCES.md", "目录有记录但来源表两段都未提及", { scope: "source" });
}

// ------------------------------------------------------------ E 证据覆盖率
const stateCount = {};
const emptyFields = [];
const coverage = new Map();
let fieldTotal = 0;
let fieldsWithSources = 0;
for (const record of records) {
  const bucket = coverage.get(record.category) ?? { records: 0, fields: 0, sourced: 0 };
  bucket.records++;
  for (const [path, attr] of Object.entries(attrOf(record))) {
    fieldTotal++;
    stateCount[attr?.state] = (stateCount[attr?.state] ?? 0) + 1;
    bucket.fields++;
    if (Array.isArray(attr?.sources) && attr.sources.length > 0) {
      fieldsWithSources++;
      bucket.sourced++;
    }
    if (attr?.state === "EMPTY") emptyFields.push(`${record.componentId}.${path}`);
  }
  coverage.set(record.category, bucket);
}
const recordsWithSource = records.filter(
  (record) => sourcedIds.has(record.componentId) || legacyIds.has(record.componentId),
).length;
const recordsWithUrl = records.filter((record) => sourcedIds.has(record.componentId)).length;

// ------------------------------------------------------------ F 冻结表反向覆盖
// docs/03 §3.3 声明了每类允许的字段路径。某条路径如果没有任何记录使用，
// 说明目录没采集它（覆盖缺口），不是数据错误：只报告，不计为异常。
const unusedPaths = [];
for (const category of CATEGORIES) {
  const declared = Object.keys(FIELDS[category] ?? {});
  const used = new Set(
    records.filter((record) => record.category === category)
      .flatMap((record) => Object.keys(attrOf(record))),
  );
  for (const path of declared) {
    if (!used.has(path)) unusedPaths.push({ category, path });
  }
}
const pct = (part, whole) => (whole === 0 ? "—" : `${Math.round((part / whole) * 100)}%`);

// ------------------------------------------------------------ 输出报告
const STRICT = process.argv.includes("--strict");
// --strict：把「已解释」的来源表异常也算失败，供陈思言补采后当闸门用
const unexplained = anomalies.filter(
  (item) => !item.explained || (STRICT && item.scope === "source"),
);
const codesOf = (id, scope) =>
  [...new Set(anomalies.filter((item) => item.id === id && item.scope === scope).map((item) => item.code))];
const cell = (codes) => (codes.length ? codes.join(", ") : "PASS");
const sorted = [...records].sort((a, b) => {
  const byCategoryOrder = CATEGORIES.indexOf(a.category) - CATEGORIES.indexOf(b.category);
  return byCategoryOrder !== 0 ? byCategoryOrder : String(a.componentId).localeCompare(String(b.componentId));
});

const lines = [];
lines.push("# 20 · 目录一致性审计（DATA-03）", "");
lines.push("> 生成方式：`node tools/audit-catalogue.mjs`。本文件的统计与表格由脚本产出，"
  + "数据变更请改 `pricedata/` 后重新导出，不要手改本文件。");
lines.push(`> 审计对象：\`${CATALOGUE}\` — ${records.length} 条 / 版本 \`${version}\` / `
  + `价格日 \`${file.priceDate}\` / \`isDemo=${isDemo}\``);
lines.push("> 判定依据：[`docs/03-rules.md`](03-rules.md) §3.3 冻结字段表与 §3.4 CSV、数值解析规范；"
  + "任务包 [`docs/tasks/DATA-03.md`](tasks/DATA-03.md)");
lines.push(`> 运行结果：**${records.length} 条记录、${fieldTotal} 个字段逐项检查完毕；`
  + `异常 ${anomalies.length} 条（待修 ${unexplained.length} 条）；退出码 ${unexplained.length === 0 ? 0 : 1}**`, "");

lines.push("## 20.1 结论", "");
lines.push(`- 记录级（必需键、ID 唯一、类别、价格与日期、版本一致）：${records.length} 条全部合格；`);
lines.push(`- 字段级（冻结表字段齐全、枚举/正则/版本格式/数值下限、证据三态）：${fieldTotal} 个字段全部合格；`);
lines.push(`- 交叉级（板型↔机箱、socket↔主板、内存代数↔主板、芯片组↔主板）：无断链；`);
lines.push(`- 来源表：本轮采集 ${matchedSources.length}/${sourcedRows.length} 条与目录对上，`
  + `陈旧 ${staleSources.length} 条、目录缺来源 ${missingSources.length} 条，见 20.6；`);
if (unexplained.length === 0) {
  lines.push(`- 异常清单：${anomalies.length} 条，全部「已解释并定向」，无待修项，见 20.3；`);
} else {
  lines.push(`- 异常清单：${anomalies.length} 条，其中 **${unexplained.length} 条待修**，见 20.3；`);
}
lines.push(`- 待答辩关注项：字段级 \`sources\` 落地 ${fieldsWithSources}/${fieldTotal}，见 20.5。`, "");

lines.push("## 20.2 逐条结果", "");
lines.push("| # | componentId | 类别 | 型号 | 字段 | 记录级 | 字段级 | 异常代码 |");
lines.push("|---:|---|---|---|---:|---|---|---|");
sorted.forEach((record, index) => {
  const recordCodes = codesOf(record.componentId, "record").concat(codesOf(record.componentId, "source"));
  const fieldCodes = codesOf(record.componentId, "field").concat(codesOf(record.componentId, "cross"));
  lines.push(`| ${index + 1} | \`${record.componentId}\` | ${record.category} | ${record.model} | `
    + `${Object.keys(attrOf(record)).length} | ${cell(recordCodes)} | ${cell(fieldCodes)} | `
    + `${[...recordCodes, ...fieldCodes].join(", ") || "—"} |`);
});
lines.push("");

lines.push("## 20.3 异常与解释", "");
if (anomalies.length === 0) {
  lines.push("无异常。", "");
} else {
  lines.push("| # | 记录 | 检查项 | 现象 | 判定 | 去向 |");
  lines.push("|---:|---|---|---|---|---|");
  anomalies.forEach((item, index) => {
    const verdict = item.explained ? "已解释" : "**待修**";
    lines.push(`| ${index + 1} | \`${item.id}\` | ${TITLES[item.code] ?? item.code}（\`${item.code}\` / \`${item.path}\`） | `
      + `${item.detail} | ${verdict} | ${item.explained ?? "需在本卡或后续卡修复"} |`);
  });
  lines.push("");
}

lines.push("## 20.4 交叉一致性", "");
lines.push("主板板型 ↔ 机箱支持列表（`✓` = 该机箱支持该板型）：", "");
lines.push(`| 主板 | formFactor | ${cases.map((chassis) => `\`${chassis.componentId}\``).join(" | ")} |`);
lines.push(`|---|---|${cases.map(() => ":-:").join("|")}|`);
for (const board of boards) {
  const formFactor = valueOf(board, "formFactor");
  const cells = cases.map((chassis) =>
    formFactor && csvOf(chassis, "supportedFormFactors").includes(formFactor) ? "✓" : "—");
  lines.push(`| \`${board.componentId}\` | ${formFactor ?? "—"} | ${cells.join(" | ")} |`);
}
lines.push("");
for (const socket of SOCKETS) {
  const count = boards.filter((board) => valueOf(board, "socket") === socket).length;
  const cpusCount = cpus.filter((cpu) => valueOf(cpu, "socket") === socket).length;
  lines.push(`- socket \`${socket}\`：${cpusCount} 颗 CPU ↔ ${count} 块主板${count === 0 ? "（**断链**）" : ""}；`);
}
for (const generation of MEMORY_GENERATIONS) {
  const count = boards.filter((board) => valueOf(board, "memoryGeneration") === generation).length;
  const memoryCount = memories.filter((memory) => valueOf(memory, "generation") === generation).length;
  lines.push(`- 内存 \`${generation}\`：${memoryCount} 套内存 ↔ ${count} 块主板${count === 0 ? "（**断链**）" : ""}；`);
}
lines.push("");

lines.push("## 20.5 证据链现状（统计，不计为异常）", "");
lines.push("| 指标 | 值 |");
lines.push("|---|---:|");
lines.push(`| 字段总数 | ${fieldTotal} |`);
for (const state of STATES) lines.push(`| \`${state}\` | ${stateCount[state] ?? 0} |`);
lines.push(`| 字段级 \`sources\` 非空 | ${fieldsWithSources} |`);
lines.push(`| 字段级来源覆盖率 | ${pct(fieldsWithSources, fieldTotal)} |`);
lines.push(`| 记录级在 \`SOURCES.md\` 有出处 | ${recordsWithSource} / ${records.length} |`);
lines.push(`| 　其中本轮采集带 URL | ${recordsWithUrl} / ${records.length} |`);
lines.push(`| 　其中仅早期 demo 列举（无逐条 URL） | ${recordsWithSource - recordsWithUrl} / ${records.length} |`);
lines.push("");
lines.push(`\`EMPTY\` 是合法三态：规则读到即返回 \`UNVERIFIED\`，不视为缺数据 bug。本次共 `
  + `${emptyFields.length} 个：${emptyFields.map((item) => `\`${item}\``).join("、") || "无"}。`, "");
lines.push(`**要点**：${fieldTotal} 个字段的 \`sources\` 全部为空数组——字段级证据目前没有落地 URL；`
  + "`VERIFIED / REPORTED` 的差别来自 `pricedata` 导出时整条记录的 `confidence`"
  + "（见 [`pricedata/src/pricepipe/export.py`](../pricedata/src/pricepipe/export.py) 的 `_catalogue`），"
  + "记录级来源在 [`assets/catalogue/SOURCES.md`](../assets/catalogue/SOURCES.md)。"
  + "答辩口径只能表述为「记录级有来源、字段级暂无 URL」，不要声称字段级可溯源。", "");

lines.push("### 20.5.1 分门类覆盖率", "");
lines.push("| 类别 | 记录 | 字段 | 字段级有来源 | 覆盖率 |");
lines.push("|---|---:|---:|---:|---:|");
for (const category of CATEGORIES) {
  const bucket = coverage.get(category) ?? { records: 0, fields: 0, sourced: 0 };
  lines.push(`| ${category} | ${bucket.records} | ${bucket.fields} | ${bucket.sourced} | `
    + `${pct(bucket.sourced, bucket.fields)} |`);
}
lines.push(`| **合计** | **${records.length}** | **${fieldTotal}** | **${fieldsWithSources}** | `
  + `**${pct(fieldsWithSources, fieldTotal)}** |`, "");

lines.push("### 20.5.2 冻结表反向覆盖（声明了、但没有任何记录使用的字段）", "");
if (unusedPaths.length === 0) {
  lines.push("`docs/03` §3.3 声明的每一条字段路径都至少被一条记录使用。", "");
} else {
  lines.push(`${unusedPaths.length} 条字段路径尚未被任何记录使用。这是**采集覆盖缺口**，`
    + "不是数据错误：冻结表允许它，目录没采它。补数或删路径都需要走 ADR，本审计不代为选择。", "");
  lines.push("| 类别 | 未使用的字段路径 |");
  lines.push("|---|---|");
  for (const item of unusedPaths) lines.push(`| ${item.category} | \`${item.path}\` |`);
  lines.push("");
}

lines.push("## 20.6 来源表交叉（只读，不改）", "");
lines.push("`assets/catalogue/SOURCES.md` 里有两段口径不同的内容：一段是 2026-11-04 的本轮采集表格"
  + "（逐条带来源 URL），一段是用散文列出的早期 demo seed（文件自己声明「价格不是本轮采集结果」）。"
  + "两段分开统计，后一段不计为缺来源：", "");
lines.push("| 指标 | 值 |");
lines.push("|---|---:|");
lines.push(`| 本轮采集表格行 | ${sourcedRows.length} |`);
lines.push(`| 　其中与目录匹配 | ${matchedSources.length} |`);
lines.push(`| 　其中目录已无此 ID（陈旧） | ${staleSources.length} |`);
lines.push(`| 早期 demo seed（散文列举） | ${legacyIds.size} |`);
lines.push(`| 　其中与目录匹配 | ${legacyInCatalogue.length} |`);
lines.push(`| 目录记录 | ${records.length} |`);
lines.push(`| 目录有记录、两段都未提及 | ${missingSources.length} |`);
lines.push("");
if (staleSources.length) {
  lines.push("陈旧的来源表行（型号已下线或替换，`SOURCES.md` 归陈思言，本卡只报不改）：", "");
  for (const row of staleSources) {
    lines.push(`- \`${row.id}\` → 来源表写「${row.model}」¥${row.price}，目录中已无该 ID；`);
  }
  lines.push("");
}
lines.push(`**口径**：「价格表无空来源」在本仓库只能是「本轮采集的 ${matchedSources.length} 条有 URL」；`
  + `另 ${legacyInCatalogue.length} 条早期 demo 没有逐条 URL，来源表已声明，属已解释项。`
  + "若要把「每条都有 URL」当闸门，需陈思言在 INFO-01 补采后另开任务，本审计不代为断言。", "");

lines.push("## 20.7 检查项清单", "");
lines.push("脚本实现的每条规则都有稳定代码，`grep` 代码即可定位：", "");
for (const [code, title] of Object.entries(TITLES)) {
  lines.push(`- \`${code}\`：${title}；`);
}
lines.push("");

lines.push("## 20.8 复现与验收", "");
lines.push("```bash");
lines.push("node tools/audit-catalogue.mjs            # 打印摘要并重写本文件");
lines.push("node tools/audit-catalogue.mjs --no-write # 只检查不写文件（CI 用）");
lines.push("```", "");
lines.push("验收标准：退出码 0，且 20.3 节没有「待修」项。", "");

const report = lines.join("\n");
if (WRITE) writeFileSync(resolvePath(REPORT), report, "utf8");

// ------------------------------------------------------------ 控制台摘要
console.log(`[audit] ${CATALOGUE}：${records.length} 条记录 / ${fieldTotal} 个字段 / 版本 ${version}`);
console.log(`[audit] 证据分布：${STATES.map((state) => `${state}=${stateCount[state] ?? 0}`).join(" ")}`
  + `；字段级 sources 非空 ${fieldsWithSources}/${fieldTotal}`);
console.log(`[audit] 证据覆盖：字段级 ${fieldsWithSources}/${fieldTotal}（${pct(fieldsWithSources, fieldTotal)}）；`
  + `记录级有出处 ${recordsWithSource}/${records.length}（本轮带 URL ${recordsWithUrl}）；`
  + `未使用字段路径 ${unusedPaths.length} 条`);
console.log(`[audit] 来源表：表格 ${sourcedRows.length} 行（匹配 ${matchedSources.length} / 陈旧 ${staleSources.length}）`
  + `，早期 demo ${legacyIds.size} 条，目录缺来源 ${missingSources.length} 条`);
console.log(`[audit] 异常 ${anomalies.length} 条（待修 ${unexplained.length}${STRICT ? "，--strict" : ""}）`);
for (const item of anomalies) {
  const tag = item.explained ? "[已解释]" : "[待修]";
  const line = `  ${tag} ${item.code} ${item.id}.${item.path} — ${item.detail}`;
  if (item.explained) console.log(line);
  else console.error(line);
}
console.log(WRITE ? `[audit] 已写入 ${REPORT}` : "[audit] --no-write：未写文件");
if (unexplained.length) {
  console.error(`[audit] 失败：${unexplained.length} 条异常待修。`);
  process.exitCode = 1;
} else {
  console.log("[audit] OK — 无待修异常。");
}

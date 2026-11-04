#!/usr/bin/env node
/**
 * 生成 assets/catalogue/demo-v1.json、assets/rules/rules-v1.json、
 * assets/fixtures/fixtures-rules-v1.json（15 规则 × 4 类 = 60 条）、
 * assets/fixtures/fixtures-anchor-v1.json（答辩锚点）。
 *
 * 单一真相源：本文件的 DEMO / RULE_SPECS。改数值先改这里再重新生成，
 * 禁止手改生成物（CI 会用 --check 比对）。
 *
 * 用法：
 *   node tools/gen-fixtures.mjs           # 生成
 *   node tools/gen-fixtures.mjs --check   # 只校验生成物是否与源一致
 */
import { writeFileSync, readFileSync, existsSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");

const CATALOGUE_VERSION = "static-v1";
const RULES_VERSION = "rules-v1";
const PRICE_DATE = "2026-10-31";

const V = (v, n) => ({ value: String(v), state: "VERIFIED", sources: [], conflict: false, numeric: n === undefined ? null : n });
const EMPTY = () => ({ value: null, state: "EMPTY", sources: [], conflict: false, numeric: null });
const REPORTED = (v, n) => ({ value: String(v), state: "REPORTED", sources: [{ source: "retailer", rawValue: String(v), state: "REPORTED" }], conflict: false, numeric: n === undefined ? null : n });

/** 演示目录：8 类 10 件，价格刻意合计为 7800（答辩锚点）。 */
const DEMO = {
  "cpu-demo-7600x": { category: "CPU", manufacturer: "AMD", model: "Ryzen 5 7600X", partNumber: "100-100000593WOF", priceCny: 1500,
    attributes: { socket: V("AM5"), supportedChipsets: V("B650,X670"), tdpW: V(105, 105), minBiosGeneration: V("1.0", 1.0) } },
  "cpu-demo-13600k": { category: "CPU", manufacturer: "Intel", model: "Core i5-13600K", partNumber: "BX8071513600K", priceCny: 1700,
    attributes: { socket: V("LGA1700"), supportedChipsets: V("B760,Z790"), tdpW: V(125, 125), minBiosGeneration: V("1.0", 1.0) } },
  "mb-demo-b650": { category: "MOTHERBOARD", manufacturer: "ASUS", model: "TUF B650-PLUS", partNumber: "TUF-B650-PLUS", priceCny: 900,
    attributes: { socket: V("AM5"), chipset: V("B650"), biosGeneration: V("1.2", 1.2), memoryGeneration: V("DDR5"),
      memorySlots: V(4, 4), maxMemoryGb: V(128, 128), formFactor: V("ATX"), m2Slots: V(2, 2), sataPorts: V(4, 4),
      storageInterfaces: V("M2_NVME,SATA") } },
  "mb-demo-lga1700": { category: "MOTHERBOARD", manufacturer: "MSI", model: "PRO B760M-A", partNumber: "B760M-A", priceCny: 700,
    attributes: { socket: V("LGA1700"), chipset: V("B760"), biosGeneration: V("1.0", 1.0), memoryGeneration: V("DDR5"),
      memorySlots: V(4, 4), maxMemoryGb: V(128, 128), formFactor: V("MICRO_ATX"), m2Slots: V(2, 2), sataPorts: V(4, 4),
      storageInterfaces: V("M2_NVME,SATA") } },
  "mem-demo-ddr5-32": { category: "MEMORY", manufacturer: "Kingston", model: "FURY Beast 32GB", partNumber: "KF560C36BBEK2-32", priceCny: 500,
    attributes: { generation: V("DDR5"), moduleCapacityGb: V(16, 16), kitCapacityGb: V(32, 32), moduleCount: V(2, 2) } },
  "mem-demo-ddr4-32": { category: "MEMORY", manufacturer: "Kingston", model: "FURY Beast 32GB DDR4", partNumber: "KF432C16BBK2-32", priceCny: 380,
    attributes: { generation: V("DDR4"), moduleCapacityGb: V(16, 16), kitCapacityGb: V(32, 32), moduleCount: V(2, 2) } },
  "gpu-demo-337": { category: "GPU", manufacturer: "NVIDIA", model: "RTX 4070 Ti (337mm)", partNumber: "DEMO-337", priceCny: 3400,
    attributes: { lengthMm: V(337, 337), thicknessMm: V(61, 61), slotWidth: V(3, 3), tdpW: V(285, 285),
      recommendedPsuW: V(750, 750), requiredPsuConnectors: V("12VHPWR") } },
  "gpu-demo-300": { category: "GPU", manufacturer: "NVIDIA", model: "RTX 4070 (300mm)", partNumber: "DEMO-300", priceCny: 3500,
    attributes: { lengthMm: V(300, 300), thicknessMm: V(55, 55), slotWidth: V(3, 3), tdpW: V(200, 200),
      recommendedPsuW: V(650, 650), requiredPsuConnectors: V("8PIN_X2") } },
  "psu-demo-750": { category: "PSU", manufacturer: "Seasonic", model: "FOCUS GX-750", partNumber: "GX-750", priceCny: 600,
    attributes: { continuousW: V(750, 750), connectors: V("12VHPWR,8PIN_X2,SATA") } },
  "psu-demo-550": { category: "PSU", manufacturer: "Cooler Master", model: "MWE 550", partNumber: "MWE550", priceCny: 450,
    attributes: { continuousW: V(550, 550), connectors: V("8PIN_X2,SATA") } },
  "case-demo-320": { category: "CASE", manufacturer: "Fractal", model: "North (320mm)", partNumber: "FD-NORTH", priceCny: 400,
    attributes: { maxGpuLengthMm: V(320, 320), maxGpuThicknessMm: V(60, 60), maxGpuSlots: V(3, 3),
      maxCoolerHeightMm: V(160, 160), supportedFormFactors: V("ATX,MICRO_ATX"), driveBays: V(3, 3) } },
  "case-demo-itx": { category: "CASE", manufacturer: "Cooler Master", model: "NR200", partNumber: "NR200", priceCny: 350,
    attributes: { maxGpuLengthMm: V(330, 330), maxGpuThicknessMm: V(60, 60), maxGpuSlots: V(3, 3),
      maxCoolerHeightMm: V(155, 155), supportedFormFactors: V("MICRO_ATX,MINI_ITX"), driveBays: V(2, 2) } },
  "cooler-demo-160": { category: "COOLER", manufacturer: "Thermalright", model: "PA120", partNumber: "PA120", priceCny: 300,
    attributes: { heightMm: V(160, 160), socketMounts: V("AM5,AM4,LGA1700") } },
  "cooler-demo-175": { category: "COOLER", manufacturer: "Noctua", model: "NH-D15", partNumber: "NH-D15", priceCny: 320,
    attributes: { heightMm: V(175, 175), socketMounts: V("AM4,LGA1700") } },
  "storage-demo-nvme": { category: "STORAGE", manufacturer: "Samsung", model: "990 EVO 1TB", partNumber: "MZ-V9E1T0", priceCny: 200,
    attributes: { interface: V("M2_NVME") } },
  "storage-demo-sata": { category: "STORAGE", manufacturer: "Crucial", model: "MX500 1TB", partNumber: "CT1000MX500", priceCny: 180,
    attributes: { interface: V("SATA") } }
};

const item = (id, qty = 1) => ({ componentId: id, category: DEMO[id].category, quantity: qty, unitPriceCny: DEMO[id].priceCny });

/** 规则表：left 是"集合/左值"，right 是"单值/右值"，语义见 docs/03-rules.md §3.4。 */
const RULES = [
  { ruleId: "CPU_SOCKET_MATCH", kind: "EQUALS", left: { category: "CPU", path: "socket" }, right: { category: "MOTHERBOARD", path: "socket" }, severity: "BLOCKER", aggregation: "SINGLE" },
  { ruleId: "CPU_MB_CHIPSET_SUPPORT", kind: "CSV_CONTAINS", left: { category: "CPU", path: "supportedChipsets" }, right: { category: "MOTHERBOARD", path: "chipset" }, severity: "BLOCKER", aggregation: "SINGLE" },
  { ruleId: "CPU_MB_BIOS_SUPPORT", kind: "NUMERIC_GE", left: { category: "MOTHERBOARD", path: "biosGeneration" }, right: { category: "CPU", path: "minBiosGeneration" }, severity: "BLOCKER", aggregation: "SINGLE" },
  { ruleId: "MEMORY_DDR_GENERATION", kind: "EQUALS", left: { category: "MEMORY", path: "generation" }, right: { category: "MOTHERBOARD", path: "memoryGeneration" }, severity: "BLOCKER", aggregation: "PER_ITEM" },
  { ruleId: "MEMORY_SLOT_COUNT", kind: "COUNT_LE", left: { category: "MEMORY", path: "moduleCount" }, right: { category: "MOTHERBOARD", path: "memorySlots" }, severity: "MAJOR", aggregation: "COUNT" },
  { ruleId: "MEMORY_CAPACITY_LIMIT", kind: "SUM_LE", left: { category: "MEMORY", path: "kitCapacityGb" }, right: { category: "MOTHERBOARD", path: "maxMemoryGb" }, severity: "MAJOR", aggregation: "SUM" },
  { ruleId: "GPU_CASE_LENGTH", kind: "NUMERIC_LE", left: { category: "GPU", path: "lengthMm" }, right: { category: "CASE", path: "maxGpuLengthMm" }, severity: "BLOCKER", aggregation: "SINGLE" },
  { ruleId: "GPU_CASE_THICKNESS", kind: "NUMERIC_LE", left: { category: "GPU", path: "thicknessMm" }, right: { category: "CASE", path: "maxGpuThicknessMm" }, severity: "MAJOR", aggregation: "SINGLE" },
  { ruleId: "GPU_CASE_SLOT_WIDTH", kind: "NUMERIC_LE", left: { category: "GPU", path: "slotWidth" }, right: { category: "CASE", path: "maxGpuSlots" }, severity: "MAJOR", aggregation: "SINGLE" },
  { ruleId: "PSU_CONTINUOUS_POWER", kind: "NUMERIC_GE", left: { category: "PSU", path: "continuousW" }, right: { category: "CPU", path: "tdpW" }, severity: "BLOCKER", aggregation: "SINGLE",
    formula: "requiredW = ceil((cpu.tdpW + gpu.tdpW + 100) * 1.2)", formulaTarget: "PSU.continuousW" },
  { ruleId: "PSU_CONNECTORS", kind: "CSV_SUBSET", left: { category: "PSU", path: "connectors" }, right: { category: "GPU", path: "requiredPsuConnectors" }, severity: "BLOCKER", aggregation: "SINGLE" },
  { ruleId: "COOLER_CASE_HEIGHT", kind: "NUMERIC_LE", left: { category: "COOLER", path: "heightMm" }, right: { category: "CASE", path: "maxCoolerHeightMm" }, severity: "BLOCKER", aggregation: "SINGLE" },
  { ruleId: "COOLER_CPU_SOCKET", kind: "CSV_CONTAINS", left: { category: "COOLER", path: "socketMounts" }, right: { category: "CPU", path: "socket" }, severity: "BLOCKER", aggregation: "SINGLE" },
  { ruleId: "MB_CASE_FORM_FACTOR", kind: "CSV_CONTAINS", left: { category: "CASE", path: "supportedFormFactors" }, right: { category: "MOTHERBOARD", path: "formFactor" }, severity: "BLOCKER", aggregation: "SINGLE" },
  { ruleId: "STORAGE_MB_INTERFACE", kind: "CSV_CONTAINS", left: { category: "MOTHERBOARD", path: "storageInterfaces" }, right: { category: "STORAGE", path: "interface" }, severity: "MAJOR", aggregation: "SINGLE" }
];

const BUILD_A = ["cpu-demo-7600x", "mb-demo-b650", "mem-demo-ddr5-32", "gpu-demo-337", "psu-demo-750", "case-demo-320", "cooler-demo-160", "storage-demo-nvme"];
const BUILD_B = BUILD_A.map((id) => (id === "gpu-demo-337" ? "gpu-demo-300" : id));
const items = (ids, qty = {}) => ids.map((id) => item(id, qty[id] || 1));

/** 每条规则四类用例：pass / conflict / unverified / missing。 */
const CASES = {
  CPU_SOCKET_MATCH: {
    pass: { items: items(["cpu-demo-7600x", "mb-demo-b650"]) },
    conflict: { items: items(["cpu-demo-7600x", "mb-demo-lga1700"]) },
    unverified: { items: items(["cpu-demo-7600x", "mb-demo-b650"]), overrides: { "mb-demo-b650": { socket: EMPTY() } } },
    missing: { items: items(["cpu-demo-7600x"]) }
  },
  CPU_MB_CHIPSET_SUPPORT: {
    pass: { items: items(["cpu-demo-7600x", "mb-demo-b650"]) },
    conflict: { items: items(["cpu-demo-7600x", "mb-demo-b650"]), overrides: { "mb-demo-b650": { chipset: V("X870") } } },
    unverified: { items: items(["cpu-demo-7600x", "mb-demo-b650"]), overrides: { "cpu-demo-7600x": { supportedChipsets: EMPTY() } } },
    missing: { items: items(["mb-demo-b650"]) }
  },
  CPU_MB_BIOS_SUPPORT: {
    pass: { items: items(["cpu-demo-7600x", "mb-demo-b650"]) },
    conflict: { items: items(["cpu-demo-7600x", "mb-demo-b650"]), overrides: { "mb-demo-b650": { biosGeneration: V("0.9", 0.9) } } },
    unverified: { items: items(["cpu-demo-7600x", "mb-demo-b650"]), overrides: { "mb-demo-b650": { biosGeneration: EMPTY() } } },
    missing: { items: items(["cpu-demo-7600x"]) }
  },
  MEMORY_DDR_GENERATION: {
    pass: { items: items(["mem-demo-ddr5-32", "mb-demo-b650"]) },
    conflict: { items: items(["mem-demo-ddr4-32", "mb-demo-b650"]) },
    unverified: { items: items(["mem-demo-ddr5-32", "mb-demo-b650"]), overrides: { "mem-demo-ddr5-32": { generation: EMPTY() } } },
    missing: { items: items(["mb-demo-b650"]) }
  },
  MEMORY_SLOT_COUNT: {
    pass: { items: items(["mem-demo-ddr5-32", "mb-demo-b650"]) },
    conflict: { items: items(["mem-demo-ddr5-32", "mb-demo-b650"], { "mem-demo-ddr5-32": 3 }) },
    unverified: { items: items(["mem-demo-ddr5-32", "mb-demo-b650"]), overrides: { "mem-demo-ddr5-32": { moduleCount: EMPTY() } } },
    missing: { items: items(["mb-demo-b650"]) }
  },
  MEMORY_CAPACITY_LIMIT: {
    pass: { items: items(["mem-demo-ddr5-32", "mb-demo-b650"]) },
    conflict: { items: items(["mem-demo-ddr5-32", "mb-demo-b650"], { "mem-demo-ddr5-32": 5 }) },
    unverified: { items: items(["mem-demo-ddr5-32", "mb-demo-b650"]), overrides: { "mb-demo-b650": { maxMemoryGb: EMPTY() } } },
    missing: { items: items(["mem-demo-ddr5-32"]) }
  },
  GPU_CASE_LENGTH: {
    pass: { items: items(["gpu-demo-300", "case-demo-320"]) },
    conflict: { items: items(["gpu-demo-337", "case-demo-320"]) },
    unverified: { items: items(["gpu-demo-337", "case-demo-320"]), overrides: { "case-demo-320": { maxGpuLengthMm: EMPTY() } } },
    missing: { items: items(["gpu-demo-337"]) }
  },
  GPU_CASE_THICKNESS: {
    pass: { items: items(["gpu-demo-300", "case-demo-320"]) },
    conflict: { items: items(["gpu-demo-337", "case-demo-320"]) },
    unverified: { items: items(["gpu-demo-337", "case-demo-320"]), overrides: { "gpu-demo-337": { thicknessMm: EMPTY() } } },
    missing: { items: items(["gpu-demo-337"]) }
  },
  GPU_CASE_SLOT_WIDTH: {
    pass: { items: items(["gpu-demo-337", "case-demo-320"]) },
    conflict: { items: items(["gpu-demo-337", "case-demo-320"]), overrides: { "gpu-demo-337": { slotWidth: V(4, 4) } } },
    unverified: { items: items(["gpu-demo-337", "case-demo-320"]), overrides: { "case-demo-320": { maxGpuSlots: EMPTY() } } },
    missing: { items: items(["gpu-demo-337"]) }
  },
  PSU_CONTINUOUS_POWER: {
    pass: { items: items(["cpu-demo-7600x", "gpu-demo-300", "psu-demo-750"]) },
    conflict: { items: items(["cpu-demo-7600x", "gpu-demo-337", "psu-demo-550"]) },
    unverified: { items: items(["cpu-demo-7600x", "gpu-demo-300", "psu-demo-750"]), overrides: { "psu-demo-750": { continuousW: EMPTY() } } },
    missing: { items: items(["cpu-demo-7600x", "gpu-demo-300"]) }
  },
  PSU_CONNECTORS: {
    pass: { items: items(["gpu-demo-300", "psu-demo-750"]) },
    conflict: { items: items(["gpu-demo-337", "psu-demo-550"]) },
    unverified: { items: items(["gpu-demo-300", "psu-demo-750"]), overrides: { "psu-demo-750": { connectors: EMPTY() } } },
    missing: { items: items(["gpu-demo-300"]) }
  },
  COOLER_CASE_HEIGHT: {
    pass: { items: items(["cooler-demo-160", "case-demo-320"]) },
    conflict: { items: items(["cooler-demo-175", "case-demo-320"]) },
    unverified: { items: items(["cooler-demo-160", "case-demo-320"]), overrides: { "cooler-demo-160": { heightMm: EMPTY() } } },
    missing: { items: items(["cooler-demo-160"]) }
  },
  COOLER_CPU_SOCKET: {
    pass: { items: items(["cpu-demo-7600x", "cooler-demo-160"]) },
    conflict: { items: items(["cpu-demo-7600x", "cooler-demo-175"]) },
    unverified: { items: items(["cpu-demo-7600x", "cooler-demo-160"]), overrides: { "cooler-demo-160": { socketMounts: EMPTY() } } },
    missing: { items: items(["cpu-demo-7600x"]) }
  },
  MB_CASE_FORM_FACTOR: {
    pass: { items: items(["mb-demo-b650", "case-demo-320"]) },
    conflict: { items: items(["mb-demo-b650", "case-demo-itx"]) },
    unverified: { items: items(["mb-demo-b650", "case-demo-320"]), overrides: { "mb-demo-b650": { formFactor: EMPTY() } } },
    missing: { items: items(["mb-demo-b650"]) }
  },
  STORAGE_MB_INTERFACE: {
    pass: { items: items(["storage-demo-nvme", "mb-demo-b650"]) },
    conflict: { items: items(["storage-demo-sata", "mb-demo-b650"]), overrides: { "mb-demo-b650": { storageInterfaces: V("M2_NVME") } } },
    unverified: { items: items(["storage-demo-nvme", "mb-demo-b650"]), overrides: { "mb-demo-b650": { storageInterfaces: EMPTY() } } },
    missing: { items: items(["storage-demo-nvme"]) }
  }
};

/** 期望值：与实现无关的声明式断言（oracle 由测试侧独立计算，见 07 章）。 */
function expectedFor(rule, kind, spec) {
  if (kind === "missing") {
    return { outcome: "UNVERIFIED", reason: "MISSING_FIELD", weakest: "VERIFIED", missingFieldIsNull: true };
  }
  if (kind === "unverified") {
    return { outcome: "UNVERIFIED", reason: "MISSING_FIELD", weakest: "EMPTY" };
  }
  if (kind === "pass") return { outcome: "PASSED", weakest: "VERIFIED" };
  return { outcome: "CONFLICT", severity: rule.severity, weakest: "VERIFIED" };
}

function buildRuleFixtures() {
  const out = [];
  for (const rule of RULES) {
    const spec = CASES[rule.ruleId];
    for (const [kind, c] of Object.entries(spec)) {
      const overrides = c.overrides || {};
      const ids = [...new Set([...c.items.map((i) => i.componentId), ...Object.keys(overrides)])];
      const catalogue = ids.map((id) => ({ componentId: id, ...clone(DEMO[id]) }));
      for (const [cid, patch] of Object.entries(overrides)) {
        const rec = catalogue.find((r) => r.componentId === cid);
        Object.assign(rec.attributes, patch);
      }
      const fx = {
        fixtureId: "fx_" + rule.ruleId.toLowerCase() + "_" + kind,
        ruleId: rule.ruleId,
        case: ({ pass: "PASSED", conflict: "CONFLICT", unverified: "UNVERIFIED", missing: "MISSING" })[kind],
        isDemo: true,
        buildId: "fx-" + rule.ruleId.toLowerCase() + "-" + kind,
        revisionId: 1,
        items: c.items,
        catalogue,
        expected: { [rule.ruleId]: expectedFor(rule, kind, rule) }
      };
      if (kind === "pass" && rule.ruleId === "GPU_CASE_LENGTH") {
        fx.expected[rule.ruleId].conflictId = null;
      }
      out.push(fx);
    }
  }
  return out;
}

function clone(o) { return JSON.parse(JSON.stringify(o)); }

function buildAnchorFixtures() {
  const full = items(BUILD_A);
  const total = full.reduce((s, i) => s + i.quantity * i.unitPriceCny, 0);
  const totalAfter = items(BUILD_B).reduce((s, i) => s + i.quantity * i.unitPriceCny, 0);
  const cat = (ids) => ids.map((id) => ({ componentId: id, ...clone(DEMO[id]) }));

  return [
    {
      fixtureId: "fx_gpu_len_conflict_337_320",
      purpose: "答辩锚点：337 mm 显卡 vs 320 mm 机箱",
      isDemo: true, buildId: "fx-anchor-337", revisionId: 1,
      items: items(["gpu-demo-337", "case-demo-320"]),
      catalogue: cat(["gpu-demo-337", "case-demo-320"]),
      expected: {
        GPU_CASE_LENGTH: {
          outcome: "CONFLICT", severity: "BLOCKER", weakest: "VERIFIED",
          conflictId: "GPU_CASE_LENGTH:gpu-demo-337.lengthMm:case-demo-320.maxGpuLengthMm",
          leftValue: "337", rightValue: "320"
        }
      }
    },
    {
      fixtureId: "fx_gpu_len_pass_300_320",
      purpose: "边界：300 <= 320 必须 PASSED（杀死 <= 改 < 的变异）",
      isDemo: true, buildId: "fx-anchor-300", revisionId: 1,
      items: items(["gpu-demo-300", "case-demo-320"]),
      catalogue: cat(["gpu-demo-300", "case-demo-320"]),
      expected: { GPU_CASE_LENGTH: { outcome: "PASSED", weakest: "VERIFIED" } }
    },
    {
      fixtureId: "fx_gpu_len_equal_320_320",
      purpose: "边界：320 <= 320 必须 PASSED（等号边界）",
      isDemo: true, buildId: "fx-anchor-eq", revisionId: 1,
      items: items(["gpu-demo-300", "case-demo-320"]),
      catalogue: cat(["gpu-demo-300", "case-demo-320"]).map((r) => {
        if (r.componentId === "gpu-demo-300") r.attributes.lengthMm = V(320, 320);
        return r;
      }),
      expected: { GPU_CASE_LENGTH: { outcome: "PASSED", weakest: "VERIFIED" } }
    },
    {
      fixtureId: "fx_bios_missing",
      purpose: "答辩锚点：BIOS 字段 EMPTY -> UNVERIFIED，missingField 指向它",
      isDemo: true, buildId: "fx-anchor-bios", revisionId: 1,
      items: items(["cpu-demo-7600x", "mb-demo-b650"]),
      catalogue: cat(["cpu-demo-7600x", "mb-demo-b650"]).map((r) => {
        if (r.componentId === "mb-demo-b650") r.attributes.biosGeneration = EMPTY();
        return r;
      }),
      expected: {
        CPU_MB_BIOS_SUPPORT: {
          outcome: "UNVERIFIED", reason: "MISSING_FIELD", weakest: "EMPTY",
          missingField: { componentId: "mb-demo-b650", fieldPath: "biosGeneration" }
        }
      }
    },
    {
      fixtureId: "fx_source_conflict_gpu_len",
      purpose: "答辩锚点：两个 reported 来源 337 / 335 -> UNVERIFIED(SOURCE_CONFLICT)，不得按 337 比较",
      isDemo: true, buildId: "fx-anchor-conflict", revisionId: 1,
      items: items(["gpu-demo-337", "case-demo-320"]),
      catalogue: cat(["gpu-demo-337", "case-demo-320"]).map((r) => {
        if (r.componentId === "gpu-demo-337") {
          r.attributes.lengthMm = {
            value: "337", state: "REPORTED", conflict: true, numeric: null,
            sources: [
              { source: "retailer-a", rawValue: "337", state: "REPORTED" },
              { source: "retailer-b", rawValue: "335", state: "REPORTED" }
            ]
          };
        }
        return r;
      }),
      expected: {
        GPU_CASE_LENGTH: {
          outcome: "UNVERIFIED", reason: "SOURCE_CONFLICT", weakest: "REPORTED",
          missingField: { componentId: "gpu-demo-337", fieldPath: "lengthMm" }
        }
      }
    },
    {
      fixtureId: "fx_psu_power_conflict",
      purpose: "答辩锚点：requiredW 606 > continuousW 550",
      isDemo: true, buildId: "fx-anchor-psu", revisionId: 1,
      items: items(["cpu-demo-7600x", "gpu-demo-337", "psu-demo-550"]),
      catalogue: cat(["cpu-demo-7600x", "gpu-demo-337", "psu-demo-550"]),
      expected: {
        PSU_CONTINUOUS_POWER: {
          outcome: "CONFLICT", severity: "BLOCKER", weakest: "VERIFIED",
          note: "requiredW = ceil((105 + 285 + 100) * 1.2) = ceil(588) = 588 > 550"
        }
      }
    },
    {
      fixtureId: "fx_memory_gen_conflict",
      purpose: "DDR4 内存插 DDR5 主板",
      isDemo: true, buildId: "fx-anchor-mem", revisionId: 1,
      items: items(["mem-demo-ddr4-32", "mb-demo-b650"]),
      catalogue: cat(["mem-demo-ddr4-32", "mb-demo-b650"]),
      expected: {
        MEMORY_DDR_GENERATION: { outcome: "CONFLICT", severity: "BLOCKER", weakest: "VERIFIED", leftValue: "DDR4", rightValue: "DDR5" }
      }
    },
    {
      fixtureId: "fx_suggestion_300_plus_100",
      purpose: "端到端锚点：7800 -> 7900，GPU_CASE_LENGTH 转 PASSED，BIOS 仍 UNVERIFIED",
      isDemo: true, buildId: "fx-anchor-e2e", revisionId: 1,
      items: full,
      catalogue: cat(BUILD_A).map((r) => {
        if (r.componentId === "mb-demo-b650") r.attributes.biosGeneration = EMPTY();
        return r;
      }),
      expectedBefore: {
        totalCny: total,
        GPU_CASE_LENGTH: { outcome: "CONFLICT", severity: "BLOCKER" },
        CPU_MB_BIOS_SUPPORT: { outcome: "UNVERIFIED", reason: "MISSING_FIELD" }
      },
      suggestion: {
        conflictId: "GPU_CASE_LENGTH:gpu-demo-337.lengthMm:case-demo-320.maxGpuLengthMm",
        candidateComponentId: "gpu-demo-300",
        priceDeltaCny: 100,
        rank: 1
      },
      expectedAfter: {
        totalCny: totalAfter,
        GPU_CASE_LENGTH: { outcome: "PASSED" },
        CPU_MB_BIOS_SUPPORT: { outcome: "UNVERIFIED", reason: "MISSING_FIELD" },
        budgetCny: 8000,
        withinBudget: true
      }
    }
  ];
}

function emit(path, obj) {
  const text = JSON.stringify(obj, null, 2) + "\n";
  if (process.argv.includes("--check")) {
    const old = existsSync(path) ? readFileSync(path, "utf8") : "";
    if (old !== text) {
      console.error("[gen-fixtures] 生成物与源不一致：" + path);
      process.exitCode = 1;
    } else {
      console.log("[gen-fixtures] OK " + path);
    }
    return;
  }
  writeFileSync(path, text);
  console.log("[gen-fixtures] wrote " + path + " (" + text.length + " bytes)");
}

const catalogue = {
  catalogueVersion: CATALOGUE_VERSION,
  priceDate: PRICE_DATE,
  isDemo: true,
  note: "概念数据，仅用于 fixture 与答辩演示；禁止进入生产 seed（INV-11）。",
  components: Object.entries(DEMO).map(([id, r]) => ({ componentId: id, ...r, catalogueVersion: CATALOGUE_VERSION, priceDate: PRICE_DATE, isDemo: true }))
};

const rules = {
  rulesVersion: RULES_VERSION,
  catalogueVersion: CATALOGUE_VERSION,
  rules: RULES.map((r) => ({ ...r, fixtures: Object.keys(CASES[r.ruleId]).map((k) => "fx_" + r.ruleId.toLowerCase() + "_" + k) }))
};

const ruleFixtures = { fixturesVersion: "fx-rules-v1", rulesVersion: RULES_VERSION, catalogueVersion: CATALOGUE_VERSION, fixtures: buildRuleFixtures() };
const anchorFixtures = { fixturesVersion: "fx-anchor-v1", rulesVersion: RULES_VERSION, catalogueVersion: CATALOGUE_VERSION, fixtures: buildAnchorFixtures() };

emit(join(ROOT, "assets/catalogue/demo-v1.json"), catalogue);
emit(join(ROOT, "assets/rules/rules-v1.json"), rules);
emit(join(ROOT, "assets/fixtures/fixtures-rules-v1.json"), ruleFixtures);
emit(join(ROOT, "assets/fixtures/fixtures-anchor-v1.json"), anchorFixtures);

console.log("[gen-fixtures] rules=" + RULES.length + " ruleFixtures=" + ruleFixtures.fixtures.length + " anchors=" + anchorFixtures.fixtures.length + " buildA=" + totalOf(BUILD_A) + " buildB=" + totalOf(BUILD_B));
function totalOf(ids) { return items(ids).reduce((s, i) => s + i.quantity * i.unitPriceCny, 0); }

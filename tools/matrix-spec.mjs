#!/usr/bin/env node
/**
 * 验证矩阵的规格来源（单一事实源）。
 *
 * `check-matrix.mjs` 用它判断"期望有哪些格子"；`gen-matrix.mjs` 用它决定
 * 从真实测试产物里能生成哪些格子。两边共用一份定义，避免规格漂移。
 *
 * 依据：docs/07-unit-verification.md §7.5、docs/03-rules.md、docs/04-state-machines.md。
 */
import { readFileSync } from "node:fs";
import { join, dirname } from "node:path";
import { fileURLToPath } from "node:url";

export const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..");
export const CASES = ["PASSED", "CONFLICT", "UNVERIFIED", "MISSING"];
export const readJson = (rel) => JSON.parse(readFileSync(join(ROOT, rel), "utf8"));

export const FSM_GRID = {
    diagnosis: {
        states: ["Idle", "Running", "Done", "Failed"],
        events: ["StartCheck", "CheckCompleted", "CheckFailed", "BuildEdited"],
    },
    replacement: {
        states: ["Idle", "Candidates", "Previewing", "Rechecking", "Applied", "NoCandidate"],
        events: [
            "ConflictSelected",
            "CandidatesLoaded",
            "PreviewCandidate",
            "PreviewBuilt",
            "Apply",
            "Cancel",
            "RecheckCompleted",
            "RecheckRejected",
            "BuildEdited",
            "Dismiss",
        ],
    },
};

/** 规则矩阵：15 条规则 × 4 类结果。 */
export function expectedRules() {
    const out = new Set();
    for (const rule of readJson("assets/rules/rules-v1.json").rules) {
        for (const c of CASES) out.add("rule:" + rule.ruleId + "|case:" + c);
    }
    return out;
}

/** 状态机矩阵：诊断 4×4 + 替换 6×10 = 76 格。 */
export function expectedFsm() {
    const out = new Set();
    for (const [machine, grid] of Object.entries(FSM_GRID)) {
        for (const s of grid.states) for (const e of grid.events) out.add("fsm:" + machine + ":" + s + ":" + e);
    }
    return out;
}

/** 不变量矩阵：INV-01..18。 */
export function expectedInvariants() {
    const out = new Set();
    for (let i = 1; i <= 18; i++) out.add("inv:INV-" + String(i).padStart(2, "0"));
    return out;
}

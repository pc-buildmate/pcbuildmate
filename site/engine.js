/*
 * 规则引擎（浏览器版，与 docs/03-rules.md 的执行语义一一对应）。
 * 纯函数：evaluate(revision, catalogue, opts) -> Outcome[]
 * 不读时钟、不读随机、不碰 DOM。
 */
(function (root, factory) {
  var api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.PBEngine = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function () {
  "use strict";

  var STATE_RANK = { EMPTY: 0, REPORTED: 1, VERIFIED: 2 };
  var rankOf = function (s) { return STATE_RANK[s] == null ? 0 : STATE_RANK[s]; };
  var weakestOf = function (states) {
    if (!states.length) return "VERIFIED";
    var w = states[0];
    for (var i = 1; i < states.length; i++) if (rankOf(states[i]) < rankOf(w)) w = states[i];
    return w;
  };
  var splitCsv = function (v) {
    if (v == null) return [];
    return String(v).split(",").map(function (x) { return x.trim(); }).filter(Boolean);
  };
  var numOf = function (field) {
    if (!field) return NaN;
    if (typeof field.numeric === "number") return field.numeric;
    var n = parseFloat(field.value);
    return isNaN(n) ? NaN : n;
  };

  function catalogueIndex(catalogue) {
    var idx = {};
    var list = Array.isArray(catalogue) ? catalogue : (catalogue && catalogue.catalogue) || [];
    // 先出现的记录优先：允许 fixture 用同名记录覆盖演示目录中的证据状态
    for (var i = 0; i < list.length; i++) if (!idx[list[i].componentId]) idx[list[i].componentId] = list[i];
    return idx;
  }

  /** 取某个类别的全部条目（保持 items 顺序）。 */
  function itemsOf(revision, category) {
    return (revision.items || []).filter(function (it) { return it.category === category; });
  }

  /** 解析一个操作数：item -> record -> field（含证据覆盖）；返回结构化错误而不是抛异常。 */
  function resolve(item, idx, path, overrides, stateOverride) {
    if (!item) return { ok: false, code: "MISSING_PART" };
    var rec = idx[item.componentId];
    if (!rec) return { ok: false, code: "MISSING_RECORD" };
    var field = rec.attributes ? rec.attributes[path] : null;
    if (!field) return { ok: false, code: "MISSING_FIELD", path: path };
    var copy = {
      value: field.value, state: field.state, numeric: field.numeric,
      conflict: !!field.conflict, sources: field.sources || []
    };
    var ov = overrides ? overrides[item.componentId + "." + path] : null;
    if (ov) {
      copy.state = ov.state || copy.state;
      if (ov.value !== undefined) copy.value = ov.value;
      if (ov.value === null || ov.state === "EMPTY") { copy.value = null; copy.numeric = null; }
      if (ov.conflict !== undefined) copy.conflict = ov.conflict;
    }
    return { ok: true, item: item, record: rec, field: copy, path: path };
  }

  function conflictId(ruleId, leftItem, leftPath, rightItem, rightPath, suffix) {
    return ruleId + ":" + leftItem.componentId + "." + leftPath + ":" +
      rightItem.componentId + "." + rightPath + (suffix || "");
  }

  function unverified(ruleId, reason, missingField, weakest, extra) {
    var o = {
      ruleId: ruleId, status: "UNVERIFIED", reason: reason,
      missingField: missingField == null ? null : missingField,
      weakest: weakest || "VERIFIED"
    };
    return Object.assign(o, extra || {});
  }

  function conflict(ruleId, severity, weakest, cid, left, right) {
    return {
      ruleId: ruleId, status: "CONFLICT", severity: severity, weakest: weakest,
      conflictId: cid, left: left, right: right
    };
  }

  function passed(ruleId, weakest, left, right) {
    return { ruleId: ruleId, status: "PASSED", weakest: weakest, left: left, right: right };
  }

  function describe(item, path, field) {
    return {
      componentId: item.componentId, category: item.category, path: path,
      value: field ? String(field.value) : null, numeric: field ? numOf(field) : null,
      state: field ? field.state : null
    };
  }

  /** 把一次比较失败/成功包装成统一结果。 */
  function compareOutcome(rule, ctx) {
    var left = ctx.left, right = ctx.right; // resolve() 结果
    var weakest = weakestOf([left.field.state, right.field.state]);
    var cid = conflictId(rule.ruleId, left.item, left.path, right.item, right.path, ctx.suffix);
    var l = describe(left.item, left.path, left.field);
    var r = describe(right.item, right.path, right.field);
    if (ctx.ok) return passed(rule.ruleId, weakest, l, r);
    return conflict(rule.ruleId, rule.severity, weakest, cid, l, r);
  }

  /** 五步判定中的第 2/3 步：来源冲突优先于字段缺失。 */
  function precheck(rule, operands) {
    for (var i = 0; i < operands.length; i++) {
      var o = operands[i];
      if (o.field.conflict) {
        return unverified(rule.ruleId, "SOURCE_CONFLICT", o.path, o.field.state, {
          conflictOn: o.item.componentId + "." + o.path
        });
      }
    }
    for (var j = 0; j < operands.length; j++) {
      var f = operands[j].field;
      if (f.value == null || f.state === "EMPTY") {
        return unverified(rule.ruleId, "MISSING_FIELD", operands[j].path, weakestOf([f.state]));
      }
    }
    return null;
  }

  function evalSingle(rule, revision, idx, overrides) {
    var leftItems = itemsOf(revision, rule.left.category);
    var rightItems = itemsOf(revision, rule.right.category);
    var leftItem = leftItems[0];
    var rightItem = rightItems[0];
    if (!leftItem || !rightItem) {
      return [unverified(rule.ruleId, "MISSING_FIELD", null, "VERIFIED")];
    }
    var L = resolve(leftItem, idx, rule.left.path, overrides);
    var R = resolve(rightItem, idx, rule.right.path, overrides);
    if (!L.ok || !R.ok) {
      return [unverified(rule.ruleId, "MISSING_FIELD", (!L.ok && L.path) || (!R.ok && R.path) || null, "VERIFIED")];
    }
    var pre = precheck(rule, [L, R]);
    if (pre) return [pre];

    var ok = false, lv, rv;
    if (rule.kind === "EQUALS") {
      var a = String(L.field.value).trim(), b = String(R.field.value).trim();
      ok = a === b; lv = a; rv = b;
    } else if (rule.kind === "CSV_CONTAINS") {
      var set = splitCsv(L.field.value);
      var single = String(R.field.value).trim();
      ok = set.indexOf(single) >= 0; lv = set.join(","); rv = single;
    } else if (rule.kind === "CSV_SUBSET") {
      var leftSet = splitCsv(L.field.value);
      var need = splitCsv(R.field.value);
      ok = need.every(function (t) { return leftSet.indexOf(t) >= 0; });
      lv = leftSet.join(","); rv = need.join(",");
    } else if (rule.kind === "NUMERIC_LE" || rule.kind === "NUMERIC_GE") {
      var ln = numOf(L.field), rn = numOf(R.field);
      if (isNaN(ln) || isNaN(rn)) {
        return [unverified(rule.ruleId, "INSUFFICIENT_DATA", isNaN(ln) ? L.path : R.path, "VERIFIED")];
      }
      ok = rule.kind === "NUMERIC_LE" ? ln <= rn : ln >= rn;
      lv = ln; rv = rn;
    } else {
      return [unverified(rule.ruleId, "UNSUPPORTED_RULE", null, "VERIFIED")];
    }
    return [compareOutcome(rule, { left: L, right: R, ok: ok })];
  }

  function evalPerItem(rule, revision, idx, overrides) {
    var leftItems = itemsOf(revision, rule.left.category);
    var rightItems = itemsOf(revision, rule.right.category);
    var rightItem = rightItems[0];
    if (!leftItems.length || !rightItem) {
      return [unverified(rule.ruleId, "MISSING_FIELD", null, "VERIFIED")];
    }
    return leftItems.map(function (item, i) {
      var L = resolve(item, idx, rule.left.path, overrides);
      var R = resolve(rightItem, idx, rule.right.path, overrides);
      if (!L.ok || !R.ok) {
        return unverified(rule.ruleId, "MISSING_FIELD", (!L.ok && L.path) || null, "VERIFIED", { instance: i });
      }
      var pre = precheck(rule, [L, R]);
      if (pre) { pre.instance = i; return pre; }
      var ok = String(L.field.value).trim() === String(R.field.value).trim();
      return compareOutcome(rule, { left: L, right: R, ok: ok, suffix: "#" + i });
    });
  }

  function evalAggregate(rule, revision, idx, overrides) {
    var leftItems = itemsOf(revision, rule.left.category);
    var rightItem = itemsOf(revision, rule.right.category)[0];
    if (!leftItems.length || !rightItem) {
      return [unverified(rule.ruleId, "MISSING_FIELD", null, "VERIFIED")];
    }
    var sum = 0, states = [];
    for (var i = 0; i < leftItems.length; i++) {
      var it = leftItems[i];
      var L = resolve(it, idx, rule.left.path, overrides);
      if (!L.ok) return [unverified(rule.ruleId, "MISSING_FIELD", L.path || null, "VERIFIED")];
      if (L.field.conflict) return [unverified(rule.ruleId, "SOURCE_CONFLICT", L.path, L.field.state)];
      if (L.field.value == null || L.field.state === "EMPTY") {
        return [unverified(rule.ruleId, "MISSING_FIELD", L.path, L.field.state)];
      }
      var n = numOf(L.field);
      if (isNaN(n)) return [unverified(rule.ruleId, "INSUFFICIENT_DATA", L.path, L.field.state)];
      sum += n * (it.quantity || 1);
      states.push(L.field.state);
    }
    var R = resolve(rightItem, idx, rule.right.path, overrides);
    if (!R.ok) return [unverified(rule.ruleId, "MISSING_FIELD", null, "VERIFIED")];
    if (R.field.conflict) return [unverified(rule.ruleId, "SOURCE_CONFLICT", R.path, R.field.state)];
    if (R.field.value == null || R.field.state === "EMPTY") {
      return [unverified(rule.ruleId, "MISSING_FIELD", R.path, R.field.state)];
    }
    states.push(R.field.state);
    var limit = numOf(R.field);
    if (isNaN(limit)) return [unverified(rule.ruleId, "INSUFFICIENT_DATA", R.path, "VERIFIED")];
    var weakest = weakestOf(states);
    var syntheticLeft = { componentId: leftItems[0].componentId, category: leftItems[0].category, path: rule.left.path, value: String(sum), numeric: sum, state: weakest };
    var syntheticRight = { componentId: rightItem.componentId, category: rightItem.category, path: rule.right.path, value: String(limit), numeric: limit, state: R.field.state };
    if (sum <= limit) return [passed(rule.ruleId, weakest, syntheticLeft, syntheticRight)];
    return [conflict(rule.ruleId, rule.severity, weakest,
      conflictId(rule.ruleId, leftItems[0], rule.left.path, rightItem, rule.right.path),
      syntheticLeft, syntheticRight)];
  }

  function evalFormula(rule, revision, idx, overrides) {
    // PSU: requiredW = ceil((cpu.tdpW + gpu.tdpW + 100) * 1.2)
    var cpu = itemsOf(revision, "CPU")[0];
    var gpu = itemsOf(revision, "GPU")[0];
    var psuItem = itemsOf(revision, rule.left.category)[0];
    if (!cpu || !gpu || !psuItem) return [unverified(rule.ruleId, "MISSING_FIELD", null, "VERIFIED")];
    var cpuTdp = resolve(cpu, idx, "tdpW", overrides);
    var gpuTdp = resolve(gpu, idx, "tdpW", overrides);
    var psuW = resolve(psuItem, idx, rule.left.path, overrides);
    if (!cpuTdp.ok || !gpuTdp.ok || !psuW.ok) return [unverified(rule.ruleId, "MISSING_FIELD", null, "VERIFIED")];
    var pre = precheck(rule, [cpuTdp, gpuTdp, psuW]);
    if (pre) return [pre];
    var required = Math.ceil((numOf(cpuTdp.field) + numOf(gpuTdp.field) + 100) * 1.2);
    var weakest = weakestOf([cpuTdp.field.state, gpuTdp.field.state, psuW.field.state]);
    var left = describe(psuItem, rule.left.path, psuW.field);
    var right = { componentId: "formula", category: "FORMULA", path: "requiredW", value: String(required), numeric: required, state: weakest };
    var cid = conflictId(rule.ruleId, psuItem, rule.left.path, { componentId: "formula" }, "requiredW");
    if (numOf(psuW.field) >= required) return [passed(rule.ruleId, weakest, left, right)];
    return [conflict(rule.ruleId, rule.severity, weakest, cid, left, right)];
  }

  function evaluate(revision, catalogue, opts) {
    opts = opts || {};
    var idx = catalogueIndex(catalogue);
    var byId = {};
    for (var i = 0; i < opts.rules.length; i++) byId[opts.rules[i].ruleId] = opts.rules[i];
    var rules = (opts.rules || []).slice().sort(function (a, b) {
      return a.ruleId < b.ruleId ? -1 : a.ruleId > b.ruleId ? 1 : 0;
    });
    var out = [];
    for (var k = 0; k < rules.length; k++) {
      var rule = rules[k];
      var res;
      if (rule.formula) res = evalFormula(rule, revision, idx, opts.evidenceOverrides);
      else if (rule.aggregation === "PER_ITEM") res = evalPerItem(rule, revision, idx, opts.evidenceOverrides);
      else if (rule.aggregation === "COUNT" || rule.aggregation === "SUM") res = evalAggregate(rule, revision, idx, opts.evidenceOverrides);
      else res = evalSingle(rule, revision, idx, opts.evidenceOverrides);
      out = out.concat(res);
    }
    out.sort(function (a, b) {
      if (a.ruleId !== b.ruleId) return a.ruleId < b.ruleId ? -1 : 1;
      var x = a.conflictId || "", y = b.conflictId || "";
      return x < y ? -1 : x > y ? 1 : 0;
    });
    return out;
  }

  function overall(outcomes) {
    if (!outcomes || !outcomes.length) return "UNVERIFIED";
    var conflicts = outcomes.filter(function (o) { return o.status === "CONFLICT"; });
    var unverified = outcomes.filter(function (o) { return o.status === "UNVERIFIED"; });
    if (conflicts.some(function (o) { return o.severity === "BLOCKER"; })) return "CONFLICT";
    if (conflicts.length && unverified.length) return "MIXED";
    if (conflicts.length) return "CONFLICT";
    if (unverified.length) return "UNVERIFIED";
    return "PASSED";
  }

  function risk(outcomes) {
    if (!outcomes || !outcomes.length) return "UNKNOWN";
    var c = outcomes.filter(function (o) { return o.status === "CONFLICT"; });
    if (c.some(function (o) { return o.severity === "BLOCKER"; })) return "HIGH";
    if (c.some(function (o) { return o.severity === "MAJOR"; })) return "MEDIUM";
    return "LOW";
  }

  function score(outcomes) {
    var b = 0, M = 0, m = 0, u = 0;
    (outcomes || []).forEach(function (o) {
      if (o.status === "UNVERIFIED") { u++; return; }
      if (o.status !== "CONFLICT") return;
      if (o.severity === "BLOCKER") b++;
      else if (o.severity === "MAJOR") M++;
      else m++;
    });
    return Math.max(0, Math.min(100, 100 - 30 * b - 15 * M - 5 * m - 3 * u));
  }

  function diagnose(revision, catalogue, opts) {
    var outcomes = evaluate(revision, catalogue, opts);
    return {
      revisionId: revision.id,
      outcomes: outcomes,
      overall: overall(outcomes),
      riskLevel: risk(outcomes),
      score: score(outcomes),
      counts: {
        passed: outcomes.filter(function (o) { return o.status === "PASSED"; }).length,
        conflict: outcomes.filter(function (o) { return o.status === "CONFLICT"; }).length,
        unverified: outcomes.filter(function (o) { return o.status === "UNVERIFIED"; }).length
      },
      catalogueVersion: opts.catalogueVersion || null,
      computedAt: opts.now || null
    };
  }

  return {
    evaluate: evaluate, diagnose: diagnose, overall: overall, risk: risk, score: score,
    weakestOf: weakestOf, splitCsv: splitCsv
  };
});

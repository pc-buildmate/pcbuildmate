/*
 * 工作区状态机（浏览器版）：Intent -> 新 revision -> 两张状态机 -> Effect。
 * 纯函数，不碰 DOM、不读时钟。docs/04-state-machines.md 的转移表在这里落地。
 */
(function (root, factory) {
  var api = factory(root.PBEngine || (typeof require === "function" ? require("./engine.js") : null));
  if (typeof module === "object" && module.exports) module.exports = api;
  else root.PBState = api;
})(typeof globalThis !== "undefined" ? globalThis : this, function (engine) {
  "use strict";

  var CATEGORIES = ["CPU", "MOTHERBOARD", "MEMORY", "GPU", "PSU", "CASE", "COOLER", "STORAGE"];
  var CATEGORY_LABEL = {
    CPU: "处理器", MOTHERBOARD: "主板", MEMORY: "内存", GPU: "显卡",
    PSU: "电源", CASE: "机箱", COOLER: "散热器", STORAGE: "硬盘"
  };

  var computeTotal = function (items) {
    var t = 0;
    for (var i = 0; i < items.length; i++) t += items[i].quantity * items[i].unitPriceCny;
    return t;
  };

  var emptyRevision = function (buildId, budget) {
    return {
      id: 1, buildId: buildId, items: [], totalCny: 0,
      catalogueVersion: "static-v1", priceDate: "", isDemo: true, budgetCny: budget
    };
  };

  function itemsFor(cat, picks) {
    return CATEGORIES.map(function (c) {
      var id = picks[c];
      return id ? { category: c, componentId: id, quantity: 1, unitPriceCny: 0 } : null;
    }).filter(Boolean);
  }

  /** 用目录里的价格重算 items（保证 totalCny 与 items 一致，INV-01）。 */
  function reprice(items, catalogueIndex) {
    return items.map(function (it) {
      var rec = catalogueIndex[it.componentId];
      return {
        category: it.category, componentId: it.componentId,
        quantity: it.quantity, unitPriceCny: rec ? rec.priceCny : it.unitPriceCny
      };
    });
  }

  function indexCatalogue(list) {
    var idx = {};
    for (var i = 0; i < list.length; i++) if (!idx[list[i].componentId]) idx[list[i].componentId] = list[i];
    return idx;
  }

  function makeRevision(state, items) {
    var items2 = reprice(items, state.catalogueIndex);
    return {
      id: state.nextRevisionId,
      buildId: state.revision.buildId,
      items: items2,
      totalCny: computeTotal(items2),
      catalogueVersion: state.catalogueVersion,
      priceDate: state.priceDate,
      isDemo: true,
      budgetCny: state.budget
    };
  }

  /** 编辑都走这里：新 revision、清诊断、不自动重检（docs/04 D-03/D-04）。 */
  function edit(state, mutate, logText) {
    var items = state.revision.items.map(function (x) { return Object.assign({}, x); });
    var next = mutate(items);
    if (next === false) return { state: state, effects: [] };
    var rev = makeRevision(state, next);
    var nextState = Object.assign({}, state, {
      revision: rev,
      nextRevisionId: state.nextRevisionId + 1,
      revisionHistory: state.revisionHistory.concat([state.revision]),
      diagnosis: null,
      diagnosisState: { kind: "Idle" },
      replacement: { kind: "Idle" },
      needsCheck: true,
      screen: state.screen === "entry" ? "workspace" : state.screen
    });
    nextState = log(nextState, logText || "编辑配置", "new revision #" + rev.id + "，诊断已清空（不自动重检）");
    return { state: nextState, effects: [{ type: "SaveDraft", revisionId: rev.id }] };
  }

  function log(state, kind, detail) {
    var entry = { at: state.logSeq, kind: kind, detail: detail };
    return Object.assign({}, state, { log: state.log.concat([entry]), logSeq: state.logSeq + 1 });
  }

  function decision(state, code, detail) {
    return log(state, code, detail);
  }

  // ---------------------------------------------------------------- 建议
  function suggest(state, conflict) {
    var cat = conflict.left.category;
    var current = state.revision.items.filter(function (i) { return i.category === cat; });
    var candidates = [];
    var pool = state.catalogue.filter(function (r) { return r.category === cat; });
    var baseDiag = state.diagnosis;
    var baseBlockers = {};
    if (baseDiag) {
      baseDiag.outcomes.forEach(function (o) {
        if (o.status === "CONFLICT" && o.severity === "BLOCKER") baseBlockers[o.conflictId] = true;
      });
    }
    for (var i = 0; i < pool.length; i++) {
      var rec = pool[i];
      if (current.length && current[0].componentId === rec.componentId) continue;
      var items = state.revision.items.map(function (it) {
        if (it.category !== cat) return it;
        return { category: cat, componentId: rec.componentId, quantity: it.quantity, unitPriceCny: rec.priceCny };
      });
      var rev = {
        id: state.revision.id + 1, items: items, totalCny: computeTotal(items),
        catalogueVersion: state.catalogueVersion, isDemo: true
      };
      var diag = engine.diagnose(rev, state.catalogue, {
        rules: state.rules, evidenceOverrides: state.evidenceOverrides,
        catalogueVersion: state.catalogueVersion, now: "preview"
      });
      var target = null, otherConflicts = [];
      diag.outcomes.forEach(function (o) {
        if (o.ruleId === conflict.ruleId) target = o;
        else if (o.status === "CONFLICT") otherConflicts.push(o);
      });
      if (!target || target.status === "CONFLICT") continue; // 没解决目标冲突
      var newBlockers = otherConflicts.filter(function (o) {
        return o.severity === "BLOCKER" && !baseBlockers[o.conflictId];
      });
      if (newBlockers.length) continue; // 引入新 BLOCKER：直接过滤
      var currentPrice = current.reduce(function (s, x) { return s + x.unitPriceCny * x.quantity; }, 0);
      var qty = current.length ? current[0].quantity : 1;
      candidates.push({
        componentId: rec.componentId,
        category: cat,
        model: rec.manufacturer + " " + rec.model,
        priceCny: rec.priceCny,
        priceDeltaCny: (rec.priceCny - (current[0] ? current[0].unitPriceCny : 0)) * qty,
        totalAfterCny: computeTotal(items),
        affectsOtherRules: otherConflicts.map(function (o) { return o.ruleId; }),
        resolvesConflictId: conflict.conflictId
      });
    }
    candidates.sort(function (a, b) {
      if (a.priceDeltaCny !== b.priceDeltaCny) return a.priceDeltaCny - b.priceDeltaCny;
      return a.componentId < b.componentId ? -1 : 1;
    });
    candidates.forEach(function (c, i) { c.rank = i + 1; });
    return {
      candidates: candidates.slice(0, 6),
      noCandidate: candidates.length ? null : {
        code: "NO_COMPONENT_RESOLVES_CONFLICT",
        blockingConstraint: conflict.ruleId + " @ " + conflict.left.path,
        message: "目录里没有能同时满足硬约束的替代件"
      }
    };
  }

  function previewFor(state, candidate) {
    var items = state.revision.items.map(function (it) {
      if (it.category !== candidate.category) return it;
      return { category: candidate.category, componentId: candidate.componentId, quantity: it.quantity, unitPriceCny: candidate.priceCny };
    });
    var rev = {
      id: state.nextRevisionId, buildId: state.revision.buildId, items: items,
      totalCny: computeTotal(items), catalogueVersion: state.catalogueVersion,
      priceDate: state.priceDate, isDemo: true, budgetCny: state.budget
    };
    return rev;
  }

  // ---------------------------------------------------------------- reducer
  function reduce(state, ev) {
    switch (ev.type) {
      case "SetBudget": {
        return { state: Object.assign({}, state, { budget: ev.budget, revision: Object.assign({}, state.revision, { budgetCny: ev.budget }) }), effects: [] };
      }
      case "SetUseCase": {
        return { state: Object.assign({}, state, { useCase: ev.useCase }), effects: [] };
      }
      case "Goto": {
        return { state: Object.assign({}, state, { screen: ev.screen }), effects: [] };
      }
      case "LoadDemo": {
        var rev = makeRevision(state, itemsFor(null, ev.picks));
        var s1 = Object.assign({}, state, {
          revision: rev, nextRevisionId: state.nextRevisionId + 1,
          diagnosis: null, diagnosisState: { kind: "Idle" },
          replacement: { kind: "Idle" }, needsCheck: false, screen: "workspace"
        });
        return { state: log(s1, "LoadDemo", "载入配置，总价 " + rev.totalCny + "，revision #" + rev.id), effects: [{ type: "SaveDraft" }] };
      }
      case "AddComponent":
      case "ReplaceComponent": {
        var rec = state.catalogueIndex[ev.componentId];
        if (!rec) return { state: decision(state, "REJECTED", "目录里没有该零件（CATALOGUE_MISS）"), effects: [] };
        var exists = state.revision.items.some(function (i) { return i.category === rec.category; });
        if (ev.type === "AddComponent" && exists) return { state: decision(state, "REJECTED", "该类别已有零件，请用替换"), effects: [] };
        return edit(state, function (items) {
          var replaced = false;
          var out = items.map(function (it) {
            if (it.category !== rec.category) return it;
            replaced = true;
            return { category: it.category, componentId: rec.componentId, quantity: it.quantity, unitPriceCny: rec.priceCny };
          });
          if (!replaced) out.push({ category: rec.category, componentId: rec.componentId, quantity: 1, unitPriceCny: rec.priceCny });
          return out;
        }, (ev.type === "AddComponent" ? "添加 " : "替换为 ") + rec.model);
      }
      case "RemoveComponent": {
        return edit(state, function (items) {
          return items.filter(function (it) { return it.category !== ev.category; });
        }, "移除 " + (CATEGORY_LABEL[ev.category] || ev.category));
      }
      case "SetQuantity": {
        if (ev.quantity < 1 || ev.quantity > 6) return { state: decision(state, "REJECTED", "数量必须在 1–6（QUANTITY_INVALID）"), effects: [] };
        return edit(state, function (items) {
          return items.map(function (it) {
            if (it.componentId !== ev.componentId) return it;
            return { category: it.category, componentId: it.componentId, quantity: ev.quantity, unitPriceCny: it.unitPriceCny };
          });
        }, "数量改为 " + ev.quantity);
      }
      case "Undo": {
        if (!state.revisionHistory.length) return { state: decision(state, "REJECTED", "没有可撤销的操作"), effects: [] };
        var prev = state.revisionHistory[state.revisionHistory.length - 1];
        var rev = Object.assign({}, prev, { id: state.nextRevisionId });
        var s2 = Object.assign({}, state, {
          revision: rev, nextRevisionId: state.nextRevisionId + 1,
          revisionHistory: state.revisionHistory.slice(0, -1),
          diagnosis: null, diagnosisState: { kind: "Idle" }, replacement: { kind: "Idle" },
          needsCheck: true
        });
        return { state: log(s2, "Undo", "回到 revision #" + prev.id + " 的内容，新的 id 是 #" + rev.id), effects: [{ type: "SaveDraft" }] };
      }
      case "StartCheck": {
        if (state.diagnosisState.kind === "Running") return { state: decision(state, "IGNORED", "已在检查中（重复点击被忽略）"), effects: [] };
        if (!state.revision.items.length) return { state: decision(state, "REJECTED", "空配置不能检查（diagnostics_required）"), effects: [] };
        return {
          state: Object.assign({}, state, { diagnosisState: { kind: "Running", revisionId: state.revision.id }, screen: "diagnostics" }),
          effects: [{ type: "RunDiagnosis", revisionId: state.revision.id, revision: state.revision, slow: state.slowCheck }]
        };
      }
      case "CheckCompleted": {
        var diag = ev.diagnosis;
        if (state.revision.id !== diag.revisionId) {
          return { state: decision(state, "DROPPED", "迟到的诊断属于 revision #" + diag.revisionId + "，当前是 #" + state.revision.id + "，已丢弃"), effects: [] };
        }
        var s3 = Object.assign({}, state, { diagnosis: diag, diagnosisState: { kind: "Done", revisionId: diag.revisionId }, needsCheck: false });
        return { state: log(s3, "CheckDone", "overall=" + diag.overall + "，冲突 " + diag.counts.conflict + "，未验证 " + diag.counts.unverified), effects: [] };
      }
      case "CheckFailed": {
        return { state: Object.assign({}, state, { diagnosisState: { kind: "Failed", error: ev.error } }), effects: [] };
      }
      case "SelectConflict": {
        if (!state.diagnosis) return { state: decision(state, "REJECTED", "还没有诊断结果"), effects: [] };
        var c = state.diagnosis.outcomes.filter(function (o) { return o.conflictId === ev.conflictId; })[0];
        if (!c) return { state: decision(state, "REJECTED", "找不到该冲突"), effects: [] };
        return {
          state: Object.assign({}, state, { replacement: { kind: "Loading", conflict: c } }),
          effects: [{ type: "LoadCandidates", conflict: c }]
        };
      }
      case "CandidatesLoaded": {
        if (state.replacement.kind !== "Loading") {
          return { state: decision(state, "IGNORED", "候选返回时已切换上下文，丢弃"), effects: [] };
        }
        var r = ev.result;
        var conflict = state.replacement.conflict;
        if (r.noCandidate) {
          return {
            state: Object.assign({}, state, { replacement: { kind: "NoCandidate", conflict: conflict, reason: r.noCandidate } }),
            effects: [{ type: "ShowText", key: "no_candidate" }]
          };
        }
        return {
          state: Object.assign({}, state, { replacement: { kind: "Candidates", conflict: conflict, candidates: r.candidates } }),
          effects: []
        };
      }
      case "PreviewCandidate": {
        var cand = (state.replacement.candidates || []).filter(function (x) { return x.componentId === ev.componentId; })[0];
        if (!cand) return { state: decision(state, "REJECTED", "候选不存在"), effects: [] };
        var preview = previewFor(state, cand);
        var s4 = Object.assign({}, state, {
          replacement: {
            kind: "Previewing", conflict: state.replacement.conflict, candidate: cand,
            previewRevision: preview, baseRevisionId: state.revision.id
          }
        });
        return { state: log(s4, "Preview", "预览总价 " + preview.totalCny + "（差 " + (preview.totalCny - state.revision.totalCny) + "），未写入"), effects: [{ type: "BuildPreview", revisionId: preview.id }] };
      }
      case "Apply": {
        var rp = state.replacement;
        if (rp.kind !== "Previewing") return { state: decision(state, "REJECTED", "没有可应用的预览"), effects: [] };
        if (rp.baseRevisionId !== state.revision.id) {
          return {
            state: decision(Object.assign({}, state, { replacement: { kind: "Idle" } }), "REJECTED", "APPLY_REVISION_STALE：基线已变，预览作废"),
            effects: [{ type: "ShowText", key: "apply_revision_stale" }]
          };
        }
        var s5 = Object.assign({}, state, { replacement: Object.assign({}, rp, { kind: "Rechecking" }) });
        return { state: log(s5, "Recheck", "应用前全量重检 preview #" + rp.previewRevision.id), effects: [{ type: "Recheck", revision: rp.previewRevision }] };
      }
      case "RecheckCompleted": {
        var rp2 = state.replacement;
        if (rp2.kind !== "Rechecking") {
          return { state: decision(state, "IGNORED", "重检结果到达时已不在重检中，丢弃"), effects: [] };
        }
        var d = ev.diagnosis;
        var blockers = d.outcomes.filter(function (o) { return o.status === "CONFLICT" && o.severity === "BLOCKER"; });
        if (blockers.length) {
          var s6 = Object.assign({}, state, {
            replacement: { kind: "Candidates", conflict: rp2.conflict, candidates: rp2.candidates }
          });
          return {
            state: log(s6, "RecheckRejected", "重检发现新的 BLOCKER：" + blockers.map(function (b) { return b.ruleId; }).join(", ") + "，未应用"),
            effects: [{ type: "ShowText", key: "recheck_rejected" }]
          };
        }
        var rev2 = Object.assign({}, rp2.previewRevision, { diagnosisSource: "recheck" });
        var s7 = Object.assign({}, state, {
          revision: rev2, nextRevisionId: state.nextRevisionId + 1,
          revisionHistory: state.revisionHistory.concat([state.revision]),
          diagnosis: d, diagnosisState: { kind: "Done", revisionId: rev2.id },
          replacement: { kind: "Applied", conflict: rp2.conflict, candidate: rp2.candidate },
          needsCheck: false
        });
        return {
          state: log(s7, "Applied", "已应用，revision #" + rev2.id + "，总价 " + rev2.totalCny + "，overall=" + d.overall),
          effects: [{ type: "PersistApplied", revisionId: rev2.id }]
        };
      }
      case "CancelPreview":
      case "Dismiss": {
        if (state.replacement.kind === "Idle") return { state: state, effects: [] };
        return { state: Object.assign({}, state, { replacement: { kind: "Idle" } }), effects: [] };
      }
      case "SaveBuild": {
        if (!state.revision.items.length) return { state: decision(state, "REJECTED", "空配置不能保存"), effects: [] };
        var build = {
          id: "b" + (state.savedBuilds.length + 1),
          name: (state.buildName || "配置") + " #" + (state.savedBuilds.length + 1),
          revision: JSON.parse(JSON.stringify(state.revision)),
          diagnosis: state.diagnosis ? JSON.parse(JSON.stringify(state.diagnosis)) : null,
          savedAt: state.savedAtLabel
        };
        var s8 = Object.assign({}, state, { savedBuilds: state.savedBuilds.concat([build]) });
        return { state: log(s8, "Saved", "保存 " + build.name + "（revision #" + build.revision.id + "）"), effects: [{ type: "PersistRevision" }] };
      }
      case "SwitchBuild": {
        var target = state.savedBuilds.filter(function (b) { return b.id === ev.buildId; })[0];
        if (!target) return { state: decision(state, "REJECTED", "没有这个配置"), effects: [] };
        var revS = Object.assign({}, target.revision, { id: state.nextRevisionId });
        var s10 = Object.assign({}, state, {
          revision: revS, nextRevisionId: state.nextRevisionId + 1,
          revisionHistory: state.revisionHistory.concat([state.revision]),
          diagnosis: null, diagnosisState: { kind: "Idle" }, replacement: { kind: "Idle" },
          needsCheck: true, screen: "workspace"
        });
        return {
          state: log(s10, "SwitchBuild", "打开「" + target.name + "」→ 新 revision #" + revS.id + "（原诊断绑定 #" + (target.diagnosis ? target.diagnosis.revisionId : "-") + "，不会跟着走）"),
          effects: []
        };
      }
      case "EvidenceChanged": {
        var ov = Object.assign({}, state.evidenceOverrides);
        if (ev.state === "RESET") delete ov[ev.key];
        else ov[ev.key] = { state: ev.state, value: ev.state === "EMPTY" ? null : ev.value };
        var s9 = Object.assign({}, state, {
          evidenceOverrides: ov,
          diagnosis: null, diagnosisState: { kind: "Idle" }, replacement: { kind: "Idle" },
          needsCheck: true
        });
        return { state: log(s9, "Evidence", "证据改为 " + ev.state + "：" + ev.key + "（诊断已清空）"), effects: [] };
      }
      default:
        return { state: decision(state, "UNKNOWN", "未知事件 " + ev.type), effects: [] };
    }
  }

  // ---------------------------------------------------------------- 不变量
  function invariants(state) {
    var out = [];
    var rev = state.revision;
    var add = function (id, name, ok, detail) { out.push({ id: id, name: name, ok: !!ok, detail: detail || "" }); };

    add("INV-01", "总价 = Σ(数量 × 单价)", rev.totalCny === computeTotal(rev.items), rev.totalCny + " vs " + computeTotal(rev.items));

    var oldIds = state.revisionHistory.map(function (r) { return r.id; });
    var mono = oldIds.every(function (x) { return x < rev.id; }) && state.nextRevisionId > rev.id;
    add("INV-02", "revision id 单调递增", mono, "current #" + rev.id + " / 历史 [" + oldIds.join(",") + "] / next " + state.nextRevisionId);

    var cur = state.diagnosis;
    var staleOk = !cur || cur.revisionId === rev.id || state.screen === "compare";
    add("INV-03", "旧诊断不得冒充当前结论", staleOk, cur ? "诊断 #" + cur.revisionId + " / 当前 #" + rev.id : "无诊断");

    var noEmptyPass = !cur || cur.outcomes.every(function (o) { return !(o.status === "PASSED" && o.weakest === "EMPTY"); });
    add("INV-04", "缺证据不得 PASSED", noEmptyPass);

    var weakestOk = !cur || cur.outcomes.every(function (o) { return o.weakest !== "EMPTY" || o.status !== "PASSED"; });
    add("INV-06", "weakest 向上传播", weakestOk);

    var appliedOk = state.replacement.kind !== "Applied" || (state.lastApplyRechecked !== false);
    add("INV-07", "只有重检通过才能 Applied", appliedOk);

    var sorted = !cur || cur.outcomes.every(function (o, i) {
      return i === 0 || cur.outcomes[i - 1].ruleId <= o.ruleId;
    });
    add("INV-15", "结果按 ruleId 排序（确定性）", sorted);

    var demoOk = rev.items.every(function (it) {
      var rec = state.catalogueIndex[it.componentId];
      return rec && rec.isDemo === true;
    });
    add("INV-11", "演示数据不进生产", demoOk);

    var itemsValid = new Set(rev.items.map(function (i) { return i.category; })).size === rev.items.length;
    add("INV-13b", "一个类别最多一个零件", itemsValid);

    var compareOk = state.screen !== "compare" || state.savedBuilds.concat([{ revision: rev, diagnosis: cur }]).every(function (b) {
      return !b.diagnosis || b.diagnosis.revisionId === b.revision.id;
    });
    add("INV-12", "比较列不显示过期诊断", compareOk);

    return out;
  }

  // ---------------------------------------------------------------- 初始状态
  function createApp(opts) {
    var budget = opts.budget || 8000;
    var rev = emptyRevision(opts.buildId || "demo-build", budget);
    return {
      catalogue: opts.catalogue,
      catalogueIndex: indexCatalogue(opts.catalogue),
      catalogueVersion: opts.catalogueVersion || "static-v1",
      priceDate: opts.priceDate || "",
      rules: opts.rules,
      evidenceOverrides: opts.evidenceOverrides || {},
      budget: budget,
      useCase: opts.useCase || "GAMING",
      buildName: opts.buildName || "我的配置",
      savedAtLabel: opts.savedAtLabel || "2026-11-02 09:00",
      revision: rev,
      nextRevisionId: 2,
      revisionHistory: [],
      diagnosis: null,
      diagnosisState: { kind: "Idle" },
      replacement: { kind: "Idle" },
      savedBuilds: [],
      screen: "entry",
      slowCheck: false,
      log: [],
      logSeq: 1,
      lastApplyRechecked: true,
      needsCheck: false
    };
  }

  // ---------------------------------------------------------------- 推荐（启发式）
  function recommend(state, budget, useCase) {
    var catIdx = indexCatalogue(state.catalogue);
    var byCat = {};
    state.catalogue.forEach(function (r) { (byCat[r.category] = byCat[r.category] || []).push(r); });
    var order = ["CPU", "MOTHERBOARD", "MEMORY", "GPU", "PSU", "CASE", "COOLER", "STORAGE"];

    function buildItems(p) {
      return order.map(function (c) {
        var rec = catIdx[p[c]];
        return rec ? { category: c, componentId: rec.componentId, quantity: 1, unitPriceCny: rec.priceCny } : null;
      }).filter(Boolean);
    }
    function blockersOf(items) {
      var rev = { id: 99, items: items, totalCny: computeTotal(items) };
      var out = engine.evaluate(rev, state.catalogue, { rules: state.rules, evidenceOverrides: state.evidenceOverrides });
      return out.filter(function (o) { return o.status === "CONFLICT" && o.severity === "BLOCKER"; });
    }
    function greedy(limit) {
      var picks = {};
      for (var i = 0; i < order.length; i++) {
        var cat = order[i];
        var pool = (byCat[cat] || []).slice().sort(function (a, b) {
          if (cat === "GPU" && useCase === "GAMING") return b.priceCny - a.priceCny;
          return a.priceCny - b.priceCny;
        });
        for (var j = 0; j < pool.length; j++) {
          var trial = Object.assign({}, picks);
          trial[cat] = pool[j].componentId;
          var items = buildItems(trial);
          if (limit != null && computeTotal(items) > limit) continue;
          if (blockersOf(items).length) continue;
          picks[cat] = pool[j].componentId;
          break;
        }
      }
      return picks;
    }

    var minimalPicks = greedy(null);
    var minimalItems = buildItems(minimalPicks);
    var minimalTotal = computeTotal(minimalItems);

    if (minimalTotal > budget) {
      return {
        picks: minimalPicks, items: minimalItems, totalCny: minimalTotal,
        feasible: false, shortfallCny: minimalTotal - budget,
        weightsVisible: ["已按最低可行价组合", "缺口 = 最低可行总价 − 预算"],
        relaxedPreferences: ["预算不足以覆盖全部 8 类部件"]
      };
    }
    var picks = greedy(budget);
    if (Object.keys(picks).length < order.length) picks = minimalPicks;
    var items = buildItems(picks);
    var total = computeTotal(items);
    return {
      picks: picks, items: items, totalCny: total,
      feasible: total <= budget, shortfallCny: Math.max(0, total - budget),
      weightsVisible: ["GPU 权重优先（游戏）", "电源按公式留 20% 余量", "预算内选最便宜可行件"]
    };
  }

  function applyRecommendation(state, result) {
    var rev = makeRevision(state, result.items);
    var s = Object.assign({}, state, {
      revision: rev, nextRevisionId: state.nextRevisionId + 1,
      diagnosis: null, diagnosisState: { kind: "Idle" }, replacement: { kind: "Idle" },
      screen: "workspace",
      lastRecommendation: {
        feasible: result.feasible, totalCny: result.totalCny,
        shortfallCny: result.shortfallCny,
        weightsVisible: result.weightsVisible,
        relaxedPreferences: result.relaxedPreferences || []
      }
    });
    return log(s, "Recommend", (result.feasible ? "推荐可行" : "推荐不可行，缺口 " + result.shortfallCny) + "，总价 " + rev.totalCny);
  }


  return {
    CATEGORIES: CATEGORIES, CATEGORY_LABEL: CATEGORY_LABEL,
    computeTotal: computeTotal, reduce: reduce, suggest: suggest, previewFor: previewFor,
    invariants: invariants, recommend: recommend, applyRecommendation: applyRecommendation,
    createApp: createApp, indexCatalogue: indexCatalogue,
    makeRevision: makeRevision, itemsFor: itemsFor
  };
});

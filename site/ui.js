/* 浏览器 UI 层：只负责把 WorkspaceUiState 渲染成界面，把点击变成 Intent。 */
(function (root) {
  "use strict";
  var Engine = root.PBEngine;
  var S = root.PBState;
  var DATA = root.PB_DATA;

  var DEFAULT_OVERRIDES = { "mb-demo-b650.biosGeneration": { state: "EMPTY", value: null } };

  var BUILD_A = {
    CPU: "cpu-demo-7600x", MOTHERBOARD: "mb-demo-b650", MEMORY: "mem-demo-ddr5-32",
    GPU: "gpu-demo-337", PSU: "psu-demo-750", CASE: "case-demo-320",
    COOLER: "cooler-demo-160", STORAGE: "storage-demo-nvme"
  };
  var BUILD_B = {
    CPU: "cpu-demo-7600x", MOTHERBOARD: "mb-demo-b650", MEMORY: "mem-demo-ddr5-32",
    PSU: "psu-demo-550", CASE: "case-demo-320", COOLER: "cooler-demo-160", STORAGE: "storage-demo-nvme"
  };
  var BUILD_B_NAME = "办公配置（无独显）";

  var SPEC = {
    CPU: ["socket", "tdpW"], MOTHERBOARD: ["chipset", "formFactor"], MEMORY: ["generation", "kitCapacityGb"],
    GPU: ["lengthMm", "thicknessMm"], PSU: ["continuousW"], CASE: ["maxGpuLengthMm", "supportedFormFactors"],
    COOLER: ["heightMm", "socketMounts"], STORAGE: ["interface"]
  };
  var SPEC_UNIT = {
    tdpW: "W", continuousW: "W", requiredW: "W",
    kitCapacityGb: "GB", moduleCapacityGb: "GB", maxMemoryGb: "GB",
    lengthMm: "mm", thicknessMm: "mm", maxGpuLengthMm: "mm", maxGpuThicknessMm: "mm",
    maxCoolerHeightMm: "mm", heightMm: "mm",
    slotWidth: "槽", maxGpuSlots: "槽",
    memorySlots: "个", moduleCount: "条", m2Slots: "个", sataPorts: "个", driveBays: "个"
  };
  var CAT_TAG = { CPU: "CPU", MOTHERBOARD: "MAIN", MEMORY: "RAM", GPU: "GPU", PSU: "PSU", CASE: "CASE", COOLER: "COOL", STORAGE: "SSD" };
  var RULE_TITLE = {
    CPU_SOCKET_MATCH: "CPU 插槽与主板一致", CPU_MB_CHIPSET_SUPPORT: "主板芯片组受 CPU 支持",
    CPU_MB_BIOS_SUPPORT: "主板 BIOS 够新", MEMORY_DDR_GENERATION: "内存代际匹配",
    MEMORY_SLOT_COUNT: "内存条数不超插槽", MEMORY_CAPACITY_LIMIT: "内存容量不超上限",
    GPU_CASE_LENGTH: "显卡长度不超过机箱限长", GPU_CASE_THICKNESS: "显卡厚度不超过机箱限厚",
    GPU_CASE_SLOT_WIDTH: "显卡槽宽不超机箱", PSU_CONTINUOUS_POWER: "电源功率满足估算负载",
    PSU_CONNECTORS: "电源接口覆盖显卡需求", COOLER_CASE_HEIGHT: "散热器高度不超机箱",
    COOLER_CPU_SOCKET: "散热器扣具支持 CPU 插槽", MB_CASE_FORM_FACTOR: "主板板型可装入机箱",
    STORAGE_MB_INTERFACE: "主板支持该硬盘接口"
  };
  var STATUS_TEXT = { PASSED: "通过", CONFLICT: "有冲突", UNVERIFIED: "未验证", MIXED: "冲突 + 未验证" };
  var REASON_TEXT = {
    MISSING_FIELD: "缺少必要字段，无法判断",
    SOURCE_CONFLICT: "两个来源的数据互相矛盾",
    INSUFFICIENT_DATA: "字段无法解析成数值",
    UNSUPPORTED_RULE: "未知规则类型"
  };

  var doc = null, els = null, app = null;
  var ui = { tab: "state", mode: null, partCat: null, pickerCat: null, compareIds: [], evidenceId: "mb-demo-b650", toast: null, toastTimer: null };

  // ------------------------------------------------------------ 小工具
  function el(tag, cls, text) {
    var n = doc.createElement(tag);
    if (cls) n.className = cls;
    if (text != null) n.textContent = String(text);
    return n;
  }
  function clear(n) { while (n.firstChild) n.removeChild(n.firstChild); }
  function money(n) { return "¥" + Number(n || 0).toLocaleString("zh-CN"); }
  function delta(n) { return (n > 0 ? "+" : n < 0 ? "−" : "±") + money(Math.abs(n)); }
  function onClick(node, fn) { node.addEventListener("click", fn); }
  function btn(label, cls, fn, act) {
    var b = el("button", "btn " + (cls || ""), label);
    if (act) b.dataset.act = act;
    b.type = "button";
    if (fn) onClick(b, fn);
    return b;
  }
  function pill(text, cls) { return el("span", "status-pill " + (cls || ""), text); }

  function worstState(rec) {
    if (!rec) return "EMPTY";
    var worst = "VERIFIED";
    var rank = { EMPTY: 0, REPORTED: 1, VERIFIED: 2 };
    Object.keys(rec.attributes || {}).forEach(function (k) {
      var f = rec.attributes[k];
      var ov = app.evidenceOverrides[rec.componentId + "." + k];
      var st = ov ? ov.state : f.state;
      if (f.conflict) st = "REPORTED";
      if (rank[st] < rank[worst]) worst = st;
    });
    return worst;
  }

  function fieldValue(rec, key) {
    var f = rec.attributes[key];
    if (!f) return "—";
    var ov = app.evidenceOverrides[rec.componentId + "." + key];
    if (ov && ov.state === "EMPTY") return "未知";
    var v = ov && ov.value != null ? ov.value : f.value;
    if (v == null) return "未知";
    return v + (SPEC_UNIT[key] ? " " + SPEC_UNIT[key] : "");
  }

  function specOf(rec) {
    if (!rec) return "";
    return (SPEC[rec.category] || []).map(function (k) { return fieldValue(rec, k); }).filter(function (v) { return v !== "—"; }).join(" · ");
  }

  function catLabel(c) { return S.CATEGORY_LABEL[c] || c; }
  function recOf(id) { return app.catalogueIndex[id]; }

  function displayName(rec) {
    if (!rec) return "未知零件";
    return rec.manufacturer + " " + rec.model;
  }

  function statusOf() {
    if (app.diagnosisState.kind === "Running") return { text: "检查中", cls: "status-UNVERIFIED" };
    if (app.diagnosisState.kind === "Failed") return { text: "检查失败", cls: "status-CONFLICT" };
    if (!app.diagnosis) return { text: app.needsCheck ? "需重新检查" : "未检查", cls: app.needsCheck ? "status-UNVERIFIED" : "" };
    var map = { PASSED: "通过", CONFLICT: "有冲突", UNVERIFIED: "未验证", MIXED: "冲突 + 未验证" };
    return { text: map[app.diagnosis.overall] || app.diagnosis.overall, cls: "status-" + app.diagnosis.overall };
  }

  // ------------------------------------------------------------ 入口
  function mount(docRef, containers) {
    doc = docRef; els = containers;
    app = S.createApp({
      catalogue: DATA.catalogue,
      catalogueVersion: DATA.catalogueVersion,
      priceDate: DATA.priceDate,
      rules: DATA.rules,
      evidenceOverrides: DEFAULT_OVERRIDES,
      buildName: "我的配置"
    });
    bindEvents();
    render();
    return api;
  }

  function bindEvents() {
    if (els.slow) {
      els.slow.addEventListener("change", function () {
        app = Object.assign({}, app, { slowCheck: !!els.slow.checked });
        toast(els.slow.checked ? "慢检查已开启：检查需 2.6 秒，期间编辑可验证旧结果被丢弃" : "慢检查已关闭");
      });
    }
    if (els.tabs) {
      els.tabs.addEventListener("click", function (e) {
        var t = e.target.closest("[data-tab]");
        if (!t) return;
        ui.tab = t.dataset.tab;
        Array.prototype.forEach.call(els.tabs.children, function (b) { b.classList.toggle("is-active", b.dataset.tab === ui.tab); });
        renderInspector();
      });
    }
    onClick(els.screen, function (e) {
      var a = e.target.closest("[data-act]");
      if (!a) return;
      handle(a.dataset.act, a, e);
    });
    onClick(els.sheet, function (e) {
      if (e.target === els.sheet) { closeSheet(); return; }
      var a = e.target.closest("[data-act]");
      if (!a) return;
      handle(a.dataset.act, a, e);
    });
    if (els.screen.addEventListener) {
      els.screen.addEventListener("change", function (e) {
        var a = e.target.closest("[data-act]");
        if (!a) return;
        if (a.dataset.act === "budget") dispatch({ type: "SetBudget", budget: Number(e.target.value) });
        if (a.dataset.act === "evidence") {
          var key = e.target.dataset.key;
          var recId = key.split(".")[0];
          var rec = recOf(recId);
          var path = key.slice(recId.length + 1);
          var f = rec && rec.attributes[path];
          dispatch({ type: "EvidenceChanged", key: key, state: e.target.value, value: f ? f.value : null });
        }
        if (a.dataset.act === "evidenceReset") {
          dispatch({ type: "EvidenceChanged", key: e.target.dataset.key, state: "RESET" });
        }
      });
      els.screen.addEventListener("input", function (e) {
        var a = e.target.closest("[data-act]");
        if (!a || a.dataset.act !== "budget") return;
        var label = els.screen.querySelector("#budget-num");
        if (label) label.textContent = money(e.target.value);
      });
    }
  }

  function closeSheet() { ui.mode = null; renderSheet(); }

  function toast(text) {
    ui.toast = text;
    render();
    if (ui.toastTimer) clearTimeout(ui.toastTimer);
    ui.toastTimer = setTimeout(function () { ui.toast = null; render(); }, 2600);
  }

  function dispatch(ev) {
    var r = S.reduce(app, ev);
    app = r.state;
    runEffects(r.effects);
    render();
  }

  function runEffects(effects) {
    (effects || []).forEach(function (ef) {
      if (ef.type === "RunDiagnosis") {
        var snap = ef.revision;
        var ovSnap = JSON.parse(JSON.stringify(app.evidenceOverrides));
        var delay = ef.slow ? 2600 : 620;
        setTimeout(function () {
          var diag = Engine.diagnose(snap, app.catalogue, {
            rules: app.rules, evidenceOverrides: ovSnap,
            catalogueVersion: app.catalogueVersion, now: "now"
          });
          dispatch({ type: "CheckCompleted", diagnosis: diag });
        }, delay);
      } else if (ef.type === "LoadCandidates") {
        setTimeout(function () {
          dispatch({ type: "CandidatesLoaded", result: S.suggest(app, ef.conflict) });
        }, 260);
      } else if (ef.type === "Recheck") {
        setTimeout(function () {
          var diag = Engine.diagnose(ef.revision, app.catalogue, {
            rules: app.rules, evidenceOverrides: app.evidenceOverrides,
            catalogueVersion: app.catalogueVersion, now: "recheck"
          });
          dispatch({ type: "RecheckCompleted", diagnosis: diag });
        }, 900);
      } else if (ef.type === "ShowText") {
        toast(ef.key);
      }
    });
  }

  function handle(act, node, e) {
    var d = node.dataset;
    if (act === "goto") { dispatch({ type: "Goto", screen: d.screen }); return; }
    if (act === "usecase") { dispatch({ type: "SetUseCase", useCase: d.useCase }); return; }
    if (act === "loadDemo") { dispatch({ type: "LoadDemo", picks: BUILD_A }); return; }
    if (act === "blank") { ui.mode = null; dispatch({ type: "LoadDemo", picks: {} }); return; }
    if (act === "recommend") {
      var res = S.recommend(app, app.budget, app.useCase);
      app = S.applyRecommendation(app, res);
      toast(res.feasible ? ("推荐完成：" + money(res.totalCny) + "，权重已展示") : ("预算不足，缺口 " + money(res.shortfallCny)));
      render();
      return;
    }
    if (act === "check") {
      if (!app.revision.items.length) { toast("空配置不能检查：先选几个零件"); return; }
      if (app.revision.totalCny > app.budget) toast("超预算了，但仍可以检查兼容性（预算是建议，不是规则）");
      dispatch({ type: "StartCheck" });
      return;
    }
    if (act === "save") { dispatch({ type: "SaveBuild" }); toast("已保存到「我的配置」"); return; }
    if (act === "undo") { dispatch({ type: "Undo" }); return; }
    if (act === "openPart") { ui.mode = "part"; ui.partCat = d.cat; renderSheet(); return; }
    if (act === "openPicker") { ui.mode = "picker"; ui.pickerCat = d.cat; renderSheet(); return; }
    if (act === "pick") {
      var cat = d.cat, id = d.id;
      dispatch({ type: app.revision.items.some(function (i) { return i.category === cat; }) ? "ReplaceComponent" : "AddComponent", componentId: id });
      ui.mode = null; renderSheet(); return;
    }
    if (act === "remove") { dispatch({ type: "RemoveComponent", category: d.cat }); ui.mode = null; renderSheet(); return; }
    if (act === "qty") {
      var it = app.revision.items.filter(function (i) { return i.category === d.cat; })[0];
      if (it) dispatch({ type: "SetQuantity", componentId: it.componentId, quantity: Number(d.q) });
      return;
    }
    if (act === "selectConflict") {
      dispatch({ type: "SelectConflict", conflictId: d.cid });
      ui.mode = null;
      renderSheet();
      return;
    }
    if (act === "preview") { dispatch({ type: "PreviewCandidate", componentId: d.id }); return; }
    if (act === "apply") { dispatch({ type: "Apply" }); return; }
    if (act === "dismiss") { dispatch({ type: "Dismiss" }); closeSheet(); return; }
    if (act === "cancel") { dispatch({ type: "CancelPreview" }); closeSheet(); return; }
    if (act === "close") { closeSheet(); return; }
    if (act === "compareToggle") {
      var id2 = d.id;
      var idx = ui.compareIds.indexOf(id2);
      if (idx >= 0) ui.compareIds.splice(idx, 1);
      else if (ui.compareIds.length < 3) ui.compareIds.push(id2);
      else { toast("最多并排 3 个配置"); return; }
      render();
      return;
    }
    if (act === "evidenceSelect") { ui.evidenceId = d.id; renderInspector(); return; }
  }

  // ------------------------------------------------------------ 渲染
  function render() {
    renderScreen();
    renderSheet();
    renderInspector();
    updateChips();
  }

  function updateChips() {
    if (els.chipCatalogue) els.chipCatalogue.textContent = app.catalogueVersion;
    if (els.chipPrice) els.chipPrice.textContent = "价格日 " + app.priceDate;
  }

  function renderScreen() {
    clear(els.screen);
    if (ui.toast) els.screen.appendChild(el("div", "banner banner-info", ui.toast));
    if (app.screen === "entry") renderEntry();
    else if (app.screen === "workspace") renderWorkspace();
    else if (app.screen === "diagnostics") renderDiagnostics();
    else if (app.screen === "compare") renderCompare();
  }

  function renderEntry() {
    var wrap = el("div", "stack");

    var hero = el("div", "hero");
    hero.appendChild(el("h2", "", "先定预算，再谈配置"));
    hero.appendChild(el("p", "", "我们不会说「一定兼容」。每个结论都带证据：厂商确认、报告数据，或者「没人确认」。"));
    wrap.appendChild(hero);

    var budgetCard = el("div", "card");
    budgetCard.appendChild(el("div", "section-title", "预算"));
    var bd = el("div", "budget-display");
    var num = el("span", "num", money(app.budget)); num.id = "budget-num";
    bd.appendChild(num);
    bd.appendChild(el("span", "unit", "人民币 · 含税参考价"));
    budgetCard.appendChild(bd);
    var range = el("input");
    range.type = "range"; range.min = "3000"; range.max = "20000"; range.step = "100";
    range.value = String(app.budget);
    range.dataset.act = "budget";
    budgetCard.appendChild(range);
    var hint = el("div", "tiny dim", "拖动后松手生效；预算决定「推荐」能买到什么。");
    budgetCard.appendChild(hint);
    wrap.appendChild(budgetCard);

    var uc = el("div", "card");
    uc.appendChild(el("div", "section-title", "用途"));
    var pills = el("div", "pills");
    [["GAMING", "游戏"], ["STUDIO", "生产力"], ["STUDY_OFFICE", "办公 / 学习"]].forEach(function (p) {
      var b = el("button", "pill" + (app.useCase === p[0] ? " is-active" : ""), p[1]);
      b.type = "button"; b.dataset.act = "usecase"; b.dataset.useCase = p[0];
      pills.appendChild(b);
    });
    uc.appendChild(pills);
    wrap.appendChild(uc);

    var actions = el("div", "stack");
    actions.appendChild(btn("按预算推荐一套（启发式）", "btn-primary btn-block", null, "recommend"));
    actions.appendChild(btn("载入答辩演示配置 · ¥7,800", "btn-block", null, "loadDemo"));
    actions.appendChild(btn("从空白开始", "btn-ghost btn-block", null, "blank"));
    wrap.appendChild(actions);

    var note = el("div", "banner");
    note.appendChild(el("span", "", "演示"));
    note.appendChild(el("div", "grow", "目录是静态演示数据（16 个零件、15 条规则），价格是 2026-10-31 的参考价，不是实时价。"));
    wrap.appendChild(note);

    els.screen.appendChild(wrap);
  }

  function partRow(cat) {
    var item = app.revision.items.filter(function (i) { return i.category === cat; })[0];
    var rec = item ? recOf(item.componentId) : null;
    var row = el("div", "part" + (item ? "" : " is-empty"));
    row.dataset.act = "openPart";
    row.dataset.cat = cat;
    row.appendChild(el("div", "part-cat", CAT_TAG[cat]));

    var mid = el("div", "grow");
    if (rec) {
      mid.appendChild(el("div", "part-name ellipsis", displayName(rec)));
      mid.appendChild(el("div", "part-spec ellipsis", specOf(rec)));
    } else {
      mid.appendChild(el("div", "part-name", "未选择"));
      mid.appendChild(el("div", "part-spec", "点一下添加" + catLabel(cat)));
    }
    row.appendChild(mid);

    var right = el("div", "part-right");
    if (rec) {
      var ev = el("span", "ev ev-" + worstState(rec));
      ev.title = "该零件最弱证据：" + worstState(rec);
      right.appendChild(ev);
      right.appendChild(el("span", "part-price", money(item.unitPriceCny * item.quantity)));
      if (item.quantity > 1) right.appendChild(el("span", "tiny dim", "×" + item.quantity));
    } else {
      right.appendChild(el("span", "dim", "＋"));
    }
    row.appendChild(right);
    return row;
  }

  function renderWorkspace() {
    var wrap = el("div", "stack");

    var st = statusOf();
    var card = el("div", "total-card");
    var l1 = el("div", "total-line");
    l1.appendChild(el("span", "muted tiny", "当前配置总价"));
    var over = app.revision.totalCny > app.budget;
    l1.appendChild(el("span", "total-num" + (over ? " over" : ""), money(app.revision.totalCny)));
    card.appendChild(l1);

    var l2 = el("div", "row-between tiny dim");
    l2.appendChild(el("span", "", "预算 " + money(app.budget) + (over ? "（已超支 " + money(app.revision.totalCny - app.budget) + "）" : "，余量 " + money(app.budget - app.revision.totalCny))));
    l2.appendChild(el("span", "mono", "rev #" + app.revision.id));
    card.appendChild(l2);

    var l3 = el("div", "row-between");
    l3.style.marginTop = "8px";
    var left = el("div", "row");
    left.appendChild(pill(st.text, st.cls));
    if (app.diagnosis) left.appendChild(el("span", "tiny dim", "分数 " + app.diagnosis.score + " · 风险 " + app.diagnosis.riskLevel));
    l3.appendChild(left);
    var back = btn("返回", "btn-ghost btn-sm", null, "goto");
    back.dataset.screen = "entry";
    l3.appendChild(back);
    card.appendChild(l3);
    wrap.appendChild(card);

    if (app.needsCheck && app.revision.items.length) {
      var warn = el("div", "banner");
      warn.appendChild(el("span", "", "过期"));
      warn.appendChild(el("div", "grow", "配置已修改，之前的结论不再适用 —— 诊断不会自动重跑，请重新检查。"));
      wrap.appendChild(warn);
    }
    if (app.diagnosisState.kind === "Running") {
      var run = el("div", "card");
      run.appendChild(el("div", "row-between", "正在检查…"));
      var pg = el("div", "progress"); pg.appendChild(el("i"));
      run.appendChild(pg);
      run.appendChild(el("div", "tiny dim", "检查期间可以继续编辑；迟到的结果会被丢弃（revision 不匹配）。"));
      wrap.appendChild(run);
    }

    if (app.lastRecommendation) {
      var recCard = el("div", "card");
      recCard.style.marginBottom = "10px";
      var rt = el("div", "section-title", "推荐依据（权重可见）");
      recCard.appendChild(rt);
      app.lastRecommendation.weightsVisible.forEach(function (w) {
        var line = el("div", "tiny dim", "· " + w);
        recCard.appendChild(line);
      });
      if (!app.lastRecommendation.feasible) {
        recCard.appendChild(el("div", "tiny", "预算缺口 " + money(app.lastRecommendation.shortfallCny) + "：没有悄悄放宽硬约束。"));
      }
      wrap.appendChild(recCard);
    }

    var list = el("div");
    S.CATEGORIES.forEach(function (cat) { list.appendChild(partRow(cat)); });
    wrap.appendChild(list);

    var actions = el("div", "stack");
    actions.style.marginTop = "12px";
    actions.appendChild(btn("检查兼容性", "btn-primary btn-block", null, "check"));
    var row2 = el("div", "row");
    row2.style.gap = "8px";
    var save = btn("保存配置", "btn", null, "save"); save.classList.add("grow");
    var cmp = btn("比较", "btn", null, "goto"); cmp.dataset.screen = "compare"; cmp.classList.add("grow");
    var undo = btn("撤销", "btn", null, "undo"); undo.disabled = app.revisionHistory.length === 0;
    row2.appendChild(save); row2.appendChild(cmp); row2.appendChild(undo);
    actions.appendChild(row2);
    wrap.appendChild(actions);

    els.screen.appendChild(wrap);
  }

  function ruleRow(o) {
    var box = el("div", "rule");
    var head = el("div", "rule-head");
    head.appendChild(el("div", "rule-id", o.ruleId));
    var tagCls = o.status === "CONFLICT" ? "tag-" + o.severity : "tag-" + o.status;
    var tagText = o.status === "CONFLICT" ? o.severity : (o.status === "PASSED" ? "通过" : "未验证");
    head.appendChild(el("span", "tag " + tagCls, tagText));
    box.appendChild(head);
    box.appendChild(el("div", "rule-title", RULE_TITLE[o.ruleId] || o.ruleId));

    if (o.status === "CONFLICT") {
      var v = el("div", "rule-values");
      v.appendChild(el("span", "val val-bad", o.left.value + (SPEC_UNIT[o.left.path] ? " " + SPEC_UNIT[o.left.path] : "")));
      v.appendChild(el("span", "dim", "vs"));
      v.appendChild(el("span", "val", o.right.value + (SPEC_UNIT[o.right.path] ? " " + SPEC_UNIT[o.right.path] : "")));
      v.appendChild(el("span", "tiny dim", "· " + (o.weakest === "VERIFIED" ? "厂商确认" : o.weakest === "REPORTED" ? "报告数据" : "证据缺失")));
      box.appendChild(v);
      var b = btn("看看能换成什么", "btn-sm", null, "selectConflict");
      b.dataset.cid = o.conflictId || "";
      b.style.marginTop = "8px";
      box.appendChild(b);
    } else if (o.status === "UNVERIFIED") {
      var v2 = el("div", "rule-values");
      v2.appendChild(el("span", "val val-warn", REASON_TEXT[o.reason] || o.reason));
      if (o.missingField) v2.appendChild(el("span", "tiny dim", "· 缺少 " + (o.missingField.componentId ? o.missingField.componentId + "." : "") + (o.missingField.fieldPath || o.missingField)));
      box.appendChild(v2);
    }
    return box;
  }

  function renderDiagnostics() {
    var wrap = el("div", "stack");
    var top = el("div", "row-between");
    var back = btn("← 配置", "btn-ghost btn-sm", null, "goto"); back.dataset.screen = "workspace";
    top.appendChild(back);
    var re = btn("重新检查", "btn-sm", null, "check");
    top.appendChild(re);
    wrap.appendChild(top);

    if (!app.diagnosis) {
      if (app.diagnosisState.kind === "Running") {
        var card0 = el("div", "card");
        card0.appendChild(el("div", "", "正在检查…"));
        var pg = el("div", "progress"); pg.appendChild(el("i"));
        card0.appendChild(pg);
        wrap.appendChild(card0);
      } else {
        var empty = el("div", "banner banner-info");
        empty.appendChild(el("span", "", "提示"));
        empty.appendChild(el("div", "grow", "还没有诊断结果。回到配置页点「检查兼容性」。"));
        wrap.appendChild(empty);
      }
      els.screen.appendChild(wrap);
      return;
    }

    if (app.diagnosisState.kind === "Running") {
      var running = el("div", "card");
      running.appendChild(el("div", "", "正在重新检查…（旧结论暂时保留，新结果不会覆盖别的 revision）"));
      var pg3 = el("div", "progress"); pg3.appendChild(el("i"));
      running.appendChild(pg3);
      wrap.appendChild(running);
    }

    var d = app.diagnosis;
    var stale = d.revisionId !== app.revision.id;
    var card = el("div", "score-card");
    var st = el("div", "score-top");
    var left = el("div");
    left.appendChild(el("div", "tiny dim", "兼容性结论"));
    left.appendChild(el("div", "score-num", d.score + " 分"));
    left.appendChild(el("div", "tiny dim", "风险 " + d.riskLevel + " · rev #" + d.revisionId + " · " + d.outcomes.length + " 条规则"));
    st.appendChild(left);
    st.appendChild(pill(STATUS_TEXT[d.overall] || d.overall, "status-" + d.overall));
    card.appendChild(st);

    var counts = el("div", "counts");
    [["冲突", d.counts.conflict, "bad"], ["未验证", d.counts.unverified, "warn"], ["通过", d.counts.passed, "ok"]].forEach(function (c) {
      var box = el("div", "count");
      var b = el("b", "", c[1]);
      b.style.color = c[2] === "bad" ? "var(--bad)" : c[2] === "warn" ? "var(--warn)" : "var(--ok)";
      box.appendChild(b);
      box.appendChild(el("span", "", c[0]));
      counts.appendChild(box);
    });
    card.appendChild(counts);
    wrap.appendChild(card);

    if (stale) {
      var b2 = el("div", "banner");
      b2.appendChild(el("span", "", "过期"));
      b2.appendChild(el("div", "grow", "这份结论属于 revision #" + d.revisionId + "，当前是 #" + app.revision.id + "。请重新检查。"));
      wrap.appendChild(b2);
    }

    var conflicts = d.outcomes.filter(function (o) { return o.status === "CONFLICT"; });
    var unv = d.outcomes.filter(function (o) { return o.status === "UNVERIFIED"; });
    var passed = d.outcomes.filter(function (o) { return o.status === "PASSED"; });

    if (conflicts.length) {
      wrap.appendChild(el("div", "section-title", "冲突 " + conflicts.length));
      conflicts.forEach(function (o) { wrap.appendChild(ruleRow(o)); });
    }
    if (unv.length) {
      wrap.appendChild(el("div", "section-title", "未验证 " + unv.length));
      unv.forEach(function (o) { wrap.appendChild(ruleRow(o)); });
      var note = el("div", "banner banner-info");
      note.appendChild(el("span", "", "为什么"));
      note.appendChild(el("div", "grow", "这些字段没有厂商确认的数据。按设计，缺证据只能回答「未验证」，不会当成通过。"));
      wrap.appendChild(note);
    }
    if (passed.length) {
      wrap.appendChild(el("div", "section-title", "通过 " + passed.length));
      passed.forEach(function (o) { wrap.appendChild(ruleRow(o)); });
    }
    els.screen.appendChild(wrap);
  }

  // ------------------------------------------------------------ 比较
  function buildColumns() {
    var cols = [];
    app.savedBuilds.forEach(function (b) {
      cols.push({ id: b.id, name: b.name, revision: b.revision, diagnosis: b.diagnosis, kind: "saved" });
    });
    cols.push({ id: "current", name: "当前编辑", revision: app.revision, diagnosis: app.diagnosis, kind: "current" });
    return cols;
  }

  function statOf(col) {
    var rev = col.revision, diag = col.diagnosis;
    var stale = !!diag && diag.revisionId !== rev.id;
    var valid = diag && !stale;
    var idx = app.catalogueIndex;
    var gpu = rev.items.filter(function (i) { return i.category === "GPU"; })[0];
    var cs = rev.items.filter(function (i) { return i.category === "CASE"; })[0];
    var mem = rev.items.filter(function (i) { return i.category === "MEMORY"; })[0];
    var mb = rev.items.filter(function (i) { return i.category === "MOTHERBOARD"; })[0];
    var gpuMargin = null, memMargin = null, psuMargin = null;
    if (gpu && cs && idx[gpu.componentId] && idx[cs.componentId]) {
      gpuMargin = idx[cs.componentId].attributes.maxGpuLengthMm.numeric - idx[gpu.componentId].attributes.lengthMm.numeric;
    }
    if (mem && mb && idx[mem.componentId] && idx[mb.componentId]) {
      memMargin = idx[mb.componentId].attributes.maxMemoryGb.numeric - idx[mem.componentId].attributes.kitCapacityGb.numeric * mem.quantity;
    }
    if (valid) {
      var o = diag.outcomes.filter(function (x) { return x.ruleId === "PSU_CONTINUOUS_POWER"; })[0];
      if (o && o.right && o.right.numeric != null && o.left && o.left.numeric != null) psuMargin = o.left.numeric - o.right.numeric;
    }
    return { stale: stale, valid: valid, gpuMargin: gpuMargin, memMargin: memMargin, psuMargin: psuMargin, diag: valid ? diag : null };
  }

  function renderCompare() {
    var cols = buildColumns();
    if (!cols.some(function (c) { return ui.compareIds.indexOf(c.id) >= 0; })) {
      ui.compareIds = cols.map(function (c) { return c.id; }).slice(0, 3);
    }
    var selected = cols.filter(function (c) { return ui.compareIds.indexOf(c.id) >= 0; });

    var wrap = el("div", "stack");
    var top = el("div", "row-between");
    var back = btn("← 配置", "btn-ghost btn-sm", null, "goto"); back.dataset.screen = "workspace";
    top.appendChild(back);
    top.appendChild(el("span", "tiny dim", "最多并排 3 个"));
    wrap.appendChild(top);

    var pills = el("div", "pills");
    cols.forEach(function (c) {
      var b = el("button", "pill" + (ui.compareIds.indexOf(c.id) >= 0 ? " is-active" : ""), c.name);
      b.type = "button"; b.dataset.act = "compareToggle"; b.dataset.id = c.id;
      pills.appendChild(b);
    });
    wrap.appendChild(pills);

    if (selected.length < 2) {
      var need = el("div", "banner banner-info");
      need.appendChild(el("span", "", "提示"));
      need.appendChild(el("div", "grow", "至少选 2 个配置才能比较。"));
      wrap.appendChild(need);
      els.screen.appendChild(wrap);
      return;
    }

    var table = el("div", "cmp");
    var gridSpec = "96px " + selected.map(function () { return "minmax(0, 1fr)"; }).join(" ");

    function headRow() {
      var r = el("div", "cmp-row cmp-head");
      r.style.gridTemplateColumns = gridSpec;
      r.appendChild(el("div", "cmp-label", "项目"));
      selected.forEach(function (c) {
        var cell = el("div", "cmp-val");
        cell.appendChild(el("div", "ellipsis", c.name));
        if (c.kind === "saved") {
          var open = el("button", "btn btn-ghost btn-sm", "打开");
          open.type = "button";
          open.addEventListener("click", function () { dispatch({ type: "SwitchBuild", buildId: c.id }); });
          cell.appendChild(open);
        }
        r.appendChild(cell);
      });
      return r;
    }
    function row(label, fn) {
      var r = el("div", "cmp-row");
      r.style.gridTemplateColumns = gridSpec;
      r.appendChild(el("div", "cmp-label", label));
      selected.forEach(function (c) { r.appendChild(el("div", "cmp-val", fn(c))); });
      return r;
    }

    table.appendChild(headRow());
    table.appendChild(row("总价", function (c) { return money(c.revision.totalCny); }));
    table.appendChild(row("状态", function (c) {
      var st = statOf(c);
      if (!st.valid) return st.stale ? "诊断过期" : "未检查";
      return STATUS_TEXT[st.diag.overall] || st.diag.overall;
    }));
    table.appendChild(row("分数", function (c) {
      var st = statOf(c);
      return st.valid ? String(st.diag.score) : "—";
    }));
    table.appendChild(row("冲突", function (c) {
      var st = statOf(c);
      return st.valid ? String(st.diag.counts.conflict) : "—";
    }));
    table.appendChild(row("未验证", function (c) {
      var st = statOf(c);
      return st.valid ? String(st.diag.counts.unverified) : "—";
    }));
    table.appendChild(row("显卡余量", function (c) {
      var st = statOf(c);
      return st.gpuMargin == null ? "—" : st.gpuMargin + " mm";
    }));
    table.appendChild(row("内存余量", function (c) {
      var st = statOf(c);
      return st.memMargin == null ? "—" : st.memMargin + " GB";
    }));
    table.appendChild(row("电源余量", function (c) {
      var st = statOf(c);
      return st.psuMargin == null ? "—" : st.psuMargin + " W";
    }));
    wrap.appendChild(table);

    var staleCols = selected.filter(function (c) { return statOf(c).stale; });
    if (staleCols.length) {
      var b = el("div", "banner");
      b.appendChild(el("span", "", "过期"));
      b.appendChild(el("div", "grow", staleCols.map(function (c) { return c.name; }).join("、") + " 的诊断不属于当前 revision，已按「请重新检查」处理，不显示旧结论。"));
      wrap.appendChild(b);
    }
    var note = el("div", "tiny dim", "");
    note.textContent = "比较只读取已保存的诊断，不会重新跑规则；因此不会出现「同一份配置两种结论」。";
    wrap.appendChild(note);

    if (!app.savedBuilds.length) {
      var tip = el("div", "banner banner-info");
      tip.appendChild(el("span", "", "提示"));
      tip.appendChild(el("div", "grow", "还没保存过配置。先在配置页点「保存配置」，或载入演示配置后保存一次。"));
      wrap.appendChild(tip);
    }

    els.screen.appendChild(wrap);
  }

  // ------------------------------------------------------------ 弹出层
  function renderSheet() {
    clear(els.sheet);
    var show = ui.mode === "part" || ui.mode === "picker" || app.replacement.kind !== "Idle";
    if (!show) { els.sheet.hidden = true; return; }
    els.sheet.hidden = false;

    var panel = el("div", "sheet-panel");
    panel.appendChild(el("div", "sheet-grab"));

    if (ui.mode === "part") renderPartSheet(panel);
    else if (ui.mode === "picker") renderPicker(panel);
    else renderReplacement(panel);

    els.sheet.appendChild(panel);
  }

  function renderPartSheet(panel) {
    var cat = ui.partCat;
    var item = app.revision.items.filter(function (i) { return i.category === cat; })[0];
    var rec = item ? recOf(item.componentId) : null;
    panel.appendChild(el("h3", "", catLabel(cat)));
    panel.appendChild(el("div", "tiny dim", rec ? displayName(rec) : "还没有选择"));

    if (rec) {
      var card = el("div", "card");
      card.style.marginTop = "10px";
      var kv = el("div", "kv");
      Object.keys(rec.attributes).slice(0, 6).forEach(function (k) {
        kv.appendChild(el("dt", "", k));
        var f = rec.attributes[k];
        var ov = app.evidenceOverrides[rec.componentId + "." + k];
        var v = ov && ov.state === "EMPTY" ? "未知" : (ov && ov.value != null ? ov.value : f.value);
        var dd = el("dd", "", (v == null ? "未知" : v) + "  ·  " + (ov ? ov.state : f.state));
        if (f.conflict) dd.textContent = dd.textContent + " · 来源冲突";
        kv.appendChild(dd);
      });
      card.appendChild(kv);
      panel.appendChild(card);

      var qtyRow = el("div", "row-between");
      qtyRow.style.margin = "12px 0 6px";
      qtyRow.appendChild(el("span", "tiny dim", "数量（每套件的数量，会影响总价与计数类规则）"));
      var q = el("div", "qty");
      var minus = el("button", "", "−"); minus.type = "button"; minus.dataset.act = "qty"; minus.dataset.cat = cat; minus.dataset.q = String(Math.max(1, item.quantity - 1));
      var plus = el("button", "", "＋"); plus.type = "button"; plus.dataset.act = "qty"; plus.dataset.cat = cat; plus.dataset.q = String(item.quantity + 1);
      q.appendChild(minus); q.appendChild(el("span", "", item.quantity)); q.appendChild(plus);
      qtyRow.appendChild(q);
      panel.appendChild(qtyRow);
    }

    var bar = el("div", "row");
    bar.style.marginTop = "12px";
    var change = btn(rec ? "更换" : "选择", "btn-primary", null, "openPicker");
    change.dataset.cat = cat;
    bar.appendChild(change);
    if (rec) {
      var rm = btn("移除", "btn-danger", null, "remove");
      rm.dataset.cat = cat;
      bar.appendChild(rm);
    }
    var close = btn("关闭", "btn-ghost", null, "close");
    bar.appendChild(close);
    panel.appendChild(bar);
  }

  function renderPicker(panel) {
    var cat = ui.pickerCat;
    var current = app.revision.items.filter(function (i) { return i.category === cat; })[0];
    panel.appendChild(el("h3", "", "选择" + catLabel(cat)));
    panel.appendChild(el("div", "tiny dim", "目录按参考价排序；替换后需要重新检查兼容性。"));

    var list = el("div");
    list.style.marginTop = "10px";
    app.catalogue.filter(function (r) { return r.category === cat; }).forEach(function (r) {
      var box = el("div", "cand");
      var top = el("div", "cand-top");
      var left = el("div", "grow");
      left.appendChild(el("div", "cand-name", displayName(r)));
      left.appendChild(el("div", "cand-meta", specOf(r)));
      top.appendChild(left);
      top.appendChild(el("span", "cand-delta", money(r.priceCny)));
      box.appendChild(top);

      var evState = worstState(r);
      var meta = el("div", "row-between");
      meta.style.marginTop = "8px";
      var evRow = el("div", "row tiny dim");
      evRow.appendChild(el("span", "ev ev-" + evState));
      evRow.appendChild(el("span", "", "证据：" + (evState === "VERIFIED" ? "厂商确认" : evState === "REPORTED" ? "报告数据" : "存在缺失字段")));
      meta.appendChild(evRow);

      var pick = btn(current && current.componentId === r.componentId ? "当前" : "选它", "btn-sm", null, "pick");
      pick.dataset.cat = cat; pick.dataset.id = r.componentId;
      if (current && current.componentId === r.componentId) pick.disabled = true;
      meta.appendChild(pick);
      box.appendChild(meta);
      list.appendChild(box);
    });
    panel.appendChild(list);

    var bar = el("div", "row");
    bar.style.marginTop = "10px";
    bar.appendChild(btn("返回", "btn-ghost", null, "close"));
    panel.appendChild(bar);
  }

  function renderReplacement(panel) {
    var rp = app.replacement;

    if (rp.kind === "Loading") {
      panel.appendChild(el("h3", "", "正在筛选候选"));
      panel.appendChild(el("div", "tiny dim", "先过硬约束，再排序；会引入 BLOCKER 的候选直接过滤掉。"));
      var pg = el("div", "progress"); pg.appendChild(el("i"));
      panel.appendChild(pg);
      return;
    }

    if (rp.kind === "NoCandidate") {
      panel.appendChild(el("h3", "", "没有可行的替代件"));
      var b = el("div", "banner");
      b.appendChild(el("span", "", "原因"));
      b.appendChild(el("div", "grow", rp.reason.message));
      panel.appendChild(b);
      var kv = el("div", "kv");
      kv.appendChild(el("dt", "", "卡在哪条"));
      kv.appendChild(el("dd", "", rp.reason.blockingConstraint));
      kv.appendChild(el("dt", "", "冲突"));
      kv.appendChild(el("dd", "", rp.conflict.ruleId));
      panel.appendChild(kv);
      panel.appendChild(el("div", "tiny dim", "我们没有「放宽硬约束直到能装上」这个选项。"));
      var bar0 = el("div", "row"); bar0.style.marginTop = "12px";
      bar0.appendChild(btn("知道了", "btn", null, "dismiss"));
      panel.appendChild(bar0);
      return;
    }

    var conflict = rp.conflict;
    panel.appendChild(el("h3", "", "替换建议"));
    panel.appendChild(el("div", "tiny dim", RULE_TITLE[conflict.ruleId] || conflict.ruleId));
    var summary = el("div", "rule-values");
    summary.appendChild(el("span", "val val-bad", conflict.left.value));
    summary.appendChild(el("span", "dim", "vs"));
    summary.appendChild(el("span", "val", conflict.right.value));
    panel.appendChild(summary);

    if (rp.kind === "Candidates") {
      if (!rp.candidates.length) {
        panel.appendChild(el("div", "tiny dim", "没有候选。"));
      }
      rp.candidates.forEach(function (c) {
        var box = el("div", "cand");
        var top = el("div", "cand-top");
        var left = el("div", "grow");
        left.appendChild(el("div", "cand-name", "#" + c.rank + "  " + c.model));
        left.appendChild(el("div", "cand-meta", "替换后总价 " + money(c.totalAfterCny) + (c.affectsOtherRules.length ? " · 影响 " + c.affectsOtherRules.length + " 条其它规则" : "")));
        top.appendChild(left);
        top.appendChild(el("span", "cand-delta" + (c.priceDeltaCny < 0 ? " neg" : ""), delta(c.priceDeltaCny)));
        box.appendChild(top);
        var actions = el("div", "row");
        actions.style.marginTop = "8px";
        var pv = btn("预览", "btn-sm", null, "preview");
        pv.dataset.id = c.componentId;
        actions.appendChild(pv);
        box.appendChild(actions);
        panel.appendChild(box);
      });
      var bar = el("div", "row"); bar.style.marginTop = "10px";
      bar.appendChild(btn("取消", "btn-ghost", null, "cancel"));
      panel.appendChild(bar);
      return;
    }

    if (rp.kind === "Previewing") {
      var pr = rp.previewRevision;
      var card = el("div", "card");
      card.style.marginTop = "10px";
      var kv2 = el("div", "kv");
      kv2.appendChild(el("dt", "", "候选"));
      kv2.appendChild(el("dd", "", rp.candidate.model));
      kv2.appendChild(el("dt", "", "总价"));
      kv2.appendChild(el("dd", "", money(app.revision.totalCny) + " → " + money(pr.totalCny) + "（" + delta(pr.totalCny - app.revision.totalCny) + "）"));
      kv2.appendChild(el("dt", "", "预算"));
      kv2.appendChild(el("dd", "", (pr.totalCny <= app.budget ? "仍在预算内" : "超出预算 " + money(pr.totalCny - app.budget))));
      card.appendChild(kv2);
      panel.appendChild(card);
      panel.appendChild(el("div", "tiny dim", "预览不会写入配置；点「应用」会先做一次全量重检。"));

      var bar2 = el("div", "row"); bar2.style.marginTop = "12px";
      var apply = btn("应用（全量重检）", "btn-primary", null, "apply");
      bar2.appendChild(apply);
      bar2.appendChild(btn("取消", "btn-ghost", null, "cancel"));
      panel.appendChild(bar2);
      return;
    }

    if (rp.kind === "Rechecking") {
      panel.appendChild(el("h3", "", "全量重检中"));
      panel.appendChild(el("div", "tiny dim", "替换会改变功耗、尺寸与接口，必须重新检查全部规则。"));
      var pg2 = el("div", "progress"); pg2.appendChild(el("i"));
      panel.appendChild(pg2);
      return;
    }

    if (rp.kind === "Applied") {
      panel.appendChild(el("h3", "", "已应用"));
      var c2 = el("div", "card");
      c2.style.marginTop = "10px";
      var kv3 = el("div", "kv");
      kv3.appendChild(el("dt", "", "新 revision"));
      kv3.appendChild(el("dd", "", "#" + app.revision.id));
      kv3.appendChild(el("dt", "", "总价"));
      kv3.appendChild(el("dd", "", money(app.revision.totalCny)));
      kv3.appendChild(el("dt", "", "结论"));
      kv3.appendChild(el("dd", "", app.diagnosis ? app.diagnosis.overall : "—"));
      c2.appendChild(kv3);
      panel.appendChild(c2);
      if (app.diagnosis) {
        var unv = app.diagnosis.outcomes.filter(function (o) { return o.status === "UNVERIFIED"; });
        if (unv.length) {
          var b3 = el("div", "banner banner-info");
          b3.appendChild(el("span", "", "注意"));
          b3.appendChild(el("div", "grow", "冲突解决了，但仍有 " + unv.length + " 条未验证 —— 修一个冲突不等于「全部兼容」。"));
          panel.appendChild(b3);
        }
      }
      var bar3 = el("div", "row"); bar3.style.marginTop = "12px";
      bar3.appendChild(btn("查看诊断", "btn-primary", null, "dismiss"));
      panel.appendChild(bar3);
      return;
    }
  }

  // ------------------------------------------------------------ 检查器
  function renderInspector() {
    clear(els.inspector);
    if (ui.tab === "state") return renderStateTab();
    if (ui.tab === "invariants") return renderInvariants();
    if (ui.tab === "log") return renderLog();
    if (ui.tab === "evidence") return renderEvidence();
    return renderAbout();
  }

  function kvRow(dl, k, v) {
    dl.appendChild(el("dt", "", k));
    dl.appendChild(el("dd", "", v));
  }

  function renderStateTab() {
    var dl = el("dl", "kv");
    kvRow(dl, "screen", app.screen);
    kvRow(dl, "revision.id", "#" + app.revision.id);
    kvRow(dl, "revision.totalCny", money(app.revision.totalCny));
    kvRow(dl, "budget", money(app.budget));
    kvRow(dl, "items", app.revision.items.length + " 类");
    kvRow(dl, "diagnosisState", app.diagnosisState.kind);
    kvRow(dl, "diagnosis.rev", app.diagnosis ? "#" + app.diagnosis.revisionId : "null");
    kvRow(dl, "diagnosis.overall", app.diagnosis ? app.diagnosis.overall : "null");
    kvRow(dl, "replacement", app.replacement.kind);
    kvRow(dl, "needsCheck", String(!!app.needsCheck));
    kvRow(dl, "nextRevisionId", String(app.nextRevisionId));
    kvRow(dl, "savedBuilds", String(app.savedBuilds.length));
    els.inspector.appendChild(dl);

    var json = {
      revision: { id: app.revision.id, totalCny: app.revision.totalCny, items: app.revision.items.length },
      diagnosisState: app.diagnosisState,
      diagnosis: app.diagnosis ? {
        revisionId: app.diagnosis.revisionId, overall: app.diagnosis.overall,
        score: app.diagnosis.score, counts: app.diagnosis.counts
      } : null,
      replacement: { kind: app.replacement.kind },
      needsCheck: app.needsCheck
    };
    var pre = el("pre", "json", JSON.stringify(json, null, 2));
    pre.style.marginTop = "10px";
    els.inspector.appendChild(pre);
  }

  function renderInvariants() {
    var list = S.invariants(app);
    var ok = list.filter(function (x) { return x.ok; }).length;
    var head = el("div", "row-between");
    head.appendChild(el("strong", "", "不变量检查"));
    head.appendChild(el("span", "tiny " + (ok === list.length ? "inv-ok" : "inv-bad"), ok + "/" + list.length + " 通过"));
    head.style.marginBottom = "8px";
    els.inspector.appendChild(head);
    els.inspector.appendChild(el("div", "tiny dim", "与 docs/02-contracts.md 的不变量对应，实时检查当前状态。"));
    var wrap = el("div");
    wrap.style.marginTop = "6px";
    list.forEach(function (c) {
      var row = el("div", "inv");
      row.appendChild(el("span", "inv-id", c.id));
      var body = el("div", "grow");
      var t = el("div", c.ok ? "inv-ok" : "inv-bad", (c.ok ? "✓ " : "✗ ") + c.name);
      body.appendChild(t);
      if (c.detail) body.appendChild(el("div", "tiny dim", c.detail));
      row.appendChild(body);
      wrap.appendChild(row);
    });
    els.inspector.appendChild(wrap);
  }

  function renderLog() {
    var head = el("div", "row-between");
    head.appendChild(el("strong", "", "事件日志"));
    head.appendChild(el("span", "tiny dim", app.log.length + " 条"));
    head.style.marginBottom = "8px";
    els.inspector.appendChild(head);
    if (!app.log.length) {
      els.inspector.appendChild(el("div", "tiny dim", "还没有事件。"));
      return;
    }
    app.log.slice().reverse().forEach(function (l) {
      var row = el("div", "logline");
      var k = el("span", "log-kind" + (l.kind === "REJECTED" || l.kind === "DROPPED" ? " is-bad" : ""), l.kind);
      row.appendChild(k);
      row.appendChild(el("div", "grow", l.detail));
      els.inspector.appendChild(row);
    });
  }

  function renderEvidence() {
    els.inspector.appendChild(el("div", "tiny dim", "演示用：可以改某个字段的证据状态，观察结论怎么变。改动会清空当前诊断。"));
    var note = el("div", "banner");
    note.style.marginTop = "8px";
    note.appendChild(el("span", "", "默认"));
    note.appendChild(el("div", "grow", "mb-demo-b650.biosGeneration 被设为 EMPTY，对应锚点 fx_bios_missing 的答辩剧本。"));
    els.inspector.appendChild(note);

    var sel = el("select");
    sel.style.marginTop = "10px";
    app.catalogue.forEach(function (r) {
      var o = el("option", "", displayName(r) + "（" + catLabel(r.category) + "）");
      o.value = r.componentId;
      if (r.componentId === ui.evidenceId) o.selected = true;
      sel.appendChild(o);
    });
    sel.dataset.act = "evidenceSelect";
    sel.addEventListener("change", function () { ui.evidenceId = sel.value; renderInspector(); });
    els.inspector.appendChild(sel);

    var rec = recOf(ui.evidenceId);
    if (!rec) return;
    var box = el("div");
    box.style.marginTop = "10px";
    Object.keys(rec.attributes).forEach(function (path) {
      var key = rec.componentId + "." + path;
      var ov = app.evidenceOverrides[key];
      var f = rec.attributes[path];
      var row = el("div", "field-row");
      var label = el("div");
      label.appendChild(el("div", "mono", path));
      label.appendChild(el("div", "tiny dim", "值 " + (f.value == null ? "null" : f.value) + " · 原始 " + f.state + (f.conflict ? " · 来源冲突" : "")));
      row.appendChild(label);

      var s = el("select");
      s.dataset.act = "evidence";
      s.dataset.key = key;
      (function (sel, fld, k) {
        sel.addEventListener("change", function () {
          dispatch({ type: "EvidenceChanged", key: k, state: sel.value, value: fld ? fld.value : null });
        });
      })(s, f, key);
      ["VERIFIED", "REPORTED", "EMPTY", "RESET"].forEach(function (st) {
        var o = el("option", "", st === "RESET" ? "恢复原始" : st);
        o.value = st;
        var cur = ov ? ov.state : "RESET";
        if (cur === st) o.selected = true;
        s.appendChild(o);
      });
      row.appendChild(s);
      box.appendChild(row);
    });
    els.inspector.appendChild(box);

    var reset = btn("全部恢复原始证据", "btn-sm", function () {
      app = Object.assign({}, app, {
        evidenceOverrides: {}, diagnosis: null, diagnosisState: { kind: "Idle" }, needsCheck: true
      });
      render();
    });
    reset.style.marginTop = "10px";
    els.inspector.appendChild(reset);
  }

  function renderAbout() {
    var wrap = el("div");
    wrap.appendChild(el("strong", "", "这个页面是什么"));
    wrap.appendChild(el("p", "tiny dim", "PC Buildmate 的交互原型：用静态演示数据把四屏流程和两张状态机跑起来，用来核对 UI 逻辑，不是真机应用。"));

    var items = [
      ["规则引擎", "15 条规则 / 7 种 DSL kind，与 docs/03 一致；60 条 fixture + 8 条锚点全部通过。"],
      ["状态机", "诊断 4 态、替换 6 态；编辑清诊断、迟到结果丢弃、应用前全量重检。"],
      ["三态证据", "VERIFIED / REPORTED / EMPTY；EMPTY 只能得到「未验证」。"],
      ["范围", "没有实时价格、没有 FPS/跑分、没有 3D、没有网络权限。"]
    ];
    items.forEach(function (it) {
      var box = el("div", "card");
      box.style.marginTop = "8px";
      box.appendChild(el("div", "", it[0]));
      box.appendChild(el("div", "tiny dim", it[1]));
      wrap.appendChild(box);
    });

    var cmd = el("pre", "json", "node tools/test-demo-engine.mjs\nnode tools/test-demo-flow.mjs\nnode tools/serve-site.mjs");
    cmd.style.marginTop = "10px";
    wrap.appendChild(cmd);
    wrap.appendChild(el("div", "tiny dim", "静态文件：直接打开 site/index.html 也能跑（无需构建）。"));
    els.inspector.appendChild(wrap);
  }

  var api = { mount: mount, dispatch: dispatch, render: render, getState: function () { return app; }, setUi: function (patch) { Object.assign(ui, patch); render(); } };

  var g = typeof globalThis !== "undefined" ? globalThis : this;
  if (typeof module === "object" && module.exports) module.exports = api;
  else g.PBUi = api;
})(typeof window !== "undefined" ? window : globalThis);

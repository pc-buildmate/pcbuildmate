# Defense · Q&A follow-ups (English, 12 deeper questions)

> Companion to `Defense_QA_EN.md`. These are the second-round questions — what an examiner asks after
> the first answer lands. Each answer is two or three sentences and points at where the detail lives.
> No number here goes beyond `Defense_Numbers_To_Confirm.md`.

**F1. Why fifteen rules? Why not twenty?**
Because a rule has to be decidable from fields we carry and explainable in one line. Each of the
fifteen has four fixtures (passed / conflict / unverified / missing), so adding one is a data change
plus four cases, not a code change. The boundary is in `docs/03` §3.6, including what we deliberately
did not model.
中文要点：可判定 + 一句话能解释；加一条=改数据+四条 fixture，不是改引擎。

**F2. What if two rules contradict each other?**
They cannot silently: each rule returns its own outcome, and the overall status is a fold over them,
not a vote. If two rules genuinely conflict, the build shows both — the user sees the disagreement
instead of an averaged verdict. INV-15 keeps the output order stable by rule id.
中文要点：每条规则独立出结论，不做投票；冲突就两条都展示。

**F3. Why not just query an existing compatibility database?**
Because the product promise is about evidence, not just answers. An external database gives a verdict
without telling us which field is missing or which source disagrees, and it would require network
access, which we deliberately do not request. Our catalogue is static and dated so the demo is
reproducible offline.
中文要点：外部库只给结论不给证据，还要联网；我们要的是“哪项缺失/哪个来源冲突”。

**F4. How would you validate the data at scale?**
The same way we validate it now, automated: a field-path table with types and units, an audit script
that checks every record against it (`docs/20`), and seed validation that rejects unknown paths before
the app ships. What changes at scale is the ingest side — adapters per store — not the validation.
中文要点：字段表 + 审计脚本 + seed 校验；规模化变的是采集 adapter，不是校验。

**F5. What is the biggest technical risk?**
That the catalogue is wrong in a way our rules cannot see: the engine and the oracle would agree with
each other and both be wrong. That is why the evidence chapter says explicitly that rule parameters
and price accuracy are not proven by the test suite, and why field-level sources are the next data
task.
中文要点：目录本身错——引擎和 oracle 会一起错；所以证据文档明说规则参数与价格未被测试证明。

**F6. Why Kotlin and Compose?**
Compose lets the UI depend only on the contract types, so screens can be rendered from static state —
which is also how the Compose semantics tests run on the JVM without a device. Kotlin gives us a pure
JVM domain layer, which is what makes the 163 unit tests fast enough to run on every pull request.
中文要点：UI 只依赖契约，所以能用静态 state 做语义测试；纯 JVM 领域层让 163 个单测能每个 PR 都跑。

**F7. What about multiple GPUs or multi-socket boards?**
Out of scope by decision, not by accident: the catalogue pins single-instance categories to one unit
(ADR-0010), and the rules evaluate one card. Multi-GPU is physically possible but would change the
power, clearance and slot model, so it is documented as unsupported rather than silently mis-evaluated.
中文要点：单件策略（ADR-0010）是决定，不是遗漏；多卡会改功耗/间隙/槽位模型。

**F8. Does it handle ATX 3.0 / 12VHPWR?**
The connector set is a CSV field, so `12VHPWR` is expressible and the subset rule checks it. What we do
not model is the per-rail distribution or transient spikes — that needs vendor data we do not have,
and guessing it would violate the evidence principle.
中文要点：接口是 CSV 字段，子集规则能判；不做每路分配和瞬时尖峰，因为缺厂商数据。

**F9. How do you know the UI matches the rules?**
Two layers: the workspace state exposes a diagnosis only when its revision matches the current one,
and that accessor has unit tests; on top of that, Compose semantics tests assert what the screens show
in the stale case — no summary, and a "please recheck" banner instead.
中文要点：revision 不匹配就不返回诊断（单测）+ 屏幕在过期时不显示结论（语义测试）。

**F10. What would you do with two more weeks?**
Field-level sources for the catalogue, then the data work's two gaps: the x16 slot values are still
standard-layout references (one board has none) and prices still need reconciliation against official
pages. Functionally we would rather harden than add features — the feature set is frozen on 11-28.
中文要点：先补字段级来源，再把 x16 槽位从参考值换成厂商图核实、补齐缺的那块板，最后价格对账；功能冻结后再加功能不是优先项。

**F11. What if the catalogue has no dimension for a part?**
The 3D view draws what exists and labels what it estimates (for example the card's body width);
a red box is only drawn when the catalogue actually declares a limit, because "no limit data" must not
become "does not fit". The clearance view is a visual aid on top of the rules — it never decides
compatibility on its own.

**F12. Why is the release arm64-only?**
Because every phone that runs this app is arm64 and shipping x86 doubles the download for a device
class we do not target. The release workflow enforces it: any non-arm64 ABI fails the build rather
than being noticed later.

---

## How to use these

- Only reach for a follow-up if the first answer was accepted; otherwise expand the first answer.
- Each answer ends with a pointer ("in docs/03", "in docs/20"); say the pointer out loud, it shows the
  claim is checkable.
- If a question is about numbers we have not confirmed, say so — do not improvise.

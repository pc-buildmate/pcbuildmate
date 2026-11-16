# Defense · Speaker script — Test evidence and conclusion

> Speaker: 杨壹硕. Covers outline slides 20–25 (12:50–15:00). English as written; the bracketed
> Chinese lines are rehearsal notes and are not spoken.
> Numbers come from `docs/19-test-evidence.md` and are reproducible with the commands there.

## Slide 20 — Section cover (5 s)

> "So, can you trust a green build? That is the question this section answers."

（中文提示：一句话过渡，不要停在这里解释。）

## Slide 21 — Three questions, four gates (70 s)

> "Anyone can show a green tick. A green tick is only meaningful if it would turn red when the code is
> wrong — and if the tests themselves are right."

> "We answer three questions, each with a gate."

> "First: is every cell really executed? Our rule matrix, the state-machine matrix and the eighteen
> invariants are generated from real JUnit output, not typed into a document. Sixty, seventy-six and
> eighteen — and the checker fails if a single cell is missing."

> "Second: would breaking the code turn tests red? We keep twelve key mutations — realistic bugs, such as
> 'a missing field is treated as a pass', or 'applying a replacement skips the recheck'. Every one is
> killed, and this now runs in CI on every pull request."

> "Third: are the tests just restating the implementation? We wrote an independent reference model in a
> module that is forbidden from importing our domain code. On the same fixtures it agrees with the
> production engine on all sixty rule cases, and on all seventy-six state-machine transitions. And to
> prove it is not a rubber stamp, we deliberately inverted its weakest-evidence rule — the cross-check
> went red immediately."

> "The fourth gate is about drift: if a module has tests but is missing from the CI task list, the build
> fails. That check exists because we found two modules that had never run in CI at all."

（中文提示：四个门按顺序讲完；第三个门是本页重点，要说"故意写反会变红"这句。）

## Slide 22 — What this does not prove (45 s)

> "Now the part that is more useful than the green ticks: what none of this proves."

> "It does not prove that our rule parameters match real hardware — the fixtures encode our
> understanding, and if that understanding is wrong, both sides are wrong together. It does not prove
> that prices are accurate. It does not prove the 3D slot coordinates, because we do not yet have the
> motherboard x16 slot position in the catalogue. It does not prove accessibility. And it does not prove
> that Room persistence works on every pull request — that job runs on main and on demand."

> "We say this first, because a claim that cannot be falsified is not evidence."

（中文提示：这一页主动承认边界，能显著抬高可信度；不要念完四项就停，最后一句要讲。）

## Slide 23 — Known limits (35 s)

> "Concretely, three limits. The catalogue is static, with dated reference prices and no live feed — the
> manifest has no network permission, so this is a design boundary, not a missing feature. The
> motherboard slot position is not recorded yet, so the 3D view uses a documented estimate. And we have
> not automated real-device gestures; the nine-step demo is run by hand, three times, with screenshots."

（中文提示：每项都给出"为什么"而不是"以后会做"。）

## Slide 24 — Next steps (30 s)

> "Between now and December the first: a signed release with a checksum, so what we demonstrate is what
> you can install. Second: three device runs recorded with timestamps and numbers, 7800 to 7900 each
> time. Third: more component data from official store pages. Then we freeze features on November 28."

（中文提示：不要承诺实时价格或更多功能。）

## Slide 25 — Closing (25 s)

> "One sentence to take away: the weakest evidence decides the conclusion. A build is not 'compatible'
> because a check passed — it is compatible to the extent that the evidence behind every field allows.
> Thank you. We are happy to take questions."

（中文提示：收尾句和演示中"BIOS 仍是未验证"呼应，如需可回指那一页。）

---

## Handover lines

- To the demo speaker before this section: "Let me show you how we know the demo is not luck."
- To the next speaker (if any): "My teammate will cover market position and the plan."
- To a question outside scope: "That is deliberately outside this version; the boundary is documented
  in the scope chapter."

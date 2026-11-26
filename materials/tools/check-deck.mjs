#!/usr/bin/env node
/**
 * 答辩 deck 自检：页脚页码、备注与计时、按目录核对数字。
 *
 * 由来：deck 是手工排的 30 页，插/删一页就会把后面所有「N / 30」和 ⏱ 时间轴一起弄错；
 * 而讲稿、彩排表都按这套页码和时间点写的。这个脚本把「改完忘了同步」变成几秒钟的事。
 *
 * 检查项：
 *   1. 页数 = 30；
 *   2. 第 2 页起每页页脚 = 「N / 30」，首页没有页脚；
 *   3. 正文页（3–25 中有备注的）备注必须有 ⏱ 起止 + 中文提示；附录页（26–30）不能有 ⏱；
 *   4. ⏱ 时间轴不重叠、递增，最后一页收在 15:00；正文页之间的空档只允许出现在无备注的分节页上；
 *   5. 附录 D 的「30 records / 170 fields」与 assets/catalogue 实际一致。
 *
 * 用法：node materials/tools/check-deck.mjs [--deck path]
 * 退出码：0 = 全过；1 = 有问题或读不到文件。
 */
import { readFileSync, existsSync } from "node:fs";
import { inflateRawSync } from "node:zlib";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "..");
const SLIDES = 30;
const BUDGET_SECONDS = 15 * 60;
const APPENDIX_FROM = 26;

function openZip(file) {
  const b = readFileSync(file);
  let eocd = -1;
  for (let i = b.length - 22; i >= 0; i--) if (b.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  if (eocd < 0) throw new Error(`${file} 不是 zip（没有 EOCD）`);
  const count = b.readUInt16LE(eocd + 10);
  let off = b.readUInt32LE(eocd + 16);
  const entries = new Map();
  for (let i = 0; i < count; i++) {
    if (b.readUInt32LE(off) !== 0x02014b50) break;
    const nameLen = b.readUInt16LE(off + 28);
    const extraLen = b.readUInt16LE(off + 30);
    const commentLen = b.readUInt16LE(off + 32);
    const name = b.toString("utf8", off + 46, off + 46 + nameLen);
    // 压缩大小从**中央目录**取：本地文件头在 data descriptor 模式（通用标志 bit 3）下会写 0。
    entries.set(name, { lo: b.readUInt32LE(off + 42), compressed: b.readUInt32LE(off + 20) });
    off += 46 + nameLen + extraLen + commentLen;
  }
  return {
    names: () => [...entries.keys()],
    read(name) {
      const entry = entries.get(name);
      if (!entry) return null;
      const { lo, compressed } = entry;
      const nameLen = b.readUInt16LE(lo + 26);
      const extraLen = b.readUInt16LE(lo + 28);
      const start = lo + 30 + nameLen + extraLen;
      const raw = b.slice(start, start + compressed);
      return (b.readUInt16LE(lo + 8) === 0 ? raw : inflateRawSync(raw)).toString("utf8");
    },
  };
}

const texts = (xml) => [...(xml || "").matchAll(/<a:t>([^<]*)<\/a:t>/g)].map((m) => m[1]);
const mmss = (s, m) => +s * 60 + +m;
const clock = (sec) => `${Math.floor(sec / 60)}:${String(sec % 60).padStart(2, "0")}`;

function slideIndex(zip) {
  return zip
    .names()
    .filter((n) => /^ppt\/slides\/slide\d+\.xml$/.test(n))
    .sort((a, b) => a.localeCompare(b, undefined, { numeric: true }));
}

function notesFor(zip, i) {
  const rels = zip.read(`ppt/slides/_rels/slide${i}.xml.rels`) || "";
  const m = rels.match(/notesSlides\/notesSlide(\d+)\.xml/);
  return m ? zip.read(`ppt/notesSlides/notesSlide${m[1]}.xml`) : null;
}

export function checkDeck(file) {
  const problems = [];
  const zip = openZip(file);
  const slides = slideIndex(zip);
  if (slides.length !== SLIDES) problems.push(`页数：实际 ${slides.length}，期望 ${SLIDES}`);

  const timed = [];
  for (let i = 1; i <= slides.length; i++) {
    const runs = texts(zip.read(slides[i - 1]));
    // 页脚是最后一个文本框（形如「3 · Demo    14 / 30」）；正文里也有「60 / 76」，不能当页码用。
    const footer = (runs[runs.length - 1] || "").match(/(\d+)\s*\/\s*(\d+)\s*$/);
    if (i === 1) {
      if (footer) problems.push(`第 1 页不该有页码，实际「${footer[0]}」`);
    } else if (!footer) {
      problems.push(`第 ${i} 页没有页脚「${i} / ${SLIDES}」`);
    } else if (+footer[1] !== i || +footer[2] !== SLIDES) {
      problems.push(`第 ${i} 页页脚写成「${footer[1]} / ${footer[2]}」`);
    }

    const note = notesFor(zip, i);
    if (!note) continue;
    const noteText = texts(note).join(" ");
    const t = noteText.match(/⏱\s*(\d+):(\d+)\s*[–-]\s*(\d+):(\d+)/);
    const isAppendix = i >= APPENDIX_FROM;
    if (isAppendix) {
      if (t) problems.push(`附录页 ${i} 不该有 ⏱（附录不计时）`);
      continue;
    }
    if (!t) { problems.push(`正文页 ${i} 的备注没有 ⏱ 起止`); continue; }
    if (!noteText.includes("中文提示")) problems.push(`正文页 ${i} 的备注没有「中文提示」`);
    timed.push({ page: i, from: mmss(t[1], t[2]), to: mmss(t[3], t[4]) });
  }

  for (let k = 1; k < timed.length; k++) {
    const prev = timed[k - 1];
    const cur = timed[k];
    if (cur.from < prev.to) problems.push(`第 ${prev.page}→${cur.page} 页时间重叠（${clock(cur.from)} < ${clock(prev.to)}）`);
    const gap = cur.from - prev.to;
    if (gap > 10) problems.push(`第 ${prev.page}→${cur.page} 页之间空了 ${gap}s，正文页之间只允许分节页的短暂停顿`);
  }
  if (timed.length) {
    const last = timed[timed.length - 1];
    if (last.to !== BUDGET_SECONDS) {
      problems.push(`时间轴收在 ${clock(last.to)}，预算 15:00（${clock(BUDGET_SECONDS)}）`);
    }
    if (timed[0].from > 60) problems.push(`第一个计时点 ${clock(timed[0].from)} 太晚，开场应在 1 分钟内进入正文`);
  }

  // 附录 D 的数字与目录对账
  const catalogueFile = join(ROOT, "assets/catalogue/catalogue-app.json");
  if (existsSync(catalogueFile)) {
    const j = JSON.parse(readFileSync(catalogueFile, "utf8"));
    const records = j.components?.length ?? 0;
    const fields = (j.components || []).reduce((n, c) => n + Object.keys(c.attributes || {}).length, 0);
    const appendixD = texts(zip.read(slides[SLIDES - 1])).join(" ");
    const m = appendixD.match(/(\d+)\s*records,\s*(\d+)\s*fields/i);
    if (!m) problems.push("附录 D 里找不到「N records, M fields」");
    else if (+m[1] !== records || +m[2] !== fields) {
      problems.push(`附录 D 写 ${m[1]} records / ${m[2]} fields，目录实际 ${records} / ${fields}`);
    }
  } else {
    problems.push("找不到 assets/catalogue/catalogue-app.json，无法核对附录 D 的数字");
  }
  return problems;
}

function main() {
  const arg = process.argv.indexOf("--deck");
  const file = arg > 0 ? process.argv[arg + 1] : join(ROOT, "materials/source/Defense_Deck.pptx");
  if (!existsSync(file)) {
    console.error(`✗ 找不到 deck：${file}`);
    process.exit(1);
  }
  let problems;
  try {
    problems = checkDeck(file);
  } catch (e) {
    console.error(`✗ 读不了 deck：${e.message}`);
    process.exit(1);
  }
  if (problems.length) {
    console.error(`✗ [deck] ${problems.length} 处问题：`);
    for (const p of problems) console.error("  " + p);
    console.error("  修法：改 Defense_Deck.pptx 的页脚/备注，或同步 materials/README 的页码口径。");
    process.exit(1);
  }
  console.log(`[deck] OK — ${SLIDES} 页、页脚齐全、时间轴收在 15:00、附录 D 数字与目录一致。`);
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) main();

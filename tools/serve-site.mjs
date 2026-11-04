#!/usr/bin/env node
/**
 * 零依赖静态服务器，只服务 site/ 目录。
 * 用法：node tools/serve-site.mjs [--port 4173]
 * 说明：这个原型也能直接双击 site/index.html 打开（没有 fetch / module，无需构建）。
 */
import { createServer } from "node:http";
import { readFile, stat } from "node:fs/promises";
import { join, extname, normalize } from "node:path";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = join(dirname(fileURLToPath(import.meta.url)), "..", "site");
const portArg = process.argv.indexOf("--port");
const PORT = portArg > -1 ? Number(process.argv[portArg + 1]) : 4173;

const TYPES = {
  ".html": "text/html; charset=utf-8",
  ".js": "text/javascript; charset=utf-8",
  ".css": "text/css; charset=utf-8",
  ".json": "application/json; charset=utf-8",
  ".svg": "image/svg+xml",
  ".png": "image/png"
};

const server = createServer(async (req, res) => {
  const url = new URL(req.url, "http://localhost");
  let path = decodeURIComponent(url.pathname);
  if (path === "/") path = "/index.html";
  const full = join(ROOT, normalize(path).replace(/^(\.\.[/\\])+/, ""));
  if (!full.startsWith(ROOT)) { res.writeHead(403).end("forbidden"); return; }
  try {
    const info = await stat(full);
    if (info.isDirectory()) { res.writeHead(404).end("not found"); return; }
    const body = await readFile(full);
    res.writeHead(200, { "content-type": TYPES[extname(full)] || "application/octet-stream", "cache-control": "no-store" });
    res.end(body);
  } catch {
    res.writeHead(404, { "content-type": "text/plain; charset=utf-8" }).end("not found: " + path);
  }
});

server.listen(PORT, "127.0.0.1", () => {
  console.log("[serve-site] http://127.0.0.1:" + PORT + "/");
  console.log("[serve-site] 目录：" + ROOT);
});

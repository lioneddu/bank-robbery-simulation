// Tiny static server to try the web version (site/ folder) locally.
// Supports Range requests, used by CheerpJ to read the jar.
// Usage: node web/serve.mjs [port]
import { createServer } from "node:http";
import { stat, open } from "node:fs/promises";
import { createReadStream } from "node:fs";
import { extname, join, normalize, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const root = resolve(fileURLToPath(new URL("../site", import.meta.url)));
const port = Number(process.argv[2] || 8080);
const types = {
  ".html": "text/html; charset=utf-8", ".js": "text/javascript", ".jar": "application/java-archive",
  ".txt": "text/plain; charset=utf-8", ".png": "image/png", ".jpg": "image/jpeg",
};

createServer(async (req, res) => {
  try {
    let path = decodeURIComponent(new URL(req.url, "http://localhost").pathname);
    if (path.endsWith("/")) path += "index.html";
    const file = normalize(join(root, path));
    if (!file.startsWith(root)) { res.writeHead(403).end(); return; }
    const info = await stat(file);
    if (!info.isFile()) { res.writeHead(404).end(); return; }

    const headers = { "Content-Type": types[extname(file)] || "application/octet-stream", "Accept-Ranges": "bytes" };
    const range = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range || "");
    if (range) {
      const start = range[1] === "" ? Math.max(0, info.size - Number(range[2])) : Number(range[1]);
      const end = range[1] !== "" && range[2] !== "" ? Math.min(Number(range[2]), info.size - 1) : info.size - 1;
      if (start > end || start >= info.size) {
        res.writeHead(416, { "Content-Range": `bytes */${info.size}` }).end();
        return;
      }
      res.writeHead(206, { ...headers, "Content-Range": `bytes ${start}-${end}/${info.size}`, "Content-Length": end - start + 1 });
      if (req.method === "HEAD") { res.end(); return; }
      createReadStream(file, { start, end }).pipe(res);
    } else {
      res.writeHead(200, { ...headers, "Content-Length": info.size });
      if (req.method === "HEAD") { res.end(); return; }
      createReadStream(file).pipe(res);
    }
  } catch {
    res.writeHead(404).end();
  }
}).listen(port, () => console.log(`Bank Robbery web: http://localhost:${port}/`));

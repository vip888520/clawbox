#!/usr/bin/env node
// decompress-zst.mjs <input.zst> <output.tar>
// Pure-JS zstd decode via zstddec.
import { ZSTDDecoder } from "zstddec";
import { readFileSync, writeFileSync } from "node:fs";

const [inPath, outPath] = process.argv.slice(2);
if (!inPath || !outPath) {
  console.error("usage: node decompress-zst.mjs <in.zst> <out.tar>");
  process.exit(1);
}
const compressed = readFileSync(inPath);
const decoder = new ZSTDDecoder();
await decoder.init();
const data = decoder.decode(compressed);
writeFileSync(outPath, data);
console.log(`decoded ${inPath} -> ${outPath} (${data.length} bytes)`);

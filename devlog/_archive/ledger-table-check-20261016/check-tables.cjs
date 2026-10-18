const fs = require('fs');

const files = process.argv.slice(2);
let bad = 0;

for (const file of files) {
  const lines = fs.readFileSync(file, 'utf8').split(/\r?\n/);
  let i = 0;
  while (i < lines.length) {
    if (!lines[i].trim().startsWith('|')) { i++; continue; }
    const block = [];
    while (i < lines.length && lines[i].trim().startsWith('|')) { block.push([i + 1, lines[i]]); i++; }
    const cells = (l) => l.trim().replace(/^\|/, '').replace(/\|$/, '').split(/(?<!\\)\|/).length;
    const counts = block.map(([n, l]) => [n, cells(l)]);
    const sep = block.length > 1 && /^\s*\|[\s:|-]+\|\s*$/.test(block[1][1]);
    if (!sep && block.length > 1) console.log(`${file}:${block[0][0]} NOTE missing separator row`);
    const base = counts[0][1];
    for (const [n, c] of counts) {
      if (c !== base) { console.log(`${file}:${n} BAD cells=${c} expected=${base}`); bad++; }
    }
  }
}
console.log(bad === 0 ? `bad rows 0 (files: ${files.length})` : `BAD TOTAL ${bad}`);

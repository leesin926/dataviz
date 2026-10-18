const fs = require('fs');
const path = require('path');

const ROOT = 'E:/wowowo/backend';
const HANDLER = /@(Get|Post|Put|Delete|Patch|Request)Mapping\b/g;
const ANNO = /@RequiresPermission\b/;

function walk(dir, out) {
  for (const e of fs.readdirSync(dir)) {
    if (e === 'target' || e === 'node_modules') continue;
    const p = path.join(dir, e);
    const st = fs.statSync(p);
    if (st.isDirectory()) walk(p, out);
    else if (e.endsWith('Controller.java')) out.push(p);
  }
  return out;
}

const files = walk(ROOT, []);
let totalHandlers = 0, coveredHandlers = 0, zeroAnnoFiles = 0;
const perService = new Map();

for (const file of files) {
  const src = fs.readFileSync(file, 'utf8');
  const service = path.relative(ROOT, file).split('/')[0];
  const lines = src.split('\n');

  // class-level annotation = the first @RequiresPermission appearing before "class X"
  const classIdx = lines.findIndex((l) => /\b(public\s+)?(final\s+)?class\s+\w+/.test(l));
  const classLevel = lines.slice(0, classIdx < 0 ? lines.length : classIdx).some((l) => ANNO.test(l));

  let handlers = 0, covered = 0;
  for (let i = 0; i < lines.length; i++) {
    const m = lines[i].match(HANDLER);
    if (!m) continue;
    // class-level @RequestMapping (declared before the class declaration) is not a handler
    if (classIdx >= 0 && i < classIdx && /@RequestMapping/.test(lines[i])) continue;
    handlers += m.length;
    let hasOwn = false;
    for (let j = i + 1; j < lines.length; j++) {
      if (ANNO.test(lines[j])) hasOwn = true;
      if (/\b(public|private|protected)\s+[\w<>,\[\]\s]+\s+\w+\s*\(/.test(lines[j])) break;
      if (/@\w+/.test(lines[j])) continue;
      if (lines[j].trim() === '' || lines[j].trim().startsWith('*') || lines[j].trim().startsWith('//')) continue;
      break;
    }
    // annotation may sit *above* the mapping too
    for (let j = i - 1; j >= 0 && j > i - 6; j--) {
      if (ANNO.test(lines[j])) hasOwn = true;
      if (lines[j].trim() === '' || /^\s*[A-Za-z]/.test(lines[j])) break;
    }
    if (hasOwn || classLevel) covered += 1;
  }
  totalHandlers += handlers;
  coveredHandlers += covered;
  if (covered === 0) zeroAnnoFiles += 1;
  const cur = perService.get(service) || { files: 0, handlers: 0, covered: 0 };
  cur.files += 1;
  cur.handlers += handlers;
  cur.covered += covered;
  perService.set(service, cur);
}

const rows = [...perService.entries()]
  .filter(([, v]) => v.handlers > 0)
  .sort((a, b) => b[1].handlers - a[1].handlers);
for (const [svc, v] of rows) {
  console.log(`${svc.padEnd(22)} files=${String(v.files).padStart(2)} handlers=${String(v.handlers).padStart(3)} covered=${String(v.covered).padStart(3)} uncovered=${String(v.handlers - v.covered).padStart(3)}`);
}
console.log(`\nTOTAL handlers=${totalHandlers} covered=${coveredHandlers} uncovered=${totalHandlers - coveredHandlers} controllers-with-zero-annotation=${zeroAnnoFiles}`);

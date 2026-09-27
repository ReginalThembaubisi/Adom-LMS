// The design keeps its styles as inline CSS strings ("display:flex;gap:12px"). s() turns
// one into the object React's style prop needs. Parsed once per distinct string.
const cache = new Map();

export function s(css) {
  let out = cache.get(css);
  if (out) return out;
  out = {};
  for (const decl of split(css)) {
    const i = decl.indexOf(':');
    if (i < 0) continue;
    const prop = decl.slice(0, i).trim();
    const value = decl.slice(i + 1).trim();
    if (!prop || !value) continue;
    out[prop.startsWith('--') ? prop : prop.replace(/-([a-z])/g, (_, c) => c.toUpperCase())] = value;
  }
  cache.set(css, out);
  return out;
}

// Split on semicolons that aren't inside quotes or brackets (url(...), 'Archivo',...).
function split(css) {
  const parts = [];
  let depth = 0, quote = null, start = 0;
  for (let i = 0; i < css.length; i++) {
    const c = css[i];
    if (quote) { if (c === quote) quote = null; continue; }
    if (c === '"' || c === "'") quote = c;
    else if (c === '(') depth++;
    else if (c === ')') depth--;
    else if (c === ';' && depth === 0) { parts.push(css.slice(start, i)); start = i + 1; }
  }
  parts.push(css.slice(start));
  return parts;
}

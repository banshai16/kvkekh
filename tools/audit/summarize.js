const r = require("./axe-report.json");
console.log("pages scanned:", r.length);
const bad = r.filter(x => x.status >= 400 && !x.url.includes("does-not-exist")); console.log("non-200:", bad.map(b=>b.url+" "+b.status));
const ov = r.filter(x => x.horizontalOverflow > 0); console.log("horizontal overflow:", ov.map(o => o.viewport+" "+o.url.replace(/.*kvkekh/,"")+" +"+o.horizontalOverflow+"px"));
const agg = {};
for (const p of r) for (const v of p.violations) { const a = agg[v.id] ||= { impact: v.impact, help: v.help, pages: new Set(), nodes: 0, examples: [] }; a.pages.add(p.viewport+"/"+p.label+" "+p.url.replace(/.*kvkekh/,"")); a.nodes += v.count; if (a.examples.length < 4) a.examples.push(v.nodes[0]); }
for (const [id, a] of Object.entries(agg)) { console.log("\n##", id, a.impact, "-", a.help, "| pages:", a.pages.size, "nodes:", a.nodes); console.log("  e.g.", [...a.pages].slice(0,5).join(" ; ")); a.examples.forEach(e => console.log("   ->", e.target, "|", (e.summary||"").split("\n").slice(0,3).join(" / ").slice(0,300))); }
const inc = {}; for (const p of r) for (const v of p.incomplete||[]) { inc[v.id] = (inc[v.id]||0) + v.count; } console.log("\nincomplete (needs manual review):", inc);

const { chromium } = require('playwright-core');
const axeSource = require('fs').readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');
(async () => {
  const b = await chromium.launch({ channel: 'msedge' }); const p = await (await b.newContext({ bypassCSP: true })).newPage();
  const base = 'http://localhost:8080/kvkekh';
  const sm = await (await p.request.get(base + '/sitemap.xml')).text();
  const urls = [...sm.matchAll(/<loc>([^<]+)<\/loc>/g)].map(m => m[1]);
  const seen = new Map();
  for (const dark of [false, true]) for (const u of urls) {
    await p.goto(u); if (dark) { await p.evaluate(() => localStorage.setItem('kvk-a11y', '{"dark":true}')); await p.reload(); }
    await p.addScriptTag({ content: axeSource });
    const r = await p.evaluate(async () => (await axe.run(document, { runOnly: ['color-contrast'] })).incomplete);
    for (const v of r) for (const n of v.nodes) { const k = (dark ? '[dark] ' : '') + n.target.join(' ').replace(/:nth-child\(\d+\)/g, ''); if (!seen.has(k)) seen.set(k, u.replace(base, '') + ' :: ' + (n.any[0]||{}).message); }
  }
  for (const [k, v] of seen) console.log(k, '=>', v);
  await b.close();
})();

// Runs axe-core (WCAG 2.0/2.1 A + AA, plus best practices) on every page of the site.
// Usage: node axe-scan.js http://localhost:8080/kvkekh  > axe-report.json
const { chromium } = require('playwright-core');
const fs = require('fs');
const axeSource = fs.readFileSync(require.resolve('axe-core/axe.min.js'), 'utf8');

const base = process.argv[2];
const VIEWPORTS = [{ name: 'desktop', width: 1366, height: 900 }, { name: 'mobile-320', width: 320, height: 640 }];

(async () => {
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const ctx = await browser.newContext({ bypassCSP: true });  // only so axe can be injected; the site itself keeps its CSP
  const page = await ctx.newPage();

  const sitemap = await (await page.request.get(base + '/sitemap.xml')).text();
  const urls = [...sitemap.matchAll(/<loc>([^<]+)<\/loc>/g)].map(m => m[1]);
  // One album photo page, discovered from the gallery (photo pages are not in the sitemap)
  const albumHref = (await (await page.request.get(base + '/gallery')).text()).match(/href="[^"]*?(\/gallery\/[a-z0-9-]+)"/);
  if (albumHref) {
    const albumHtml = await (await page.request.get(base + albumHref[1])).text();
    const photo = albumHtml.match(new RegExp('href="[^"]*?(' + albumHref[1] + '/[a-z0-9-]+)"'));
    if (photo) urls.push(base + photo[1]);
  }
  urls.push(base + '/search?q=maize', base + '/does-not-exist');

  const results = [];
  async function scan(url, label, viewport, prepare) {
    await page.setViewportSize({ width: viewport.width, height: viewport.height });
    const response = await page.goto(url, { waitUntil: 'load' });
    if (prepare) await prepare();
    await page.addScriptTag({ content: axeSource });
    const r = await page.evaluate(async () => await window.axe.run(document, {
      runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa', 'best-practice'] },
      resultTypes: ['violations', 'incomplete'],
    }));
    // WCAG 1.4.10 reflow: at 320 CSS px nothing may scroll horizontally (except tables/images by exception)
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    results.push({
      url, label, viewport: viewport.name, status: response.status(), horizontalOverflow: overflow,
      violations: r.violations.map(v => ({ id: v.id, impact: v.impact, help: v.help, tags: v.tags,
        nodes: v.nodes.slice(0, 5).map(n => ({ target: n.target.join(' '), summary: n.failureSummary })), count: v.nodes.length })),
      incomplete: r.incomplete.map(v => ({ id: v.id, help: v.help, count: v.nodes.length })),
    });
  }

  for (const vp of VIEWPORTS) {
    for (const url of urls) await scan(url, 'default', vp);
  }
  // Dark-contrast mode and enlarged text on a representative set of pages (colour contrast in both themes).
  const sample = [base + '/', urls[1], base + '/gallery', base + '/feedback', base + '/documents/publications'];
  for (const url of sample) {
    await scan(url, 'dark-contrast', VIEWPORTS[0], async () => {
      await page.evaluate(() => { localStorage.setItem('kvk-a11y', JSON.stringify({ dark: true, text: 4 })); });
      await page.reload({ waitUntil: 'load' });
    });
  }
  await page.evaluate(() => localStorage.clear());

  // Feedback form error state (server-side validation result).
  await page.setViewportSize({ width: 1366, height: 900 });
  await page.goto(base + '/feedback');
  await page.click('button[type=submit]');
  await page.waitForLoadState('load');
  await page.addScriptTag({ content: axeSource });
  const fr = await page.evaluate(async () => await window.axe.run(document, { runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'best-practice'] } }));
  results.push({ url: base + '/feedback (POST, errors)', label: 'form-errors', viewport: 'desktop', status: 200, horizontalOverflow: 0,
    violations: fr.violations.map(v => ({ id: v.id, impact: v.impact, help: v.help, nodes: v.nodes.slice(0, 5).map(n => ({ target: n.target.join(' '), summary: n.failureSummary })), count: v.nodes.length })), incomplete: [] });

  await browser.close();
  console.log(JSON.stringify(results, null, 1));
})().catch(e => { console.error(e); process.exit(1); });

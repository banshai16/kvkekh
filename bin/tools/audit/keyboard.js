// Keyboard, focus and interaction checks that automated rule engines cannot do.
// Usage: node keyboard.js http://localhost:8080/kvkekh
const { chromium } = require('playwright-core');
const base = process.argv[2];
const results = [];
const check = (name, ok, detail = '') => results.push({ name, ok: !!ok, detail });

(async () => {
  const browser = await chromium.launch({ channel: 'msedge' });
  const page = await (await browser.newContext({ viewport: { width: 1366, height: 900 }, bypassCSP: true })).newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));
  page.on("console", m => { if (m.type() === "error") errors.push(m.text()); });
  page.on("response", r => { if (r.status() >= 400) errors.push(r.status() + " " + r.url()); });

  // 1. Skip link is the first Tab stop and moves focus to <main>
  await page.goto(base + '/sitemap');
  await page.keyboard.press('Tab');
  const first = await page.evaluate(() => ({ text: document.activeElement.textContent.trim(), visible: document.activeElement.getBoundingClientRect().top >= 0 }));
  check('First Tab stop is "Skip to main content" and it becomes visible', first.text === 'Skip to main content' && first.visible, JSON.stringify(first));
  await page.keyboard.press('Enter');
  check('Skip link moves focus to main content', await page.evaluate(() => document.activeElement.id === 'main' || location.hash === '#main'));

  // 2. Every Tab stop on the home page has a visible focus indicator and is on-screen-visible (not hidden)
  await page.goto(base + '/');
  const stops = [];
  for (let i = 0; i < 60; i++) {
    await page.keyboard.press('Tab');
    const s = await page.evaluate(() => {
      const el = document.activeElement; if (!el || el === document.body) return null;
      const cs = getComputedStyle(el); const r = el.getBoundingClientRect();
      return { tag: el.tagName, label: (el.getAttribute('aria-label') || el.textContent || el.getAttribute('alt') || '').trim().slice(0, 40),
        indicator: (cs.outlineStyle !== 'none' && parseFloat(cs.outlineWidth) >= 2) || cs.boxShadow !== 'none',
        rendered: r.width > 0 && r.height > 0 && cs.visibility !== 'hidden' };
    });
    if (!s) break; stops.push(s);
  }
  const noIndicator = stops.filter(s => !s.indicator);
  const hiddenStops = stops.filter(s => !s.rendered);
  check(`All ${stops.length} Tab stops on the home page show a focus indicator`, noIndicator.length === 0, JSON.stringify(noIndicator.slice(0, 5)));
  check('No Tab stop lands on a hidden element (closed menus are out of the tab order)', hiddenStops.length === 0, JSON.stringify(hiddenStops.slice(0, 5)));

  // 3. Dropdown menu: open with Enter, reach a link with Tab, close with Escape (focus returns)
  await page.goto(base + '/');
  const toggle = page.locator('.menu__toggle').first();
  const submenuId = await toggle.getAttribute('aria-controls');
  await toggle.focus(); await page.keyboard.press('Enter');
  check('Menu opens with Enter and sets aria-expanded="true"', await toggle.getAttribute('aria-expanded') === 'true' && await page.locator('#' + submenuId).isVisible());
  await page.keyboard.press('Tab');
  check('Tab moves into the open submenu', await page.evaluate(() => !!document.activeElement.closest('.submenu')));
  await page.keyboard.press('Escape');
  check('Escape closes the submenu and returns focus to its button',
    await toggle.getAttribute('aria-expanded') === 'false' && await page.evaluate(() => document.activeElement.classList.contains('menu__toggle')));

  // 4. Accessibility panel: every control works from the keyboard and is remembered
  const a11yBtn = page.locator('button[aria-controls="a11y-panel"]');
  await a11yBtn.focus(); await page.keyboard.press('Enter');
  check('Accessibility panel opens and focus moves into it', await page.locator('#a11y-panel').isVisible() && await page.evaluate(() => !!document.activeElement.closest('#a11y-panel')));
  for (const t of ['dark', 'invert', 'saturation', 'links', 'hide-images', 'cursor']) {
    const b = page.locator(`[data-a11y-toggle="${t}"]`); await b.focus(); await page.keyboard.press('Enter');
    const on = await page.evaluate(n => document.documentElement.classList.contains('a11y-' + n), t);
    check(`"${t}" toggles on with the keyboard and reports aria-pressed`, on && await b.getAttribute('aria-pressed') === 'true');
    await page.keyboard.press('Enter');
  }
  await page.locator('[data-a11y-text="up"]').focus(); await page.keyboard.press('Enter'); await page.keyboard.press('Enter');
  const size = await page.evaluate(() => parseFloat(getComputedStyle(document.documentElement).fontSize));
  check('Text size increase enlarges the root font (16px → 20px after two steps)', Math.abs(size - 20) < 0.5, size + 'px');
  check('Status message announces the change', (await page.locator('#a11y-status').textContent()).includes('125%'));
  await page.reload();
  check('Text size is remembered after reload', Math.abs(await page.evaluate(() => parseFloat(getComputedStyle(document.documentElement).fontSize)) - 20) < 0.5);
  await a11yBtn.click(); await page.locator('[data-a11y-reset]').click();
  check('Reset all restores defaults', Math.abs(await page.evaluate(() => parseFloat(getComputedStyle(document.documentElement).fontSize)) - 16) < 0.5);
  await page.keyboard.press('Escape');

  // 5. Language menu opens/closes and is labelled
  const lang = page.locator('button[aria-controls="language-menu"]');
  await lang.click();
  check('Language menu opens', await page.locator('#language-menu').isVisible());
  await page.mouse.click(5, 600);
  check('Clicking outside closes an open menu', !(await page.locator('#language-menu').isVisible()));

  // 6. Mobile (320px): menu button reveals navigation, controls are large enough to tap (WCAG 2.5.8: 24×24 minimum)
  await page.setViewportSize({ width: 320, height: 700 });
  await page.goto(base + '/');
  check('At 320px the navigation is collapsed behind a Menu button', !(await page.locator('#global-menu').isVisible()) && await page.locator('.nav-toggle').isVisible());
  await page.locator('.nav-toggle').click();
  check('Menu button expands the navigation', await page.locator('#global-menu').isVisible() && await page.locator('.nav-toggle').getAttribute('aria-expanded') === 'true');
  const small = await page.evaluate(() => [...document.querySelectorAll('button, .uc-btn, .menu > li > a, .btn, input, textarea')]
    .filter(e => { const r = e.getBoundingClientRect(); return r.width > 0 && (r.width < 24 || r.height < 24); })
    .map(e => e.tagName + ' ' + (e.textContent || e.name || '').trim().slice(0, 30)));
  check('All buttons, menu items and form controls are at least 24×24 px', small.length === 0, JSON.stringify(small));

  // 7. WCAG 1.4.12 text spacing: no content lost or overlapping horizontally when spacing is increased
  await page.setViewportSize({ width: 1366, height: 900 });
  // Pages with tables, forms and grids; '/about/staff' is skipped on sites without it (404 is reported).
  for (const u of ['/', '/feedback', '/gallery', '/about/staff']) {
    await page.goto(base + u);
    await page.addStyleTag({ content: '* { line-height: 1.5 !important; letter-spacing: 0.12em !important; word-spacing: 0.16em !important; } p { margin-bottom: 2em !important; }' });
    const clipped = await page.evaluate(() => [...document.querySelectorAll('body *')].filter(e => {
      const cs = getComputedStyle(e); return (cs.overflow === 'hidden' || cs.overflowX === 'hidden') && e.scrollWidth > e.clientWidth + 1 && e.clientWidth > 0 && !e.classList.contains('visually-hidden') && !e.closest('.visually-hidden, .field--honeypot');
    }).map(e => e.className || e.tagName).slice(0, 5));
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    check(`Text spacing override on ${u}: no clipped text, no horizontal scroll`, clipped.length === 0 && overflow <= 0, JSON.stringify({ clipped, overflow }));
  }

  // 8. WCAG 1.4.4: 200% zoom (simulated with a 683px viewport) keeps content in one column without horizontal scroll
  await page.setViewportSize({ width: 683, height: 900 });
  for (const u of ['/', '/feedback', '/gallery']) {
    await page.goto(base + u);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
    check(`200% zoom on ${u}: no horizontal scroll`, overflow <= 0, overflow + 'px');
  }

  // 9. Page language, titles and headings
  await page.setViewportSize({ width: 1366, height: 900 });
  const sm = await (await page.request.get(base + '/sitemap.xml')).text();
  const urls = [...sm.matchAll(/<loc>([^<]+)<\/loc>/g)].map(m => m[1]);
  const titles = new Map(); const problems = [];
  for (const u of urls) {
    await page.goto(u);
    const info = await page.evaluate(() => ({ lang: document.documentElement.lang, title: document.title,
      h1: document.querySelectorAll('h1').length,
      skips: (() => { let prev = 1, bad = []; document.querySelectorAll('main h1, main h2, main h3, main h4').forEach(h => { const l = +h.tagName[1]; if (l > prev + 1) bad.push(h.tagName + ':' + h.textContent.trim().slice(0, 30)); prev = l; }); return bad; })() }));
    if (info.lang !== 'en' || info.h1 !== 1 || info.skips.length) problems.push({ u: u.replace(base, ''), ...info });
    titles.set(info.title, (titles.get(info.title) || 0) + 1);
  }
  check(`All ${urls.length} pages: lang="en", exactly one h1, no skipped heading levels`, problems.length === 0, JSON.stringify(problems.slice(0, 5)));
  const dupTitles = [...titles].filter(([, n]) => n > 1);
  check('Every page has a unique <title>', dupTitles.length === 0, JSON.stringify(dupTitles));

  check('No JavaScript errors on any page visited', errors.length === 0, JSON.stringify(errors.slice(0, 5)));

  await browser.close();
  for (const r of results) console.log((r.ok ? 'PASS ' : 'FAIL ') + r.name + (r.ok || !r.detail ? '' : '\n     ' + r.detail));
  console.log(`\n${results.filter(r => r.ok).length}/${results.length} passed`);
})().catch(e => { console.error(e); process.exit(1); });

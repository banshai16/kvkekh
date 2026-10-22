# Tools

Helpers used to build and audit this site. They are not part of the application.

## `audit/` — accessibility and keyboard checks (Node 20+, Microsoft Edge installed)

```sh
cd tools/audit
npm install --ignore-scripts          # playwright-core + axe-core only; uses the installed Edge, downloads no browser
node axe-scan.js http://localhost:8080/<context> > axe-report.json && node summarize.js
node keyboard.js http://localhost:8080/<context>
node contrast-incomplete.js            # lists colour-contrast items axe could not decide (edit base URL inside)
```

- `axe-scan.js` — axe-core (WCAG 2.0/2.1/2.2 A+AA + best practice) on every sitemap URL at 1366 px and 320 px,
  dark mode, one album photo page, search results, the 404 page and the feedback form error state; also reports
  horizontal overflow at 320 px (WCAG 1.4.10). The browser context uses `bypassCSP` only so axe can be injected.
- `keyboard.js` — skip link, focus indicator on every Tab stop, dropdown menus and Escape, accessibility panel,
  language menu, mobile menu, target sizes, text spacing (1.4.12), 200% zoom, one h1 / heading order / unique
  titles on every page, and JavaScript errors.
- Expect a favicon request to `/favicon.ico` if the layout has no `<link rel="icon">`.

## `Sheet.java` — numbered contact sheets of photos, for writing captions

Needs the app running (it fetches `/media/thumb/...`). Input: a text file of `album-slug|media/relative/path|n` lines.

```sh
java Sheet.java list.txt http://localhost:8080/<context>/media/thumb/ out-dir
```

## `PdfAudit.java` — which PDFs are scanned images (no text layer) or untagged

Needs PDFBox on the classpath (e.g. the jars from `mvn dependency:copy-dependencies`).

```sh
java -cp "target/dependency/*" tools/PdfAudit.java ../<site>/public_html
```

# KVK website migration playbook

How the KVK East Khasi Hills website was moved from PHP to Spring Boot with a DBIM 3.0 / GIGW 3.0 compliant UI,
written so the same work can be repeated for the other KVK sites (`e:\NIC\KVK\kvkwjh`, `e:\NIC\KVK\kvkwkh`).

**Reference implementation:** `e:\NIC\KVK\kvkekh-springboot` (this project). Copy it; do not rebuild from scratch.
Read its `README.md`, then this file, before changing anything.

---

## 1. Decisions already made (apply to every site unless the user says otherwise)

| Topic | Decision |
|---|---|
| Stack | Java 25, Spring Boot 4.1.1, Thymeleaf, Maven. JDK 25 is GraalVM at `E:\Software\graalvm-jdk-25.0.1+8.1` (the system `java` is 23 — set `JAVA_HOME`). |
| Content model | Templates only, no CMS or login. Content lives in `src/main/resources/content/*.yml` and `templates/`. |
| Language | English only. Keep the language selector slot in the header (DBIM fixed control). |
| Identity | DBIM **Header 2 + Logo Lockup 3A**: State Emblem + organisation logo + "Krishi Vigyan Kendra, &lt;District&gt;". Parent line in the top strip: "Department of Agriculture and Farmers' Welfare, Government of Meghalaya". ICAR logo as co-brand (max 2). Fallback if emblem use is not permitted: Lockup 3B / Header 3. |
| Colour / type | DBIM **Green** colour group (`#0F5757 #2D8686 #75BDBD #A6D9D9 #D9F2F2`) + functional palette only, also in dark mode. Noto Sans, self-hosted. Links/buttons use `#0F5757` (`#2D8686` fails AA on white). |
| Navigation | `Home · About KVK · Offerings · Documents · Media · Connect` — **Offerings** = training, on-farm trials, frontline demonstrations, extension activities; **Documents** = publications, newsletters, sale, archive, success stories (index + each story); **Media** = the photo gallery (page title "Media"); **Connect** = contact us, feedback, related links. URL paths stay as in the reference (`/activities/...`, `/stories/...`, `/gallery`); only labels and grouping differ. |
| Footer | Website policies, archive, sitemap, help, contact us, related links, feedback; Last Updated On; visitor count; lineage "This is the official website of Krishi Vigyan Kendra, &lt;District&gt;, under the Department of Agriculture and Farmers' Welfare, Government of Meghalaya." |
| Context path | The legacy URL prefix of that site (e.g. `/kvkekh`), so old links keep working. Check `base_url` in the legacy `template/header.php` or `footer.php`. |
| Feedback mail | SMTP via `SPRING_MAIL_*` env vars; submissions always saved to H2 first. The legacy NIC Relay Mail SOAP endpoint is dead (404). **Never copy the relay username/password from `feedback.php`.** |
| Visitor counter | Continues from the legacy `counter.txt` (`visitors.baseline`). Never migrate `ip.txt` / `hits.txt` (raw IPs). |

## 2. Start a new site

1. Copy the reference project to `e:\NIC\KVK\<site>-springboot`, **excluding** `target/`, `var/`, `tools/audit/node_modules/`.
2. In `pom.xml` change `artifactId`/`name`/`description`. Keep the Java package (`in.nic.kvkekh`) unless the user
   asks otherwise — identical code across sites keeps a later merge into one shared codebase easy.
3. In `application.yml`: `server.servlet.context-path`, `site.base-url` default, `media.root` default
   (`../<site>/public_html`), `feedback.recipient` (from the legacy `feedback.php` `to` address), `visitors.baseline`
   (legacy `counter.txt`).
4. Replace site content (sections 3–5). Keep all Java, `fragments/`, CSS and JS as they are.
5. Before running, delete `var/` if it was copied (it holds the other site's database and image cache).

## 3. Survey the legacy site first

- List every `.php` page (`content/*.php` and root), the includes/templates, and which pages are copies of others.
- Note per-site differences. Known ones: **kvkwjh** has duplicate `"(1)"` files and a `download/` folder from the
  server export (ignore duplicates; compare before choosing); **kvkwkh** has `videos/`, `docs/`, `unitegallery/`
  and `Camera-master/` (videos are not yet supported — design a Media sub-section for them, with captions and
  dates per DBIM, and ask the user before adding a video player).
- Find all media folders (`images/photogallery/*`, `publication`, `newsletter`, `advertisement`, `Actionplan`,
  `docs`, …). Add any new top-level folder to `WebConfig.MEDIA_FOLDERS` and `DocumentLibrary.FOLDERS`.
- Read `feedback.php` for the recipient and validation; read `counter.txt`; find the live URL.
- Check the extracted page text for errors carried from the old site (wrong years, copy-paste headings such as
  "Host Organization" on unrelated pages, truncated sentences).

## 4. Port content

- `content/site.yml`: name lines, contact, related links (check each URL; prefer HTTPS), footer, navigation (section 1).
- `content/pages.yml`: one entry per page with `legacy:` paths for every old `.php` URL (301 redirects).
  `last-updated:` = the legacy site's own stated date for carried-over content; today's date for rewritten pages.
- `templates/pages/...`: port each page as clean semantic HTML. Rules used:
  - fix spelling and grammar, but **never invent facts** (names, numbers, abbreviation expansions, service promises);
    leave a `<!--/* TODO: ... */-->` comment where something needs confirming;
  - tables: `<th scope>`, numbers in `td.num`, wrap in
    `<div class="table-scroll" role="region" tabindex="0" aria-label="...">`;
  - images over 500 KB or any content photo: `@{/media/medium/images/...}` (resized), not the original;
  - static paths only inside `@{...}` (Thymeleaf 3.1 forbids bean calls in link expressions).
- Pages rewritten for the reference site and reusable almost verbatim (change names/places only): help, screen
  reader access, browser accessibility options, accessibility statement (list that site's scanned PDFs), privacy,
  terms, copyright, hyperlinking, disclaimer, and the six draft GIGW policies (cmap, content-archival, content-review,
  website-monitoring, contingency-management, security).
- `content/albums.yml`: one album per gallery folder, `legacy:` = the old album `.php` page. Write captions from
  **numbered contact sheets** (`tools/Sheet.java`): describe only what is visible; use descriptive file names as-is;
  correct spelling (e.g. Brocolli → Broccoli); English captions for Khasi file names.
- `content/documents.yml`: collections (notices, annual-reports, leaflets, leaflets in Khasi with `lang: kha`,
  newsletters…). Give outdated notices `archived: <date>` (they move to the Archive page). Check PDFs with
  `tools/PdfAudit.java` and name scanned (no text layer) PDFs in the accessibility statement.
- Home page: banner (one static image, < 500 KB), About, What's New (from `notices`), district profile, quick links.

## 5. Tests and fixtures

`src/test/resources/test-media` and the assertions in `SiteTest`, `AuditFixesTest`, `FeedbackTest` refer to the
reference site's fixture album (`/gallery/crops`, its captions) and PDF. For a new site either keep a matching
`crops` album entry in `albums.yml` or replace the fixture files and update those assertions. All tests must pass:
`mvn test` (41 in the reference).

## 6. Verify (in this order)

1. `mvn test`.
2. Run with `-Dspring-boot.run.profiles=dev`; open `http://localhost:8080/<context>/` (the root `/` is a 404 by design).
3. Startup log: no "Document ... not found", no "Album ... folder not found" warnings.
4. Crawl: every link from sitemap, publications, newsletters, stories and gallery returns 200; every album and
   photo page returns 200; no content image over 500 KB.
5. `tools/audit`: `axe-scan.js` + `summarize.js` (target: 0 violations, 0 overflow) and `keyboard.js` (all pass).
6. Security: `curl -I` for headers (CSP, XFO, nosniff, Referrer-Policy, Permissions-Policy; HSTS with
   `X-Forwarded-Proto: https`); PUT/DELETE/PATCH 403, TRACE 405, OPTIONS 403; `Thumbs.db`/`.docx`/`.mp4` under media
   folders 404; `/sitemap.xml` with a forged `Host` header still uses `site.base-url`.
7. Dependencies: `mvn dependency:list` → query https://api.osv.dev/v1/querybatch; fix anything found by overriding
   versions in `pom.xml` (use releases at least two weeks old).
8. Screenshots with headless Edge at 1366 px and 520 px (headless Edge on Windows cannot go narrower than ~500 px;
   use the Playwright scripts for 320 px).
9. Publish a pre-audit report (DBIM Annexure F.1 items 1–43, accessibility tests, security, open items with owners).

## 7. Pitfalls already hit (do not rediscover them)

- `spring.config.import` for the content YAML files; Map keys containing dots do not bind — captions are a list.
- Binding `../relative` to `java.nio.file.Path` fails (treated as a classpath resource) — bind as `String`.
- `LocalDate` in `@ConfigurationProperties` records needs `@DateTimeFormat(iso = DATE)`.
- H2 2.x reserves `DAY` — the column is `visit_day`.
- Thymeleaf: SpEL `map[var]` uses the literal key "var" — use `map.get(var)`; `th:field` re-renders rejected values
  (the captcha input is written without `th:field`); a checkbox needs an explicit `id` for error-summary links.
- `@{${@bean.method()}}` is forbidden in Thymeleaf 3.1 link expressions.
- `th:aria-*` / `th:attr` with a null value removes the attribute (used for `aria-current`, `aria-describedby`).
- Strict CSP blocks the axe/Playwright injection — the audit browser uses `bypassCSP`; the site keeps its CSP.
- `forward-headers-strategy: native` (not `framework`) so clients cannot spoof their IP.
- Spring Security's firewall rejects `../` with 400 before controllers see it (tests accept any 4xx).
- PDFBox logs a warning per repaired object in old PDFs — `logging.level.org.apache.pdfbox: ERROR`.
- Phone photos need EXIF rotation (handled by `ImageDerivatives`); WhatsApp/vlcsnap/IMG names are not captions.
- Without a favicon every page logs a 404 for `/favicon.ico`.

## 8. Open items that recur on every site (owners)

State Emblem file and permission (Department) · organisation logo (Department) · SMTP relay details (NIC) ·
approval of the six draft policies (Department) · staff list, contacts, prices and other carried-over content
(KVK staff) · scanned PDFs (KVK staff) · unreachable related links (KVK staff) · cookie banner and CCPS decisions
(Department/NIC) · production: `KVK_BASE_URL`, `KVK_DB_PASSWORD`, backups of `var/data`, TLS at the proxy,
robots.txt at the host root, rotation of the relay credentials exposed in the legacy `feedback.php`, deletion of
legacy `ip.txt`/`hits.txt` (NIC).

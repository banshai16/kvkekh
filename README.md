# KVK East Khasi Hills — Spring Boot

Replacement for the PHP site in `../kvkekh/public_html`, following **DBIM 3.0** (MeitY Digital Brand Identity
Manual) and **GIGW 3.0**.

- Java 25, Spring Boot 4.1, Thymeleaf (server-side rendering), no database yet
- DBIM **Header 2 + Logo Lockup 3A**, Green colour group, Noto Sans, DBIM footer with lineage
- Accessibility controls from DBIM Figure 44 (dark contrast, invert, saturation, text size ±, highlight
  links, hide images, big cursor), persisted per browser
- Strict CSP (no inline script/style), CSRF, frame/referrer/permissions headers; public pages set no cookies

## Run

```sh
# JDK 25 required
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# http://localhost:8080/kvkekh/
```

| Setting | Environment variable | Default | Purpose |
|---|---|---|---|
| `media.root` | `KVK_MEDIA_ROOT` | `../kvkekh/public_html` | Folder containing `images/`, `publication/`, `newsletter/`, `Actionplan/`, `advertisement/` (read-only) |
| `media.cache-dir` | `KVK_CACHE_DIR` | `./var/image-cache` | Resized images; must be writable, safe to delete |
| datasource | `KVK_DATA_DIR`, `KVK_DB_PASSWORD` | `./var/data` | Embedded H2 database file (feedback log, visitor counter). **Back this folder up.** |
| `spring.mail.*` | `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | not set | SMTP relay for feedback email. Without a host, feedback is saved but not emailed. |
| | `KVK_MAIL_AUTH`, `KVK_MAIL_STARTTLS` | `true`, `true` | Set `KVK_MAIL_AUTH=false` for an IP-whitelisted relay |
| `feedback.sender` | `KVK_MAIL_FROM` | not set | From address the relay accepts (the visitor's address goes in Reply-To) |
| `feedback.recipient` | `KVK_FEEDBACK_TO` | `kvkekhup@yahoo.co.in` | Who receives feedback |

Never put mail credentials in `application.yml` or the repository. The legacy `feedback.php` contains the
NIC Relay Mail username and password in plain text — treat those as exposed and ask NIC to rotate them when
the PHP site is retired. (The legacy SOAP endpoint, `meghalayaonline.gov.in/NicRelayMailWSApplication`, now
returns 404, so the old form has most likely not been delivering mail.)

### Behind a reverse proxy

`server.forward-headers-strategy: native` trusts `X-Forwarded-For` / `X-Forwarded-Proto` only from private
(internal) proxy addresses. If the proxy has a public IP, set `server.tomcat.remoteip.internal-proxies`
to match it; otherwise the rate limiter and visitor counter see the proxy's address for every visitor.

```sh
mvn test             # uses the fixture media in src/test/resources/test-media
mvn package          # executable jar in target/
```

## How content is organised

Everything editable lives in `src/main/resources/content/`:

| File | Holds |
|---|---|
| `pages.yml` | every page: id, URL, title, description, template, parent, last-updated date, legacy `.php` URLs |
| `site.yml` | identity/lockup, footer, contact, related links, global navigation, footer links |
| `albums.yml` | photo albums: folder, title, cover, per-photo captions, legacy album URLs |
| `documents.yml` | downloadable documents (annual reports, leaflets, newsletters) and What's New notices |

- **Migrate or edit a page**: edit `templates/pages/…html`, and set `template:` / `last-updated:` in `pages.yml`.
  Routes, menu, breadcrumbs, "In this section", sitemap, search and 301 redirects follow automatically.
  Startup fails on duplicate URLs or navigation entries that point at unknown pages.
- **Add photos**: copy them into the album folder — they appear immediately. Add a caption in `albums.yml`
  (it is also the image's alt text); without one, descriptive file names are used ("Field preparation for SRI").
- **Add a document or notice**: put the file under the media root and add an entry to `documents.yml`;
  format and size ("PDF, 2.5 MB") are read from the file.
- **Feedback** (`/feedback`): server-side validation with an accessible error summary, a text captcha
  (screen-reader friendly, single use), a hidden honeypot field, CSRF protection and a per-IP hourly limit.
  Each submission is saved first, then emailed as plain text with a reference number (FB-XXXXXX); the database
  copy is deleted after one year (`feedback.retention`). To read saved submissions when mail failed, open the
  H2 file with the H2 console or `java -cp h2.jar org.h2.tools.Shell` and query the `feedback` table
  (`mail_status` shows SENT, FAILED or NOT_CONFIGURED).
- **Visitor counter** continues from the legacy count (10,569). Each visitor is counted once a day using a
  keyed hash that changes daily; no IP addresses are stored, and hashes are deleted nightly. Bots, assets and
  documents are not counted. The legacy `ip.txt` / `hits.txt` (raw IP addresses) should be deleted, not migrated.
- **`/sitemap.xml` and `/robots.txt`** are generated from `pages.yml` and `albums.yml`. Crawlers read
  robots.txt only at the host root, so if the site stays under `/kvkekh`, the root web server should serve it.
- **Images** are never served full-size from the gallery: `/media/{thumb|medium|large}/images/…` returns a
  resized, EXIF-rotated JPEG (thumb ≤ 100 KB, large ≤ 5 MB, per the DBIM imagery checklist), cached on disk.
  Thumbnails for all albums are generated in the background at startup.

## Open items before go-live

- [ ] **State Emblem**: replace `static/assets/img/emblem-placeholder.svg` with the file from an authorised
      source (DBIM 5.1); confirm the KVK's permission to use it, else switch to Lockup 3B / Header 3.
- [ ] **Organisation logo**: `directorate-agriculture-logo.png` is the Directorate of Agriculture logo
      from the old site; confirm it is the right mark.
- [ ] **Content review by KVK staff** (carried over from the legacy site, last updated there in 2020):
      staff list, contact officers, sale prices, host-organisation phone labels "(DA)/(DH)", related-link URLs.
      Search the templates for `TODO`.
- [ ] **Photo captions**: written from what is visible in each photo; staff should add names, places and dates.
- [ ] **Mail relay**: get SMTP relay details for the production server from NIC (host, port, whether it
      needs credentials or IP whitelisting, an accepted From address) and send a test feedback on staging.
- [ ] **Backups**: include `KVK_DATA_DIR` in the server backup.
- [ ] **Draft policies**: approve the six GIGW policy pages under `/policies` (templates marked DRAFT).
- [ ] **Scanned PDFs**: replace the 10 PDFs without a text layer (named in the accessibility statement).
- [ ] **Related links**: kvkribhoi.nic.in, kvkwestgarohills.nic.in, icarzcu3.gov.in did not respond on 16 Oct 2026.
- [ ] **Production**: set `KVK_BASE_URL`, `KVK_DB_PASSWORD`; TLS at the proxy; serve robots.txt at the host root.
- [ ] Decisions: CCPS applicability, DBIM toolkit icons.
- [ ] **State Emblem of India**: `site.yml` currently points at `Emblem_of_Meghalaya.svg` (the State of Meghalaya
      emblem, now labelled correctly). DBIM 5.1 requires the State Emblem of India from an authorised source.
- [x] Cookie consent banner (DBIM 7.6.1) added: Accept / Reject / Customise, "Cookie preferences" in the footer.
      Optional scripts such as analytics must run only when `<html>` has the class `consent-analytics`.
- [ ] Manual screen-reader pass (NVDA, TalkBack), then the STQC (GIGW 3.0) and CERT-In audits.

Pre-audit report (16 Oct 2026): automated accessibility scans, keyboard checks, DBIM Annexure F.1 checklist
and security review — see the published audit report.

See [docs/MIGRATION-PLAYBOOK.md](docs/MIGRATION-PLAYBOOK.md) to repeat this migration for another KVK site, and [tools/README.md](tools/README.md) for the audit scripts.
"# kvkekh" 

package in.nic.kvkekh.web;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

import in.nic.kvkekh.config.SiteProperties;
import in.nic.kvkekh.content.Page;
import in.nic.kvkekh.content.PageRegistry;
import in.nic.kvkekh.gallery.GalleryService;
import in.nic.kvkekh.gallery.GalleryService.AlbumView;

/**
 * {@code sitemap.xml} (every published page and album, with last-modified dates) and {@code robots.txt}.
 * Note: crawlers only read robots.txt at the host root; if this application is deployed under /kvkekh, the
 * root server should serve or link to this one.
 */
@Controller
class SeoController {

    /** Pages that should not appear in search engines. */
    private static final Set<String> EXCLUDED = Set.of("search");

    private final PageRegistry pages;
    private final GalleryService gallery;
    private final SiteProperties site;

    SeoController(PageRegistry pages, GalleryService gallery, SiteProperties site) {
        this.pages = pages;
        this.gallery = gallery;
        this.site = site;
    }

    /** The configured canonical URL; the request's own URL only when none is configured (development). */
    private String base(HttpServletRequest request) {
        if (site.baseUrl() != null && !site.baseUrl().isBlank()) {
            return site.baseUrl().replaceAll("/+$", "");
        }
        return ServletUriComponentsBuilder.fromContextPath(request).build().toUriString();
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    ResponseEntity<String> sitemap(HttpServletRequest request) {
        String base = base(request);
        StringBuilder xml = new StringBuilder("""
                <?xml version="1.0" encoding="UTF-8"?>
                <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
                """);
        for (Page page : pages.all()) {
            if (page.isMigrated() && !EXCLUDED.contains(page.id())) {
                url(xml, base + page.path(), page.lastUpdated());
            }
        }
        for (AlbumView album : gallery.albums()) {
            url(xml, base + album.path(), album.lastUpdated());
        }
        xml.append("</urlset>\n");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .body(xml.toString());
    }

    @GetMapping(value = "/robots.txt", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> robots(HttpServletRequest request) {
        String base = base(request);
        String contextPath = request.getContextPath();
        String body = """
                User-agent: *
                Disallow: %1$s/search
                Disallow: %1$s/feedback/thank-you

                Sitemap: %2$s/sitemap.xml
                """.formatted(contextPath, base);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .body(body);
    }

    private static void url(StringBuilder xml, String location, LocalDate lastModified) {
        xml.append("  <url><loc>").append(HtmlUtils.htmlEscape(location)).append("</loc>");
        if (lastModified != null) {
            xml.append("<lastmod>").append(lastModified).append("</lastmod>");
        }
        xml.append("</url>\n");
    }
}

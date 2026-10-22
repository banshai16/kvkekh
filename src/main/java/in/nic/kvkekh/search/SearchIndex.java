package in.nic.kvkekh.search;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import in.nic.kvkekh.content.Page;
import in.nic.kvkekh.content.PageRegistry;
import in.nic.kvkekh.documents.DocumentLibrary;
import in.nic.kvkekh.documents.DocumentLibrary.Document;
import in.nic.kvkekh.gallery.GalleryService;
import in.nic.kvkekh.gallery.GalleryService.AlbumView;
import in.nic.kvkekh.gallery.GalleryService.Photo;

/**
 * Full-text site search over page text, document titles and PDF contents, and photo album titles and
 * captions (DBIM 3.0 checklist item 42: "HTML, PDF and image metadata"). The index is built at startup;
 * PDF text is extracted in the background and merged in when ready.
 */
@Service
public class SearchIndex {

    private static final Logger log = LoggerFactory.getLogger(SearchIndex.class);

    private static final int MAX_TERMS = 8;
    private static final int MAX_RESULTS = 30;
    private static final int MAX_PDF_PAGES = 60;
    private static final int MAX_PDF_CHARS = 200_000;
    private static final Set<String> NOT_INDEXED = Set.of("search", "sitemap");
    private static final Pattern THYMELEAF_COMMENT = Pattern.compile("<!--/\\*.*?\\*/-->", Pattern.DOTALL);
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    public record Fragment(String text, boolean match) {
    }

    public record Hit(String title, String url, String kind, String meta, List<Fragment> snippet) {
    }

    private record Entry(String title, String url, String kind, String meta, String text, int weight,
            String haystack, String lowerTitle) {
    }

    private final PageRegistry pages;
    private final DocumentLibrary documents;
    private final GalleryService gallery;
    private final ResourceLoader resources;
    private final Map<String, String> pdfText = new ConcurrentHashMap<>();
    private volatile List<Entry> entries = List.of();

    public SearchIndex(PageRegistry pages, DocumentLibrary documents, GalleryService gallery, ResourceLoader resources) {
        this.pages = pages;
        this.documents = documents;
        this.gallery = gallery;
        this.resources = resources;
        rebuild();
    }

    /** Extracts PDF text off the request path, then rebuilds the index with it. */
    @EventListener(ApplicationReadyEvent.class)
    void indexPdfsInBackground() {
        Thread.ofVirtual().name("pdf-indexer").start(() -> {
            long start = System.nanoTime();
            for (var collection : documents.all().values()) {
                for (Document d : collection.documents()) {
                    if (d.path() != null && "PDF".equals(d.type()) && !pdfText.containsKey(d.url())) {
                        pdfText.put(d.url(), extractPdf(d));
                    }
                }
            }
            rebuild();
            log.info("Search index: {} entries, text of {} PDFs indexed in {} ms", entries.size(), pdfText.size(),
                    (System.nanoTime() - start) / 1_000_000);
        });
    }

    public List<Hit> search(String query) {
        List<String> terms = Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+"))
                .filter(t -> !t.isBlank())
                .distinct()
                .limit(MAX_TERMS)
                .toList();
        if (terms.isEmpty()) {
            return List.of();
        }
        return entries.stream()
                .filter(e -> terms.stream().allMatch(e.haystack()::contains))
                .sorted(Comparator.comparingInt((Entry e) -> -score(e, terms)).thenComparing(Entry::title))
                .limit(MAX_RESULTS)
                .map(e -> new Hit(e.title(), e.url(), e.kind(), e.meta(), snippet(e.text(), terms)))
                .toList();
    }

    synchronized void rebuild() {
        List<Entry> built = new ArrayList<>();
        for (Page page : pages.all()) {
            if (page.isMigrated() && !NOT_INDEXED.contains(page.id())) {
                String text = join(page.description(), templateText(page.template()));
                built.add(entry(page.title(), page.path(), "Page", null, text, 3));
            }
        }
        for (var collection : documents.all().values()) {
            for (Document d : collection.documents()) {
                if (d.available() && d.download()) {
                    String meta = d.size() != null ? d.type() + ", " + d.size() : d.type();
                    String text = join(collection.title(), pdfText.get(d.url()));
                    built.add(entry(d.title(), d.url(), "Document", meta, text, 1));
                }
            }
        }
        for (AlbumView album : gallery.albums()) {
            String captions = album.photos().stream()
                    .map(Photo::caption).filter(c -> c != null)
                    .collect(Collectors.joining(". "));
            String meta = album.photos().size() == 1 ? "1 photo" : album.photos().size() + " photos";
            built.add(entry(album.album().title(), album.path(), "Photo album", meta,
                    join(album.album().description(), captions), 2));
        }
        entries = List.copyOf(built);
    }

    private static Entry entry(String title, String url, String kind, String meta, String text, int weight) {
        String body = text == null ? "" : text;
        return new Entry(title, url, kind, meta, body, weight,
                (title + " " + body).toLowerCase(Locale.ROOT), title.toLowerCase(Locale.ROOT));
    }

    private static int score(Entry e, List<String> terms) {
        int score = e.weight();
        for (String term : terms) {
            if (e.lowerTitle().contains(term)) {
                score += 10;
            }
        }
        return score;
    }

    /** About 220 characters around the first match, split into plain and matching fragments for highlighting. */
    static List<Fragment> snippet(String text, List<String> terms) {
        if (text.isEmpty()) {
            return List.of();
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int first = terms.stream().mapToInt(lower::indexOf).filter(i -> i >= 0).min().orElse(0);
        int start = Math.max(0, first - 80);
        int end = Math.min(text.length(), start + 220);
        if (start > 0) {
            int space = text.indexOf(' ', start);
            start = space >= 0 && space < first ? space + 1 : start;
        }
        if (end < text.length()) {
            int space = text.lastIndexOf(' ', end);
            end = space > first ? space : end;
        }
        String window = (start > 0 ? "… " : "") + text.substring(start, end) + (end < text.length() ? " …" : "");
        String lowerWindow = window.toLowerCase(Locale.ROOT);

        List<Fragment> fragments = new ArrayList<>();
        int pos = 0;
        while (pos < window.length()) {
            int next = -1;
            String hit = null;
            for (String term : terms) {
                int i = lowerWindow.indexOf(term, pos);
                if (i >= 0 && (next < 0 || i < next)) {
                    next = i;
                    hit = term;
                }
            }
            if (next < 0) {
                fragments.add(new Fragment(window.substring(pos), false));
                break;
            }
            if (next > pos) {
                fragments.add(new Fragment(window.substring(pos, next), false));
            }
            fragments.add(new Fragment(window.substring(next, next + hit.length()), true));
            pos = next + hit.length();
        }
        return fragments;
    }

    private String templateText(String template) {
        Resource resource = resources.getResource("classpath:templates/" + template + ".html");
        try (InputStream in = resource.getInputStream()) {
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            html = THYMELEAF_COMMENT.matcher(html).replaceAll(" ");
            html = TAG.matcher(html).replaceAll(" ");
            return SPACE.matcher(HtmlUtils.htmlUnescape(html)).replaceAll(" ").strip();
        } catch (IOException e) {
            log.warn("Cannot index template {}: {}", template, e.toString());
            return "";
        }
    }

    private static String extractPdf(Document d) {
        try (PDDocument pdf = Loader.loadPDF(d.path().toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setEndPage(MAX_PDF_PAGES);
            String text = SPACE.matcher(stripper.getText(pdf)).replaceAll(" ").strip();
            return text.length() > MAX_PDF_CHARS ? text.substring(0, MAX_PDF_CHARS) : text;
        } catch (IOException | RuntimeException e) {
            log.warn("Cannot extract text from {}: {}", d.path(), e.toString());
            return "";
        }
    }

    private static String join(String a, String b) {
        return ((a == null ? "" : a) + " " + (b == null ? "" : b)).strip();
    }
}

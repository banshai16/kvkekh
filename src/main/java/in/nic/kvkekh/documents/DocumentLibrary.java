package in.nic.kvkekh.documents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import in.nic.kvkekh.config.MediaProperties;
import in.nic.kvkekh.documents.DocumentsProperties.Item;
import in.nic.kvkekh.media.MediaUrls;

/**
 * Resolves the document collections at startup: file type and size are read from disk, so every download
 * link shows "(PDF, 2.5 MB)" as GIGW requires. Missing files are logged and listed without a link.
 */
@Service
public class DocumentLibrary {

    private static final Logger log = LoggerFactory.getLogger(DocumentLibrary.class);
    private static final List<String> FOLDERS = List.of("publication", "newsletter", "Actionplan", "advertisement", "images");

    /** @param url context-relative URL, or null when the file is missing */
    public record Document(String title, String url, String type, String size, String lang, String date,
            boolean download, Path path, LocalDate archived) {

        public boolean available() {
            return url != null;
        }
    }

    public record Collection(String id, String title, String description, List<Document> documents) {

        /** Documents that are not archived. */
        public List<Document> current() {
            return documents.stream().filter(d -> d.archived() == null).toList();
        }

        public List<Document> archived() {
            return documents.stream().filter(d -> d.archived() != null).toList();
        }
    }

    private final Map<String, Collection> collections = new LinkedHashMap<>();

    public DocumentLibrary(DocumentsProperties properties, MediaProperties media, MediaUrls urls) {
        Path root = media.rootPath();
        for (DocumentsProperties.Collection c : properties.collections()) {
            List<Document> documents = c.items().stream().map(item -> resolve(item, root, urls)).toList();
            if (collections.putIfAbsent(c.id(), new Collection(c.id(), c.title(), c.description(), documents)) != null) {
                throw new IllegalStateException("Duplicate document collection id: " + c.id());
            }
        }
    }

    public Map<String, Collection> all() {
        return collections;
    }

    public Optional<Collection> find(String id) {
        return Optional.ofNullable(collections.get(id));
    }

    private static Document resolve(Item item, Path root, MediaUrls urls) {
        if (item.link() != null) {
            return new Document(item.title(), item.link(), null, null, item.lang(), item.date(), false, null, item.archived());
        }
        String relative = item.file().replace('\\', '/');
        Path file = root.resolve(relative).normalize();
        boolean allowed = FOLDERS.stream().anyMatch(folder -> file.startsWith(root.resolve(folder)));
        if (!allowed || !Files.isRegularFile(file)) {
            log.warn("Document '{}' not found or outside the media folders: {}", item.title(), file);
            return new Document(item.title(), null, null, null, item.lang(), item.date(), true, null, item.archived());
        }
        try {
            return new Document(item.title(), urls.file(relative), type(relative), size(Files.size(file)),
                    item.lang(), item.date(), true, file, item.archived());
        } catch (IOException e) {
            log.warn("Cannot read size of {}: {}", file, e.toString());
            return new Document(item.title(), urls.file(relative), type(relative), null, item.lang(), item.date(), true, file, item.archived());
        }
    }

    private static String type(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? "File" : path.substring(dot + 1).toUpperCase(Locale.ROOT);
    }

    static String size(long bytes) {
        if (bytes < 1000 * 1024) {
            return Math.max(1, Math.round(bytes / 1024.0)) + " KB";
        }
        return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024.0));
    }
}

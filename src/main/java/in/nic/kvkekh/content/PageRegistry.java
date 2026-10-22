package in.nic.kvkekh.content;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Component;

/**
 * Lookup over the content registry. Fails fast at startup on duplicate ids, paths or legacy URLs,
 * or on a parent reference that does not exist.
 */
@Component
public class PageRegistry {

    private final Map<String, Page> byId = new LinkedHashMap<>();
    private final Map<String, Page> byPath = new LinkedHashMap<>();
    private final Map<String, Page> byLegacyPath = new LinkedHashMap<>();

    public PageRegistry(ContentProperties content) {
        for (Page page : content.pages()) {
            requireAbsolute(page.path(), page);
            putUnique(byId, page.id(), page, "id");
            putUnique(byPath, page.path(), page, "path");
            for (String legacy : page.legacy()) {
                requireAbsolute(legacy, page);
                putUnique(byLegacyPath, legacy, page, "legacy path");
            }
        }
        for (Page page : byId.values()) {
            if (page.parent() != null && !byId.containsKey(page.parent())) {
                throw new IllegalStateException("Page '" + page.id() + "' has unknown parent '" + page.parent() + "'");
            }
        }
    }

    public Collection<Page> all() {
        return byId.values();
    }

    public Optional<Page> findById(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public Page require(String id) {
        return findById(id).orElseThrow(() -> new IllegalStateException("Unknown page id '" + id + "'"));
    }

    public Optional<Page> findByPath(String path) {
        return Optional.ofNullable(byPath.get(path));
    }

    public Optional<Page> findByLegacyPath(String legacyPath) {
        return Optional.ofNullable(byLegacyPath.get(legacyPath));
    }

    public Collection<String> legacyPaths() {
        return byLegacyPath.keySet();
    }

    private static void putUnique(Map<String, Page> map, String key, Page page, String what) {
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Page " + page + " is missing its " + what);
        }
        Page existing = map.putIfAbsent(key, page);
        if (existing != null) {
            throw new IllegalStateException("Duplicate " + what + " '" + key + "' on pages '"
                    + existing.id() + "' and '" + page.id() + "'");
        }
    }

    private static void requireAbsolute(String path, Page page) {
        if (path == null || !path.startsWith("/")) {
            throw new IllegalStateException("Page '" + page.id() + "' has a path not starting with '/': " + path);
        }
    }
}

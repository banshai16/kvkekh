package in.nic.kvkekh.web;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import in.nic.kvkekh.content.Page;
import in.nic.kvkekh.content.PageRegistry;
import in.nic.kvkekh.gallery.GalleryProperties;
import in.nic.kvkekh.gallery.GalleryProperties.Album;

/** Every legacy PHP URL and its new location, from pages.yml and albums.yml. Duplicates fail startup. */
@Component
public class LegacyRedirects {

    private final Map<String, String> targets = new LinkedHashMap<>();

    public LegacyRedirects(PageRegistry pages, GalleryProperties gallery) {
        for (Page page : pages.all()) {
            page.legacy().forEach(legacy -> add(legacy, page.path()));
        }
        for (Album album : gallery.albums()) {
            album.legacy().forEach(legacy -> add(legacy, album.path()));
        }
    }

    public Optional<String> target(String legacyPath) {
        return Optional.ofNullable(targets.get(legacyPath));
    }

    public Set<String> paths() {
        return Collections.unmodifiableSet(targets.keySet());
    }

    private void add(String legacy, String target) {
        if (!legacy.startsWith("/")) {
            throw new IllegalStateException("Legacy path must start with '/': " + legacy);
        }
        String existing = targets.putIfAbsent(legacy, target);
        if (existing != null) {
            throw new IllegalStateException("Legacy path " + legacy + " redirects to both " + existing + " and " + target);
        }
    }
}

package in.nic.kvkekh.gallery;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Photo albums, bound from {@code content/albums.yml}. Photos are discovered from each album's folder. */
@ConfigurationProperties("gallery")
public record GalleryProperties(List<Album> albums) {

    public GalleryProperties {
        albums = albums == null ? List.of() : List.copyOf(albums);
        long distinct = albums.stream().map(Album::slug).distinct().count();
        if (distinct != albums.size()) {
            throw new IllegalArgumentException("Album slugs in albums.yml must be unique");
        }
    }

    /**
     * @param folder   media-relative folder, e.g. {@code images/photogallery/Crops}
     * @param cover    file name of the cover photo; defaults to the first photo
     * @param captions optional captions per file; files without one get a caption from a descriptive file name
     */
    public record Album(
            String slug,
            String title,
            String description,
            String folder,
            String cover,
            List<String> legacy,
            List<Caption> captions) {

        public Album {
            if (slug == null || !slug.matches("[a-z0-9]+(-[a-z0-9]+)*")) {
                throw new IllegalArgumentException("Album slug must be lower-case words joined by hyphens: " + slug);
            }
            legacy = legacy == null ? List.of() : List.copyOf(legacy);
            captions = captions == null ? List.of() : List.copyOf(captions);
        }

        public String path() {
            return "/gallery/" + slug;
        }
    }

    public record Caption(String file, String text) {
    }
}

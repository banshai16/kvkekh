package in.nic.kvkekh.gallery;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import in.nic.kvkekh.config.MediaProperties;
import in.nic.kvkekh.gallery.GalleryProperties.Album;
import in.nic.kvkekh.gallery.GalleryProperties.Caption;
import in.nic.kvkekh.media.MediaUrls;

/**
 * Album and photo lookup. Folders are listed on each request (they hold tens of files), so photos added to
 * a folder appear without a restart; captions and album metadata come from {@code albums.yml}.
 */
@Service
public class GalleryService {

    private static final Logger log = LoggerFactory.getLogger(GalleryService.class);

    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    /** Camera-generated name parts that never make a useful caption (IMG_2019…, DSC00705, WA0010, …). */
    private static final Set<String> CAMERA_WORDS = Set.of("img", "dsc", "dscn", "image", "photo", "pic", "hdr",
            "wa", "pxl", "screenshot", "copy", "new", "jpg", "jpeg", "png", "whatsapp", "signal", "telegram", "vlcsnap");
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");

    public record Photo(String id, String file, String path, String caption, int number, String thumbUrl,
            String mediumUrl, String largeUrl) {

        /** Text alternative: the caption, or a positional description when the photo has none. */
        public String alt(String albumTitle, int total) {
            return caption != null ? caption : albumTitle + ", photo " + number + " of " + total;
        }
    }

    public record AlbumView(Album album, List<Photo> photos, Photo cover, LocalDate lastUpdated) {

        public String path() {
            return album.path();
        }

        public Optional<Photo> photo(String id) {
            return photos.stream().filter(p -> p.id().equals(id)).findFirst();
        }
    }

    private final GalleryProperties gallery;
    private final Path mediaRoot;
    private final MediaUrls urls;

    public GalleryService(GalleryProperties gallery, MediaProperties media, MediaUrls urls) {
        this.gallery = gallery;
        this.mediaRoot = media.rootPath();
        this.urls = urls;
    }

    public List<AlbumView> albums() {
        return gallery.albums().stream().map(this::view).filter(view -> !view.photos().isEmpty()).toList();
    }

    public Optional<AlbumView> album(String slug) {
        return gallery.albums().stream()
                .filter(album -> album.slug().equals(slug))
                .findFirst()
                .map(this::view)
                .filter(view -> !view.photos().isEmpty());
    }

    /** Media-relative paths of every photo, for cache warm-up. */
    public List<String> allPhotoPaths() {
        return albums().stream().flatMap(a -> a.photos().stream()).map(Photo::path).toList();
    }

    private AlbumView view(Album album) {
        Path folder = mediaRoot.resolve(album.folder()).normalize();
        if (!folder.startsWith(mediaRoot.resolve("images")) || !Files.isDirectory(folder)) {
            log.warn("Album '{}' folder not found: {}", album.slug(), folder);
            return new AlbumView(album, List.of(), null, null);
        }
        List<Path> files;
        try (Stream<Path> listing = Files.list(folder)) {
            files = listing.filter(Files::isRegularFile)
                    .filter(GalleryService::isImage)
                    .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase(Locale.ROOT)))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Map<String, String> captions = album.captions().stream()
                .collect(Collectors.toMap(Caption::file, Caption::text, (a, b) -> b));
        Set<String> usedIds = new HashSet<>();
        List<Photo> photos = new ArrayList<>(files.size());
        Instant newest = Instant.EPOCH;
        for (Path file : files) {
            String name = file.getFileName().toString();
            String relative = mediaRoot.relativize(file).toString().replace('\\', '/');
            String caption = captions.getOrDefault(name, captionFromFileName(stem(name)));
            photos.add(new Photo(uniqueId(stem(name), usedIds), name, relative, caption, photos.size() + 1,
                    urls.image(relative, "thumb"), urls.image(relative, "medium"), urls.image(relative, "large")));
            try {
                Instant modified = Files.getLastModifiedTime(file).toInstant();
                newest = modified.isAfter(newest) ? modified : newest;
            } catch (IOException e) {
                log.debug("Cannot read modification time of {}", file);
            }
        }
        Photo cover = photos.stream()
                .filter(p -> p.file().equals(album.cover()))
                .findFirst()
                .orElse(photos.isEmpty() ? null : photos.getFirst());
        LocalDate lastUpdated = photos.isEmpty() ? null : LocalDate.ofInstant(newest, ZONE);
        return new AlbumView(album, List.copyOf(photos), cover, lastUpdated);
    }

    private static boolean isImage(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        return dot > 0 && EXTENSIONS.contains(name.substring(dot + 1));
    }

    private static String stem(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private static String uniqueId(String stem, Set<String> used) {
        String base = NON_SLUG.matcher(stem.toLowerCase(Locale.ROOT)).replaceAll("-").replaceAll("^-+|-+$", "");
        if (base.isEmpty()) {
            base = "photo";
        }
        String id = base;
        for (int n = 2; !used.add(id); n++) {
            id = base + "-" + n;
        }
        return id;
    }

    /**
     * Uses the file name as a caption when it reads like words a person typed ("Field preparation for SRI"),
     * and returns null for camera-generated names ("IMG_20191111_112947496_HDR", "DSC00705").
     */
    static String captionFromFileName(String stem) {
        String text = stem.replace('_', ' ').replace('-', ' ').trim().replaceAll("\\s+", " ");
        boolean descriptive = Arrays.stream(text.split(" ")).anyMatch(word -> word.length() >= 3
                && word.chars().allMatch(Character::isLetter)
                && word.chars().anyMatch(Character::isLowerCase)
                && !CAMERA_WORDS.contains(word.toLowerCase(Locale.ROOT)));
        if (!descriptive) {
            return null;
        }
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}

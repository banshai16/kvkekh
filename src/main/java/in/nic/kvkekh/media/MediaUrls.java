package in.nic.kvkekh.media;

import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Component;
import org.springframework.web.util.UriUtils;

/**
 * Builds URLs for media files and image derivatives, percent-encoding each path segment (album folders and
 * file names contain spaces). Templates with fixed, space-free paths can write {@code @{/media/medium/images/…}}
 * directly; Thymeleaf 3.1 does not allow bean calls inside link expressions.
 */
@Component
public class MediaUrls {

    /** URL of a resized copy: {@code /media/{size}/images/...} with each path segment percent-encoded. */
    public String image(String relativePath, String size) {
        ImageSize.fromKey(size).orElseThrow(() -> new IllegalArgumentException("Unknown image size: " + size));
        return "/media/" + size + "/" + encode(relativePath);
    }

    /** URL of an original media file under its legacy path, e.g. {@code /publication/Annual%20Report%202021.pdf}. */
    public String file(String relativePath) {
        return "/" + encode(relativePath);
    }

    private static String encode(String relativePath) {
        String path = relativePath.startsWith("/") ? relativePath.substring(1) : relativePath;
        return UriUtils.encodePath(path, StandardCharsets.UTF_8);
    }
}

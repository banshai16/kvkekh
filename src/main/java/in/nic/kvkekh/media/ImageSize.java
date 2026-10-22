package in.nic.kvkekh.media;

import java.util.Arrays;
import java.util.Optional;

/**
 * Image derivative sizes. Limits follow the DBIM 3.0 imagery checklist: thumbnails under 100 KB,
 * banner/display images under 500 KB where practical, high-resolution images under 5 MB.
 */
public enum ImageSize {

    THUMB("thumb", 640, 0.78f, 100 * 1024),
    MEDIUM("medium", 1280, 0.82f, 500 * 1024),
    LARGE("large", 2048, 0.86f, 5 * 1024 * 1024);

    private final String key;
    private final int maxEdge;
    private final float quality;
    private final long maxBytes;

    ImageSize(String key, int maxEdge, float quality, long maxBytes) {
        this.key = key;
        this.maxEdge = maxEdge;
        this.quality = quality;
        this.maxBytes = maxBytes;
    }

    public String key() {
        return key;
    }

    public int maxEdge() {
        return maxEdge;
    }

    public float quality() {
        return quality;
    }

    public long maxBytes() {
        return maxBytes;
    }

    public static Optional<ImageSize> fromKey(String key) {
        return Arrays.stream(values()).filter(size -> size.key.equals(key)).findFirst();
    }
}

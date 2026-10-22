package in.nic.kvkekh.media;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;

import in.nic.kvkekh.config.MediaProperties;

/**
 * Creates resized JPEG copies of images under {@code media.root/images}, cached on disk under
 * {@code media.cache-dir}. Originals are never modified. EXIF orientation is applied, so phone photos
 * display upright. The cache key includes the source size and modification time, so replacing a photo
 * produces a fresh derivative.
 */
@Service
public class ImageDerivatives {

    private static final Logger log = LoggerFactory.getLogger(ImageDerivatives.class);

    private static final Set<String> EXTENSIONS = Set.of("jpg", "jpeg", "png");
    private static final float MIN_QUALITY = 0.5f;

    private final Path root;
    private final Path imagesRoot;
    private final Path cache;
    /** Decoding a 12-megapixel photo needs ~50 MB; cap concurrent work to keep memory bounded. */
    private final Semaphore permits = new Semaphore(2);
    private final ConcurrentHashMap<Path, Object> locks = new ConcurrentHashMap<>();

    public ImageDerivatives(MediaProperties media) {
        this.root = media.rootPath();
        this.imagesRoot = root.resolve("images");
        this.cache = media.cachePath();
    }

    /** The original file for a media-relative path such as {@code images/photogallery/Crops/Maize.JPG}. */
    public Optional<Path> original(String relativePath) {
        if (relativePath == null) {
            return Optional.empty();
        }
        String relative = relativePath.replace('\\', '/');
        while (relative.startsWith("/")) {
            relative = relative.substring(1);
        }
        String lower = relative.toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || !EXTENSIONS.contains(lower.substring(dot + 1))) {
            return Optional.empty();
        }
        Path file = root.resolve(relative).normalize();
        if (!file.startsWith(imagesRoot) || !Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(file);
    }

    /** The cached derivative, generating it first if needed. Empty if the source is missing or unreadable. */
    public Optional<Path> derivative(String relativePath, ImageSize size) {
        return original(relativePath).flatMap(source -> {
            try {
                return Optional.of(ensure(source, size));
            } catch (IOException | RuntimeException e) {
                log.warn("Could not create {} image for {}: {}", size.key(), source, e.toString());
                return Optional.empty();
            }
        });
    }

    private Path ensure(Path source, ImageSize size) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(source, BasicFileAttributes.class);
        String key = sha256(root.relativize(source) + "|" + size + "|" + attrs.size() + "|"
                + attrs.lastModifiedTime().toMillis());
        Path target = cache.resolve(size.key()).resolve(key.substring(0, 2)).resolve(key + ".jpg");
        if (Files.isRegularFile(target)) {
            return target;
        }
        Object lock = locks.computeIfAbsent(target, k -> new Object());
        synchronized (lock) {
            try {
                if (!Files.isRegularFile(target)) {
                    permits.acquireUninterruptibly();
                    try {
                        generate(source, target, size);
                    } finally {
                        permits.release();
                    }
                }
            } finally {
                locks.remove(target);
            }
        }
        return target;
    }

    private void generate(Path source, Path target, ImageSize size) throws IOException {
        int orientation = exifOrientation(source);
        BufferedImage image = read(source, size.maxEdge());
        image = orient(scaleToFit(toRgb(image), size.maxEdge()), orientation);

        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), "tmp-", ".jpg");
        try {
            float quality = size.quality();
            writeJpeg(image, temp, quality);
            while (Files.size(temp) > size.maxBytes() && quality > MIN_QUALITY) {
                quality -= 0.08f;
                writeJpeg(image, temp, quality);
            }
            Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Decodes with source subsampling, so huge photos are never fully decoded just to make a thumbnail. */
    private static BufferedImage read(Path source, int maxEdge) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(source.toFile())) {
            if (in == null) {
                throw new IOException("Cannot open " + source);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("Unsupported image format: " + source);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                int longEdge = Math.max(reader.getWidth(0), reader.getHeight(0));
                int factor = Math.max(1, longEdge / (maxEdge * 2));
                ImageReadParam param = reader.getDefaultReadParam();
                if (factor > 1) {
                    param.setSourceSubsampling(factor, factor, 0, 0);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    private static int exifOrientation(Path source) {
        try {
            Metadata metadata = ImageMetadataReader.readMetadata(source.toFile());
            ExifIFD0Directory exif = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exif != null && exif.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return exif.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            }
        } catch (Exception e) {
            log.debug("No readable EXIF orientation in {}: {}", source, e.toString());
        }
        return 1;
    }

    /** Flattens alpha onto white and normalises the pixel layout for the JPEG encoder. */
    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rgb.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, image.getWidth(), image.getHeight());
            g.drawImage(image, 0, 0, null);
        } finally {
            g.dispose();
        }
        return rgb;
    }

    /** Downscales in halving steps, which avoids the aliasing of a single large bilinear step. */
    private static BufferedImage scaleToFit(BufferedImage image, int maxEdge) {
        int width = image.getWidth();
        int height = image.getHeight();
        double scale = (double) maxEdge / Math.max(width, height);
        if (scale >= 1.0) {
            return image;
        }
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        BufferedImage current = image;
        do {
            width = Math.max(targetWidth, width / 2);
            height = Math.max(targetHeight, height / 2);
            BufferedImage step = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = step.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
                g.drawImage(current, 0, 0, width, height, null);
            } finally {
                g.dispose();
            }
            current = step;
        } while (width != targetWidth || height != targetHeight);
        return current;
    }

    /** Applies EXIF orientation 2–8 so the stored pixels are upright. */
    static BufferedImage orient(BufferedImage image, int orientation) {
        int w = image.getWidth();
        int h = image.getHeight();
        AffineTransform t = switch (orientation) {
            case 2 -> new AffineTransform(-1, 0, 0, 1, w, 0);   // mirror horizontal
            case 3 -> new AffineTransform(-1, 0, 0, -1, w, h);  // rotate 180
            case 4 -> new AffineTransform(1, 0, 0, -1, 0, h);   // mirror vertical
            case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);    // transpose
            case 6 -> new AffineTransform(0, 1, -1, 0, h, 0);   // rotate 90 clockwise
            case 7 -> new AffineTransform(0, -1, -1, 0, h, w);  // transverse
            case 8 -> new AffineTransform(0, -1, 1, 0, 0, w);   // rotate 90 anti-clockwise
            default -> null;
        };
        if (t == null) {
            return image;
        }
        boolean swap = orientation >= 5;
        BufferedImage out = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        try {
            g.drawImage(image, t, null);
        } finally {
            g.dispose();
        }
        return out;
    }

    private static void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        // A file-backed ImageOutputStream does not truncate, so start from an empty file on every attempt.
        Files.deleteIfExists(target);
        try (ImageOutputStream out = ImageIO.createImageOutputStream(target.toFile())) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(quality);
            param.setProgressiveMode(ImageWriteParam.MODE_DEFAULT);
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}

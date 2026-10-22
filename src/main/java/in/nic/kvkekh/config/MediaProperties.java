package in.nic.kvkekh.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Where the legacy media lives and where generated image derivatives are cached.
 *
 * @param root      folder containing {@code images/}, {@code publication/}, {@code newsletter/}, … (read-only)
 * @param cacheDir  writable folder for resized images; safe to delete, it is rebuilt on demand
 * @param warmCache generate gallery thumbnails in the background at startup
 */
@ConfigurationProperties("media")
public record MediaProperties(String root, String cacheDir, @DefaultValue("true") boolean warmCache) {

    public Path rootPath() {
        return Path.of(root).toAbsolutePath().normalize();
    }

    public Path cachePath() {
        return Path.of(cacheDir).toAbsolutePath().normalize();
    }
}

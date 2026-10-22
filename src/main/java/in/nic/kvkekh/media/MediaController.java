package in.nic.kvkekh.media;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

/** Serves resized images: {@code /media/{thumb|medium|large}/images/...}. */
@Controller
class MediaController {

    private final ImageDerivatives images;

    MediaController(ImageDerivatives images) {
        this.images = images;
    }

    @GetMapping("/media/{size}/{*path}")
    ResponseEntity<Resource> image(@PathVariable String size, @PathVariable String path, WebRequest request)
            throws IOException {
        ImageSize imageSize = ImageSize.fromKey(size)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Path file = images.derivative(path, imageSize)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        long lastModified = Files.getLastModifiedTime(file).toMillis();
        if (request.checkNotModified(lastModified)) {
            return null;
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                .lastModified(lastModified)
                .body(new FileSystemResource(file));
    }
}

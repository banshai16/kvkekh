package in.nic.kvkekh.gallery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import in.nic.kvkekh.config.MediaProperties;
import in.nic.kvkekh.media.ImageDerivatives;
import in.nic.kvkekh.media.ImageSize;

/** Generates missing gallery thumbnails in the background after startup, so the first visitors don't wait. */
@Component
class ThumbnailWarmer {

    private static final Logger log = LoggerFactory.getLogger(ThumbnailWarmer.class);

    private final MediaProperties media;
    private final GalleryService gallery;
    private final ImageDerivatives images;

    ThumbnailWarmer(MediaProperties media, GalleryService gallery, ImageDerivatives images) {
        this.media = media;
        this.gallery = gallery;
        this.images = images;
    }

    @EventListener(ApplicationReadyEvent.class)
    void warm() {
        if (!media.warmCache()) {
            return;
        }
        Thread.ofVirtual().name("thumbnail-warmer").start(() -> {
            long start = System.nanoTime();
            var paths = gallery.allPhotoPaths();
            long created = paths.stream().filter(path -> images.derivative(path, ImageSize.THUMB).isPresent()).count();
            log.info("Gallery thumbnails ready: {} of {} in {} ms", created, paths.size(),
                    (System.nanoTime() - start) / 1_000_000);
        });
    }
}

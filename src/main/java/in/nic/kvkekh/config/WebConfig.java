package in.nic.kvkekh.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.AbstractResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

/**
 * Serves the legacy media folders from disk under their original URLs, so existing links to PDFs and
 * images (e.g. {@code /kvkekh/publication/AnnualReport2018-19.pdf}) keep working without redirects.
 * Only published file types are served: stray files in those folders (Thumbs.db, editable .docx drafts,
 * unpublished videos) return 404.
 */
@Configuration
class WebConfig implements WebMvcConfigurer {

    static final List<String> MEDIA_FOLDERS = List.of("images", "publication", "newsletter", "Actionplan", "advertisement");
    static final Set<String> PUBLISHED_TYPES = Set.of("jpg", "jpeg", "png", "gif", "webp", "pdf");

    private final Path mediaRoot;

    WebConfig(MediaProperties media) {
        this.mediaRoot = media.rootPath();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        for (String folder : MEDIA_FOLDERS) {
            registry.addResourceHandler("/" + folder + "/**")
                    .addResourceLocations(mediaRoot.resolve(folder).toUri().toString())
                    .setCacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                    .resourceChain(true)
                    .addResolver(new PublishedTypesOnly());
        }
    }

    /** Resolves a media file only if its extension is one of {@link #PUBLISHED_TYPES}. */
    static final class PublishedTypesOnly extends AbstractResourceResolver {

        @Override
        protected Resource resolveResourceInternal(HttpServletRequest request, String requestPath,
                List<? extends Resource> locations, ResourceResolverChain chain) {
            return isPublished(requestPath) ? chain.resolveResource(request, requestPath, locations) : null;
        }

        @Override
        protected String resolveUrlPathInternal(String resourceUrlPath, List<? extends Resource> locations,
                ResourceResolverChain chain) {
            return isPublished(resourceUrlPath) ? chain.resolveUrlPath(resourceUrlPath, locations) : null;
        }

        private static boolean isPublished(String path) {
            int dot = path.lastIndexOf('.');
            return dot > 0 && PUBLISHED_TYPES.contains(path.substring(dot + 1).toLowerCase(Locale.ROOT));
        }
    }
}

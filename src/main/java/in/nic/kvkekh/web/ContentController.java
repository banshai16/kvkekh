package in.nic.kvkekh.web;

import java.net.URI;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerMapping;

import in.nic.kvkekh.content.Page;
import in.nic.kvkekh.content.PageModel;
import in.nic.kvkekh.content.PageModelContributor;
import in.nic.kvkekh.content.PageRegistry;

/**
 * Renders every page of the content registry and redirects legacy PHP URLs. The handler methods carry no
 * mapping annotations: {@link ContentRoutes} registers one exact-path mapping per page and per legacy URL.
 */
@Controller
public class ContentController {

    private final PageRegistry pages;
    private final PageModel pageModel;
    private final LegacyRedirects redirects;
    private final List<PageModelContributor> contributors;

    public ContentController(PageRegistry pages, PageModel pageModel, LegacyRedirects redirects,
            List<PageModelContributor> contributors) {
        this.pages = pages;
        this.pageModel = pageModel;
        this.redirects = redirects;
        this.contributors = contributors;
    }

    public String page(HttpServletRequest request, Model model) {
        Page page = pages.findByPath(matchedPattern(request))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        pageModel.apply(model, page);
        for (PageModelContributor contributor : contributors) {
            if (contributor.supports(page)) {
                contributor.contribute(page, request, model);
            }
        }
        return page.isMigrated() ? page.template() : "pages/pending";
    }

    public ResponseEntity<Void> legacyRedirect(HttpServletRequest request) {
        String target = redirects.target(matchedPattern(request))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        return ResponseEntity.status(HttpStatus.MOVED_PERMANENTLY)
                .location(URI.create(request.getContextPath() + target))
                .build();
    }

    private static String matchedPattern(HttpServletRequest request) {
        return (String) request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
    }
}

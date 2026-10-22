package in.nic.kvkekh.visitors;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/** Counts successful HTML page views by people — not assets, media, documents, errors or known bots. */
@Component
class VisitorCountingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(VisitorCountingFilter.class);

    private static final List<String> SKIPPED_PREFIXES = List.of(
            "/assets/", "/media/", "/images/", "/publication/", "/newsletter/", "/Actionplan/", "/advertisement/", "/error");
    private static final Pattern BOTS = Pattern.compile(
            "bot|crawl|spider|slurp|preview|monitor|curl|wget|python|java/|httpclient|okhttp|headless|lighthouse",
            Pattern.CASE_INSENSITIVE);

    private final VisitorCounter counter;

    VisitorCountingFilter(VisitorCounter counter) {
        this.counter = counter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!"GET".equals(request.getMethod())) {
            return true;
        }
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        String agent = request.getHeader(HttpHeaders.USER_AGENT);
        if (accept == null || !accept.contains("text/html") || agent == null || BOTS.matcher(agent).find()) {
            return true;
        }
        String path = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
        return SKIPPED_PREFIXES.stream().anyMatch(path::startsWith) || path.endsWith(".xml") || path.endsWith(".txt");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        if (response.getStatus() == HttpServletResponse.SC_OK) {
            try {
                counter.record(request.getRemoteAddr());
            } catch (RuntimeException e) {
                log.warn("Visitor counter update failed: {}", e.toString());
            }
        }
    }
}

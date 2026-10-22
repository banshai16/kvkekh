package in.nic.kvkekh.web;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.util.UrlPathHelper;

import in.nic.kvkekh.config.SiteProperties;
import in.nic.kvkekh.content.MenuItem;
import in.nic.kvkekh.content.Navigation;
import in.nic.kvkekh.visitors.VisitorCounter;

/** Model attributes needed by the shared layout on every page, error pages included. */
@ControllerAdvice
class GlobalModelAdvice {

    /** Indian digit grouping: 1,23,456. */
    private static final Locale EN_IN = Locale.of("en", "IN");

    private final SiteProperties site;
    private final Navigation navigation;
    private final VisitorCounter visitors;

    GlobalModelAdvice(SiteProperties site, Navigation navigation, VisitorCounter visitors) {
        this.site = site;
        this.navigation = navigation;
        this.visitors = visitors;
    }

    @ModelAttribute("site")
    SiteProperties site() {
        return site;
    }

    @ModelAttribute("menu")
    List<MenuItem> menu() {
        return navigation.menu();
    }

    @ModelAttribute("footerLinks")
    List<MenuItem> footerLinks() {
        return navigation.footerLinks();
    }

    @ModelAttribute("currentPath")
    String currentPath(HttpServletRequest request) {
        return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
    }

    @ModelAttribute("visitorCount")
    String visitorCount() {
        return NumberFormat.getIntegerInstance(EN_IN).format(visitors.total());
    }
}

package in.nic.kvkekh.content;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import in.nic.kvkekh.config.SiteProperties;
import in.nic.kvkekh.config.SiteProperties.NavEntry;

/**
 * Global navigation, footer links and breadcrumbs, resolved once at startup from {@code site.yml}
 * against the {@link PageRegistry}. An unknown page id fails startup rather than rendering a dead link.
 */
@Component
public class Navigation {

    public record Crumb(String label, String path) {
    }

    private final PageRegistry pages;
    private final List<MenuItem> menu;
    private final List<MenuItem> footerLinks;

    public Navigation(SiteProperties site, PageRegistry pages) {
        this.pages = pages;
        this.menu = resolveAll(site.navigation());
        this.footerLinks = resolveAll(site.footerLinks());
    }

    public List<MenuItem> menu() {
        return menu;
    }

    public List<MenuItem> footerLinks() {
        return footerLinks;
    }

    /** Home &gt; [menu group] &gt; [parent pages…] &gt; page. Empty for the home page itself. */
    public List<Crumb> breadcrumbs(Page page) {
        Page home = pages.require("home");
        if (page.path().equals(home.path())) {
            return List.of();
        }
        Deque<Page> lineage = new ArrayDeque<>();
        for (Page p = page; p != null; p = p.parent() == null ? null : pages.require(p.parent())) {
            lineage.addFirst(p);
        }
        List<Crumb> crumbs = new ArrayList<>();
        crumbs.add(new Crumb(home.title(), home.path()));
        groupOf(lineage.getFirst().path()).ifPresent(group -> crumbs.add(new Crumb(group.label(), null)));
        for (Page p : lineage) {
            crumbs.add(new Crumb(p.title(), p == page ? null : p.path()));
        }
        return crumbs;
    }

    /**
     * Pages listed under "In this section": the children of the page's top-most parent (e.g. Help, Website
     * Policies), otherwise the top-level menu group containing the page.
     */
    public Optional<MenuItem> sectionOf(Page page) {
        Page top = page;
        while (top.parent() != null) {
            top = pages.require(top.parent());
        }
        String rootId = top.id();
        List<MenuItem> children = pages.all().stream()
                .filter(p -> rootId.equals(p.parent()))
                .map(p -> new MenuItem(p.title(), p.path(), List.of()))
                .toList();
        if (!children.isEmpty()) {
            return Optional.of(new MenuItem(top.title(), top.path(), children));
        }
        return groupOf(page.path());
    }

    private Optional<MenuItem> groupOf(String path) {
        return menu.stream().filter(item -> item.isGroup() && item.contains(path)).findFirst();
    }

    private List<MenuItem> resolveAll(List<NavEntry> entries) {
        return entries.stream().map(this::resolve).toList();
    }

    private MenuItem resolve(NavEntry entry) {
        List<MenuItem> children = resolveAll(entry.children());
        if (entry.page() != null) {
            Page page = pages.require(entry.page());
            return new MenuItem(entry.label() != null ? entry.label() : page.title(), page.path(), children);
        }
        if (entry.label() == null || children.isEmpty()) {
            throw new IllegalStateException("Navigation entry needs either a page id or a label with children: " + entry);
        }
        return new MenuItem(entry.label(), null, children);
    }
}

package in.nic.kvkekh.config;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Site identity and chrome (DBIM 3.0 Header 2 / Lockup 3A, footer), bound from {@code content/site.yml}.
 *
 * @param baseUrl canonical public URL including the context path, used for absolute links (sitemap.xml,
 *                robots.txt) instead of the request's Host header, which clients control
 */
@ConfigurationProperties("site")
public record SiteProperties(
        String baseUrl,
        String name,
        List<String> nameLines,
        String parent,
        Image emblem,
        Image logo,
        List<LinkedImage> coBrands,
        String lineage,
        String contentOwner,
        String hostedBy,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lastUpdated,
        List<LinkedImage> footerLogos,
        Contact contact,
        List<Link> relatedLinks,
        List<NavEntry> navigation,
        List<NavEntry> footerLinks) {

    public SiteProperties {
        nameLines = nullToEmpty(nameLines);
        coBrands = nullToEmpty(coBrands);
        footerLogos = nullToEmpty(footerLogos);
        relatedLinks = nullToEmpty(relatedLinks);
        navigation = nullToEmpty(navigation);
        footerLinks = nullToEmpty(footerLinks);
        if (coBrands.size() > 2) {
            throw new IllegalArgumentException("DBIM 5.4 allows at most 2 co-branding logos in the header");
        }
    }

    public record Image(String src, String alt) {
    }

    public record LinkedImage(String src, String alt, String href) {
    }

    public record Link(String label, String href) {
    }

    public record Contact(List<String> addressLines, String phone, List<String> emails) {

        public Contact {
            addressLines = nullToEmpty(addressLines);
            emails = nullToEmpty(emails);
        }
    }

    /** A navigation entry: either a reference to a page id, or a labelled group of children. */
    public record NavEntry(String page, String label, List<NavEntry> children) {

        public NavEntry {
            children = nullToEmpty(children);
        }
    }

    private static <T> List<T> nullToEmpty(List<T> list) {
        return list == null ? List.of() : List.copyOf(list);
    }
}

package in.nic.kvkekh.content;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * One entry of the content registry ({@code content/pages.yml}).
 *
 * @param template Thymeleaf view name, or {@code null} while the page still awaits migration
 * @param parent   page id used for breadcrumbs when the page is not part of the global navigation
 * @param legacy   paths of the old PHP site that permanently redirect to {@link #path()}
 */
public record Page(
        String id,
        String path,
        String title,
        String description,
        String template,
        String parent,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate lastUpdated,
        List<String> legacy) {

    public Page {
        legacy = legacy == null ? List.of() : List.copyOf(legacy);
    }

    public boolean isMigrated() {
        return template != null;
    }
}

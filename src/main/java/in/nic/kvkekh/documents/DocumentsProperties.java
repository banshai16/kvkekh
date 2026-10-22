package in.nic.kvkekh.documents;

import java.time.LocalDate;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.format.annotation.DateTimeFormat;

/** Downloadable documents and notices, bound from {@code content/documents.yml}. */
@ConfigurationProperties("documents")
public record DocumentsProperties(List<Collection> collections) {

    public DocumentsProperties {
        collections = collections == null ? List.of() : List.copyOf(collections);
    }

    public record Collection(String id, String title, String description, List<Item> items) {

        public Collection {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    /**
     * Either {@code file} (media-relative path to a PDF etc.) or {@code link} (a page path on this site).
     *
     * @param lang     BCP 47 language of the document when it is not English, e.g. {@code kha} for Khasi
     * @param archived date the item was moved to the archive (GIGW / DBIM: outdated content is archived, not deleted)
     */
    public record Item(String title, String file, String link, String lang, String date,
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate archived) {
    }
}

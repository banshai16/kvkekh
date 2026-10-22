package in.nic.kvkekh.content;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("content")
public record ContentProperties(List<Page> pages) {

    public ContentProperties {
        pages = pages == null ? List.of() : List.copyOf(pages);
    }
}

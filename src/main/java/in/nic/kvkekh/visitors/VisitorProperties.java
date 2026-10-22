package in.nic.kvkekh.visitors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** @param baseline starting total, taken from the legacy site's counter when the database is first created */
@ConfigurationProperties("visitors")
public record VisitorProperties(@DefaultValue("0") long baseline) {
}

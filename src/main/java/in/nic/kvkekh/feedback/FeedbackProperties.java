package in.nic.kvkekh.feedback;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param recipient  address that receives feedback emails
 * @param sender     From address accepted by the mail relay; the visitor's address goes in Reply-To
 * @param maxPerHour submissions allowed per IP address per hour
 * @param retention  how long submissions are kept in the database
 */
@ConfigurationProperties("feedback")
public record FeedbackProperties(
        String recipient,
        String sender,
        @DefaultValue("5") int maxPerHour,
        @DefaultValue("365d") Duration retention) {
}

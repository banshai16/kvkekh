package in.nic.kvkekh.feedback;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import in.nic.kvkekh.feedback.FeedbackRepository.MailStatus;

/**
 * Emails feedback to the KVK as plain text — so visitor input can never inject markup into the message,
 * unlike the legacy HTML email. The From address is the site's own (accepted by the relay); the visitor's
 * address is set as Reply-To.
 */
@Component
class FeedbackMailer {

    private static final Logger log = LoggerFactory.getLogger(FeedbackMailer.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm 'IST'")
            .withZone(ZoneId.of("Asia/Kolkata"));

    record Result(MailStatus status, String error) {
    }

    private final ObjectProvider<JavaMailSender> mailSender;
    private final FeedbackProperties properties;

    FeedbackMailer(ObjectProvider<JavaMailSender> mailSender, FeedbackProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    Result send(String reference, Instant receivedAt, FeedbackForm form) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null || isBlank(properties.sender()) || isBlank(properties.recipient())) {
            log.warn("Feedback {} saved but not emailed: set SPRING_MAIL_HOST and KVK_MAIL_FROM to enable mail", reference);
            return new Result(MailStatus.NOT_CONFIGURED, null);
        }
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(properties.sender());
        mail.setTo(properties.recipient());
        mail.setReplyTo(form.getEmail());
        mail.setSubject("Website feedback " + reference + " — KVK East Khasi Hills");
        mail.setText("""
                Feedback received through the KVK East Khasi Hills website.

                Reference: %s
                Received:  %s
                Name:      %s
                Location:  %s
                Email:     %s

                Feedback:
                %s

                --
                Reply to this email to respond to the sender directly.
                """.formatted(reference, WHEN.format(receivedAt), form.getName(), form.getLocation(),
                form.getEmail(), form.getMessage()));
        try {
            sender.send(mail);
            return new Result(MailStatus.SENT, null);
        } catch (MailException e) {
            log.error("Feedback {} saved but email failed: {}", reference, e.getMessage());
            return new Result(MailStatus.FAILED, e.getMessage());
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

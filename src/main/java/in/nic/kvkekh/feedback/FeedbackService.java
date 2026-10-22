package in.nic.kvkekh.feedback;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import in.nic.kvkekh.feedback.FeedbackMailer.Result;
import in.nic.kvkekh.feedback.FeedbackRepository.MailStatus;

/** Saves each submission first, then emails it, so feedback is never lost if the mail relay is down. */
@Service
class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);
    /** No 0/O, 1/I/L: references are read out over the phone. */
    private static final char[] ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ".toCharArray();

    private final FeedbackRepository repository;
    private final FeedbackMailer mailer;
    private final FeedbackProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    FeedbackService(FeedbackRepository repository, FeedbackMailer mailer, FeedbackProperties properties, Clock clock) {
        this.repository = repository;
        this.mailer = mailer;
        this.properties = properties;
        this.clock = clock;
    }

    /** @return the reference number shown to the visitor */
    String submit(FeedbackForm form) {
        Instant now = clock.instant();
        String reference = newReference();
        repository.insert(reference, now, form);
        Result result = mailer.send(reference, now, form);
        repository.updateMailStatus(reference, result.status(), result.error());
        if (result.status() == MailStatus.SENT) {
            log.info("Feedback {} received and emailed", reference);
        }
        return reference;
    }

    /** Data minimisation (DPDP Act 2023): delete submissions older than the retention period, daily at 02:30. */
    @Scheduled(cron = "0 30 2 * * *", zone = "Asia/Kolkata")
    void purgeExpired() {
        int deleted = repository.deleteOlderThan(clock.instant().minus(properties.retention()));
        if (deleted > 0) {
            log.info("Deleted {} feedback submissions older than {}", deleted, properties.retention());
        }
    }

    private String newReference() {
        StringBuilder reference = new StringBuilder("FB-");
        for (int i = 0; i < 6; i++) {
            reference.append(ALPHABET[random.nextInt(ALPHABET.length)]);
        }
        return reference.toString();
    }
}

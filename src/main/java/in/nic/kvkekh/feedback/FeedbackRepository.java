package in.nic.kvkekh.feedback;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class FeedbackRepository {

    enum MailStatus { PENDING, SENT, FAILED, NOT_CONFIGURED }

    private final JdbcClient jdbc;

    FeedbackRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    void insert(String reference, Instant createdAt, FeedbackForm form) {
        jdbc.sql("""
                INSERT INTO feedback (reference, created_at, name, location, email, message, mail_status)
                VALUES (:reference, :createdAt, :name, :location, :email, :message, :status)
                """)
                .param("reference", reference)
                .param("createdAt", OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC))
                .param("name", form.getName())
                .param("location", form.getLocation())
                .param("email", form.getEmail())
                .param("message", form.getMessage())
                .param("status", MailStatus.PENDING.name())
                .update();
    }

    void updateMailStatus(String reference, MailStatus status, String error) {
        jdbc.sql("UPDATE feedback SET mail_status = :status, mail_error = :error WHERE reference = :reference")
                .param("status", status.name())
                .param("error", error == null ? null : error.substring(0, Math.min(error.length(), 500)))
                .param("reference", reference)
                .update();
    }

    int deleteOlderThan(Instant cutoff) {
        return jdbc.sql("DELETE FROM feedback WHERE created_at < :cutoff")
                .param("cutoff", OffsetDateTime.ofInstant(cutoff, ZoneOffset.UTC))
                .update();
    }

    long count() {
        return jdbc.sql("SELECT COUNT(*) FROM feedback").query(Long.class).single();
    }
}

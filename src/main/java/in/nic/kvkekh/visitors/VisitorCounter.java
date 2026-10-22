package in.nic.kvkekh.visitors;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Counts unique visitors per day, continuing from the legacy counter. No IP address is stored: a visitor is
 * identified by HMAC-SHA256(server key, date | IP), which changes every day, and previous days' hashes are
 * deleted nightly — so visits cannot be linked across days or traced back to an address without the key.
 */
@Service
public class VisitorCounter {

    private static final String KEY_NAME = "visitor-key";

    private final JdbcClient jdbc;
    private final Clock clock;
    private final SecretKeySpec key;
    private final AtomicLong total;
    /** Today's visitors already counted, so repeat page views don't touch the database. */
    private final AtomicReference<Day> today = new AtomicReference<>(new Day(LocalDate.MIN, Set.of()));

    private record Day(LocalDate date, Set<String> seen) {
    }

    public VisitorCounter(JdbcClient jdbc, Clock clock, VisitorProperties properties) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.key = new SecretKeySpec(loadOrCreateKey(jdbc), "HmacSHA256");
        jdbc.sql("INSERT INTO visitor_total (id, total) SELECT 1, :baseline WHERE NOT EXISTS (SELECT 1 FROM visitor_total WHERE id = 1)")
                .param("baseline", properties.baseline())
                .update();
        this.total = new AtomicLong(jdbc.sql("SELECT total FROM visitor_total WHERE id = 1").query(Long.class).single());
    }

    public long total() {
        return total.get();
    }

    /** Counts the client once per day. */
    public void record(String clientIp) {
        LocalDate date = LocalDate.now(clock);
        Day day = today.updateAndGet(d -> d.date().equals(date) ? d : new Day(date, ConcurrentHashMap.newKeySet()));
        String visitor = hash(date + "|" + clientIp);
        if (!day.seen().add(visitor)) {
            return;
        }
        try {
            int inserted = jdbc.sql("""
                    INSERT INTO visitor_seen (visit_day, visitor)
                    SELECT :day, :visitor WHERE NOT EXISTS (SELECT 1 FROM visitor_seen WHERE visit_day = :day AND visitor = :visitor)
                    """)
                    .param("day", date)
                    .param("visitor", visitor)
                    .update();
            if (inserted == 1) {
                jdbc.sql("UPDATE visitor_total SET total = total + 1 WHERE id = 1").update();
                total.incrementAndGet();
            }
        } catch (DuplicateKeyException e) {
            // counted concurrently by another request (or before a restart) — nothing to do
        }
    }

    /** Shortly after midnight, forget all earlier days' visitor hashes. */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Kolkata")
    void forgetPreviousDays() {
        jdbc.sql("DELETE FROM visitor_seen WHERE visit_day < :today").param("today", LocalDate.now(clock)).update();
    }

    private String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] loadOrCreateKey(JdbcClient jdbc) {
        var existing = jdbc.sql("SELECT secret FROM app_secret WHERE name = :name")
                .param("name", KEY_NAME)
                .query(String.class)
                .optional();
        if (existing.isPresent()) {
            return HexFormat.of().parseHex(existing.get());
        }
        byte[] secret = new byte[32];
        new SecureRandom().nextBytes(secret);
        jdbc.sql("INSERT INTO app_secret (name, secret) VALUES (:name, :secret)")
                .param("name", KEY_NAME)
                .param("secret", HexFormat.of().formatHex(secret))
                .update();
        return secret;
    }
}

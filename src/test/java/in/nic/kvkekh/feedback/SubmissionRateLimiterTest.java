package in.nic.kvkekh.feedback;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class SubmissionRateLimiterTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-16T10:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Test
    void allowsTheConfiguredNumberPerHourPerAddress() {
        MutableClock clock = new MutableClock();
        SubmissionRateLimiter limiter = new SubmissionRateLimiter(
                new FeedbackProperties("to@example.org", "from@example.org", 2, Duration.ofDays(365)), clock);

        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
        assertThat(limiter.tryAcquire("10.0.0.1")).isFalse();
        assertThat(limiter.tryAcquire("10.0.0.2")).isTrue();

        clock.now = clock.now.plus(Duration.ofMinutes(61));
        assertThat(limiter.tryAcquire("10.0.0.1")).isTrue();
    }
}

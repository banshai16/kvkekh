package in.nic.kvkekh.feedback;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Sliding one-hour window of accepted submissions per client IP, held in memory only. */
@Component
class SubmissionRateLimiter {

    private static final Duration WINDOW = Duration.ofHours(1);

    private final ConcurrentHashMap<String, Deque<Instant>> recent = new ConcurrentHashMap<>();
    private final FeedbackProperties properties;
    private final Clock clock;

    SubmissionRateLimiter(FeedbackProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** True, and the attempt is counted, if the client is still under its hourly limit. */
    boolean tryAcquire(String clientIp) {
        Instant now = clock.instant();
        boolean[] allowed = {false};
        recent.compute(clientIp, (ip, times) -> {
            Deque<Instant> deque = times == null ? new ArrayDeque<>() : times;
            while (!deque.isEmpty() && deque.peekFirst().isBefore(now.minus(WINDOW))) {
                deque.pollFirst();
            }
            if (deque.size() < properties.maxPerHour()) {
                deque.addLast(now);
                allowed[0] = true;
            }
            return deque;
        });
        return allowed[0];
    }

    @Scheduled(fixedDelay = 15 * 60 * 1000)
    void evictIdle() {
        Instant cutoff = clock.instant().minus(WINDOW);
        recent.entrySet().removeIf(e -> e.getValue().isEmpty() || e.getValue().peekLast().isBefore(cutoff));
    }
}

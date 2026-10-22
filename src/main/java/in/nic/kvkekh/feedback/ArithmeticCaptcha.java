package in.nic.kvkekh.feedback;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;

/**
 * A text question ("What is 4 plus 7?") instead of the legacy image captcha, which screen-reader users
 * could not solve (GIGW 3.0 / WCAG 1.1.1). The answer is kept in the session and is valid once, for a
 * limited time. It is one layer of spam protection alongside the honeypot field and the rate limit.
 */
@Component
class ArithmeticCaptcha {

    private static final String SESSION_KEY = ArithmeticCaptcha.class.getName();
    private static final Duration VALIDITY = Duration.ofMinutes(30);
    private static final String[] WORDS = {"zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"};

    private record Challenge(int answer, Instant expires) {
    }

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    ArithmeticCaptcha(Clock clock) {
        this.clock = clock;
    }

    /** Creates a new question, replacing any previous one in the session. */
    String newQuestion(HttpSession session) {
        int a = 1 + random.nextInt(9);
        int b = 1 + random.nextInt(9);
        session.setAttribute(SESSION_KEY, new Challenge(a + b, clock.instant().plus(VALIDITY)));
        return "What is " + WORDS[a] + " plus " + b + "?";
    }

    /** Checks the answer and consumes the question, so each one can be used only once. */
    boolean verify(HttpSession session, String answer) {
        Object stored = session.getAttribute(SESSION_KEY);
        session.removeAttribute(SESSION_KEY);
        if (!(stored instanceof Challenge challenge) || clock.instant().isAfter(challenge.expires()) || answer == null) {
            return false;
        }
        try {
            return Integer.parseInt(answer.strip()) == challenge.answer();
        } catch (NumberFormatException e) {
            return false;
        }
    }
}

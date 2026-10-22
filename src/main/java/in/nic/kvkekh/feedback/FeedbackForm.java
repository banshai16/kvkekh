package in.nic.kvkekh.feedback;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The feedback form. Messages are written for the error summary, so each one names its field and says
 * how to fix it. {@code website} is a honeypot: hidden from people, but filled in by many spam bots.
 */
public class FeedbackForm {

    static final int MESSAGE_MAX = 2000;

    @NotBlank(message = "Enter your name")
    @Size(max = 100, message = "Name must be 100 characters or fewer")
    @Pattern(regexp = "[\\p{L}\\p{M} .'-]*", message = "Name can only contain letters, spaces, full stops, hyphens and apostrophes")
    private String name;

    @NotBlank(message = "Enter your village, town or district")
    @Size(max = 100, message = "Location must be 100 characters or fewer")
    @Pattern(regexp = "[^\\r\\n<>]*", message = "Location cannot contain line breaks or angle brackets")
    private String location;

    @NotBlank(message = "Enter your email address")
    @Size(max = 254, message = "Email address must be 254 characters or fewer")
    @Email(regexp = "[^\\s@]+@[^\\s@]+\\.[^\\s@]+", message = "Enter an email address in the correct format, like name@example.com")
    private String email;

    @NotBlank(message = "Enter your feedback")
    @Size(max = MESSAGE_MAX, message = "Feedback must be " + MESSAGE_MAX + " characters or fewer")
    private String message;

    @NotBlank(message = "Answer the question to show you are not a computer program")
    @Size(max = 10, message = "Enter the answer as a number")
    private String captchaAnswer;

    @AssertTrue(message = "Confirm that we may use your details to reply to you")
    private boolean consent;

    private String website;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = trim(name);
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = trim(location);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = trim(email);
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message == null ? null : message.strip();
    }

    public String getCaptchaAnswer() {
        return captchaAnswer;
    }

    public void setCaptchaAnswer(String captchaAnswer) {
        this.captchaAnswer = trim(captchaAnswer);
    }

    public boolean isConsent() {
        return consent;
    }

    public void setConsent(boolean consent) {
        this.consent = consent;
    }

    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }

    private static String trim(String value) {
        return value == null ? null : value.strip().replaceAll("\\s+", " ");
    }
}

package in.nic.kvkekh.feedback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class FeedbackTest {

    private static final Pattern QUESTION = Pattern.compile("What is (\\w+) plus (\\d)\\?");
    private static final List<String> WORDS = Arrays.asList("zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine");

    @Autowired
    MockMvc mvc;

    @Autowired
    FeedbackRepository repository;

    @MockitoBean
    JavaMailSender mailSender;

    @BeforeEach
    void resetMocks() {
        clearInvocations(mailSender);
    }

    /** Loads the form in a fresh session and returns the answer to the captcha question shown. */
    private String loadFormAndSolveCaptcha(MockHttpSession session) throws Exception {
        String html = mvc.perform(get("/feedback").session(session))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        Matcher m = QUESTION.matcher(html);
        assertThat(m.find()).as("captcha question on the page").isTrue();
        return String.valueOf(WORDS.indexOf(m.group(1)) + Integer.parseInt(m.group(2)));
    }

    private static MockHttpServletRequestBuilder validPost(MockHttpSession session, String answer) {
        return post("/feedback").session(session).with(csrf())
                .param("name", "Iaibha Lyngdoh")
                .param("location", "Mawphlang")
                .param("email", "iaibha@example.com")
                .param("message", "When is the next training on mushroom cultivation?\n<b>Thanks</b>")
                .param("consent", "true")
                .param("captchaAnswer", answer);
    }

    @Test
    void validSubmissionIsSavedEmailedAsPlainTextAndRedirects() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String answer = loadFormAndSolveCaptcha(session);
        long before = repository.count();

        mvc.perform(validPost(session, answer))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/feedback/thank-you"))
                .andExpect(flash().attributeExists("reference"));

        assertThat(repository.count()).isEqualTo(before + 1);
        ArgumentCaptor<SimpleMailMessage> mail = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(mail.capture());
        assertThat(mail.getValue().getTo()).containsExactly("kvk@example.org");
        assertThat(mail.getValue().getFrom()).isEqualTo("website@example.org");
        assertThat(mail.getValue().getReplyTo()).isEqualTo("iaibha@example.com");
        assertThat(mail.getValue().getText()).contains("Iaibha Lyngdoh", "Mawphlang", "<b>Thanks</b>");
    }

    @Test
    void thankYouPageNeedsASubmission() throws Exception {
        mvc.perform(get("/feedback/thank-you")).andExpect(redirectedUrl("/feedback"));
    }

    @Test
    void missingFieldsShowAnErrorSummaryInFieldOrder() throws Exception {
        MockHttpSession session = new MockHttpSession();
        loadFormAndSolveCaptcha(session);
        String html = mvc.perform(post("/feedback").session(session).with(csrf()).param("email", "not-an-email"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("There is a problem")))
                .andExpect(content().string(containsString("aria-invalid=\"true\"")))
                .andReturn().getResponse().getContentAsString();
        assertThat(html.indexOf("href=\"#name\"")).isLessThan(html.indexOf("href=\"#email\""));
        assertThat(html).contains("Enter an email address in the correct format");
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void wrongCaptchaIsRejectedAndANewQuestionIsShown() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String answer = loadFormAndSolveCaptcha(session);
        String wrong = String.valueOf(Integer.parseInt(answer) + 1);
        long before = repository.count();

        mvc.perform(validPost(session, wrong))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The answer is not correct")))
                .andExpect(content().string(containsString("name=\"captchaAnswer\" value=\"\"")))
                .andExpect(content().string(containsString("value=\"Iaibha Lyngdoh\"")));
        assertThat(repository.count()).isEqualTo(before);
    }

    @Test
    void captchaCannotBeReused() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String answer = loadFormAndSolveCaptcha(session);
        mvc.perform(validPost(session, answer)).andExpect(status().is3xxRedirection());
        mvc.perform(validPost(session, answer))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("The answer is not correct")));
    }

    @Test
    void honeypotSubmissionsAreDiscardedSilently() throws Exception {
        MockHttpSession session = new MockHttpSession();
        String answer = loadFormAndSolveCaptcha(session);
        long before = repository.count();

        mvc.perform(validPost(session, answer).param("website", "http://spam.example"))
                .andExpect(redirectedUrl("/feedback/thank-you"));
        assertThat(repository.count()).isEqualTo(before);
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void postWithoutCsrfTokenIsForbidden() throws Exception {
        mvc.perform(post("/feedback").param("name", "x")).andExpect(status().isForbidden());
    }

    @Test
    void formPageIsAccessible() throws Exception {
        mvc.perform(get("/feedback"))
                .andExpect(content().string(containsString("<label for=\"name\">Full name <span class=\"field__required\">(required)</span></label>")))
                .andExpect(content().string(containsString("autocomplete=\"email\"")))
                .andExpect(content().string(containsString("name=\"_csrf\"")))
                .andExpect(content().string(not(containsString("There is a problem"))));
    }
}

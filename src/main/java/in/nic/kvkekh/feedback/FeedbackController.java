package in.nic.kvkekh.feedback;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import in.nic.kvkekh.content.Page;
import in.nic.kvkekh.content.PageModel;
import in.nic.kvkekh.content.PageRegistry;

/** Feedback form: validate, check captcha and rate limit, save, email, then redirect (Post/Redirect/Get). */
@Controller
@RequestMapping("/feedback")
class FeedbackController {

    /** Error summary order follows the order of the fields on the page. */
    private static final List<String> FIELD_ORDER = List.of("name", "location", "email", "message", "consent", "captchaAnswer");

    record ErrorLink(String field, String message) {
    }

    private final FeedbackService feedback;
    private final ArithmeticCaptcha captcha;
    private final SubmissionRateLimiter rateLimiter;
    private final PageRegistry pages;
    private final PageModel pageModel;

    FeedbackController(FeedbackService feedback, ArithmeticCaptcha captcha, SubmissionRateLimiter rateLimiter,
            PageRegistry pages, PageModel pageModel) {
        this.feedback = feedback;
        this.captcha = captcha;
        this.rateLimiter = rateLimiter;
        this.pages = pages;
        this.pageModel = pageModel;
    }

    @GetMapping
    String form(Model model, HttpSession session) {
        if (!model.containsAttribute("feedbackForm")) {
            model.addAttribute("feedbackForm", new FeedbackForm());
        }
        return render(model, session, List.of());
    }

    @PostMapping
    String submit(@Valid @ModelAttribute("feedbackForm") FeedbackForm form, BindingResult result,
            HttpServletRequest request, HttpSession session, Model model, RedirectAttributes redirect) {
        boolean captchaPassed = captcha.verify(session, form.getCaptchaAnswer());

        // Honeypot filled in: almost certainly a bot. Respond as if accepted, but store nothing.
        if (form.getWebsite() != null && !form.getWebsite().isBlank()) {
            redirect.addFlashAttribute("submitted", true);
            return "redirect:/feedback/thank-you";
        }
        if (!captchaPassed && !result.hasFieldErrors("captchaAnswer")) {
            result.rejectValue("captchaAnswer", "captcha.wrong", "The answer is not correct — answer the new question below");
        }
        if (result.hasErrors()) {
            return render(model, session, errorLinks(result));
        }
        if (!rateLimiter.tryAcquire(request.getRemoteAddr())) {
            model.addAttribute("rateLimited", true);
            return render(model, session, List.of());
        }

        String reference = feedback.submit(form);
        redirect.addFlashAttribute("submitted", true);
        redirect.addFlashAttribute("reference", reference);
        return "redirect:/feedback/thank-you";
    }

    @GetMapping("/thank-you")
    String thankYou(Model model) {
        if (!Boolean.TRUE.equals(model.getAttribute("submitted"))) {
            return "redirect:/feedback";
        }
        Page page = new Page("feedback-sent", "/feedback/thank-you", "Thank you for your feedback", null, null,
                "feedback", null, List.of());
        pageModel.apply(model, page);
        return "pages/feedback-sent";
    }

    private String render(Model model, HttpSession session, List<ErrorLink> errors) {
        pageModel.apply(model, pages.require("feedback"));
        model.addAttribute("captchaQuestion", captcha.newQuestion(session));
        model.addAttribute("errorLinks", errors);
        model.addAttribute("messageMax", FeedbackForm.MESSAGE_MAX);
        return "pages/feedback";
    }

    private static List<ErrorLink> errorLinks(BindingResult result) {
        return FIELD_ORDER.stream()
                .map(result::getFieldError)
                .filter(error -> error != null)
                .map(FeedbackController::link)
                .toList();
    }

    private static ErrorLink link(FieldError error) {
        return new ErrorLink(error.getField(), error.getDefaultMessage());
    }
}

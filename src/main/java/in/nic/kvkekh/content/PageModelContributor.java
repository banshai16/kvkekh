package in.nic.kvkekh.content;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.ui.Model;

/** Adds data a particular registry page needs beyond the common layout attributes (e.g. search results). */
public interface PageModelContributor {

    boolean supports(Page page);

    void contribute(Page page, HttpServletRequest request, Model model);
}

package in.nic.kvkekh.documents;

import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import in.nic.kvkekh.content.Page;
import in.nic.kvkekh.content.PageModelContributor;

/** Exposes the document collections as {@code docs} to the pages that list documents. */
@Component
class DocumentsContributor implements PageModelContributor {

    private static final Set<String> PAGES = Set.of("home", "publications", "newsletters", "success-stories", "archive");

    private final DocumentLibrary library;

    DocumentsContributor(DocumentLibrary library) {
        this.library = library;
    }

    @Override
    public boolean supports(Page page) {
        return PAGES.contains(page.id());
    }

    @Override
    public void contribute(Page page, HttpServletRequest request, Model model) {
        model.addAttribute("docs", library.all());
    }
}

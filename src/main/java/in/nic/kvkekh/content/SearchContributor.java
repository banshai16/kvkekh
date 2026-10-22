package in.nic.kvkekh.content;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import in.nic.kvkekh.search.SearchIndex;

@Component
class SearchContributor implements PageModelContributor {

    static final int MAX_QUERY_LENGTH = 100;

    private final SearchIndex index;

    SearchContributor(SearchIndex index) {
        this.index = index;
    }

    @Override
    public boolean supports(Page page) {
        return page.id().equals("search");
    }

    @Override
    public void contribute(Page page, HttpServletRequest request, Model model) {
        String query = normalise(request.getParameter("q"));
        model.addAttribute("query", query);
        model.addAttribute("results", query.isEmpty() ? List.of() : index.search(query));
    }

    private static String normalise(String raw) {
        if (raw == null) {
            return "";
        }
        String query = raw.strip();
        return query.length() > MAX_QUERY_LENGTH ? query.substring(0, MAX_QUERY_LENGTH) : query;
    }
}

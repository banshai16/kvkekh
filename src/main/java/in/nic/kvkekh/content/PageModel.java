package in.nic.kvkekh.content;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

import in.nic.kvkekh.content.Navigation.Crumb;

/** Adds the per-page attributes the shared layout expects: page, breadcrumbs and "In this section". */
@Component
public class PageModel {

    private final Navigation navigation;

    public PageModel(Navigation navigation) {
        this.navigation = navigation;
    }

    public void apply(Model model, Page page) {
        apply(model, page, navigation.breadcrumbs(page));
    }

    public void apply(Model model, Page page, List<Crumb> breadcrumbs) {
        model.addAttribute("page", page);
        model.addAttribute("breadcrumbs", breadcrumbs);
        model.addAttribute("section", navigation.sectionOf(page).orElse(null));
    }
}

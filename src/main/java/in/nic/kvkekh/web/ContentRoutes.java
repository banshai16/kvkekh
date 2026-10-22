package in.nic.kvkekh.web;

import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.ui.Model;
import org.springframework.util.ReflectionUtils;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import in.nic.kvkekh.content.Page;
import in.nic.kvkekh.content.PageRegistry;

/**
 * Registers an exact GET mapping for every registry page and every legacy PHP URL, so the set of routes is
 * driven by {@code pages.yml} / {@code albums.yml} and anything not listed is a 404. A page whose path is
 * already handled by an annotated controller (e.g. {@code /gallery}) keeps that controller.
 */
@Component
class ContentRoutes implements SmartInitializingSingleton {

    private static final Method PAGE = ReflectionUtils.findMethod(
            ContentController.class, "page", HttpServletRequest.class, Model.class);
    private static final Method LEGACY = ReflectionUtils.findMethod(
            ContentController.class, "legacyRedirect", HttpServletRequest.class);

    private final RequestMappingHandlerMapping mapping;
    private final ContentController controller;
    private final PageRegistry pages;
    private final LegacyRedirects redirects;

    ContentRoutes(@Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping mapping,
            ContentController controller, PageRegistry pages, LegacyRedirects redirects) {
        this.mapping = mapping;
        this.controller = controller;
        this.pages = pages;
        this.redirects = redirects;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Set<String> annotated = mapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream())
                .collect(Collectors.toSet());
        for (Page page : pages.all()) {
            if (!annotated.contains(page.path())) {
                mapping.registerMapping(get(page.path()), controller, PAGE);
            }
        }
        for (String legacyPath : redirects.paths()) {
            mapping.registerMapping(get(legacyPath), controller, LEGACY);
        }
    }

    private RequestMappingInfo get(String path) {
        return RequestMappingInfo.paths(path)
                .methods(RequestMethod.GET, RequestMethod.HEAD)
                .options(mapping.getBuilderConfiguration())
                .build();
    }
}

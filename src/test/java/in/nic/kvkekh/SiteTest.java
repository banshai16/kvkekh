package in.nic.kvkekh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import in.nic.kvkekh.content.Page;
import in.nic.kvkekh.content.PageRegistry;

/** Uses the "test" profile: fixture media in src/test/resources/test-media (one album, one PDF), in-memory H2. */
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class SiteTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PageRegistry pages;

    @Test
    void homeRendersDbimHeaderFooterAndNotices() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Department of Agriculture and Farmers&#39; Welfare, Government of Meghalaya")))
                .andExpect(content().string(containsString("class=\"lockup\"")))
                .andExpect(content().string(containsString("id=\"a11y-panel\"")))
                .andExpect(content().string(containsString("This is the official website of Krishi Vigyan Kendra")))
                .andExpect(content().string(containsString("Last Updated On:")))
                .andExpect(content().string(containsString("What's New")));
    }

    @Test
    void everyPageCarriesTheCookieConsentBannerAndFooterButton() throws Exception {
        for (String path : new String[] {"/", "/about/objectives", "/policies/privacy"}) {
            mvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"cookie-banner\"")))
                    .andExpect(content().string(containsString("data-cookie-accept")))
                    .andExpect(content().string(containsString("data-cookie-reject")))
                    .andExpect(content().string(containsString("data-cookie-customize")))
                    .andExpect(content().string(containsString("data-cookie-open")));
        }
    }

    @Test
    void everyRegistryPageRenders() throws Exception {
        for (Page page : pages.all()) {
            mvc.perform(get(page.path()))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("<h1")));
        }
    }

    @Test
    void migratedPageHasBreadcrumbSectionNavAndLastUpdated() throws Exception {
        mvc.perform(get("/about/objectives"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<title>Objectives | Krishi Vigyan Kendra, East Khasi Hills</title>")))
                .andExpect(content().string(containsString("aria-label=\"Breadcrumb\"")))
                .andExpect(content().string(containsString("<span>About KVK</span>")))
                .andExpect(content().string(containsString("In About KVK")))
                .andExpect(content().string(containsString("11 February 2020")));
    }

    @Test
    void sitemapXmlAndRobotsTxtListPublishedPages() throws Exception {
        mvc.perform(get("/sitemap.xml"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("<loc>http://localhost/about/objectives</loc><lastmod>2020-02-11</lastmod>")))
                .andExpect(content().string(containsString("<loc>http://localhost/gallery/crops</loc>")))
                .andExpect(content().string(not(containsString("/search<"))));
        mvc.perform(get("/robots.txt"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Sitemap: http://localhost/sitemap.xml")))
                .andExpect(content().string(containsString("Disallow: /search")));
    }

    @Test
    void childPagesListTheirParentSection() throws Exception {
        mvc.perform(get("/policies/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("In Website Policies")))
                .andExpect(content().string(containsString("Hyperlinking Policy")));
    }

    @Test
    void legacyPhpUrlsRedirectPermanently() throws Exception {
        mvc.perform(get("/content/objective.php"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/about/objectives"));
        mvc.perform(get("/index.php"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/"));
        mvc.perform(get("/content/cropsphoto.php"))
                .andExpect(status().isMovedPermanently())
                .andExpect(header().string("Location", "/gallery/crops"));
    }

    @Test
    void unknownPathsAreNotFound() throws Exception {
        mvc.perform(get("/content/does-not-exist.php")).andExpect(status().isNotFound());
        mvc.perform(get("/includes/captcha_code_file.php")).andExpect(status().isNotFound());
        mvc.perform(get("/gallery/no-such-album")).andExpect(status().isNotFound());
        mvc.perform(get("/gallery/crops/no-such-photo")).andExpect(status().isNotFound());
    }

    @Test
    void galleryListsAlbumsWithPhotosOnly() throws Exception {
        mvc.perform(get("/gallery"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/gallery/crops")))
                .andExpect(content().string(containsString("3 photos")))
                .andExpect(content().string(not(containsString("/gallery/kisan-mela"))));
    }

    @Test
    void albumUsesCaptionsFromYamlFileNamesAndPositions() throws Exception {
        mvc.perform(get("/gallery/crops"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alt=\"Visitors in a crop demonstration field\"")))
                .andExpect(content().string(containsString("alt=\"Field preparation for SRI\"")))
                .andExpect(content().string(containsString("alt=\"Maize\"")))
                .andExpect(content().string(containsString("/media/thumb/images/photogallery/Crops/Field%20preparation%20for%20SRI.JPG")));
    }

    @Test
    void photoPageHasCaptionAndPager() throws Exception {
        mvc.perform(get("/gallery/crops/field-preparation-for-sri"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Crops: photo 2 of 3")))
                .andExpect(content().string(containsString("rel=\"prev\"")))
                .andExpect(content().string(containsString("rel=\"next\"")))
                .andExpect(content().string(containsString("<a href=\"/gallery/crops\">Crops</a>")));
    }

    @Test
    void thumbnailsAreResizedJpegsUnder100Kb() throws Exception {
        byte[] body = mvc.perform(get("/media/thumb/images/photogallery/Crops/Maize.JPG"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(header().string("Cache-Control", containsString("max-age=2592000")))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(body.length).isBetween(1, 100 * 1024);
    }

    @Test
    void mediaRejectsTraversalUnknownSizesAndNonImages() throws Exception {
        // "../" is already rejected (400) by Spring Security's firewall before reaching the controller.
        mvc.perform(get("/media/thumb/images/../advertisement/Advt_kvkekhMar2019.pdf")).andExpect(status().is4xxClientError());
        mvc.perform(get("/media/huge/images/photogallery/Crops/Maize.JPG")).andExpect(status().isNotFound());
        mvc.perform(get("/media/thumb/advertisement/Advt_kvkekhMar2019.pdf")).andExpect(status().isNotFound());
        mvc.perform(get("/media/thumb/images/photogallery/Crops/missing.jpg")).andExpect(status().isNotFound());
    }

    @Test
    void searchFindsPagesAndEscapesQuery() throws Exception {
        mvc.perform(get("/search").param("q", "objectives"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Objectives")));
        mvc.perform(get("/search").param("q", "<script>alert(1)</script>"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("<script>alert(1)</script>"))));
    }

    @Test
    void sitemapListsNavigationGroups() throws Exception {
        mvc.perform(get("/sitemap"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Frontline Demonstrations")));
    }

    @Test
    void publicPagesSetNoCookies() throws Exception {
        mvc.perform(get("/")).andExpect(header().doesNotExist("Set-Cookie"));
        mvc.perform(get("/gallery/crops")).andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void securityHeadersArePresent() throws Exception {
        mvc.perform(get("/"))
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")))
                .andExpect(header().string("X-Frame-Options", "SAMEORIGIN"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }
}

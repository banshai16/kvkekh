package in.nic.kvkekh;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import in.nic.kvkekh.search.SearchIndex;
import in.nic.kvkekh.search.SearchIndex.Hit;

/** Regression tests for findings of the Phase 5 GIGW / DBIM / security audit. */
@SpringBootTest(properties = "site.base-url=https://kvk.example.gov.in/kvkekh")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class AuditFixesTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    SearchIndex search;

    @Test
    void mediaFoldersServeOnlyPublishedFileTypes() throws Exception {
        mvc.perform(get("/advertisement/Advt_kvkekhMar2019.pdf")).andExpect(status().isOk());
        mvc.perform(get("/images/photogallery/Crops/Maize.JPG")).andExpect(status().isOk());
        mvc.perform(get("/images/photogallery/Crops/Thumbs.db")).andExpect(status().isNotFound());
        mvc.perform(get("/advertisement/notes.docx")).andExpect(status().isNotFound());
    }

    @Test
    void optionsRequestsAreRefused() throws Exception {
        mvc.perform(options("/")).andExpect(status().isForbidden());
    }

    @Test
    void sitemapUsesTheCanonicalUrlNotTheHostHeader() throws Exception {
        mvc.perform(get("/sitemap.xml").header("Host", "evil.example"))
                .andExpect(content().string(containsString("<loc>https://kvk.example.gov.in/kvkekh/about/objectives</loc>")))
                .andExpect(content().string(not(containsString("evil.example"))));
        mvc.perform(get("/robots.txt").header("Host", "evil.example"))
                .andExpect(content().string(containsString("Sitemap: https://kvk.example.gov.in/kvkekh/sitemap.xml")));
    }

    @Test
    void searchCoversPageTextPhotoCaptionsAndHighlightsMatches() throws Exception {
        // Words from the body of the Mandate page (not its title or description)
        mvc.perform(get("/search").param("q", "location-specific sustainable"))
                .andExpect(content().string(containsString("Mandate")))
                .andExpect(content().string(containsString("<mark>location-specific</mark>")));
        // A photo caption from albums.yml
        mvc.perform(get("/search").param("q", "crop demonstration field"))
                .andExpect(content().string(containsString("Photo album")))
                .andExpect(content().string(containsString("/gallery/crops")));
    }

    @Test
    void searchCoversTextInsidePdfs() throws Exception {
        List<Hit> hits = List.of();
        for (int attempt = 0; attempt < 50 && hits.isEmpty(); attempt++) {
            hits = search.search("permanent residents");   // occurs only inside the fixture PDF
            if (hits.isEmpty()) {
                Thread.sleep(100);                          // PDF text is indexed in the background at startup
            }
        }
        assertThat(hits).extracting(Hit::title).contains("Advertisement for non-technical posts");
        assertThat(hits.getFirst().meta()).isEqualTo("PDF, 17 KB");
    }

    @Test
    void outdatedNoticesAreArchivedWithTheirDate() throws Exception {
        mvc.perform(get("/"))
                .andExpect(content().string(containsString("There are no current notices.")))
                .andExpect(content().string(containsString("/documents/archive")));
        mvc.perform(get("/documents/archive"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Advertisement for non-technical posts")))
                .andExpect(content().string(containsString("archived on")))
                .andExpect(content().string(containsString("16 October 2026")));
    }

    @Test
    void scrollableTablesAreKeyboardFocusableRegions() throws Exception {
        mvc.perform(get("/about/staff"))
                .andExpect(content().string(containsString("class=\"table-scroll\" role=\"region\" tabindex=\"0\" aria-label=\"Staff list\"")));
    }

    @Test
    void gigwPoliciesArePublishedUnderWebsitePolicies() throws Exception {
        String html = mvc.perform(get("/policies")).andReturn().getResponse().getContentAsString();
        assertThat(html).contains("Content Archival Policy", "Content Review Policy", "Website Monitoring Policy",
                "Contingency Management Plan", "Security Policy", "Content Contribution, Moderation and Approval Policy");
    }
}

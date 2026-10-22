package in.nic.kvkekh.visitors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
class VisitorCounterTest {

    private static final String BROWSER = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Firefox/140.0";

    @Autowired
    MockMvc mvc;

    @Autowired
    VisitorCounter counter;

    @Autowired
    JdbcClient jdbc;

    private static MockHttpServletRequestBuilder page(String path, String ip, String agent) {
        return get(path).header("Accept", "text/html").header("User-Agent", agent)
                .with(request -> {
                    request.setRemoteAddr(ip);
                    return request;
                });
    }

    @Test
    void countsEachBrowserVisitorOncePerDayAndIgnoresBotsAndAssets() throws Exception {
        long start = counter.total();
        assertThat(start).isGreaterThanOrEqualTo(10569);

        mvc.perform(page("/", "203.0.113.10", BROWSER));
        mvc.perform(page("/about/objectives", "203.0.113.10", BROWSER));
        assertThat(counter.total()).isEqualTo(start + 1);

        mvc.perform(page("/", "203.0.113.11", BROWSER));
        assertThat(counter.total()).isEqualTo(start + 2);

        mvc.perform(page("/", "203.0.113.12", "Googlebot/2.1 (+http://www.google.com/bot.html)"));
        mvc.perform(page("/assets/css/dbim.css", "203.0.113.13", BROWSER));
        mvc.perform(page("/no-such-page", "203.0.113.14", BROWSER));
        assertThat(counter.total()).isEqualTo(start + 2);
    }

    @Test
    void storesNoIpAddresses() throws Exception {
        mvc.perform(page("/", "198.51.100.77", BROWSER));
        var stored = jdbc.sql("SELECT visitor FROM visitor_seen").query(String.class).list();
        assertThat(stored).isNotEmpty().allSatisfy(v -> assertThat(v).hasSize(64).doesNotContain("198.51.100.77"));
    }
}

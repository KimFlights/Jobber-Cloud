package cs590.ScraperService.scraper;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base for jsoup/HTML-backed site scrapers. Handles the shared mechanics — HTTP fetch, circuit
 * breaker, selector-driven field extraction — so a new site is just a subclass that supplies its
 * search URL and its {@link SiteSelectors}.
 *
 * <p>To add a site (e.g. Indeed, Handshake): extend this class, implement {@link #siteName()},
 * {@link #buildSearchUrl(String)} and {@link #selectors()}, annotate it as a Spring
 * {@code @Component} (gated behind its own {@code @ConditionalOnProperty} flag), and the
 * orchestrator picks it up automatically via the injected {@code List<SiteScraper>}.
 *
 * <p>Operators are responsible for respecting each site's Terms of Service / robots.txt.
 */
public abstract class AbstractJsoupSiteScraper implements SiteScraper {

    private static final Logger log = LoggerFactory.getLogger(AbstractJsoupSiteScraper.class);

    protected final ScraperHttpProperties http;

    protected AbstractJsoupSiteScraper(ScraperHttpProperties http) {
        this.http = http;
    }

    /** Build the site's search-results URL for a query. */
    protected abstract String buildSearchUrl(String query);

    /** Selectors for extracting fields from this site's results HTML. */
    protected abstract SiteSelectors selectors();

    @Override
    @CircuitBreaker(name = "siteScrape", fallbackMethod = "fallback")
    public List<RawJobData> scrape(String query) {
        List<RawJobData> jobs = new ArrayList<>();
        SiteSelectors s = selectors();
        try {
            Document doc = Jsoup.connect(buildSearchUrl(query))
                    .userAgent(http.getUserAgent())
                    .timeout(http.getTimeoutMillis())
                    .get();
            for (Element card : doc.select(s.listing())) {
                String title = text(card, s.title());
                if (title == null || title.isBlank()) {
                    continue;
                }
                String company = text(card, s.company());
                String location = text(card, s.location());
                String description = text(card, s.description());
                String url = s.url() == null ? null : card.select(s.url()).attr("abs:href");
                jobs.add(new RawJobData(siteName(), url, title, company, location, description));
            }
        } catch (Exception e) {
            log.warn("Scrape failed for site {}: {}", siteName(), e.getMessage());
        }
        return jobs;
    }

    /** Circuit-breaker fallback: return nothing rather than failing the whole scrape run. */
    @SuppressWarnings("unused")
    List<RawJobData> fallback(String query, Throwable t) {
        log.warn("Circuit open for site {} — returning no results", siteName());
        return List.of();
    }

    protected String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String text(Element card, String selector) {
        if (selector == null || selector.isBlank()) {
            return null;
        }
        Element el = card.selectFirst(selector);
        return el == null ? null : el.text();
    }
}

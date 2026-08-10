package cs590.ScraperService.scraper;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * LinkedIn job scraper — the first real site. Targets the public guest job-search endpoint,
 * which returns a list of job cards as HTML.
 *
 * <p>Off by default ({@code scraper.linkedin.enabled=true} to turn on). LinkedIn actively rate
 * limits/blocks automated access and its ToS restricts scraping (see ARCHITECTURE.md §9); a
 * production deployment would likely move this to an official/partner API while keeping this same
 * {@link SiteScraper} seam. The listing endpoint carries no full description, so
 * {@code description} is left null and enrichment summarizes from the available fields.
 */
@Component
@ConditionalOnProperty(name = "scraper.linkedin.enabled", havingValue = "true")
public class LinkedInScraper extends AbstractJsoupSiteScraper {

    private static final String SEARCH_URL =
            "https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search?keywords=%s";

    private static final SiteSelectors SELECTORS = new SiteSelectors(
            "li",                                    // each job card
            "h3.base-search-card__title",            // title
            "h4.base-search-card__subtitle",         // company
            "span.job-search-card__location",        // location
            null,                                    // description not on the listing page
            "a.base-card__full-link");               // job url

    public LinkedInScraper(ScraperHttpProperties http) {
        super(http);
    }

    @Override
    public String siteName() {
        return "linkedin";
    }

    @Override
    protected String buildSearchUrl(String query) {
        return SEARCH_URL.formatted(encode(query));
    }

    @Override
    protected SiteSelectors selectors() {
        return SELECTORS;
    }
}

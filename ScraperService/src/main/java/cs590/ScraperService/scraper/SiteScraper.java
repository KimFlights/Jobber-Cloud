package cs590.ScraperService.scraper;

import java.util.List;

/**
 * One per-site scraping strategy. Implementations are discovered as Spring beans and fanned out
 * over by the scrape orchestration; a failing site must not sink the others.
 */
public interface SiteScraper {

    /** Stable, human-readable site identifier stored as {@code source}. */
    String siteName();

    /** Fetch postings for a query. Should return an empty list (not throw) on soft failures. */
    List<RawJobData> scrape(String query);
}

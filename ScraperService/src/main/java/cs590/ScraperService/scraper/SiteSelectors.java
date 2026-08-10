package cs590.ScraperService.scraper;

/**
 * CSS selectors describing how to pull job fields out of one site's search-results HTML.
 * A {@code null} field selector means "not available on the listing page" (skipped).
 */
public record SiteSelectors(
        String listing,
        String title,
        String company,
        String location,
        String description,
        String url) {
}

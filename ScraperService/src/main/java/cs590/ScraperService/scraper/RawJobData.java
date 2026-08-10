package cs590.ScraperService.scraper;

/**
 * A single job posting as returned by a {@link SiteScraper}, before content-hashing and
 * persistence. Intentionally close to {@code RawJob} but decoupled from storage concerns.
 */
public record RawJobData(
        String source,
        String url,
        String title,
        String company,
        String location,
        String rawDescription) {
}

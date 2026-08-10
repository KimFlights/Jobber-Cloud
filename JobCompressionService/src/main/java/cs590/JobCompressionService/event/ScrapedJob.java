package cs590.JobCompressionService.event;

import java.time.Instant;

/**
 * Inbound event on the {@code scraped-job} topic (from ScraperService). Mirror of the producer's
 * schema; kept as a local record since there is no shared events module.
 */
public record ScrapedJob(
        String jobId,
        String query,
        String source,
        String url,
        String title,
        String company,
        String location,
        String rawDescription,
        Instant scrapedAt) {
}

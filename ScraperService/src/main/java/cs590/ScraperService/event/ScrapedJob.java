package cs590.ScraperService.event;

import java.time.Instant;

/**
 * Outbound event on the {@code scraped-job} topic — one raw job description, consumed by
 * JobCompressionService. {@code jobId} is a content hash so the pipeline is idempotent by id.
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

package cs590.ScraperService.event;

import java.time.Instant;

/**
 * Inbound event on the {@code scrape-request} topic, published by SearchService when a query is
 * stale or thin (ARCHITECTURE.md §4, step 3).
 */
public record ScrapeRequest(String query, Instant requestedAt) {
}

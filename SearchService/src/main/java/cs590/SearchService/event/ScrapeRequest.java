package cs590.SearchService.event;

import java.time.Instant;

/**
 * Outbound event on the {@code scrape-request} topic — SearchService's trigger for a background
 * refresh when a query is stale/thin (ARCHITECTURE.md §4, step 3).
 */
public record ScrapeRequest(String query, Instant requestedAt) {
}

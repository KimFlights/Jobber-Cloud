package cs590.ScraperService.model;

import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Per-query freshness metadata: when a query was last scraped and how many results it yielded.
 * Backs the freshness rule (ARCHITECTURE.md §4.1) without re-counting the corpus each time.
 */
@Document(collection = "query_freshness")
public class QueryFreshness {

    /** Normalized query text. */
    @Id
    private String query;

    private Instant lastScrapedAt;
    private int resultCount;

    public QueryFreshness() {
    }

    public QueryFreshness(String query, Instant lastScrapedAt, int resultCount) {
        this.query = query;
        this.lastScrapedAt = lastScrapedAt;
        this.resultCount = resultCount;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Instant getLastScrapedAt() {
        return lastScrapedAt;
    }

    public void setLastScrapedAt(Instant lastScrapedAt) {
        this.lastScrapedAt = lastScrapedAt;
    }

    public int getResultCount() {
        return resultCount;
    }

    public void setResultCount(int resultCount) {
        this.resultCount = resultCount;
    }
}

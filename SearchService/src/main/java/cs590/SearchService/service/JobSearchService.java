package cs590.SearchService.service;

import cs590.SearchService.messaging.ScrapeRequestPublisher;
import cs590.SearchService.model.JobDocument;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;

/**
 * Read-side search. Queries the Elasticsearch index and returns whatever exists immediately
 * (STALE-NOW — the user never blocks on scraping). If the query is thin (&lt; minResults) or the
 * freshest result is older than maxAge, it fires a background {@code scrape-request}; refreshed
 * results surface on the user's next query (poll, not push) — ARCHITECTURE.md §4 steps 2–3.
 */
@Service
public class JobSearchService {

    private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);
    private static final int MAX_RESULTS = 50;

    private final ElasticsearchOperations operations;
    private final ScrapeRequestPublisher scrapeRequestPublisher;
    private final int minResults;
    private final Duration maxAge;

    public JobSearchService(ElasticsearchOperations operations,
                            ScrapeRequestPublisher scrapeRequestPublisher,
                            @Value("${search.freshness.min-results:10}") int minResults,
                            @Value("${search.freshness.max-age-days:7}") long maxAgeDays) {
        this.operations = operations;
        this.scrapeRequestPublisher = scrapeRequestPublisher;
        this.minResults = minResults;
        this.maxAge = Duration.ofDays(maxAgeDays);
    }

    public List<JobDocument> search(String query) {
        String q = query == null ? "" : query.trim();
        if (q.isBlank()) {
            return List.of();
        }
        List<JobDocument> results = runSearch(q);
        if (isStale(results)) {
            log.info("Query '{}' is stale/thin ({} results) — requesting background scrape", q, results.size());
            scrapeRequestPublisher.publish(q.toLowerCase());
        }
        return results;
    }

    private List<JobDocument> runSearch(String q) {
        Criteria criteria = new Criteria("title").matches(q)
                .or(new Criteria("summary").matches(q))
                .or(new Criteria("coreRequirements").matches(q))
                .or(new Criteria("mustHaves").matches(q))
                .or(new Criteria("company").matches(q));
        CriteriaQuery query = new CriteriaQuery(criteria);
        query.setPageable(PageRequest.of(0, MAX_RESULTS));
        try {
            SearchHits<JobDocument> hits = operations.search(query, JobDocument.class);
            return hits.stream().map(SearchHit::getContent).toList();
        } catch (Exception e) {
            // Missing index / cold start: treat as no results (which will trigger a scrape).
            log.warn("Search failed for '{}': {}", q, e.getMessage());
            return List.of();
        }
    }

    private boolean isStale(List<JobDocument> results) {
        if (results.size() < minResults) {
            return true;
        }
        Instant newest = results.stream()
                .map(JobDocument::getEnrichedAt)
                .filter(Objects::nonNull)
                .max(Comparator.naturalOrder())
                .orElse(null);
        return newest == null || newest.isBefore(Instant.now().minus(maxAge));
    }
}

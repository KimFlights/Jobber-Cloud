package cs590.ScraperService.service;

import cs590.ScraperService.model.RawJob;
import cs590.ScraperService.repository.RawJobRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * The freshness rule (ARCHITECTURE.md §4.1): a query is re-scraped if it has fewer than
 * {@code minResults} stored results OR its newest result is older than {@code maxAge}.
 */
@Component
public class FreshnessPolicy {

    private final RawJobRepository rawJobRepository;
    private final int minResults;
    private final Duration maxAge;

    public FreshnessPolicy(
            RawJobRepository rawJobRepository,
            @org.springframework.beans.factory.annotation.Value("${scraper.freshness.min-results:10}")
            int minResults,
            @org.springframework.beans.factory.annotation.Value("${scraper.freshness.max-age-days:7}")
            long maxAgeDays) {
        this.rawJobRepository = rawJobRepository;
        this.minResults = minResults;
        this.maxAge = Duration.ofDays(maxAgeDays);
    }

    public boolean needsScrape(String query) {
        long count = rawJobRepository.countByQuery(query);
        if (count < minResults) {
            return true;
        }
        Optional<RawJob> newest = rawJobRepository.findTopByQueryOrderByScrapedAtDesc(query);
        if (newest.isEmpty() || newest.get().getScrapedAt() == null) {
            return true;
        }
        return newest.get().getScrapedAt().isBefore(Instant.now().minus(maxAge));
    }
}

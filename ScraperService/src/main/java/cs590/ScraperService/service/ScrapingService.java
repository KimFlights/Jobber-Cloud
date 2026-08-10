package cs590.ScraperService.service;

import cs590.ScraperService.event.ScrapedJob;
import cs590.ScraperService.messaging.ScrapedJobPublisher;
import cs590.ScraperService.model.QueryFreshness;
import cs590.ScraperService.model.RawJob;
import cs590.ScraperService.repository.QueryFreshnessRepository;
import cs590.ScraperService.repository.RawJobRepository;
import cs590.ScraperService.scraper.RawJobData;
import cs590.ScraperService.scraper.SiteScraper;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Orchestrates one scrape request end to end: enforce freshness → fan out over site scrapers →
 * dedupe/upsert raw jobs in Mongo → emit a {@code scraped-job} event per newly stored posting.
 * ScraperService owns the raw corpus only; enrichment happens downstream.
 */
@Service
public class ScrapingService {

    private static final Logger log = LoggerFactory.getLogger(ScrapingService.class);

    private final List<SiteScraper> scrapers;
    private final RawJobRepository rawJobRepository;
    private final QueryFreshnessRepository freshnessRepository;
    private final FreshnessPolicy freshnessPolicy;
    private final ScrapedJobPublisher publisher;

    public ScrapingService(List<SiteScraper> scrapers,
                           RawJobRepository rawJobRepository,
                           QueryFreshnessRepository freshnessRepository,
                           FreshnessPolicy freshnessPolicy,
                           ScrapedJobPublisher publisher) {
        this.scrapers = scrapers;
        this.rawJobRepository = rawJobRepository;
        this.freshnessRepository = freshnessRepository;
        this.freshnessPolicy = freshnessPolicy;
        this.publisher = publisher;
    }

    public void handleScrapeRequest(String query) {
        String normalized = normalize(query);
        if (normalized.isBlank()) {
            return;
        }
        if (!freshnessPolicy.needsScrape(normalized)) {
            log.info("Query '{}' is still fresh — skipping scrape", normalized);
            return;
        }

        int emitted = 0;
        for (SiteScraper scraper : scrapers) {
            List<RawJobData> jobs;
            try {
                jobs = scraper.scrape(normalized);
            } catch (Exception e) {
                log.warn("Scraper {} failed: {}", scraper.siteName(), e.getMessage());
                continue;
            }
            for (RawJobData data : jobs) {
                if (storeAndEmit(normalized, data)) {
                    emitted++;
                }
            }
        }

        long total = rawJobRepository.countByQuery(normalized);
        freshnessRepository.save(new QueryFreshness(normalized, Instant.now(), (int) total));
        log.info("Scrape for '{}' complete: {} new jobs emitted, {} total stored",
                normalized, emitted, total);
    }

    /**
     * Upsert one posting and emit its event. Returns true only for genuinely new postings so
     * duplicates don't re-trigger enrichment.
     */
    private boolean storeAndEmit(String query, RawJobData data) {
        String id = ContentHash.of(data.source(), data.title(), data.company(), data.rawDescription());
        boolean isNew = !rawJobRepository.existsById(id);

        RawJob raw = new RawJob();
        raw.setId(id);
        raw.setQuery(query);
        raw.setSource(data.source());
        raw.setUrl(data.url());
        raw.setTitle(data.title());
        raw.setCompany(data.company());
        raw.setLocation(data.location());
        raw.setRawDescription(data.rawDescription());
        raw.setScrapedAt(Instant.now());
        rawJobRepository.save(raw);

        if (isNew) {
            publisher.publish(new ScrapedJob(
                    id, query, data.source(), data.url(), data.title(),
                    data.company(), data.location(), data.rawDescription(), raw.getScrapedAt()));
        }
        return isNew;
    }

    private String normalize(String query) {
        return query == null ? "" : query.trim().toLowerCase();
    }
}

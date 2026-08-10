package cs590.ScraperService.messaging;

/** Kafka topic names for the scrape→enrich→index pipeline (ARCHITECTURE.md §5). */
public final class Topics {

    public static final String SCRAPE_REQUEST = "scrape-request";
    public static final String SCRAPED_JOB = "scraped-job";

    private Topics() {
    }
}

package cs590.SearchService.messaging;

/** Kafka topics SearchService touches: publishes {@code scrape-request}, consumes {@code enriched-job}. */
public final class Topics {

    public static final String SCRAPE_REQUEST = "scrape-request";
    public static final String ENRICHED_JOB = "enriched-job";

    private Topics() {
    }
}

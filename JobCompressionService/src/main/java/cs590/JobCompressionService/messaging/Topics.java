package cs590.JobCompressionService.messaging;

/** Kafka topics this stateless processor bridges: {@code scraped-job} → {@code enriched-job}. */
public final class Topics {

    public static final String SCRAPED_JOB = "scraped-job";
    public static final String ENRICHED_JOB = "enriched-job";

    private Topics() {
    }
}

package cs590.JobCompressionService.messaging;

import cs590.JobCompressionService.event.EnrichedJob;
import cs590.JobCompressionService.event.ScrapedJob;
import cs590.JobCompressionService.service.JobCompressor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code scraped-job}, compresses it, and publishes {@code enriched-job}. Stateless: the
 * only side effect is the outbound event.
 */
@Component
public class ScrapedJobListener {

    private static final Logger log = LoggerFactory.getLogger(ScrapedJobListener.class);

    private final JobCompressor compressor;
    private final EnrichedJobPublisher publisher;

    public ScrapedJobListener(JobCompressor compressor, EnrichedJobPublisher publisher) {
        this.compressor = compressor;
        this.publisher = publisher;
    }

    @KafkaListener(topics = Topics.SCRAPED_JOB, groupId = "${spring.kafka.consumer.group-id:compression}")
    public void onScrapedJob(ScrapedJob job) {
        if (job == null || job.jobId() == null) {
            return;
        }
        try {
            EnrichedJob enriched = compressor.compress(job);
            publisher.publish(enriched);
            log.info("Enriched job {} ('{}')", job.jobId(), job.title());
        } catch (Exception e) {
            // Let the container's error handling decide on retry/DLT; log for visibility.
            log.error("Failed to enrich job {}: {}", job.jobId(), e.getMessage());
            throw e;
        }
    }
}

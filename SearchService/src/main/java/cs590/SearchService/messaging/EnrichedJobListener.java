package cs590.SearchService.messaging;

import cs590.SearchService.event.EnrichedJob;
import cs590.SearchService.service.JobIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code enriched-job} and indexes it into Elasticsearch. Indexing is an idempotent
 * upsert by job id, so replays are safe (fresh results surface on the user's next query — poll).
 */
@Component
public class EnrichedJobListener {

    private static final Logger log = LoggerFactory.getLogger(EnrichedJobListener.class);

    private final JobIndexer indexer;

    public EnrichedJobListener(JobIndexer indexer) {
        this.indexer = indexer;
    }

    @KafkaListener(topics = Topics.ENRICHED_JOB, groupId = "${spring.kafka.consumer.group-id:search}")
    public void onEnrichedJob(EnrichedJob job) {
        if (job == null || job.jobId() == null) {
            return;
        }
        indexer.index(job);
        log.info("Indexed job {} ('{}')", job.jobId(), job.title());
    }
}

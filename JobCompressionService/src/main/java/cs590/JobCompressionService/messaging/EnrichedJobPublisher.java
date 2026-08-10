package cs590.JobCompressionService.messaging;

import cs590.JobCompressionService.event.EnrichedJob;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes {@code enriched-job} events, keyed by job id. */
@Component
public class EnrichedJobPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public EnrichedJobPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(EnrichedJob job) {
        kafkaTemplate.send(Topics.ENRICHED_JOB, job.jobId(), job);
    }
}

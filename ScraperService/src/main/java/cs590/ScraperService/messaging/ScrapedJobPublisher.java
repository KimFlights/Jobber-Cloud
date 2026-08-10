package cs590.ScraperService.messaging;

import cs590.ScraperService.event.ScrapedJob;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes {@code scraped-job} events, keyed by job id so a posting's events stay ordered. */
@Component
public class ScrapedJobPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ScrapedJobPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(ScrapedJob job) {
        kafkaTemplate.send(Topics.SCRAPED_JOB, job.jobId(), job);
    }
}

package cs590.SearchService.messaging;

import cs590.SearchService.event.ScrapeRequest;
import java.time.Instant;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publishes {@code scrape-request} events to trigger a background refresh, keyed by query. */
@Component
public class ScrapeRequestPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ScrapeRequestPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(String query) {
        kafkaTemplate.send(Topics.SCRAPE_REQUEST, query, new ScrapeRequest(query, Instant.now()));
    }
}

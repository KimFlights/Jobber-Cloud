package cs590.ScraperService.messaging;

import cs590.ScraperService.event.ScrapeRequest;
import cs590.ScraperService.service.ScrapingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/** Consumes {@code scrape-request} events from SearchService and drives the scrape. */
@Component
public class ScrapeRequestListener {

    private static final Logger log = LoggerFactory.getLogger(ScrapeRequestListener.class);

    private final ScrapingService scrapingService;

    public ScrapeRequestListener(ScrapingService scrapingService) {
        this.scrapingService = scrapingService;
    }

    @KafkaListener(topics = Topics.SCRAPE_REQUEST, groupId = "${spring.kafka.consumer.group-id:scraper}")
    public void onScrapeRequest(ScrapeRequest request) {
        if (request == null || request.query() == null) {
            return;
        }
        log.info("Received scrape-request for '{}'", request.query());
        scrapingService.handleScrapeRequest(request.query());
    }
}

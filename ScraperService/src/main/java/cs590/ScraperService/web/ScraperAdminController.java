package cs590.ScraperService.web;

import cs590.ScraperService.service.ScrapingService;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual scrape trigger — a convenience for demos/testing that runs the same path as a
 * {@code scrape-request} Kafka event. The primary trigger remains the event listener.
 */
@RestController
@RequestMapping("/api/scrape")
public class ScraperAdminController {

    private final ScrapingService scrapingService;

    public ScraperAdminController(ScrapingService scrapingService) {
        this.scrapingService = scrapingService;
    }

    @PostMapping
    public Map<String, String> scrape(@RequestParam String query) {
        scrapingService.handleScrapeRequest(query);
        return Map.of("status", "accepted", "query", query);
    }
}

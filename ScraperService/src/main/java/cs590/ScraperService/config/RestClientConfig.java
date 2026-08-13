package cs590.ScraperService.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/**
 * Provides the {@link RestClient.Builder} that the JSON-API scrapers (Arbeitnow, Remotive, Ashby, …)
 * inject. This was previously supplied implicitly by spring-cloud-commons (pulled in transitively by
 * the Consul discovery starter). Now that Consul is removed from the cloud build — discovery is Cloud
 * Map DNS — we declare the builder explicitly so the scrapers own their dependency outright.
 * Each scraper customises its own copy (converters, base URL) via AbstractJsonApiScraper.
 */
@Configuration
public class RestClientConfig {

    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}

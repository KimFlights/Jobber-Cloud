package cs590.ScraperService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Arbeitnow — a tech / European job board that natively serves a key-less public JSON feed. No
 * account required. The feed has no search parameter, so we pull the current page and filter by
 * the query locally ({@link #matchesQuery}).
 *
 * <p>API: {@code GET https://www.arbeitnow.com/api/job-board-api} →
 * {@code { data: [ { title, company_name, url, location, description(HTML), tags[] } ], meta } }.
 */
@Component
@ConditionalOnProperty(name = "scraper.arbeitnow.enabled", havingValue = "true", matchIfMissing = true)
public class ArbeitnowScraper extends AbstractJsonApiScraper {

    public ArbeitnowScraper(RestClient.Builder builder, ScraperHttpProperties http,
                            @Value("${scraper.max-results-per-site:15}") int maxResults) {
        super(builder, http, maxResults);
    }

    @Override
    public String siteName() {
        return "arbeitnow";
    }

    @Override
    protected List<RawJobData> fetch(String query) {
        JsonNode root = getJson("https://www.arbeitnow.com/api/job-board-api");
        List<RawJobData> out = new ArrayList<>();
        for (JsonNode j : root.path("data")) {
            String title = text(j, "title");
            String description = stripHtml(text(j, "description"));
            String tags = joinArray(j, "tags", " ");
            if (!matchesQuery(query, title, description, tags, text(j, "location"))) {
                continue;
            }
            out.add(new RawJobData(
                    siteName(),
                    text(j, "url"),
                    title,
                    text(j, "company_name"),
                    text(j, "location"),
                    description));
            if (out.size() >= maxResults) {
                break;
            }
        }
        return out;
    }
}

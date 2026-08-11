package cs590.ScraperService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Remotive — a remote-jobs board with a free, key-less public API that supports a server-side
 * keyword search, so it maps directly onto our query model. No account required.
 *
 * <p>API: {@code GET https://remotive.com/api/remote-jobs?search=<query>&limit=<n>} →
 * {@code { jobs: [ { title, company_name, url, candidate_required_location, description(HTML) } ] }}.
 */
@Component
@ConditionalOnProperty(name = "scraper.remotive.enabled", havingValue = "true", matchIfMissing = true)
public class RemotiveScraper extends AbstractJsonApiScraper {

    public RemotiveScraper(RestClient.Builder builder, ScraperHttpProperties http,
                           @Value("${scraper.max-results-per-site:15}") int maxResults) {
        super(builder, http, maxResults);
    }

    @Override
    public String siteName() {
        return "remotive";
    }

    @Override
    protected List<RawJobData> fetch(String query) {
        JsonNode root = getJson("https://remotive.com/api/remote-jobs?search="
                + encode(query) + "&limit=" + maxResults);
        List<RawJobData> out = new ArrayList<>();
        for (JsonNode j : root.path("jobs")) {
            // Remotive already filtered by the search term server-side.
            out.add(new RawJobData(
                    siteName(),
                    text(j, "url"),
                    text(j, "title"),
                    text(j, "company_name"),
                    text(j, "candidate_required_location"),
                    stripHtml(text(j, "description"))));
            if (out.size() >= maxResults) {
                break;
            }
        }
        return out;
    }
}

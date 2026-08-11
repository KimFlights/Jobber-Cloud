package cs590.ScraperService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Himalayas — a remote-jobs board that maintains an open API for third-party aggregators. No
 * account required. We pull a page of the feed and filter by the query locally.
 *
 * <p>API: {@code GET https://himalayas.app/jobs/api?limit=<n>} →
 * {@code { jobs: [ { title, companyName, applicationLink, locationRestrictions[], description(HTML),
 * categories[], excerpt } ], totalCount } }.
 */
@Component
@ConditionalOnProperty(name = "scraper.himalayas.enabled", havingValue = "true", matchIfMissing = true)
public class HimalayasScraper extends AbstractJsonApiScraper {

    /** The feed has no search param, so we scan a larger page and keep the query-relevant ones. */
    private static final int FEED_PAGE = 100;

    public HimalayasScraper(RestClient.Builder builder, ScraperHttpProperties http,
                            @Value("${scraper.max-results-per-site:15}") int maxResults) {
        super(builder, http, maxResults);
    }

    @Override
    public String siteName() {
        return "himalayas";
    }

    @Override
    protected List<RawJobData> fetch(String query) {
        JsonNode root = getJson("https://himalayas.app/jobs/api?limit=" + FEED_PAGE);
        List<RawJobData> out = new ArrayList<>();
        for (JsonNode j : root.path("jobs")) {
            String title = text(j, "title");
            String description = stripHtml(text(j, "description"));
            String categories = joinArray(j, "categories", " ");
            String location = joinArray(j, "locationRestrictions", ", ");
            if (!matchesQuery(query, title, description, categories, text(j, "excerpt"))) {
                continue;
            }
            out.add(new RawJobData(
                    siteName(),
                    text(j, "applicationLink"),
                    title,
                    text(j, "companyName"),
                    location,
                    description));
            if (out.size() >= maxResults) {
                break;
            }
        }
        return out;
    }
}

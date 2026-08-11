package cs590.ScraperService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Ashby — an ATS whose customers expose a key-less public job-board API at a predictable URL. No
 * account required. This is a per-company source: it reads a configured list of Ashby job-board
 * names ({@code scraper.ashby.companies}), pulls each board, and keeps the query-relevant postings.
 *
 * <p>API: {@code GET https://api.ashbyhq.com/posting-api/job-board/<board>} →
 * {@code { jobs: [ { title, location, jobUrl, applyUrl, descriptionPlain, department, isRemote } ] }}.
 * There is no company field (the board <em>is</em> the company), so the configured slug is used.
 */
@Component
@ConditionalOnProperty(name = "scraper.ashby.enabled", havingValue = "true", matchIfMissing = true)
public class AshbyScraper extends AbstractJsonApiScraper {

    private final List<String> companies;

    public AshbyScraper(RestClient.Builder builder, ScraperHttpProperties http,
                        @Value("${scraper.max-results-per-site:15}") int maxResults,
                        @Value("${scraper.ashby.companies:}") String companiesCsv) {
        super(builder, http, maxResults);
        this.companies = csv(companiesCsv);
    }

    @Override
    public String siteName() {
        return "ashby";
    }

    @Override
    protected List<RawJobData> fetch(String query) {
        List<RawJobData> out = new ArrayList<>();
        for (String company : companies) {
            JsonNode root = getJsonQuietly("https://api.ashbyhq.com/posting-api/job-board/" + encode(company));
            for (JsonNode j : root.path("jobs")) {
                String title = text(j, "title");
                String description = text(j, "descriptionPlain"); // Ashby provides plain text already
                if (!matchesQuery(query, title, description, text(j, "department"), text(j, "location"))) {
                    continue;
                }
                out.add(new RawJobData(
                        siteName(),
                        text(j, "jobUrl"),
                        title,
                        prettify(company),
                        text(j, "location"),
                        description));
                if (out.size() >= maxResults) {
                    return out;
                }
            }
        }
        return out;
    }

    /** Turn a board slug like "ashbyhq" into a display-ish company name. */
    private String prettify(String slug) {
        if (slug == null || slug.isEmpty()) {
            return slug;
        }
        return Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
    }
}

package cs590.ScraperService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Lever — an ATS whose customers expose a key-less public postings feed at a predictable URL. No
 * account required. Per-company source, driven by {@code scraper.lever.companies}.
 *
 * <p><strong>Off by default</strong> and shipped with an empty board list: unlike Ashby/
 * SmartRecruiters, we could not verify a currently-live public Lever board at build time (many
 * companies have migrated off Lever, and unknown handles return 404). Enable it and supply your
 * own board handles to use it: {@code scraper.lever.enabled=true},
 * {@code scraper.lever.companies=<handle1>,<handle2>}.
 *
 * <p>API: {@code GET https://api.lever.co/v0/postings/<handle>?mode=json} →
 * {@code [ { text(title), hostedUrl, categories{location,commitment,team}, descriptionPlain } ]}.
 */
@Component
@ConditionalOnProperty(name = "scraper.lever.enabled", havingValue = "true")
public class LeverScraper extends AbstractJsonApiScraper {

    private final List<String> companies;

    public LeverScraper(RestClient.Builder builder, ScraperHttpProperties http,
                        @Value("${scraper.max-results-per-site:15}") int maxResults,
                        @Value("${scraper.lever.companies:}") String companiesCsv) {
        super(builder, http, maxResults);
        this.companies = csv(companiesCsv);
    }

    @Override
    public String siteName() {
        return "lever";
    }

    @Override
    protected List<RawJobData> fetch(String query) {
        List<RawJobData> out = new ArrayList<>();
        for (String company : companies) {
            JsonNode root = getJsonQuietly("https://api.lever.co/v0/postings/" + encode(company) + "?mode=json");
            if (!root.isArray()) {
                continue;
            }
            for (JsonNode j : root) {
                String title = text(j, "text");
                String description = text(j, "descriptionPlain");
                JsonNode cats = j.path("categories");
                String location = text(cats, "location");
                if (!matchesQuery(query, title, description, text(cats, "team"), location)) {
                    continue;
                }
                out.add(new RawJobData(
                        siteName(),
                        text(j, "hostedUrl"),
                        title,
                        prettify(company),
                        location,
                        description));
                if (out.size() >= maxResults) {
                    return out;
                }
            }
        }
        return out;
    }

    private String prettify(String slug) {
        if (slug == null || slug.isEmpty()) {
            return slug;
        }
        return Character.toUpperCase(slug.charAt(0)) + slug.substring(1);
    }
}

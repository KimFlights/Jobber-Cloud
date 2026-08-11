package cs590.ScraperService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * SmartRecruiters — an ATS whose customers expose a key-less public postings API that even supports
 * a server-side keyword search ({@code q}). No account required. Per-company source: reads a
 * configured list of company identifiers ({@code scraper.smartrecruiters.companies}).
 *
 * <p>API: {@code GET https://api.smartrecruiters.com/v1/companies/<co>/postings?q=<query>&limit=<n>}
 * → {@code { content: [ { id, name, location{city,region,remote}, company{name} } ], totalFound }}.
 * The list view carries no description; the public posting URL is
 * {@code https://jobs.smartrecruiters.com/<co>/<id>} and enrichment summarizes from the title.
 */
@Component
@ConditionalOnProperty(name = "scraper.smartrecruiters.enabled", havingValue = "true", matchIfMissing = true)
public class SmartRecruitersScraper extends AbstractJsonApiScraper {

    private final List<String> companies;

    public SmartRecruitersScraper(RestClient.Builder builder, ScraperHttpProperties http,
                                  @Value("${scraper.max-results-per-site:15}") int maxResults,
                                  @Value("${scraper.smartrecruiters.companies:}") String companiesCsv) {
        super(builder, http, maxResults);
        this.companies = csv(companiesCsv);
    }

    @Override
    public String siteName() {
        return "smartrecruiters";
    }

    @Override
    protected List<RawJobData> fetch(String query) {
        List<RawJobData> out = new ArrayList<>();
        for (String company : companies) {
            JsonNode root = getJsonQuietly("https://api.smartrecruiters.com/v1/companies/" + encode(company)
                    + "/postings?q=" + encode(query) + "&limit=" + maxResults);
            for (JsonNode j : root.path("content")) {
                String title = text(j, "name");
                String id = text(j, "id");
                JsonNode loc = j.path("location");
                String location = joinNonBlank(", ", text(loc, "city"), text(loc, "region"));
                JsonNode co = j.path("company");
                String companyName = text(co, "name");
                out.add(new RawJobData(
                        siteName(),
                        "https://jobs.smartrecruiters.com/" + company + "/" + id,
                        title,
                        companyName != null ? companyName : company,
                        location,
                        null)); // no description in the list view; LLM summarizes from the title
                if (out.size() >= maxResults) {
                    return out;
                }
            }
        }
        return out;
    }

    private String joinNonBlank(String sep, String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                if (sb.length() > 0) {
                    sb.append(sep);
                }
                sb.append(p);
            }
        }
        return sb.length() == 0 ? null : sb.toString();
    }
}

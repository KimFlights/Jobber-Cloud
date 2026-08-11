package cs590.ScraperService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Hacker News "Ask HN: Who is hiring?" — the monthly hiring threads are entirely public, and HN's
 * official Firebase API is free and key-less. No account required. We resolve the latest hiring
 * thread from the {@code whoishiring} user's submissions, then read its top-level comments (each
 * comment is one job posting) and keep the query-relevant ones.
 *
 * <p>Postings follow a loose convention: {@code Company | Role | Location | REMOTE | $comp …}. We
 * split on {@code |} for company/title and keep the whole comment as the description for enrichment.
 *
 * <p>This source makes one HTTP call per comment, so it is the slowest; {@link #MAX_COMMENTS_SCANNED}
 * bounds the fan-out.
 */
@Component
@ConditionalOnProperty(name = "scraper.hackernews.enabled", havingValue = "true", matchIfMissing = true)
public class HackerNewsScraper extends AbstractJsonApiScraper {

    private static final String ITEM = "https://hacker-news.firebaseio.com/v0/item/%s.json";
    private static final String USER = "https://hacker-news.firebaseio.com/v0/user/whoishiring.json";
    private static final int MAX_COMMENTS_SCANNED = 80;

    public HackerNewsScraper(RestClient.Builder builder, ScraperHttpProperties http,
                             @Value("${scraper.max-results-per-site:15}") int maxResults) {
        super(builder, http, maxResults);
    }

    @Override
    public String siteName() {
        return "hackernews";
    }

    @Override
    protected List<RawJobData> fetch(String query) {
        JsonNode thread = latestHiringThread();
        if (thread == null) {
            return List.of();
        }
        List<RawJobData> out = new ArrayList<>();
        int scanned = 0;
        for (JsonNode kid : thread.path("kids")) {
            if (scanned >= MAX_COMMENTS_SCANNED || out.size() >= maxResults) {
                break;
            }
            scanned++;
            JsonNode c = getJsonQuietly(ITEM.formatted(kid.asText()));
            String html = text(c, "text");
            if (html == null || c.path("deleted").asBoolean(false) || c.path("dead").asBoolean(false)) {
                continue;
            }
            String plain = stripHtml(html);
            if (!matchesQuery(query, plain)) {
                continue;
            }
            String[] parts = plain.split("\\|");
            String company = parts.length > 0 ? parts[0].trim() : "Unknown";
            String title = parts.length > 1 ? parts[1].trim() : firstWords(plain);
            String location = parts.length > 2 ? parts[2].trim() : null;
            out.add(new RawJobData(
                    siteName(),
                    "https://news.ycombinator.com/item?id=" + c.path("id").asText(),
                    title,
                    company,
                    location,
                    plain));
        }
        return out;
    }

    /** Newest {@code Who is hiring?} story from the whoishiring user's recent submissions. */
    private JsonNode latestHiringThread() {
        JsonNode user = getJsonQuietly(USER);
        JsonNode submitted = user.path("submitted");
        int checked = 0;
        for (JsonNode id : submitted) {
            if (checked++ >= 8) {
                break; // the hiring thread is always among the most recent submissions
            }
            JsonNode item = getJsonQuietly(ITEM.formatted(id.asText()));
            String title = text(item, "title");
            if (title != null) {
                String t = title.toLowerCase();
                if (t.contains("who is hiring") && !t.contains("wants to be hired")
                        && !t.contains("freelancer")) {
                    return item;
                }
            }
        }
        return null;
    }

    private String firstWords(String text) {
        String trimmed = text.strip();
        return trimmed.length() <= 80 ? trimmed : trimmed.substring(0, 80) + "…";
    }
}

package cs590.ScraperService.scraper;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Deterministic demo scraper. Generates plausible postings from the query text so the whole
 * scrape→enrich→index pipeline is exercisable locally without hitting live job boards (whose
 * Terms of Service / robots.txt legality is flagged in ARCHITECTURE.md §9 and the README).
 *
 * <p>Enabled by default; disable with {@code scraper.sample.enabled=false} once real
 * {@link JsoupSiteScraper} sites are configured.
 */
@Component
@ConditionalOnProperty(name = "scraper.sample.enabled", havingValue = "true", matchIfMissing = true)
public class SampleSiteScraper implements SiteScraper {

    private static final String[] COMPANIES = {
            "Acme Corp", "Globex", "Initech", "Umbrella", "Hooli", "Stark Industries"};
    private static final String[] LOCATIONS = {
            "Remote", "New York, NY", "San Francisco, CA", "Austin, TX", "Seattle, WA"};

    private final int jobsPerQuery;

    public SampleSiteScraper(
            @org.springframework.beans.factory.annotation.Value("${scraper.sample.jobs-per-query:12}")
            int jobsPerQuery) {
        this.jobsPerQuery = jobsPerQuery;
    }

    @Override
    public String siteName() {
        return "sample";
    }

    @Override
    public List<RawJobData> scrape(String query) {
        List<RawJobData> jobs = new ArrayList<>(jobsPerQuery);
        for (int i = 0; i < jobsPerQuery; i++) {
            String company = COMPANIES[i % COMPANIES.length];
            String location = LOCATIONS[i % LOCATIONS.length];
            String title = "%s (%s)".formatted(query, i % 2 == 0 ? "Senior" : "Mid-level");
            String url = "https://example.com/jobs/%s/%d".formatted(slug(query), i);
            String description = """
                    %s at %s — %s.

                    We are hiring a %s. Responsibilities include building and operating services,
                    collaborating across teams, and owning features end to end. Requirements: strong
                    fundamentals related to "%s", good communication, and a track record of shipping.
                    Nice to have: cloud experience, testing discipline, and mentorship.
                    """.formatted(title, company, location, query, query);
            jobs.add(new RawJobData(siteName(), url, title, company, location, description));
        }
        return jobs;
    }

    private String slug(String query) {
        return query.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}

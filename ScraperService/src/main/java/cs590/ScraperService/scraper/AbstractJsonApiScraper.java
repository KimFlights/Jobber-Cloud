package cs590.ScraperService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.RestClient;

/**
 * Base for JSON-API-backed site scrapers (public job-board APIs and ATS feeds). Handles the shared
 * mechanics — a configured {@link RestClient}, the circuit breaker, HTML→text cleanup, and simple
 * query relevance filtering — so a new source is just a subclass that supplies its endpoint(s) and
 * maps the response into {@link RawJobData}.
 *
 * <p>This is the JSON counterpart to {@link AbstractJsoupSiteScraper}. Prefer it for sources that
 * expose a documented feed/API (no anti-scraping walls, no ToS grey area) over HTML scraping.
 *
 * <p>To add a source: extend this class, implement {@link #siteName()} and {@link #fetch(String)},
 * annotate as a {@code @Component} gated behind its own {@code @ConditionalOnProperty} flag, and the
 * orchestrator picks it up automatically via the injected {@code List<SiteScraper>}.
 */
public abstract class AbstractJsonApiScraper implements SiteScraper {

    private static final Logger log = LoggerFactory.getLogger(AbstractJsonApiScraper.class);

    // Own Jackson-2 mapper: Boot 4's web stack defaults to Jackson 3, whose RestClient converter
    // cannot produce a Jackson-2 JsonNode. Fetching the body as a String and parsing it here keeps
    // this decoupled from whichever Jackson the web layer wires.
    private static final ObjectMapper MAPPER = new ObjectMapper();

    protected final RestClient client;
    protected final int maxResults;

    protected AbstractJsonApiScraper(RestClient.Builder builder, ScraperHttpProperties http, int maxResults) {
        this.client = builder
                .defaultHeader("User-Agent", http.getUserAgent())
                .defaultHeader("Accept", "application/json")
                .build();
        this.maxResults = maxResults;
    }

    /** Map this source's API response(s) for a query into raw postings. May throw on hard errors. */
    protected abstract List<RawJobData> fetch(String query) throws Exception;

    @Override
    @CircuitBreaker(name = "siteScrape", fallbackMethod = "fallback")
    public List<RawJobData> scrape(String query) {
        try {
            return fetch(query);
        } catch (Exception e) {
            log.warn("JSON scrape failed for site {}: {}", siteName(), e.getMessage());
            return List.of();
        }
    }

    /** Circuit-breaker fallback: return nothing rather than failing the whole scrape run. */
    @SuppressWarnings("unused")
    List<RawJobData> fallback(String query, Throwable t) {
        log.warn("Circuit open for site {} — returning no results", siteName());
        return List.of();
    }

    /** GET the URL as text and parse it into a Jackson tree. {@link URI} avoids template expansion. */
    protected JsonNode getJson(String url) {
        String body = client.get().uri(URI.create(url)).retrieve().body(String.class);
        if (body == null || body.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return MAPPER.readTree(body);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse JSON from " + url + ": " + e.getMessage(), e);
        }
    }

    /**
     * Like {@link #getJson} but returns an empty node instead of throwing — for per-item loops
     * (per-company ATS boards, per-comment HN) where one bad slug/id must not abort the siblings.
     */
    protected JsonNode getJsonQuietly(String url) {
        try {
            return getJson(url);
        } catch (Exception e) {
            log.debug("GET {} failed for site {}: {}", url, siteName(), e.getMessage());
            return NullNode.getInstance();
        }
    }

    /** Field text, or {@code null} for missing/null nodes. */
    protected String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    /** Flatten a string-array field (e.g. tags, location restrictions) into a joined string. */
    protected String joinArray(JsonNode node, String field, String sep) {
        JsonNode arr = node.get(field);
        if (arr == null || !arr.isArray()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode el : arr) {
            if (sb.length() > 0) {
                sb.append(sep);
            }
            sb.append(el.asText());
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** Strip HTML markup down to readable text (job descriptions are frequently HTML). */
    protected String stripHtml(String html) {
        return html == null || html.isBlank() ? html : Jsoup.parse(html).text();
    }

    /**
     * Lightweight relevance filter for sources without a server-side keyword search: keep a posting
     * when any meaningful query token (length ≥ 3) appears anywhere in the supplied text. ES applies
     * the authoritative relevance ranking later at search time; this just avoids flooding the index
     * with obviously-unrelated postings from a full feed.
     */
    protected boolean matchesQuery(String query, String... haystackParts) {
        String hay = String.join(" ",
                Arrays.stream(haystackParts).filter(Objects::nonNull).toList()).toLowerCase();
        for (String token : query.toLowerCase().split("\\W+")) {
            if (token.length() >= 3 && hay.contains(token)) {
                return true;
            }
        }
        return false;
    }

    protected String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /** Parse a comma-separated config value (e.g. company slugs) into a trimmed, non-empty list. */
    protected static List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}

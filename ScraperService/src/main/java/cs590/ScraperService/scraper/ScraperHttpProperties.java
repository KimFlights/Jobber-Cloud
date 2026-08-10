package cs590.ScraperService.scraper;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Shared HTTP settings for all jsoup-based site scrapers, bound from {@code scraper.http.*}. */
@Component
@ConfigurationProperties(prefix = "scraper.http")
public class ScraperHttpProperties {

    private String userAgent = "JobberBot/1.0 (+https://example.com/bot)";
    private int timeoutMillis = 8000;

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public int getTimeoutMillis() {
        return timeoutMillis;
    }

    public void setTimeoutMillis(int timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }
}

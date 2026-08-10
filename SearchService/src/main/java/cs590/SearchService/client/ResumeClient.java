package cs590.SearchService.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Synchronous client for ResumeService's embedding endpoint — the primary circuit-breaker
 * protected call in the system (ARCHITECTURE.md §5, Resilience). On an open circuit or error it
 * returns {@link Optional#empty()} so match degrades gracefully instead of failing hard.
 */
@Component
public class ResumeClient {

    private static final Logger log = LoggerFactory.getLogger(ResumeClient.class);

    private final RestClient restClient;

    public ResumeClient(RestClient resumeRestClient) {
        this.restClient = resumeRestClient;
    }

    @CircuitBreaker(name = "resumeEmbedding", fallbackMethod = "fallback")
    public Optional<ResumeEmbedding> fetchEmbedding(String cognitoSub) {
        ResumeEmbedding body = restClient.get()
                .uri("/api/resumes/{sub}/embedding", cognitoSub)
                .retrieve()
                .body(ResumeEmbedding.class);
        return Optional.ofNullable(body);
    }

    @SuppressWarnings("unused")
    Optional<ResumeEmbedding> fallback(String cognitoSub, Throwable t) {
        log.warn("Resume embedding fetch failed for {}: {}", cognitoSub, t.getMessage());
        return Optional.empty();
    }
}

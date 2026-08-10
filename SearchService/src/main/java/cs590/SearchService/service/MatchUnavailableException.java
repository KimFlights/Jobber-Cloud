package cs590.SearchService.service;

/**
 * Thrown when a match cannot be computed (missing embedding, or ResumeService unreachable behind
 * an open circuit). Mapped to HTTP 503.
 */
public class MatchUnavailableException extends RuntimeException {
    public MatchUnavailableException(String message) {
        super(message);
    }
}

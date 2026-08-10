package cs590.SearchService.web.dto;

/** The result of an on-demand match: a single percentage, no LLM verdict. */
public record MatchResponse(String jobId, int matchPercent) {
}

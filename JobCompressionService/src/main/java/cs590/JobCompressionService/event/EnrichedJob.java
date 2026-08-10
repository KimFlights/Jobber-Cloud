package cs590.JobCompressionService.event;

import java.time.Instant;
import java.util.List;

/**
 * Outbound event on the {@code enriched-job} topic — the structured, summarized, embedded job.
 * This IS the searchable document SearchService will index into Elasticsearch (ARCHITECTURE.md
 * §2: "the enriched/compressed job IS the searchable ES document"). {@code embeddingDim} lets the
 * consumer assert the vector space before indexing.
 */
public record EnrichedJob(
        String jobId,
        String query,
        String source,
        String url,
        String title,
        String company,
        String location,
        String summary,
        List<String> coreRequirements,
        String experienceLevel,
        List<String> mustHaves,
        List<String> niceToHaves,
        List<String> responsibilities,
        List<Float> embedding,
        int embeddingDim,
        Instant enrichedAt) {
}

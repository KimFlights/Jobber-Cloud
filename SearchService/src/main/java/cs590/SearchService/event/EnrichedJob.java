package cs590.SearchService.event;

import java.time.Instant;
import java.util.List;

/**
 * Inbound event on the {@code enriched-job} topic (from JobCompressionService). This is the
 * searchable document SearchService persists/indexes into Elasticsearch.
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

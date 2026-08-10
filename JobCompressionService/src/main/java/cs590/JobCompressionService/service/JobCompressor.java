package cs590.JobCompressionService.service;

import cs590.JobCompressionService.event.EnrichedJob;
import cs590.JobCompressionService.event.ScrapedJob;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

/**
 * Turns a raw {@link ScrapedJob} into an {@link EnrichedJob}: one LLM call to structure/summarize,
 * then one embedding call in the shared/pinned vector space. Fully stateless — the Kafka log is
 * the source of truth and every result is derived deterministically from the input (idempotent by
 * job id).
 */
@Service
public class JobCompressor {

    private static final int MAX_CHARS = 12_000;

    private final ChatClient chatClient;
    private final EmbeddingModel embeddingModel;

    public JobCompressor(ChatClient chatClient, EmbeddingModel embeddingModel) {
        this.chatClient = chatClient;
        this.embeddingModel = embeddingModel;
    }

    public EnrichedJob compress(ScrapedJob job) {
        CompressionResult result = structure(job);
        float[] embedding = embeddingModel.embed(embeddingText(job, result));
        List<Float> vector = toList(embedding);

        return new EnrichedJob(
                job.jobId(),
                job.query(),
                job.source(),
                job.url(),
                job.title(),
                job.company(),
                job.location(),
                result.summary(),
                nullSafe(result.coreRequirements()),
                result.experienceLevel(),
                nullSafe(result.mustHaves()),
                nullSafe(result.niceToHaves()),
                nullSafe(result.responsibilities()),
                vector,
                vector.size(),
                Instant.now());
    }

    /** The single LLM call — structured output mapped onto {@link CompressionResult}. */
    private CompressionResult structure(ScrapedJob job) {
        String raw = job.rawDescription() == null ? "" : job.rawDescription();
        if (raw.length() > MAX_CHARS) {
            raw = raw.substring(0, MAX_CHARS);
        }
        return chatClient.prompt()
                .system("""
                        You compress raw job postings into structured fields for search.
                        Extract: a one-paragraph summary; core requirements; the experience level
                        (e.g. Intern, Junior, Mid, Senior, Staff); must-have requirements vs
                        nice-to-haves; and key responsibilities. Be faithful to the posting; do not
                        invent requirements.""")
                .user("Title: %s\nCompany: %s\nLocation: %s\n\nDescription:\n%s"
                        .formatted(job.title(), job.company(), job.location(), raw))
                .call()
                .entity(CompressionResult.class);
    }

    /**
     * Text fed to the embedding model. Uses the compressed fields (not the raw HTML) so the vector
     * reflects the distilled meaning of the posting, matching how resumes are embedded.
     */
    private String embeddingText(ScrapedJob job, CompressionResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append(job.title() == null ? "" : job.title()).append('\n');
        if (result.summary() != null) {
            sb.append(result.summary()).append('\n');
        }
        appendAll(sb, result.coreRequirements());
        appendAll(sb, result.mustHaves());
        appendAll(sb, result.responsibilities());
        return sb.toString().strip();
    }

    private void appendAll(StringBuilder sb, List<String> items) {
        if (items != null) {
            for (String item : items) {
                sb.append(item).append('\n');
            }
        }
    }

    private List<String> nullSafe(List<String> list) {
        return list == null ? List.of() : list;
    }

    private List<Float> toList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }
}

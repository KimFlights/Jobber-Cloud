package cs590.ResumeService.dto;

import cs590.ResumeService.model.ResumeEntity;
import java.time.Instant;
import java.util.List;

/**
 * Client-facing view of a stored resume. Deliberately omits the raw embedding vector — that is
 * served only to SearchService via {@link EmbeddingResponse}.
 */
public record ResumeView(
        String cognitoSub,
        String contactEmail,
        String headline,
        List<String> skills,
        List<String> suggestedQueries,
        Instant updatedAt) {

    public static ResumeView from(ResumeEntity e) {
        return new ResumeView(
                e.getCognitoSub(),
                e.getContactEmail(),
                e.getHeadline(),
                e.getSkills(),
                e.getSuggestedQueries(),
                e.getUpdatedAt());
    }
}

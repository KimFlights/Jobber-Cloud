package cs590.SearchService.client;

import java.util.List;

/** ResumeService's embedding response shape (see ResumeService EmbeddingResponse). */
public record ResumeEmbedding(String cognitoSub, int dimension, List<Float> embedding) {
}

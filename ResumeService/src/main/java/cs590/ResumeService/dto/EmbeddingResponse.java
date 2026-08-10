package cs590.ResumeService.dto;

import java.util.List;

/**
 * The resume embedding, served to SearchService at match time (the primary circuit-breaker
 * protected call in the system). {@code dimension} lets the caller assert the shared vector
 * space matches the job embeddings before computing cosine similarity.
 */
public record EmbeddingResponse(String cognitoSub, int dimension, List<Float> embedding) {
}

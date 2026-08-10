package cs590.SearchService.service;

import java.util.List;

/** Cosine similarity between two equal-length vectors. Result is in [-1, 1]. */
public final class CosineSimilarity {

    private CosineSimilarity() {
    }

    public static double between(List<Float> a, List<Float> b) {
        if (a == null || b == null || a.isEmpty() || a.size() != b.size()) {
            throw new IllegalArgumentException("Vectors must be non-empty and equal length");
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.size(); i++) {
            double x = a.get(i);
            double y = b.get(i);
            dot += x * y;
            normA += x * x;
            normB += y * y;
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    /** Cosine mapped to a 0–100 percentage (negative similarities clamp to 0). */
    public static int toPercent(double cosine) {
        return (int) Math.round(Math.max(0.0, Math.min(1.0, cosine)) * 100);
    }
}

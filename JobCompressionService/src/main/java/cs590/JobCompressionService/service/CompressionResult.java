package cs590.JobCompressionService.service;

import java.util.List;

/**
 * Structured output shape for the single LLM call that parses a raw job description into fields.
 * Spring AI maps the model's JSON response onto this record.
 */
public record CompressionResult(
        String summary,
        List<String> coreRequirements,
        String experienceLevel,
        List<String> mustHaves,
        List<String> niceToHaves,
        List<String> responsibilities) {
}

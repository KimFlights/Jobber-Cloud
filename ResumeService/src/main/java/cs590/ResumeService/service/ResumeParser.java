package cs590.ResumeService.service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Light, deterministic parsing of resume text into a few structured fields. Kept heuristic on
 * purpose: the single permitted LLM call at upload time is spent on suggested queries
 * (ARCHITECTURE.md §2), not on parsing.
 */
@Component
public class ResumeParser {

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    /** A small, extensible dictionary of common tech skills to surface from the text. */
    private static final List<String> KNOWN_SKILLS = List.of(
            "Java", "Kotlin", "Python", "JavaScript", "TypeScript", "Go", "Rust", "C++", "C#",
            "Spring", "Spring Boot", "React", "Angular", "Vue", "Node.js", "Kafka", "MongoDB",
            "Postgres", "PostgreSQL", "Elasticsearch", "Redis", "Docker", "Kubernetes", "AWS",
            "GCP", "Azure", "Terraform", "GraphQL", "REST", "gRPC", "Microservices", "SQL");

    public ParsedResume parse(String text) {
        String contactEmail = firstMatch(EMAIL, text);
        String headline = firstNonBlankLine(text);
        List<String> skills = detectSkills(text);
        return new ParsedResume(contactEmail, headline, skills);
    }

    private String firstMatch(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group() : null;
    }

    private String firstNonBlankLine(String text) {
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.isBlank()) {
                return trimmed.length() > 140 ? trimmed.substring(0, 140) : trimmed;
            }
        }
        return null;
    }

    private List<String> detectSkills(String text) {
        String haystack = text.toLowerCase();
        Set<String> found = new LinkedHashSet<>();
        for (String skill : KNOWN_SKILLS) {
            if (haystack.contains(skill.toLowerCase())) {
                found.add(skill);
            }
        }
        return new ArrayList<>(found);
    }

    public record ParsedResume(String contactEmail, String headline, List<String> skills) {
    }
}

package cs590.ResumeService.service;

import cs590.ResumeService.model.ResumeEntity;
import cs590.ResumeService.repository.ResumeRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the resume lifecycle: parse → embed → one LLM call for suggested queries → persist.
 * The embedding is produced with the shared/pinned model so the vector space matches the job
 * embeddings SearchService compares against (ARCHITECTURE.md §5).
 */
@Service
public class ResumeService {

    private static final int MAX_QUERIES = 6;
    /** Cap resume text sent to the models to keep token cost/latency bounded. */
    private static final int MAX_CHARS = 12_000;

    private final ResumeRepository repository;
    private final ResumeParser parser;
    private final EmbeddingModel embeddingModel;
    private final ChatClient chatClient;

    public ResumeService(ResumeRepository repository, ResumeParser parser,
                         EmbeddingModel embeddingModel, ChatClient chatClient) {
        this.repository = repository;
        this.parser = parser;
        this.embeddingModel = embeddingModel;
        this.chatClient = chatClient;
    }

    @Transactional
    public ResumeEntity upload(String cognitoSub, String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Resume text must not be blank");
        }
        String trimmed = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;

        ResumeParser.ParsedResume parsed = parser.parse(trimmed);
        float[] embedding = embeddingModel.embed(trimmed);
        List<String> queries = generateSuggestedQueries(trimmed);

        ResumeEntity entity = repository.findById(cognitoSub)
                .orElseGet(() -> new ResumeEntity(cognitoSub));
        entity.setRawText(trimmed);
        entity.setContactEmail(parsed.contactEmail());
        entity.setHeadline(parsed.headline());
        entity.setSkills(parsed.skills());
        entity.setEmbedding(toList(embedding));
        entity.setSuggestedQueries(queries);
        entity.setUpdatedAt(Instant.now());
        return repository.save(entity);
    }

    @Transactional(readOnly = true)
    public ResumeEntity get(String cognitoSub) {
        return repository.findById(cognitoSub)
                .orElseThrow(() -> new ResumeNotFoundException(cognitoSub));
    }

    /**
     * The single upload-time LLM call. Asks for a short list of concrete job-search queries and
     * parses them line-by-line. Failures degrade gracefully to no suggestions rather than
     * blocking the upload.
     */
    private List<String> generateSuggestedQueries(String resumeText) {
        try {
            String content = chatClient.prompt()
                    .system("""
                            You help a job seeker search job boards. Given resume text, output
                            concrete search queries they should run (role titles + key skills,
                            e.g. "Senior Java Backend Engineer Kafka"). Output ONE query per line,
                            no numbering, no commentary, at most %d lines.""".formatted(MAX_QUERIES))
                    .user(resumeText)
                    .call()
                    .content();
            return parseQueries(content);
        } catch (Exception e) {
            // Suggested queries are a convenience; never fail the upload over them.
            return List.of();
        }
    }

    private List<String> parseQueries(String content) {
        if (content == null) {
            return List.of();
        }
        List<String> queries = new ArrayList<>();
        for (String line : content.split("\\R")) {
            String q = line.replaceFirst("^\\s*[-*\\d.\\)]+\\s*", "").trim();
            if (!q.isBlank()) {
                queries.add(q);
            }
            if (queries.size() >= MAX_QUERIES) {
                break;
            }
        }
        return queries;
    }

    private List<Float> toList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }
}

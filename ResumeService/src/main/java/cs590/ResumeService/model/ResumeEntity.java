package cs590.ResumeService.model;

import cs590.ResumeService.persistence.FloatListConverter;
import cs590.ResumeService.persistence.StringListConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;

/**
 * A user's resume, keyed by the Cognito {@code sub}. Owns the parsed text, the pinned-model
 * embedding, and the LLM-suggested search queries. This is the only place resume state lives
 * (see ARCHITECTURE.md §2 / §6.1).
 */
@Entity
@Table(name = "resumes")
public class ResumeEntity {

    /** Cognito {@code sub} — the per-user key everywhere in the system. */
    @Id
    @Column(name = "cognito_sub", nullable = false, updatable = false)
    private String cognitoSub;

    @Column(name = "raw_text", columnDefinition = "text")
    private String rawText;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "headline")
    private String headline;

    @Convert(converter = StringListConverter.class)
    @Column(name = "skills", columnDefinition = "text")
    private List<String> skills;

    /** Embedding of the resume text in the shared/pinned vector space. */
    @Convert(converter = FloatListConverter.class)
    @Column(name = "embedding", columnDefinition = "text")
    private List<Float> embedding;

    /** LLM-suggested search queries produced by the single upload-time LLM call. */
    @Convert(converter = StringListConverter.class)
    @Column(name = "suggested_queries", columnDefinition = "text")
    private List<String> suggestedQueries;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected ResumeEntity() {
    }

    public ResumeEntity(String cognitoSub) {
        this.cognitoSub = cognitoSub;
    }

    public String getCognitoSub() {
        return cognitoSub;
    }

    public String getRawText() {
        return rawText;
    }

    public void setRawText(String rawText) {
        this.rawText = rawText;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public void setContactEmail(String contactEmail) {
        this.contactEmail = contactEmail;
    }

    public String getHeadline() {
        return headline;
    }

    public void setHeadline(String headline) {
        this.headline = headline;
    }

    public List<String> getSkills() {
        return skills;
    }

    public void setSkills(List<String> skills) {
        this.skills = skills;
    }

    public List<Float> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Float> embedding) {
        this.embedding = embedding;
    }

    public List<String> getSuggestedQueries() {
        return suggestedQueries;
    }

    public void setSuggestedQueries(List<String> suggestedQueries) {
        this.suggestedQueries = suggestedQueries;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

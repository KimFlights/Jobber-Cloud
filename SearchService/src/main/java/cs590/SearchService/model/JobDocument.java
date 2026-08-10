package cs590.SearchService.model;

import java.time.Instant;
import java.util.List;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

/**
 * The enriched, searchable job — the ES document SearchService owns (read side). Carries the
 * dense embedding so on-demand cosine match needs only a resume vector to compare against.
 */
@Document(indexName = "jobs")
public class JobDocument {

    @Id
    private String jobId;

    @Field(type = FieldType.Keyword)
    private String query;

    @Field(type = FieldType.Keyword)
    private String source;

    @Field(type = FieldType.Keyword, index = false)
    private String url;

    @Field(type = FieldType.Text)
    private String title;

    @Field(type = FieldType.Text)
    private String company;

    @Field(type = FieldType.Text)
    private String location;

    @Field(type = FieldType.Text)
    private String summary;

    @Field(type = FieldType.Text)
    private List<String> coreRequirements;

    @Field(type = FieldType.Keyword)
    private String experienceLevel;

    @Field(type = FieldType.Text)
    private List<String> mustHaves;

    @Field(type = FieldType.Text)
    private List<String> niceToHaves;

    @Field(type = FieldType.Text)
    private List<String> responsibilities;

    /** Dense embedding in the shared/pinned vector space; used for cosine match. */
    @Field(type = FieldType.Dense_Vector, dims = 1536)
    private List<Float> embedding;

    @Field(type = FieldType.Date)
    private Instant enrichedAt;

    public JobDocument() {
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getCompany() {
        return company;
    }

    public void setCompany(String company) {
        this.company = company;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<String> getCoreRequirements() {
        return coreRequirements;
    }

    public void setCoreRequirements(List<String> coreRequirements) {
        this.coreRequirements = coreRequirements;
    }

    public String getExperienceLevel() {
        return experienceLevel;
    }

    public void setExperienceLevel(String experienceLevel) {
        this.experienceLevel = experienceLevel;
    }

    public List<String> getMustHaves() {
        return mustHaves;
    }

    public void setMustHaves(List<String> mustHaves) {
        this.mustHaves = mustHaves;
    }

    public List<String> getNiceToHaves() {
        return niceToHaves;
    }

    public void setNiceToHaves(List<String> niceToHaves) {
        this.niceToHaves = niceToHaves;
    }

    public List<String> getResponsibilities() {
        return responsibilities;
    }

    public void setResponsibilities(List<String> responsibilities) {
        this.responsibilities = responsibilities;
    }

    public List<Float> getEmbedding() {
        return embedding;
    }

    public void setEmbedding(List<Float> embedding) {
        this.embedding = embedding;
    }

    public Instant getEnrichedAt() {
        return enrichedAt;
    }

    public void setEnrichedAt(Instant enrichedAt) {
        this.enrichedAt = enrichedAt;
    }
}

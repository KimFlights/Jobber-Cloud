package cs590.SearchService.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Per-user state for a job, keyed by (Cognito sub, job id): the saved-list membership, the last
 * computed match {@code rating} (%), and applied status. This is the only per-user state in the
 * system (ARCHITECTURE.md §2 / §6.5).
 */
@Entity
@Table(name = "user_job_state")
@IdClass(UserJobStateId.class)
public class UserJobState {

    @Id
    @Column(name = "cognito_id", nullable = false)
    private String cognitoId;

    @Id
    @Column(name = "job_id", nullable = false)
    private String jobId;

    /** Last computed cosine match percentage (0–100), or null if never matched. */
    @Column(name = "rating")
    private Integer rating;

    @Column(name = "applied", nullable = false)
    private boolean applied;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public UserJobState() {
    }

    public UserJobState(String cognitoId, String jobId) {
        this.cognitoId = cognitoId;
        this.jobId = jobId;
    }

    public String getCognitoId() {
        return cognitoId;
    }

    public void setCognitoId(String cognitoId) {
        this.cognitoId = cognitoId;
    }

    public String getJobId() {
        return jobId;
    }

    public void setJobId(String jobId) {
        this.jobId = jobId;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public boolean isApplied() {
        return applied;
    }

    public void setApplied(boolean applied) {
        this.applied = applied;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}

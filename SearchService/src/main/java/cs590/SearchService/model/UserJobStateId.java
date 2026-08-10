package cs590.SearchService.model;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link UserJobState}: the (Cognito sub, job id) pair. */
public class UserJobStateId implements Serializable {

    private String cognitoId;
    private String jobId;

    public UserJobStateId() {
    }

    public UserJobStateId(String cognitoId, String jobId) {
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof UserJobStateId that)) {
            return false;
        }
        return Objects.equals(cognitoId, that.cognitoId) && Objects.equals(jobId, that.jobId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(cognitoId, jobId);
    }
}

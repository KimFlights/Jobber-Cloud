package cs590.SearchService.web.dto;

import cs590.SearchService.model.UserJobState;
import java.time.Instant;

/** Per-user saved-job view: membership plus the last match rating and applied status. */
public record SavedJobView(String jobId, Integer rating, boolean applied, Instant updatedAt) {

    public static SavedJobView from(UserJobState s) {
        return new SavedJobView(s.getJobId(), s.getRating(), s.isApplied(), s.getUpdatedAt());
    }
}

package cs590.SearchService.service;

import cs590.SearchService.model.UserJobState;
import cs590.SearchService.model.UserJobStateId;
import cs590.SearchService.repository.UserJobStateRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns per-user job state: save-list membership, applied status, and last match rating. */
@Service
public class UserJobStateService {

    private final UserJobStateRepository repository;

    public UserJobStateService(UserJobStateRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public UserJobState save(String cognitoId, String jobId) {
        return upsert(cognitoId, jobId, state -> {
        });
    }

    @Transactional
    public UserJobState recordRating(String cognitoId, String jobId, int rating) {
        return upsert(cognitoId, jobId, state -> state.setRating(rating));
    }

    @Transactional
    public UserJobState setApplied(String cognitoId, String jobId, boolean applied) {
        return upsert(cognitoId, jobId, state -> state.setApplied(applied));
    }

    @Transactional(readOnly = true)
    public List<UserJobState> listSaved(String cognitoId) {
        return repository.findByCognitoId(cognitoId);
    }

    private UserJobState upsert(String cognitoId, String jobId, java.util.function.Consumer<UserJobState> mutation) {
        UserJobState state = repository.findById(new UserJobStateId(cognitoId, jobId))
                .orElseGet(() -> new UserJobState(cognitoId, jobId));
        mutation.accept(state);
        state.setUpdatedAt(Instant.now());
        return repository.save(state);
    }
}

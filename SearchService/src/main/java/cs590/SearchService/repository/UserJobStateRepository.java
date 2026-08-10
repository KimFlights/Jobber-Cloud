package cs590.SearchService.repository;

import cs590.SearchService.model.UserJobState;
import cs590.SearchService.model.UserJobStateId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserJobStateRepository extends JpaRepository<UserJobState, UserJobStateId> {

    List<UserJobState> findByCognitoId(String cognitoId);
}

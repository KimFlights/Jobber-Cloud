package cs590.ScraperService.repository;

import cs590.ScraperService.model.RawJob;
import java.util.Optional;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface RawJobRepository extends MongoRepository<RawJob, String> {

    long countByQuery(String query);

    Optional<RawJob> findTopByQueryOrderByScrapedAtDesc(String query);
}

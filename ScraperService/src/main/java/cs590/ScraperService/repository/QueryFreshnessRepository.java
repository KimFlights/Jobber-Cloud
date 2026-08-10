package cs590.ScraperService.repository;

import cs590.ScraperService.model.QueryFreshness;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface QueryFreshnessRepository extends MongoRepository<QueryFreshness, String> {
}

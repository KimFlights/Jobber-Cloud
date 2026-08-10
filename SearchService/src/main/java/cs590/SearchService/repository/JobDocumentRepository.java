package cs590.SearchService.repository;

import cs590.SearchService.model.JobDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

/** CRUD/indexing for enriched job documents. Full-text search uses ElasticsearchOperations. */
public interface JobDocumentRepository extends ElasticsearchRepository<JobDocument, String> {
}
